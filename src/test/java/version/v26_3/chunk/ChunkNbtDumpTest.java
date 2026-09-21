package version.v26_3.chunk;

import core.config.Config;
import core.config.Version;
import core.coordinates.CoordinateDim2D;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.*;
import version.v26_3.dimension.Dimension;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.world.WorldManager;
import version.v26_3.dimension.Biome;
import version.v26_3.dimension.BiomeRegistry;
import version.v26_3.dimension.DimensionRegistry;
import version.v26_3.registries.RegistryManager;
import core.chunk.palette.BlockColors;

import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

public class ChunkNbtDumpTest {
    @Test
    void dumpStoredChunkNbt() throws Exception {
        WorldManager mock = mock(WorldManager.class);
        when(mock.getBlockColors()).thenReturn(mock(BlockColors.class));
        when(mock.getChunkFactory()).thenReturn(new ChunkFactory());
        DimensionRegistry codecMock = mock(DimensionRegistry.class);
        Map<String, Biome> biomeMap = new HashMap<>();
        biomeMap.put("minecraft:plains", new Biome(0));
        when(codecMock.getBiomeRegistry()).thenReturn(new BiomeRegistry(biomeMap));
        when(mock.getDimensionRegistry()).thenReturn(codecMock);
        WorldManager.setInstance(mock);
        RegistryManager.setInstance(mock(RegistryManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(Version.V26_3.protocolVersion);

        ObjectInputStream in = new PackageRemappingObjectInputStream(
            ChunkNbtDumpTest.class.getClassLoader().getResourceAsStream("chunkdata_26_3"));
        ChunkBinary cb = (ChunkBinary) in.readObject();
        NamedTag nbt = cb.getNbt();
        CompoundTag root = (CompoundTag) nbt.tag;

        System.out.println("=== ROOT TAGS ===");
        for (NamedTag t : root) {
            System.out.println(t.name() + " : " + t.tag.getClass().getSimpleName() + " " + shortValue(t.tag));
        }

        Tag sectionsTag = root.get("sections");
        if (!sectionsTag.isError()) {
            ListTag sections = (ListTag) sectionsTag;
            System.out.println("=== sections: " + sections.size() + " ===");
            for (int i = 0; i < Math.min(3, sections.size()); i++) {
                CompoundTag sec = (CompoundTag) sections.get(i);
                System.out.println("--- section " + i + " ---");
                for (NamedTag t : sec) {
                    System.out.println("  " + t.name() + " : " + t.tag.getClass().getSimpleName());
                }
                Tag bs = sec.get("block_states");
                if (!bs.isError()) {
                    CompoundTag bsc = (CompoundTag) bs;
                    Tag pal = bsc.get("palette");
                    Tag data = bsc.get("data");
                    System.out.println("  block_states.palette isError=" + pal.isError()
                        + " data.isError=" + data.isError());
                    if (!pal.isError()) {
                        ListTag p = (ListTag) pal;
                        System.out.println("  palette size=" + p.size() + " elementType=" + p.getType());
                        for (int j = 0; j < Math.min(3, p.size()); j++) {
                            System.out.println("    [" + j + "] " + p.get(j));
                        }
                        int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(0, p.size() - 1)));
                        int expectedLongs = (int) Math.ceil(4096.0 / (64 / bits));
                        System.out.println("  bits(from size)=" + bits + " expectedDataLongs=" + expectedLongs
                            + " actual=" + (data.isError() ? "MISSING" : ((LongArrayTag) data).longArray().length));
                    }
                }
                Tag bio = sec.get("biomes");
                if (!bio.isError()) {
                    CompoundTag bc = (CompoundTag) bio;
                    Tag pal = bc.get("palette");
                    Tag data = bc.get("data");
                    if (!pal.isError()) {
                        ListTag p = (ListTag) pal;
                        System.out.println("  biomes.palette size=" + p.size() + " elementType=" + p.getType()
                            + " first=" + p.get(0));
                    }
                    System.out.println("  biomes.data=" + (data.isError() ? "MISSING" : ((LongArrayTag) data).longArray().length + " longs"));
                }
            }
        }
        Tag dver = root.get("DataVersion");
        System.out.println("DataVersion=" + (dver.isError() ? "MISSING" : dver.toString()));
        Tag status = root.get("Status");
        System.out.println("Status=" + (status.isError() ? "MISSING" : status));

        // now dump what the CURRENT toNbt produces for the same chunk
        System.out.println("=== CURRENT toNbt() ===");
        Chunk parsed = cb.toChunk(new CoordinateDim2D(-362, 77, Dimension.OVERWORLD));
        NamedTag fresh = parsed.toNbt();
        if (fresh == null) {
            System.out.println("toNbt() returned NULL");
        } else {
            CompoundTag froot = (CompoundTag) fresh.tag;
            for (NamedTag t : froot) {
                System.out.println(t.name() + " : " + t.tag.getClass().getSimpleName());
            }
            Tag sec = froot.get("sections");
            System.out.println("sections isError=" + sec.isError() + (sec.isError() ? "" : " size=" + ((ListTag) sec).size()));
        }

        // === FULL ROUND TRIP: toNbt -> ChunkBinary -> McaFile -> disk -> readFile -> getNbt ===
        java.nio.file.Path tmp = java.nio.file.Paths.get("/tmp/mca-out");
        try {
            Config.getInstance().worldOutputDir = tmp.resolve("world").toString();

            Chunk parsed2 = cb.toChunk(new CoordinateDim2D(-362, 77, Dimension.OVERWORLD));
            version.v26_3.region.McaFile mca = new version.v26_3.region.McaFile(
                new CoordinateDim2D(-362, 77, Dimension.OVERWORLD).chunkToDimRegion());
            ChunkBinary bin = ChunkBinary.fromChunk(parsed2);
            int pos = new CoordinateDim2D(-362, 77, Dimension.OVERWORLD).toRegionLocal().getX() & 31;
            CoordinateDim2D loc = new CoordinateDim2D(-362, 77, Dimension.OVERWORLD);
            core.coordinates.Coordinate2D local = loc.toRegionLocal();
            int slot = 4 * ((local.getX() & 31) + (local.getZ() & 31) * 32);
            mca.addChunks(java.util.Collections.singletonMap(slot, bin));
            mca.write();

            java.io.File f = new java.io.File(tmp.resolve("world")
                .resolve("dimensions").resolve("minecraft").resolve("overworld")
                .resolve("region").resolve("r.-12.2.mca").toString());
            System.out.println("=== MCA round trip: file exists=" + f.exists() + " size=" + f.length());

            version.v26_3.region.McaFile readBack = new version.v26_3.region.McaFile(f);
            ChunkBinary back = readBack.getChunkBinary(new CoordinateDim2D(-362, 77, Dimension.OVERWORLD));
            System.out.println("chunk read back=" + (back != null));
            if (back != null) {
                NamedTag rn = back.getNbt();
                Tag rsec = ((CompoundTag) rn.tag).get("sections");
                System.out.println("read-back sections=" + ((ListTag) rsec).size());
            }
        } finally {
            Config.getInstance().worldOutputDir = "world";
        }
    }

    private String shortValue(Tag t) {
        if (t instanceof IntTag || t instanceof LongTag || t instanceof ByteTag || t instanceof StringTag || t instanceof ShortTag) {
            return "= " + t;
        }
        return "";
    }
}
