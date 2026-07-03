package net.puffish.castledungeons.generation;

public enum ConnectionType {
    /** Solid wall — no passage between the two cells. */
    WALL,
    /** Doorway — wooden door frame between the two cells. */
    DOOR,
    /** Open passage — no door, just an opening. */
    PASSAGE,
    /** No connector piece needed (e.g. exterior face or tower-adjacent). */
    NONE
}
