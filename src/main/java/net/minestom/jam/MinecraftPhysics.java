package net.minestom.jam;

import com.jme3.bullet.NativePhysicsObject;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import net.minestom.jam.objects.MinecraftPhysicsObject;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MinecraftPhysics {

    private PhysicsSpace physicsSpace;
    private PhysicsRigidBody floor;

    public static final Tag<MinecraftPhysicsObject> PHYSICS_BLOCK_TAG = Tag.Transient("physicsblock");
    public static final Tag<PhysicsRigidBody> PLAYER_RIGID_BODY_TAG = Tag.Transient("playerrigidbody");

    private static final int KILL_HEIGHT = -10;

    private final @NotNull List<MinecraftPhysicsObject> objects = new CopyOnWriteArrayList<>();
    private final @NotNull Map<NativePhysicsObject, MinecraftPhysicsObject> objectMap = new ConcurrentHashMap<>();
    private final Instance instance;

    public MinecraftPhysics(Instance instance) {
        this.instance = instance;
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);

        // Default: -9.81f
        // Minecraft: -31.36f
        physicsSpace.setGravity(new Vector3f(0, -17f, 0));
    }

    public void update(float delta) {
        if (physicsSpace == null) {
            System.out.println("PhysicsSpace is null!");
            return;
        }

        physicsSpace.update(delta);

        for (MinecraftPhysicsObject object : objects) {
            object.update();

            // If bellow kill height, destroy the object
//            if (object.getCollisionObject().getPhysicsLocation(null).y < KILL_HEIGHT) {
//                object.destroy();
//                removeObject(object);
//            }
        }
    }

    public @NotNull List<MinecraftPhysicsObject> getObjects() {
        return objects;
    }

    public void addObject(MinecraftPhysicsObject object) {
        objects.add(object);
        objectMap.put(object.getCollisionObject(), object);
    }

    public void removeObject(MinecraftPhysicsObject object) {
        objects.remove(object);
        objectMap.remove(object.getCollisionObject());
    }

    public @Nullable MinecraftPhysicsObject getObjectByPhysicsObject(NativePhysicsObject physicsObject) {
        return objectMap.get(physicsObject);
    }

    public Instance getInstance() {
        return instance;
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }
}