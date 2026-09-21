package version.v26_3.packets.handler;

import core.NetworkMode;
import version.v26_3.proxy.ConnectionManager;

import java.util.HashMap;
import java.util.Map;

import static core.util.PrintUtils.devPrint;

public class ServerBoundLoginPacketHandler extends PacketHandler {
    private HashMap<String, PacketOperator> operations = new HashMap<>();
    public ServerBoundLoginPacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        operations.put("Hello", provider -> {
            String username = provider.readString();

            devPrint("Login by: " + username);

            getConnectionManager().getEncryptionManager().setUsername(username);
            return true;
        });

        operations.put("Key", provider -> {
            int sharedSecretLength = provider.readVarInt();
            byte[] sharedSecret = provider.readByteArray(sharedSecretLength);
            byte[] nonce = provider.readByteArray(provider.readVarInt());
            getConnectionManager().getEncryptionManager().setClientEncryptionConfirmation(sharedSecret, nonce);

            return false;
        });

        operations.put("LoginAcknowledged", provider -> {
            // 1.20.2+ transitions through a Configuration phase before Game; that's the only
            // path used by the supported versions (26.x).
            getConnectionManager().setMode(NetworkMode.CONFIGURATION);
            return true;
        });
    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return operations;
    }

    @Override
    public boolean isClientBound() {
        return false;
    }
}
