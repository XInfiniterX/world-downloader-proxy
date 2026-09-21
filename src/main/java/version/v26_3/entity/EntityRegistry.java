package version.v26_3.entity;

import core.config.Config;
import core.coordinates.CoordinateDim2D;
import core.interfaces.IEntityRegistry;
import core.schematic.BoundingBox;
import se.llbit.nbt.SpecificTag;
import version.v26_3.chunk.Chunk;
import version.v26_3.entity.specific.Villager;
import version.v26_3.entity.version.RelativeMovementReader;
import version.v26_3.packets.DataTypeProvider;
import version.v26_3.packets.UUID;
import version.v26_3.world.WorldManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static core.util.ExceptionHandling.attempt;

public class EntityRegistry implements IEntityRegistry {

    private final Map<UUID, PlayerEntity> players;
    private final Map<CoordinateDim2D, Set<Entity>> perChunk;
    private final Map<Integer, Entity> entities;
    private final WorldManager worldManager;

    private final ExecutorService executor;

    public EntityRegistry(WorldManager manager) {
        this.worldManager = manager;
        this.perChunk = new ConcurrentHashMap<>();
        this.entities = new ConcurrentHashMap<>();
        this.players = new ConcurrentHashMap<>();

        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Entity Parser Service"));;
    }
    /**
     * Add a new entity.
     */
    public void addEntity(DataTypeProvider provider, Function<DataTypeProvider, Entity> parser) {
        this.executor.execute(() -> attempt(() -> {
            Entity ent = parser.apply(provider);
            if (ent == null) { return; }

            // If an entity with the same ID already exists (e.g. the server
            // re-sent AddEntity after the player walked away and back, and
            // schematic mode kept the old one), remove the stale entry from
            // perChunk before overwriting it — otherwise both the old and new
            // entity objects would be in the perChunk set.
            Entity existing = entities.get(ent.getId());
            if (existing != null) {
                CoordinateDim2D oldChunk = existing.getChunkLocation();
                Set<Entity> oldSet = perChunk.get(oldChunk);
                if (oldSet != null) {
                    oldSet.remove(existing);
                    if (oldSet.isEmpty()) {
                        perChunk.remove(oldChunk);
                    }
                }
            }

            entities.put(ent.getId(), ent);

            // If this is a player entity spawned via AddEntity (protocol 776+ has no
            // separate AddPlayer packet), copy the initial position to the PlayerEntity
            // in the players map so that MoveEntityPos updates work correctly.
            if ("minecraft:player".equals(ent.typeName)) {
                PlayerEntity player = players.get(ent.uuid);
                if (player != null && player.getPosition() == null) {
                    player.setInitialPosition(ent.x, ent.y, ent.z);
                }
            }

            ent.registerOnLocationChange((oldPos, newPos) -> {
                CoordinateDim2D oldChunk = oldPos == null ? null : oldPos.globalToDimChunk();
                CoordinateDim2D newChunk = newPos.globalToDimChunk();

                // if they're the same, just mark the chunk as unsaved
                if (oldPos == newPos) {
                    markUnsaved(newChunk);
                    return;
                }

                Set<Entity> entities = oldChunk == null ? null : perChunk.get(oldChunk);
                if (entities != null) {
                    entities.remove(ent);

                    if (entities.isEmpty()) {
                        perChunk.remove(oldChunk);
                    }
                }

                Set<Entity> set = perChunk.computeIfAbsent(newChunk, (k) -> ConcurrentHashMap.newKeySet());
                set.add(ent);

                markUnsaved(newChunk);

            });
            
            if (ent instanceof Villager villager) {
                villager.registerOnTradeUpdate((pos) -> markUnsaved(pos.globalToDimChunk()));
            }
        }));
    }

    public void addPlayer(DataTypeProvider provider) {
        executor.execute(() -> attempt(() -> {
            PlayerEntity player = PlayerEntity.parse(provider);
            players.put(player.getUUID(), player);
        }));
    }

    public void updatePlayerAction(DataTypeProvider provider) {
        executor.execute(() -> attempt(() -> {
            byte actions = provider.readNext();
            int playerCnt = provider.readVarInt();


            for (int i = 0; i < playerCnt; i++) {
                UUID uuid = provider.readUUID();

                if ((actions & 0x01) > 0) {
                    // Only create a new PlayerEntity if we don't already have one - SpawnPlayer
                    // (AddPlayer packet) sets the initial position; PlayerInfoUpdate just registers
                    // the UUID/name and has no position, so overwriting an existing player here
                    // would discard their position and cause NPEs on later MoveEntityPos packets.
                    String name = provider.readString();
                    PlayerEntity existing = players.get(uuid);
                    if (existing != null) {
                        existing.setName(name);
                    } else {
                        players.computeIfAbsent(uuid, u -> new PlayerEntity(u, name));
                    }

                    int properties = provider.readVarInt();
                    for (int j = 0; j < properties; j++) {
                        provider.readString();
                        provider.readString();
                        boolean signed = provider.readBoolean();
                        if (signed) provider.readString();
                    }
                }

                if ((actions & 0x02) > 0) {
                    boolean signature = provider.readBoolean();
                    if (signature) {
                        provider.readUUID();
                        provider.readLong();
                        int encKeySz = provider.readVarInt();
                        provider.readByteArray(encKeySz);
                        int pubKeySz = provider.readVarInt();
                        provider.readByteArray(pubKeySz);
                    }
                }

                if ((actions & 0x04) > 0) {
                    provider.readVarInt();
                }

                if ((actions & 0x08) > 0) {
                    provider.readBoolean();
                }

                if ((actions & 0x10) > 0) {
                    provider.readVarInt();
                }

                if ((actions & 0x20) > 0) {
                    boolean displayName = provider.readBoolean();
                    if (displayName) {
                        provider.readChat();
                    }
                }

                // 1.21.2+ adds list order/priority (VarInt)
                if ((actions & 0x40) > 0) {
                    provider.readVarInt();
                }

                // 1.21.4+ adds hat visibility (Boolean)
                if ((actions & 0x80) > 0) {
                    provider.readBoolean();
                }
            }
        }));
    }

