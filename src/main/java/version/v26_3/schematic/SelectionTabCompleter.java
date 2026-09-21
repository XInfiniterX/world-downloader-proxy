package version.v26_3.schematic;

import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.proxy.PacketInjector;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles tab-completion for the proxy's schematic commands. When the client sends a
 * {@code CommandSuggestion} packet (i.e. the player is typing a command in chat and wants
 * suggestions), this class checks whether the partially-typed command starts with our
 * {@code /world-downloader-proxy} prefix. If so, it responds with matching subcommands directly
 * to the client and tells the caller to <b>not</b> forward the request to the server.
 *
 * <p>The response packet is {@code CommandSuggestions} (clientbound) with the format:
 * <pre>
 * transactionId: VarInt  (echoed from the request)
 * start: VarInt          (index in the text where suggestions begin)
 * length: VarInt         (length of the text being replaced)
 * matches: []VarInt      (count, then each entry:)
 *   match: String        (the suggestion text)
 *   tooltip: Boolean     (false — no tooltip)
 * </pre>
 */
public class SelectionTabCompleter {
    private static final String COMMAND_ROOT = "world-downloader-proxy";
    private static final String[] SUBCOMMANDS = {
        "area-selection",
        "schematic-export",
        "pos1",
        "pos2",
        "fly"
    };

    /**
     * @param transactionId the transaction ID from the request packet (echoed in the response)
     * @param text          the full text the player has typed so far (without leading '/')
     * @return true if this request was for our command (caller must not forward to server),
     *         false if it should be forwarded normally
     */
    public boolean handle(int transactionId, String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // The text from the client may or may not include a leading '/'
        String trimmed = text.startsWith("/") ? text.substring(1) : text;
        String[] parts = trimmed.split("\\s+", 2);

        if (parts.length == 0 || !parts[0].equalsIgnoreCase(COMMAND_ROOT)) {
            return false;
        }

        // If the user is still typing the root command (no space yet), don't interfere —
        // the client's own command tree will handle it. We only complete subcommands.
        if (parts.length < 2) {
            // The user typed "/world-downloader-proxy" with no space — complete the root
            // itself so it shows up in suggestions. This handles the case where the
            // command isn't in the server's declared command tree.
            sendSuggestions(transactionId, 0, parts[0].length(), List.of(COMMAND_ROOT));
            return true;
        }

        String partial = parts[1];
        List<String> matches = new ArrayList<>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(partial.toLowerCase())) {
                matches.add(sub);
            }
        }

        // start = index where the subcommand begins in the text (after root + space)
        int start = (text.startsWith("/") ? 1 : 0) + parts[0].length() + 1;
        int length = partial.length();
        sendSuggestions(transactionId, start, length, matches);
        return true;
    }

    /**
     * Send a {@code CommandSuggestions} packet to the client with the given matches.
     * If the matches list is empty, we still send a response with 0 matches so the
     * client doesn't hang waiting for a response.
     */
    private void sendSuggestions(int transactionId, int start, int length, List<String> matches) {
        PacketInjector injector = VersionAccessors.injector();
        if (injector == null) {
            return;
        }

        PacketBuilder pb = new PacketBuilder("CommandSuggestions");
        pb.writeVarInt(transactionId);
        pb.writeVarInt(start);
        pb.writeVarInt(length);
        pb.writeVarInt(matches.size());
        for (String match : matches) {
            pb.writeString(match);
            pb.writeBoolean(false); // no tooltip
        }
        injector.enqueuePacket(pb);
    }
}
