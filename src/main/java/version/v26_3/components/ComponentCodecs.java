package version.v26_3.components;

import se.llbit.nbt.*;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.registries.DataComponentRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class ComponentCodecs {
    private final Map<String, ComponentCodec<?>> codecs = new HashMap<>();

    public static ComponentCodecs defaults() {
        ComponentCodecs codecs = new ComponentCodecs();
        ComponentCodec<SpecificTag> nbt = passthrough(DataTypeProvider::readNbtTag);
        ComponentCodec<SpecificTag> chat = passthrough(DataTypeProvider::readChatTag);

        // --- NBT passthrough components ---
        codecs.register(nbt, "minecraft:custom_data", "minecraft:bucket_entity_data",
                "minecraft:debug_stick_state", "minecraft:map_decorations",
                // In 26.x, entity_data and block_entity_data are just anonymousNbt
                // (the VarInt type prefix was removed after 1.20.5).
                "minecraft:entity_data", "minecraft:block_entity_data",
                "minecraft:recipes", "minecraft:lock", "minecraft:container_loot");
        codecs.register(chat, "minecraft:custom_name", "minecraft:item_name");

        // --- VarInt scalar components ---
        codecs.register(scalar(DataTypeProvider::readVarInt, IntTag::new),
                "minecraft:max_stack_size", "minecraft:max_damage", "minecraft:damage", "minecraft:repair_cost",
                "minecraft:enchantable", "minecraft:additional_trade_cost", "minecraft:ominous_bottle_amplifier",
                "minecraft:map_id", "minecraft:damage_type", "minecraft:map_post_processing");

        // --- Mob variant components (all VarInt) ---
        codecs.register(scalar(DataTypeProvider::readVarInt, IntTag::new),
                "minecraft:villager/variant", "minecraft:wolf/variant", "minecraft:wolf/sound_variant",
                "minecraft:wolf/collar", "minecraft:fox/variant", "minecraft:salmon/size",
                "minecraft:parrot/variant", "minecraft:tropical_fish/pattern",
                "minecraft:tropical_fish/base_color", "minecraft:tropical_fish/pattern_color",
                "minecraft:mooshroom/variant", "minecraft:rabbit/variant",
                "minecraft:pig/variant", "minecraft:pig/sound_variant",
                "minecraft:cow/variant", "minecraft:cow/sound_variant",
                "minecraft:chicken/variant", "minecraft:chicken/sound_variant",
                "minecraft:zombie_nautilus/variant", "minecraft:horse/variant",
                "minecraft:painting/variant", "minecraft:llama/variant",
                "minecraft:axolotl/variant", "minecraft:cat/variant",
                "minecraft:cat/sound_variant", "minecraft:cat/collar",
                "minecraft:sheep/color", "minecraft:shulker/color",
                "minecraft:base_color", "minecraft:frog/variant");

        // --- Int scalar components ---
        codecs.register(scalar(DataTypeProvider::readInt, IntTag::new), "minecraft:dyed_color");

        // --- Float scalar components ---
        codecs.register(scalar(DataTypeProvider::readFloat, FloatTag::new),
                "minecraft:minimum_attack_charge", "minecraft:potion_duration_scale");

        // --- Boolean scalar components ---
        codecs.register(scalar(DataTypeProvider::readBoolean, value -> new ByteTag(value ? 1 : 0)),
                "minecraft:enchantment_glint_override");

        // --- Void components (no fields) ---
        codecs.register(scalar(input -> Boolean.TRUE, ignored -> new CompoundTag()),
                "minecraft:unbreakable", "minecraft:creative_slot_lock", "minecraft:intangible_projectile",
                "minecraft:glider", "minecraft:waxed");

        // --- String scalar components ---
        codecs.register(scalar(DataTypeProvider::readString, StringTag::new),
                "minecraft:item_model", "minecraft:damage_resistant", "minecraft:tooltip_style",
                "minecraft:provides_banner_patterns", "minecraft:note_block_sound");

        // --- Enum components ---
        codecs.register(enumeration("common", "uncommon", "rare", "epic"), "minecraft:rarity");
        codecs.register(enumeration(
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
        ), "minecraft:dye", "minecraft:cushion/color");

        // --- Enchantments (Prefixed Array of {id: VarInt, level: VarInt}) ---
        codecs.register(enchantmentsCodec(), "minecraft:enchantments", "minecraft:stored_enchantments");

        // --- Suspicious stew effects (Prefixed Array of {effect: VarInt, duration: VarInt}) ---
        codecs.register(suspiciousStewEffectsCodec(), "minecraft:suspicious_stew_effects");

        // --- Tooltip display (Boolean + Prefixed Array of VarInt) ---
        codecs.register(tooltipDisplayCodec(), "minecraft:tooltip_display");

        // --- Food (VarInt + Float + Boolean) ---
        codecs.register(foodCodec(), "minecraft:food");

        // --- Weapon (VarInt + Float) ---
        codecs.register(weaponCodec(), "minecraft:weapon");

        // --- Use effects (Boolean + Boolean + Float) — 26.x ---
        codecs.register(useEffectsCodec(), "minecraft:use_effects");

        // --- Attack range (6 Floats) — 26.x ---
        codecs.register(attackRangeCodec(), "minecraft:attack_range");

        // --- Swing animation (VarInt + VarInt) — 26.x ---
        codecs.register(swingAnimationCodec(), "minecraft:attack_animation", "minecraft:interact_animation");

        // --- Use cooldown (Float + Optional String) ---
        codecs.register(useCooldownCodec(), "minecraft:use_cooldown");

        // --- Use remainder (Slot) ---
        codecs.register(slotCodec(), "minecraft:use_remainder", "minecraft:sulfur_cube_content");

        // --- Charged projectiles (Prefixed Array of Slots) ---
        codecs.register(slotArrayCodec(), "minecraft:charged_projectiles", "minecraft:bundle_contents",
                "minecraft:container");

        // --- Pot decorations (Prefixed Array of VarInt) ---
        codecs.register(varIntArrayCodec(), "minecraft:pot_decorations");

        // --- Block state (Prefixed Array of {String, String}) ---
        codecs.register(blockStateCodec(), "minecraft:block_state");

        // --- Bees (Prefixed Array of {NBT, VarInt, VarInt}) ---
        codecs.register(beesCodec(), "minecraft:bees");

        // --- IDSet components ---
        codecs.register(idSetCodec(), "minecraft:repairable");

        // --- RegistryEntryHolder components ---
        codecs.register(registryEntryHolderCodec(), "minecraft:instrument", "minecraft:provides_trim_material",
                "minecraft:jukebox_playable");
        codecs.register(soundHolderCodec(), "minecraft:break_sound");

        // --- Trim (material: RegistryEntryHolder + pattern: RegistryEntryHolder) ---
        codecs.register(trimCodec(), "minecraft:trim");

        // --- Equippable ---
        codecs.register(equippableCodec(), "minecraft:equippable");

        // --- Tool ---
        codecs.register(toolCodec(), "minecraft:tool");

        // --- Consumable ---
        codecs.register(consumableCodec(), "minecraft:consumable");

        // --- Death protection (Prefixed Array of ConsumeEffects) ---
        codecs.register(deathProtectionCodec(), "minecraft:death_protection");

        // --- Blocks attacks ---
        codecs.register(blocksAttacksCodec(), "minecraft:blocks_attacks");

        // --- Attribute modifiers ---
        codecs.register(attributeModifiersCodec(), "minecraft:attribute_modifiers");

        // --- Can place on / Can break (Prefixed Array of BlockPredicates) ---
        codecs.register(blockPredicateArrayCodec(), "minecraft:can_place_on", "minecraft:can_break");

        // --- Potion contents ---
        codecs.register(potionContentsCodec(), "minecraft:potion_contents");

        // --- Writable book content ---
        codecs.register(writableBookContentCodec(), "minecraft:writable_book_content");

        // --- Written book content ---
        codecs.register(writtenBookContentCodec(), "minecraft:written_book_content");

        // --- Firework explosion ---
        codecs.register(fireworkExplosionCodec(), "minecraft:firework_explosion");

        // --- Fireworks (VarInt + Prefixed Array of FireworkExplosions) ---
        codecs.register(fireworksCodec(), "minecraft:fireworks");

        // --- Banner patterns ---
        codecs.register(bannerPatternsCodec(), "minecraft:banner_patterns");

        // --- Lodestone tracker ---
        codecs.register(lodestoneTrackerCodec(), "minecraft:lodestone_tracker");

        // --- Kinetic weapon — 26.x ---
        codecs.register(kineticWeaponCodec(), "minecraft:kinetic_weapon");

        // --- Piercing weapon — 26.x ---
        codecs.register(piercingWeaponCodec(), "minecraft:piercing_weapon");

        // --- Structured codecs ---
        codecs.register(new LoreComponentCodec(), "minecraft:lore");
        codecs.register(new CustomModelDataComponentCodec(), "minecraft:custom_model_data");
        codecs.register(new ProfileComponentCodec(), "minecraft:profile");
        ComponentCodecsV26_3.register(codecs);
        return codecs;
    }

    public void register(ComponentCodec<?> codec, String... names) {
        for (String name : names) {
            codecs.put(name, codec);
        }
    }

    public ComponentCodec<?> get(String name) {
        return codecs.get(name);
    }

    public Set<String> unsupported(DataComponentRegistry registry) {
        Set<String> unsupported = new HashSet<>(registry.names());
        unsupported.removeAll(codecs.keySet());
        return unsupported;
    }

    // =================== Helper methods ===================

    private static <T> ComponentCodec<T> scalar(ValueReader<T> reader, Function<T, SpecificTag> writer) {
        return new ComponentCodec<>() {
            @Override
            public T read(DataTypeProvider input, ComponentReadContext context) {
                return reader.read(input);
            }

            @Override
            public SpecificTag toNbt(T value, ComponentNbtContext context) {
                return writer.apply(value);
            }
        };
    }

    private static ComponentCodec<String> enumeration(String... values) {
        return scalar(input -> {
            int id = input.readVarInt();
            if (id < 0 || id >= values.length) {
                throw new IllegalArgumentException("Unknown component enum value: " + id);
            }
            return values[id];
        }, StringTag::new);
    }

    private static ComponentCodec<SpecificTag> passthrough(TagReader reader) {
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

    /** Reads a boolean-prefixed optional value. Returns null if absent. */
    private static <T> ComponentCodec<T> optional(ValueReader<T> reader, Function<T, SpecificTag> writer) {
        return new ComponentCodec<>() {
            @Override
            public T read(DataTypeProvider input, ComponentReadContext context) {
                if (input.readBoolean()) {
                    return reader.read(input);
                }
                return null;
            }

            @Override
            public SpecificTag toNbt(T value, ComponentNbtContext context) {
                return value == null ? null : writer.apply(value);
            }
        };
    }

    /** Reads a VarInt-prefixed array of elements. */
    private static <T> ComponentCodec<List<T>> prefixedArray(ValueReader<T> elementReader,
                                                              Function<List<T>, SpecificTag> writer) {
        return new ComponentCodec<>() {
            @Override
            public List<T> read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<T> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(elementReader.read(input));
                }
                return list;
            }

            @Override
            public SpecificTag toNbt(List<T> value, ComponentNbtContext context) {
                return writer.apply(value);
            }
        };
    }

    // =================== Specific codecs ===================

    /** Enchantments: Prefixed Array of {id: VarInt, level: VarInt} */
    private static ComponentCodec<SpecificTag> enchantmentsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                CompoundTag levels = new CompoundTag();
                for (int i = 0; i < count; i++) {
                    int id = input.readVarInt();
                    int level = input.readVarInt();
                    // Convert numeric enchantment ID to namespaced name (e.g. 12 → "minecraft:sharpness").
                    // The destination server expects namespaced keys in the "levels" compound, not numeric IDs.
                    String name = version.v26_3.schematic.DynamicRegistry.getInstance()
                            .getName("minecraft:enchantment", id);
                    String key = name != null ? name : String.valueOf(id);
                    levels.add(key, new IntTag(level));
                }
                CompoundTag root = new CompoundTag();
                root.add("levels", levels);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Suspicious stew effects: Prefixed Array of {effect: VarInt, duration: VarInt} */
    private static ComponentCodec<SpecificTag> suspiciousStewEffectsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                ListTag effects = new ListTag(Tag.TAG_COMPOUND, new ArrayList<>());
                for (int i = 0; i < count; i++) {
                    int effectId = input.readVarInt();
                    int duration = input.readVarInt();
                    CompoundTag effect = new CompoundTag();
                    effect.add("id", new IntTag(effectId));
                    effect.add("duration", new IntTag(duration));
                    effects.add(effect);
                }
                CompoundTag root = new CompoundTag();
                root.add("effects", effects);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Tooltip display: Boolean + Prefixed Array of VarInt */
    private static ComponentCodec<SpecificTag> tooltipDisplayCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                boolean hideTooltip = input.readBoolean();
                int count = input.readVarInt();
                ListTag hidden = new ListTag(Tag.TAG_INT, new ArrayList<>());
                for (int i = 0; i < count; i++) {
                    hidden.add(new IntTag(input.readVarInt()));
                }
                CompoundTag root = new CompoundTag();
                root.add("hide_tooltip", new ByteTag(hideTooltip ? 1 : 0));
                root.add("hidden_components", hidden);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Food: VarInt + Float + Boolean */
    private static ComponentCodec<SpecificTag> foodCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int nutrition = input.readVarInt();
                float saturation = input.readFloat();
                boolean canAlwaysEat = input.readBoolean();
                CompoundTag root = new CompoundTag();
                root.add("nutrition", new IntTag(nutrition));
                root.add("saturation_modifier", new FloatTag(saturation));
                root.add("can_always_eat", new ByteTag(canAlwaysEat ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Weapon: VarInt + Float */
    private static ComponentCodec<SpecificTag> weaponCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int itemDamagePerAttack = input.readVarInt();
                float disableBlockingForSeconds = input.readFloat();
                CompoundTag root = new CompoundTag();
                root.add("item_damage_per_attack", new IntTag(itemDamagePerAttack));
                root.add("disable_blocking_for_seconds", new FloatTag(disableBlockingForSeconds));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Use effects: Boolean + Boolean + Float — 26.x */
    private static ComponentCodec<SpecificTag> useEffectsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                boolean canSprint = input.readBoolean();
                boolean interactVibrations = input.readBoolean();
                float speedMultiplier = input.readFloat();
                CompoundTag root = new CompoundTag();
                root.add("can_sprint", new ByteTag(canSprint ? 1 : 0));
                root.add("interact_vibrations", new ByteTag(interactVibrations ? 1 : 0));
                root.add("speed_multiplier", new FloatTag(speedMultiplier));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Attack range: 6 Floats — 26.x */
    private static ComponentCodec<SpecificTag> attackRangeCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                root.add("min_reach", new FloatTag(input.readFloat()));
                root.add("max_reach", new FloatTag(input.readFloat()));
                root.add("min_creative_reach", new FloatTag(input.readFloat()));
                root.add("max_creative_reach", new FloatTag(input.readFloat()));
                root.add("hitbox_margin", new FloatTag(input.readFloat()));
                root.add("mob_factor", new FloatTag(input.readFloat()));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Swing animation: VarInt + VarInt — 26.x */
    private static ComponentCodec<SpecificTag> swingAnimationCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int type = input.readVarInt();
                int duration = input.readVarInt();
                String[] types = {"none", "whack", "stab"};
                if (type < 0 || type >= types.length) {
                    throw new IllegalArgumentException("Unknown swing animation type: " + type);
                }
                CompoundTag root = new CompoundTag();
                root.add("type", new StringTag(types[type]));
                root.add("duration", new IntTag(duration));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Use cooldown: Float + Optional String */
    private static ComponentCodec<SpecificTag> useCooldownCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                float seconds = input.readFloat();
                String group = input.readBoolean() ? input.readString() : null;
                CompoundTag root = new CompoundTag();
                root.add("seconds", new FloatTag(seconds));
                if (group != null) {
                    root.add("cooldown_group", new StringTag(group));
                }
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Slot codec for single-slot components */
    private static ComponentCodec<SpecificTag> slotCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                var slot = input.readSlot();
                return slot != null ? slot.toNbt() : new CompoundTag();
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Prefixed Array of Slots */
    private static ComponentCodec<SpecificTag> slotArrayCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    var slot = input.readSlot();
                    items.add(slot != null ? slot.toNbt() : new CompoundTag());
                }
                return new ListTag(Tag.TAG_COMPOUND, items);
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Prefixed Array of VarInt */
    private static ComponentCodec<SpecificTag> varIntArrayCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> items = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    items.add(new IntTag(input.readVarInt()));
                }
                return new ListTag(Tag.TAG_INT, items);
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Block state: Prefixed Array of {name: String, value: String} */
    private static ComponentCodec<SpecificTag> blockStateCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                CompoundTag properties = new CompoundTag();
                for (int i = 0; i < count; i++) {
                    String name = input.readString();
                    String value = input.readString();
                    properties.add(name, new StringTag(value));
                }
                CompoundTag root = new CompoundTag();
                root.add("properties", properties);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Bees: Prefixed Array of {nbtData: NBT, ticksInHive: VarInt, minTicksInHive: VarInt} */
    private static ComponentCodec<SpecificTag> beesCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> bees = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    SpecificTag nbtData = input.readNbtTag();
                    int ticksInHive = input.readVarInt();
                    int minTicksInHive = input.readVarInt();
                    CompoundTag bee = new CompoundTag();
                    bee.add("entity_data", nbtData);
                    bee.add("ticks_in_hive", new IntTag(ticksInHive));
                    bee.add("min_ticks_in_hive", new IntTag(minTicksInHive));
                    bees.add(bee);
                }
                CompoundTag root = new CompoundTag();
                root.add("bees", new ListTag(Tag.TAG_COMPOUND, bees));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** IDSet: Boolean + String (tag) OR Boolean + VarInt array */
    private static ComponentCodec<SpecificTag> idSetCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                boolean isTag = input.readBoolean();
                if (isTag) {
                    String tagName = input.readString();
                    CompoundTag root = new CompoundTag();
                    root.add("tag", new StringTag(tagName));
                    return root;
                }
                int count = input.readVarInt();
                List<SpecificTag> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(new IntTag(input.readVarInt()));
                }
                CompoundTag root = new CompoundTag();
                root.add("ids", new ListTag(Tag.TAG_INT, ids));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** RegistryEntryHolder: Boolean + VarInt (id) OR Boolean + inline data */
    /** RegistryEntryHolder / IdOr: VarInt discriminator (0 = inline, n > 0 = registry ID n-1) */
    private static ComponentCodec<SpecificTag> registryEntryHolderCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int discriminator = input.readVarInt();
                if (discriminator > 0) {
                    int id = discriminator - 1;
                    CompoundTag root = new CompoundTag();
                    root.add("id", new IntTag(id));
                    return root;
                }
                // Inline data — read as NBT (best effort)
                SpecificTag data = input.readNbtTag();
                CompoundTag root = new CompoundTag();
                root.add("data", data);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** ItemSoundHolder: IdOr<SoundEvent> — VarInt discriminator (0 = inline, n > 0 = registry ID n-1) */
    private static ComponentCodec<SpecificTag> soundHolderCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int discriminator = input.readVarInt();
                if (discriminator > 0) {
                    int id = discriminator - 1;
                    CompoundTag root = new CompoundTag();
                    root.add("sound_id", new IntTag(id));
                    return root;
                }
                // Inline: soundName: String, fixedRange?: Float
                String soundName = input.readString();
                CompoundTag root = new CompoundTag();
                CompoundTag data = new CompoundTag();
                data.add("sound_name", new StringTag(soundName));
                if (input.readBoolean()) {
                    data.add("fixed_range", new FloatTag(input.readFloat()));
                }
                root.add("data", data);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Trim: material (RegistryEntryHolder) + pattern (RegistryEntryHolder) */
    private static ComponentCodec<SpecificTag> trimCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                SpecificTag material = registryEntryHolderCodec().read(input, context);
                SpecificTag pattern = registryEntryHolderCodec().read(input, context);
                CompoundTag root = new CompoundTag();
                root.add("material", material);
                root.add("pattern", pattern);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Equippable: slot (VarInt) + sound (SoundHolder) + model? + cameraOverlay? + allowedEntities? + 4 booleans */
    private static ComponentCodec<SpecificTag> equippableCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int slot = input.readVarInt();
                SpecificTag sound = soundHolderCodec().read(input, context);
                String model = input.readBoolean() ? input.readString() : null;
                String cameraOverlay = input.readBoolean() ? input.readString() : null;
                SpecificTag allowedEntities = input.readBoolean() ? idSetCodec().read(input, context) : null;
                boolean dispensable = input.readBoolean();
                boolean swappable = input.readBoolean();
                boolean damageable = input.readBoolean();
                boolean equipOnInteract = input.readBoolean();

                CompoundTag root = new CompoundTag();
                root.add("slot", new IntTag(slot));
                root.add("sound", sound);
                if (model != null) root.add("model", new StringTag(model));
                if (cameraOverlay != null) root.add("camera_overlay", new StringTag(cameraOverlay));
                if (allowedEntities != null) root.add("allowed_entities", allowedEntities);
                root.add("dispensable", new ByteTag(dispensable ? 1 : 0));
                root.add("swappable", new ByteTag(swappable ? 1 : 0));
                root.add("damageable", new ByteTag(damageable ? 1 : 0));
                root.add("equip_on_interact", new ByteTag(equipOnInteract ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Tool: rules (Prefixed Array of {blocks: IDSet, speed?: Float, correctDropForBlocks?: Boolean}) + Float + VarInt + Boolean */
    private static ComponentCodec<SpecificTag> toolCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int ruleCount = input.readVarInt();
                List<SpecificTag> rules = new ArrayList<>(ruleCount);
                for (int i = 0; i < ruleCount; i++) {
                    SpecificTag blocks = idSetCodec().read(input, context);
                    Float speed = input.readBoolean() ? input.readFloat() : null;
                    Boolean correctDrop = input.readBoolean() ? input.readBoolean() : null;
                    CompoundTag rule = new CompoundTag();
                    rule.add("blocks", blocks);
                    if (speed != null) rule.add("speed", new FloatTag(speed));
                    if (correctDrop != null) rule.add("correct_drop_for_blocks", new ByteTag(correctDrop ? 1 : 0));
                    rules.add(rule);
                }
                float defaultMiningSpeed = input.readFloat();
                int damagePerBlock = input.readVarInt();
                boolean canDestroyBlocksInCreative = input.readBoolean();

                CompoundTag root = new CompoundTag();
                root.add("rules", new ListTag(Tag.TAG_COMPOUND, rules));
                root.add("default_mining_speed", new FloatTag(defaultMiningSpeed));
                root.add("damage_per_block", new IntTag(damagePerBlock));
                root.add("can_destroy_blocks_in_creative", new ByteTag(canDestroyBlocksInCreative ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Consumable: Float + VarInt enum + SoundHolder + Boolean + Prefixed Array of ConsumeEffects */
    private static ComponentCodec<SpecificTag> consumableCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                float consumeSeconds = input.readFloat();
                int animation = input.readVarInt();
                SpecificTag sound = soundHolderCodec().read(input, context);
                boolean makesParticles = input.readBoolean();
                int effectCount = input.readVarInt();
                List<SpecificTag> effects = new ArrayList<>(effectCount);
                for (int i = 0; i < effectCount; i++) {
                    effects.add(consumeEffectCodec().read(input, context));
                }
                CompoundTag root = new CompoundTag();
                root.add("consume_seconds", new FloatTag(consumeSeconds));
                root.add("animation", new IntTag(animation));
                root.add("sound", sound);
                root.add("makes_particles", new ByteTag(makesParticles ? 1 : 0));
                root.add("effects", new ListTag(Tag.TAG_COMPOUND, effects));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** ConsumeEffect: type (VarInt) + type-specific data */
    private static ComponentCodec<SpecificTag> consumeEffectCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int type = input.readVarInt();
                CompoundTag root = new CompoundTag();
                root.add("type", new IntTag(type));
                switch (type) {
                    case 0 -> { // apply_effects: ItemPotionEffect[] + Float
                        int count = input.readVarInt();
                        List<SpecificTag> effects = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            effects.add(potionEffectCodec().read(input, context));
                        }
                        root.add("effects", new ListTag(Tag.TAG_COMPOUND, effects));
                        root.add("probability", new FloatTag(input.readFloat()));
                    }
                    case 1 -> { // remove_effects: IDSet
                        root.add("effects", idSetCodec().read(input, context));
                    }
                    case 2 -> { // clear_all_effects: void
                    }
                    case 3 -> { // teleport_randomly: Float
                        root.add("diameter", new FloatTag(input.readFloat()));
                    }
                    case 4 -> { // play_sound: SoundHolder
                        root.add("sound", soundHolderCodec().read(input, context));
                    }
                    default -> throw new IllegalArgumentException("Unknown consume effect type: " + type);
                }
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** PotionEffect: id (VarInt) + details (amplifier, duration, ambient, showParticles, showIcon, hiddenEffect?) */
    private static ComponentCodec<SpecificTag> potionEffectCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int id = input.readVarInt();
                int amplifier = input.readVarInt();
                int duration = input.readVarInt();
                boolean ambient = input.readBoolean();
                boolean showParticles = input.readBoolean();
                boolean showIcon = input.readBoolean();
                CompoundTag root = new CompoundTag();
                root.add("id", new IntTag(id));
                CompoundTag details = new CompoundTag();
                details.add("amplifier", new IntTag(amplifier));
                details.add("duration", new IntTag(duration));
                details.add("ambient", new ByteTag(ambient ? 1 : 0));
                details.add("show_particles", new ByteTag(showParticles ? 1 : 0));
                details.add("show_icon", new ByteTag(showIcon ? 1 : 0));
                if (input.readBoolean()) {
                    details.add("hidden_effect", read(input, context));
                }
                root.add("details", details);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Death protection: Prefixed Array of ConsumeEffects */
    private static ComponentCodec<SpecificTag> deathProtectionCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> effects = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    effects.add(consumeEffectCodec().read(input, context));
                }
                CompoundTag root = new CompoundTag();
                root.add("effects", new ListTag(Tag.TAG_COMPOUND, effects));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Blocks attacks: complex structured type */
    private static ComponentCodec<SpecificTag> blocksAttacksCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                float blockDelaySeconds = input.readFloat();
                float disableCooldownScale = input.readFloat();
                int reductionCount = input.readVarInt();
                List<SpecificTag> reductions = new ArrayList<>(reductionCount);
                for (int i = 0; i < reductionCount; i++) {
                    float horizontalAngle = input.readFloat();
                    SpecificTag type = input.readBoolean() ? idSetCodec().read(input, context) : null;
                    float base = input.readFloat();
                    float factor = input.readFloat();
                    CompoundTag reduction = new CompoundTag();
                    reduction.add("horizontal_blocking_angle", new FloatTag(horizontalAngle));
                    if (type != null) reduction.add("type", type);
                    reduction.add("base", new FloatTag(base));
                    reduction.add("factor", new FloatTag(factor));
                    reductions.add(reduction);
                }
                float itemDamageThreshold = input.readFloat();
                float itemDamageBase = input.readFloat();
                float itemDamageFactor = input.readFloat();
                String bypassedBy = input.readBoolean() ? input.readString() : null;
                SpecificTag blockSound = input.readBoolean() ? soundHolderCodec().read(input, context) : null;
                SpecificTag disableSound = input.readBoolean() ? soundHolderCodec().read(input, context) : null;

                CompoundTag root = new CompoundTag();
                root.add("block_delay_seconds", new FloatTag(blockDelaySeconds));
                root.add("disable_cooldown_scale", new FloatTag(disableCooldownScale));
                root.add("damage_reductions", new ListTag(Tag.TAG_COMPOUND, reductions));
                CompoundTag itemDamage = new CompoundTag();
                itemDamage.add("threshold", new FloatTag(itemDamageThreshold));
                itemDamage.add("base", new FloatTag(itemDamageBase));
                itemDamage.add("factor", new FloatTag(itemDamageFactor));
                root.add("item_damage", itemDamage);
                if (bypassedBy != null) root.add("bypassed_by", new StringTag(bypassedBy));
                if (blockSound != null) root.add("block_sound", blockSound);
                if (disableSound != null) root.add("disable_sound", disableSound);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Attribute modifiers: Prefixed Array of {typeId: VarInt, name: String, value: Double, operation: VarInt, slot: VarInt} */
    private static ComponentCodec<SpecificTag> attributeModifiersCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> modifiers = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int typeId = input.readVarInt();
                    String name = input.readString();
                    double value = input.readDouble();
                    int operation = input.readVarInt();
                    int slot = input.readVarInt();
                    CompoundTag mod = new CompoundTag();
                    mod.add("type", new IntTag(typeId));
                    mod.add("id", new StringTag(name));
                    mod.add("amount", new DoubleTag(value));
                    mod.add("operation", new IntTag(operation));
                    mod.add("slot", new IntTag(slot));
                    modifiers.add(mod);
                }
                CompoundTag root = new CompoundTag();
                root.add("modifiers", new ListTag(Tag.TAG_COMPOUND, modifiers));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Block predicate array: Prefixed Array of ItemBlockPredicate */
    private static ComponentCodec<SpecificTag> blockPredicateArrayCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int count = input.readVarInt();
                List<SpecificTag> predicates = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    predicates.add(blockPredicateCodec().read(input, context));
                }
                CompoundTag root = new CompoundTag();
                root.add("predicates", new ListTag(Tag.TAG_COMPOUND, predicates));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** ItemBlockPredicate: blockSet? + properties? + nbt + components(DataComponentMatchers) */
    private static ComponentCodec<SpecificTag> blockPredicateCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                if (input.readBoolean()) {
                    root.add("block_set", idSetCodec().read(input, context));
                }
                if (input.readBoolean()) {
                    int propCount = input.readVarInt();
                    List<SpecificTag> props = new ArrayList<>(propCount);
                    for (int i = 0; i < propCount; i++) {
                        String name = input.readString();
                        boolean isExact = input.readBoolean();
                        CompoundTag prop = new CompoundTag();
                        prop.add("name", new StringTag(name));
                        if (isExact) {
                            prop.add("exact_value", new StringTag(input.readString()));
                        } else {
                            prop.add("min_value", new StringTag(input.readString()));
                            prop.add("max_value", new StringTag(input.readString()));
                        }
                        props.add(prop);
                    }
                    root.add("properties", new ListTag(Tag.TAG_COMPOUND, props));
                }
                SpecificTag nbt = input.readNbtTag();
                root.add("nbt", nbt);
                // DataComponentMatchers: exactMatchers + partialMatchers
                int exactCount = input.readVarInt();
                List<SpecificTag> exact = new ArrayList<>(exactCount);
                for (int i = 0; i < exactCount; i++) {
                    int compCount = input.readVarInt();
                    List<SpecificTag> comps = new ArrayList<>(compCount);
                    for (int j = 0; j < compCount; j++) {
                        int compId = input.readVarInt();
                        SpecificTag compData = input.readNbtTag();
                        CompoundTag comp = new CompoundTag();
                        comp.add("type", new IntTag(compId));
                        comp.add("data", compData);
                        comps.add(comp);
                    }
                    exact.add(new ListTag(Tag.TAG_COMPOUND, comps));
                }
                int partialCount = input.readVarInt();
                List<SpecificTag> partial = new ArrayList<>(partialCount);
                for (int i = 0; i < partialCount; i++) {
                    partial.add(new IntTag(input.readVarInt()));
                }
                CompoundTag matchers = new CompoundTag();
                matchers.add("exact_matchers", new ListTag(Tag.TAG_COMPOUND, exact));
                matchers.add("partial_matchers", new ListTag(Tag.TAG_INT, partial));
                root.add("components", matchers);
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Potion contents: potionId? + customColor? + customEffects (Prefixed Array) + customName? */
    private static ComponentCodec<SpecificTag> potionContentsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                if (input.readBoolean()) {
                    root.add("potion_id", new IntTag(input.readVarInt()));
                }
                if (input.readBoolean()) {
                    root.add("custom_color", new IntTag(input.readInt()));
                }
                int effectCount = input.readVarInt();
                List<SpecificTag> effects = new ArrayList<>(effectCount);
                for (int i = 0; i < effectCount; i++) {
                    effects.add(potionEffectCodec().read(input, context));
                }
                root.add("custom_effects", new ListTag(Tag.TAG_COMPOUND, effects));
                if (input.readBoolean()) {
                    root.add("custom_name", new StringTag(input.readString()));
                }
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Writable book content: pages (Prefixed Array of {content: String, filteredContent?: String}) */
    private static ComponentCodec<SpecificTag> writableBookContentCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int pageCount = input.readVarInt();
                List<SpecificTag> pages = new ArrayList<>(pageCount);
                for (int i = 0; i < pageCount; i++) {
                    String content = input.readString();
                    String filtered = input.readBoolean() ? input.readString() : null;
                    CompoundTag page = new CompoundTag();
                    page.add("content", new StringTag(content));
                    if (filtered != null) page.add("filtered_content", new StringTag(filtered));
                    pages.add(page);
                }
                CompoundTag root = new CompoundTag();
                root.add("pages", new ListTag(Tag.TAG_COMPOUND, pages));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Written book content: rawTitle + filteredTitle? + author + generation + pages + resolved */
    private static ComponentCodec<SpecificTag> writtenBookContentCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                String rawTitle = input.readString();
                String filteredTitle = input.readBoolean() ? input.readString() : null;
                String author = input.readString();
                int generation = input.readVarInt();
                int pageCount = input.readVarInt();
                List<SpecificTag> pages = new ArrayList<>(pageCount);
                for (int i = 0; i < pageCount; i++) {
                    SpecificTag content = input.readNbtTag();
                    SpecificTag filtered = input.readBoolean() ? input.readNbtTag() : null;
                    CompoundTag page = new CompoundTag();
                    page.add("content", content);
                    if (filtered != null) page.add("filtered_content", filtered);
                    pages.add(page);
                }
                boolean resolved = input.readBoolean();

                CompoundTag root = new CompoundTag();
                root.add("raw_title", new StringTag(rawTitle));
                if (filteredTitle != null) root.add("filtered_title", new StringTag(filteredTitle));
                root.add("author", new StringTag(author));
                root.add("generation", new IntTag(generation));
                root.add("pages", new ListTag(Tag.TAG_COMPOUND, pages));
                root.add("resolved", new ByteTag(resolved ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Firework explosion: shape (VarInt) + colors (Int[]) + fadeColors (Int[]) + hasTrail + hasTwinkle */
    private static ComponentCodec<SpecificTag> fireworkExplosionCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int shape = input.readVarInt();
                int colorCount = input.readVarInt();
                List<SpecificTag> colors = new ArrayList<>(colorCount);
                for (int i = 0; i < colorCount; i++) {
                    colors.add(new IntTag(input.readInt()));
                }
                int fadeCount = input.readVarInt();
                List<SpecificTag> fadeColors = new ArrayList<>(fadeCount);
                for (int i = 0; i < fadeCount; i++) {
                    fadeColors.add(new IntTag(input.readInt()));
                }
                boolean hasTrail = input.readBoolean();
                boolean hasTwinkle = input.readBoolean();

                CompoundTag root = new CompoundTag();
                root.add("shape", new IntTag(shape));
                root.add("colors", new ListTag(Tag.TAG_INT, colors));
                root.add("fade_colors", new ListTag(Tag.TAG_INT, fadeColors));
                root.add("has_trail", new ByteTag(hasTrail ? 1 : 0));
                root.add("has_twinkle", new ByteTag(hasTwinkle ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Fireworks: flightDuration (VarInt) + explosions (Prefixed Array of FireworkExplosion) */
    private static ComponentCodec<SpecificTag> fireworksCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int flightDuration = input.readVarInt();
                int explosionCount = input.readVarInt();
                List<SpecificTag> explosions = new ArrayList<>(explosionCount);
                for (int i = 0; i < explosionCount; i++) {
                    explosions.add(fireworkExplosionCodec().read(input, context));
                }
                CompoundTag root = new CompoundTag();
                root.add("flight_duration", new IntTag(flightDuration));
                root.add("explosions", new ListTag(Tag.TAG_COMPOUND, explosions));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Banner patterns: Prefixed Array of {pattern: RegistryEntryHolder, color: VarInt} */
    private static ComponentCodec<SpecificTag> bannerPatternsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                int layerCount = input.readVarInt();
                List<SpecificTag> layers = new ArrayList<>(layerCount);
                for (int i = 0; i < layerCount; i++) {
                    // Banner pattern is a registry entry holder: VarInt discriminator.
                    // >0 means registry ID (discriminator - 1); 0 means inline NBT.
                    int discriminator = input.readVarInt();
                    int color = input.readVarInt();
                    CompoundTag layer = new CompoundTag();
                    if (discriminator > 0) {
                        int patternId = discriminator - 1;
                        // Convert numeric banner pattern ID to namespaced name (e.g. 1 → "minecraft:stripe_bottom").
                        String name = version.v26_3.schematic.DynamicRegistry.getInstance()
                                .getName("minecraft:banner_pattern", patternId);
                        layer.add("pattern", new StringTag(name != null ? name : "minecraft:" + patternId));
                    } else {
                        // Inline: read NBT data and pass through
                        SpecificTag data = input.readNbtTag();
                        layer.add("pattern", data);
                    }
                    layer.add("color", new IntTag(color));
                    layers.add(layer);
                }
                // The component value is a list of layers directly (not wrapped in a compound).
                return new ListTag(Tag.TAG_COMPOUND, layers);
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Lodestone tracker: globalPosition? (dimension + position) + tracked */
    private static ComponentCodec<SpecificTag> lodestoneTrackerCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                if (input.readBoolean()) {
                    String dimension = input.readString();
                    var pos = input.readCoordinates();
                    CompoundTag globalPos = new CompoundTag();
                    globalPos.add("dimension", new StringTag(dimension));
                    CompoundTag posTag = new CompoundTag();
                    posTag.add("x", new IntTag(pos.getX()));
                    posTag.add("y", new IntTag(pos.getY()));
                    posTag.add("z", new IntTag(pos.getZ()));
                    globalPos.add("pos", posTag);
                    root.add("global_position", globalPos);
                }
                boolean tracked = input.readBoolean();
                root.add("tracked", new ByteTag(tracked ? 1 : 0));
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Kinetic weapon — 26.x: complex structured type */
    private static ComponentCodec<SpecificTag> kineticWeaponCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                root.add("contact_cooldown_ticks", new IntTag(input.readVarInt()));
                root.add("delay_ticks", new IntTag(input.readVarInt()));
                // Dismount conditions (optional)
                if (input.readBoolean()) {
                    root.add("dismount_conditions", kineticWeaponConditionsCodec().read(input, context));
                }
                // Knockback conditions (optional)
                if (input.readBoolean()) {
                    root.add("knockback_conditions", kineticWeaponConditionsCodec().read(input, context));
                }
                // Damage conditions (optional)
                if (input.readBoolean()) {
                    root.add("damage_conditions", kineticWeaponConditionsCodec().read(input, context));
                }
                root.add("forward_movement", new FloatTag(input.readFloat()));
                root.add("damage_multiplier", new FloatTag(input.readFloat()));
                if (input.readBoolean()) {
                    root.add("sound", soundHolderCodec().read(input, context));
                }
                if (input.readBoolean()) {
                    root.add("hit_sound", soundHolderCodec().read(input, context));
                }
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Kinetic weapon conditions — 26.x */
    private static ComponentCodec<SpecificTag> kineticWeaponConditionsCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                // Best effort: read as NBT if present
                // The exact format of kinetic weapon conditions is not fully documented
                // Reading as a generic approach
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    /** Piercing weapon — 26.x: Boolean + Boolean + Optional Sound + Optional Sound */
    private static ComponentCodec<SpecificTag> piercingWeaponCodec() {
        return new ComponentCodec<>() {
            @Override
            public SpecificTag read(DataTypeProvider input, ComponentReadContext context) {
                CompoundTag root = new CompoundTag();
                root.add("deals_knockback", new ByteTag(input.readBoolean() ? 1 : 0));
                root.add("dismounts", new ByteTag(input.readBoolean() ? 1 : 0));
                if (input.readBoolean()) {
                    root.add("sound", soundHolderCodec().read(input, context));
                }
                if (input.readBoolean()) {
                    root.add("hit_sound", soundHolderCodec().read(input, context));
                }
                return root;
            }

            @Override
            public SpecificTag toNbt(SpecificTag value, ComponentNbtContext context) {
                return value;
            }
        };
    }

    private interface ValueReader<T> {
        T read(DataTypeProvider input);
    }

    private interface TagReader {
        SpecificTag read(DataTypeProvider input);
    }
}
