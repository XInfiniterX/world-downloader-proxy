package version.v26_3.entity.metadata;

import se.llbit.nbt.ByteTag;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.ShortTag;
import se.llbit.nbt.SpecificTag;
import version.v26_3.packets.DataTypeProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Parses entity metadata ("Set Entity Data" packet). If a future Minecraft version changes the metadata
 * type IDs or the tracked fields again, add a new subclass overriding {@link #getTypeHandler},
 * {@link #getIndexHandler} and/or {@link #addNbtTags}, and pick it in {@link #getVersionedMetaData()} (see
 * the pre-26.x history of this class in git for an example of that pattern).
 */
public class MetaData {
    private final static int TERMINATOR = 0xFF;

    // network.syncher.EntityDataSerializers
    private static final Map<Integer, Consumer<DataTypeProvider>> typeHandlers = new HashMap<>();
    static {
        typeHandlers.put(0, DataTypeProvider::readNext);
        typeHandlers.put(1, DataTypeProvider::readVarInt);
        typeHandlers.put(2, DataTypeProvider::readVarLong);
        typeHandlers.put(3, DataTypeProvider::readFloat);
        typeHandlers.put(4, DataTypeProvider::readString);
        typeHandlers.put(5, DataTypeProvider::readChat);
        typeHandlers.put(6, DataTypeProvider::readOptChat);
        typeHandlers.put(7, DataTypeProvider::readSlot);
        typeHandlers.put(8, DataTypeProvider::readBoolean);
        typeHandlers.put(9, provider -> {
            provider.readFloat();
            provider.readFloat();
            provider.readFloat();
        });
        typeHandlers.put(10, DataTypeProvider::readLong);
        typeHandlers.put(11, provider -> {
            if (provider.readBoolean()) {
                provider.readLong();
            }
        });
        typeHandlers.put(12, DataTypeProvider::readVarInt);
        typeHandlers.put(13, provider -> {
            if (provider.readBoolean()) {
                provider.readUUID();
            }
        });
        typeHandlers.put(14, DataTypeProvider::readVarInt);
        typeHandlers.put(15, DataTypeProvider::readVarInt);
        typeHandlers.put(18, provider -> {
            provider.readVarInt();
            provider.readVarInt();
            provider.readVarInt();
        });
        for (int i = 19; i <= 32; i++) {
            typeHandlers.put(i, DataTypeProvider::readVarInt);
        }
        for (int i = 35; i <= 38; i++) {
            typeHandlers.put(i, DataTypeProvider::readVarInt);
        }
        typeHandlers.put(39, provider -> {
            provider.readFloat();
            provider.readFloat();
            provider.readFloat();
        });
        typeHandlers.put(40, provider -> {
            provider.readFloat();
            provider.readFloat();
            provider.readFloat();
            provider.readFloat();
        });
        typeHandlers.put(43, DataTypeProvider::readVarInt);
    }

    private int air = 300;
    private SpecificTag customName;
    private boolean customNameVisible;
    private boolean isInvisible;
    private boolean isSilent;
    private boolean hasNoGravity;

    protected MetaData() { }

    public void parse(DataTypeProvider provider) {
        while (true) {
            int entryPosition = provider.position();
            int index = provider.readNext() & 0xFF;
            if (index == TERMINATOR) { break; }

            int type = provider.readVarInt();

            Consumer<DataTypeProvider> indexHandler = getIndexHandler(index);
            Consumer<DataTypeProvider> typeHandler = getTypeHandler(type);

            if (indexHandler == null && typeHandler == null) {
                provider.markIncomplete(
                        "UNSUPPORTED_ENTITY_DATA_SERIALIZER", "entity_metadata", type, null, entryPosition,
                        "No metadata reader for serializer " + type + " at entity data index " + index
                );
                break;
            }

            if (indexHandler != null) {
                indexHandler.accept(provider);
            } else {
                typeHandler.accept(provider);
            }
        }
    }

    /**
     * Returns a MetaData object of the correct version.
     * @return the metadata matching the given version
     */
    public static MetaData getVersionedMetaData() {
        return new MetaData();
    }

    public Consumer<DataTypeProvider> getTypeHandler(int i) {
        return typeHandlers.getOrDefault(i, null);
    }

    public Consumer<DataTypeProvider> getIndexHandler(int i) {
        switch (i) {
            case 0: return provider -> isInvisible = (provider.readNext() & 0x20) > 0;
            case 1: return provider -> this.air = provider.readVarInt();
            case 2: return provider -> this.customName = provider.readOptChatTag();
            case 3: return provider -> this.customNameVisible = provider.readBoolean();
            case 4: return provider -> this.isSilent = provider.readBoolean();
            case 5: return provider -> this.hasNoGravity = provider.readBoolean();
        }
        return null;
    }

    public void addNbtTags(CompoundTag nbt) {
        nbt.add("Silent", new ByteTag(isSilent ? 1 : 0));
        nbt.add("NoGravity", new ByteTag(hasNoGravity ? 1 : 0));
        nbt.add("Invisible", new ByteTag(isInvisible ? 1 : 0));
        nbt.add("CustomNameVisible", new ByteTag(customNameVisible ? 1 : 0));
        nbt.add("Air", new ShortTag((short) air));
        if (customName != null) {
            nbt.add("CustomName", customName);
        }
    }
}
