package version.v26_3.entity.version;

import version.v26_3.packets.DataTypeProvider;

public final class RelativeMovementReader {
    private static final int MIN_BYTES_PER_STEP = 7;

    private RelativeMovementReader() { }

    public static Delta read(DataTypeProvider provider) {
        int properties = provider.readVarInt();
        int stepCount = properties >>> 1;
        if (stepCount == 0) {
            return new Delta(provider.readShort(), provider.readShort(), provider.readShort());
        }

        int maxSteps = provider.remaining() / MIN_BYTES_PER_STEP;
        if (stepCount > maxSteps) {
            throw new IllegalArgumentException("Relative movement step count " + stepCount + " exceeds maximum " + maxSteps);
        }

        int x = 0;
        int y = 0;
        int z = 0;
        for (int i = 0; i < stepCount; i++) {
            provider.readVarInt();
            x += provider.readShort();
            y += provider.readShort();
            z += provider.readShort();
        }
        return new Delta(x, y, z);
    }

    public record Delta(int x, int y, int z) { }
}
