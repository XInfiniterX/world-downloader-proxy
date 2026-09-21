package version.v26_3.schematic;

import core.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.world.WorldManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ParticleRegistryTest {

    @BeforeEach
    void setUp() {
        // ParticleRegistry is a singleton — reset its state before each test.
        // We don't load any server registry, so isLoaded() will be false and the
        // vanilla fallback IDs should be used.
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        ParticleRegistry.getInstance().clear();
    }

    private void setProtocol(int protocolVersion) {
        Config.setProtocolVersion(protocolVersion);
    }

    @Test
    void flameFallbackFor26_1_is32() {
        setProtocol(775);
        assertThat(ParticleRegistry.getInstance().getId("minecraft:flame")).isEqualTo(32);
    }

    @Test
    void flameFallbackFor26_2_is39() {
        setProtocol(776);
        assertThat(ParticleRegistry.getInstance().getId("minecraft:flame")).isEqualTo(39);
    }

    @Test
    void flameFallbackFor26_3_is39() {
        setProtocol(777);
        assertThat(ParticleRegistry.getInstance().getId("minecraft:flame")).isEqualTo(39);
    }

    @Test
    void unknownParticleReturnsMinusOne() {
        setProtocol(777);
        assertThat(ParticleRegistry.getInstance().getId("minecraft:does_not_exist")).isEqualTo(-1);
    }

    @Test
    void isLoadedFalseWhenNoRegistrySent() {
        setProtocol(777);
        assertThat(ParticleRegistry.getInstance().isLoaded()).isFalse();
    }

    @Test
    void loadedRegistryOverridesFallback() {
        setProtocol(777);

        // Simulate a server that sends a custom particle_type registry where flame has id 5
        DataTypeProvider.Registry registry = new DataTypeProvider.Registry(
            "minecraft:particle_type",
            List.of(
                new DataTypeProvider.RegistryEntry("minecraft:custom_one", Optional.empty()),
                new DataTypeProvider.RegistryEntry("minecraft:custom_two", Optional.empty()),
                new DataTypeProvider.RegistryEntry("minecraft:custom_three", Optional.empty()),
                new DataTypeProvider.RegistryEntry("minecraft:custom_four", Optional.empty()),
                new DataTypeProvider.RegistryEntry("minecraft:custom_five", Optional.empty()),
                new DataTypeProvider.RegistryEntry("minecraft:flame", Optional.empty())
            )
        );
        ParticleRegistry.getInstance().load(registry);

        assertThat(ParticleRegistry.getInstance().isLoaded()).isTrue();
        assertThat(ParticleRegistry.getInstance().getId("minecraft:flame")).isEqualTo(5);
        // not in the custom registry and not a known fallback → -1
        assertThat(ParticleRegistry.getInstance().getId("minecraft:dust")).isEqualTo(-1);
    }
}
