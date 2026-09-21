package version.v26_3.components;

import core.snapshot.SnapshotCompleteness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.builder.PacketBuilder;
import version.v26_3.registries.DataComponentRegistry;
import version.v26_3.registries.RegistryLoader;
import version.v26_3.registries.RegistryManager;
import version.v26_3.schematic.DynamicRegistry;
import version.v26_3.world.WorldManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewDataComponentCodecsTest {
    @BeforeEach
    void setUp() throws Exception {
        DynamicRegistry.getInstance().clear();
        RegistryLoader loader = RegistryLoader.forVersion("26.3");
        RegistryManager.setInstance(null);
        RegistryManager.getInstance().setRegistries(loader);
        WorldManager worldManager = mock(WorldManager.class);
        when(worldManager.getEntityMap()).thenReturn(loader.generateEntityNames());
        WorldManager.setInstance(worldManager);
    }

    @Test
    void decodesAnimationAndSimpleComponents() {
        PacketBuilder animation = new PacketBuilder();
        animation.writeVarInt(1);
        animation.writeVarInt(0);
        CompoundTag attack = decode("minecraft:attack_animation", animation).asCompound();
        assertThat(attack.get("type").stringValue()).isEqualTo("whack");
        assertThat(attack.get("duration").intValue()).isZero();

        PacketBuilder food = new PacketBuilder();
        food.writeVarInt(6);
        assertThat(decode("minecraft:villager_food", food).get("nutrition").intValue()).isEqualTo(6);

        PacketBuilder cushionColor = new PacketBuilder();
        cushionColor.writeVarInt(15);
        assertThat(decode("minecraft:cushion/color", cushionColor).stringValue()).isEqualTo("black");

        assertThat(decode("minecraft:waxed", new PacketBuilder()).asCompound().isEmpty()).isTrue();
    }

    @Test
    void decodesResolvableNumberComponents() {
        PacketBuilder compostable = new PacketBuilder();
        compostable.writeBoolean(true);
        compostable.writeInt(3);
        assertThat(decode("minecraft:compostable", compostable).get("layers").intValue()).isEqualTo(3);

        PacketBuilder cookingFuel = new PacketBuilder();
        cookingFuel.writeBoolean(true);
        cookingFuel.writeInt(200);
        cookingFuel.writeBoolean(true);
        cookingFuel.writeFloat(1.5f);
        CompoundTag cooking = decode("minecraft:cooking_fuel", cookingFuel).asCompound();
        assertThat(cooking.get("burn_time").intValue()).isEqualTo(200);
        assertThat(cooking.get("speed_multiplier").floatValue()).isEqualTo(1.5f);

        PacketBuilder brewingFuel = new PacketBuilder();
        brewingFuel.writeBoolean(false);
        brewingFuel.writeString("minecraft:test_uses");
        brewingFuel.writeBoolean(false);
        brewingFuel.writeString("minecraft:test_speed");
        CompoundTag brewing = decode("minecraft:brewing_fuel", brewingFuel).asCompound();
        assertThat(brewing.get("uses").stringValue()).isEqualTo("minecraft:test_uses");
        assertThat(brewing.get("speed_multiplier").stringValue()).isEqualTo("minecraft:test_speed");
    }

    @Test
    void resolvesDynamicRegistryReferences() {
        DynamicRegistry.getInstance().load(new DataTypeProvider.Registry("minecraft:block_transformer",
                List.of(entry("minecraft:first"), entry("minecraft:strip"))));
        DynamicRegistry.getInstance().load(new DataTypeProvider.Registry("minecraft:decorated_pot_pattern",
                List.of(entry("minecraft:angler"))));

        PacketBuilder transformer = new PacketBuilder();
        transformer.writeVarInt(1);
        assertThat(decode("minecraft:block_transformer", transformer).stringValue()).isEqualTo("minecraft:strip");

        PacketBuilder pottery = new PacketBuilder();
        pottery.writeVarInt(0);
        assertThat(decode("minecraft:provides_pottery_pattern", pottery).stringValue()).isEqualTo("minecraft:angler");
    }

    @Test
    void decodesMobVisibilityHolderSet() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(3);
        packet.writeVarInt(33);
        packet.writeVarInt(106);
        packet.writeFloat(0.5f);

        CompoundTag nbt = decode("minecraft:mob_visibility", packet).asCompound();
        assertThat(nbt.get("targeting_entity_types").asList()).hasSize(2);
        assertThat(nbt.get("targeting_entity_types").asList().get(0).stringValue()).isEqualTo("minecraft:cushion");
        assertThat(nbt.get("targeting_entity_types").asList().get(1).stringValue()).isEqualTo("minecraft:poplar_boat");
        assertThat(nbt.get("visibility").floatValue()).isEqualTo(0.5f);
    }

    @Test
    void decodesSignText() {
        PacketBuilder packet = new PacketBuilder();
        for (int i = 0; i < 4; i++) {
            packet.writeNbtDirect(new StringTag("line-" + i));
        }
        packet.writeBoolean(false);
        packet.writeVarInt(14);
        packet.writeBoolean(true);

        CompoundTag nbt = decode("minecraft:sign_text_front", packet).asCompound();
        assertThat(nbt.get("messages").asList()).hasSize(4);
        assertThat(nbt.get("messages").asList().get(0).stringValue()).isEqualTo("line-0");
        assertThat(nbt.get("messages").asList().get(3).stringValue()).isEqualTo("line-3");
        assertThat(nbt.get("filtered_messages").isError()).isTrue();
        assertThat(nbt.get("color").stringValue()).isEqualTo("red");
        assertThat(nbt.get("has_glowing_text").byteValue()).isEqualTo((byte) 1);
    }

    @Test
    void marksMissingDynamicRegistryEntryIncomplete() {
        PacketBuilder packet = new PacketBuilder();
        packet.writeVarInt(5);
        SnapshotCompleteness completeness = new SnapshotCompleteness();

        SpecificTag nbt = decode("minecraft:block_transformer", packet, completeness);

        assertThat(nbt).isNull();
        assertThat(completeness.getDiagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("UNKNOWN_DYNAMIC_REGISTRY_ENTRY");
            assertThat(diagnostic.numericId()).isEqualTo(5);
            assertThat(diagnostic.resourceLocation()).isEqualTo("minecraft:block_transformer");
        });
    }

    private SpecificTag decode(String component, PacketBuilder packet) {
        return decode(component, packet, new SnapshotCompleteness());
    }

    private <T> SpecificTag decode(String component, PacketBuilder packet, SnapshotCompleteness completeness) {
        @SuppressWarnings("unchecked")
        ComponentCodec<T> codec = (ComponentCodec<T>) ComponentCodecs.defaults().get(component);
        assertThat(codec).isNotNull();
        DataComponentRegistry registry = RegistryManager.getInstance().getDataComponentRegistry();
        DataTypeProvider provider = DataTypeProvider.ofPacket(packet.toArray());
        T value = codec.read(provider, new ComponentReadContext(registry, completeness, "26.3", "test", 0, 16));
        assertThat(provider.hasNext()).isFalse();
        return codec.toNbt(value, new ComponentNbtContext(registry, completeness, 0, 16));
    }

    private static DataTypeProvider.RegistryEntry entry(String name) {
        return new DataTypeProvider.RegistryEntry(name, Optional.empty());
    }
}
