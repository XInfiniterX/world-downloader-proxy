package version.v26_3.entity.specific;

import core.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.StringTag;
import version.v26_3.module.VersionModuleImpl;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.registries.RegistryLoader;
import version.v26_3.registries.RegistryManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayEntityTest {
    @BeforeEach
    void setUp() throws Exception {
        Config.setInstance(new Config());
        Config.setVersionModule(new VersionModuleImpl());
        RegistryManager.setInstance(null);
        RegistryManager.getInstance().setRegistries(RegistryLoader.forVersion("26.3"));
    }

    @Test
    void parsesCommonAndTextDisplayMetadata() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeOptionalChatEntry(out, 2, new StringTag("Custom"));
        writeVarIntEntry(out, 10, 6);
        writeVectorEntry(out, 11, 1, 2, 3);
        writeVectorEntry(out, 12, 2, 2, 2);
        writeQuaternionEntry(out, 13, 0.1f, 0.2f, 0.3f, 0.4f);
        writeByteEntry(out, 15, 3);
        writeVarIntEntry(out, 16, (7 << 4) | (12 << 20));
        writeFloatEntry(out, 17, 2.5f);
        writeFloatEntry(out, 18, 0.4f);
        writeFloatEntry(out, 19, 0.8f);
        writeFloatEntry(out, 20, 4);
        writeFloatEntry(out, 21, 5);
        writeVarIntEntry(out, 22, 0x123456);
        writeChatEntry(out, 23, new StringTag("Hello"));
        writeVarIntEntry(out, 24, 80);
        writeVarIntEntry(out, 25, 0x11223344);
        writeByteEntry(out, 26, 127);
        writeByteEntry(out, 27, 0x17);
        out.writeByte(0xFF);

        DisplayEntity.TextDisplay entity = new DisplayEntity.TextDisplay();
        entity.parseMetadata(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = addNbtData(entity);

        assertThat(nbt.get("Air").shortValue()).isEqualTo((short) 300);
        assertThat(nbt.get("CustomName").stringValue()).isEqualTo("Custom");
        assertThat(nbt.get("interpolation_duration").intValue()).isEqualTo(6);
        assertThat(nbt.get("billboard").stringValue()).isEqualTo("center");
        assertThat(nbt.get("brightness").get("block").intValue()).isEqualTo(7);
        assertThat(nbt.get("brightness").get("sky").intValue()).isEqualTo(12);
        assertThat(nbt.get("view_range").floatValue()).isEqualTo(2.5f);
        assertThat(nbt.get("glow_color_override").intValue()).isEqualTo(0x123456);
        ListTag translation = nbt.get("transformation").get("translation").asList();
        assertThat(translation.get(0).floatValue()).isEqualTo(1);
        assertThat(translation.get(1).floatValue()).isEqualTo(2);
        assertThat(translation.get(2).floatValue()).isEqualTo(3);
        assertThat(nbt.get("text").stringValue()).isEqualTo("Hello");
        assertThat(nbt.get("line_width").intValue()).isEqualTo(80);
        assertThat(nbt.get("background").intValue()).isEqualTo(0x11223344);
        assertThat(nbt.get("text_opacity").byteValue()).isEqualTo(127);
        assertThat(nbt.get("shadow").byteValue()).isEqualTo(1);
        assertThat(nbt.get("see_through").byteValue()).isEqualTo(1);
        assertThat(nbt.get("default_background").byteValue()).isEqualTo(1);
        assertThat(nbt.get("alignment").stringValue()).isEqualTo("right");
    }

    @Test
    void parsesInteractionMetadata() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeFloatEntry(out, 8, 3.5f);
        writeFloatEntry(out, 9, 1.25f);
        writeBooleanEntry(out, 10, true);
        out.writeByte(0xFF);

        DisplayEntity.InteractionEntity entity = new DisplayEntity.InteractionEntity();
        entity.parseMetadata(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = addNbtData(entity);

        assertThat(nbt.get("width").floatValue()).isEqualTo(3.5f);
        assertThat(nbt.get("height").floatValue()).isEqualTo(1.25f);
        assertThat(nbt.get("response").byteValue()).isEqualTo(1);
    }

    @Test
    void parsesCurrentArmorStandMetadataIndices() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeByteEntry(out, 15, 0x0D);
        out.writeByte(16);
        writeVarInt(out, 9);
        out.writeFloat(10);
        out.writeFloat(20);
        out.writeFloat(30);
        out.writeByte(0xFF);

        ArmorStand entity = new ArmorStand();
        entity.parseMetadata(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = addNbtData(entity);

        assertThat(nbt.get("Small").byteValue()).isEqualTo(1);
        assertThat(nbt.get("ShowArms").byteValue()).isEqualTo(1);
        assertThat(nbt.get("NoBasePlate").byteValue()).isEqualTo(1);
        assertThat(nbt.get("Marker").byteValue()).isEqualTo(0);
        assertThat(nbt.get("NoGravity").byteValue()).isEqualTo(1);
        assertThat(nbt.get("Pose").get("Head").asList().get(1).floatValue()).isEqualTo(20);
    }

    @Test
    void parsesItemFrameDirectionVisibilityItemAndRotation() throws Exception {
        RegistryManager.setInstance(null);
        RegistryManager.getInstance().setRegistries(RegistryLoader.forVersion("26.3"));
        int itemId = RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeByteEntry(out, 0, 0x20);
        writeVarIntTypedEntry(out, 8, 12, 2);
        out.writeByte(9);
        writeVarInt(out, 7);
        writeVarInt(out, 1);
        writeVarInt(out, itemId);
        writeVarInt(out, 0);
        writeVarInt(out, 0);
        writeVarIntEntry(out, 10, 5);
        out.writeByte(0xFF);

        ItemFrame entity = new ItemFrame();
        entity.parseMetadata(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = addNbtData(entity);

        assertThat(nbt.get("Facing").intValue()).isEqualTo(2);
        assertThat(nbt.get("Invisible").byteValue()).isEqualTo(1);
        assertThat(nbt.get("Fixed").byteValue()).isEqualTo(1);
        assertThat(nbt.get("ItemRotation").byteValue()).isEqualTo(5);
        assertThat(nbt.get("Item").get("id").stringValue()).isEqualTo("minecraft:diamond");
        assertThat(nbt.get("Item").get("count").intValue()).isEqualTo(1);
        assertThat(nbt.get("block_pos").intArray()).containsExactly(0, 0, 0);
    }

    @Test
    void preservesInvisibleItemFrameWithCustomPlayerHead() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writeByteEntry(out, 0, 0x20);
        out.writeByte(9);
        writeVarInt(out, 7);
        writePlayerHeadSlot(out);
        writeVarIntEntry(out, 10, 6);
        out.writeByte(0xFF);

        ItemFrame entity = new ItemFrame();
        entity.parseMetadata(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = addNbtData(entity);

        assertThat(nbt.get("Invisible").byteValue()).isEqualTo((byte) 1);
        assertThat(nbt.get("ItemRotation").byteValue()).isEqualTo((byte) 6);
        CompoundTag profile = nbt.get("Item").get("components").get("minecraft:profile").asCompound();
        assertThat(profile.get("name").stringValue()).isEqualTo("FrameHead");
        assertThat(profile.get("properties").asList().get(0).get("value").stringValue()).isEqualTo("dGV4dHVyZXM=");
    }

    @Test
    void writesItemFrameAttachmentRelativeToSchematicOrigin() throws Exception {
        ByteArrayOutputStream positionBytes = new ByteArrayOutputStream();
        DataOutputStream position = new DataOutputStream(positionBytes);
        position.writeDouble(10.5);
        position.writeDouble(64.0);
        position.writeDouble(-5.5);
        ItemFrame entity = new ItemFrame();
        entity.readPosition(DataTypeProvider.ofPacket(positionBytes.toByteArray()));
        CompoundTag nbt = new CompoundTag();
        Method method = ItemFrame.class.getDeclaredMethod("addNbtDataRelative", CompoundTag.class, core.coordinates.Coordinate3D.class);
        method.setAccessible(true);

        method.invoke(entity, nbt, new core.coordinates.Coordinate3D(10, 64, -6));

        assertThat(nbt.get("block_pos").intArray()).containsExactly(0, 0, 0);
        assertThat(nbt.get("TileX").intValue()).isZero();
        assertThat(nbt.get("TileY").intValue()).isZero();
        assertThat(nbt.get("TileZ").intValue()).isZero();
    }

    @Test
    void preservesCustomPlayerHeadInArmorStandHeadSlot() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        // Slot 5 = HEAD (wire order: 0=mainhand, 1=offhand, 2=feet, 3=legs, 4=chest, 5=head)
        out.writeByte(5);
        writePlayerHeadSlot(out);

        ArmorStand entity = new ArmorStand();
        entity.addEquipment(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = new CompoundTag();
        Method method = version.v26_3.entity.Entity.class.getDeclaredMethod("addNbtEquipment", CompoundTag.class);
        method.setAccessible(true);
        method.invoke(entity, nbt);

        CompoundTag head = nbt.get("equipment").asCompound().get("head").asCompound();
        CompoundTag profile = head.get("components").get("minecraft:profile").asCompound();
        assertThat(head.get("id").stringValue()).isEqualTo("minecraft:player_head");
        assertThat(profile.get("name").stringValue()).isEqualTo("FrameHead");
        assertThat(profile.get("properties").asList().get(0).get("signature").stringValue()).isEqualTo("signature");
    }

    @Test
    void preservesEnchantedDiamondChestplateOnArmorStand() throws Exception {
        // Regression test: previously, an unsupported DataComponent (e.g. enchantments)
        // caused the entire equipment to be lost. Now the item is preserved with its
        // enchantments even when other components are unsupported.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        // slot 4 = Chest, no next slot (wire order: 0=mainhand, 1=offhand, 2=feet, 3=legs, 4=chest, 5=head)
        out.writeByte(4);
        writeEnchantedDiamondChestplateSlot(out);

        ArmorStand entity = new ArmorStand();
        entity.addEquipment(DataTypeProvider.ofPacket(bytes.toByteArray()));
        CompoundTag nbt = new CompoundTag();
        Method method = version.v26_3.entity.Entity.class.getDeclaredMethod("addNbtEquipment", CompoundTag.class);
        method.setAccessible(true);
        method.invoke(entity, nbt);

        CompoundTag chest = nbt.get("equipment").asCompound().get("chest").asCompound();
        assertThat(chest.get("id").stringValue()).isEqualTo("minecraft:diamond_chestplate");
        assertThat(chest.get("count").intValue()).isEqualTo(1);
        CompoundTag enchantments = chest.get("components").get("minecraft:enchantments").asCompound();
        assertThat(enchantments.get("levels").asCompound().get("0").intValue()).isEqualTo(4);
    }

    @Test
    void slotReaderConsumesEmptyComponentPatch() {
        DataTypeProvider provider = DataTypeProvider.ofPacket(new byte[] { 1, 2, 0, 0, 99 });

        assertThat(provider.readSlot()).isNotNull();
        assertThat(provider.readNext()).isEqualTo((byte) 99);
    }

    @Test
    void reportsUnsupportedMetadataSerializer() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(100);
        writeVarInt(out, 99);
        out.writeByte(0xFF);
        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());

        new DisplayEntity.TextDisplay().parseMetadata(provider);

        assertThat(provider.getCompleteness().isComplete()).isFalse();
        assertThat(provider.getCompleteness().getDiagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("UNSUPPORTED_ENTITY_DATA_SERIALIZER");
            assertThat(diagnostic.numericId()).isEqualTo(99);
            assertThat(diagnostic.bufferPosition()).isZero();
        });
    }

    private static CompoundTag addNbtData(Object entity) throws Exception {
        Method method = entity instanceof DisplayEntity
            ? DisplayEntity.class.getDeclaredMethod("addNbtData", CompoundTag.class)
            : entity.getClass().getDeclaredMethod("addNbtData", CompoundTag.class);
        method.setAccessible(true);
        CompoundTag nbt = new CompoundTag();
        method.invoke(entity, nbt);
        return nbt;
    }

    private static void writePlayerHeadSlot(DataOutputStream out) throws Exception {
        writeVarInt(out, 1);
        writeVarInt(out, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:player_head"));
        writeVarInt(out, 1);
        writeVarInt(out, 0);
        writeVarInt(out, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:profile"));
        writeVarInt(out, 1);
        out.writeLong(0x0011223344556677L);
        out.writeLong(0x8899AABBCCDDEEFFL);
        writeString(out, "FrameHead");
        writeVarInt(out, 1);
        writeString(out, "textures");
        writeString(out, "dGV4dHVyZXM=");
        out.writeBoolean(true);
        writeString(out, "signature");
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
        out.writeBoolean(false);
    }

    private static void writeEnchantedDiamondChestplateSlot(DataOutputStream out) throws Exception {
        writeVarInt(out, 1); // count
        writeVarInt(out, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond_chestplate"));
        writeVarInt(out, 1); // 1 added component
        writeVarInt(out, 0); // 0 removed components
        // enchantments component: id + data (1 enchantment: id=0, level=4)
        writeVarInt(out, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:enchantments"));
        writeVarInt(out, 1); // 1 enchantment
        writeVarInt(out, 0); // enchantment id 0
        writeVarInt(out, 4); // level 4
    }

    private static void writeString(DataOutputStream out, String value) throws Exception {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(out, encoded.length);
        out.write(encoded);
    }

    private static void writeChatEntry(DataOutputStream out, int index, StringTag value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 5);
        value.writeType(out);
        value.write(out);
    }

    private static void writeOptionalChatEntry(DataOutputStream out, int index, StringTag value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 6);
        out.writeBoolean(true);
        value.writeType(out);
        value.write(out);
    }

    private static void writeVarIntEntry(DataOutputStream out, int index, int value) throws Exception {
        writeVarIntTypedEntry(out, index, 1, value);
    }

    private static void writeVarIntTypedEntry(DataOutputStream out, int index, int type, int value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, type);
        writeVarInt(out, value);
    }

    private static void writeByteEntry(DataOutputStream out, int index, int value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 0);
        out.writeByte(value);
    }

    private static void writeBooleanEntry(DataOutputStream out, int index, boolean value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 8);
        out.writeBoolean(value);
    }

    private static void writeFloatEntry(DataOutputStream out, int index, float value) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 3);
        out.writeFloat(value);
    }

    private static void writeVectorEntry(DataOutputStream out, int index, float x, float y, float z) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 39);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
    }

    private static void writeQuaternionEntry(DataOutputStream out, int index, float x, float y, float z, float w) throws Exception {
        out.writeByte(index);
        writeVarInt(out, 40);
        out.writeFloat(x);
        out.writeFloat(y);
        out.writeFloat(z);
        out.writeFloat(w);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }
}
