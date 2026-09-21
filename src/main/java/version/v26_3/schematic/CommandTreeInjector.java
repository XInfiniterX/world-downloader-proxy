package version.v26_3.schematic;

import core.messages.Messages;
import version.v26_3.module.VersionAccessors;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.proxy.PacketInjector;

import java.util.ArrayList;
import java.util.List;

/**
 * Intercepts the server's {@code DeclareCommands} packet and injects our proxy command
 * ({@code /world-downloader-proxy}) into the command tree before forwarding it to the client.
 *
 * <p>Without this, the client doesn't know about our command and will:
 * <ul>
 *   <li>show "Unknown or incomplete command" when the user types it</li>
 *   <li>not send {@code CommandSuggestion} tab-completion requests for it</li>
 * </ul>
 *
 * <p>The DeclareCommands packet is a graph of command nodes. We parse it, add three new
 * literal nodes ({@code world-downloader-proxy}, {@code area-selection},
 * {@code schematic-export}), and add the first as a child of the root node.
 *
 * <p>Node flags layout (byte):
 * <pre>
 *   bits 0-1: node type (0=root, 1=literal, 2=argument)
 *   bit    2: isExecutable
 *   bit    3: hasRedirect
 *   bit    4: hasCustomSuggestions
 *   bits 5-7: unused
 * </pre>
 *
 * <p><b>Version compatibility:</b> Protocol 777 (26.3) uses VarInt parser IDs with
 * 62 entries (0–61) in the {@code minecraft:command_argument_type} registry.
 *
 * <p>Sources:
 * <ul>
 *   <li><a href="https://minecraft.wiki/w/Java_Edition_protocol/Command_data">Minecraft Wiki — Command data</a></li>
 *   <li><a href="https://github.com/PrismarineJS/minecraft-data">PrismarineJS minecraft-data</a> — protocol.json for 1.21.8+</li>
 * </ul>
 */
public final class CommandTreeInjector {

    public static final String ROOT_COMMAND = "world-downloader-proxy";

    /** Subcommands registered in {@link SelectionCommandRouter}. */
    private static final String[] SUBCOMMANDS = {
        "area-selection",
        "schematic-export",
        "pos1",
        "pos2",
        "fly"
    };

    // Node type constants
    private static final int TYPE_ROOT = 0;
    private static final int TYPE_LITERAL = 1;
    private static final int TYPE_ARGUMENT = 2;

    // Flag bits
    private static final int FLAG_EXECUTABLE = 0x04;
    private static final int FLAG_HAS_REDIRECT = 0x08;
    private static final int FLAG_HAS_SUGGESTIONS = 0x10;

    // --- Parser ID table (indexed by VarInt ID, value = string identifier) ---
    // Source: PrismarineJS minecraft-data protocol.json for 1.21.8+ (protocol 772+)
    // This is the only table needed for the supported versions (26.x, protocol 775/776/777).

