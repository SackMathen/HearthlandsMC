package net.puffish.castledungeons;

import net.puffish.castledungeons.config.CastleVariantConfig;
import net.puffish.castledungeons.config.RoomTemplateConfig;
import net.puffish.castledungeons.listener.WorldStructureListener;
import net.puffish.castledungeons.placement.PieceSet;
import net.puffish.castledungeons.placement.PlacementGrid;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CastleDungeonsPlugin extends JavaPlugin {

    private final List<CastleVariantConfig> variants = new ArrayList<>();
    private final Map<String, PieceSet> pieceSets   = new HashMap<>();
    private PlacementGrid placementGrid;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        placementGrid = new PlacementGrid(
                getConfig().getInt("spacing",    64),
                getConfig().getInt("separation",  8),
                getConfig().getLong("salt", 30042004L)
        );

        ConfigurationSection variantsSection = getConfig().getConfigurationSection("variants");
        if (variantsSection == null) {
            getLogger().severe("No 'variants' section found in config.yml — disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean anyLoaded = false;
        for (String variantName : variantsSection.getKeys(false)) {
            ConfigurationSection section = variantsSection.getConfigurationSection(variantName);
            if (section == null) continue;

            if (!section.getBoolean("enabled", true)) {
                getLogger().info("Variant '" + variantName + "' is disabled — skipping.");
                continue;
            }

            List<RoomTemplateConfig> rooms = parseRooms(section, variantName);
            CastleVariantConfig variant = new CastleVariantConfig(
                    variantName,
                    true,
                    section.getInt("min-size", 4),
                    section.getInt("max-size", 8),
                    section.getDouble("mossiness", 0.0),
                    section.getDouble("crackiness", 0.0),
                    section.getStringList("biomes"),
                    rooms
            );
            variants.add(variant);

            PieceSet ps = new PieceSet(variantName);
            if (!ps.load(this)) {
                getLogger().severe("Failed to load pieces for variant '" + variantName + "' — skipping.");
                continue;
            }
            pieceSets.put(variantName, ps);
            anyLoaded = true;
            getLogger().info("Loaded castle variant: " + variantName
                    + " (size " + variant.minSize() + "–" + variant.maxSize() + ")");
        }

        if (!anyLoaded) {
            getLogger().severe("No variants loaded successfully — disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(
                new WorldStructureListener(this, placementGrid), this);

        getLogger().info("Castle Dungeons enabled — "
                + variants.size() + " variant(s) active, "
                + "spacing=" + getConfig().getInt("spacing", 64) + " chunks.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Castle Dungeons disabled.");
    }

    // -----------------------------------------------------------------------

    private List<RoomTemplateConfig> parseRooms(ConfigurationSection variantSection,
                                                String variantName) {
        List<Map<?, ?>> roomList = variantSection.getMapList("rooms");
        List<RoomTemplateConfig> result = new ArrayList<>();

        for (Map<?, ?> entry : roomList) {
            try {
                String structure = (String) entry.get("structure");
                int weight   = ((Number) entry.get("weight")).intValue();
                int minCount = entry.containsKey("min-count") ? ((Number) entry.get("min-count")).intValue() : 0;
                int maxCount = entry.containsKey("max-count") ? ((Number) entry.get("max-count")).intValue() : 1;

                // Look up NBT dimensions from resource to determine cell footprint
                int[] size = readNbtSize("structures/common/rooms/" + structure + ".nbt");
                int sizeX = size != null ? size[0] : 5;
                int sizeZ = size != null ? size[2] : 5;

                result.add(new RoomTemplateConfig(structure, weight, minCount, maxCount, sizeX, sizeZ));
            } catch (Exception e) {
                getLogger().warning("Skipping malformed room entry in variant '" + variantName + "': " + entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Reads the {@code size} field from a Minecraft NBT structure file bundled in the JAR.
     * Returns [x, y, z] or null on failure.
     */
    private int[] readNbtSize(String resourcePath) {
        try (var in = getResource(resourcePath)) {
            if (in == null) return null;
            // Read all bytes and decompress (Minecraft structure files are gzip-compressed NBT)
            byte[] compressed = in.readAllBytes();
            try (var gzIn = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
                 var dataIn = new java.io.DataInputStream(gzIn)) {

                // NBT root: type(1 byte) + name_len(2 bytes) + name + payload
                int type = dataIn.readUnsignedByte();
                if (type != 10) return null; // must be TAG_Compound
                int nameLen = dataIn.readUnsignedShort();
                dataIn.skipBytes(nameLen);

                // Scan tags until we find "size" (TAG_List of TAG_Int)
                while (true) {
                    int tagType = dataIn.readUnsignedByte();
                    if (tagType == 0) break; // TAG_End
                    int tNameLen = dataIn.readUnsignedShort();
                    byte[] tNameBytes = new byte[tNameLen];
                    dataIn.readFully(tNameBytes);
                    String tName = new String(tNameBytes);

                    if (tName.equals("size") && tagType == 9) {
                        // TAG_List: element type (1 byte) + count (4 bytes) + elements
                        int elemType = dataIn.readUnsignedByte();
                        int count    = dataIn.readInt();
                        if (elemType == 3 && count == 3) {
                            return new int[]{dataIn.readInt(), dataIn.readInt(), dataIn.readInt()};
                        }
                        return null;
                    }

                    // Skip this tag's payload so we can move to the next
                    skipNbtPayload(dataIn, tagType);
                }
            }
        } catch (Exception e) {
            getLogger().fine("Could not read NBT size from " + resourcePath + ": " + e.getMessage());
        }
        return null;
    }

    /** Skips over an NBT payload of the given type. */
    private static void skipNbtPayload(java.io.DataInputStream in, int type) throws java.io.IOException {
        switch (type) {
            case 1  -> in.skipBytes(1);
            case 2  -> in.skipBytes(2);
            case 3  -> in.skipBytes(4);
            case 4  -> in.skipBytes(8);
            case 5  -> in.skipBytes(4);
            case 6  -> in.skipBytes(8);
            case 7  -> in.skipBytes(in.readInt());          // TAG_ByteArray
            case 8  -> in.skipBytes(in.readUnsignedShort()); // TAG_String
            case 9  -> {                                    // TAG_List
                int et = in.readUnsignedByte();
                int cnt = in.readInt();
                for (int i = 0; i < cnt; i++) skipNbtPayload(in, et);
            }
            case 10 -> {                                    // TAG_Compound
                while (true) {
                    int et = in.readUnsignedByte();
                    if (et == 0) break;
                    in.skipBytes(in.readUnsignedShort());   // skip name
                    skipNbtPayload(in, et);
                }
            }
            case 11 -> in.skip((long) in.readInt() * 4L);    // TAG_IntArray
            case 12 -> in.skip((long) in.readInt() * 8L);    // TAG_LongArray
        }
    }

    // -----------------------------------------------------------------------
    // Accessors used by WorldStructureListener

    public List<CastleVariantConfig> getVariants()       { return Collections.unmodifiableList(variants); }
    public PieceSet getPieceSet(String variantName)      { return pieceSets.get(variantName); }
    public PlacementGrid getPlacementGrid()              { return placementGrid; }
}
