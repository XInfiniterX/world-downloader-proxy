package version.v26_3.schematic;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.queue.ByteQueue;
import core.schematic.SelectionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import version.v26_3.dimension.Dimension;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.world.WorldManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelectionInputInterceptorTest {
    private SelectionState state;
    private SelectionInputInterceptor interceptor;

    @BeforeEach
    void setUp() {
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());

        WorldManager mockWorld = mock(WorldManager.class);
        when(mockWorld.getDimension()).thenReturn(Dimension.OVERWORLD);
        WorldManager.setInstance(mockWorld);

        state = new SelectionState();
        interceptor = new SelectionInputInterceptor(state, new SelectionFeedback());
    }

    private DataTypeProvider buildPlayerAction(int status, int x, int y, int z) {
        PacketBuilder builder = new PacketBuilder(0x00);
        builder.writeByte((byte) status);
        long encoded = ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        builder.writeLong(encoded);
        // the trailing face/sequence fields are intentionally not written: the interceptor only
        // reads status + position, and we want afterEach-style "no leftover bytes" semantics
        return skipPrefix(builder);
    }

    private DataTypeProvider buildUseItemOn(int x, int y, int z) {
        PacketBuilder builder = new PacketBuilder(0x00);
        // 26.x layout: hand (VarInt) first, then position, then face (VarInt)
        builder.writeVarInt(0); // hand
        long encoded = ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        builder.writeLong(encoded);
        builder.writeVarInt(0); // face/direction
        return skipPrefix(builder);
    }

    /**
     * PacketBuilder.build() prepends the packet length and packet id as VarInts; the interceptor
     * only cares about the payload, so skip those exactly like PacketBuilderAndParserTest#getParser
     * does.
     */
    private DataTypeProvider skipPrefix(PacketBuilder builder) {
        ByteQueue built = builder.build();
        byte[] arr = new byte[built.size()];
        built.copyTo(arr);
        DataTypeProvider provider = new DataTypeProvider(arr);
        provider.readVarInt(); // packet length
        provider.readVarInt(); // packet id
        return provider;
    }

    @Test
    void forwardsPacketsWhenSelectionModeIsDisabled() {
        // disabled = no-op: nothing is read, packet must be forwarded
        DataTypeProvider provider = buildPlayerAction(0, 1, 2, 3);

        assertThat(interceptor.onPlayerAction(provider)).isTrue();
        assertThat(state.hasCompleteSelection()).isFalse();
    }

    @Test
    void startDestroyBlockWhileSelectingSetsPos1AndConsumesPacket() {
        state.enable();
        DataTypeProvider provider = buildPlayerAction(0, 10, 64, -20);

        assertThat(interceptor.onPlayerAction(provider)).isFalse();
        assertThat(state.getPos1()).isEqualTo(new Coordinate3D(10, 64, -20));
    }

    @Test
    void nonStartDestroyPlayerActionIsStillConsumedWhileSelecting() {
        // while selecting, *any* player action is swallowed so the server never sees the dig;
        // only START_DESTROY_BLOCK actually updates pos1, but the packet is consumed regardless
        state.enable();
        DataTypeProvider provider = buildPlayerAction(1 /* STOP_DESTROY_BLOCK */, 5, 5, 5);

        assertThat(interceptor.onPlayerAction(provider)).isFalse();
        assertThat(state.getPos1()).isNull();
    }

    @Test
    void useItemOnWhileSelectingSetsPos2AndConsumesPacket() {
        state.enable();
        DataTypeProvider provider = buildUseItemOn(20, 70, 30);

        assertThat(interceptor.onUseItemOn(provider)).isFalse();
        assertThat(state.getPos2()).isEqualTo(new Coordinate3D(20, 70, 30));
    }

    @Test
    void useItemOnForwardsWhenSelectionDisabled() {
        DataTypeProvider provider = buildUseItemOn(20, 70, 30);

        assertThat(interceptor.onUseItemOn(provider)).isTrue();
        assertThat(state.getPos2()).isNull();
    }
}