    private static final String[] PARSERS_1_21_8 = {
        "brigadier:bool",            // 0
        "brigadier:float",           // 1
        "brigadier:double",          // 2
        "brigadier:integer",         // 3
        "brigadier:long",            // 4
        "brigadier:string",          // 5
        "minecraft:entity",          // 6
        "minecraft:game_profile",    // 7
        "minecraft:block_pos",       // 8
        "minecraft:column_pos",      // 9
        "minecraft:vec3",            // 10
        "minecraft:vec2",            // 11
        "minecraft:block_state",     // 12
        "minecraft:block_predicate", // 13
        "minecraft:item_stack",      // 14
        "minecraft:item_predicate",  // 15
        "minecraft:team_color",      // 16
        "minecraft:hex_color",       // 17
        "minecraft:component",       // 18
        "minecraft:style",           // 19
        "minecraft:message",         // 20
        "minecraft:nbt_compound_tag", // 21
        "minecraft:nbt_tag",         // 22
        "minecraft:nbt_path",        // 23
        "minecraft:objective",       // 24
        "minecraft:objective_criteria", // 25
        "minecraft:operation",       // 26
        "minecraft:particle",        // 27
        "minecraft:angle",           // 28
        "minecraft:rotation",        // 29
        "minecraft:scoreboard_slot", // 30
        "minecraft:score_holder",    // 31
        "minecraft:swizzle",         // 32
        "minecraft:team",            // 33
        "minecraft:item_slot",       // 34
        "minecraft:item_slots",      // 35
        "minecraft:resource_location", // 36
        "minecraft:function",        // 37
        "minecraft:entity_anchor",   // 38
        "minecraft:int_range",       // 39
        "minecraft:float_range",     // 40
        "minecraft:dimension",       // 41
        "minecraft:gamemode",        // 42
        "minecraft:time",            // 43
        "minecraft:resource_or_tag", // 44
        "minecraft:resource_or_tag_key", // 45
        "minecraft:resource",        // 46
        "minecraft:resource_key",    // 47
        "minecraft:resource_selector", // 48
        "minecraft:template_mirror", // 49
        "minecraft:template_rotation", // 50
        "minecraft:heightmap",       // 51
        "minecraft:loot_table",      // 52
        "minecraft:loot_predicate",  // 53
        "minecraft:loot_modifier",   // 54
        "minecraft:context_float_provider", // 55
        "minecraft:context_int_provider",   // 56
        "minecraft:slot_source",     // 57
        "minecraft:dialog",          // 58
        "minecraft:feature",         // 59
        "minecraft:swing_animation", // 60
        "minecraft:uuid",            // 61
    };

    /**
     * Parser ID table for the supported versions (26.x, protocol 775/776/777).
     * In 26.3 the command_argument_type registry has 62 entries (0–61): the new parsers
     * context_float_provider/context_int_provider/slot_source/feature/swing_animation were
     * inserted before dialog/uuid, shifting those to IDs 58 and 61.
     */
    private static final String[] PARSER_TABLE = PARSERS_1_21_8;

    /** Parsed single node — stores raw bytes + parsed children for the root. */
    private static final class Node {
        final byte[] raw;
        final int flags;
        final int[] children;

        Node(byte[] raw, int flags, int[] children) {
            this.raw = raw;
            this.flags = flags;
            this.children = children;
        }

        int type() { return flags & 0x03; }
        boolean hasRedirect() { return (flags & FLAG_HAS_REDIRECT) != 0; }
    }

    /**
     * Process the DeclareCommands packet. The {@code provider} is positioned after the
     * packet ID VarInt has been read.
     *
     * @return {@code true} to forward the original packet unchanged,
     *         {@code false} if we injected a modified version.
     */
    public boolean process(DataTypeProvider provider) {
        PacketInjector injector = VersionAccessors.injector();
        if (injector == null) {
            return true; // can't inject, forward original
        }

        try {
            return processInternal(provider, injector);
        } catch (Exception e) {
            System.out.println(Messages.console("console.commandtree.failed", e.getMessage()));
            e.printStackTrace();
            return true; // forward original on error
        }
    }

    private boolean processInternal(DataTypeProvider provider, PacketInjector injector) {
        // Read count
        int count = provider.readVarInt();

        // Parse all nodes, storing raw bytes
        List<Node> nodes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int start = provider.position();
            int flags = provider.readNext() & 0xFF;

            // children array
            int childCount = provider.readVarInt();
            int[] children = new int[childCount];
            for (int c = 0; c < childCount; c++) {
                children[c] = provider.readVarInt();
            }

            // redirect node
            if ((flags & FLAG_HAS_REDIRECT) != 0) {
                provider.readVarInt();
            }

            // name + parser + properties
            if ((flags & 0x03) == TYPE_LITERAL || (flags & 0x03) == TYPE_ARGUMENT) {
                provider.readString(); // name
            }
            if ((flags & 0x03) == TYPE_ARGUMENT) {
                // 26.x: parser is a VarInt ID into PARSER_TABLE
                int parserId = provider.readVarInt();
                String parser = parserId >= 0 && parserId < PARSER_TABLE.length
                        ? PARSER_TABLE[parserId]
                        : "unknown:" + parserId;
                skipParserProperties(provider, parser);
            }
            // suggestions type — an identifier string if flag 0x10 is set
            if ((flags & FLAG_HAS_SUGGESTIONS) != 0) {
                provider.readString();
            }

            int end = provider.position();
            byte[] raw = extractBytes(provider, start, end);
            nodes.add(new Node(raw, flags, children));
        }

