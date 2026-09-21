package version.v26_3.chunk.palette;

import se.llbit.nbt.SpecificTag;

/**
 * Interface for data source of palettes.
 */
public interface Registry {
    State getState(int i);

    State getState(SpecificTag nbt);

    int getStateId(SpecificTag nbt);

    State getDefaultState();
}
