package version.v26_3.schematic.export;

import core.coordinates.Coordinate3D;
import core.interfaces.IBlockState;
import core.schematic.BoundingBox;
import core.schematic.export.BlockRegionReader;
import se.llbit.nbt.SpecificTag;
import version.v26_3.world.WorldManager;

import java.util.List;

/**
 * The production {@link BlockRegionReader}: reads block data out of the in-memory world held by
 * {@link WorldManager}. This is the only class in the export pipeline that talks to
 * {@code WorldManager}.
 *
 * {@code WorldManager.blockStateAt} always reads from the manager's currently active dimension;
 * callers are responsible for verifying that dimension still matches the one the selection was
 * made in before reading (see {@code SchematicExportService}).
 */
public class WorldManagerBlockRegionReader implements core.schematic.export.BlockRegionReader {
    private final WorldManager worldManager;

    public WorldManagerBlockRegionReader(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    @Override
    public IBlockState blockAt(Coordinate3D coordinate) {
        return worldManager.blockStateAt(coordinate);
    }

    @Override
    public String biomeAt(Coordinate3D coordinate) {
        return worldManager.biomeAt(coordinate);
    }

    @Override
    public SpecificTag blockEntityAt(Coordinate3D coordinate) {
        return worldManager.blockEntityAt(coordinate);
    }

    @Override
    public List<SpecificTag> entitiesIn(BoundingBox box) {
        return worldManager.getEntityRegistry().getEntitiesNbt(box);
    }
}
