package version.v26_3.components;

import core.snapshot.SnapshotCompleteness;
import core.snapshot.SnapshotDiagnostic;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.SpecificTag;
import version.v26_3.packets.DataTypeProvider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DataComponentPatch {
    private static final int MAX_COMPONENTS = 4096;
    private final Map<String, DataComponentValue<?>> added = new LinkedHashMap<>();
    private final Set<String> removed = new LinkedHashSet<>();
    private final SnapshotCompleteness completeness;
    private boolean fullyParsed = true;

    private DataComponentPatch(SnapshotCompleteness completeness) {
        this.completeness = completeness;
    }

    /**
     * Returns {@code true} if the entire patch was parsed successfully.
     * When {@code false}, the buffer position is undefined and callers must not
     * attempt to read further data from the same stream — only already-parsed
     * components are available.
     */
    public boolean isFullyParsed() {
        return fullyParsed;
    }

    public static DataComponentPatch empty() {
        return new DataComponentPatch(new SnapshotCompleteness());
    }

    public static DataComponentPatch read(DataTypeProvider input, ComponentReadContext context) {
        return read(input, context, ComponentCodecs.defaults());
    }

    static DataComponentPatch read(DataTypeProvider input, ComponentReadContext context, ComponentCodecs codecs) {
        DataComponentPatch patch = new DataComponentPatch(context.completeness());
        int addedCount = input.readVarInt();
        int removedCount = input.readVarInt();
        validateCount(addedCount, "added");
        validateCount(removedCount, "removed");

        for (int i = 0; i < addedCount; i++) {
            int position = input.position();
            int componentId = input.readVarInt();
            String name = context.registry().getName(componentId);
            ComponentCodec<?> codec = name == null ? null : codecs.get(name);
            if (codec == null) {
                SnapshotDiagnostic diagnostic = new SnapshotDiagnostic(
                        "UNSUPPORTED_DATA_COMPONENT", context.protocolVersion(), context.source(), componentId,
                        name, position, "No wire codec for data component " + componentId + " (" + name + ")"
                );
                context.completeness().markIncomplete(diagnostic);
                patch.fullyParsed = false;
                // Cannot skip unknown component data — stop parsing to avoid
                // buffer misalignment. Already-parsed components are preserved.
                return patch;
            }
            try {
                patch.added.put(name, readValue(name, codec, input, context));
            } catch (RuntimeException exception) {
                context.completeness().markIncomplete(new SnapshotDiagnostic(
                        "DATA_COMPONENT_DECODE_FAILED", context.protocolVersion(), context.source(), componentId,
                        name, position, exception.getMessage()
                ));
                patch.fullyParsed = false;
                return patch;
            }
        }

        for (int i = 0; i < removedCount; i++) {
            int position = input.position();
            int componentId = input.readVarInt();
            String name = context.registry().getName(componentId);
            if (name == null) {
                context.completeness().markIncomplete(new SnapshotDiagnostic(
                        "UNKNOWN_REMOVED_DATA_COMPONENT", context.protocolVersion(), context.source(), componentId,
                        null, position, "Unknown removed data component " + componentId
                ));
            } else {
                patch.removed.add(name);
            }
        }
        if (!patch.removed.isEmpty()) {
            context.completeness().markIncomplete(new SnapshotDiagnostic(
                    "REMOVED_DATA_COMPONENT_NBT_UNSUPPORTED", context.protocolVersion(), context.source(), null,
                    null, input.position(), "Removed data component NBT encoding is not implemented"
            ));
        }
        return patch;
    }

    private static void validateCount(int count, String kind) {
        if (count < 0 || count > MAX_COMPONENTS) {
            throw new IllegalArgumentException("Invalid " + kind + " data component count: " + count);
        }
    }

    private static <T> DataComponentValue<T> readValue(String name, ComponentCodec<T> codec,
                                                        DataTypeProvider input, ComponentReadContext context) {
        return new DataComponentValue<>(name, codec.read(input, context), codec);
    }

    public CompoundTag toNbt(ComponentNbtContext context) {
        CompoundTag result = new CompoundTag();
        for (DataComponentValue<?> value : added.values()) {
            SpecificTag tag = value.toNbt(context);
            if (tag != null) {
                result.add(value.type(), tag);
            }
        }
        return result;
    }

    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }

    public Map<String, DataComponentValue<?>> getAdded() {
        return Collections.unmodifiableMap(added);
    }

    public Set<String> getRemoved() {
        return Collections.unmodifiableSet(removed);
    }

    public SnapshotCompleteness getCompleteness() {
        return completeness;
    }
}
