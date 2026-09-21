package version.v26_3.chunk.palette;

import core.config.Config;
import core.config.Version;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import version.v26_3.chunk.version.encoder.BlockLocationEncoder;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.world.WorldManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class PaletteTransformerTest {

    @BeforeAll
    public static void setup() {
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(Version.V26_3.protocolVersion);
    }

    @Test
    public void checkConversion() {
        // init data
        DirectPalette oldPalette = new DirectPalette(15);
        BlockLocationEncoder ble = new BlockLocationEncoder();
        long[] blocks = new long[1024];

        // write unique block into every position
        int i = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    ble.setTo(x, y, z, oldPalette.getBitsPerBlock());
                    ble.write(blocks, i++);
                }
            }
        }

        // transform
        PaletteTransformer transformer = new PaletteTransformer(ble, oldPalette);
        long[] newBlocks = transformer.transform(blocks);
        Palette newPalette = transformer.getNewPalette();

        // verify all blocks are the same as before
        i = 0;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    ble.setTo(x, y, z, newPalette.getBitsPerBlock());

                    assertThat(i++)
                        .as(x + ", " + y  + ", " + z )
                        .isEqualTo(ble.fetch(newBlocks));
                }
            }
        }
    }
}
