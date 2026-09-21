package version.v26_3.components;

import se.llbit.nbt.ByteArrayTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.FloatTag;
import se.llbit.nbt.IntArrayTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;
import version.v26_3.packets.DataTypeProvider;

import java.util.ArrayList;
import java.util.List;

public final class CustomModelDataComponentCodec implements ComponentCodec<CustomModelDataComponent> {
    private static final int MAX_VALUES = 4096;

    @Override
    public CustomModelDataComponent read(DataTypeProvider input, ComponentReadContext context) {
        int floatCount = readCount(input, "custom model float");
        List<Float> floats = new ArrayList<>(floatCount);
        for (int i = 0; i < floatCount; i++) {
            floats.add(input.readFloat());
        }
        int flagCount = readCount(input, "custom model flag");
        List<Boolean> flags = new ArrayList<>(flagCount);
        for (int i = 0; i < flagCount; i++) {
            flags.add(input.readBoolean());
        }
        int stringCount = readCount(input, "custom model string");
        List<String> strings = new ArrayList<>(stringCount);
        for (int i = 0; i < stringCount; i++) {
            strings.add(input.readString());
        }
        int colorCount = readCount(input, "custom model color");
        int[] colors = new int[colorCount];
        for (int i = 0; i < colorCount; i++) {
            colors[i] = input.readInt();
        }
        return new CustomModelDataComponent(floats, flags, strings, colors);
    }

    private static int readCount(DataTypeProvider input, String field) {
        int count = input.readVarInt();
        if (count < 0 || count > MAX_VALUES) {
            throw new IllegalArgumentException("Invalid " + field + " count: " + count);
        }
        return count;
    }

    @Override
    public SpecificTag toNbt(CustomModelDataComponent value, ComponentNbtContext context) {
        CompoundTag result = new CompoundTag();
        List<SpecificTag> floats = value.floats().stream().map(FloatTag::new).map(SpecificTag.class::cast).toList();
        result.add("floats", new ListTag(Tag.TAG_FLOAT, floats));
        byte[] flags = new byte[value.flags().size()];
        for (int i = 0; i < flags.length; i++) {
            flags[i] = value.flags().get(i) ? (byte) 1 : 0;
        }
        result.add("flags", new ByteArrayTag(flags));
        List<SpecificTag> strings = value.strings().stream().map(StringTag::new).map(SpecificTag.class::cast).toList();
        result.add("strings", new ListTag(Tag.TAG_STRING, strings));
        result.add("colors", new IntArrayTag(value.colors()));
        return result;
    }
}
