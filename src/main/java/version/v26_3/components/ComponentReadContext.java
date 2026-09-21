package version.v26_3.components;

import core.snapshot.SnapshotCompleteness;
import version.v26_3.registries.DataComponentRegistry;

public record ComponentReadContext(
        DataComponentRegistry registry,
        SnapshotCompleteness completeness,
        String protocolVersion,
        String source,
        int depth,
        int maxDepth
) {
    public ComponentReadContext child() {
        if (depth >= maxDepth) {
            throw new IllegalStateException("Maximum data component recursion depth exceeded");
        }
        return new ComponentReadContext(registry, completeness, protocolVersion, source, depth + 1, maxDepth);
    }
}
