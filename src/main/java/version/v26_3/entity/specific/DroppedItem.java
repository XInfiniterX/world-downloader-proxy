package version.v26_3.entity.specific;

import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.ShortTag;
import version.v26_3.container.Slot;
import version.v26_3.entity.ObjectEntity;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;

import java.util.function.Consumer;

/**
 * Handle dropped items
 */
public class DroppedItem extends ObjectEntity {
    private ItemMetaData metaData;

    public DroppedItem() {
        super();
    }

    /**
     * Add additional fields needed for dropped items.
     */
    @Override
    protected void addNbtData(CompoundTag root) {
        super.addNbtData(root);

        // Age in ticks; 36000 = 30 minutes before despawn.
        root.add("Age", new ShortTag((short) 36000));
        root.add("Health", new ShortTag((short) 5));

        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    @Override
    public void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = new ItemMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }

    private static class ItemMetaData extends MetaData {
        Slot item;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            if (item != null) {
                nbt.add("Item", item.toNbt());
            }
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            switch (i) {
                case 8: return provider -> item = provider.readSlot();
            }
            return super.getIndexHandler(i);
        }
    }
}