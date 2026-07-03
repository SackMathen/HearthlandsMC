package net.puffish.castledungeons.generation;

/**
 * Stores the node type and inter-cell connection type for a W×D castle grid.
 * <p>
 * Nodes occupy positions (gx, gz) where gx ∈ [0, width) and gz ∈ [0, depth).
 * Connections are stored directionally: a connection between (gx,gz)→EAST is
 * the same physical gap as (gx+1,gz)→WEST.
 */
public class CastleGrid {

    private final int width;
    private final int depth;
    private final CastleNodeType[] nodes;

    // Connections from each node in each of 4 directions; indexed [gz*width*4 + gx*4 + dir.ordinal()]
    private final ConnectionType[] connections;

    public CastleGrid(int width, int depth) {
        this.width = width;
        this.depth = depth;
        this.nodes = new CastleNodeType[width * depth];
        this.connections = new ConnectionType[width * depth * 4];

        for (int i = 0; i < nodes.length; i++) nodes[i] = CastleNodeType.EMPTY;
        for (int i = 0; i < connections.length; i++) connections[i] = ConnectionType.NONE;
    }

    public int getWidth()  { return width; }
    public int getDepth()  { return depth; }

    public CastleNodeType getNode(int gx, int gz) {
        return nodes[gz * width + gx];
    }

    public void setNode(int gx, int gz, CastleNodeType type) {
        nodes[gz * width + gx] = type;
    }

    public ConnectionType getConnection(int gx, int gz, Direction4 dir) {
        return connections[(gz * width + gx) * 4 + dir.ordinal()];
    }

    /** Sets both directions of the connection symmetrically. */
    public void setConnection(int gx, int gz, Direction4 dir, ConnectionType type) {
        connections[(gz * width + gx) * 4 + dir.ordinal()] = type;
        int nx = gx + dir.dx;
        int nz = gz + dir.dz;
        if (nx >= 0 && nx < width && nz >= 0 && nz < depth) {
            connections[(nz * width + nx) * 4 + dir.opposite().ordinal()] = type;
        }
    }

    public boolean inBounds(int gx, int gz) {
        return gx >= 0 && gx < width && gz >= 0 && gz < depth;
    }
}
