package version.v26_3.entity;

import se.llbit.nbt.CompoundTag;
import version.v26_3.entity.metadata.MetaData;
import version.v26_3.packets.DataTypeProvider;

public class MobEntity extends Entity {
    private float headPitch;

    private MetaData metaData;

    public MobEntity() {
    }

    @Override
    protected void addNbtData(CompoundTag root) {
        if (metaData != null) {
            metaData.addNbtTags(root);
        }
    }

    public static Entity parse(DataTypeProvider provider) {
        PrimitiveEntity primitive = PrimitiveEntity.parse(provider);
        Entity ent = primitive.getEntity(MobEntity::new);

        if (ent == null) { return null; }

        ent.readPosition(provider);

        // As of 26.1 (protocol 774+), velocity (LpVec3) moved before the angles. That's the
        // only layout used by the supported versions (26.x).
        parseVelocity(provider);

        ent.pitch = provider.readNext();
        ent.yaw = provider.readNext();
        byte headPitch = provider.readNext();

        // 1.19+ includes a VarInt data field; that's the only layout used by 26.x.
        provider.readVarInt(); // data — not used by MobEntity but must be consumed

        if (ent instanceof MobEntity) {
            ((MobEntity) ent).headPitch = headPitch;
        }

        return ent;
    }

    @Override
    public void parseMetadata(DataTypeProvider provider) {
        if (metaData == null) {
            metaData = MetaData.getVersionedMetaData();
        }
        try {
            metaData.parse(provider);
        } catch (Exception ex) {
            // couldn't parse metadata, whatever
        }
    }
}
