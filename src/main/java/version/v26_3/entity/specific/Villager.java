package version.v26_3.entity.specific;

import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim3D;
import se.llbit.nbt.*;
import version.v26_3.entity.MobEntity;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;
import version.v26_3.registries.RegistryManager;
import version.v26_3.villagers.VillagerTrade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handle villagers because they have interesting metadata.
 */
public class Villager extends MobEntity {
    private VillagerMetaData metaData;
    private List<VillagerTrade> trades;
    private int villagerLevel;
    private int villagerExp;

    private Consumer<CoordinateDim3D> onTrade;

    /**
     * Add additional fields needed for villagers.
     */
    @Override
    protected void addNbtData(CompoundTag root) {
        super.addNbtData(root);

        if (metaData != null) {
            metaData.addNbtTags(root);
        }

        addTradeNbtTags(root);
    }

    @Override
    public void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = new VillagerMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }

    public void registerOnTradeUpdate(Consumer<CoordinateDim3D> handler) {
        this.onTrade = handler;
    }

    public void updateTrades(List<VillagerTrade> trades, int villagerLevel, int villagerExp, CoordinateDim3D lastInteractedLocation) {
        this.trades = trades;
        this.villagerLevel = villagerLevel;
        this.villagerExp = villagerExp;
        if (this.onTrade != null) {
            onTrade.accept(lastInteractedLocation);
        }
    }

    private void addTradeNbtTags(CompoundTag root) {
        if (trades == null || trades.isEmpty()) {
            return;
        }

        List<CompoundTag> tradeOptions = new ArrayList<>();
        for (VillagerTrade trade : trades) {
            CompoundTag tradeOption = new CompoundTag();

            tradeOption.add("buy", trade.firstItem().toNbt());
            if (trade.secondItem() != null) {
                tradeOption.add("buy2", trade.secondItem().toNbt());
            }
            tradeOption.add("sell", trade.sellingItem().toNbt());
            tradeOption.add("demand", new IntTag(trade.demand()));
            tradeOption.add("maxUses", new IntTag(trade.maxUses()));
            tradeOption.add("priceMultiplier", new FloatTag(trade.priceMultiplier()));
            tradeOption.add("specialPrice", new IntTag(trade.specialPrice()));
            tradeOption.add("uses", new IntTag(trade.uses()));
            tradeOption.add("xp", new IntTag(trade.xp()));

            tradeOptions.add(tradeOption);
        }

        CompoundTag offers = new CompoundTag();
        offers.add("Recipes", new ListTag(Tag.TAG_COMPOUND, tradeOptions));
        root.add("Offers", offers);

        root.add("Xp", new IntTag(villagerExp));
    }

    public UUID getUUID() {
        return this.uuid;
    }

    public Coordinate3D getCoordinate3D() {
        return new Coordinate3D(x, y, z);
    }

    private static class VillagerMetaData extends MetaData {

        boolean noAI;
        int headShakeTimer;
        int type;
        int profession;
        int level;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);

            nbt.add("NoAI", new ByteTag(noAI ? 1 : 0));

            CompoundTag villagerData = new CompoundTag();

            String typeName = RegistryManager.getInstance().getVillagerTypeRegistry().getType(type);
            String professionName = RegistryManager.getInstance().getVillagerProfessionRegistry().getProfession(profession);
            villagerData.add("type", new StringTag(typeName));
            villagerData.add("profession", new StringTag(professionName));
            villagerData.add("level", new IntTag(level));

            nbt.add("VillagerData", villagerData);
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 15 -> provider -> noAI = (provider.readNext() & 0x01) > 0;
                case 17 -> provider -> headShakeTimer = provider.readVarInt();
                case 18 -> provider -> {
                    type = provider.readVarInt();
                    profession = provider.readVarInt();
                    level = provider.readVarInt();
                };
                default -> super.getIndexHandler(i);
            };
        }
    }
}