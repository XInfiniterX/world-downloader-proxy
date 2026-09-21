package version.v26_3.schematic;

import core.config.Config;
import version.v26_3.entity.version.PlayerTeleportReader;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.builder.GameEventBuilder;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.protocol.Protocol;
import version.v26_3.proxy.EncryptionManager;
import version.v26_3.proxy.PacketInjector;

/**
 * Local-only fly mode: tells the client to switch to creative (so the player can fly
 * freely to inspect high builds for schematic selection) while the server still thinks the
 * player is at their original position. Movement packets from the client are intercepted and
 * NOT forwarded to the server while this mode is active.
 *
 * <p>Toggle with {@code /world-downloader-proxy fly}.
 *
 * <p>Packet construction is delegated to {@link GameEventBuilder}; this class only manages
 * the fly-mode state and delivery.
 */
public class CreativeMode {
    private boolean active = false;
    private int savedGamemode = GameEventBuilder.GAMEMODE_SURVIVAL;

    public boolean isActive() {
        return active;
    }

    /**
     * Track the player's initial game mode from Login/Respawn packets so we can
     * restore it when fly mode is disabled. This is called before any GameEvent
     * change-gamemode packets, so the initial mode is captured correctly even if
     * the server never sends an explicit change event.
     */
    public void onInitialGameMode(byte gamemode) {
        // Only update if fly mode is not active — while flying we don't want the
        // server's gamemode changes to overwrite the saved original.
        if (!active) {
            savedGamemode = gamemode;
        }
    }

    /**
     * Track the player's current game mode from the server's GameEvent packets so we can
     * restore it when fly mode is disabled.
     */
    public void onServerGameEvent(int eventId, float value) {
        if (eventId == GameEventBuilder.EVENT_CHANGE_GAMEMODE) {
            // Only update saved gamemode if we're not in fly mode — otherwise this
            // is the server trying to change our gamemode, which we block.
            if (!active) {
                savedGamemode = (int) value;
            }
        }
    }

    /**
     * Toggle fly mode on/off.
     * @return true if now active, false if now disabled.
     */
    public boolean toggle() {
        if (active) {
            disable();
        } else {
            enable();
        }
        return active;
    }

    public void enable() {
        if (active) {
            return;
        }
        active = true;
        sendGameEvent(GameEventBuilder.EVENT_CHANGE_GAMEMODE, GameEventBuilder.GAMEMODE_CREATIVE);
    }

    public void disable() {
        if (!active) {
            return;
        }
        active = false;
        sendGameEvent(GameEventBuilder.EVENT_CHANGE_GAMEMODE, savedGamemode);
    }

    /**
     * @return true if the given serverbound packet should be swallowed (not forwarded to the
     *         server) because the player is flying locally and the server shouldn't see the movement.
     */
    public boolean shouldInterceptMovement() {
        return active;
    }

    private void sendGameEvent(int event, float value) {
        PacketBuilder pb = GameEventBuilder.build(event, value);
        EncryptionManager em = VersionAccessors.encryptionManager();
        if (em != null) {
            try {
                em.streamToClientDirect(pb);
                return;
            } catch (Exception e) {
                // fall through to injector
            }
        }
        PacketInjector injector = VersionAccessors.injector();
        if (injector != null) {
            injector.enqueuePacket(pb);
        }
    }

    /**
     * Send an AcceptTeleportation packet to the server so it doesn't kick the player
     * while we're swallowing its position sync packets. In 26.3 the packet echoes back
     * the resolved teleport target: id + x/y/z + yRot/xRot.
     */
    public void sendAcceptTeleportation(PlayerTeleportReader.TeleportTarget target) {
        EncryptionManager em = VersionAccessors.encryptionManager();
        if (em == null) {
            return;
        }
        int packetId = ((Protocol) Config.versionReporter().getProtocol()).serverBound("AcceptTeleportation");
        if (packetId < 0) {
            return;
        }
        PacketBuilder pb = new PacketBuilder(packetId);
        pb.writeVarInt(target.teleportId());
        pb.writeDouble(target.x());
        pb.writeDouble(target.y());
        pb.writeDouble(target.z());
        pb.writeFloat(target.yRot());
        pb.writeFloat(target.xRot());
        try {
            em.streamToServer(pb);
        } catch (Exception e) {
            // ignore — best effort
        }
    }
}
