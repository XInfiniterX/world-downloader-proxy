package version.v26_3.chunk;

import core.chunk.palette.BlockColors;
import core.config.Config;
import core.config.Version;
import core.coordinates.CoordinateDim2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.*;
import version.v26_3.chunk.BlockEntityRegistry;
import version.v26_3.dimension.Biome;
import version.v26_3.dimension.BiomeRegistry;
import version.v26_3.dimension.Dimension;
import version.v26_3.dimension.DimensionRegistry;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.registries.RegistryManager;
import version.v26_3.world.WorldManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test for the chunk-merge regression introduced when parse() started
 * running before loadChunk(): Region.addChunk() re-parses the on-disk chunk NBT
 * into the freshly parsed network chunk. The disk data must only fill gaps and
 * must never overwrite sections that already came from the network — otherwise
 * stale/empty chunks in existing region files permanently replace downloaded
 * terrain.
 */
public class ChunkDiskMergeTest {

    private static final CoordinateDim2D POS = new CoordinateDim2D(0, 0, Dimension.OVERWORLD);

    @BeforeAll
    static void setup() {
        WorldManager mock = mock(WorldManager.class);
        when(mock.getBlockColors()).thenReturn(mock(BlockColors.class));
        when(mock.getChunkFactory()).thenReturn(new ChunkFactory());

        Chunk.setWorldHeight(-63, 384);
        DimensionRegistry codecMock = mock(DimensionRegistry.class);
        Map<String, Biome> biomeMap = new HashMap<>();
        biomeMap.put("minecraft:plains", new Biome(0));
        when(codecMock.getBiomeRegistry()).thenReturn(new BiomeRegistry(biomeMap));
        when(mock.getDimensionRegistry()).thenReturn(codecMock);

        RegistryManager registryManager = mock(RegistryManager.class);
        when(registryManager.getBlockEntityRegistry()).thenReturn(new BlockEntityRegistry());
        RegistryManager.setInstance(registryManager);

        WorldManager.setInstance(mock);
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(Version.V26_3.protocolVersion);
    }

    private Chunk loadFixtureChunk() throws IOException, ClassNotFoundException {
        ObjectInputStream in = new PackageRemappingObjectInputStream(
            getClass().getClassLoader().getResourceAsStream("chunkdata_26_3"));
        ChunkBinary cb = (ChunkBinary) in.readObject();
        return cb.toChunk(POS);
    }

    private static CompoundTag emptySectionNbt(byte sectionY) {
        CompoundTag air = new CompoundTag();
        air.add("Name", new StringTag("minecraft:air"));

        CompoundTag blockStates = new CompoundTag();
        blockStates.add("palette", new ListTag(Tag.TAG_COMPOUND, java.util.List.of(air)));

        CompoundTag biomes = new CompoundTag();
        biomes.add("palette", new ListTag(Tag.TAG_STRING, java.util.List.of(new StringTag("minecraft:plains"))));

        CompoundTag section = new CompoundTag();
        section.add("Y", new ByteTag(sectionY));
        section.add("block_states", blockStates);
        section.add("biomes", biomes);
        return section;
    }

    private int firstNonAirBlock(Chunk c) {
        for (ChunkSection section : c.getAllSections()) {
            int baseY = section.getY() << 4;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int id = c.getNumericBlockStateAt(x, baseY + y, z);
                        if (id != 0) {
                            return (section.getY() << 16) | (y << 8) | (x << 4) | z;
                        }
                    }
                }
            }
        }
        return -1;
    }

    @Test
    void diskMergeDoesNotOverwriteNetworkParsedSections() throws IOException, ClassNotFoundException {
        Chunk fresh = loadFixtureChunk();

        // find a section with real blocks and a known non-air position
        ChunkSection targetSection = null;
        for (ChunkSection s : fresh.getAllSections()) {
            targetSection = s;
            break;
        }
        assertThat(targetSection).as("fixture must contain at least one section").isNotNull();

        int packed = firstNonAirBlock(fresh);
        assertThat(packed).as("fixture must contain at least one non-air block").isNotEqualTo(-1);

        int secY = packed >> 16;
        int localY = (packed >> 8) & 0xFF;
        int x = (packed >> 4) & 0xF;
        int z = packed & 0xF;
        int before = fresh.getNumericBlockStateAt(x, (secY << 4) + localY, z);
        assertThat(before).isNotEqualTo(0);

        // simulate the stale on-disk chunk at the same position: every section
        // exists but is completely empty (all-air palette, no data)
        CompoundTag oldNbt = new CompoundTag();
        java.util.List<SpecificTag> oldSections = new java.util.ArrayList<>();
        for (ChunkSection s : fresh.getAllSections()) {
            oldSections.add(emptySectionNbt((byte) s.getY()));
        }
        oldNbt.add("sections", new ListTag(Tag.TAG_COMPOUND, oldSections));
        oldNbt.add("Heightmaps", new CompoundTag());

        // this is what Region.addChunk() does with data already on disk
        fresh.parse(oldNbt);

        int after = fresh.getNumericBlockStateAt(x, (secY << 4) + localY, z);
        assertThat(after)
            .as("fresh network-parsed section must not be overwritten by stale disk data")
            .isEqualTo(before);
    }

    @Test
    void diskMergeFillsMissingSections() throws IOException, ClassNotFoundException {
        // a chunk that has no sections yet (e.g. loaded fresh) must still
        // receive the sections stored on disk
        Chunk empty = new Chunk(POS, Version.V26_3.dataVersion);

        CompoundTag oldNbt = new CompoundTag();
        oldNbt.add("sections", new ListTag(Tag.TAG_COMPOUND, java.util.List.of(emptySectionNbt((byte) 0))));
        oldNbt.add("Heightmaps", new CompoundTag());

        empty.parse(oldNbt);

        assertThat(empty.getChunkSection(0))
            .as("disk merge should fill sections the chunk lacks")
            .isNotNull();
    }

    @Test
    void whenParsedRunsImmediatelyForAlreadyParsedChunk() throws IOException, ClassNotFoundException {
        Chunk parsed = loadFixtureChunk();

        AtomicBoolean ran = new AtomicBoolean(false);
        parsed.whenParsed(() -> ran.set(true));

        assertThat(ran.get())
            .as("whenParsed() must run immediately when the chunk was already parsed")
            .isTrue();
    }
}
