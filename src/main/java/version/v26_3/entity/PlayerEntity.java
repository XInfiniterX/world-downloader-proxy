package version.v26_3.entity;

import com.google.gson.Gson;
import core.coordinates.CoordinateDouble3D;
import core.interfaces.IPlayerEntity;
import core.messages.Messages;
import kong.unirest.Unirest;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerEntity implements IMovableEntity, IPlayerEntity {
    final static Map<UUID, String> knownNames = new ConcurrentHashMap<>();
    final static String API_GET_NAME = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private CoordinateDouble3D pos;
    private UUID uuid;
    private boolean hasRequestedName = false;
    private String name;

    PlayerEntity(UUID uuid) {
        this.uuid = uuid;
    }

    PlayerEntity(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        knownNames.put(uuid, name);
    }

    public static PlayerEntity parse(DataTypeProvider provider) {
        PlayerEntity ent = new PlayerEntity(provider.readUUID());
        ent.readPosition(provider);

        return ent;
    }

    /**
     * Fetch the player's name from the Mojang API.
     */
    private void fetchName() {
        if (hasRequestedName) {
            return;
        }
        hasRequestedName = true;

        if (knownNames.containsKey(uuid)) {
            System.out.println(Messages.console("console.auth.retrieved_name", knownNames.get(uuid)));
            this.name = knownNames.get(uuid);
            return;
        }

        Unirest.get(API_GET_NAME + uuid.toDashedString()).asStringAsync((str) -> {
            if (!str.isSuccess()) {
                return;
            }

            ProfileResponse res = new Gson().fromJson(str.getBody(), ProfileResponse.class);
            if (res.name != null) {
                knownNames.put(uuid, res.name);
                this.name = res.name;
            }
        });
    }

    static class ProfileResponse {
        String id;
        String name;
    }

    @Override
    public void incrementPosition(int dx, int dy, int dz) {
        if (pos == null) {
            return;
        }
        pos.increment(
                dx / Entity.CHANGE_MULTIPLIER,
                dy / Entity.CHANGE_MULTIPLIER,
                dz / Entity.CHANGE_MULTIPLIER
        );
    }

    @Override
    public void readPosition(DataTypeProvider provider) {
        this.pos = provider.readDoubleCoordinates();
    }

    @Override
    public String toString() {
        return "PlayerEntity{" +
                "uuid=" + uuid +
                '}';
    }

    public CoordinateDouble3D getPosition() {
        return pos;
    }

    public String getName() {
        if (!hasRequestedName) {
            fetchName();
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
        knownNames.put(uuid, name);
    }

    /**
     * Sets the initial position from a SpawnEntity packet. In protocol 776+
     * there is no separate AddPlayer packet, so players are spawned via
     * AddEntity and the position must be copied to the PlayerEntity.
     */
    public void setInitialPosition(double x, double y, double z) {
        this.pos = new CoordinateDouble3D(x, y, z);
    }

    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getUUIDString() {
        return uuid.toDashedString();
    }
}
