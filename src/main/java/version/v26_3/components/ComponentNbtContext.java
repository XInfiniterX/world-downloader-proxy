package version.v26_3.components;

import core.snapshot.SnapshotCompleteness;
import version.v26_3.registries.DataComponentRegistry;

public record ComponentNbtContext(
        DataComponentRegistry registry,
        SnapshotCompleteness completeness,
        int depth,
        int maxDepth
) {
    public ComponentNbtContext child() {
        if (depth >= maxDepth) {
            throw new IllegalStateException("Maximum data component NBT recursion depth exceeded");
        }
        return new ComponentNbtContext(registry, completeness, depth + 1, maxDepth);
    }
}
