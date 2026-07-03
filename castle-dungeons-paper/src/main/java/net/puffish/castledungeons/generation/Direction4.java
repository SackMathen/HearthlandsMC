package net.puffish.castledungeons.generation;

public enum Direction4 {
    NORTH(0, -1),
    SOUTH(0, 1),
    WEST(-1, 0),
    EAST(1, 0);

    public final int dx;
    public final int dz;

    Direction4(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Direction4 opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
