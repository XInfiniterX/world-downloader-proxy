package version.v26_3.components;

import se.llbit.nbt.SpecificTag;
import version.v26_3.packets.DataTypeProvider;

public interface ComponentCodec<T> {
    T read(DataTypeProvider input, ComponentReadContext context);

    SpecificTag toNbt(T value, ComponentNbtContext context);
}
