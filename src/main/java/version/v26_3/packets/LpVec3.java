package version.v26_3.packets;

/**
 * Reader for the Low-Precision Vec3 wire format introduced in Minecraft 1.21.11 / 26.1
 * (protocol 774+). Used by the {@code Spawn Entity} and {@code Set Entity Velocity} packets
 * to encode entity velocity compactly instead of the legacy three {@code Short} values.
 *
 * <p>Wire format (mirrors {@code net.minecraft.network.LpVec3}):
 * <ul>
 *   <li>If the first byte is {@code 0x00}, the whole vector is zero and no further bytes
 *       are read (1 byte total).</li>
 *   <li>Otherwise 6 bytes are read: 2 bytes + 1 big-endian {@code int32}. The 48-bit packed
 *       value is laid out as:
 *       <pre>
 *         bits 0-1 : scale (low 2 bits)
 *         bit  2   : continuation flag (1 = a VarInt follows with the upper scale bits)
 *         bits 3-17: X (15 bits, unsigned)
 *         bits 18-32: Y (15 bits, unsigned)
 *         bits 33-47: Z (15 bits, unsigned)
 *       </pre>
 *       If the continuation flag is set, a VarInt is read and {@code (varint << 2)} is
 *       added to the scale.</li>
 * </ul>
 *
 * <p>The decoded components are {@code quantized / 32766 * scale} (blocks per tick). The
 * proxy does not persist velocity (it writes 0 to NBT), so this class only needs to consume
 * the correct number of bytes from the stream — but the full decode is implemented for
 * correctness and future use.
 */
public final class LpVec3 {
    public static final double MAX_QUANTIZED_VALUE = 32766.0;

    public final double x;
    public final double y;
    public final double z;

    private LpVec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static LpVec3 zero() {
        return new LpVec3(0, 0, 0);
    }

    /**
     * Read an LpVec3 from the given provider, consuming exactly the bytes the format
     * requires so the packet stream stays aligned.
     */
    public static LpVec3 read(DataTypeProvider provider) {
        int a = provider.readNext() & 0xFF;
        if (a == 0) {
            return zero();
        }

        int b = provider.readNext() & 0xFF;
        long c = provider.readInt() & 0xFFFFFFFFL;

        long packed = (c << 16) | (b << 8) | a;

        boolean continuation = (packed & 0x4L) != 0;
        long scale = packed & 0x3L;
        if (continuation) {
            scale |= ((long) provider.readVarInt()) << 2;
        }

        long xn = (packed >>> 7) & 0x7FFFL;
        long yn = (packed >>> 22) & 0x7FFFL;
        long zn = (packed >>> 37) & 0x7FFFL;

        double sx = scale;
        double x = xn / MAX_QUANTIZED_VALUE * sx;
        double y = yn / MAX_QUANTIZED_VALUE * sx;
        double z = zn / MAX_QUANTIZED_VALUE * sx;

        return new LpVec3(x, y, z);
    }

    @Override
    public String toString() {
        return "LpVec3(" + x + ", " + y + ", " + z + ")";
    }
}
