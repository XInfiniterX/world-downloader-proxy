package version.v26_3.packets.builder;

import version.v26_3.packets.UUID;

/**
 * Builds {@code BossEvent} (boss bar) packets. 26.x serialises the title field as NBT.
 */
public final class BossBarBuilder {
    /** BossBar action: ADD = 0, REMOVE = 1, UPDATE_HEALTH = 2, UPDATE_TITLE = 3 */
    public static final int ACTION_ADD = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_UPDATE_TITLE = 3;

    /** BossBar color: PINK=0, BLUE=1, RED=2, GREEN=3, YELLOW=4, PURPLE=5, WHITE=6 */
    public static final int COLOR_YELLOW = 4;
    /** BossBar division: NONE=0 */
    public static final int DIVISION_NONE = 0;

    private BossBarBuilder() { }

    /**
     * Build a BossEvent ADD packet: shows a new boss bar with the given title.
     */
    public static PacketBuilder buildAdd(UUID uuid, String title, int color, int division, byte flags) {
        PacketBuilder pb = new PacketBuilder("BossEvent");
        pb.writeUUID(uuid);
        pb.writeVarInt(ACTION_ADD);
        writeTitle(pb, title);
        pb.writeFloat(1.0f);          // health (0..1) — full bar
        pb.writeVarInt(color);
        pb.writeVarInt(division);
        pb.writeByte(flags);
        return pb;
    }

    /**
     * Build a BossEvent REMOVE packet: removes the boss bar with the given UUID.
     */
    public static PacketBuilder buildRemove(UUID uuid) {
        PacketBuilder pb = new PacketBuilder("BossEvent");
        pb.writeUUID(uuid);
        pb.writeVarInt(ACTION_REMOVE);
        return pb;
    }

    /**
     * Build a BossEvent UPDATE_TITLE packet: changes the title of an existing boss bar.
     */
    public static PacketBuilder buildUpdateTitle(UUID uuid, String title) {
        PacketBuilder pb = new PacketBuilder("BossEvent");
        pb.writeUUID(uuid);
        pb.writeVarInt(ACTION_UPDATE_TITLE);
        writeTitle(pb, title);
        return pb;
    }

    private static void writeTitle(PacketBuilder pb, String title) {
        Chat chat = new Chat(title);
        pb.writeNbtDirect(chat.toNbt());
    }
}
