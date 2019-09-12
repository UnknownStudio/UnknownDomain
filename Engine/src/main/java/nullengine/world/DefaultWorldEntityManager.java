package nullengine.world;

import nullengine.entity.Entity;
import nullengine.entity.EntityProvider;
import nullengine.event.entity.EntityCreateEvent;
import nullengine.event.entity.EntitySpawnEvent;
import nullengine.logic.Tickable;
import nullengine.registry.Registries;
import org.apache.commons.lang3.Validate;
import org.joml.Vector3dc;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultWorldEntityManager implements WorldEntityManager, Tickable {

    private final World world;

    private final AtomicInteger nextId = new AtomicInteger(0);

    private final Set<Entity> entities = new HashSet<>();
    private final Collection<Entity> unmodifiableEntities = Collections.unmodifiableSet(entities);

    public DefaultWorldEntityManager(World world) {
        this.world = world;
    }

    @Override
    public World getWorld() {
        return world;
    }

    @Override
    public <T extends Entity> T spawnEntity(Class<T> entityType, double x, double y, double z) {
        var provider = Registries.getEntityRegistry().getValue(entityType);
        return (T) spawnEntity(provider, x, y, z);
    }

    @Override
    public <T extends Entity> T spawnEntity(Class<T> entityType, Vector3dc position) {
        return spawnEntity(entityType, position.x(), position.y(), position.z());
    }

    @Override
    public Entity spawnEntity(String providerName, double x, double y, double z) {
        var provider = Registries.getEntityRegistry().getValue(providerName);
        return spawnEntity(provider, x, y, z);
    }

    @Override
    public Entity spawnEntity(String providerName, Vector3dc position) {
        return spawnEntity(providerName, position.x(), position.y(), position.z());
    }

    private Entity spawnEntity(EntityProvider provider, double x, double y, double z) {
        Validate.notNull(provider, "Entity provider is not found");
        var entity = provider.createEntity(nextId.getAndIncrement(), world, x, y, z);
        world.getGame().getEventBus().post(new EntityCreateEvent(entity));
        spawnEntity(entity);
        return entity;
    }

    private void spawnEntity(Entity entity) {
        if (entities.contains(entity)) {
            return;
        }

        var event = new EntitySpawnEvent.Pre(entity);
        if (world.getGame().getEventBus().post(event)) {
            return;
        }
        entities.add(entity);
        world.getGame().getEventBus().post(new EntitySpawnEvent.Post(entity));
    }


    @Override
    public Collection<Entity> getEntities() {
        return unmodifiableEntities;
    }

    @Override
    public void tick() {
        entities.forEach(Tickable::tick);
    }
}