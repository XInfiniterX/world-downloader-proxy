package version.v26_3.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import version.v26_3.registries.DataComponentRegistry;
import version.v26_3.registries.RegistryLoader;
import version.v26_3.registries.RegistryManager;

import static org.assertj.core.api.Assertions.assertThat;

class DataComponentRegistryCoverageTest {
    private DataComponentRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        RegistryManager.setInstance(null);
        RegistryManager.getInstance().setRegistries(RegistryLoader.forVersion("26.3"));
        registry = RegistryManager.getInstance().getDataComponentRegistry();
    }

    @Test
    void loadsAllBundledDataComponentTypes() {
        assertThat(registry.size()).isEqualTo(122);
        assertThat(registry.getProtocolId("minecraft:custom_data")).isEqualTo(0);
        assertThat(registry.getName(0)).isEqualTo("minecraft:custom_data");
        assertThat(registry.getProtocolId("minecraft:sulfur_cube_content")).isEqualTo(80);
    }

    @Test
    void reportsRegistryEntriesWithoutWireCodecs() {
        ComponentCodecs codecs = ComponentCodecs.defaults();

        // All registered data component types now have wire codecs.
        assertThat(codecs.unsupported(registry))
                .isEmpty();
    }
}
