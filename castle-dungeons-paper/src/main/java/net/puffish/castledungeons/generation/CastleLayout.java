package net.puffish.castledungeons.generation;

import net.puffish.castledungeons.config.CastleVariantConfig;
import net.puffish.castledungeons.config.RoomTemplateConfig;

import java.util.*;

/**
 * Procedurally generates a CastleGrid from a CastleVariantConfig and a seeded Random.
 * <p>
 * Castle anatomy (top-down):
 * <pre>
 *   T H H H T
 *   H R R R H
 *   H R R R H
 *   T H H H T
 * </pre>
 * T = TOWER (corner, spans all floors)
 * H = HALLWAY (perimeter edge, per-floor)
 * R = ROOM or merged room area (interior)
 * </p>
 */
public class CastleLayout {

    /** Width of each structural cell piece in blocks (tower.nbt / room.nbt x-dimension). */
    public static final int CELL_W = 7;
    /** Depth of each structural cell piece in blocks. */
    public static final int CELL_D = 7;
    /**
     * Stride between cell origins: 7-block cell + 1-block gap for connector pieces
     * (door.nbt / wall.nbt / passage.nbt are all 7×5×1).
     */
    public static final int STRIDE = 8;
    /** Height of one floor (hallway.nbt / room.nbt y-dimension). */
    public static final int FLOOR_H = 5;
    /** Number of full structural floors stacked inside the tower (tower.nbt = 15 = 3×5). */
    public static final int NUM_FLOORS = 3;

    // -----------------------------------------------------------------------

    private final CastleGrid grid;
    /**
     * roomAssignments[gz][gx] = RoomTemplateConfig to place at interior cell (gx,gz),
     * or null for an undecorated room shell.
     */
    private final RoomTemplateConfig[][] roomAssignments;
    /** Width of the generated grid in cells. */
    private final int width;
    /** Depth of the generated grid in cells. */
    private final int depth;
    /** Which cell on the perimeter is the main entrance (encoded as gx*1000+gz for quick lookup). */
    private final int entranceGx;
    private final int entranceGz;
    private final Direction4 entranceFacing;

    private CastleLayout(CastleGrid grid, RoomTemplateConfig[][] roomAssignments,
                         int entranceGx, int entranceGz, Direction4 entranceFacing) {
        this.grid = grid;
        this.roomAssignments = roomAssignments;
        this.width = grid.getWidth();
        this.depth = grid.getDepth();
        this.entranceGx = entranceGx;
        this.entranceGz = entranceGz;
        this.entranceFacing = entranceFacing;
    }

    // -----------------------------------------------------------------------
    // Factory

    public static CastleLayout generate(CastleVariantConfig config, Random rng) {
        int w = config.minSize() + rng.nextInt(config.maxSize() - config.minSize() + 1);
        int d = config.minSize() + rng.nextInt(config.maxSize() - config.minSize() + 1);

        CastleGrid grid = new CastleGrid(w, d);

        // --- 1. Assign node types ---
        for (int gz = 0; gz < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                boolean isCorner = (gx == 0 || gx == w - 1) && (gz == 0 || gz == d - 1);
                boolean isEdge   = gx == 0 || gx == w - 1 || gz == 0 || gz == d - 1;

                if (isCorner)    grid.setNode(gx, gz, CastleNodeType.TOWER);
                else if (isEdge) grid.setNode(gx, gz, CastleNodeType.HALLWAY);
                else             grid.setNode(gx, gz, CastleNodeType.ROOM);
            }
        }

        // --- 2. Assign room templates to interior cells ---
        RoomTemplateConfig[][] assignments = new RoomTemplateConfig[d][w];
        if (!config.rooms().isEmpty()) {
            assignRooms(grid, assignments, config.rooms(), w, d, rng);
        }

        // --- 3. Build connections between adjacent cells ---
        buildConnections(grid, assignments, w, d);

