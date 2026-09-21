package version.v26_3.entity.specific;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.CompoundTag;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.world.WorldManager;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CushionTest {
    @BeforeEach
    void setUp() {
        WorldManager.setInstance(mock(WorldManager.class));
    }

    @Test
    void writesWhiteColor() throws Exception {
        CompoundTag nbt = parseAndExtractNbt(0);
        assertThat(nbt.get("color").stringValue()).isEqualTo("white");
        assertThat(nbt.get("Air").shortValue()).isEqualTo((short) 300);
    }

    @Test
    void writesBlackColor() throws Exception {
        CompoundTag nbt = parseAndExtractNbt(15);
        assertThat(nbt.get("color").stringValue()).isEqualTo("black");
        assertThat(nbt.get("Air").shortValue()).isEqualTo((short) 300);
    }

    @Test
    void consumesDyeColorSerializerAtAnyIndex() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeByte((byte) 50);
        packet.writeVarInt(43);
        packet.writeVarInt(14);
        packet.writeByte((byte) 0xFF);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        MetaData.getVersionedMetaData().parse(provider);

        assertThat(provider.hasNext()).isFalse();
    }

    private static CompoundTag parseAndExtractNbt(int color) throws Exception {
        PacketBuilder packet = new PacketBuilder();
        packet.writeByte((byte) 8);
        packet.writeVarInt(43);
        packet.writeVarInt(color);
        packet.writeByte((byte) 0xFF);
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());

        Cushion cushion = new Cushion();
        cushion.parseMetadata(provider);
        assertThat(provider.hasNext()).isFalse();

        Method method = Cushion.class.getDeclaredMethod("addNbtData", CompoundTag.class);
        method.setAccessible(true);
        CompoundTag nbt = new CompoundTag();
        method.invoke(cushion, nbt);
        return nbt;
    }
}
