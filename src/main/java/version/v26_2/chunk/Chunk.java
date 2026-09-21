package version.v26_2.chunk;

import core.config.Config;
import core.coordinates.Coordinate2D;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IChunk;
import core.interfaces.IChunkImageFactory;
import core.messages.Messages;
import core.util.Pair;
import se.llbit.nbt.*;
import version.v26_2.chunk.palette.*;
import version.v26_2.dimension.Dimension;
import version.v26_2.packets.DataTypeProvider;
import version.v26_2.packets.builder.PacketBuilder;
import version.v26_2.protocol.Protocol;
import version.v26_2.registries.RegistryManager;
import version.v26_2.world.WorldManager;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Basic chunk class. This is the single (flattened) implementation for the supported Minecraft
 * versions (26.x), previously reached through the
 * {@code Chunk_1_13 -> _1_14 -> _1_15 -> _1_16 -> _1_17 -> _1_18 -> _1_20 -> _26_1} inheritance
 * chain. The effective 26.x behavior is folded in here directly.
 *
 * <p>The class is non-final and the version-differentiating methods ({@link #parse(DataTypeProvider)},
 * {@link #parse(Tag)}, {@link #parseHeightMaps(DataTypeProvider)}, {@link #writeHeightMaps},
 * {@link #readChunkColumn}, {@link #createNewChunkSection}, {@link #parseSection},
 * {@link #toPacket}, {@link #toNbt}, {@link #addLevelNbtTags}, {@link #updateLight}) stay overridable
 * so a future Minecraft version can add a subclass overriding just the delta (see
 * docs/LEGACY_VERSION_REMOVAL_PLAN.md section 3.1).
 */
public class Chunk extends ChunkEntities implements IChunk {
    public static final int SECTION_HEIGHT = 16;
    public static final int SECTION_WIDTH = 16;
    protected static final int LIGHT_SIZE = 2048;

    static int minBlockSectionY = 0;
    static int maxBlockSectionY = 15;
    static int fullHeight;

    private final ChunkSection[] chunkSections;
    public CoordinateDim2D location;
    private Runnable afterParse;
    private Runnable onUnload;
    private boolean isNewChunk;
    private boolean saved;
    private boolean parsed;
    private ChunkImageFactory imageFactory;

    public ChunkHeightHandler getChunkHeightHandler() {
        return chunkHeightHandler;
    }

    private ChunkHeightHandler chunkHeightHandler;

    private final int dataVersion;

    SpecificTag heightMap;

    public Chunk(CoordinateDim2D location, int dataVersion) {
        super();

        this.dataVersion = dataVersion;
        this.saved = false;
        this.location = location;
        this.isNewChunk = false;

        chunkSections = new ChunkSection[getMaxLightSection() - getMinLightSection() + 1];
    }

    public static void setWorldHeight(int min_y, int height) {
        fullHeight = height;
        minBlockSectionY = min_y >> 4;
        maxBlockSectionY = minBlockSectionY + (height >> 4) - 1;
    }

    protected ChunkSection getChunkSection(int y) {
        if (y < getMinLightSection()) { return null; }
        if (y > getMaxLightSection()) { return null; }

        return chunkSections[y - getMinLightSection()];
    }

    protected void setChunkSection(int y, ChunkSection section) {
        if (y < getMinLightSection()) { return; }
        if (y > getMaxLightSection()) { return; }

        chunkSections[y - getMinLightSection()] = section;
    }

    protected int getMinLightSection() {
        return minBlockSectionY - 1;
    }

    protected int getMinBlockSection() {
        return minBlockSectionY;
    }

    protected int getMaxLightSection() {
        return maxBlockSectionY + 1;
    }

    protected int getMaxBlockSection() {
        return maxBlockSectionY;
    }

    protected Iterable<ChunkSection> getAllSections() {
        return () -> Arrays.stream(chunkSections).filter(Objects::nonNull).iterator();
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        if (saved && !this.saved) {
            getChunkImageFactory().markSaved();
        }
        this.saved = saved;
    }

    public void setOnUnload(Runnable r) {
        this.onUnload = r;
    }

    @Override
    public Dimension getDimension() {
        return (Dimension) location.getDimension();
    }

    /**
     * Allows a callback to be called when the chunk is done being parsed.
     */
    public void whenParsed(Runnable r) {
        if (parsed || isSaved()) {
            r.run();
        } else {
            afterParse = r;
        }
    }

    /**
     * Parse the chunk data (the 1.18+ {@code ClientboundLevelChunkWithLightPacket} layout).
     *
     * @param dataProvider network input
     */
    protected void parse(DataTypeProvider dataProvider) {
        raiseEvent("parse from packet");

        parseHeightMaps(dataProvider);

        int size = dataProvider.readVarInt();

        try {
            readChunkColumn(dataProvider.ofLength(size));

            parseBlockEntities(dataProvider);

            updateLight(dataProvider);
        } catch (Exception ex) {
            // seems to happen when there's blocks above 192 under some conditions
            System.out.println(Messages.console("console.chunk.parse_issue", location, ex.getMessage()));
            ex.printStackTrace();
        }

        // if the packet layout this version expects doesn't match what was actually sent, we typically won't get an
        // exception (reads just return whatever garbage bytes happen to be there) -- the surest sign of that is bytes
        // left unread at the end of the packet. When that happens the chunk we just built is likely garbage/corrupt.
        if (dataProvider.hasNext()) {
            System.err.println(
                "[chunk desync] " + dataProvider.remaining() + " unread byte(s) left over after parsing "
                    + "LevelChunkWithLight for chunk " + location + " (protocol "
                    + ((Protocol) Config.versionReporter().getProtocol()).getVersion() + "). The packet layout this version "
                    + "expects no longer matches what the server sent, so this chunk is likely corrupt."
            );
        }

        afterParse();
    }

    /**
     * As of protocol 775 (26.1), heightmaps in the chunk packet are no longer sent as an NBT
     * compound, but as an explicit array of (type, long array) pairs. We still store them
     * internally as the same NBT compound used by older versions (and by the on-disk chunk format),
     * so only the network (de)serialization changes here.
     */
    private static final String[] HEIGHTMAP_TYPES = {
        "WORLD_SURFACE_WG",
        "WORLD_SURFACE",
        "OCEAN_FLOOR_WG",
        "OCEAN_FLOOR",
        "MOTION_BLOCKING",
        "MOTION_BLOCKING_NO_LEAVES"
    };

    protected void parseHeightMaps(DataTypeProvider dataProvider) {
        CompoundTag tag = new CompoundTag();

        int count = dataProvider.readVarInt();
        for (int i = 0; i < count; i++) {
            int type = dataProvider.readVarInt();
            int longCount = dataProvider.readVarInt();
            long[] data = dataProvider.readLongArray(longCount);

            String name = type >= 0 && type < HEIGHTMAP_TYPES.length ? HEIGHTMAP_TYPES[type] : "UNKNOWN_" + type;
            tag.add(name, new LongArrayTag(data));
        }

        heightMap = tag;
    }

    protected void writeHeightMaps(PacketBuilder packet) {
        CompoundTag tag = heightMap != null ? heightMap.asCompound() : new CompoundTag();

        packet.writeVarInt(tag.size());
        for (NamedTag entry : tag) {
            packet.writeVarInt(indexOfType(entry.name()));

            long[] data = entry.getTag().longArray();
            packet.writeVarInt(data.length);
            packet.writeLongArray(data);
        }
    }

    private int indexOfType(String name) {
        for (int i = 0; i < HEIGHTMAP_TYPES.length; i++) {
            if (HEIGHTMAP_TYPES[i].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    public ChunkSection createNewChunkSection(byte y, Palette palette) {
        return new ChunkSection(y, palette, this);
    }

    protected ChunkSection parseSection(int sectionY, SpecificTag section) {
        return new ChunkSection(sectionY, section, this);
    }

    /**
     * Read a chunk column for 26.1+. Same as the 1.18 layout, except: a "fluid count" short follows
     * the block count, and the block/biome data arrays are no longer length-prefixed - their length
     * is derived from bits-per-entry instead.
     */
    public void readChunkColumn(DataTypeProvider dataProvider) {
        for (int sectionY = getMinBlockSection(); sectionY <= getMaxBlockSection() && dataProvider.hasNext(); sectionY++) {
            ChunkSection section = getChunkSection(sectionY);

            int blockCount = dataProvider.readShort();
            dataProvider.readShort(); // fluid count, not tracked separately

            Palette blockPalette = Palette.readPalette(dataProvider, PaletteType.BLOCKS);

            if (section == null) {
                section = createNewChunkSection((byte) (sectionY & 0xFF), blockPalette);
            } else {
                section.setBlockPalette(blockPalette);
            }

            section.setBlockCount(blockCount);
            section.setBlocks(dataProvider.readLongArray(ChunkSection.longsRequired(blockPalette.getBitsPerBlock())));

            Palette biomePalette = Palette.readPalette(dataProvider, PaletteType.BIOMES);
            section.setBiomePalette(biomePalette);
            section.setBiomes(dataProvider.readLongArray(ChunkSection.longsRequiredBiomes(biomePalette.getBitsPerBlock())));

            setChunkSection(sectionY, section);

            if (containsBlockEntities(blockPalette)) {
                findBlockEntities(section, sectionY);
            }
        }
    }

    protected void findBlockEntities(ChunkSection section, int sectionY) {
        BlockEntityRegistry blockEntities = RegistryManager.getInstance().getBlockEntityRegistry();
        BlockRegistry globalPalette = GlobalPaletteProvider.getGlobalPalette(getDataVersion());

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = globalPalette.getState(section.getNumericBlockStateAt(x, y, z));

                    if (blockEntities.isBlockEntity(state.getName())) {
                        Coordinate3D coords = new Coordinate3D(x, y, z).sectionLocalToGlobal(sectionY, this.location);
                        // Only generate a minimal block entity if we don't already have one.
                        // parseBlockEntities (called right after readChunkColumn) will overwrite
                        // this with the real NBT from the chunk packet. But if the chunk is
                        // re-parsed later (server re-sends it), findBlockEntities would overwrite
                        // the existing real data (with profile/patterns) with a minimal stub
                        // (just id+xyz). That race causes schematic exports to lose head skins
                        // and banner patterns. putIfAbsent prevents the overwrite.
                        if (this.getBlockEntity(coords) == null) {
                            this.addBlockEntity(coords, this.generateBlockEntity(state.getName(), coords));
                        }
                    }
                }
            }
        }
    }

    protected boolean containsBlockEntities(Palette p) {
        BlockEntityRegistry blockEntities = RegistryManager.getInstance().getBlockEntityRegistry();
        for (SpecificTag tag : p.toNbt()) {
            if (blockEntities.isBlockEntity(tag.get("Name").stringValue())) {
                return true;
            }
        }
        return false;
    }

    protected void parseBlockEntities(DataTypeProvider dataProvider) {
        int blockEntityCount = dataProvider.readVarInt();
        for (int i = 0; i < blockEntityCount; i++) {
            byte xz = dataProvider.readNext();
            int x = (xz >> 4) & 0b1111;
            int z = xz & 0b1111;
            int y = dataProvider.readShort();
            int type = dataProvider.readVarInt();

            // Get the exact coordinates in the world
            x = (this.getLocation().getX() * 16) + x;
            z = (this.getLocation().getZ() * 16) + z;

            SpecificTag tag = dataProvider.readNbtTag();
            if (tag instanceof CompoundTag entity) {
                String blockEntityID = RegistryManager.getInstance().getBlockEntityRegistry().getBlockEntityName(type);

                entity.add("id", new StringTag(blockEntityID));

                addBlockEntity(new Coordinate3D(x, y, z), entity);
            }
        }
    }

    /**
     * Generate network packet for this chunk (the 1.18+ {@code LevelChunkWithLight} packet, which
     * embeds the light data).
     */
    public PacketBuilder toPacket() {
        Protocol p = (Protocol) Config.versionReporter().getProtocol();
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(p.clientBound("LevelChunkWithLight"));

        packet.writeInt(location.getX());
        packet.writeInt(location.getZ());

        writeHeightMaps(packet);
        writeChunkSections(packet);

        // we don't include block entities - these chunks will be far away so they shouldn't be rendered anyway
        packet.writeVarInt(0);

        writeLightEdgesTrusted(packet);
        writeLightToPacket(packet);

        return packet;
    }

    /**
     * A separate light packet is not used in 1.18+ (light is embedded in the chunk packet), so this
     * returns {@code null}.
     */
    public PacketBuilder toLightPacket() {
        return null;
    }

    protected void writeChunkSections(PacketBuilder packet) {
        PacketBuilder columns = writeSectionData();
        byte[] columnArr = columns.toArray();
        packet.writeVarInt(columnArr.length);
        packet.writeByteArray(columnArr);
    }

    protected PacketBuilder writeSectionData() {
        PacketBuilder column = new PacketBuilder();
        for (ChunkSection section : getAllSections()) {
            if (section.getY() >= getMinBlockSection()) {
                section.write(column);
            }
        }

        return column;
    }

    /**
     * Versions &lt; 1.20 included a boolean for lighting data on edges; removed in 1.20+. No-op for
     * 26.x, kept as an extension point.
     */
    void parseLightEdgesTrusted(DataTypeProvider provider) {
    }

    void writeLightEdgesTrusted(PacketBuilder packet) {
    }

    /**
     * Write the embedded light data (sky + block light masks and arrays) into the chunk packet.
     */
    public void writeLightToPacket(PacketBuilder packet) {
        Pair<BitSet, PacketBuilder> skyLight = writeLightToPacket(ChunkSection::getSkyLight);
        Pair<BitSet, PacketBuilder> blockLight = writeLightToPacket(ChunkSection::getBlockLight);

        packet.writeBitSet(skyLight.getKey());
        packet.writeBitSet(blockLight.getKey());

        // empty masks we just set to 0
        packet.writeBitSet(new BitSet());
        packet.writeBitSet(new BitSet());

        packet.writeVarInt(skyLight.getKey().cardinality());
        packet.writeByteArray(skyLight.getValue().toArray());

        packet.writeVarInt(blockLight.getKey().cardinality());
        packet.writeByteArray(blockLight.getValue().toArray());
    }

    /**
     * Write one of the light arrays to a packet, return the mask and the array itself.
     */
    private Pair<BitSet, PacketBuilder> writeLightToPacket(Function<ChunkSection, byte[]> fn) {
        PacketBuilder packet = new PacketBuilder();
        BitSet mask = new BitSet();

        for (ChunkSection section : getAllSections()) {
            byte[] light = fn.apply(section);
            if (light == null || light.length == 0) { continue; }

            packet.writeVarInt(light.length);
            packet.writeByteArray(light);

            mask.set(section.getY() - getMinLightSection());
        }


        return new Pair<>(mask, packet);
    }

    public void updateLight(DataTypeProvider provider) {
        parseLightEdgesTrusted(provider);

        BitSet skyLightMask = provider.readBitSet();
        BitSet blockLightMask = provider.readBitSet();

        BitSet emptySkyLightMask = provider.readBitSet();
        BitSet emptyBlockLightMask = provider.readBitSet();

        int numSkyLight = provider.readVarInt();
        if (skyLightMask.cardinality() != numSkyLight) {
            throw new InputMismatchException("Number of provided skylight maps does not match provided mask: " + skyLightMask + " != " + numSkyLight);
        }

        parseLightArray(skyLightMask, emptySkyLightMask, provider, ChunkSection::setSkyLight, ChunkSection::getSkyLight);

        int numBlockLight = provider.readVarInt();
        if (blockLightMask.cardinality() != numBlockLight) {
            throw new InputMismatchException("Number of provided blocklight maps does not match provided mask: " + blockLightMask + " != " + numBlockLight);
        }

        parseLightArray(blockLightMask, emptyBlockLightMask, provider, ChunkSection::setBlockLight, ChunkSection::getBlockLight);
    }

    protected void parseLightArray(BitSet mask, BitSet emptyMask, DataTypeProvider provider, BiConsumer<ChunkSection, byte[]> c, Function<ChunkSection, byte[]> get) {
        for (int sectionY = getMinLightSection(); sectionY <= getMaxLightSection() && (!mask.isEmpty() || !emptyMask.isEmpty()); sectionY++) {
            ChunkSection s = getChunkSection(sectionY);
            if (s == null) {
                s = createNewChunkSection((byte) sectionY, Palette.empty());
                s.setBlocks(new long[256]);

                setChunkSection(sectionY, s);
            }

            // Mask tells us if a section is present or not
            if (!mask.get(sectionY - getMinLightSection())) {
                if (!emptyMask.get(sectionY - getMinLightSection())) {
                    c.accept(s, new byte[2048]);
                }
                emptyMask.set(sectionY - getMinLightSection(), false);
                continue;
            }
            mask.set(sectionY - getMinLightSection(), false);

            int skyLength = provider.readVarInt();
            byte[] data = provider.readByteArray(skyLength);

            c.accept(s, data);
        }
    }

    /**
     * Convert this chunk to NBT tags (the 1.18+ on-disk format: no {@code Level} wrapper, lowercase
     * {@code sections}).
     *
     * @return the nbt root tag
     */
    public NamedTag toNbt() {
        if (!hasSections()) {
            return null;
        }

        CompoundTag root = new CompoundTag();

        addLevelNbtTags(root);
        root.add("DataVersion", new IntTag(getDataVersion()));

        return new NamedTag("", root);
    }

    protected boolean hasSections() {
        return getAllSections().iterator().hasNext();
    }

    protected void addLevelNbtTags(CompoundTag map) {
        addGeneralLevelTags(map);
        map.add("yPos", new IntTag(getMinBlockSection()));

        map.add("Heightmaps", heightMap);
        map.add("Status", new StringTag("full"));

        CompoundTag structures = new CompoundTag();
        structures.add("References", new CompoundTag());
        structures.add("Starts", new CompoundTag());
        map.add("Structures", structures);

        map.add("sections", new ListTag(Tag.TAG_COMPOUND, getSectionList()));

        addBlockEntities(map);
    }

    protected void addGeneralLevelTags(CompoundTag map) {
        Coordinate2D offset = this.location.offsetChunk();
        map.add("xPos", new IntTag(offset.getX()));
        map.add("zPos", new IntTag(offset.getZ()));

        map.add("InhabitedTime", new LongTag(0));
        map.add("LastUpdate", new LongTag(0));
    }

    /**
     * Get a list of section tags for the NBT.
     */
    protected List<SpecificTag> getSectionList() {
        return Arrays.stream(chunkSections)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ChunkSection::getY))
                .map(ChunkSection::toNbt)
                .collect(Collectors.toList());
    }

    public int getNumericBlockStateAt(int x, int y, int z) {
        int sectionY = (int) Math.floor((double) y / SECTION_HEIGHT);
        ChunkSection section = getChunkSection(sectionY);
        if (section == null) {
            return 0;
        }

        return section.getNumericBlockStateAt(x, Math.floorMod(y, SECTION_HEIGHT), z);
    }

    public BlockState getBlockStateAt(Coordinate3D location) {
        return getBlockStateAt(location.getX(), location.getY(), location.getZ());
    }

    public BlockState getBlockStateAt(int x, int y, int z) {
        int id = getNumericBlockStateAt(x, y, z);
        if (id == 0) {
            return null;
        }

        return GlobalPaletteProvider.getGlobalPalette(getDataVersion()).getState(id);
    }

    /**
     * Read the biome resource location at the given world-space coordinates. Returns {@code null}
     * if the chunk section is missing or the section has no biome data.
     */
    public String getBiomeAt(int x, int y, int z) {
        int sectionY = (int) Math.floor((double) y / SECTION_HEIGHT);
        ChunkSection section = getChunkSection(sectionY);
        if (section == null) {
            return null;
        }
        return section.getBiomeAt(Math.floorMod(x, SECTION_WIDTH),
                            Math.floorMod(y, SECTION_HEIGHT),
                            Math.floorMod(z, SECTION_WIDTH));
    }

    protected void afterParse() {
        // ensure the chunk is (re)saved
        this.saved = false;
        this.parsed = true;

        // run the callback if one exists
        if (afterParse != null) {
            afterParse.run();
        }
    }

    /**
     * Mark this as a new chunk if it's sent in parts, which non-vanilla servers will do to send chunks to the client
     * before they are fully generated.
     */
    void markAsNew() {
        if (WorldManager.getInstance().markNewChunks()) {
            this.isNewChunk = true;
        }
    }

    protected boolean isNewChunk() {
        return isNewChunk;
    }

    /**
     * Parse this chunk from on-disk NBT (the 1.18+ format with lowercase {@code sections}).
     */
    public void parse(Tag tag) {
        raiseEvent("parse from nbt");

        tag.asCompound().get("sections").asList().forEach(section -> {
            int sectionY = section.get("Y").byteValue();
            // Only fill in sections this chunk doesn't have yet. Region.addChunk()
            // merges on-disk data into a chunk that was already parsed from the
            // network, and the fresh data must win over whatever is stored in the
            // region file.
            if (getChunkSection(sectionY) == null) {
                setChunkSection(sectionY, parseSection(sectionY, section));
            }
        });
        if (heightMap == null) {
            parseHeightMaps(tag);
        }
        this.parsed = true;
    }

    protected void parseHeightMaps(Tag tag) {
        heightMap = tag.asCompound().get("Heightmaps").asCompound();
    }

    /**
     * Mark this chunk as unsaved.
     */
    public void touch() {
        this.setSaved(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Chunk chunk = (Chunk) o;

        if (!Objects.equals(location, chunk.location)) return false;
        if (!Arrays.deepEquals(chunkSections, chunk.chunkSections)) return false;
        return Objects.equals(heightMap, chunk.heightMap);
    }

    @Override
    public int hashCode() {
        int result = location != null ? location.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(chunkSections);
        result = 31 * result + (heightMap != null ? heightMap.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Chunk{" +
            "dataVersion=" + dataVersion +
            ", location=" + location +
            ", chunkSections=" + Arrays.toString(chunkSections) +
            '}';
    }

    public void unload() {
        raiseEvent("unload");

        if (this.onUnload != null) {
            this.onUnload.run();
        }
    }

    public IChunkImageFactory getChunkImageFactory() {
        if (imageFactory == null) {
            chunkHeightHandler = new ChunkHeightHandler(this);

            // assignment should happen before running initialisation code
            imageFactory = new ChunkImageFactory(this);
            imageFactory.initialise();
        }
        return imageFactory;
    }

    public CoordinateDim2D getLocation() {
        return location;
    }

    public void updateBlock(Coordinate3D coords, int blockStateId) {
        updateBlock(coords, blockStateId, false);
    }

    public void updateBlock(Coordinate3D coords, int blockStateId, boolean suppressUpdate) {
        raiseEvent("update block");

        int sectionY = (int) Math.floor((double) coords.getY() / SECTION_HEIGHT);

        // if there's no section, we create an empty one
        if (getChunkSection(sectionY) == null) {
            ChunkSection newChunkSection = createNewChunkSection((byte) sectionY, Palette.empty());
            newChunkSection.setBlocks(new long[256]);
            setChunkSection(sectionY, newChunkSection);
        }

        // if the section is still null, that means it's likely out of the world bounds so just ignore this update
        ChunkSection section = getChunkSection(sectionY);
        if (section == null) { return; }

        section.setBlockAt(coords.chunkLocalToSectionLocal(), blockStateId);

        if (suppressUpdate) {
            return;
        }

        if (this.imageFactory != null) {
            this.chunkHeightHandler.updateHeight(coords);
            this.imageFactory.generateImages();
        }
    }

    /**
     * Update a number of blocks (the 1.16+ multi-block change layout using varlong-packed records).
     * toUpdate keeps track of which blocks have changed so that we can only redraw the chunk if
     * that's actually needed.
     * @param pos chunk selection
     * @param provider network input
     */
    public void updateBlocks(Coordinate3D pos, DataTypeProvider provider) {
        parseLightEdgesTrusted(provider);

        int count = provider.readVarInt();
        Collection<Coordinate3D> toUpdate = new ArrayList<>();
        while (count-- > 0) {
            long blockChange = provider.readVarLong();
            int blockId = (int) blockChange >>> 12;

            int x = (int) (blockChange >> 8) & 0x0F;
            int z = (int) (blockChange >> 4) & 0x0F;
            int y = (int) (blockChange     ) & 0x0F;

            // since updateBlock expects the height to be [0-256], we add in the section coordinates.
            Coordinate3D blockPos = new Coordinate3D(x, pos.getY() * 16 + y, z);
            toUpdate.add(blockPos);

            updateBlock(blockPos, blockId, true);
        }
        if (this.getChunkHeightHandler() != null) {
            this.getChunkHeightHandler().recomputeHeights(toUpdate);
        }
    }

    public boolean hasSeparateEntities() {
        return true;
    }
}
