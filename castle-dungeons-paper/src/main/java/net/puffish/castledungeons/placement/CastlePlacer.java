package net.puffish.castledungeons.placement;

import net.puffish.castledungeons.config.CastleVariantConfig;
import net.puffish.castledungeons.config.RoomTemplateConfig;
import net.puffish.castledungeons.generation.CastleGrid;
import net.puffish.castledungeons.generation.CastleLayout;
import net.puffish.castledungeons.generation.CastleNodeType;
import net.puffish.castledungeons.generation.ConnectionType;
import net.puffish.castledungeons.generation.Direction4;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructurePlaceSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static net.puffish.castledungeons.generation.CastleLayout.*;

/**
 * Places a fully-generated {@link CastleLayout} into the world using NBT structure pieces.
 *
 * <h3>Coordinate system</h3>
 * <pre>
 *   origin (originX, originY, originZ) = NW corner of cell (0, 0)
 *   cell (gx, gz) world-X = originX + gx * STRIDE
 *   cell (gx, gz) world-Z = originZ + gz * STRIDE
 *   floor f        world-Y = originY + f * FLOOR_H
 * </pre>
 *
 * <h3>Connector pieces (door, wall, passage)</h3>
 * Connectors fill the 1-block gap between adjacent cells.
 * The base piece is [7 × 5 × 1] oriented along the Z axis.
 * For an X-direction gap the piece is rotated CLOCKWISE_90 to become [1 × 5 × 7].
 */
public class CastlePlacer {

    // Mossy/cracked block substitutions mirroring MossyCrackedStructureProcessor
    private static final Map<Material, Material> MOSSY = new HashMap<>(Map.of(
            Material.STONE_BRICKS,       Material.MOSSY_STONE_BRICKS,
            Material.STONE_BRICK_SLAB,   Material.MOSSY_STONE_BRICK_SLAB,
            Material.STONE_BRICK_STAIRS, Material.MOSSY_STONE_BRICK_STAIRS,
            Material.STONE_BRICK_WALL,   Material.MOSSY_STONE_BRICK_WALL
    ));

    private static final Map<Material, Material> CRACKED = new HashMap<>(Map.of(
            Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS
    ));

    // -----------------------------------------------------------------------

    public static void place(World world, int originX, int originY, int originZ,
                             CastleLayout layout, PieceSet pieces,
                             CastleVariantConfig config, Random rng) {
        CastleGrid grid = layout.getGrid();
        int w = layout.getWidth();
        int d = layout.getDepth();

        StructurePlaceSettings defaultSettings = new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .setMirror(Mirror.NONE)
                .setRotation(StructureRotation.NONE);

        // ---- 1. Place towers (full-height, placed once — not per-floor) -------
        for (int gz = 0; gz < d; gz++) {
            for (int gx = 0; gx < w; gx++) {
                if (grid.getNode(gx, gz) != CastleNodeType.TOWER) continue;
                int wx = originX + gx * STRIDE;
                int wz = originZ + gz * STRIDE;
                placeStructure(pieces.getPiece("tower"), world, wx, originY, wz, defaultSettings, rng);

                // Tower-balustrade on the top portion of each corner tower
                // (piece is [7×10×2]; placed on each exterior face at floor-1 height)
                placeTowerFacings(world, gx, gz, wx, originY, wz,
                        layout, pieces, rng);
            }
        }

        // ---- 2. Per-floor: hallways, rooms, connectors -----------------------
        for (int floor = 0; floor < NUM_FLOORS; floor++) {
            int wy = originY + floor * FLOOR_H;

            for (int gz = 0; gz < d; gz++) {
                for (int gx = 0; gx < w; gx++) {
                    CastleNodeType nodeType = grid.getNode(gx, gz);
                    int wx = originX + gx * STRIDE;
                    int wz = originZ + gz * STRIDE;

                    if (floor < NUM_FLOORS - 1) {
                        // Full floors (0 and 1): hallways and rooms
                        if (nodeType == CastleNodeType.HALLWAY) {
                            placeStructure(pieces.getPiece("hallway"), world, wx, wy, wz, defaultSettings, rng);
                        } else if (nodeType == CastleNodeType.ROOM) {
                            placeStructure(pieces.getPiece("room"), world, wx, wy, wz, defaultSettings, rng);
                            placeRoomTemplate(world, gx, gz, wx, wy, wz, layout, pieces, rng);
                        }
                    } else {
                        // Top floor (2): battlements
                        if (nodeType == CastleNodeType.HALLWAY) {
                            placeStructure(pieces.getPiece("walk"), world, wx, wy, wz, defaultSettings, rng);
                            placeWalkBalustrade(world, gx, gz, wx, wy, wz, layout, pieces, rng);
                        }
                        // ROOM cells on the roof level are left open (no piece)
                    }

                    // ---- Connector pieces in the EAST gap (between gx and gx+1) ----
                    if (gx < w - 1 && floor < NUM_FLOORS - 1) {
                        ConnectionType conn = grid.getConnection(gx, gz, Direction4.EAST);
                        placeConnectorX(conn, world, originX + gx * STRIDE + CELL_W, wy, wz, pieces, rng, layout, gx, gz);
                    }

                    // ---- Connector pieces in the SOUTH gap (between gz and gz+1) ----
                    if (gz < d - 1 && floor < NUM_FLOORS - 1) {
                        ConnectionType conn = grid.getConnection(gx, gz, Direction4.SOUTH);
                        placeConnectorZ(conn, world, wx, wy, originZ + gz * STRIDE + CELL_D, pieces, rng, layout, gx, gz);
                    }
                }
            }
        }

        // ---- 3. Apply mossy / cracked post-processing ------------------------
        if (config.mossiness() > 0 || config.crackiness() > 0) {
            applyMossyCracked(world, originX, originY, originZ, layout,
                    config.mossiness(), config.crackiness(), rng);
        }
    }

