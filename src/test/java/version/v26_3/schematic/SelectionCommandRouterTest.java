package version.v26_3.schematic;

import core.schematic.SelectionState;
import core.schematic.export.SchematicExportService;
import core.schematic.export.SchematicFileNamer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionCommandRouterTest {
    private SelectionCommandRouter newRouter(SelectionState state) {
        SchematicExportService exportService = new SchematicExportService(
            (box, dimension, targetFile) -> { throw new AssertionError("export should not run in this test"); },
            new SchematicFileNamer(),
            new SelectionFeedback(),
            Path.of("schematic")
        );
        return new SelectionCommandRouter(state, new SelectionFeedback(), exportService, new CreativeMode());
    }

    @Test
    void ignoresUnrelatedChatMessages() {
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("hello world")).isFalse();
        assertThat(router.handle("/some-other-plugin-command")).isFalse();
    }

    @Test
    void togglesSelectionModeWithLeadingSlash() {
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("/world-downloader-proxy area-selection")).isTrue();
        assertThat(state.isEnabled()).isTrue();

        assertThat(router.handle("/world-downloader-proxy area-selection")).isTrue();
        assertThat(state.isEnabled()).isFalse();
    }

    @Test
    void togglesSelectionModeWithoutLeadingSlash() {
        // 1.19+ ChatCommand packets don't include the leading slash on the wire
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("world-downloader-proxy area-selection")).isTrue();
        assertThat(state.isEnabled()).isTrue();
    }

    @Test
    void isCaseInsensitiveForRootAndSubcommand() {
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("/World-Downloader-Proxy Area-Selection")).isTrue();
        assertThat(state.isEnabled()).isTrue();
    }

    @Test
    void handlesMissingSubcommandWithoutThrowing() {
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("/world-downloader-proxy")).isTrue();
    }

    @Test
    void handlesUnknownSubcommandWithoutThrowing() {
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("/world-downloader-proxy does-not-exist")).isTrue();
    }

    @Test
    void exportWithoutACompleteSelectionDoesNotInvokeTheExporter() {
        // the fake SchematicExporter throws if invoked - reaching that would fail the test,
        // so this also verifies SchematicExportService validates the selection before exporting
        SelectionState state = new SelectionState();
        SelectionCommandRouter router = newRouter(state);

        assertThat(router.handle("/world-downloader-proxy schematic-export")).isTrue();
    }
}
