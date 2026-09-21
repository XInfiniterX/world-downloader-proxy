package version.v26_3.chunk;

import core.coordinates.Coordinate3D;
import core.messages.Messages;
import org.apache.commons.lang3.mutable.MutableBoolean;
import se.llbit.nbt.*;
import version.v26_3.chunk.palette.*;
import version.v26_3.chunk.version.encoder.BlockLocationEncoder;
import version.v26_3.packets.builder.PacketBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * Class to hold a 16 block tall chunk section.
 *
 * <p>This is the single (flattened) implementation for the supported Minecraft versions (26.x),
 * previously reached through the {@code ChunkSection_1_13 -> _1_14 -> _1_15 -> _1_16 -> _1_18 ->
 * _26_1} inheritance chain. The effective 26.x behavior is folded in here directly. The class is
 * non-final and the version-differentiating methods ({@link #write}, {@link #parse},
 * {@link #getLocationEncoder}, {@link #createNewChunkSection} in {@link Chunk}) stay overridable so
 * a future Minecraft version can add a subclass overriding just the delta (see
 * docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class ChunkSection {
    protected final Chunk chunk;

    protected long[] blocks;
    protected byte[] blockLight;
    protected byte[] skyLight;
    protected byte y;
    protected Palette palette;

    long[] biomes;
    Palette biomePalette;
    int blockCount = -1;

    private final BlockLocationEncoder blockLocationEncoder = new BlockLocationEncoder();

    public int getDataVersion() {
        return chunk.getDataVersion();
    }

    public ChunkSection(byte y, Palette palette, Chunk chunk) {
        this.chunk = chunk;
        this.y = y;
        this.palette = palette;
    }

    public ChunkSection(int sectionY, Chunk chunk) {
        this.y = (byte) sectionY;
        this.chunk = chunk;
    }

    public ChunkSection(int sectionY, Tag nbt, Chunk chunk) {
        this(sectionY, chunk);
        parse(nbt);
    }

    protected BlockLocationEncoder getLocationEncoder() {
        return this.blockLocationEncoder;
    }

    public void setSkyLight(byte[] skyLight) {
        this.skyLight = skyLight;
    }

    public void setBlockLight(byte[] blockLight) {
        this.blockLight = blockLight;
    }

    public void setBlocks(long[] blocks) {
        this.blocks = blocks;
    }

    public void setBiomes(long[] biomes) {
        this.biomes = biomes;
    }

    public void setBiomePalette(Palette biomePalette) {
        this.biomePalette = biomePalette;
        this.biomePalette.biomePalette();
    }

    public void setBlockPalette(Palette blockPalette) {
        this.palette = blockPalette;
    }

    /**
     * Vanilla client doesn't store the block count but we can speed things up a bit by saving it
     */
    public void setBlockCount(int blockCount) {
        this.blockCount = blockCount;
    }

    /**
     * Number of non-air blocks in this section as reported by the server (or -1 if unknown).
     */
    public int getBlockCount() {
        return blockCount;
    }

    public Palette getBlockPalette() {
        return palette;
    }

    public long[] getBlocks() {
        return blocks;
    }

    /**
     * Convert this section to NBT.
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();

        tag.add("biomes", getPalettedCompound(biomePalette, Tag.TAG_STRING, biomes, PaletteType.BIOMES));
        tag.add("block_states", getPalettedCompound(palette, Tag.TAG_COMPOUND, blocks, PaletteType.BLOCKS));

        tag.add("Y", new ByteTag(y));
        if (blockLight != null && blockLight.length > 0) {
            tag.add("BlockLight", new ByteArrayTag(blockLight));
        }
        if (skyLight != null && skyLight.length > 0) {
            tag.add("SkyLight", new ByteArrayTag(skyLight));
        }
        if (blockCount > 0) {
            tag.add("block_count", new IntTag(blockCount));
        }

        return tag;
    }

    private CompoundTag getPalettedCompound(Palette palette, int tagType, long[] data, PaletteType type) {
        CompoundTag tag = new CompoundTag();

        // If the palette is empty (usually meaning no blocks in a section), set it to a palette
        // with just air in it.
        if (palette == null || (data.length == 0 && !(palette instanceof SingleValuePalette))) {
            palette = new SingleValuePalette(0);
            if (type == PaletteType.BIOMES) {
                palette.biomePalette();
            }
        }

        // If we have a direct palette, we need to convert it to a proper palette since the world
        // format doesn't allow direct palettes (I think).
        if (palette instanceof DirectPalette directPalette) {
            PaletteTransformer transformer = new PaletteTransformer(getLocationEncoder(), directPalette);
            long[] newData = transformer.transform(data);

            if (newData != data) {
                data = newData;
                palette = transformer.getNewPalette();
            }
        }

        List<SpecificTag> paletteItems = palette.toNbt();
        if (paletteItems.isEmpty()) {
            // this shouldn't ever happen
            System.err.println(Messages.console("console.chunk.empty_palette", getY(), palette));
        } else {
            tag.add("palette", new ListTag(tagType, paletteItems));
        }
        if (data != null && data.length > 0) {
            tag.add("data", new LongArrayTag(data));
        }

        return tag;
    }

    public static int getBlockIndex(int x, int y, int z) {
        return y * 16 * 16 + z * 16 + x;
    }

    public byte getY() {
        return y;
    }

    public int computeHeight(int x, int z, MutableBoolean foundAir) {
        BlockRegistry globalPalette = GlobalPaletteProvider.getGlobalPalette(getDataVersion());

        for (int y = 15; y >= 0 ; y--) {
            int blockStateId = getNumericBlockStateAt(x, y, z);

            BlockState state = globalPalette.getState(blockStateId);

            if (state == null || !state.isSolid()) {
                foundAir.setTrue();
                continue;
            }

            if (foundAir.isFalse()) {
                continue;
            }
            return y;
        }
        return -1;
    }

    public int getNumericBlockStateAt(int x, int y, int z) {
        return palette.stateFromId(getPaletteIndex(x, y, z));
    }

    public int getPaletteIndex(int x, int y, int z) {
        return getPaletteIndex(x, y, z, palette.getBitsPerBlock());
    }

    private synchronized int getPaletteIndex(int x, int y, int z, int bitsPerBlock) {
        if (blocks.length == 0 || bitsPerBlock == 0) {
            return 0;
        }

        return getLocationEncoder().setTo(x, y, z, bitsPerBlock).fetch(blocks);
    }

    /**
     * Write this section to a network packet. As of 26.1, a "fluid count" short follows the block
     * count, and the paletted container data arrays (blocks and biomes) are no longer
     * length-prefixed on the wire - their length is derived from bits-per-entry instead.
     */
    public void write(PacketBuilder packet) {
        if (blockCount < 0) { blockCount = palette.isEmpty() ? 0 : 4096; }

        packet.writeShort(blockCount);
        packet.writeShort(0); // fluid count - not tracked separately, 0 is a safe placeholder

        writePalettedContainer(packet, palette, blocks, true);
        writePalettedContainer(packet, biomePalette, biomes, false);
    }

    /**
     * In 26.1+ the data array length is not sent on the wire; it is derived from bits-per-entry.
     * A 1-entry palette with an empty data array (common for sections that contain only air) must be
     * written as a single-value palette (bitsPerBlock = 0, no data array), otherwise the reader
     * would expect longsRequired(bitsPerBlock) longs that were never written.
     */
    private void writePalettedContainer(PacketBuilder packet, Palette pal, long[] data, boolean isBlocks) {
        if (pal.size() == 1 && (data == null || data.length == 0)) {
            packet.writeByte((byte) 0);
            packet.writeVarInt(pal.stateFromId(0));
            return;
        }

        int expectedLen = isBlocks
            ? longsRequired(pal.getBitsPerBlock())
            : longsRequiredBiomes(pal.getBitsPerBlock());

        long[] dataToWrite = data;
        if (data == null || data.length != expectedLen) {
            dataToWrite = new long[expectedLen];
            if (data != null) {
                System.arraycopy(data, 0, dataToWrite, 0, Math.min(data.length, expectedLen));
            }
        }

        pal.write(packet);
        packet.writeLongArray(dataToWrite);
    }

    /**
     * Parse this section from on-disk NBT (the 1.18+ section format: {@code block_states} /
     * {@code biomes} compounds each holding a {@code palette} and {@code data}).
     */
    protected void parse(Tag nbt) {
        this.setBlockLight(nbt.get("BlockLight").byteArray());
        this.setSkyLight(nbt.get("SkyLight").byteArray());

        CompoundTag blockStates = nbt.get("block_states").asCompound();
        this.setBlocks(blockStates.get("data").longArray());
        this.palette = new Palette(getDataVersion(), blockStates.get("palette").asList());

        CompoundTag biomes = nbt.get("biomes").asCompound();
        this.biomePalette = Palette.biomes(getDataVersion(), biomes.get("palette").asList());
        this.biomes = biomes.get("data").longArray();

        Tag blockCount = nbt.get("block_count");
        if (!blockCount.isError()) {
            this.blockCount = blockCount.intValue();
        }
    }

    public synchronized void setBlockAt(Coordinate3D coords, int blockStateId) {
        int index = palette.getIndexFor(this, blockStateId);

        if (palette instanceof SingleValuePalette svp) {
            if (blocks == null || blocks.length == 0) {
                resetBlocks();
            }

            this.palette = svp.asNormalPalette();
        }

        // Some servers seem to send a palette with a bits-per-block that doesn't match the number of provided longs
        // when the section is empty. In this case we assume the section was empty before and remake the array.
        resizeBlocksIfRequired(palette.getBitsPerBlock());

        getLocationEncoder().setTo(
                coords.getX(), coords.getY(), coords.getZ(),
                palette.getBitsPerBlock()
        );
        getLocationEncoder().write(blocks, index);
    }

    /**
     * When the bits per block increases, we must rewrite the blocks array.
     */
    public synchronized void resizeBlocksIfRequired(int newBitsPerBlock) {
        int newSize = longsRequired(newBitsPerBlock);

        // if blocks is empty or isn't the correct size, no need to copy
        if (blocks == null || blocks.length != longsRequired(palette.getBitsPerBlock())) {
            this.blocks = new long[newSize];
            return;
        }

        // if the length didn't change we don't have to do anything
        if (blocks.length == newSize) {
            return;
        }

        copyBlocks(new long[newSize], newBitsPerBlock);
    }

    public static int longsRequired(int bitsPerBlock) {
        return longsRequired(bitsPerBlock, 4096);
    }

    public static int longsRequiredBiomes(int bitsPerBlock) {
        return longsRequired(bitsPerBlock, 64);
    }

    private static int longsRequired(int bitsPerBlock, double totalItems) {
        if (bitsPerBlock == 0) {
            return 0;
        }

        int blocksPerLong = 64 / bitsPerBlock;
        return (int) Math.ceil(totalItems / blocksPerLong);
    }

    public synchronized void copyBlocks(long[] newBlocks, int newBitsPerBlock) {
        BlockLocationEncoder locationHelper = getLocationEncoder();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    int index = getPaletteIndex(x, y, z);

                    locationHelper.setTo(x, y, z, newBitsPerBlock).write(newBlocks, index);
                }
            }
        }
        this.blocks = newBlocks;
    }

    public byte[] getSkyLight() { return skyLight; }
    public byte[] getBlockLight() { return blockLight; }

    public void resetBlocks() {
        this.blocks = new long[256];
        this.palette = Palette.empty();
    }

    public void copyTo(ChunkSection other) {
        other.blocks = this.blocks;
        other.palette = this.palette;
    }

    /**
     * Read the biome name at the given block-local coordinates. Biomes in 1.18+ are stored in a
     * 4×4×4 grid per section (64 entries), packed in the same long[] bit-array format as blocks.
     *
     * @param x  block-local X (0–15)
     * @param y  block-local Y within the section (0–15)
     * @param z  block-local Z (0–15)
     * @return the biome resource location (e.g. {@code minecraft:plains}), or {@code null} if
     *         the section or biome palette is missing
     */
    public String getBiomeAt(int x, int y, int z) {
        if (biomePalette == null || biomes == null || biomes.length == 0) {
            return null;
        }

        // biome grid is 4×4×4 within a 16×16×16 section
        int biomeX = x / 4;
        int biomeY = y / 4;
        int biomeZ = z / 4;

        int bitsPerEntry = biomePalette.getBitsPerBlock();
        int paletteIndex;
        if (bitsPerEntry == 0) {
            paletteIndex = 0;
        } else {
            paletteIndex = getLocationEncoder().setTo(biomeX, biomeY, biomeZ, bitsPerEntry, 4, 4).fetch(biomes);
        }

        int biomeId = biomePalette.stateFromId(paletteIndex);
        State state = biomePalette.lookupState(biomeId);
        return state != null ? state.toString() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChunkSection that = (ChunkSection) o;

        if (getY() != that.getY()) return false;
        return Arrays.equals(blocks, that.blocks);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(blocks);
        result = 31 * result + (int) y;
        result = 31 * result + (palette != null ? palette.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ChunkSection{" +
            "y=" + y +
            ", biomePalette=" + biomePalette +
            ", biomes=" + Arrays.toString(biomes) +
            ", blocks[" + blocks.length + "]" +
            ", palette=" + palette +
            ", blockLight[" + blockLight.length + "]" +
            ", skyLight[" + skyLight.length + "]" +
            '}';
    }
}
