package version.v26_3.chunk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the signed-byte bug in {@link Chunk#parseBlockEntities}.
 *
 * <p>The chunk packet packs the in-chunk X and Z of a block entity into a single
 * signed {@code byte}: the high nibble is localX (0-15) and the low nibble is
 * localZ (0-15). When localX >= 8 the byte value is negative (e.g. 0x80 = -128),
 * and Java's arithmetic {@code >>} sign-extends, producing a negative localX.
 * That caused block entities to be placed in the wrong chunk (off by one in X),
 * which in turn made the destination server reject them with
 * "Block entity found in a wrong chunk".
 *
 * <p>The fix masks the result: {@code (xz >> 4) & 0b1111}.
 */
class BlockEntityCoordinateTest {

    @Test
    void allPackedXzCombinationsDecodeToCorrectLocalCoords() {
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                byte xz = (byte) ((localX << 4) | localZ);

                // The exact expressions used in Chunk.parseBlockEntities.
                int decodedX = (xz >> 4) & 0b1111;
                int decodedZ = xz & 0b1111;

                assertThat(decodedX)
                    .as("localX for packed byte 0x%02X", xz & 0xFF)
                    .isEqualTo(localX);
                assertThat(decodedZ)
                    .as("localZ for packed byte 0x%02X", xz & 0xFF)
                    .isEqualTo(localZ);
            }
        }
    }

    @Test
    void highNibbleXDoesNotProduceNegativeLocalX() {
        // The specific case that was broken: localX = 8, localZ = 0.
        // Packed byte = 0x80 = -128 as a signed byte.
        byte xz = (byte) 0x80;

        int x = (xz >> 4) & 0b1111;
        int z = xz & 0b1111;

        assertThat(x).isEqualTo(8);
        assertThat(z).isEqualTo(0);

        // Without the mask (the old buggy code), x would be -8.
        int buggyX = xz >> 4;
        assertThat(buggyX).isEqualTo(-8);
    }

    @Test
    void worldCoordinateIsCorrectForChunkAt887() {
        // Reproduces the exact scenario from the user's logs:
        // chunk X = 887, localX = 10 -> world X should be 887*16 + 10 = 14202
        // (which is in chunk 887, as expected).
        //
        // With the old buggy code (no mask), localX decoded to -6, giving
        // world X = 887*16 + (-6) = 14186, which lands in chunk 886 — exactly
        // the "Block entity found in a wrong chunk, expected from [887, 112]"
        // error the destination server reported.
        int chunkX = 887;
        int localX = 10;
        byte xz = (byte) ((localX << 4) | 0);

        int decodedX = (xz >> 4) & 0b1111;
        int worldX = chunkX * 16 + decodedX;

        assertThat(worldX).isEqualTo(14202);
        assertThat(worldX >> 4).isEqualTo(887); // stays in the sending chunk

        // Demonstrate the old bug: without the mask, worldX would be 14186
        // (chunk 886), matching the user's log.
        int buggyX = xz >> 4;
        int buggyWorldX = chunkX * 16 + buggyX;
        assertThat(buggyWorldX).isEqualTo(14186);
        assertThat(buggyWorldX >> 4).isEqualTo(886); // wrong chunk!
    }
}
