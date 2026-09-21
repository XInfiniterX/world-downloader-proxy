package version.v26_3.commandblock;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim3D;
import version.v26_3.chunk.Chunk;
import version.v26_3.chunk.ChunkEntities;
import version.v26_3.chunk.palette.BlockState;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.Chat;
import version.v26_3.packets.builder.MessageTarget;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.world.WorldManager;

import java.util.HashMap;
import java.util.Map;

public class CommandBlockManager {

    private final Map<CoordinateDim3D, CommandBlock> storedCommandBlocks;

    public CommandBlockManager() {
        storedCommandBlocks = new HashMap<>();
    }

    public void readAndStoreCommandBlock(DataTypeProvider provider) {
        final Coordinate3D coords = provider.readCoordinates();
        final String command = provider.readString();

        // Mode (0 = chain, 1 = repeating, 2 = impulse)
        final int mode = provider.readVarInt();
        final byte flags = provider.readNext();
        
        CommandBlock commandblock = new CommandBlock(coords, command, mode, flags);
        storedCommandBlocks.put(coords.addDimension3D(WorldManager.getInstance().getDimension()), commandblock);

        Chunk c = WorldManager.getInstance().getChunk(coords.globalToChunk().addDimension(WorldManager.getInstance().getDimension()));
        if (c != null) {
            BlockState block = c.getBlockStateAt(coords.withinChunk());
            if (block != null) {
                c.addCommandBlock(commandblock);
                sendCommandBlockMessage(commandblock);
            } else {
                sendCommandBlockFailureMessage(commandblock, "Block not found.");
            }
        } else {
            sendCommandBlockFailureMessage(commandblock, "Chunk not loaded.");
        }
    }
    
    public void loadPreviousCommandBlockAt(ChunkEntities chunk, CoordinateDim3D location) {
        if (storedCommandBlocks.containsKey(location)) {
            chunk.addCommandBlock(storedCommandBlocks.get(location));
        }
    }

    private void sendCommandBlockFailureMessage(CommandBlock commandblock, String cause) {
        if (Config.sendInfoMessages()) {
            Chat message = new Chat("Unable to save command block at " + commandblock.getLocation() + ". " + cause);
            message.setColor("red");

            VersionAccessors.injector().enqueuePacket(PacketBuilder.constructClientMessage(message, MessageTarget.GAMEINFO));
        }
    }
    
    private void sendCommandBlockMessage(CommandBlock commandblock) {
        if (Config.sendInfoMessages()) {
            String message = "Recorded command block at " + commandblock.getLocation();
            VersionAccessors.injector().enqueuePacket(PacketBuilder.constructClientMessage(message, MessageTarget.GAMEINFO));
        }
    }
}
