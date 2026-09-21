package version.v26_3.components;

import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.Tag;
import version.v26_3.packets.DataTypeProvider;

import java.util.ArrayList;
import java.util.List;

public final class LoreComponentCodec implements ComponentCodec<List<SpecificTag>> {
    private static final int MAX_LINES = 4096;

    @Override
    public List<SpecificTag> read(DataTypeProvider input, ComponentReadContext context) {
        int count = input.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new IllegalArgumentException("Invalid lore line count: " + count);
        }
        List<SpecificTag> lines = new ArrayList<>(count);
        int tagType = -1;
        for (int i = 0; i < count; i++) {
            SpecificTag line = input.readChatTag();
            if (tagType < 0) {
                tagType = line.tagType();
            } else if (tagType != line.tagType()) {
                throw new IllegalArgumentException("Lore contains mixed NBT tag types");
            }
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    @Override
    public SpecificTag toNbt(List<SpecificTag> value, ComponentNbtContext context) {
        int tagType = value.isEmpty() ? Tag.TAG_COMPOUND : value.getFirst().tagType();
        return new ListTag(tagType, value);
    }
}
