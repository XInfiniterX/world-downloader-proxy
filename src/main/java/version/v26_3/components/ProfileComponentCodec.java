package version.v26_3.components;

import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.IntArrayTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;
import se.llbit.nbt.StringTag;
import se.llbit.nbt.Tag;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProfileComponentCodec implements ComponentCodec<ProfileComponent> {
    private static final int MAX_PROPERTIES = 16;

    @Override
    public ProfileComponent read(DataTypeProvider input, ComponentReadContext context) {
        int type = input.readVarInt();
        ProfileComponent.Kind kind;
        String name;
        UUID id;
        List<ProfileComponent.Property> properties;
        if (type == 0) {
            kind = ProfileComponent.Kind.PARTIAL;
            name = readOptionalString(input, 16, "profile name");
            id = input.readBoolean() ? input.readUUID() : null;
            properties = readProperties(input);
        } else if (type == 1) {
            kind = ProfileComponent.Kind.COMPLETE;
            id = input.readUUID();
            name = readString(input, 16, "profile name");
            properties = readProperties(input);
        } else {
            throw new IllegalArgumentException("Unknown resolvable profile type: " + type);
        }

        String texture = readOptionalString(input, 32767, "profile texture");
        String cape = readOptionalString(input, 32767, "profile cape");
        String elytra = readOptionalString(input, 32767, "profile elytra");
        ProfileComponent.Model model = null;
        if (input.readBoolean()) {
            int modelId = input.readVarInt();
            model = switch (modelId) {
                case 0 -> ProfileComponent.Model.WIDE;
                case 1 -> ProfileComponent.Model.SLIM;
                default -> throw new IllegalArgumentException("Unknown player skin model: " + modelId);
            };
        }
        return new ProfileComponent(kind, name, id, properties, texture, cape, elytra, model);
    }

    private static List<ProfileComponent.Property> readProperties(DataTypeProvider input) {
        int count = input.readVarInt();
        if (count < 0 || count > MAX_PROPERTIES) {
            throw new IllegalArgumentException("Invalid profile property count: " + count);
        }
        List<ProfileComponent.Property> properties = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = readString(input, 64, "profile property name");
            String value = readString(input, 32767, "profile property value");
            String signature = readOptionalString(input, 1024, "profile property signature");
            properties.add(new ProfileComponent.Property(name, value, signature));
        }
        return properties;
    }

    private static String readOptionalString(DataTypeProvider input, int maxLength, String field) {
        return input.readBoolean() ? readString(input, maxLength, field) : null;
    }

    private static String readString(DataTypeProvider input, int maxLength, String field) {
        String value = input.readString();
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " UTF-16 code units");
        }
        return value;
    }

    @Override
    public SpecificTag toNbt(ProfileComponent value, ComponentNbtContext context) {
        CompoundTag result = new CompoundTag();
        if (value.name() != null) {
            result.add("name", new StringTag(value.name()));
        }
        if (value.id() != null) {
            result.add("id", new IntArrayTag(value.id().asIntArray()));
        }
        if (!value.properties().isEmpty()) {
            List<SpecificTag> properties = new ArrayList<>(value.properties().size());
            for (ProfileComponent.Property property : value.properties()) {
                CompoundTag tag = new CompoundTag();
                tag.add("name", new StringTag(property.name()));
                tag.add("value", new StringTag(property.value()));
                if (property.signature() != null) {
                    tag.add("signature", new StringTag(property.signature()));
                }
                properties.add(tag);
            }
            result.add("properties", new ListTag(Tag.TAG_COMPOUND, properties));
        }
        if (value.texture() != null) {
            result.add("texture", new StringTag(value.texture()));
        }
        if (value.cape() != null) {
            result.add("cape", new StringTag(value.cape()));
        }
        if (value.elytra() != null) {
            result.add("elytra", new StringTag(value.elytra()));
        }
        if (value.model() != null) {
            result.add("model", new StringTag(value.model().name().toLowerCase(Locale.ROOT)));
        }
        return result;
    }
}