    private void markUnsaved(CoordinateDim2D coord) {
        Chunk chunk = worldManager.getChunk(coord);
        if (chunk != null) {
            worldManager.touchChunk(chunk);
        }
    }

    /**
     * Delete all tile entities for a chunk, only done when the chunk is also unloaded. Note that this only related to
     * tile entities sent in the update-tile-entity packets, ones sent with the chunk will only be stored in the chunk.
     * @param location the position of the chunk for which we can delete tile entities.
     */
    public void unloadChunk(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.remove(location);
        if (entities == null) { return; }

        for (Entity e : entities) {
            this.entities.remove(e.getId());
        }
    }


    public void addMetadata(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            Entity ent = entities.get(provider.readVarInt());

            if (ent != null) {
                try {
                    ent.parseMetadata(provider);
                    markUnsaved(ent.getChunkLocation());
                } finally {
                    ent.mergeDecodeCompleteness(provider.getCompleteness());
                }
            }
        }));
    }

    public void updatePositionRelative(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            IMovableEntity ent = getMovableEntity(provider.readVarInt());
            RelativeMovementReader.Delta delta = RelativeMovementReader.read(provider);

            if (ent != null) {
                ent.incrementPosition(delta.x(), delta.y(), delta.z());
            }
        }));
    }

    public void updatePositionAbsolute(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            IMovableEntity ent = getMovableEntity(provider.readVarInt());

            if (ent != null) {
                ent.readPosition(provider);
            }
        }));
    }

    public IMovableEntity getMovableEntity(int entId) {
        Entity tmpEnt = entities.get(entId);
        if (tmpEnt == null) return null;
        IMovableEntity ent = players.get(tmpEnt.uuid);
        if (ent != null) {
            return ent;
        }

        return tmpEnt;
    }

    public List<SpecificTag> getEntitiesNbt(CoordinateDim2D location) {
        Set<Entity> entities = perChunk.get(location);

        if (entities == null) {
            return Collections.emptyList();
        }

        return entities.stream().map(Entity::toNbt).collect(Collectors.toList());
    }

    public List<SpecificTag> getEntitiesNbt(BoundingBox box) {
        CoordinateDim2D minChunk = box.getMin().globalToChunk().addDimension(worldManager.getDimension());
        CoordinateDim2D maxChunk = box.getMax().globalToChunk().addDimension(worldManager.getDimension());
        Set<Entity> selected = new LinkedHashSet<>();

        for (int chunkX = minChunk.getX(); chunkX <= maxChunk.getX(); chunkX++) {
            for (int chunkZ = minChunk.getZ(); chunkZ <= maxChunk.getZ(); chunkZ++) {
                Set<Entity> chunkEntities = perChunk.get(new CoordinateDim2D(chunkX, chunkZ, worldManager.getDimension()));
                if (chunkEntities != null) {
                    selected.addAll(chunkEntities);
                }
            }
        }

        return selected.stream()
                .map(entity -> entity.toNbtIfInside(box))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void reset() {
        this.entities.clear();
        this.perChunk.clear();
        this.players.clear();
    }

    public void addEquipment(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            int id = provider.readVarInt();
            Entity ent = entities.get(id);

            if (ent != null) {
                try {
                    ent.addEquipment(provider);
                    markUnsaved(ent.getChunkLocation());
                } finally {
                    ent.mergeDecodeCompleteness(provider.getCompleteness());
                }
            }
        }));
    }

    /**
     * When destroyEntities is called, we don't remove the entities from the perChunk map. These will only be removed
     * when the chunk is unloaded. This way we won't accidentally delete entities that belong to an unsaved chunk.
     *
     * In schematic mode, we also keep entities in the entities map so that late metadata/equipment
     * updates still find them, and so entity IDs aren't reused for stale entries in perChunk.
     */
    public void destroyEntities(DataTypeProvider provider) {
        int count = provider.readVarInt();
        if (Config.isSchematicMode()) {
            // Still consume the packet data, but don't remove anything.
            while (count-- > 0) {
                provider.readVarInt();
            }
            return;
        }
        while (count-- > 0) {
            int id = provider.readVarInt();
            Entity removed = entities.remove(id);
            if (removed != null) {
                players.remove(removed.uuid);
            }
        }
    }

    public int countActiveEntities() {
        return this.entities.size();
    }
    public int countActivePlayers() {
        return this.players.size();
    }

    public Collection<core.interfaces.IPlayerEntity> getPlayerSet() {
        return (Collection<core.interfaces.IPlayerEntity>) (Collection) players.values();
    }

    public void addVillagerTrades(DataTypeProvider provider) {
        this.executor.execute(() -> attempt(() -> {
            worldManager.getVillagerManager().parseAndStoreVillagerTrade(provider);
        }));
    }
}
