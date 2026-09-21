package version.v26_3.schematic;

import core.coordinates.Coordinate3D;
import core.messages.Messages;
import core.schematic.SelectionState;
import version.v26_3.dimension.Dimension;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.world.WorldManager;

/**
 * Translates the player's left/right clicks into pos1/pos2 updates on the {@link SelectionState}
 * while selection mode is enabled. Wired into {@code ServerBoundGamePacketHandler}'s
 * "PlayerAction" and "UseItemOn" operators.
 *
 * When selection mode is disabled, both methods return {@code true} immediately without reading
 * anything from the packet, so there is zero behavioural change for normal gameplay.
 */
public class SelectionInputInterceptor {
    /** Status value of the "PlayerAction" packet that means "started destroying a block". */
    private static final int START_DESTROY_BLOCK = 0;

    private final SelectionState selectionState;
    private final SelectionFeedback feedback;

    public SelectionInputInterceptor(SelectionState selectionState, SelectionFeedback feedback) {
        this.selectionState = selectionState;
        this.feedback = feedback;
    }

    /**
     * Handle a serverbound "PlayerAction" packet (sent when the player starts/stops/cancels
     * digging a block). Only the packet's leading Status + Location fields are read; that is
     * enough to detect "started digging" and where, and (since this fully consumes the packet
     * without forwarding it) correctness of any trailing fields such as Face/Sequence does not
     * matter here.
     *
     * @return true if the packet should be forwarded to the real server, false if it was consumed
     *         as a pos1 marker (or otherwise swallowed while selecting) and must not reach the server
     */
    public boolean onPlayerAction(DataTypeProvider provider) {
        if (!selectionState.isEnabled()) {
            return true;
        }

        int status = provider.readVarInt();
        Coordinate3D position = provider.readCoordinates();

        if (status == START_DESTROY_BLOCK) {
            applyCorner(position, true);
        }
        return false;
    }

    /**
     * Handle a serverbound "UseItemOn" packet (right-click on a block).
     * 26.x layout: hand (VarInt), position, face (VarInt), ...
     *
     * @return true if the packet should be forwarded to the real server (selection mode is off,
     *         nothing was read), false if it was consumed as a pos2 marker and must not reach the
     *         server
     */
    public boolean onUseItemOn(DataTypeProvider provider) {
        if (!selectionState.isEnabled()) {
            return true;
        }

        provider.readVarInt(); // hand
        Coordinate3D position = provider.readCoordinates();

        applyCorner(position, false);
        return false;
    }

    private void applyCorner(Coordinate3D position, boolean isFirstCorner) {
        Dimension dimension = WorldManager.getInstance().getDimension();

        if (isFirstCorner) {
            selectionState.setPos1(position, dimension);
            feedback.send(Messages.server("server.selection.pos1_set", position));
        } else {
            selectionState.setPos2(position, dimension);
            feedback.send(Messages.server("server.selection.pos2_set", position));
        }

        if (selectionState.hasCompleteSelection()) {
            feedback.send(Messages.server("server.selection.bbox", selectionState.toBoundingBox()));
        }
    }
}
