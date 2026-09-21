package version.v26_3.schematic;

import core.coordinates.Coordinate3D;
import core.messages.Messages;
import core.schematic.SelectionCommand;
import core.schematic.SelectionState;
import core.schematic.export.SchematicExportService;
import version.v26_3.dimension.Dimension;
import version.v26_3.world.WorldManager;

/**
 * Parses {@code /world-downloader-proxy <subcommand>} chat messages and routes them to the
 * selection state / export service. Any other chat message (including the player's own, unrelated
 * commands) is reported as "not handled" so the caller forwards the original packet unchanged.
 *
 * Input is expected to already have any leading {@code /} stripped by the caller, since whether the
 * slash is present on the wire differs between protocol versions (see
 * {@code SelectionInputInterceptor}) - this class only deals with plain text, not packets.
 */
public class SelectionCommandRouter {
    private static final String COMMAND_ROOT = "world-downloader-proxy";

    private final SelectionState selectionState;
    private final SelectionFeedback feedback;
    private final SchematicExportService exportService;
    private final CreativeMode creativeMode;
    private Runnable onSelectionModeChanged;

    public SelectionCommandRouter(SelectionState selectionState, SelectionFeedback feedback,
                                   SchematicExportService exportService,
                                   CreativeMode creativeMode) {
        this.selectionState = selectionState;
        this.feedback = feedback;
        this.exportService = exportService;
        this.creativeMode = creativeMode;
    }

    /**
     * Set a callback that is invoked whenever selection mode is toggled on or off.
     * Used by {@code ServerBoundGamePacketHandler} to start/stop the particle renderer.
     */
    public void setOnSelectionModeChanged(Runnable callback) {
        this.onSelectionModeChanged = callback;
    }

    public CreativeMode getCreativeMode() {
        return creativeMode;
    }

    /**
     * @param chatMessage the raw text the player typed, with any leading '/' already stripped
     * @return true if this was one of our commands (caller must not forward the packet), false if
     *         it should be treated as a normal chat message/command and forwarded as-is
     */
    public boolean handle(String chatMessage) {
        String trimmed = chatMessage.trim();
        // Be lenient about the leading '/' - 1.19+ ChatCommand packets omit it on the wire,
        // while legacy Chat packets and what the player literally typed include it.
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0 || !parts[0].equalsIgnoreCase(COMMAND_ROOT)) {
            return false;
        }

        if (parts.length < 2) {
            feedback.send(Messages.server("server.selection.usage", COMMAND_ROOT));
            return true;
        }

        SelectionCommand command = SelectionCommand.fromArgument(parts[1]);
        if (command == null) {
            feedback.send(Messages.server("server.selection.unknown_subcommand", parts[1]));
            return true;
        }

        switch (command) {
            case TOGGLE_SELECTION -> handleToggle();
            case EXPORT -> exportService.exportAndClear(selectionState);
            case POS1 -> handleSetCorner(true);
            case POS2 -> handleSetCorner(false);
            case FLY -> handleFly();
        }
        return true;
    }

    private void handleToggle() {
        boolean nowEnabled = selectionState.toggle();
        if (nowEnabled) {
            feedback.send(Messages.server("server.selection.enabled"));
        } else {
            feedback.send(Messages.server("server.selection.disabled"));
            // Remove the boss bar shortly after the "disabled" message so it doesn't linger.
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                feedback.clear();
            }).start();
        }
        if (onSelectionModeChanged != null) {
            onSelectionModeChanged.run();
        }
    }

    /**
     * Set pos1 or pos2 to the block under the player's feet.
     * Only works while selection mode is enabled (use area-selection first).
     */
    private void handleSetCorner(boolean isFirstCorner) {
        if (!selectionState.isEnabled()) {
            feedback.send(Messages.server("server.selection.off", COMMAND_ROOT));
            return;
        }
        // Use the raw double position and floor it so we get the block actually
        // under the player's feet. getPlayerPosition() truncates toward zero,
        // which gives the wrong block for negative coordinates.
        core.coordinates.CoordinateDouble3D raw = WorldManager.getInstance().getPlayerPositionDouble();
        Coordinate3D pos = new Coordinate3D(
                (int) Math.floor(raw.getX()),
                (int) Math.floor(raw.getY()),
                (int) Math.floor(raw.getZ())
        );
        Dimension dim = WorldManager.getInstance().getDimension();
        if (isFirstCorner) {
            selectionState.setPos1(pos, dim);
            feedback.send(Messages.server("server.selection.pos1_set", pos));
        } else {
            selectionState.setPos2(pos, dim);
            feedback.send(Messages.server("server.selection.pos2_set", pos));
        }
        if (selectionState.hasCompleteSelection()) {
            feedback.send(Messages.server("server.selection.bbox", selectionState.toBoundingBox()));
        }
    }

    private void handleFly() {
        boolean nowActive = creativeMode.toggle();
        if (nowActive) {
            feedback.send(Messages.server("server.fly.enabled"));
        } else {
            feedback.send(Messages.server("server.fly.disabled"));
        }
    }
}
