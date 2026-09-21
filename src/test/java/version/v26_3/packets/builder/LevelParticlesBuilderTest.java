package version.v26_3.packets.builder;

import core.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.world.WorldManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LevelParticlesBuilderTest {
    @BeforeEach
    void setUp() {
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(777);
    }

    @Test
    void writesMinecraft26_3SingleParticleLayout() {
        DataTypeProvider provider = DataTypeProvider.ofPacket(
                LevelParticlesBuilder.buildSingle(39, 1.25, -4.5, 9.75).toArray()
        );

        assertThat(provider.readVarInt()).isEqualTo(0x30);
        assertThat(provider.readVarInt()).isEqualTo(39);
        assertThat(provider.readBoolean()).isTrue();
        assertThat(provider.readBoolean()).isFalse();
        assertThat(provider.readDouble()).isEqualTo(1.25);
        assertThat(provider.readDouble()).isEqualTo(-4.5);
        assertThat(provider.readDouble()).isEqualTo(9.75);
        for (int i = 0; i < 6; i++) {
            assertThat(provider.readFloat()).isZero();
        }
        assertThat(provider.readVarInt()).isZero();
        assertThat(provider.readVarInt()).isZero();
        assertThat(provider.hasNext()).isFalse();
    }
}
