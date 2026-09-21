package version.v26_3.packets.builder;

import core.coordinates.Coordinate3D;
import core.queue.ByteQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.*;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;

import java.util.Arrays;
import java.util.BitSet;

import static org.assertj.core.api.Assertions.assertThat;

public class PacketBuilderAndParserTest {

    protected PacketBuilder builder;
    protected DataTypeProvider parser;

    @BeforeEach
    public void beforeEach() {
        builder = new PacketBuilder(0x00);
    }

    @AfterEach
    public void afterEach() {
        assertThat(parser.hasNext()).isFalse();
    }

    /**
     * Get a DataTypeProvider built from the exiting packet builder.
     */
    protected DataTypeProvider getParser() {
        ByteQueue built = builder.build();
        byte[] arr = new byte[built.size()];
        built.copyTo(arr);

        parser = new DataTypeProvider(arr);
        int length = parser.readVarInt();
        assertThat(length).isGreaterThan(0);

        int packetId = parser.readVarInt();
        return parser;
    }

    @Test
    void varInt() {
        int before = 10;
        builder.writeVarInt(before);

        int after = getParser().readVarInt();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void varIntBig() {
        int before = 2 << 26 - 1;
        builder.writeVarInt(before);

        int after = getParser().readVarInt();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void varIntNegative() {
        int before = - 1337;
        builder.writeVarInt(before);

        int after = getParser().readVarInt();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void intTest() {
        int before = 10;
        builder.writeInt(before);

        int after = getParser().readInt();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void stringTest() {
        String before = "This is a string with very many words in it.";
        builder.writeString(before);

        String after = getParser().readString();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void shortTest() {
        int before = 42;
        builder.writeShort(before);

        int after = getParser().readShort();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void shortNegativeTest() {
        int before = -42;
        builder.writeShort(before);

        int after = getParser().readShort();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void byteArray() {
        byte[] before = {1, 2, 3, 2, 1};
        builder.writeByteArray(before);

        byte[] after = getParser().readByteArray(before.length);

        assertThat(after).isEqualTo(before);
    }

    @Test
    void boolTrue() {
        builder.writeBoolean(true);

        assertThat(getParser().readBoolean()).isTrue();
    }

    @Test
    void boolFalse() {
        builder.writeBoolean(false);

        assertThat(getParser().readBoolean()).isFalse();
    }

    @Test
    void nbtCompoundTest() {
        CompoundTag before = new CompoundTag();
        before.add("someKey", new IntTag(30));

        builder.writeNbtDirect(before);

        assertThat(getParser().readNbtTag()).isEqualTo(before);
    }

    @Test
    void nbtListTest() {
        ListTag before = new ListTag(Tag.TAG_STRING, Arrays.asList(
                new StringTag("a"),
                new StringTag("b"),
                new StringTag("c")
        ));

        builder.writeNbtDirect(before);

        assertThat(getParser().readNbtTag()).isEqualTo(before);
    }

    @Test
    void longTest() {
        long before = 2L^48 - 2L^12;
        builder.writeLong(before);

        long after = getParser().readLong();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void floatTest() {
        float before = 0.12345f;
        builder.writeFloat(before);

        float after = getParser().readFloat();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void uuidTest() {
        UUID before = new UUID(2L^42, 2L^12);
        builder.writeUUID(before);

        UUID after = getParser().readUUID();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void parseCoordinates() {
        long x = -1337;
        long y = 64;
        long z = -4201;
        long encoded = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF);

        builder.writeLong(encoded);

        Coordinate3D after = getParser().readCoordinates();

        assertThat(after).isEqualTo(new Coordinate3D(x, y, z));
    }

    @Test
    void parseBit() {
        BitSet before = new BitSet();
        before.set(2);
        before.set(4);
        before.set(6);
        before.set(9);
        builder.writeBitSet(before);

        BitSet after = getParser().readBitSet();

        assertThat(after).isEqualTo(before);
    }

    @Test
    void bitSetUsesMinecraft263ByteArrayWireFormat() {
        BitSet value = new BitSet();
        value.set(0);
        value.set(2);
        value.set(4);
        value.set(6);
        value.set(9);
        value.set(23);

        builder = new PacketBuilder();
        builder.writeBitSet(value);
        assertThat(builder.toArray()).containsExactly((byte) 0x03, (byte) 0x55, (byte) 0x02, (byte) 0x80);

        parser = new DataTypeProvider(new byte[] {0x03, 0x55, 0x02, (byte) 0x80, 0x2A});
        assertThat(parser.readBitSet()).isEqualTo(value);
        assertThat(parser.readVarInt()).isEqualTo(42);
    }

    @Test
    void parseCoordinatesOther() {
        long x = 1000;
        long y = 72;
        long z = 9800;
        long encoded = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF);

        builder.writeLong(encoded);

        Coordinate3D after = getParser().readCoordinates();

        assertThat(after).isEqualTo(new Coordinate3D(x, y, z));
    }
}