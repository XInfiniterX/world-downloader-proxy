package version.v26_3.container;

import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntTag;
import se.llbit.nbt.StringTag;
import version.v26_3.components.ComponentNbtContext;
import version.v26_3.components.DataComponentPatch;
import version.v26_3.registries.RegistryManager;

public class Slot {
    private final int itemId;
    private final int count;
    private final DataComponentPatch components;

    public Slot(int itemId, int count, DataComponentPatch components) {
        this.itemId = itemId;
        this.count = count;
        this.components = components == null ? DataComponentPatch.empty() : components;
    }

    public Slot(String itemName, byte count) {
        this(RegistryManager.getInstance().getItemRegistry().getItemId(itemName), count, DataComponentPatch.empty());
    }

    @Override
    public String toString() {
        return "Slot{" +
            "itemId=" + itemId +
            ", Name=" + RegistryManager.getInstance().getItemRegistry().getItemName(itemId) +
            ", count=" + count +
            ", components=" + components +
            '}';
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.add("id", new StringTag(RegistryManager.getInstance().getItemRegistry().getItemName(itemId)));
        tag.add("count", new IntTag(count));

        if (!components.isEmpty()) {
            tag.add("components", components.toNbt(new ComponentNbtContext(
                    RegistryManager.getInstance().getDataComponentRegistry(), components.getCompleteness(), 0, 16
            )));
        }
        return tag;
    }

    public CompoundTag toNbt(int index) {
        CompoundTag tag = toNbt();
        tag.add("Slot", new ByteTag(index));
        return tag;
    }

    public DataComponentPatch getComponents() {
        return components;
    }

    public core.snapshot.SnapshotCompleteness getCompleteness() {
        return components.getCompleteness();
    }
}
