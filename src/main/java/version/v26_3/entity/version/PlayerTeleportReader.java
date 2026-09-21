package version.v26_3.entity.version;

import version.v26_3.packets.DataTypeProvider;

/**
 * Reads the 26.3 ClientboundPlayerPosition packet: VarInt teleportId, a PositionMoveRotation
 * (position Vec3, delta Vec3, yRot, xRot) and an Int bitmask of Relative flags, then resolves
 * the absolute target position/rotation. The server expects the resolved values echoed back in
 * ServerboundAcceptTeleportation, which in 26.3 carries id + x/y/z + yRot/xRot.
 */
public final class PlayerTeleportReader {
    private static final int REL_X = 0;
    private static final int REL_Y = 1;
    private static final int REL_Z = 2;
    private static final int REL_Y_ROT = 3;
    private static final int REL_X_ROT = 4;

    private PlayerTeleportReader() { }

    public record TeleportTarget(int teleportId, double x, double y, double z, float yRot, float xRot) { }

    public static TeleportTarget read(DataTypeProvider provider, double curX, double curY, double curZ, float curYRot, float curXRot) {
        int teleportId = provider.readVarInt();

        double x = provider.readDouble();
        double y = provider.readDouble();
        double z = provider.readDouble();
        provider.readDouble(); // deltaMovement x
        provider.readDouble(); // deltaMovement y
        provider.readDouble(); // deltaMovement z
        float yRot = provider.readFloat();
        float xRot = provider.readFloat();
        int relatives = provider.readInt();

        if ((relatives & (1 << REL_X)) != 0) x += curX;
        if ((relatives & (1 << REL_Y)) != 0) y += curY;
        if ((relatives & (1 << REL_Z)) != 0) z += curZ;
        if ((relatives & (1 << REL_Y_ROT)) != 0) yRot += curYRot;
        if ((relatives & (1 << REL_X_ROT)) != 0) xRot += curXRot;

        return new TeleportTarget(teleportId, x, y, z, yRot, xRot);
    }
}
