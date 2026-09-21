package version.v26_3.entity.specific;

import se.llbit.nbt.*;
import version.v26_3.chunk.palette.BlockState;
import version.v26_3.chunk.palette.GlobalPaletteProvider;
import version.v26_3.container.Slot;
import version.v26_3.entity.ObjectEntity;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;

import java.util.Arrays;
import java.util.function.Consumer;

public abstract class DisplayEntity extends ObjectEntity {
    private DisplayMetaData metaData;

    @Override
    protected void addNbtData(CompoundTag root) {
        super.addNbtData(root);
        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    @Override
    public synchronized void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = createMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ignored) {
        }
    }

    protected abstract DisplayMetaData createMetaData();

    public static final class BlockDisplay extends DisplayEntity {
        public BlockDisplay() {
        }

        @Override
        protected DisplayMetaData createMetaData() {
            return new BlockDisplayMetaData();
        }
    }

    public static final class ItemDisplay extends DisplayEntity {
        public ItemDisplay() {
        }

        @Override
        protected DisplayMetaData createMetaData() {
            return new ItemDisplayMetaData();
        }
    }

    public static final class TextDisplay extends DisplayEntity {
        public TextDisplay() {
        }

        @Override
        protected DisplayMetaData createMetaData() {
            return new TextDisplayMetaData();
        }
    }

    public static final class InteractionEntity extends ObjectEntity {
        private InteractionMetaData metaData;

        public InteractionEntity() {
        }

        @Override
        protected void addNbtData(CompoundTag root) {
            super.addNbtData(root);
            if (metaData != null) {
                metaData.addNbtTags(root);
            }
        }

        @Override
        public synchronized void parseMetadata(DataTypeProvider provider) {
            if (metaData == null) {
                metaData = new InteractionMetaData();
            }
            try {
                metaData.parse(provider);
            } catch (Exception ignored) {
            }
        }
    }

    protected static class DisplayMetaData extends MetaData {
        private int interpolationDuration;
        private int teleportDuration;
        private int startInterpolation;
        private ListTag translation = vector(0, 0, 0);
        private ListTag scale = vector(1, 1, 1);
        private ListTag leftRotation = quaternion(0, 0, 0, 1);
        private ListTag rightRotation = quaternion(0, 0, 0, 1);
        private byte billboard;
        private int brightness = -1;
        private float viewRange = 1;
        private float shadowRadius;
        private float shadowStrength = 1;
        private float width;
        private float height;
        private int glowColorOverride = -1;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            CompoundTag transformation = new CompoundTag();
            transformation.add("translation", translation);
            transformation.add("scale", scale);
            transformation.add("left_rotation", leftRotation);
            transformation.add("right_rotation", rightRotation);
            nbt.add("transformation", transformation);
            nbt.add("billboard", new StringTag(switch (billboard) {
                case 1 -> "vertical";
                case 2 -> "horizontal";
                case 3 -> "center";
                default -> "fixed";
            }));
            nbt.add("interpolation_duration", new IntTag(interpolationDuration));
            nbt.add("teleport_duration", new IntTag(teleportDuration));
            if (startInterpolation != 0) {
                nbt.add("start_interpolation", new IntTag(startInterpolation));
            }
            nbt.add("view_range", new FloatTag(viewRange));
            nbt.add("shadow_radius", new FloatTag(shadowRadius));
            nbt.add("shadow_strength", new FloatTag(shadowStrength));
            nbt.add("width", new FloatTag(width));
            nbt.add("height", new FloatTag(height));
            nbt.add("glow_color_override", new IntTag(glowColorOverride));
            if (brightness >= 0) {
                CompoundTag brightnessTag = new CompoundTag();
                brightnessTag.add("block", new IntTag((brightness >> 4) & 0x0F));
                brightnessTag.add("sky", new IntTag((brightness >> 20) & 0x0F));
                nbt.add("brightness", brightnessTag);
            }
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 8 -> provider -> teleportDuration = provider.readVarInt();
                case 9 -> provider -> startInterpolation = provider.readVarInt();
                case 10 -> provider -> interpolationDuration = provider.readVarInt();
                case 11 -> provider -> translation = readVector(provider);
                case 12 -> provider -> scale = readVector(provider);
                case 13 -> provider -> rightRotation = readQuaternion(provider);
                case 14 -> provider -> leftRotation = readQuaternion(provider);
                case 15 -> provider -> billboard = provider.readNext();
                case 16 -> provider -> brightness = provider.readVarInt();
                case 17 -> provider -> viewRange = provider.readFloat();
                case 18 -> provider -> shadowRadius = provider.readFloat();
                case 19 -> provider -> shadowStrength = provider.readFloat();
                case 20 -> provider -> width = provider.readFloat();
                case 21 -> provider -> height = provider.readFloat();
                case 22 -> provider -> glowColorOverride = provider.readVarInt();
                default -> super.getIndexHandler(i);
            };
        }

        private static ListTag readVector(DataTypeProvider provider) {
            return vector(provider.readFloat(), provider.readFloat(), provider.readFloat());
        }

        private static ListTag readQuaternion(DataTypeProvider provider) {
            return quaternion(provider.readFloat(), provider.readFloat(), provider.readFloat(), provider.readFloat());
        }

        private static ListTag vector(float x, float y, float z) {
            return new ListTag(Tag.TAG_FLOAT, Arrays.asList(new FloatTag(x), new FloatTag(y), new FloatTag(z)));
        }

        private static ListTag quaternion(float x, float y, float z, float w) {
            return new ListTag(Tag.TAG_FLOAT, Arrays.asList(
                    new FloatTag(x), new FloatTag(y), new FloatTag(z), new FloatTag(w)
            ));
        }
    }

    private static final class BlockDisplayMetaData extends DisplayMetaData {
        private int blockStateId;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            BlockState state = GlobalPaletteProvider.getGlobalPalette().getState(blockStateId);
            if (state != null) {
                nbt.add("block_state", state.toNbt());
            } else {
                CompoundTag air = new CompoundTag();
                air.add("id", new StringTag("minecraft:air"));
                nbt.add("block_state", air);
            }
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return i == 23 ? provider -> blockStateId = provider.readVarInt() : super.getIndexHandler(i);
        }
    }

    private static final class ItemDisplayMetaData extends DisplayMetaData {
        private static final String[] DISPLAY_CONTEXTS = {
                "none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand",
                "firstperson_righthand", "head", "gui", "ground", "fixed"
        };
        private Slot item;
        private byte displayContext;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            if (item != null) {
                nbt.add("item", item.toNbt());
            }
            int context = Byte.toUnsignedInt(displayContext);
            nbt.add("item_display", new StringTag(context < DISPLAY_CONTEXTS.length ? DISPLAY_CONTEXTS[context] : "none"));
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 23 -> provider -> item = provider.readSlot();
                case 24 -> provider -> displayContext = provider.readNext();
                default -> super.getIndexHandler(i);
            };
        }
    }

    private static final class TextDisplayMetaData extends DisplayMetaData {
        private SpecificTag text = new StringTag("");
        private int lineWidth = 200;
        private int background = 0x40000000;
        private byte textOpacity = -1;
        private byte flags;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            nbt.add("text", text);
            nbt.add("line_width", new IntTag(lineWidth));
            nbt.add("background", new IntTag(background));
            nbt.add("text_opacity", new ByteTag(textOpacity));
            nbt.add("shadow", new ByteTag((flags & 0x01) != 0 ? 1 : 0));
            nbt.add("see_through", new ByteTag((flags & 0x02) != 0 ? 1 : 0));
            nbt.add("default_background", new ByteTag((flags & 0x04) != 0 ? 1 : 0));
            nbt.add("alignment", new StringTag((flags & 0x08) != 0 ? "left" : (flags & 0x10) != 0 ? "right" : "center"));
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 23 -> provider -> {
                    SpecificTag value = provider.readChatTag();
                    text = value == null ? new StringTag("") : value;
                };
                case 24 -> provider -> lineWidth = provider.readVarInt();
                case 25 -> provider -> background = provider.readVarInt();
                case 26 -> provider -> textOpacity = provider.readNext();
                case 27 -> provider -> flags = provider.readNext();
                default -> super.getIndexHandler(i);
            };
        }
    }

    private static final class InteractionMetaData extends MetaData {
        private float width = 1;
        private float height = 1;
        private boolean response;

        @Override
        public void addNbtTags(CompoundTag nbt) {
            super.addNbtTags(nbt);
            nbt.add("width", new FloatTag(width));
            nbt.add("height", new FloatTag(height));
            nbt.add("response", new ByteTag(response ? 1 : 0));
        }

        @Override
        public Consumer<DataTypeProvider> getIndexHandler(int i) {
            return switch (i) {
                case 8 -> provider -> width = provider.readFloat();
                case 9 -> provider -> height = provider.readFloat();
                case 10 -> provider -> response = provider.readBoolean();
                default -> super.getIndexHandler(i);
            };
        }
    }
}
