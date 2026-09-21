package version.v26_3.protocol;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ProtocolVersionHandlerTest {

    @Test
    void bestMatch() {
        ProtocolVersionHandler pvh = ProtocolVersionHandler.getInstance();

        Map<Integer, String> versions = new HashMap<>();
        versions.put(775, "26.1");
        versions.put(776, "26.2");
        versions.put(777, "26.3");

        versions.forEach((k, v) -> {
            assertThat(pvh.getProtocolByProtocolVersion(k).getVersion()).isEqualTo(v);
        });
    }
}