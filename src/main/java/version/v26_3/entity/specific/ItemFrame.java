package version.v26_3.entity.specific;

import core.config.Config;
import core.coordinates.Coordinate3D;
import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntArrayTag;
import se.llbit.nbt.IntTag;
import version.v26_3.container.Slot;
import version.v26_3.entity.ObjectEntity;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;

import java.util.function.Consumer;

/**
 * Handle item frames as they can be used as decorations
 */
public class ItemFrame extends ObjectEntity {
    int facing;
    private ItemFrameMetaData metaData;

    public ItemFrame() {
        super();
    }

    /**
     * Add additional fields needed to render item frames.
     */
    @Override
    protected void addNbtData(CompoundTag root) {
        addItemFrameNbt(root, null);
    }

    @Override
    protected void addNbtDataRelative(CompoundTag root, Coordinate3D origin) {
        addItemFrameNbt(root, origin);
    }

    private void addItemFrameNbt(CompoundTag root, Coordinate3D origin) {
        root.add("Facing", new IntTag(facing));

        int offsetX = origin == null ? Config.getCenterX() : origin.getX();
        int offsetY = origin == null ? 0 : origin.getY();
        int offsetZ = origin == null ? Config.getCenterZ() : origin.getZ();
        // use math.floor instead of just cast so that negative numbers are handled correctly
        int tileX = (int) Math.floor(x) - offsetX;
        int tileY = (int) Math.floor(y) - offsetY;
        int tileZ = (int) Math.floor(z) - offsetZ;
        root.add("block_pos", new IntArrayTag(new int[] { tileX, tileY, tileZ }));
        root.add("TileX", new IntTag(tileX));
        root.add("TileY", new IntTag(tileY));
        root.add("TileZ", new IntTag(tileZ));

        // prevent floating item frames from popping off
        root.add("Fixed", new ByteTag(1));

        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    @Override
    protected void setData(int data) {
        this.facing = data;
    }

    @Override
    public synchronized void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = new ItemFrameMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }

    private class ItemFrameMetaData extends MetaData {
        Slot item;
        int rotation;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            if (item != null) {
                nbt.add("Item", item.toNbt());
            }
            nbt.add("ItemRotation", new ByteTag(rotation));
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 8 -> provider -> facing = provider.readVarInt();
                case 9 -> provider -> item = provider.readSlot();
                case 10 -> provider -> rotation = provider.readVarInt();
                default -> super.getIndexHandler(i);
            };
        }
    }
}