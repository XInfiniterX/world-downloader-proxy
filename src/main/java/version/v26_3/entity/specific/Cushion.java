package version.v26_3.entity.specific;

import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.StringTag;
import version.v26_3.entity.ObjectEntity;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;

import java.util.function.Consumer;

public class Cushion extends ObjectEntity {
    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private int color;
    private CushionMetaData metaData;

    @Override
    protected void addNbtData(CompoundTag root) {
        if (metaData != null) {
            metaData.addNbtTags(root);
        }
        root.add("color", new StringTag(colorName(color)));
    }

    @Override
    public synchronized void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = new CushionMetaData();
        }
        metaData.parse(provider);
    }

    private static String colorName(int id) {
        if (id < 0 || id >= COLORS.length) {
            throw new IllegalArgumentException("Unknown cushion color: " + id);
        }
        return COLORS[id];
    }

    private class CushionMetaData extends MetaData {
        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int index) {
            return index == 8 ? provider -> color = provider.readVarInt() : super.getIndexHandler(index);
        }
    }
}
