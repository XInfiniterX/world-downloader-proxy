package version.v26_3.proxy;

import core.NetworkMode;
import core.config.Config;
import core.interfaces.IConnectionManager;
import core.proxy.ProxyServer;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.DataReader;
import version.v26_3.packets.handler.*;
import version.v26_3.protocol.ConfigurationProtocol;
import version.v26_3.protocol.HandshakeProtocol;
import version.v26_3.protocol.LoginProtocol;
import version.v26_3.protocol.StatusProtocol;
import version.v26_3.world.WorldManager;

/**
 * Class to manage the connection status.
 */
public class ConnectionManager implements IConnectionManager {
    private DataReader serverBoundDataReader;
    private DataReader clientBoundDataReader;
    private EncryptionManager encryptionManager;
    private CompressionManager compressionManager;

    private NetworkMode mode = NetworkMode.STATUS;

    // Tracks the currently active game handler so its background resources (the selection
    // particle renderer's scheduler) can be shut down before it's discarded. Without this, an
    // abrupt disconnect (e.g. a client crash) never sends "ConfigurationAcknowledged", so the
    // old handler's scheduler thread keeps running and injects stale LevelParticles packets into
    // whatever connection is active next — including before that connection reaches the Play
    // state, where the client rejects them as an unknown packet.
    private ServerBoundGamePacketHandler serverBoundGamePacketHandler;

    public NetworkMode getMode() {
        return mode;
    }

    public void setMode(NetworkMode mode) {
        this.mode = mode;

        if (serverBoundGamePacketHandler != null) {
            serverBoundGamePacketHandler.shutdown();
            serverBoundGamePacketHandler = null;
        }

        switch (mode) {
            case STATUS:
                PacketHandler.setProtocol(new StatusProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundStatusPacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundStatusPacketHandler(this));
                break;
            case LOGIN:
                PacketHandler.setProtocol(new LoginProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundLoginPacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundLoginPacketHandler(this));
                break;
            case GAME:
                PacketHandler.setProtocol(VersionAccessors.protocol());
                serverBoundGamePacketHandler = new ServerBoundGamePacketHandler(this);
                serverBoundDataReader.setPacketHandler(serverBoundGamePacketHandler);
                clientBoundDataReader.setPacketHandler(ClientBoundGamePacketHandler.of(this));
                break;
            case HANDSHAKE:
                PacketHandler.setProtocol(new HandshakeProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundHandshakePacketHandler(this));
                clientBoundDataReader.setPacketHandler(new ClientBoundHandshakePacketHandler(this));
                break;
            case CONFIGURATION:
                PacketHandler.setProtocol(new ConfigurationProtocol());
                serverBoundDataReader.setPacketHandler(new ServerBoundConfigurationPacketHandler(this));
                clientBoundDataReader.setPacketHandler(ClientBoundConfigurationPacketHandler.of(this));
                break;
        }
    }

    /**
     * Starts the proxy.
     */
    public void startProxy() {
        compressionManager = new CompressionManager();
        encryptionManager = new EncryptionManager(compressionManager);
        serverBoundDataReader = DataReader.serverBound(encryptionManager);
        clientBoundDataReader = DataReader.clientBound(encryptionManager);

        setMode(NetworkMode.HANDSHAKE);

        ProxyServer proxy = new ProxyServer(this, Config.getConnectionDetails());
        proxy.runServer(serverBoundDataReader, clientBoundDataReader);

        Config.registerPacketInjector(this.getEncryptionManager().getPacketInjector());
        Config.registerEncryptionManager(this.getEncryptionManager());
    }

    /**
     * Reset the connection when its lost.
     */
    public void reset() {
        encryptionManager.reset();
        compressionManager.reset();
        serverBoundDataReader.reset();
        clientBoundDataReader.reset();
        setMode(NetworkMode.HANDSHAKE);
        WorldManager.getInstance().resetConnection();
    }

    public EncryptionManager getEncryptionManager() {
        return encryptionManager;
    }

    public CompressionManager getCompressionManager() {
        return compressionManager;
    }
}
