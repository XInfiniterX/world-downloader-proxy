package version.v26_3.container;

import core.coordinates.Coordinate2D;
import core.coordinates.Coordinate3D;
import se.llbit.nbt.CompoundTag;
import version.v26_3.registries.RegistryManager;

import java.util.ArrayList;
import java.util.List;

public class InventoryWindow {
    private int windowType;
    private String windowTitle;

    private int slotCount;
    private List<Slot> slotList;

    Coordinate3D containerLocation;

    /**
     * Constructor where the slot count is determined from the window type via the menu registry.
     */
    InventoryWindow(int windowType, String windowTitle, Coordinate3D containerLocation) {
        this.windowType = windowType;
        this.windowTitle = windowTitle;
        this.containerLocation = containerLocation;

        this.slotCount = RegistryManager.getInstance().getMenuRegistry().getSlotCount(windowType);
    }

    private InventoryWindow(InventoryWindow other) {
        this.windowType = other.windowType;
        this.windowTitle = other.windowTitle;
        this.containerLocation = other.containerLocation;
        this.slotCount = other.slotCount;
        this.slotList = other.slotList;
    }

    // use the slot count to avoid adding items from the player's inventory
    public void setSlots(List<Slot> slots) {
        slotList = slots.subList(0, slotCount);
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public Coordinate3D getContainerLocation() {
        return containerLocation;
    }

    public void adjustContainerLocation(Coordinate2D change) {
        this.containerLocation = this.containerLocation.add(change);
    }

    public List<Slot> getSlotList() {
        return slotList;
    }

    public int getType() {
        return windowType;
    }

    public InventoryWindow[] split() {
        InventoryWindow first = new InventoryWindow(this);
        InventoryWindow second = new InventoryWindow(this);

        first.slotList = slotList.subList(0, slotCount / 2);
        second.slotList = slotList.subList(slotCount / 2, slotCount);

        return new InventoryWindow[]{first, second};
    }

    public List<CompoundTag> getSlotsNbt() {
        List<CompoundTag> result = new ArrayList<>(slotList.size());
        for (int i = 0; i < slotList.size(); i++) {
            Slot slot = slotList.get(i);
            if (slot == null) { continue; }

            result.add(slot.toNbt(i));
        }
        return result;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public boolean hasCustomName() {
        return !windowTitle.startsWith("{\"translate");
    }

    @Override
    public String toString() {
        return "InventoryWindow{" +
                "windowType=" + windowType +
                ", windowTitle='" + windowTitle + '\'' +
                ", slotCount=" + slotCount +
                ", slotList=" + slotList +
                ", containerLocation=" + containerLocation +
                '}';
    }
}
