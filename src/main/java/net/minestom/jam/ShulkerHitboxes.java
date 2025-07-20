package net.minestom.jam;

import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import net.minestom.jam.objects.MinecraftPhysicsObject;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.Direction;

import java.util.*;

public class ShulkerHitboxes {

    /**
     * Generates and spawns shulker entities for a given physics object.
     * This method is stateless and returns the list of spawned entities for the caller to manage.
     *
     * @param minecraftPhysics The physics world information.
     * @param mcObject         The physics object to build hitboxes for.
     * @return A list of the spawned entities (holders and shulkers).
     */
    public static List<Entity> generateAndSpawnShulkers(MinecraftPhysics minecraftPhysics, MinecraftPhysicsObject mcObject) {
        final PhysicsCollisionObject object = mcObject.getCollisionObject();
        final Instance instance = minecraftPhysics.getInstance();
        final List<Entity> spawnedEntities = new ArrayList<>();

        final Vector3f location = object.getPhysicsLocation(new Vector3f());
        final Quaternion quaternion = object.getPhysicsRotation(new Quaternion());
        final BoundingBox bb = object.getCollisionShape().boundingBox(location, quaternion, new BoundingBox());

        // This small body is used to test for collision points
        final var testObj = new PhysicsRigidBody(new BoxCollisionShape(0.01f), 1f);

        final float resolution = 0.3f;
        final PointIterator pointIterator = new PointIterator(bb, resolution);
        final List<IntVec> scaledPoints = new ArrayList<>();

        // First, find all valid, discrete points inside the physics object's bounds
        while (pointIterator.hasNext()) {
            var point = pointIterator.next();
            testObj.setPhysicsLocation(new Vector3f((float) point.x(), (float) point.y(), (float) point.z()));

            // pairTest checks if the small test body is colliding with the main object
            if (minecraftPhysics.getPhysicsSpace().pairTest(object, testObj, null) > 0) {
                scaledPoints.add(new IntVec(
                        (int) Math.round(point.x() / resolution),
                        (int) Math.round(point.y() / resolution),
                        (int) Math.round(point.z() / resolution)
                ));
            }
        }

        // Then, create shulkers only on the "shell" of these points for a hollow effect
        for (IntVec point : scaledPoints) {
            if (!anyNeighborEmpty(point, scaledPoints)) continue;

            final Pos spawnPos = new Pos(
                    point.x() * resolution,
                    point.y() * resolution,
                    point.z() * resolution
            );

            // Create and spawn the entities that form the visible hitbox
            final var holder = new Entity(EntityType.TEXT_DISPLAY);
            holder.setNoGravity(true);

            final var shulker = new LivingEntity(EntityType.SHULKER);
            shulker.setNoGravity(true);
            shulker.setInvisible(false);
            shulker.getAttribute(Attribute.SCALE).setBaseValue(0.1f);

            spawnedEntities.add(holder);
            spawnedEntities.add(shulker);

            // Asynchronously spawn the holder, then the shulker, then set the passenger
            holder.setInstance(instance, spawnPos).thenRun(() ->
                    shulker.setInstance(instance, spawnPos).thenRun(() ->
                            holder.addPassenger(shulker)
                    )
            );
        }
        return spawnedEntities;
    }

    /**
     * Checks if any adjacent position in the grid of points is empty.
     * Used to create a hollow shell of shulkers instead of a solid block.
     */
    public static boolean anyNeighborEmpty(IntVec pos, List<IntVec> positions) {
        for (Direction direction : Direction.values()) {
            if (!positions.contains(pos.add(direction.normalX(), direction.normalY(), direction.normalZ()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * A record representing a 3D integer vector, used for discrete point representation.
     */
    public record IntVec(int x, int y, int z) {
        public IntVec add(int x, int y, int z) {
            return new IntVec(this.x + x, this.y + y, this.z + z);
        }
    }

    /**
     * An iterator that provides points within a given JME BoundingBox.
     */
    public static class PointIterator implements Iterator<net.minestom.server.collision.BoundingBox.MutablePoint> {
        private final float resolution;
        private final float minX, minY, minZ, maxX, maxY, maxZ;
        private float x, y, z;
        private final net.minestom.server.collision.BoundingBox.MutablePoint point = new net.minestom.server.collision.BoundingBox.MutablePoint();

        public PointIterator(BoundingBox boundingBox, float resolution) {
            this.resolution = resolution;

            Vector3f min = boundingBox.getMin(new Vector3f());
            Vector3f max = boundingBox.getMax(new Vector3f());

            this.minX = min.x;
            this.minY = min.y;
            this.minZ = min.z;
            this.maxX = max.x;
            this.maxY = max.y;
            this.maxZ = max.z;

            this.x = this.minX;
            this.y = this.minY;
            this.z = this.minZ;
        }

        @Override
        public boolean hasNext() {
            return z <= maxZ;
        }

        @Override
        public net.minestom.server.collision.BoundingBox.MutablePoint next() {
            point.set(x, y, z);

            x += resolution;
            if (x > maxX) {
                x = minX;
                y += resolution;
                if (y > maxY) {
                    y = minY;
                    z += resolution;
                }
            }
            return point;
        }
    }
}