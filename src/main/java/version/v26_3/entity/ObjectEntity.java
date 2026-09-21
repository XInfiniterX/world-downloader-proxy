package version.v26_3.entity;

import se.llbit.nbt.CompoundTag;
import version.v26_3.packets.DataTypeProvider;

public class ObjectEntity extends Entity {
    protected ObjectEntity() { }

    @Override
    protected void addNbtData(CompoundTag root) { }

    protected void setData(int data) { }

    public static Entity parse(DataTypeProvider provider) {
        PrimitiveEntity primitive = PrimitiveEntity.parse(provider);
        Entity ent = primitive.getEntity(ObjectEntity::new);

        if (ent == null) { return null; }


        ent.readPosition(provider);


        // as of 26.1, velocity moved from the end of the packet to right after the position.
        // That's the only layout used by the supported versions (26.x).
        parseVelocity(provider);

        ent.pitch = provider.readNext();
        ent.yaw = provider.readNext();
        // 1.19+ includes head rotation and uses VarInt for data; that's the only layout used by 26.x.
        provider.readNext(); // head rotation
        int data = provider.readVarInt();

        // only if it's an ObjectEntity do we actually set the data bit
        if (ent instanceof ObjectEntity) {
            ((ObjectEntity) ent).setData(data);
        }

        return ent;
    }
}
