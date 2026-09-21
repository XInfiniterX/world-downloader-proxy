package version.v26_3.packets.handler.plugins;

import version.v26_3.packets.DataTypeProvider;

public abstract class PluginChannelHandler {

    private static PluginChannelHandler instance;

    public static PluginChannelHandler getInstance() {
        if (instance == null) {
            // The 1.12 Forge plugin-channel handler is not used by the supported versions (26.x).
            // If Forge/modded-server support is reintroduced for a future version, add a version
            // check here (see docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
            instance = new DefaultPluginChannelHandler();
        }
        return instance;
    }

    public abstract void handleCustomPayload(DataTypeProvider provider);
}

class DefaultPluginChannelHandler extends PluginChannelHandler {
    @Override
    public void handleCustomPayload(DataTypeProvider provider) { }
}
