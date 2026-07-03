package net.puffish.castledungeons.generation;

public enum CastleNodeType {
    /** Not part of the castle (exterior or unfilled interior). */
    EMPTY,
    /** Full-height corner tower spanning all floors. */
    TOWER,
    /** Covered perimeter corridor — placed once per floor. */
    HALLWAY,
    /** Open battlement walkway — placed on the roof level only. */
    WALK,
    /** Interior room — placed once per floor. */
    ROOM
}