    // -----------------------------------------------------------------------
    // Connector placement helpers

    /**
     * Places a connector piece (door/wall/passage) in the 1-block X-direction gap
     * between cell (gx,gz) and (gx+1,gz).
     * The base piece is [7×5×1]; rotate CW90 so it becomes [1×5×7] in the gap.
     */
    private static void placeConnectorX(ConnectionType conn, World world,
                                        int gapX, int y, int z,
                                        PieceSet pieces, Random rng,
                                        CastleLayout layout, int gx, int gz) {
        String pieceName = connectorPieceName(conn, layout, gx, gz, Direction4.EAST);
        if (pieceName == null) return;

        Structure piece = pieces.getPiece(pieceName);
        if (piece == null) return;

        // Rotate CW90: [7×5×1] → effectively fills 1 block in X and 7 blocks in Z
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(StructureRotation.CLOCKWISE_90)
                .setMirror(Mirror.NONE);
        placeStructure(piece, world, gapX, y, z, settings, rng);
    }

    /**
     * Places a connector piece in the 1-block Z-direction gap between (gx,gz) and (gx,gz+1).
     * No rotation needed — the base piece [7×5×1] already aligns along Z.
     */
    private static void placeConnectorZ(ConnectionType conn, World world,
                                        int x, int y, int gapZ,
                                        PieceSet pieces, Random rng,
                                        CastleLayout layout, int gx, int gz) {
        String pieceName = connectorPieceName(conn, layout, gx, gz, Direction4.SOUTH);
        if (pieceName == null) return;

        Structure piece = pieces.getPiece(pieceName);
        if (piece == null) return;

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(StructureRotation.NONE)
                .setMirror(Mirror.NONE);
        placeStructure(piece, world, x, y, gapZ, settings, rng);
    }

    private static String connectorPieceName(ConnectionType conn,
                                             CastleLayout layout, int gx, int gz,
                                             Direction4 dir) {
        // Check if this is the main entrance exterior connector
        boolean isEntrance = (gx == layout.getEntranceGx() && gz == layout.getEntranceGz()
                && dir == layout.getEntranceFacing());

        return switch (conn) {
            case DOOR    -> isEntrance ? "hallway_entrance" : "door";
            case PASSAGE -> "passage";
            case WALL    -> isEntrance ? "hallway_entrance" : "wall";
            case NONE    -> null;
        };
    }

    // -----------------------------------------------------------------------
    // Tower facing pieces

