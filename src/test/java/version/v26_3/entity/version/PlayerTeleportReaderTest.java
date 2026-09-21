package version.v26_3.entity.version;

import org.junit.jupiter.api.Test;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerTeleportReaderTest {
    @Test
    void readsAbsoluteTeleport() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(42);                       // teleportId
        packet.writeDouble(100.5);                    // position
        packet.writeDouble(64.0);
        packet.writeDouble(-200.25);
        packet.writeDouble(0.0);                      // deltaMovement
        packet.writeDouble(0.0);
        packet.writeDouble(0.0);
        packet.writeFloat(90.0f);                     // yRot
        packet.writeFloat(-15.0f);                    // xRot
        packet.writeInt(0);                           // relatives: absolute
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        PlayerTeleportReader.TeleportTarget target =
            PlayerTeleportReader.read(provider, 1.0, 2.0, 3.0, 10.0f, 5.0f);

        assertThat(target.teleportId()).isEqualTo(42);
        assertThat(target.x()).isEqualTo(100.5);
        assertThat(target.y()).isEqualTo(64.0);
        assertThat(target.z()).isEqualTo(-200.25);
        assertThat(target.yRot()).isEqualTo(90.0f);
        assertThat(target.xRot()).isEqualTo(-15.0f);
        assertThat(provider.hasNext()).isFalse();
    }

    @Test
    void resolvesRelativeFlagsAgainstCurrentPosition() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(7);
        packet.writeDouble(10.0);                     // relative x
        packet.writeDouble(1.5);                      // relative y
        packet.writeDouble(-4.0);                     // relative z
        packet.writeDouble(0.0);
        packet.writeDouble(0.0);
        packet.writeDouble(0.0);
        packet.writeFloat(45.0f);                     // relative yRot
        packet.writeFloat(-20.0f);                    // relative xRot
        packet.writeInt(0b11111);                     // all of X/Y/Z/Y_ROT/X_ROT relative
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        PlayerTeleportReader.TeleportTarget target =
            PlayerTeleportReader.read(provider, 100.0, 60.0, 50.0, 30.0f, 40.0f);

        assertThat(target.x()).isEqualTo(110.0);
        assertThat(target.y()).isEqualTo(61.5);
        assertThat(target.z()).isEqualTo(46.0);
        assertThat(target.yRot()).isEqualTo(75.0f);
        assertThat(target.xRot()).isEqualTo(20.0f);
        assertThat(provider.hasNext()).isFalse();
    }
}
