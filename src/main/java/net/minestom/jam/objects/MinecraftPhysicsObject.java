package net.minestom.jam.objects;

import net.minestom.jam.MinecraftPhysics;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.physics.PxActor;
import physx.physics.PxRigidDynamic;

import java.util.ArrayList;
import java.util.List;

import static net.minestom.jam.CoordinateUtils.*;

public abstract class MinecraftPhysicsObject {

    private final List<PxActor> relatedObjects = new ArrayList<>();

    private final @NotNull MinecraftPhysics mcPhysics;
    private final @NotNull PxRigidDynamic collisionObject;
    private final @NotNull Vec size;
    private @Nullable Entity entity;
    private boolean alwaysActive = false;

    public MinecraftPhysicsObject(@NotNull MinecraftPhysics mcPhysics, @NotNull PxRigidDynamic collisionObject, @NotNull Vec size) {
        this.mcPhysics = mcPhysics;
        this.collisionObject = collisionObject;
        this.size = size;

        mcPhysics.addObject(this);
    }

    public Entity setInstance() {
        this.entity = createEntity();
        if (this.entity != null) {
            PxTransform transform = collisionObject.getGlobalPose();
            PxVec3 position = transform.getP();
            this.entity.setInstance(mcPhysics.getInstance(), toVec(position));
        }
        return this.entity;
    }

    public void addRelated(PxActor related) {
        this.relatedObjects.add(related);
        mcPhysics.getPhysicsSpace().addActor(related);
    }

    public void destroy() {
        for (PxActor relatedObject : relatedObjects) {
            mcPhysics.getPhysicsSpace().removeActor(relatedObject);
            relatedObject.release();
        }
        relatedObjects.clear();

        mcPhysics.removeObject(this);
        collisionObject.release();

        // Remove the entity
        if (entity != null) {
            entity.remove();
        }
    }

    public @NotNull PxRigidDynamic getCollisionObject() {
        return collisionObject;
    }

    public void setAlwaysActive(boolean alwaysActive) {
        this.alwaysActive = alwaysActive;
        if (alwaysActive) {
            collisionObject.setSleepThreshold(0.0f);
            collisionObject.wakeUp();
        }
    }

    public abstract @Nullable Entity createEntity();

    public @Nullable Entity getEntity() {
        return entity;
    }

    public @NotNull Vec getSize() {
        return size;
    }

    public void update() {
        if (entity == null) {
            System.out.println("entity é null");
            return;
        }
        if (!entity.isActive()) {
            System.out.println("entity não está ativa");
            return;
        }

        if (alwaysActive) {
            collisionObject.wakeUp();
        }

        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> {
            PxTransform transform = collisionObject.getGlobalPose();
            PxVec3 position = transform.getP();

            meta.setTransformationInterpolationDuration(1);
            meta.setPosRotInterpolationDuration(1);
            meta.setTransformationInterpolationStartDelta(0);

            // Convert PhysX position to Minestom position
            entity.teleport(toPos(position));

            // Convert PhysX rotation to Minestom rotation
            meta.setLeftRotation(toFloats(transform.getQ()));
        });
    }

    /**
     * Set the position of the physics object
     */
    public void setPosition(Vec position) {
        PxTransform currentTransform = collisionObject.getGlobalPose();
        PxVec3 newPos = fromVec(position);
        PxTransform newTransform = new PxTransform(newPos, currentTransform.getQ());
        collisionObject.setGlobalPose(newTransform);
    }

    /**
     * Get the current position of the physics object
     */
    public Vec getPosition() {
        PxVec3 pos = collisionObject.getGlobalPose().getP();
        return toVec(pos);
    }

    /**
     * Set the linear velocity of the physics object
     */
    public void setLinearVelocity(Vec velocity) {
        collisionObject.setLinearVelocity(fromVec(velocity));
    }

    /**
     * Get the current linear velocity
     */
    public Vec getLinearVelocity() {
        return toVec(collisionObject.getLinearVelocity());
    }

    /**
     * Apply a force to the physics object
     */
    public void addForce(Vec force) {
        collisionObject.addForce(fromVec(force));
    }

    /**
     * Apply an impulse to the physics object
     */
    public void addImpulse(Vec impulse) {
        float mass = collisionObject.getMass();
        if (mass > 0) {
            Vec velocityChange = impulse.div(mass);
            Vec currentVelocity = getLinearVelocity();
            setLinearVelocity(currentVelocity.add(velocityChange));
        }
    }
}