    private static void placeTowerFacings(World world, int gx, int gz,
                                          int wx, int originY, int wz,
                                          CastleLayout layout, PieceSet pieces,
                                          Random rng) {
        int w = layout.getWidth();
        int d = layout.getDepth();

        // tower_balustrade [7×10×2] placed on each exterior face at y = originY + FLOOR_H
        // (spans the top 2 floors of the tower)
        int balY = originY + FLOOR_H;

        if (gz == 0) { // north face
            Structure tb = pieces.getPiece("tower_balustrade");
            if (tb != null) {
                StructurePlaceSettings s = new StructurePlaceSettings()
                        .setRotation(StructureRotation.NONE).setMirror(Mirror.NONE);
                placeStructure(tb, world, wx, balY, wz - 2, s, rng);
            }
        }
        if (gz == d - 1) { // south face
            Structure tb = pieces.getPiece("tower_balustrade");
            if (tb != null) {
                StructurePlaceSettings s = new StructurePlaceSettings()
                        .setRotation(StructureRotation.CLOCKWISE_180).setMirror(Mirror.NONE);
                placeStructure(tb, world, wx, balY, wz + CELL_D + 2, s, rng);
            }
        }
        if (gx == 0) { // west face
            Structure tb = pieces.getPiece("tower_balustrade");
            if (tb != null) {
                StructurePlaceSettings s = new StructurePlaceSettings()
                        .setRotation(StructureRotation.COUNTERCLOCKWISE_90).setMirror(Mirror.NONE);
                placeStructure(tb, world, wx - 2, balY, wz, s, rng);
            }
        }
        if (gx == w - 1) { // east face
            Structure tb = pieces.getPiece("tower_balustrade");
            if (tb != null) {
                StructurePlaceSettings s = new StructurePlaceSettings()
                        .setRotation(StructureRotation.CLOCKWISE_90).setMirror(Mirror.NONE);
                placeStructure(tb, world, wx + CELL_W + 2, balY, wz, s, rng);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Walk balustrade on roof level

    private static void placeWalkBalustrade(World world, int gx, int gz,
                                            int wx, int wy, int wz,
                                            CastleLayout layout, PieceSet pieces,
                                            Random rng) {
        int w = layout.getWidth();
        int d = layout.getDepth();
        Structure wb = pieces.getPiece("walk_balustrade");
        if (wb == null) return;

        // Exterior-facing side gets the balustrade [7×4×1]
        if (gz == 0) { // north exterior
            StructurePlaceSettings s = new StructurePlaceSettings()
                    .setRotation(StructureRotation.NONE).setMirror(Mirror.NONE);
            placeStructure(wb, world, wx, wy, wz - 1, s, rng);
        }
        if (gz == d - 1) { // south exterior
            StructurePlaceSettings s = new StructurePlaceSettings()
                    .setRotation(StructureRotation.CLOCKWISE_180).setMirror(Mirror.NONE);
            placeStructure(wb, world, wx, wy, wz + CELL_D + 1, s, rng);
        }
        if (gx == 0) { // west exterior
            StructurePlaceSettings s = new StructurePlaceSettings()
                    .setRotation(StructureRotation.COUNTERCLOCKWISE_90).setMirror(Mirror.NONE);
            placeStructure(wb, world, wx - 1, wy, wz, s, rng);
        }
        if (gx == w - 1) { // east exterior
            StructurePlaceSettings s = new StructurePlaceSettings()
                    .setRotation(StructureRotation.CLOCKWISE_90).setMirror(Mirror.NONE);
            placeStructure(wb, world, wx + CELL_W + 1, wy, wz, s, rng);
        }
    }

    // -----------------------------------------------------------------------
    // Room template placement

    /**
     * Places a room template NBT inside a room cell.
     * Small rooms (5×4×5) fit within a single cell at a 1-block inset.
     * Medium rooms (5×4×11) span two Z-adjacent cells and are placed at the same inset.
     * Large 2×2 rooms are currently deferred (TODO).
     */
    private static void placeRoomTemplate(World world, int gx, int gz,
                                          int wx, int wy, int wz,
                                          CastleLayout layout, PieceSet pieces,
                                          Random rng) {
        RoomTemplateConfig tmpl = layout.getRoomTemplate(gx, gz);
        if (tmpl == null) return;

        // Only place for the "top-left" cell of a merged room to avoid duplicate placement
        if (gx > 0 && layout.getRoomTemplate(gx - 1, gz) == tmpl) return;
        if (gz > 0 && layout.getRoomTemplate(gx, gz - 1) == tmpl) return;

        Structure roomNbt = pieces.getRoom(tmpl.structure());
        if (roomNbt == null) return;

        // Room templates sit at a 1-block inset from the cell's NW corner
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(StructureRotation.NONE)
                .setMirror(Mirror.NONE);
        placeStructure(roomNbt, world, wx + 1, wy, wz + 1, settings, rng);
    }

    // -----------------------------------------------------------------------
    // Mossy / cracked post-processing

    private static void applyMossyCracked(World world,
                                          int originX, int originY, int originZ,
                                          CastleLayout layout,
                                          double mossiness, double crackiness,
                                          Random rng) {
        int totalW = layout.totalWidthBlocks();
        int totalD = layout.totalDepthBlocks();
        int totalH = NUM_FLOORS * FLOOR_H + 5; // a bit above the top

        for (int dx = 0; dx < totalW; dx++) {
            for (int dy = 0; dy < totalH; dy++) {
                for (int dz = 0; dz < totalD; dz++) {
                    Block block = world.getBlockAt(originX + dx, originY + dy, originZ + dz);
                    Material mat = block.getType();

                    if (crackiness > 0 && rng.nextDouble() < crackiness) {
                        Material cracked = CRACKED.get(mat);
                        if (cracked != null) { block.setType(cracked, false); continue; }
                    }

                    if (mossiness > 0 && rng.nextDouble() < mossiness) {
                        Material mossy = MOSSY.get(mat);
                        if (mossy != null) block.setType(mossy, false);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utility

    private static void placeStructure(Structure structure, World world,
                                       int x, int y, int z,
                                       StructurePlaceSettings settings, Random rng) {
        if (structure == null) return;
        structure.place(new Location(world, x, y, z), true, settings, rng);
    }
}
