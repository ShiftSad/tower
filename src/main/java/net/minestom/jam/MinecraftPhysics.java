package net.minestom.jam;

import net.minestom.jam.objects.MinecraftPhysicsObject;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import physx.common.PxVec3;
import physx.physics.PxActor;
import physx.physics.PxRigidDynamic;
import physx.physics.PxRigidStatic;
import physx.physics.PxScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MinecraftPhysics {

    private PxScene physicsSpace;
    private PxRigidStatic floor;

    private static final int KILL_HEIGHT = -10;

    private final @NotNull List<MinecraftPhysicsObject> objects = new CopyOnWriteArrayList<>();
    private final @NotNull Map<PxRigidDynamic, MinecraftPhysicsObject> objectMap = new ConcurrentHashMap<>();
    private final @NotNull Map<PxActor, MinecraftPhysicsObject> actorMap = new ConcurrentHashMap<>();
    private final Instance instance;

    public MinecraftPhysics(Instance instance) {
        this.instance = instance;
        physicsSpace = PhysicsXEnv.createEmptyScene();

        floor = PhysicsXEnv.createGroundPlane(0);
        physicsSpace.addActor(floor);

        // Default: -9.81f
        // Minecraft: -31.36f
        physicsSpace.setGravity(new PxVec3(0, -17f, 0));
    }

    public void update(float delta) {
        if (physicsSpace == null) {
            System.out.println("PhysicsSpace is null!");
            return;
        }

        physicsSpace.simulate(delta);
        physicsSpace.fetchResults(true);

        for (MinecraftPhysicsObject object : objects) {
            object.update();

            object.getCollisionObject();
            PxVec3 pos = object.getCollisionObject().getGlobalPose().getP();
            if (pos.getY() < KILL_HEIGHT) {
                // Object fell too low
                object.destroy();
            }

        }
    }

    public @NotNull List<MinecraftPhysicsObject> getObjects() {
        return objects;
    }

    public void addObject(MinecraftPhysicsObject object) {
        objects.add(object);

        PxRigidDynamic rigidDynamic = object.getCollisionObject();
        objectMap.put(rigidDynamic, object);
        actorMap.put(rigidDynamic, object);

        // Add the actor to the physics scene
        physicsSpace.addActor(rigidDynamic);

        PhysicsXEnv.LOGGER.debug("Added physics object to scene: {}", object);
    }

    public void removeObject(MinecraftPhysicsObject object) {
        objects.remove(object);

        PxRigidDynamic rigidDynamic = object.getCollisionObject();
        if (rigidDynamic != null) {
            objectMap.remove(rigidDynamic);
            actorMap.remove(rigidDynamic);

            // Remove the actor from the physics scene
            physicsSpace.removeActor(rigidDynamic);

            PhysicsXEnv.LOGGER.debug("Removed physics object from scene: {}", object);
        }
    }

    public @Nullable MinecraftPhysicsObject getObjectByRigidDynamic(PxRigidDynamic rigidDynamic) {
        return objectMap.get(rigidDynamic);
    }

    public @Nullable MinecraftPhysicsObject getObjectByActor(PxActor actor) {
        return actorMap.get(actor);
    }

    public int getObjectCount() {
        return objects.size();
    }

    public void clearAllObjects() {
        List<MinecraftPhysicsObject> objectsCopy = new ArrayList<>(objects);
        for (MinecraftPhysicsObject object : objectsCopy) {
            object.destroy();
        }

        PhysicsXEnv.LOGGER.info("Cleared all physics objects from simulation");
    }

    public void setGravity(float x, float y, float z) {
        if (physicsSpace != null) {
            physicsSpace.setGravity(new PxVec3(x, y, z));
            PhysicsXEnv.LOGGER.debug("Gravity set to ({}, {}, {})", x, y, z);
        }
    }

    public PxVec3 getGravity() {
        return physicsSpace != null ? physicsSpace.getGravity() : new PxVec3(0, -17f, 0);
    }

    private boolean paused = false;

    public void setPaused(boolean paused) {
        this.paused = paused;
        PhysicsXEnv.LOGGER.debug("Physics simulation {}", paused ? "paused" : "resumed");
    }

    public boolean isPaused() {
        return paused;
    }

    public Instance getInstance() {
        return instance;
    }

    public PxScene getPhysicsSpace() {
        return physicsSpace;
    }

    public PxRigidStatic getFloor() {
        return floor;
    }

    public void cleanup() {
        if (physicsSpace != null) {
            // Clear all objects first
            clearAllObjects();

            // Remove and release the floor
            if (floor != null) {
                physicsSpace.removeActor(floor);
                floor.release();
            }

            // Release the scene
            physicsSpace.release();
            physicsSpace = null;

            PhysicsXEnv.LOGGER.info("MinecraftPhysics cleaned up successfully");
        }
    }
}