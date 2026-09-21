package version.v26_3.packets.handler;

import core.NetworkMode;
import core.config.Config;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim2D;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import version.v26_3.entity.EntityRegistry;
import version.v26_3.entity.version.PlayerTeleportReader;
import version.v26_3.entity.MobEntity;
import version.v26_3.entity.ObjectEntity;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.packets.handler.plugins.PluginChannelHandler;
import version.v26_3.protocol.Protocol;
import version.v26_3.proxy.ConnectionManager;
import version.v26_3.registries.RegistryManager;
import version.v26_3.schematic.CommandTreeInjector;
import version.v26_3.schematic.CreativeMode;
import version.v26_3.schematic.CreativeModeRegistry;
import version.v26_3.world.WorldManager;

import java.util.HashMap;
import java.util.Map;

import static version.v26_3.packets.builder.NetworkType.*;

/**
 * Handles client-bound game packets for the supported Minecraft versions (26.x).
 *
 * <p>This is the single (flattened) implementation, previously reached through the
 * {@code ClientBoundGamePacketHandler_1_14 -> _1_16 -> _1_17 -> _1_18 -> _1_19 -> _1_20_2 ->
 * _1_20_6} inheritance chain. The effective 26.x operators are registered directly in the
 * constructor. The class is non-final and {@link #getOperators()} returns a mutable map, so a
 * future Minecraft version can add a subclass overriding individual operators (see
 * docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class ClientBoundGamePacketHandler extends PacketHandler {
    private final HashMap<String, PacketOperator> operations = new HashMap<>();
    public ClientBoundGamePacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);

        WorldManager worldManager = WorldManager.getInstance();
        EntityRegistry entityRegistry = WorldManager.getInstance().getEntityRegistry();

        operations.put("SetEntityData", provider -> {
            entityRegistry.addMetadata(provider);
            return true;
        });

        operations.put("SetEquipment", provider -> {
            entityRegistry.addEquipment(provider);
            return true;
        });

        operations.put("AddMob", provider -> {
            entityRegistry.addEntity(provider, MobEntity::parse);
            return true;
        });

        operations.put("AddEntity", provider -> {
            entityRegistry.addEntity(provider, ObjectEntity::parse);
            return true;
        });

        operations.put("AddPlayer", provider -> {
            entityRegistry.addPlayer(provider);
            return true;
        });

        operations.put("RemoveEntities", provider -> {
            entityRegistry.destroyEntities(provider);
            return true;
        });

        operations.put("MoveEntityPos", provider -> {
            entityRegistry.updatePositionRelative(provider);
            return true;
        });
        operations.put("MoveEntityPosRot", provider -> {
            entityRegistry.updatePositionRelative(provider);
            return true;
        });
        operations.put("TeleportEntity", provider -> {
            entityRegistry.updatePositionAbsolute(provider);
            return true;
        });

        operations.put("MapItemData", provider -> {
            worldManager.getMapRegistry().readMap(provider);
            return true;
        });

        operations.put("Login", provider -> {
            PacketBuilder replacement = new PacketBuilder(((Protocol) Config.versionReporter().getProtocol()).clientBound("Login"));

            replacement.copy(provider, INT, BOOL); /* playerId, hardcore */

            int numLevels = provider.readVarInt();
            String[] levels = provider.readStringArray(numLevels);

            replacement.writeVarInt(numLevels);
            replacement.writeStringArray(levels);

            replacement.copy(provider, VARINT); /* maxPlayers */

            // extend view distance communicated to the client to the given value
            int viewDist = provider.readVarInt();
            replacement.writeVarInt(Math.max(viewDist, Config.getExtendedRenderDistance()));

            replacement.copy(provider, VARINT, BOOL, BOOL, BOOL); /* simulationDist, reducedDebug, showDeathScreen, limitedCrafting */

            commonInfo(provider, replacement);

            getConnectionManager().getEncryptionManager().sendImmediately(replacement);
            return false;
        });

        operations.put("Respawn", provider -> {
            commonInfo(provider, null);

            return true;
        });

        operations.put("LevelChunk", provider -> {
            try {
                worldManager.getChunkFactory().addChunk(provider);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return true;
        });

        // 1.18+ embeds light in the chunk packet itself
        operations.put("LevelChunkWithLight", provider -> {
            try {
                worldManager.getChunkFactory().addChunk(provider);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return true;
        });

        operations.put("LightUpdate", provider -> {
            worldManager.updateLight(provider);

            return true;
        });

        operations.put("BlockUpdate", provider -> {
            WorldManager.getInstance().blockChange(provider);
            return true;
        });
        operations.put("SectionBlocksUpdate", provider -> {
            Coordinate3D pos = provider.readSectionCoordinates();
            WorldManager.getInstance().multiBlockChange(pos, provider);

            return true;
        });

        operations.put("ForgetLevelChunk", provider -> {
            CoordinateDim2D co = new CoordinateDim2D(provider.readInt(), provider.readInt(), WorldManager.getInstance().getDimension());
            worldManager.unloadChunk(co);
            return worldManager.canForget(co);
        });

        operations.put("BlockEntityData", provider -> {
            Coordinate3D position = provider.readCoordinates();
            int type = provider.readVarInt();
            SpecificTag entityData = provider.readNbtTag();

            if (entityData instanceof CompoundTag entity) {
                entity.add("id", new StringTag(RegistryManager.getInstance().getBlockEntityRegistry().getBlockEntityName(type)));
            }
            worldManager.getChunkFactory().updateTileEntity(position, entityData);
            return true;
        });

        operations.put("OpenScreen", provider -> {
            int windowId = provider.readNext();

            int windowType = provider.readVarInt();
            String windowTitle = provider.readChat();

            WorldManager.getInstance().getContainerManager().openWindow(windowId, windowType, windowTitle);
            return true;
        });
        operations.put("ContainerClose", provider -> {
            final byte windowId = provider.readNext();
            worldManager.getContainerManager().closeWindow(windowId);
            worldManager.getVillagerManager().closeWindow(windowId);
            return true;
        });

        operations.put("ContainerSetContent", provider -> {
            int windowId = provider.readNext();

            int stateId = provider.readVarInt();
            int count = provider.readVarInt();
            WorldManager.getInstance().getContainerManager().items(windowId, count, provider);

            return true;
        });

        operations.put("MerchantOffers", provider -> {
            worldManager.getEntityRegistry().addVillagerTrades(provider);
            return true;
        });

        operations.put("CustomPayload", provider -> {
            PluginChannelHandler.getInstance().handleCustomPayload(provider);
            return true;
        });

        operations.put("SetChunkCacheRadius", provider -> {
            int dist = provider.readVarInt();

            return dist > Config.getExtendedRenderDistance();
        });

        operations.put("DeclareCommands", provider -> {
            return new CommandTreeInjector().process(provider);
        });

        operations.put("PlayerInfoUpdate", provider -> {
            entityRegistry.updatePlayerAction(provider);
            return true;
        });

        operations.put("StartConfiguration", dataTypeProvider -> {
            getConnectionManager().setMode(NetworkMode.CONFIGURATION);
            return true;
        });

        // Track the player's game mode from the server's GameEvent packets so CreativeMode
        // can restore it when fly mode is disabled. While fly mode is active, block the
        // server's gamemode-change events so the client stays in spectator and can noclip.
        operations.put("GameEvent", provider -> {
            int event = provider.readNext();
            float value = provider.readFloat();
            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode != null) {
                creativeMode.onServerGameEvent(event, value);
                // Block server from changing our gamemode while we're in fly mode
                if (event == 3 && creativeMode.shouldInterceptMovement()) {
                    return false; // don't forward to client
                }
            }
            return true;
        });

        // While in fly mode, swallow the server's position synchronization packets
        // so the client doesn't get teleported back. Send AcceptTeleportation back so the
        // server doesn't kick the player for not acknowledging the teleport.
        operations.put("PlayerPosition", provider -> {
            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode == null || !creativeMode.shouldInterceptMovement()) {
                return true; // forward to client normally
            }
            // Read the teleport ID and the resolved target position so we can acknowledge it,
            // then drop the packet. 26.3 format: teleportId (VarInt), position Vec3 (3×Double),
            // deltaMovement Vec3 (3×Double), yRot/xRot (2×Float), relatives (Int bitmask).
            // The server requires the resolved position echoed back in AcceptTeleportation.
            WorldManager world = WorldManager.getInstance();
            var pos = world.getPlayerPositionDouble();
            PlayerTeleportReader.TeleportTarget target = PlayerTeleportReader.read(
                provider, pos.getX(), pos.getY(), pos.getZ(), (float) world.getPlayerRotation(), 0.0f);
            creativeMode.sendAcceptTeleportation(target);
            return false; // don't forward to client
        });
    }

    /**
     * Read the trailing dimension type/name from a Login or Respawn packet, set the active
     * dimension + dimension type on the world manager, and (when {@code replacement != null})
     * copy them plus the remainder of the packet into the rebuilt Login packet.
     */
    private void commonInfo(DataTypeProvider provider, PacketBuilder replacement) {
        int dimensionType = provider.readVarInt();
        String dimensionName = provider.readString();

        WorldManager world = WorldManager.getInstance();
        world.setDimension(world.getDimensionRegistry().getDimension(dimensionName));
        world.setDimensionType(world.getDimensionRegistry().getDimensionType(dimensionType));

        if (replacement != null) {
            replacement.writeVarInt(dimensionType);
            replacement.writeString(dimensionName);

            // After dimensionName: hashedSeed (Long), gamemode (VarInt), previousGamemode (VarInt),
            // then the rest. Read gamemode explicitly so we can track it for fly-mode restoration.
            replacement.copy(provider, LONG); // hashedSeed
            int gamemode = provider.readVarInt();
            int previousGamemode = provider.readVarInt();
            replacement.writeVarInt(gamemode);
            replacement.writeVarInt(previousGamemode);

            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode != null) {
                creativeMode.onInitialGameMode((byte) gamemode);
            }

            replacement.copyRemainder(provider);
        } else {
            // Respawn: read the same fields to track gamemode, but let the packet
            // pass through to the client unchanged (caller returns true).
            provider.readLong(); // hashedSeed
            int gamemode = provider.readVarInt();
            provider.readVarInt(); // previousGamemode

            CreativeMode creativeMode = CreativeModeRegistry.get();
            if (creativeMode != null) {
                creativeMode.onInitialGameMode((byte) gamemode);
            }
        }
    }

    public static PacketHandler of(ConnectionManager connectionManager) {
        return new ClientBoundGamePacketHandler(connectionManager);
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
