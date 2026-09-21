package version.v26_3.schematic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionTabCompleterTest {
    private SelectionTabCompleter tabCompleter;

    @BeforeEach
    void setUp() {
        tabCompleter = new SelectionTabCompleter();
    }

    @Test
    void returnsFalseForUnrelatedCommand() {
        assertThat(tabCompleter.handle(1, "give Impertynator stone")).isFalse();
    }

    @Test
    void returnsFalseForEmptyText() {
        assertThat(tabCompleter.handle(1, "")).isFalse();
    }

    @Test
    void returnsTrueForOurCommandRootOnly() {
        // "/world-downloader-proxy" with no space — should complete the root
        assertThat(tabCompleter.handle(1, "world-downloader-proxy")).isTrue();
    }

    @Test
    void returnsTrueForOurCommandWithLeadingSlash() {
        assertThat(tabCompleter.handle(1, "/world-downloader-proxy ")).isTrue();
    }

    @Test
    void returnsTrueForPartialSubcommand() {
        // "/world-downloader-proxy schem" should match area-selection and schematic-export
        assertThat(tabCompleter.handle(1, "world-downloader-proxy schem")).isTrue();
    }

    @Test
    void returnsTrueForExactSubcommand() {
        assertThat(tabCompleter.handle(1, "world-downloader-proxy area-selection")).isTrue();
    }

    @Test
    void returnsTrueForNoMatchingSubcommand() {
        // Even if nothing matches, we still handle it (send empty suggestions) so the server
        // never sees the request
        assertThat(tabCompleter.handle(1, "world-downloader-proxy xyz")).isTrue();
    }

    @Test
    void isCaseInsensitiveForRoot() {
        assertThat(tabCompleter.handle(1, "World-Downloader-Proxy schem")).isTrue();
    }
}
