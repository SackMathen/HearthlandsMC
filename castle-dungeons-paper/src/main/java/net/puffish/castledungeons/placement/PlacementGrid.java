package net.puffish.castledungeons.placement;

import java.util.Random;

/**
 * Replicates Minecraft's {@code random_spread} structure placement algorithm.
 * <p>
 * The world is divided into square regions of {@code spacing × spacing} chunks.
 * Each region holds at most one candidate position, placed pseudo-randomly inside
 * the region (at least {@code separation} chunks from the region boundary).
 */
public class PlacementGrid {

    private final int spacing;
    private final int separation;
    private final long salt;

    public PlacementGrid(int spacing, int separation, long salt) {
        this.spacing    = spacing;
        this.separation = separation;
        this.salt       = salt;
    }

    /**
     * Returns {@code true} if the given chunk should be the anchor of a castle.
     */
    public boolean isStructureChunk(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        Random rng = regionRandom(worldSeed, regionX, regionZ);
        int spread = spacing - separation;
        int candidateX = regionX * spacing + rng.nextInt(spread);
        int candidateZ = regionZ * spacing + rng.nextInt(spread);
        return chunkX == candidateX && chunkZ == candidateZ;
    }

    /**
     * Returns a seeded {@link Random} tied to the given world seed + region.
     * Same algorithm as Minecraft's {@code StructureSettings.getRandom}.
     */
    public Random structureRandom(long worldSeed, int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, spacing);
        int regionZ = Math.floorDiv(chunkZ, spacing);
        long seed = worldSeed
                + (long) regionX * 341873128712L
                + (long) regionZ * 132897987541L
                + salt;
        Random rng = new Random(seed);
        // Consume the two ints used for candidate position selection
        rng.nextInt();
        rng.nextInt();
        // Return with remaining state for structure-internal randomness
        return rng;
    }

    private Random regionRandom(long worldSeed, int regionX, int regionZ) {
        long seed = worldSeed
                + (long) regionX * 341873128712L
                + (long) regionZ * 132897987541L
                + salt;
        return new Random(seed);
    }
}