        // Read rootIndex
        int rootIndex = provider.readVarInt();
        if (rootIndex < 0 || rootIndex >= nodes.size()) {
            return true; // invalid, forward original
        }

        // Build modified packet
        int firstNewNode = count;          // index of "world-downloader-proxy"
        int numNewNodes = 1 + SUBCOMMANDS.length;

        PacketBuilder pb = new PacketBuilder("DeclareCommands");

        // Write new node count
        pb.writeVarInt(count + numNewNodes);

        // Write all original nodes, modifying the root to include our command
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            if (i == rootIndex) {
                // Rewrite root node with added child
                writeNode(pb, node.flags, addChild(node.children, firstNewNode),
                        node.type() != TYPE_ROOT, node);
            } else {
                // Copy raw bytes unchanged
                pb.writeByteArray(node.raw);
            }
        }

        // Write new "world-downloader-proxy" literal node (not executable, has children)
        int[] cmdChildren = new int[SUBCOMMANDS.length];
        for (int s = 0; s < SUBCOMMANDS.length; s++) {
            cmdChildren[s] = firstNewNode + 1 + s;
        }
        writeLiteralNode(pb, TYPE_LITERAL, false, cmdChildren, ROOT_COMMAND);

        // Write subcommand literal nodes (executable, no children)
        for (String sub : SUBCOMMANDS) {
            writeLiteralNode(pb, TYPE_LITERAL | FLAG_EXECUTABLE, true, new int[0], sub);
        }

        // Write rootIndex (unchanged)
        pb.writeVarInt(rootIndex);

        // Send modified packet, drop original
        injector.enqueuePacket(pb);
        return false;
    }

    /**
     * Write a node with the given flags and children. For non-root nodes, also write
     * the name (and parser/properties for argument nodes). For the root node, we only
     * need flags + children (no name).
     */
    private void writeNode(PacketBuilder pb, int flags, int[] children,
                           boolean hasName, Node original) {
        pb.writeByte((byte) flags);
        pb.writeVarInt(children.length);
        for (int c : children) {
            pb.writeVarInt(c);
        }
        // Root nodes have no name/parser, so we're done.
        // For other nodes we already copied raw bytes in the caller, so this method
        // is only used for the root node.
    }

    /** Write a literal node: flags + children + name. */
    private void writeLiteralNode(PacketBuilder pb, int flags, boolean executable,
                                  int[] children, String name) {
        int f = flags;
        if (executable) f |= FLAG_EXECUTABLE;
        pb.writeByte((byte) f);
        pb.writeVarInt(children.length);
        for (int c : children) {
            pb.writeVarInt(c);
        }
        pb.writeString(name);
    }

    private int[] addChild(int[] original, int newChild) {
        int[] result = new int[original.length + 1];
        System.arraycopy(original, 0, result, 0, original.length);
        result[original.length] = newChild;
        return result;
    }

    // --- Position tracking helpers ---

    private byte[] extractBytes(DataTypeProvider provider, int start, int end) {
        return provider.extractBytes(start, end);
    }

    // --- Parser property skipping ---

    /**
     * Skip the properties bytes for the given argument parser.
     * Most parsers have no properties (void). The ones that do are handled explicitly.
     *
     * @param parser the parser identifier (string name from PARSER_TABLE)
     */
    private void skipParserProperties(DataTypeProvider provider, String parser) {
        switch (parser) {
            // --- void parsers (no properties) ---
            case "brigadier:bool":
            case "minecraft:game_profile":
            case "minecraft:block_pos":
            case "minecraft:column_pos":
            case "minecraft:vec3":
            case "minecraft:vec2":
            case "minecraft:block_state":
            case "minecraft:block_predicate":
            case "minecraft:item_stack":
            case "minecraft:item_predicate":
            case "minecraft:team_color":
            case "minecraft:hex_color":
            case "minecraft:component":
            case "minecraft:style":
            case "minecraft:message":
            case "minecraft:nbt":
            case "minecraft:nbt_compound_tag":
            case "minecraft:nbt_tag":
            case "minecraft:nbt_path":
            case "minecraft:objective":
            case "minecraft:objective_criteria":
            case "minecraft:operation":
            case "minecraft:particle":
            case "minecraft:angle":
            case "minecraft:rotation":
            case "minecraft:scoreboard_slot":
            case "minecraft:swizzle":
            case "minecraft:team":
            case "minecraft:item_slot":
            case "minecraft:item_slots":
            case "minecraft:resource_location":
            case "minecraft:function":
            case "minecraft:entity_anchor":
            case "minecraft:int_range":
            case "minecraft:float_range":
            case "minecraft:dimension":
            case "minecraft:gamemode":
            case "minecraft:template_mirror":
            case "minecraft:template_rotation":
            case "minecraft:heightmap":
            case "minecraft:loot_table":
            case "minecraft:loot_predicate":
            case "minecraft:loot_modifier":
            case "minecraft:context_float_provider":
            case "minecraft:context_int_provider":
            case "minecraft:slot_source":
            case "minecraft:dialog":
            case "minecraft:feature":
            case "minecraft:swing_animation":
            case "minecraft:uuid":
            case "minecraft:mob_effect":
            case "minecraft:item_enchantment":
            case "minecraft:entity_summon":
                // void — no properties
                break;

            // --- numeric parsers with min/max flags ---
            case "brigadier:float":
            case "brigadier:double":
            case "brigadier:integer":
            case "brigadier:long":
                // flags byte: bit 0 = min_present, bit 1 = max_present
                int numFlags = provider.readNext() & 0xFF;
                if ((numFlags & 0x01) != 0) {
                    if (parser.equals("brigadier:float")) provider.skip(4);
                    else if (parser.equals("brigadier:double")) provider.skip(8);
                    else if (parser.equals("brigadier:integer")) provider.skip(4);
                    else provider.skip(8); // long
                }
                if ((numFlags & 0x02) != 0) {
                    if (parser.equals("brigadier:float")) provider.skip(4);
                    else if (parser.equals("brigadier:double")) provider.skip(8);
                    else if (parser.equals("brigadier:integer")) provider.skip(4);
                    else provider.skip(8); // long
                }
                break;

            // --- string parser with mode VarInt ---
            case "brigadier:string":
                // VarInt mode: 0=single_word, 1=quotable_phrase, 2=greedy_phrase
                provider.readVarInt();
                break;

            // --- entity/score_holder with flags byte ---
            case "minecraft:entity":
            case "minecraft:score_holder":
                // single byte flags
                provider.readNext();
                break;

            // --- time parser: int (min ticks) ---
            case "minecraft:time":
                provider.readInt();
                break;

            // --- resource parsers with registry identifier ---
            case "minecraft:resource_or_tag":
            case "minecraft:resource_or_tag_key":
            case "minecraft:resource":
            case "minecraft:resource_key":
            case "minecraft:resource_selector":
                // Registry identifier (a string)
                provider.readString();
                break;

            // --- range parser (legacy, not in 26.x parser table but kept for safety) ---
            case "minecraft:range":
                // bool: allowDecimals
                provider.readNext();
                break;

            default:
                // Unknown parser — assume no properties.
                // This is safer than guessing and corrupting the stream.
                System.out.println(Messages.console("console.commandtree.unknown_parser", parser));
                break;
        }
    }
}
