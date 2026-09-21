package version.v26_3.packets.builder;

/**
 * Builds {@code LevelParticles} packets for a single particle at an exact position.
 * 26.3 layout: VarInt particleId first, overrideLimiter/alwaysShow flags, Double position,
 * 3 Float offsets, 3 Float max speeds, VarInt count, VarInt randomization type.
 */
public final class LevelParticlesBuilder {
    private LevelParticlesBuilder() { }

    /**
     * Build a LevelParticles packet for a single particle with no extra data payload
     * (e.g. flame) at an exact position.
     *
     * @param particleId the numeric particle ID (from the server registry or fallback)
     * @param x          exact world X
     * @param y          exact world Y
     * @param z          exact world Z
     */
    public static PacketBuilder buildSingle(int particleId, double x, double y, double z) {
        PacketBuilder packet = new PacketBuilder("LevelParticles");
        packet.writeVarInt(particleId);
        packet.writeBoolean(true);
        packet.writeBoolean(false);
        packet.writeDouble(x);
        packet.writeDouble(y);
        packet.writeDouble(z);
        for (int i = 0; i < 6; i++) {
            packet.writeFloat(0f);
        }
        packet.writeVarInt(0);
        packet.writeVarInt(0);
        return packet;
    }
}
