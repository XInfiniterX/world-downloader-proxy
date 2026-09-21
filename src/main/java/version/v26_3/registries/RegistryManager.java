package version.v26_3.registries;

import version.v26_3.chunk.BlockEntityRegistry;
import version.v26_3.container.ItemRegistry;
import version.v26_3.container.MenuRegistry;
import version.v26_3.villagers.VillagerProfessionRegistry;
import version.v26_3.villagers.VillagerTypeRegistry;

import java.io.IOException;

public class RegistryManager {
    private static RegistryManager instance;
    private MenuRegistry menuRegistry;
    private ItemRegistry itemRegistry;
    private DataComponentRegistry dataComponentRegistry;
    private BlockEntityRegistry blockEntityRegistry;
    private VillagerProfessionRegistry villagerProfessionRegistry;
    private VillagerTypeRegistry villagerTypeRegistry;

    private RegistryManager() { }

    public static void setInstance(RegistryManager registryManager) {
        instance = registryManager;
    }

    public static RegistryManager getInstance() {
        if (instance == null) {
            instance = new RegistryManager();
        }
        return instance;
    }


    public MenuRegistry getMenuRegistry() {
        return menuRegistry;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public DataComponentRegistry getDataComponentRegistry() {
        return dataComponentRegistry;
    }

    public BlockEntityRegistry getBlockEntityRegistry() {
        return blockEntityRegistry;
    }

    public VillagerProfessionRegistry getVillagerProfessionRegistry() {
        return villagerProfessionRegistry;
    }

    public VillagerTypeRegistry getVillagerTypeRegistry() {
        return villagerTypeRegistry;
    }


    public void setRegistries(RegistryLoader loader) throws IOException {
        this.menuRegistry = loader.generateMenuRegistry();
        this.itemRegistry = loader.generateItemRegistry();
        this.dataComponentRegistry = loader.generateDataComponentRegistry();
        this.blockEntityRegistry = loader.generateBlockEntityRegistry();
        this.villagerProfessionRegistry = loader.generateVillagerProfessionRegistry();
        this.villagerTypeRegistry = loader.generateVillagerTypeRegistry();
    }
}
