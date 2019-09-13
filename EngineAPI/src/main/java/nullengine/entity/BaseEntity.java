package nullengine.entity;

import nullengine.component.Component;
import nullengine.component.ComponentContainer;
import nullengine.world.World;
import org.joml.AABBd;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class BaseEntity implements Entity {

    private final ComponentContainer components;

    protected final EntityProvider provider;

    private UUID uniqueId;
    private int id;
    private WeakReference<World> world;
    private Vector3d position = new Vector3d();
    private Vector3f rotation = new Vector3f();
    private Vector3f motion = new Vector3f();
    private AABBd boundingBox;
    private boolean destroyed = false;

    public BaseEntity(int id, World world, double x, double y, double z) {
        this.provider = null; // TODO:
        this.uniqueId = UUID.randomUUID();
        this.id = id;
        this.world = new WeakReference<>(world);
        this.components = new ComponentContainer();
        this.position.set(x, y, z);
    }

    @Nonnull
    @Override
    public EntityProvider getProvider() {
        return null;
    }

    @Nonnull
    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Nonnull
    @Override
    public World getWorld() {
        return world.get();
    }

    @Override
    public Vector3d getPosition() {
        return position;
    }

    @Override
    public AABBd getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(AABBd boundingBox) {
        this.boundingBox = boundingBox;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void tick() {
    }

    @Override
    public Vector3f getRotation() {
        return rotation;
    }

    @Override
    public synchronized final void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        doDestroy();
        getWorld().getEntityManager().destroyEntity(this);
    }

    protected void doDestroy() {
    }

    @Override
    public final boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public Vector3f getMotion() {
        return motion;
    }

    @Nonnull
    @Override
    public <T extends Component> Optional<T> getComponent(@Nonnull Class<T> type) {
        return components.getComponent(type);
    }

    @Override
    public <T extends Component> boolean hasComponent(@Nonnull Class<T> type) {
        return components.hasComponent(type);
    }

    @Override
    public <T extends Component> void setComponent(@Nonnull Class<T> type, @Nullable T value) {
        components.setComponent(type, value);
    }

    @Override
    public <T extends Component> void removeComponent(@Nonnull Class<T> type) {
        components.removeComponent(type);
    }

    @Nonnull
    @Override
    public Set<Class<?>> getComponents() {
        return components.getComponents();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return uniqueId.equals(that.uniqueId);
    }

    @Override
    public int hashCode() {
        return uniqueId.hashCode();
    }
}
