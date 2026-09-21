package version.v26_3.packets.handler;

import version.v26_3.proxy.ConnectionManager;

import java.util.HashMap;
import java.util.Map;

public class ClientBoundHandshakePacketHandler extends PacketHandler {
    public ClientBoundHandshakePacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);
    }

    @Override
    public Map<String, PacketOperator> getOperators() {
        return new HashMap<>();
    }

    @Override
    public boolean isClientBound() {
        return true;
    }
}
