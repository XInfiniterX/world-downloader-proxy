package version.v26_3.packets;

import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDouble3D;
import core.snapshot.SnapshotCompleteness;
import core.snapshot.SnapshotDiagnostic;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.Tag;
import version.v26_3.components.ComponentReadContext;
import version.v26_3.components.DataComponentPatch;
import version.v26_3.container.Slot;
import version.v26_3.registries.DataComponentRegistry;
import version.v26_3.registries.RegistryManager;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/**
 * Class to provide an interface between the raw byte data and the various data types. Most methods are
 * self-explanatory.
 *
 * <p>If a future Minecraft version changes one of the wire formats read here again, add a subclass
 * overriding just the affected method(s) (and {@link #ofLength}) and pick it in {@link #ofPacket}, the
 * same pattern used before this class was flattened down to a single (26.x) implementation (see the git
 * history of this class, and docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class DataTypeProvider {
    private static final int MAX_SHORT_VAL = 1 << 15;
    private byte[] finalFullPacket;
    private int pos;
    private final SnapshotCompleteness completeness = new SnapshotCompleteness();

    public byte[] debug__getFullArray() {
        return finalFullPacket;
    }
    public String debug__readableString() {
        char[] out = new char[finalFullPacket.length];
        for (int i = 0; i < finalFullPacket.length; i++) {
            byte b = finalFullPacket[i];
            if (b >= 32) {
                out[i] = (char) b;
            } else {
                out[i] = '_';
            }
        }
        return new String(out);
    }
    public DataTypeProvider(byte[] finalFullPacket) {
        this.finalFullPacket = finalFullPacket;
        this.pos = 0;
    }

    public static DataTypeProvider ofPacket(byte[] finalFullPacket) {
        return new DataTypeProvider(finalFullPacket);
    }

    public DataTypeProvider ofLength(int length) {
        return new DataTypeProvider(this.readByteArray(length));
    }

    public long readVarLong() {
        int numRead = 0;
        long result = 0;
        byte read;
        do {
            if (!hasNext()) {
                throw new RuntimeException("Invalid VarLong found! Packet structure may have changed.");
            }
            read = readNext();
            int value = (read & 0b01111111);
            result |= (((long) value) << (7 * numRead));

            numRead++;
            if (numRead > 10) {
                throw new RuntimeException("VarLong is too big");
            }
        } while ((read & 0b10000000) != 0);

        return result;
    }

    public boolean hasNext() {
        return pos < finalFullPacket.length;
    }

    public byte readNext() {
        if (pos >= finalFullPacket.length) {
            return 0;
        }
        return finalFullPacket[pos++];
    }

    public int readInt() {
        byte[] bytes = readByteArray(4);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.put(bytes);
        ((Buffer) buffer).flip();
        return buffer.getInt();
    }

    public byte[] readByteArray(int size) {
        byte[] res = new byte[size];

        int available = Math.min(size, finalFullPacket.length - pos);
        if (available > 0) {
            System.arraycopy(finalFullPacket, pos, res, 0, available);
        }
        pos += size;

        return res;
    }

    public boolean readBoolean() {
        return readNext() == (byte) 0x01;
    }

    public String readString() {
        int stringSize = readVarInt();
        if (stringSize < 0 || stringSize > remaining()) {
            throw new IllegalArgumentException("Invalid string byte length: " + stringSize);
        }
        return new String(readByteArray(stringSize), StandardCharsets.UTF_8);
    }

    public int readVarInt() {
        return DataReader.readVarInt(this::hasNext, this::readNext);
    }

    public void skip(int amount) {
        while (amount-- > 0) {
            readNext();
        }
    }

    public int readShort() {
        byte low = readNext();
        byte high = readNext();
        int val = (((low & 0xFF) << 8) | (high & 0xFF));
        return val > MAX_SHORT_VAL ? -(MAX_SHORT_VAL * 2 - val) : val ;
    }

    public Coordinate3D readCoordinates() {
        long val = readLong();
        int x = (int) (val >> 38);
        int y = (int) (val & 0xFFF) << 20 >> 20;
        int z = (int) ((val << 26) >> 38);

        return new Coordinate3D(x, y, z);
    }

    public Coordinate3D readSectionCoordinates() {
        long val = readLong();
        int x = (int) (val >>> 42);
        int y = (int) (val << 44 >>> 44);
        int z = (int) (val << 22 >>> 42);

        if (x >= 1 << 21) { x -= 1 << 22; }
        if (y >= 1 << 19) { y -= 1 << 20; }
        if (z >= 1 << 21) { z -= 1 << 22; }

        return new Coordinate3D(x, y, z);
    }

    public long readLong() {
        byte[] bytes = readByteArray(8);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(bytes);
        ((Buffer) buffer).flip();
        return buffer.getLong();
    }

    public long[] readLongArray(int size, int expected) {
        long[] res = new long[expected];
        for (int i = 0; i < expected; i++) {
            res[i] = readLong();
        }
        for (int i = 0; i < size - expected; i++) {
            readLong();
        }
        return res;
    }

    public long[] readLongArray(int size) {
        return readLongArray(size, size);
    }

    public int[] readIntArray(int size) {
        int[] res = new int[size];
        for (int i = 0; i < size; i++) {
            res[i] = readInt();
        }
        return res;
    }

    public int[] readVarIntArray(int size) {
        int[] res = new int[size];
        for (int i = 0; i < size; i++) {
            res[i] = readVarInt();
        }
        return res;
    }

    public SpecificTag readNbtTag() {
        int start = position();
        try {
            return (SpecificTag) SpecificTag.read(readNext(), new DataInputStream(new InputStream() {
                @Override
                public int read() {
                    return readNext() & 0xFF;
                }
            })).unpack();
        } catch (Exception ex) {
            markIncomplete("MALFORMED_NBT", "packet", null, null, start, ex.getMessage());
            throw new IllegalArgumentException("Could not decode NBT at packet position " + start, ex);
        }
    }

    public float readFloat() {
        byte[] bytes = readByteArray(4);
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES);
        buffer.put(bytes);
        ((Buffer) buffer).flip();
        return buffer.getFloat();
    }

    public double readDouble() {
        byte[] bytes = readByteArray(8);
        ByteBuffer buffer = ByteBuffer.allocate(Double.BYTES);
        buffer.put(bytes);
        ((Buffer) buffer).flip();
        return buffer.getDouble();
    }

    public UUID readUUID() {
        return new UUID(readLong(), readLong());
    }

    public UUID readOptUUID() {
        if (readBoolean()) {
            return readUUID();
        }
        return null;
    }

    public String readChat() {
        // 26.x encodes chat components as NBT compound tags, not JSON strings.
        // Reading a VarInt-prefixed string here would consume the wrong number
        // of bytes and misalign all subsequent fields in the packet.
        SpecificTag tag = readChatTag();
        return tag != null ? tag.toString() : "";
    }

    public SpecificTag readChatTag() {
        return readNbtTag();
    }
    public String readOptChat() {
        SpecificTag tag = readOptChatTag();
        return tag == null ? null : tag.toString();
    }

    public SpecificTag readOptChatTag() {
        return readBoolean() ? readChatTag() : null;
    }

    public List<Slot> readSlots(int count) {
        List<Slot> slots = new ArrayList<>(count);

        while (count-- > 0) {
            slots.add(readSlot());
        }

        return slots;
    }

    public Slot readSlot() {
        int count = readVarInt();
        if (count <= 0) {
            return null;
        }

        int itemId = readVarInt();
        DataComponentRegistry registry = RegistryManager.getInstance().getDataComponentRegistry();
        if (registry == null) {
            throw new IllegalStateException("Data component registry is not initialized");
        }
        DataComponentPatch patch = DataComponentPatch.read(this, new ComponentReadContext(
                registry, completeness, "26.3", "item_stack", 0, 16
        ));
        return new Slot(itemId, count, patch);
    }

    public static int readOptVarInt(DataTypeProvider provider) {
        if (provider.readBoolean()) {
            return provider.readVarInt();
        }
        return 0;
    }

    public String[] readStringArray(int size) {
        String[] res = new String[size];
        for (int i = 0; i < size; i++) {
            res[i] = readString();
        }
        return res;
    }

    public BitSet readBitSet() {
        int numBytes = readVarInt();
        return BitSet.valueOf(readByteArray(numBytes));
    }

    public CoordinateDouble3D readDoubleCoordinates() {
        return new CoordinateDouble3D(readDouble(), readDouble(), readDouble());
    }

    public DataTypeProvider copy() {
        return new DataTypeProvider(Arrays.copyOf(this.finalFullPacket, this.finalFullPacket.length));
    }

    public record Registry(String name, List<RegistryEntry> entries) {}
    public record RegistryEntry(String name, Optional<Tag> nbt) {}
    public Registry readRegistry() {
        String name = readString();
        int numEntries = readVarInt();

        List<RegistryEntry> entries = new ArrayList<>(numEntries);
        for (int i = 0; i < numEntries; i++) {
            String identifier = readString();

            Optional<Tag> b = readOptional(this::readNbtTag);
            entries.add(new RegistryEntry(identifier, b));
        }

        return new Registry(name, entries);
    }

    public <T> Optional<T> readOptional(Supplier<T> provider) {
        if (readBoolean()) {
            return Optional.of(provider.get());
        }
        return Optional.empty();
    }

    public int getPosition() {
        return pos;
    }

    /**
     * Peek at a byte without consuming it — for diagnostics only.
     */
    public byte peekAt(int index) {
        return (index >= 0 && index < finalFullPacket.length) ? finalFullPacket[index] : 0;
    }

    public int remaining() {
        return this.finalFullPacket.length - pos;
    }

    /**
     * @return the current read position in the underlying byte array.
     */
    public int position() {
        return pos;
    }

    public SnapshotCompleteness getCompleteness() {
        return completeness;
    }

    public void markIncomplete(String code, String source, Integer numericId, String resourceLocation,
                               int bufferPosition, String detail) {
        completeness.markIncomplete(new SnapshotDiagnostic(
                code, "26.3", source, numericId, resourceLocation, bufferPosition, detail
        ));
    }

    /**
     * @return the total length of the underlying byte array.
     */
    public int finalFullPacketLength() {
        return finalFullPacket.length;
    }

    /**
     * Extract a copy of the bytes in the range [start, end) from the underlying array.
     * Does not change the current read position.
     */
    public byte[] extractBytes(int start, int end) {
        return Arrays.copyOfRange(finalFullPacket, start, end);
    }

    @Override
    public String toString() {
        return "DataTypeProvider{" +
                "finalFullPacket[" + finalFullPacket.length + "]=" + Arrays.toString(finalFullPacket) +
                ", pos=" + pos +
                '}';
    }
}
