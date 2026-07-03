package net.puffish.castledungeons.listener;

import net.puffish.castledungeons.CastleDungeonsPlugin;
import net.puffish.castledungeons.config.CastleVariantConfig;
import net.puffish.castledungeons.generation.CastleLayout;
import net.puffish.castledungeons.placement.CastlePlacer;
import net.puffish.castledungeons.placement.PieceSet;
import net.puffish.castledungeons.placement.PlacementGrid;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkPopulateEvent;

import java.util.List;
import java.util.Random;

/**
 * Listens for chunk population events and injects castle structures where the
 * placement grid dictates.
 */
public class WorldStructureListener implements Listener {

    private final CastleDungeonsPlugin plugin;
    private final PlacementGrid placementGrid;

    public WorldStructureListener(CastleDungeonsPlugin plugin, PlacementGrid placementGrid) {
        this.plugin        = plugin;
        this.placementGrid = placementGrid;
    }

    @SuppressWarnings("deprecation") // ChunkPopulateEvent is still functional in 1.19.2
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChunkPopulate(ChunkPopulateEvent event) {
        World world = event.getWorld();

        // Only generate in the overworld
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        Chunk chunk = event.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        long worldSeed = world.getSeed();

        if (!placementGrid.isStructureChunk(worldSeed, chunkX, chunkZ)) return;

        // Determine which variant to use by sampling the biome at the chunk centre
        int blockX = chunkX * 16 + 8;
        int blockZ = chunkZ * 16 + 8;

        CastleVariantConfig variant = pickVariant(world, blockX, blockZ);
        if (variant == null) return;

        PieceSet pieces = plugin.getPieceSet(variant.name());
        if (pieces == null) return;

        Random rng = placementGrid.structureRandom(worldSeed, chunkX, chunkZ);

        // Generate the procedural layout
        CastleLayout layout = CastleLayout.generate(variant, rng);

        // Find surface Y at the centre of the intended footprint
        int footprintCentreX = blockX + layout.totalWidthBlocks() / 2;
        int footprintCentreZ = blockZ + layout.totalDepthBlocks() / 2;
        int groundY = world.getHighestBlockYAt(footprintCentreX, footprintCentreZ);

        // Schedule one tick later so all adjacent chunks are populated before we place blocks
        int finalBlockX = blockX;
        int finalBlockZ = blockZ;
        int finalGroundY = groundY;
        plugin.getServer().getScheduler().runTask(plugin, () ->
                CastlePlacer.place(world, finalBlockX, finalGroundY, finalBlockZ,
                        layout, pieces, variant, new Random(rng.nextLong()))
        );
    }

    /**
     * Returns the first enabled variant whose biome list contains the biome at (x, z),
     * or {@code null} if no variant matches.
     */
    private CastleVariantConfig pickVariant(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z);
        Biome biome = world.getBiome(x, y, z);
        String biomeKey = biome.getKey().toString(); // e.g. "minecraft:plains"

        List<CastleVariantConfig> variants = plugin.getVariants();

        // Prioritise more specific variants (desert before plains) by config order
        for (CastleVariantConfig v : variants) {
            if (!v.enabled()) continue;
            if (v.biomes().contains(biomeKey)) return v;
        }
        return null;
    }
}
