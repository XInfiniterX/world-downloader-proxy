package version.v26_3.entity;

import version.v26_3.entity.specific.*;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;
import version.v26_3.world.WorldManager;

import java.util.function.Supplier;

/**
 * Handle the initial entity fields, we need to know the type before we can instantiate the correct object.
 */
public class PrimitiveEntity {
    protected int id;
    protected UUID uuid;
    protected int type;
    protected String typeName;

    protected static PrimitiveEntity parse(DataTypeProvider provider) {
        PrimitiveEntity ent = new PrimitiveEntity();

        ent.id = provider.readVarInt();
        ent.uuid = provider.readUUID();
        ent.type = provider.readVarInt();
        ent.typeName = WorldManager.getInstance().getEntityMap().getName(ent.type);

        return ent;
    }

    public Entity getEntity(Supplier<Entity> generate) {
        if (typeName == null) {
            return null;
        }

        // 1.13+ uses string type names; that's the only format used by the supported versions (26.x).
        return moveTo(switch(typeName) {
            case "minecraft:armor_stand" -> new ArmorStand();
            case "minecraft:axolotl" -> new Axolotl();
            case "minecraft:block_display" -> new DisplayEntity.BlockDisplay();
            case "minecraft:cat" -> new Cat();
            case "minecraft:cushion" -> new Cushion();
            case "minecraft:horse" -> new Horse();
            case "minecraft:interaction" -> new DisplayEntity.InteractionEntity();
            case "minecraft:item" -> new DroppedItem();
            case "minecraft:item_display" -> new DisplayEntity.ItemDisplay();
            case "minecraft:item_frame", "minecraft:glow_item_frame" -> new ItemFrame();
            case "minecraft:text_display" -> new DisplayEntity.TextDisplay();
            case "minecraft:sheep" -> new Sheep();
            case "minecraft:villager" -> new Villager();
            default -> generate.get();
        });
    }

    private Entity moveTo(Entity ent) {
        ent.id = this.id;
        ent.uuid = this.uuid;
        ent.type = this.type;
        ent.typeName = this.typeName;

        return ent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PrimitiveEntity that = (PrimitiveEntity) o;

        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}
