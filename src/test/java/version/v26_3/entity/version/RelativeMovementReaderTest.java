package version.v26_3.entity.version;

import org.junit.jupiter.api.Test;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelativeMovementReaderTest {
    @Test
    void readsLinearDeltaWithOnGroundFlag() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(1);
        packet.writeShort(120);
        packet.writeShort(-45);
        packet.writeShort(9);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        assertThat(RelativeMovementReader.read(provider)).isEqualTo(new RelativeMovementReader.Delta(120, -45, 9));
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void sumsSteppedDeltaAndConsumesTickOffsets() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt((3 << 1) | 1);
        writeStep(packet, 0, 10, -5, 2);
        writeStep(packet, 2, -3, 7, 4);
        writeStep(packet, 5, 8, 1, -6);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        assertThat(RelativeMovementReader.read(provider)).isEqualTo(new RelativeMovementReader.Delta(15, 3, 0));
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void leavesRotationBytesAfterPositionPath() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(2 << 1);
        writeStep(packet, 1, 4, 5, 6);
        writeStep(packet, 3, 7, 8, 9);
        packet.writeByte((byte) 12);
        packet.writeByte((byte) -20);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        assertThat(RelativeMovementReader.read(provider)).isEqualTo(new RelativeMovementReader.Delta(11, 13, 15));
        assertThat(provider.readNext()).isEqualTo((byte) 12);
        assertThat(provider.readNext()).isEqualTo((byte) -20);
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void rejectsStepCountLargerThanPayload() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(2 << 1);
        writeStep(packet, 0, 1, 2, 3);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        assertThatThrownBy(() -> RelativeMovementReader.read(provider))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("step count 2");
    }

    private static void writeStep(PacketBuilder packet, int tickOffset, int x, int y, int z) {
        packet.writeVarInt(tickOffset);
        packet.writeShort(x);
        packet.writeShort(y);
        packet.writeShort(z);
    }
}
