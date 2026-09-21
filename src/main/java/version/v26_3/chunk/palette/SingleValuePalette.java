package version.v26_3.chunk.palette;

import se.llbit.nbt.SpecificTag;
import version.v26_3.chunk.ChunkSection;
import version.v26_3.packets.builder.PacketBuilder;

import java.util.List;

public class SingleValuePalette extends Palette {
    int val;
    public SingleValuePalette(int val) {
        this.val = val;
    }

    public SingleValuePalette(PaletteType type, SpecificTag state) {
        if (type == PaletteType.BIOMES) {
            this.biomePalette();
        }
        this.val = this.registry.getStateId(state);
    }

    @Override
    public boolean hasData() {
        return false;
    }

    @Override
    public void write(PacketBuilder packet) {
        packet.writeByte((byte) 0);
        packet.writeVarInt(this.val);
    }

    @Override
    public String toString() {
        return "SingleValuePalette{" +
                "val=" + val +
                '}';
    }

    @Override
    public int stateFromId(int index) {
        if (index != 0) {
            throw new IllegalStateException("SingleValuePalette can only be accessed via index 0. Instead tried to access index " + index + ".");
        }
        return val;
    }

    @Override
    public int getIndexFor(ChunkSection section, int blockStateId) {
        return 0;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public int getBitsPerBlock() {
        return 0;
    }

    @Override
    public List<SpecificTag> toNbt() {
        return List.of(registry.getState(val).toNbt());
    }

    public Palette asNormalPalette() {
        return new Palette(new int[] { this.val });
    }
}
