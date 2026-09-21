package version.v26_3.packets.handler;

import core.NetworkMode;
import core.coordinates.Coordinate3D;
import core.schematic.SelectionState;
import core.schematic.export.SchematicExportService;
import version.v26_3.proxy.ConnectionManager;
import version.v26_3.schematic.*;
import version.v26_3.world.WorldManager;

import java.util.HashMap;
import java.util.Map;

public class ServerBoundGamePacketHandler extends PacketHandler {
    private HashMap<String, PacketOperator> operations = new HashMap<>();

    // in-game schematic selection: one selection per connection, see SelectionState for why a
    // simple instance field (rather than a global singleton) is enough here.
    private final SelectionState selectionState = new SelectionState();
    private final SelectionFeedback selectionFeedback = new SelectionFeedback();
    private final SelectionInputInterceptor selectionInputInterceptor =
        new SelectionInputInterceptor(selectionState, selectionFeedback);
    private final CreativeMode creativeMode = new CreativeMode();
    private final SelectionCommandRouter selectionCommandRouter =
        new SelectionCommandRouter(selectionState, selectionFeedback, SchematicExportService.createDefault(selectionFeedback), creativeMode);
    private final SelectionTabCompleter selectionTabCompleter = new SelectionTabCompleter();
    private final SelectionParticleRenderer selectionParticleRenderer =
        new SelectionParticleRenderer(selectionState);

