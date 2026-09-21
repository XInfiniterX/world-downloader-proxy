package version.v26_3.packets;

import core.queue.ByteQueue;
import org.junit.jupiter.api.Test;
import version.v26_3.packets.builder.PacketBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LpVec3Test {

    private static DataTypeProvider providerOf(byte... payload) {
        PacketBuilder builder = new PacketBuilder(0x00);
        for (byte b : payload) {
            builder.writeByte(b);
        }
        ByteQueue built = builder.build();
        byte[] arr = new byte[built.size()];
        built.copyTo(arr);
        DataTypeProvider provider = new DataTypeProvider(arr);
        provider.readVarInt(); // packet length
        provider.readVarInt(); // packet id
        return provider;
    }

    @Test
    void zeroVectorIsSingleZeroByte() {
        // 0x00 = zero vector, only 1 byte consumed
        DataTypeProvider provider = providerOf((byte) 0x00);

        LpVec3 v = LpVec3.read(provider);

        assertThat(v.x).isEqualTo(0.0);
        assertThat(v.y).isEqualTo(0.0);
        assertThat(v.z).isEqualTo(0.0);
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void nonZeroVectorConsumesExactlySixBytes() {
        // Construct a valid non-zero LpVec3:
        // scale = 1 (bits 0-1 = 01, no continuation)
        // xn = 100, yn = 200, zn = 300
        long scale = 1;
        long xn = 100;
        long yn = 200;
        long zn = 300;
        long packed = (scale & 0x3L)
            | (xn << 7)
            | (yn << 22)
            | (zn << 37);

        byte a = (byte) (packed & 0xFF);
        byte b = (byte) ((packed >> 8) & 0xFF);
        int c = (int) ((packed >> 16) & 0xFFFFFFFFL);

        // PacketBuilder.writeInt writes big-endian, which matches the LpVec3 wire format
        DataTypeProvider provider = providerOf(a, b);
        // writeInt as 4 bytes big-endian
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((c >> 24) & 0xFF);
        bytes[1] = (byte) ((c >> 16) & 0xFF);
        bytes[2] = (byte) ((c >> 8) & 0xFF);
        bytes[3] = (byte) (c & 0xFF);
        // rebuild with all 6 payload bytes
        byte[] payload = new byte[] { a, b, bytes[0], bytes[1], bytes[2], bytes[3] };
        provider = providerOf(payload);

        LpVec3 v = LpVec3.read(provider);

        double expectedX = xn / LpVec3.MAX_QUANTIZED_VALUE * scale;
        double expectedY = yn / LpVec3.MAX_QUANTIZED_VALUE * scale;
        double expectedZ = zn / LpVec3.MAX_QUANTIZED_VALUE * scale;

        assertThat(v.x).isCloseTo(expectedX, within(1e-9));
        assertThat(v.y).isCloseTo(expectedY, within(1e-9));
        assertThat(v.z).isCloseTo(expectedZ, within(1e-9));
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void firstByteZeroMeansZeroEvenIfMoreBytesFollow() {
        // The reader should only consume 1 byte when the first byte is 0x00,
        // leaving the rest of the stream intact.
        DataTypeProvider provider = providerOf((byte) 0x00, (byte) 0x42, (byte) 0x99);

        LpVec3 v = LpVec3.read(provider);

        assertThat(v.x).isEqualTo(0.0);
        assertThat(provider.hasNext()).isTrue();
        assertThat(provider.readNext()).isEqualTo((byte) 0x42);
    }
}
