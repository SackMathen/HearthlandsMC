package net.puffish.castledungeons.placement;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Loads and caches all NBT structure pieces for one castle variant.
 * <p>
 * Pieces are loaded from the plugin JAR's resources at enable-time so there
 * is no IO during world generation.
 */
public class PieceSet {

    /** Structural / perimeter pieces keyed by name (tower, hallway, room, walk, …). */
    private final Map<String, Structure> pieces = new HashMap<>();
    /** Room template pieces keyed by name (alchemy, throne, …). */
    private final Map<String, Structure> rooms = new HashMap<>();

    private final String variantName; // "plains" or "desert"

    public PieceSet(String variantName) {
        this.variantName = variantName;
    }

    /**
     * Loads all pieces from JAR resources.
     *
     * @return false if any mandatory piece fails to load
     */
    public boolean load(Plugin plugin) {
        StructureManager sm = Bukkit.getServer().getStructureManager();

        String[] structurePieces = {
                "door", "hallway", "hallway_entrance", "hallway_window",
                "passage", "room", "stairs",
                "tower", "tower_balustrade", "tower_entrance", "tower_window",
                "walk", "walk_balustrade", "wall"
        };

        for (String name : structurePieces) {
            Structure s = loadFromResource(sm, plugin,
                    "structures/" + variantName + "/pieces/" + name + ".nbt");
            if (s == null) {
                plugin.getLogger().severe("Failed to load piece: " + variantName + "/" + name);
                return false;
            }
            pieces.put(name, s);
        }

        String[] roomNames = {
                "alchemy", "bed", "blacksmith", "cake", "coal_storage",
                "dinning", "enchanting", "food_storage", "hay_storage",
                "jukebox", "kitchen", "library", "storage", "throne"
        };

        for (String name : roomNames) {
            Structure s = loadFromResource(sm, plugin,
                    "structures/common/rooms/" + name + ".nbt");
            if (s == null) {
                plugin.getLogger().warning("Room template not found: " + name + " — skipping");
            } else {
                rooms.put(name, s);
            }
        }

        return true;
    }

    private static Structure loadFromResource(StructureManager sm, Plugin plugin, String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                plugin.getLogger().warning("Resource not found: " + path);
                return null;
            }
            return sm.loadStructure(in);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load structure: " + path, e);
            return null;
        }
    }

    public Structure getPiece(String name) {
        return pieces.get(name);
    }

    public Structure getRoom(String name) {
        return rooms.get(name);
    }

    public boolean hasPiece(String name)  { return pieces.containsKey(name); }
    public boolean hasRoom(String name)   { return rooms.containsKey(name); }
}
