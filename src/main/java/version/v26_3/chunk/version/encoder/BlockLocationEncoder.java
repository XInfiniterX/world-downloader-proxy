package version.v26_3.chunk.version.encoder;

import core.messages.Messages;
import version.v26_3.chunk.Chunk;

/**
 * Encodes/decodes a single block's palette index within a chunk section's packed {@code long[]} data
 * array, using the packing introduced in 1.16: each block state occupies a whole number of longs
 * (a state never straddles two longs), so some bits at the end of each long may go unused. This is
 * the only packing format used by the supported versions (26.x).
 *
 * <p>This is the single (default) encoder since the legacy pre-1.16 multi-long packing was removed
 * alongside pre-26.x version support. If a future Minecraft version changes the packing again,
 * introduce a subclass overriding {@link #fetch}, {@link #write} and {@link #setTo} and return it
 * from {@link version.v26_3.chunk.ChunkSection#getLocationEncoder()} (see
 * docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class BlockLocationEncoder {
    int individualValueMask;
    int longIndex;
    int startOffset;

    public int fetch(long[] blocks) {
        int data = (int) (blocks[longIndex] >>> startOffset);
        data &= individualValueMask;

        return data;
    }

    public void write(long[] blocks, int newIndex) {
        long data = newIndex & individualValueMask;

        // first set all relevant bits to 0, then use or to put the new bits in place
        blocks[longIndex] &= ~((long) individualValueMask << startOffset);
        blocks[longIndex] |= (data << startOffset);
    }

    public BlockLocationEncoder setTo(int x, int y, int z, int bitsPerBlock) {
        return setTo(x, y, z, bitsPerBlock, Chunk.SECTION_HEIGHT, Chunk.SECTION_WIDTH);
    }

    /**
     * Overload for non-block paletted containers (e.g. biomes) that use a different grid size.
     * Biomes in 1.18+ are stored in a 4×4×4 grid per section, not 16×16×16 like blocks.
     *
     * @param gridHeight  grid height (16 for blocks, 4 for biomes)
     * @param gridWidth   grid width  (16 for blocks, 4 for biomes)
     */
    public BlockLocationEncoder setTo(int x, int y, int z, int bitsPerBlock, int gridHeight, int gridWidth) {
        this.individualValueMask = (1 << bitsPerBlock) - 1;

        int blockNumber = (((y * gridHeight) + z) * gridWidth) + x;

        // bitsPerBlock can be 0 if we're trying to call the BlockLocationEncoder
        // on a SingleValuePalette. Doing so would cause division by 0 errors!
        if (bitsPerBlock == 0) {
            longIndex = 0;
            startOffset = 0;
        } else {
            int blocksPerLong = 64 / bitsPerBlock;
            this.longIndex = blockNumber / blocksPerLong;
            int indexInLong = blockNumber % blocksPerLong;
            this.startOffset = indexInLong * bitsPerBlock;
        }

        if (longIndex < 0) {
            System.out.println(Messages.console("console.chunk.invalid_long_index"));
            System.out.println(Messages.console("console.chunk.block_location", x, y, z, bitsPerBlock));
        }

        return this;
    }
}
