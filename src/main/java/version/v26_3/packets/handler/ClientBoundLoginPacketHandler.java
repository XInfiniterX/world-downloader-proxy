package version.v26_3.packets.handler;

import core.messages.Messages;
import version.v26_3.proxy.ConnectionManager;

import java.util.HashMap;
import java.util.Map;

public class ClientBoundLoginPacketHandler extends PacketHandler {
    private HashMap<String, PacketOperator> operations = new HashMap<>();
    public ClientBoundLoginPacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        operations.put("LoginDisconnect", provider -> {
            String reason = provider.readString();
            System.out.println(Messages.console("console.packet.disconnect", reason));
            return true;
        });

        operations.put("Hello", provider -> {
            String serverId = provider.readString();
            byte[] pubKey = provider.readByteArray(provider.readVarInt());
            byte[] nonce = provider.readByteArray(provider.readVarInt());

            // 1.20.6+ includes an explicit "should authenticate" boolean; that's the only layout
            // used by the supported versions (26.x).
            boolean shouldAuthenticate = provider.readBoolean();
            getConnectionManager().getEncryptionManager().setServerEncryptionRequest(pubKey, nonce, serverId, shouldAuthenticate);

            return false;
        });
        operations.put("GameProfile", provider -> {
            // 1.16+ sends the UUID as a raw UUID rather than a string; that's the only layout used
            // by the supported versions (26.x).
            String uuid = provider.readUUID().toString();

            String username = provider.readString();

            // 26.2+ adds a Session ID UUID field to Login Success.
            provider.readUUID();

            System.out.println(Messages.console("console.auth.login_success", username, uuid));

            // 1.20.2+ transitions through a Configuration phase before Game, so we don't switch
            // to GAME here; that's the only path used by the supported versions (26.x).
            return true;
        });
        operations.put("LoginCompression", provider -> {
            int limit = provider.readVarInt();
            getConnectionManager().getCompressionManager().enableCompression(limit);
            return true;
        });

    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return operations;
    }

    @Override
    public boolean isClientBound() {
        return true;
    }
}
