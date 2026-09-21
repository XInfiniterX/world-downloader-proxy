package version.v26_3.entity;

import core.config.Config;
import core.coordinates.Coordinate3D;
import core.coordinates.CoordinateDim2D;
import core.schematic.BoundingBox;
import core.snapshot.SnapshotCompleteness;
import se.llbit.nbt.*;
import version.v26_3.container.Slot;
import version.v26_3.dimension.Dimension;
import version.v26_3.entity.version.EquipmentReader;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.LpVec3;
import version.v26_3.world.WorldManager;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

public abstract class Entity extends PrimitiveEntity implements IMovableEntity {
    private static EquipmentReader equipmentReader;
    public final static double CHANGE_MULTIPLIER = 4096.0;
    public final static float ROTATION_MULTIPLIER = 360f / 256f;

    protected double x, y, z;
    private CoordinateDim2D position;
    private Dimension dimension;

    protected float pitch;
    protected float yaw;
    private Slot[] equipment;
    private final SnapshotCompleteness completeness = new SnapshotCompleteness();

    private BiConsumer<CoordinateDim2D, CoordinateDim2D> onMove;

    Entity() {
        if (equipmentReader == null) {
            equipmentReader = EquipmentReader.getVersioned();
        }
        this.dimension = WorldManager.getInstance().getDimension();
    }

    public SpecificTag toNbt() {
        return toNbtRelativeTo(null);
    }

    synchronized SpecificTag toNbtIfInside(BoundingBox box) {
        if (x < box.getMin().getX() || x >= box.getMax().getX() + 1.0
                || y < box.getMin().getY() || y >= box.getMax().getY() + 1.0
                || z < box.getMin().getZ() || z >= box.getMax().getZ() + 1.0) {
            return null;
        }
        return toNbtRelativeTo(box.getMin());
    }

    synchronized SpecificTag toNbtRelativeTo(Coordinate3D origin) {
        CompoundTag root = new CompoundTag();
        if (origin == null) {
            addPosition(root);
        } else {
            addPositionRelativeTo(root, origin);
        }

        // write velocity as 0, we'd rather have entities become stationary
        List<DoubleTag> motion = Arrays.asList(new DoubleTag(0), new DoubleTag(0), new DoubleTag(0));
        root.add("Motion", new ListTag(ListTag.TAG_DOUBLE, motion));

        // 1.16+ uses the IntArray UUID representation (UUIDLeast/UUIDMost is deprecated).
        // That's the only format used by the supported versions (26.x).
        root.add("UUID", new IntArrayTag(uuid.asIntArray()));
        root.add("id", new StringTag(typeName));

        List<FloatTag> pos = Arrays.asList(new FloatTag(angleToRotation(yaw)), new FloatTag(angleToRotation(pitch)));
        root.add("Rotation", new ListTag(ListTag.TAG_FLOAT, pos));

        addNbtDataRelative(root, origin);
        addNbtEquipment(root);

        return root;
    }

    /**
     * Wire slot order: 0=MainHand, 1=Feet, 2=Legs, 3=Chest, 4=Head, 5=OffHand, 6=Body, 7=Saddle.
     * Minecraft 26.x uses a single "equipment" compound mapping slot names to ItemStacks,
     * replacing the old HandItems/ArmorItems lists.
     */
    private void addNbtEquipment(CompoundTag root) {
        if (equipment == null) {
            return;
        }

        CompoundTag equipmentTag = new CompoundTag();
        String[] slotNames = {"mainhand", "offhand", "feet", "legs", "chest", "head", "body", "saddle"};
        for (int i = 0; i < equipment.length && i < slotNames.length; i++) {
            if (equipment[i] != null) {
                equipmentTag.add(slotNames[i], slotToNbt(equipment[i]));
            }
        }

        if (!equipmentTag.isEmpty()) {
            root.add("equipment", equipmentTag);
        }
    }

    private CompoundTag slotToNbt(Slot s) {
        if (s == null) {
            return new CompoundTag();
        }
        return s.toNbt();
    }

    protected float angleToRotation(float angle) {
        float rotation = angle * ROTATION_MULTIPLIER;
        if (rotation < 0) {
            rotation += 360;
        }
        return rotation;
    }

    protected void addPosition(CompoundTag nbt) {
        double offsetX = x - Config.getCenterX();
        double offsetZ = z - Config.getCenterZ();
        List<DoubleTag> pos = Arrays.asList(new DoubleTag(offsetX), new DoubleTag(y), new DoubleTag(offsetZ));
        nbt.add("Pos", new ListTag(ListTag.TAG_DOUBLE, pos));
    }

    private void addPositionRelativeTo(CompoundTag nbt, Coordinate3D origin) {
        List<DoubleTag> pos = Arrays.asList(
                new DoubleTag(x - origin.getX()),
                new DoubleTag(y - origin.getY()),
                new DoubleTag(z - origin.getZ())
        );
        nbt.add("Pos", new ListTag(ListTag.TAG_DOUBLE, pos));
    }

    public void parseMetadata(DataTypeProvider provider) { };

    protected abstract void addNbtData(CompoundTag root);

    protected void addNbtDataRelative(CompoundTag root, Coordinate3D origin) {
        addNbtData(root);
    }

    /**
     * velocity is not saved but we still read it. As of 26.1 (protocol 774+) the wire format
     * changed from three Shorts to the compact LpVec3 encoding, so we must consume the right
     * number of bytes to keep the packet stream aligned.
     */
    protected static void parseVelocity(DataTypeProvider provider) {
        // As of 26.1, velocity uses the compact LpVec3 encoding instead of three Shorts.
        // That's the only format used by the supported versions (26.x).
        LpVec3.read(provider);
    }

    public Integer getId() {
        return id;
    }

    public String getTypeName() {
        return typeName;
    }

    public SnapshotCompleteness getCompleteness() {
        return completeness;
    }

    void mergeDecodeCompleteness(SnapshotCompleteness decoded) {
        completeness.merge(decoded);
    }

    public void registerOnLocationChange(BiConsumer<CoordinateDim2D, CoordinateDim2D> handler) {
        this.onMove = handler;
        handler.accept(null, position.addDimension(dimension));
    }

    /** Returns the chunk coordinates this entity currently resides in. */
    public CoordinateDim2D getChunkLocation() {
        return new CoordinateDim2D((int) Math.round(x), (int) Math.round(z), dimension).globalToDimChunk();
    }

    private void updateCoordinate() {
        CoordinateDim2D newPos = new CoordinateDim2D((int) Math.round(x), (int) Math.round(z), dimension);
        if (this.onMove != null) {
            this.onMove.accept(this.position, newPos);
        }
        this.position = newPos;
    }

    public synchronized void incrementPosition(int dx, int dy, int dz) {
        this.x += dx / CHANGE_MULTIPLIER;
        this.y += dy / CHANGE_MULTIPLIER;
        this.z += dz / CHANGE_MULTIPLIER;
        updateCoordinate();
    }

    public synchronized void readPosition(DataTypeProvider provider) {
        this.x = provider.readDouble();
        this.y = provider.readDouble();
        this.z = provider.readDouble();

        updateCoordinate();
    }

    /**
     * Parse list of slot data. Each slot starts with a byte where the first bit indicates whether another slot will
     * follow, the other 7 indicate the slot index.
     */
    public synchronized void addEquipment(DataTypeProvider provider) {
        this.equipment = equipmentReader.readSlots(this.equipment, provider);
    }
}