        // --- 4. Pick entrance on one perimeter side ---
        Direction4[] sides = Direction4.values();
        Direction4 entranceSide = sides[rng.nextInt(sides.length)];
        int eGx = 0, eGz = 0;
        List<int[]> candidates = perimeterCells(grid, w, d, entranceSide);
        if (!candidates.isEmpty()) {
            int[] picked = candidates.get(rng.nextInt(candidates.size()));
            eGx = picked[0];
            eGz = picked[1];
        }

        return new CastleLayout(grid, assignments, eGx, eGz, entranceSide);
    }

    // -----------------------------------------------------------------------
    // Room assignment

    private static void assignRooms(CastleGrid grid,
                                    RoomTemplateConfig[][] assignments,
                                    List<RoomTemplateConfig> templates,
                                    int w, int d, Random rng) {
        // Collect interior cells; shuffle for random order
        List<int[]> interior = new ArrayList<>();
        for (int gz = 1; gz < d - 1; gz++)
            for (int gx = 1; gx < w - 1; gx++)
                interior.add(new int[]{gx, gz});
        Collections.shuffle(interior, rng);

        // Track how many of each template we've placed
        int[] placed = new int[templates.size()];

        // First pass: satisfy min-count requirements (guaranteed rooms)
        for (int i = 0; i < templates.size(); i++) {
            RoomTemplateConfig tmpl = templates.get(i);
            while (placed[i] < tmpl.minCount()) {
                if (!tryPlace(tmpl, assignments, interior, w, d)) break; // not enough space
                placed[i]++;
            }
        }

        // Second pass: fill remaining cells with weighted random templates
        for (int[] cell : interior) {
            if (assignments[cell[1]][cell[0]] != null) continue; // already assigned

            List<RoomTemplateConfig> available = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < templates.size(); i++) {
                if (placed[i] < templates.get(i).maxCount()) {
                    available.add(templates.get(i));
                    indices.add(i);
                }
            }
            if (available.isEmpty()) continue;

            int totalWeight = available.stream().mapToInt(RoomTemplateConfig::weight).sum();
            int roll = rng.nextInt(totalWeight);
            int cumulative = 0;
            for (int k = 0; k < available.size(); k++) {
                cumulative += available.get(k).weight();
                if (roll < cumulative) {
                    RoomTemplateConfig chosen = available.get(k);
                    if (tryPlaceAt(chosen, cell[0], cell[1], assignments, w, d)) {
                        placed[indices.get(k)]++;
                    }
                    break;
                }
            }
        }
    }

    private static boolean tryPlace(RoomTemplateConfig tmpl,
                                    RoomTemplateConfig[][] assignments,
                                    List<int[]> interior, int w, int d) {
        for (int[] cell : interior) {
            if (assignments[cell[1]][cell[0]] != null) continue;
            if (tryPlaceAt(tmpl, cell[0], cell[1], assignments, w, d)) return true;
        }
        return false;
    }

    private static boolean tryPlaceAt(RoomTemplateConfig tmpl, int gx, int gz,
                                       RoomTemplateConfig[][] assignments, int w, int d) {
        int cx = tmpl.cellsX();
        int cz = tmpl.cellsZ();

        // Verify all required cells are interior and unassigned
        for (int dz = 0; dz < cz; dz++) {
            for (int dx = 0; dx < cx; dx++) {
                int nx = gx + dx;
                int nz = gz + dz;
                if (nx < 1 || nx > w - 2 || nz < 1 || nz > d - 2) return false;
                if (assignments[nz][nx] != null) return false;
            }
        }

        // Mark all cells in the footprint
        for (int dz = 0; dz < cz; dz++)
            for (int dx = 0; dx < cx; dx++)
                assignments[gz + dz][gx + dx] = tmpl;

        return true;
    }

    // -----------------------------------------------------------------------
    // Connection building

    private static void buildConnections(CastleGrid grid,
                                         RoomTemplateConfig[][] assignments,
                                         int w, int d) {
        for (int gz = 0; gz < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                CastleNodeType nodeType = grid.getNode(gx, gz);
                if (nodeType == CastleNodeType.EMPTY) continue;

                for (Direction4 dir : Direction4.values()) {
                    int nx = gx + dir.dx;
                    int nz = gz + dir.dz;

                    // Skip already-set connections (symmetric write handles them)
                    if (grid.getConnection(gx, gz, dir) != ConnectionType.NONE) continue;

                    if (!grid.inBounds(nx, nz)) {
                        // Exterior face — connector is handled as outward-facing piece
                        grid.setConnection(gx, gz, dir, ConnectionType.WALL);
                        continue;
                    }

                    CastleNodeType neighborType = grid.getNode(nx, nz);

                    ConnectionType conn = switch (nodeType) {
                        case TOWER   -> ConnectionType.NONE; // towers handle adjacency via their own structure
                        case HALLWAY -> switch (neighborType) {
                            case ROOM    -> ConnectionType.DOOR;
                            case HALLWAY -> ConnectionType.PASSAGE;
                            case TOWER   -> ConnectionType.NONE;
                            default      -> ConnectionType.WALL;
                        };
                        case ROOM -> switch (neighborType) {
                            case ROOM -> isSharedRoom(assignments, gx, gz, nx, nz)
                                    ? ConnectionType.PASSAGE  // merged room — open interior
                                    : ConnectionType.DOOR;
                            case HALLWAY -> ConnectionType.DOOR;
                            case TOWER   -> ConnectionType.NONE;
                            default      -> ConnectionType.WALL;
                        };
                        default -> ConnectionType.NONE;
                    };

                    grid.setConnection(gx, gz, dir, conn);
                }
            }
        }
    }

    /** Two cells share a room if they're both assigned to the exact same template instance. */
    private static boolean isSharedRoom(RoomTemplateConfig[][] assignments,
                                        int gx, int gz, int nx, int nz) {
        RoomTemplateConfig a = assignments[gz][gx];
        RoomTemplateConfig b = assignments[nz][nx];
        return a != null && a == b; // same object reference = same merged-room assignment
    }

    // -----------------------------------------------------------------------
    // Helpers

    /** Returns all HALLWAY cells on the given perimeter side. */
    private static List<int[]> perimeterCells(CastleGrid grid, int w, int d, Direction4 side) {
        List<int[]> result = new ArrayList<>();
        switch (side) {
            case NORTH -> {
                for (int gx = 1; gx < w - 1; gx++)
                    if (grid.getNode(gx, 0) == CastleNodeType.HALLWAY) result.add(new int[]{gx, 0});
            }
            case SOUTH -> {
                for (int gx = 1; gx < w - 1; gx++)
                    if (grid.getNode(gx, d - 1) == CastleNodeType.HALLWAY) result.add(new int[]{gx, d - 1});
            }
            case WEST -> {
                for (int gz = 1; gz < d - 1; gz++)
                    if (grid.getNode(0, gz) == CastleNodeType.HALLWAY) result.add(new int[]{0, gz});
            }
            case EAST -> {
                for (int gz = 1; gz < d - 1; gz++)
                    if (grid.getNode(w - 1, gz) == CastleNodeType.HALLWAY) result.add(new int[]{w - 1, gz});
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Accessors

    public CastleGrid getGrid()       { return grid; }
    public int getWidth()             { return width; }
    public int getDepth()             { return depth; }
    public int getEntranceGx()        { return entranceGx; }
    public int getEntranceGz()        { return entranceGz; }
    public Direction4 getEntranceFacing() { return entranceFacing; }

    public RoomTemplateConfig getRoomTemplate(int gx, int gz) {
        return roomAssignments[gz][gx];
    }

    /** Returns the total castle footprint width in blocks. */
    public int totalWidthBlocks() { return width * CELL_W + (width - 1); }

    /** Returns the total castle footprint depth in blocks. */
    public int totalDepthBlocks() { return depth * CELL_D + (depth - 1); }
}