    public ServerBoundGamePacketHandler(ConnectionManager connectionManager) {
        super(connectionManager);
        CreativeModeRegistry.set(creativeMode);

        // wire up the particle renderer to start/stop when selection mode is toggled
        selectionCommandRouter.setOnSelectionModeChanged(() ->
            onSelectionModeChanged(selectionState.isEnabled()));

        PacketOperator updatePlayerPosition = provider -> {
            double x = provider.readDouble();
            double y = provider.readDouble();
            double z = provider.readDouble();

            WorldManager.getInstance().setPlayerPosition(x, y, z);

            // In spectator/fly mode, swallow the movement packet so the server
            // still thinks the player is at their original position.
            return !creativeMode.shouldInterceptMovement();
        };

        PacketOperator updatePlayerRotation = provider -> {
            double yaw = provider.readFloat() % 360;
            provider.readFloat(); // pitch
            provider.readBoolean(); // on ground
            WorldManager.getInstance().setPlayerRotation(yaw);
            return !creativeMode.shouldInterceptMovement();
        };

        operations.put("MovePlayerPos", updatePlayerPosition);
        operations.put("MovePlayerRot", updatePlayerRotation);
        operations.put("MovePlayerPosRot", (provider) -> {
            updatePlayerPosition.apply(provider);
            updatePlayerRotation.apply(provider);
            return !creativeMode.shouldInterceptMovement();
        });

        // 1.21.6+ (protocol 768+): "status only" packet — client sends this when it
        // starts/stops flying. Must be swallowed in fly mode or the server learns the
        // player is flying.
        operations.put("MovePlayerStatusOnly", provider -> {
            provider.readNext(); // flags byte (on_ground, pushing_against_wall)
            return !creativeMode.shouldInterceptMovement();
        });

        // PlayerCommand contains entity actions like "start flying", "stop flying",
        // "start sneaking", etc. Swallow it in fly mode so the server doesn't learn
        // the player toggled flight.
        operations.put("PlayerCommand", provider -> {
            provider.readVarInt(); // player entity ID
            provider.readVarInt(); // action id
            provider.readVarInt(); // data
            return !creativeMode.shouldInterceptMovement();
        });

        // PlayerAbilities (serverbound): the client sends this when it toggles flying.
        // Swallow it in fly mode so the server doesn't learn the player started flying.
        operations.put("PlayerAbilities", provider -> {
            provider.readNext(); // flags byte (bit 0x02 = isFlying)
            return !creativeMode.shouldInterceptMovement();
        });

        // PlayerInput (1.21.4+): carries W/A/S/D/jump/shift/sprint as bitflags.
        // Swallow it in fly mode so the server doesn't see the player pressing
        // space (jump) or shift (sneak) while flying locally.
        operations.put("PlayerInput", provider -> {
            provider.readNext(); // input flags byte
            return !creativeMode.shouldInterceptMovement();
        });

        operations.put("MoveVehicle", updatePlayerPosition);

        operations.put("UseItem", provider -> {
            // a VarInt with the hand is included first (1.14+; the only layout used by 26.x)
            provider.readVarInt();

            // Block right-click in air while in selection mode or fly mode so the
            // server doesn't see interactions (e.g. eating, throwing items).
            if (selectionState.isEnabled() || creativeMode.shouldInterceptMovement()) {
                return false;
            }
            return true;
        });

        operations.put("ContainerClose", provider -> {
            final byte windowId = provider.readNext();
            WorldManager.getInstance().getContainerManager().closeWindow(windowId);
            WorldManager.getInstance().getVillagerManager().closeWindow(windowId);
            return true;
        });

        // block placements
        operations.put("UseItemOn", provider -> {
            // while in-game schematic selection mode is active, this right-click sets pos2 instead
            // of being forwarded to the server; onUseItemOn() returns true (having read nothing)
            // when selection mode is off, so the logic below runs completely unaffected
            if (selectionState.isEnabled()) {
                if (!selectionInputInterceptor.onUseItemOn(provider)) {
                    return false;
                }
            }

            // Block block placement in fly mode (creative places blocks instantly)
            if (creativeMode.shouldInterceptMovement()) {
                provider.readVarInt();  // Hand
                provider.readCoordinates(); // position
                return false;
            }

            provider.readVarInt();  // Hand
            Coordinate3D coords = provider.readCoordinates();
            provider.readVarInt();  // Block face
            provider.readFloat();   // Cursor x
            provider.readFloat();   // Cursor y
            provider.readFloat();   // Cursor z
            provider.readBoolean(); // If the player's head is inside of a block

            WorldManager.getInstance().getContainerManager().lastInteractedWith(coords);
            return true;
        });

        // left-click/start-digging a block; while in-game schematic selection mode is active,
        // it sets pos1 instead of being forwarded to the server. Also block in fly mode so
        // the player doesn't destroy blocks while flying in creative — but still allow
        // pos1 selection in fly mode.
        operations.put("PlayerAction", provider -> {
            if (selectionState.isEnabled()) {
                return selectionInputInterceptor.onPlayerAction(provider);
            }
            if (creativeMode.shouldInterceptMovement()) {
                provider.readNext(); // status
                provider.readCoordinates(); // position
                return false;
            }
            return true;
        });

        // in-game schematic selection commands (see SelectionCommandRouter); any chat message or
        // command that isn't one of ours is forwarded to the server completely unmodified
        operations.put("Chat", provider -> !selectionCommandRouter.handle(provider.readString()));
        operations.put("ChatCommand", provider -> !selectionCommandRouter.handle(provider.readString()));
        operations.put("ChatCommandSigned", provider -> !selectionCommandRouter.handle(provider.readString()));

        // tab-completion for our commands: intercept the request, respond to the client directly,
        // and never forward the request to the server (so the server never sees that the player
        // is typing our command)
        operations.put("CommandSuggestion", provider -> {
            int transactionId = provider.readVarInt();
            String text = provider.readString();
            return !selectionTabCompleter.handle(transactionId, text);
        });

        operations.put("SetCommandBlock", provider -> {
            WorldManager.getInstance().getCommandBlockManager().readAndStoreCommandBlock(provider);
            return true;
        });

        operations.put("Interact", provider -> {
            // Block entity interactions while in selection mode or fly mode
            if (selectionState.isEnabled() || creativeMode.shouldInterceptMovement()) {
                provider.readVarInt(); // entity ID
                provider.readVarInt(); // action
                return false;
            }
            WorldManager.getInstance().getVillagerManager().lastInteractedWith(provider);
            return true;
        });

        // ConnectionManager.setMode() shuts down this handler's particle renderer before
        // discarding it, so no explicit shutdown() call is needed here.
        operations.put("ConfigurationAcknowledged", provider -> {
            getConnectionManager().setMode(NetworkMode.CONFIGURATION);
            return true;
        });
    }

    /**
     * Shuts down this handler's background resources (the particle renderer's scheduler).
     * Must be called whenever this handler is discarded — including on an abrupt connection
     * reset (e.g. a client crash) where no "ConfigurationAcknowledged" packet is ever received —
     * otherwise its scheduler thread keeps running and injects stale LevelParticles packets into
     * whatever connection is active next, which can arrive before that connection reaches the
     * Play state and get rejected by the client as an unknown packet.
     */
    public void shutdown() {
        selectionParticleRenderer.shutdown();
        creativeMode.disable();
        CreativeModeRegistry.clear();
    }

    /**
     * Called by {@link SelectionCommandRouter} (via the toggle handler) when selection mode
     * changes. Starts or stops the particle renderer accordingly.
     */
    public void onSelectionModeChanged(boolean enabled) {
        if (enabled) {
            selectionParticleRenderer.start();
        } else {
            selectionParticleRenderer.stop();
        }
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

