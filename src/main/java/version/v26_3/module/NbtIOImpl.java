package version.v26_3.module;

import core.interfaces.INbtIO;
import se.llbit.nbt.NamedTag;
import version.v26_3.util.NbtUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * v26_3 implementation of {@link INbtIO}, bridging core's NBT I/O seam to the
 * per-version {@link NbtUtil} (which uses the per-version {@code CompressionManager}
 * and the per-version jo-nbt fork).
 */
public class NbtIOImpl implements INbtIO {
    @Override
    public void write(Object nbt, Path destination) throws IOException {
        NbtUtil.write((NamedTag) nbt, destination);
    }

    @Override
    public Object read(InputStream input) throws IOException {
        return NbtUtil.read(input);
    }
}
