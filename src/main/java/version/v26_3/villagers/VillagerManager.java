package version.v26_3.villagers;

import core.config.Config;
import core.coordinates.CoordinateDim3D;
import version.v26_3.entity.EntityRegistry;
import version.v26_3.entity.IMovableEntity;
import version.v26_3.entity.specific.Villager;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;
import version.v26_3.packets.builder.Chat;
import version.v26_3.packets.builder.MessageTarget;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.world.WorldManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("MismatchedCollectionQueryUpdate")
public class VillagerManager {

    private final Map<Integer, VillagerData> knownTrades = new HashMap<>();
    private final Map<UUID, VillagerData> storedVillager = new HashMap<>();

    private Villager lastInteractedWith;
    private CoordinateDim3D lastInteractedLocation;

    public void lastInteractedWith(DataTypeProvider provider) {
        EntityRegistry registry = WorldManager.getInstance().getEntityRegistry();
        IMovableEntity entity = registry.getMovableEntity(provider.readVarInt());

        if (!(entity instanceof Villager)) {
            return;
        }

        int interactionType = provider.readVarInt();
        if (interactionType == InteractionType.INTERACT_AT.index) {
            lastInteractedWith = (Villager) entity;
            lastInteractedLocation = lastInteractedWith.getCoordinate3D().addDimension3D(WorldManager.getInstance().getDimension());
        }
    }
    
    public void parseAndStoreVillagerTrade(DataTypeProvider provider) {
        // Villager trades cannot be saved since readSlot is broken in 1.20.2+ due to the
        // new item components. This is a no-op for all supported versions (26.x).
        return;
    }

    public void closeWindow(int windowId) {
        if (!knownTrades.containsKey(windowId)) {
            return;
        }
        final VillagerData villagerData = knownTrades.remove(windowId);
        storedVillager.put(lastInteractedWith.getUUID(), villagerData);

        if (Config.sendInfoMessages()) {
            if (villagerData.trades().size() > 0) {
                String message = "Stored villager trade at " + lastInteractedLocation + ", with " + villagerData.trades.size() + " trade(s)";
                VersionAccessors.injector().enqueuePacket(PacketBuilder.constructClientMessage(message, MessageTarget.GAMEINFO));
            } else {
                Chat message = new Chat("No villager trade at " + lastInteractedLocation);
                message.setColor("red");
                VersionAccessors.injector().enqueuePacket(PacketBuilder.constructClientMessage(message, MessageTarget.GAMEINFO));
            }
        }
    }

    public void loadPreviousTradeAt(Villager villager) {
        if (storedVillager.containsKey(villager.getUUID())){
            VillagerData data = storedVillager.get(villager.getUUID());
            villager.updateTrades(data.trades(), data.villagerLevel(), data.villagerExp(), data.lastLocation());
        }
    }

    private enum InteractionType {
        INTERACT(0), ATTACK(1), INTERACT_AT(2);

        final int index;

        InteractionType(int type) {
            this.index = type;
        }
    }

    private record VillagerData(List<VillagerTrade> trades,
                                int villagerLevel,
                                int villagerExp,
                                CoordinateDim3D lastLocation) {
    }

}


