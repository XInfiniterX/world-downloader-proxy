package version.v26_3.module;

import core.config.Config;
import version.v26_3.protocol.Protocol;
import version.v26_3.proxy.EncryptionManager;
import version.v26_3.proxy.PacketInjector;

/**
 * Typed accessors for the per-version singletons that {@link core.config.Config} stores
 * opaquely as {@code Object}. Per-version code calls these instead of casting
 * {@code Config.getPacketInjector()} / {@code Config.getEncryptionManager()} /
 * {@code Config.getGameProtocol()} at every call site.
 */
public final class VersionAccessors {
    private VersionAccessors() { }

    public static PacketInjector injector() {
        return (PacketInjector) Config.getPacketInjector();
    }

    public static EncryptionManager encryptionManager() {
        return (EncryptionManager) Config.getEncryptionManager();
    }

    public static Protocol protocol() {
        return (Protocol) Config.getGameProtocol();
    }
}
