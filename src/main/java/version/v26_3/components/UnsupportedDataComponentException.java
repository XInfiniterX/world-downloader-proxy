package version.v26_3.components;

import core.snapshot.SnapshotDiagnostic;

public final class UnsupportedDataComponentException extends RuntimeException {
    private final DataComponentPatch patch;
    private final SnapshotDiagnostic diagnostic;

    public UnsupportedDataComponentException(DataComponentPatch patch, SnapshotDiagnostic diagnostic) {
        super(diagnostic.detail());
        this.patch = patch;
        this.diagnostic = diagnostic;
    }

    public DataComponentPatch getPatch() {
        return patch;
    }

    public SnapshotDiagnostic getDiagnostic() {
        return diagnostic;
    }
}
