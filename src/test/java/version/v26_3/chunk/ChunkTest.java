package version.v26_3.chunk;

import core.chunk.palette.BlockColors;
import core.config.Config;
import core.config.Version;
import core.coordinates.CoordinateDim2D;
import org.junit.jupiter.api.Test;
import version.v26_3.dimension.Biome;
import version.v26_3.dimension.BiomeRegistry;
import version.v26_3.dimension.Dimension;
import version.v26_3.dimension.DimensionRegistry;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.packets.builder.PacketBuilderAndParserTest;
import version.v26_3.registries.RegistryManager;
import version.v26_3.world.WorldManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkTest extends PacketBuilderAndParserTest {
    @Override
    public void afterEach() {
    }

    CoordinateDim2D pos = new CoordinateDim2D(0, 0, Dimension.OVERWORLD);
    ChunkBinary cb;

    /**
     * Tests that reading in binary chunk data (as stored in MCA Files), writing it to a network packet and parsing the
     * network packet leads to the same block states. Note that this does not ensure that the client is necessarily able
     * to understand the chunk, just that it is internally consistent.
     */
    private void testFor(int protocolVersion, String dataFile) throws IOException, ClassNotFoundException {
        // set up mock
        WorldManager mock = mock(WorldManager.class);
        when(mock.getBlockColors()).thenReturn(mock(BlockColors.class));
        when(mock.getChunkFactory()).thenReturn(new ChunkFactory());

        Chunk.setWorldHeight(-63, 384);
        DimensionRegistry codecMock = mock(DimensionRegistry.class);
        Map<String, Biome> biomeMap = new HashMap<>();
        biomeMap.put("minecraft:badlands", new Biome(0));
        biomeMap.put("minecraft:forest", new Biome(1));
        biomeMap.put("minecraft:river", new Biome(2));
        biomeMap.put("minecraft:plains", new Biome(3));
        when(codecMock.getBiomeRegistry()).thenReturn(new BiomeRegistry(biomeMap));
        when(mock.getDimensionRegistry()).thenReturn(codecMock);

        RegistryManager registryManager = mock(RegistryManager.class);
        when(registryManager.getBlockEntityRegistry()).thenReturn(new BlockEntityRegistry());
        RegistryManager.setInstance(registryManager);

        WorldManager.setInstance(mock);

        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(protocolVersion);

        ObjectInputStream in = new PackageRemappingObjectInputStream(ChunkTest.class.getClassLoader().getResourceAsStream(dataFile));
        cb = (ChunkBinary) in.readObject();

        Chunk c = cb.toChunk(pos);

        builder = c.toPacket();

        DataTypeProvider parser = getParser();
        CoordinateDim2D coords = new CoordinateDim2D(parser.readInt(), parser.readInt(), pos.getDimension());
        UnparsedChunk up = new UnparsedChunk(coords);
        up.provider = parser;

        assertThat(ChunkFactory.parseChunk(up, mock)).isEqualTo(c);

        Chunk.setWorldHeight(0, 256);
    }

    @Test
    void chunk_26_3() throws IOException, ClassNotFoundException {
        testFor(Version.V26_3.protocolVersion, "chunkdata_26_3");
    }

    @Test
    void parsesMinecraft263ByteEncodedLightMasks() {
        Chunk.setWorldHeight(0, 256);
        WorldManager.setInstance(mock(WorldManager.class));
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        Config.setProtocolVersion(Version.V26_3.protocolVersion);
        try {
            byte[] skyLight = new byte[2048];
            Arrays.fill(skyLight, (byte) 0x5A);
            PacketBuilder packet = new PacketBuilder();
            packet.writeByteArray(new byte[] {0x01, 0x02, 0x00, 0x00, 0x00, 0x01, (byte) 0x80, 0x10});
            packet.writeByteArray(skyLight);
            packet.writeByte((byte) 0x00);
            DataTypeProvider provider = new DataTypeProvider(packet.toArray());

            Chunk chunk = new Chunk(pos, Version.V26_3.dataVersion);
            chunk.updateLight(provider);

            assertThat(chunk.getChunkSection(0).getSkyLight()).isEqualTo(skyLight);
            assertThat(provider.hasNext()).isFalse();
        } finally {
            Chunk.setWorldHeight(0, 256);
        }
    }
}