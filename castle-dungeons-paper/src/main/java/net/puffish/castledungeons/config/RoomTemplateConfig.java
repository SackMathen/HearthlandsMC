package net.puffish.castledungeons.config;

/**
 * Configuration for one room template type loaded from config.yml.
 *
 * @param structure  Key into the common/rooms/ NBT folder (e.g. "alchemy")
 * @param weight     Weighted random selection weight
 * @param minCount   Guaranteed minimum placements per castle
 * @param maxCount   Hard placement cap per castle
 * @param sizeX      NBT structure width  (determined at load time)
 * @param sizeZ      NBT structure depth  (determined at load time)
 */
public record RoomTemplateConfig(
        String structure,
        int weight,
        int minCount,
        int maxCount,
        int sizeX,
        int sizeZ
) {
    /** Returns how many grid cells this room occupies in X (1 or 2). */
    public int cellsX() {
        // 5-block rooms fit in 1 cell; 11-block rooms need 2 cells (5+1gap+5=11)
        return sizeX > 7 ? 2 : 1;
    }

    /** Returns how many grid cells this room occupies in Z (1 or 2). */
    public int cellsZ() {
        return sizeZ > 7 ? 2 : 1;
    }
}
