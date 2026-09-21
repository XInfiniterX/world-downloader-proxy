package version.v26_3.components;

import core.snapshot.SnapshotDiagnostic;
import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.FloatTag;
import se.llbit.nbt.IntTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;
import version.v26_3.entity.EntityNames;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.schematic.DynamicRegistry;
import version.v26_3.world.WorldManager;

import java.util.ArrayList;
import java.util.List;

final class ComponentCodecsV26_3 {
    private static final String[] DYE_COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private ComponentCodecsV26_3() { }

    static void register(ComponentCodecs codecs) {
        codecs.register(dynamicRegistryReferenceCodec("minecraft:block_transformer"), "minecraft:block_transformer");
        codecs.register(villagerFoodCodec(), "minecraft:villager_food");
        codecs.register(compostableCodec(), "minecraft:compostable");
        codecs.register(cookingFuelCodec(), "minecraft:cooking_fuel");
        codecs.register(brewingFuelCodec(), "minecraft:brewing_fuel");
        codecs.register(mobVisibilityCodec(), "minecraft:mob_visibility");
        codecs.register(dynamicRegistryReferenceCodec("minecraft:decorated_pot_pattern"), "minecraft:provides_pottery_pattern");
        ComponentCodec<SpecificTag> signText = signTextCodec();
        codecs.register(signText, "minecraft:sign_text_front", "minecraft:sign_text_back");
    }

    private static ComponentCodec<String> dynamicRegistryReferenceCodec(String registryName) {
        return new ComponentCodec<>() {
            @Override
            public String read(DataTypeProvider input, ComponentReadContext context) {
                int position = input.position();
                int id = input.readVarInt();
                String name = DynamicRegistry.getInstance().getName(registryName, id);
                if (name == null) {
                    context.completeness().markIncomplete(new SnapshotDiagnostic(
                            "UNKNOWN_DYNAMIC_REGISTRY_ENTRY", context.protocolVersion(), context.source(), id,
                            registryName, position, "Unknown entry " + id + " in dynamic registry " + registryName
                    ));
                }
                return name;
            }

            @Override
            public SpecificTag toNbt(String value, ComponentNbtContext context) {
                return value == null ? null : new StringTag(value);
            }
        };
    }

    private static ComponentCodec<SpecificTag> villagerFoodCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("nutrition", new IntTag(input.readVarInt()));
            return root;
        });
    }

    private static ComponentCodec<SpecificTag> compostableCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("layers", readResolvableInt(input));
            return root;
        });
    }

    private static ComponentCodec<SpecificTag> cookingFuelCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("burn_time", readResolvableInt(input));
            root.add("speed_multiplier", readResolvableFloat(input));
            return root;
        });
    }

    private static ComponentCodec<SpecificTag> brewingFuelCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("uses", readResolvableInt(input));
            root.add("speed_multiplier", readResolvableFloat(input));
            return root;
        });
    }

    private static SpecificTag readResolvableInt(DataTypeProvider input) {
        return input.readBoolean() ? new IntTag(input.readInt()) : new StringTag(input.readString());
    }

    private static SpecificTag readResolvableFloat(DataTypeProvider input) {
        return input.readBoolean() ? new FloatTag(input.readFloat()) : new StringTag(input.readString());
    }

    private static ComponentCodec<SpecificTag> mobVisibilityCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("targeting_entity_types", readEntityHolderSet(input));
            root.add("visibility", new FloatTag(input.readFloat()));
            return root;
        });
    }

    private static SpecificTag readEntityHolderSet(DataTypeProvider input) {
        int count = input.readVarInt() - 1;
        if (count == -1) {
            return new StringTag("#" + input.readString());
        }
        if (count < 0) {
            throw new IllegalArgumentException("Invalid entity holder set size: " + count);
        }

        EntityNames entityNames = WorldManager.getInstance().getEntityMap();
        List<SpecificTag> names = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = input.readVarInt();
            String name = entityNames == null ? null : entityNames.getName(id);
            if (name == null) {
                throw new IllegalArgumentException("Unknown entity type ID in mob_visibility: " + id);
            }
            names.add(new StringTag(name));
        }
        return new ListTag(Tag.TAG_STRING, names);
    }

    private static ComponentCodec<SpecificTag> signTextCodec() {
        return passthrough(input -> {
            CompoundTag root = new CompoundTag();
            root.add("messages", readChatList(input));
            if (input.readBoolean()) {
                root.add("filtered_messages", readChatList(input));
            }
            root.add("color", new StringTag(dyeColorName(input.readVarInt())));
            root.add("has_glowing_text", new ByteTag(input.readBoolean() ? 1 : 0));
            return root;
        });
    }

    private static ListTag readChatList(DataTypeProvider input) {
        List<SpecificTag> values = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            values.add(input.readChatTag());
        }
        return new ListTag(values.get(0).tagType(), values);
    }

    private static String dyeColorName(int id) {
        if (id < 0 || id >= DYE_COLORS.length) {
            throw new IllegalArgumentException("Unknown dye color: " + id);
        }
        return DYE_COLORS[id];
    }

    private static ComponentCodec<SpecificTag> passthrough(ValueReader reader) {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                return reader.read(input);
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    @FunctionalInterface
    private interface ValueReader {
        SpecificTag read(DataTypeProvider input);
    }
}
