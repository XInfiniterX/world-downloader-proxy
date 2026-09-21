package version.v26_3.packets.builder;

/**
 * Builds {@code GameEvent} packets. Knows the packet layout (event id byte + value float)
 * but does not know what game events mean or who they are sent to.
 */
public final class GameEventBuilder {
    /** GameEvent event id for "change game mode". */
    public static final int EVENT_CHANGE_GAMEMODE = 3;
    /** GameEvent event id for "win game" (used for credits/respawn). */
    public static final int EVENT_WIN_GAME = 0;
    /** GameEvent event id for "demo event". */
    public static final int EVENT_DEMO_EVENT = 5;

    /** Game mode values: 0=survival, 1=creative, 2=adventure, 3=spectator. */
    public static final int GAMEMODE_SURVIVAL = 0;
    public static final int GAMEMODE_CREATIVE = 1;
    public static final int GAMEMODE_ADVENTURE = 2;
    public static final int GAMEMODE_SPECTATOR = 3;

    private GameEventBuilder() { }

    /**
     * Build a GameEvent packet with the given event id and float value.
     */
    public static PacketBuilder build(int eventId, float value) {
        PacketBuilder pb = new PacketBuilder("GameEvent");
        pb.writeByte((byte) eventId);
        pb.writeFloat(value);
        return pb;
    }
}
