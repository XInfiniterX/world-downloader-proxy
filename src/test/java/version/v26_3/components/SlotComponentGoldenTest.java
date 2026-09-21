package version.v26_3.components;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.StringTag;
import version.v26_3.container.Slot;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.registries.RegistryLoader;
import version.v26_3.registries.RegistryManager;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SlotComponentGoldenTest {
    @BeforeEach
    void setUp() throws Exception {
        RegistryManager.setInstance(null);
        RegistryManager.getInstance().setRegistries(RegistryLoader.forVersion("26.3"));
    }

    @Test
    void decodesCustomDataAndKeepsPacketAligned() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond"));
        writeVarInt(output, 1);
        writeVarInt(output, 0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:custom_data"));
        CompoundTag customData = new CompoundTag();
        customData.add("key", new StringTag("value"));
        customData.writeType(output);
        customData.write(output);
        output.writeByte(99);

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        Slot slot = provider.readSlot();

        assertThat(slot.toNbt().get("components").get("minecraft:custom_data").get("key").stringValue())
                .isEqualTo("value");
        assertThat(provider.readNext()).isEqualTo((byte) 99);
    }

    @Test
    void decodesPartialPlayerProfile() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:player_head"));
        writeVarInt(output, 1);
        writeVarInt(output, 0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:profile"));
        writeVarInt(output, 0);
        output.writeBoolean(true);
        writeString(output, "DynamicHead");
        output.writeBoolean(false);
        writeVarInt(output, 0);
        output.writeBoolean(false);
        output.writeBoolean(false);
        output.writeBoolean(false);
        output.writeBoolean(false);
        output.writeByte(99);

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        CompoundTag profile = provider.readSlot().toNbt().get("components").get("minecraft:profile").asCompound();

        assertThat(profile.get("name").stringValue()).isEqualTo("DynamicHead");
        assertThat(profile.get("id").isError()).isTrue();
        assertThat(profile.get("properties").isError()).isTrue();
        assertThat(provider.readNext()).isEqualTo((byte) 99);
    }

    @Test
    void decodesCompletePlayerProfileWithPropertiesAndSkinPatch() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:player_head"));
        writeVarInt(output, 1);
        writeVarInt(output, 0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:profile"));
        writeVarInt(output, 1);
        output.writeLong(0x0011223344556677L);
        output.writeLong(0x8899AABBCCDDEEFFL);
        writeString(output, "Player");
        writeVarInt(output, 2);
        writeString(output, "textures");
        writeString(output, "dGV4dHVyZXM=");
        output.writeBoolean(true);
        writeString(output, "signature");
        writeString(output, "textures");
        writeString(output, "c2Vjb25k");
        output.writeBoolean(false);
        output.writeBoolean(true);
        writeString(output, "minecraft:entity/player/slim/alex");
        output.writeBoolean(false);
        output.writeBoolean(true);
        writeString(output, "minecraft:entity/equipment/wings/elytra");
        output.writeBoolean(true);
        writeVarInt(output, 1);
        output.writeByte(99);

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        CompoundTag profile = provider.readSlot().toNbt().get("components").get("minecraft:profile").asCompound();

        assertThat(profile.get("name").stringValue()).isEqualTo("Player");
        assertThat(profile.get("id").intArray()).containsExactly(0x00112233, 0x44556677, 0x8899AABB, 0xCCDDEEFF);
        assertThat(profile.get("properties").asList()).hasSize(2);
        assertThat(profile.get("properties").asList().get(0).get("value").stringValue()).isEqualTo("dGV4dHVyZXM=");
        assertThat(profile.get("properties").asList().get(0).get("signature").stringValue()).isEqualTo("signature");
        assertThat(profile.get("properties").asList().get(1).get("signature").isError()).isTrue();
        assertThat(profile.get("texture").stringValue()).isEqualTo("minecraft:entity/player/slim/alex");
        assertThat(profile.get("elytra").stringValue()).isEqualTo("minecraft:entity/equipment/wings/elytra");
        assertThat(profile.get("model").stringValue()).isEqualTo("slim");
        assertThat(provider.readNext()).isEqualTo((byte) 99);
        assertThat(provider.getCompleteness().isComplete()).isTrue();
    }

    @Test
    void decodesDecorativePrimitiveAndCustomModelComponents() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:leather_chestplate"));
        writeVarInt(output, 8);
        writeVarInt(output, 0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:max_damage"));
        writeVarInt(output, 250);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:damage"));
        writeVarInt(output, 5);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:unbreakable"));
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:rarity"));
        writeVarInt(output, 3);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:enchantment_glint_override"));
        output.writeBoolean(true);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:dyed_color"));
        output.writeInt(0x112233);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:custom_model_data"));
        writeVarInt(output, 2);
        output.writeFloat(1.5f);
        output.writeFloat(-2.0f);
        writeVarInt(output, 2);
        output.writeBoolean(true);
        output.writeBoolean(false);
        writeVarInt(output, 1);
        writeString(output, "wariant-żółty");
        writeVarInt(output, 2);
        output.writeInt(0x102030);
        output.writeInt(0xA0B0C0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:lore"));
        writeVarInt(output, 1);
        CompoundTag loreLine = new CompoundTag();
        loreLine.add("text", new StringTag("Linia"));
        loreLine.add("color", new StringTag("gold"));
        loreLine.add("font", new StringTag("minecraft:default"));
        loreLine.writeType(output);
        loreLine.write(output);
        output.writeByte(99);

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        CompoundTag components = provider.readSlot().toNbt().get("components").asCompound();

        assertThat(components.get("minecraft:max_damage").intValue()).isEqualTo(250);
        assertThat(components.get("minecraft:damage").intValue()).isEqualTo(5);
        assertThat(components.get("minecraft:unbreakable").asCompound().isEmpty()).isTrue();
        assertThat(components.get("minecraft:rarity").stringValue()).isEqualTo("epic");
        assertThat(components.get("minecraft:enchantment_glint_override").byteValue()).isEqualTo((byte) 1);
        assertThat(components.get("minecraft:dyed_color").intValue()).isEqualTo(0x112233);
        CompoundTag model = components.get("minecraft:custom_model_data").asCompound();
        assertThat(model.get("floats").asList().get(0).floatValue()).isEqualTo(1.5f);
        assertThat(model.get("flags").byteArray()).containsExactly(1, 0);
        assertThat(model.get("strings").asList().get(0).stringValue()).isEqualTo("wariant-żółty");
        assertThat(model.get("colors").intArray()).containsExactly(0x102030, 0xA0B0C0);
        CompoundTag lore = components.get("minecraft:lore").asList().get(0).asCompound();
        assertThat(lore.get("text").stringValue()).isEqualTo("Linia");
        assertThat(lore.get("color").stringValue()).isEqualTo("gold");
        assertThat(lore.get("font").stringValue()).isEqualTo("minecraft:default");
        assertThat(provider.readNext()).isEqualTo((byte) 99);
        assertThat(provider.getCompleteness().isComplete()).isTrue();
    }

    @Test
    void rejectsUnsupportedComponentWithStructuredDiagnostic() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond"));
        writeVarInt(output, 1);
        writeVarInt(output, 0);
        // Use a component ID outside the registry range to simulate an unknown component.
        int unknownComponentId = 9999;
        int componentPosition = bytes.size();
        writeVarInt(output, unknownComponentId);

        Slot slot = DataTypeProvider.ofPacket(bytes.toByteArray()).readSlot();

        // With the resilient parser, unsupported components no longer throw;
        // instead the patch is marked as not fully parsed and a diagnostic is recorded.
        assertThat(slot).isNotNull();
        assertThat(slot.getComponents().isFullyParsed()).isFalse();
        assertThat(slot.getCompleteness().isComplete()).isFalse();
        var diagnostics = slot.getCompleteness().getDiagnostics();
        assertThat(diagnostics).hasSize(1);
        assertThat(diagnostics.get(0).code()).isEqualTo("UNSUPPORTED_DATA_COMPONENT");
        assertThat(diagnostics.get(0).protocolVersion()).isEqualTo("26.3");
        assertThat(diagnostics.get(0).numericId()).isEqualTo(unknownComponentId);
        assertThat(diagnostics.get(0).resourceLocation()).isNull();
        assertThat(diagnostics.get(0).bufferPosition()).isEqualTo(componentPosition);
    }

    @Test
    void decodesEquippableWithRegistryReferencedSound() throws Exception {
        // Regression test: equippable's sound field uses IdOr<SoundEvent> with a VarInt
        // discriminator (0 = inline, n > 0 = registry ID n-1). Previously the codec read a
        // Boolean discriminator, which mis-aligned the stream for any registry-referenced
        // sound (the common case for all armor items), causing the entire equipment slot
        // to be dropped — resulting in empty ArmorStands.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1); // count
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond_chestplate"));
        writeVarInt(output, 1); // added components
        writeVarInt(output, 0); // removed components
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:equippable"));
        // equippable payload:
        writeVarInt(output, 6); // slot = chest
        // sound: IdOr<SoundEvent> — VarInt discriminator = 5 → registry ID 4
        writeVarInt(output, 5);
        output.writeBoolean(false); // no model
        output.writeBoolean(false); // no camera overlay
        output.writeBoolean(false); // no allowed entities
        output.writeBoolean(true);  // dispensable
        output.writeBoolean(true);  // swappable
        output.writeBoolean(true);  // damageable
        output.writeBoolean(false); // equip_on_interact
        output.writeByte(99); // sentinel to verify alignment

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        CompoundTag components = provider.readSlot().toNbt().get("components").asCompound();

        CompoundTag equippable = components.get("minecraft:equippable").asCompound();
        assertThat(equippable.get("slot").intValue()).isEqualTo(6);
        assertThat(equippable.get("sound").asCompound().get("sound_id").intValue()).isEqualTo(4);
        assertThat(equippable.get("dispensable").byteValue()).isEqualTo((byte) 1);
        assertThat(equippable.get("swappable").byteValue()).isEqualTo((byte) 1);
        assertThat(equippable.get("damageable").byteValue()).isEqualTo((byte) 1);
        assertThat(equippable.get("equip_on_interact").byteValue()).isEqualTo((byte) 0);
        // Critical: buffer must be aligned — sentinel byte is readable
        assertThat(provider.readNext()).isEqualTo((byte) 99);
        assertThat(provider.getCompleteness().isComplete()).isTrue();
    }

    @Test
    void decodesEquippableWithInlineSound() throws Exception {
        // Equippable with an inline SoundEvent (VarInt discriminator = 0).
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        writeVarInt(output, 1);
        writeVarInt(output, RegistryManager.getInstance().getItemRegistry().getItemId("minecraft:diamond_helmet"));
        writeVarInt(output, 1);
        writeVarInt(output, 0);
        writeVarInt(output, RegistryManager.getInstance().getDataComponentRegistry().getProtocolId("minecraft:equippable"));
        writeVarInt(output, 5); // slot = head
        writeVarInt(output, 0); // sound: inline (discriminator = 0)
        writeString(output, "minecraft:entity.armor.equip_diamond");
        output.writeBoolean(false); // no fixed range
        output.writeBoolean(false); // no model
        output.writeBoolean(false); // no camera overlay
        output.writeBoolean(false); // no allowed entities
        output.writeBoolean(true);  // dispensable
        output.writeBoolean(true);  // swappable
        output.writeBoolean(true);  // damageable
        output.writeBoolean(false); // equip_on_interact
        output.writeByte(99);

        DataTypeProvider provider = DataTypeProvider.ofPacket(bytes.toByteArray());
        CompoundTag components = provider.readSlot().toNbt().get("components").asCompound();

        CompoundTag equippable = components.get("minecraft:equippable").asCompound();
        assertThat(equippable.get("slot").intValue()).isEqualTo(5);
        CompoundTag sound = equippable.get("sound").asCompound();
        assertThat(sound.get("data").asCompound().get("sound_name").stringValue())
                .isEqualTo("minecraft:entity.armor.equip_diamond");
        assertThat(provider.readNext()).isEqualTo((byte) 99);
        assertThat(provider.getCompleteness().isComplete()).isTrue();
    }

    private static void writeString(DataOutputStream output, String value) throws Exception {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(output, encoded.length);
        output.write(encoded);
    }

    private static void writeVarInt(DataOutputStream output, int value) throws Exception {
        while ((value & ~0x7F) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }
}
