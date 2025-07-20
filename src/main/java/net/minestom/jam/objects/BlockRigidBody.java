package net.minestom.jam.objects;

import net.kyori.adventure.key.Key;
import net.minestom.jam.MinecraftPhysics;
import net.minestom.jam.PhysicsXEnv;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryStack;
import physx.common.PxIDENTITYEnum;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.extensions.PxRigidBodyExt;
import physx.geometry.PxBoxGeometry;
import physx.physics.PxRigidDynamic;
import physx.physics.PxShape;
import physx.support.PxArray_PxShapePtr;

public class BlockRigidBody extends MinecraftPhysicsObject {

    private final Block block;
    private final boolean visible;
    private Vec currentScale;

    public BlockRigidBody(@NotNull MinecraftPhysics mcPhysics, Vec position, Vec size, float mass, boolean visible, Block block) {
        super(mcPhysics, createRigidDynamic(position, size, mass), size);

        this.block = block;
        this.visible = visible;
        this.currentScale = size;

        // Set damping for more realistic physics
        PxRigidDynamic rigidBody = getCollisionObject();
        rigidBody.setAngularDamping(0.1f);
        rigidBody.setLinearDamping(0.3f);

        PhysicsXEnv.LOGGER.debug("Created BlockRigidBody for block {} at position {} with size {} and mass {}",
                block, position, size, mass);
    }

    private static PxRigidDynamic createRigidDynamic(Vec position, Vec size, float mass) {
        try (MemoryStack mem = MemoryStack.stackPush()) {
            PxBoxGeometry boxGeometry = PxBoxGeometry.createAt(mem, MemoryStack::nmalloc,
                    (float) (size.x() * 0.5),
                    (float) (size.y() * 0.5),
                    (float) (size.z() * 0.5));

            PxTransform pose = PxTransform.createAt(mem, MemoryStack::nmalloc, PxIDENTITYEnum.PxIdentity);
            pose.setP(PxVec3.createAt(mem, MemoryStack::nmalloc,
                    (float) position.x(),
                    (float) position.y(),
                    (float) position.z()));

            PxShape shape = PhysicsXEnv.physics.createShape(boxGeometry, PhysicsXEnv.defaultMaterial, true);
            PxRigidDynamic rigidDynamic = PhysicsXEnv.physics.createRigidDynamic(pose);
            shape.setSimulationFilterData(PhysicsXEnv.defaultFilterData);
            rigidDynamic.attachShape(shape);
            shape.release();

            if (mass > 0) PxRigidBodyExt.setMassAndUpdateInertia(rigidDynamic, mass);
            else rigidDynamic.setRigidBodyFlag(physx.physics.PxRigidBodyFlagEnum.eKINEMATIC, true);

            return rigidDynamic;
        }
    }

    public void setScale(float scale) {
        setScale(new Vec(scale, scale, scale));
    }

    public void setScale(Vec scale) {
        this.currentScale = scale;

        updatePhysicsShape(scale);

        if (getEntity() != null && visible) {
            getEntity().editEntityMeta(ItemDisplayMeta.class, meta -> {
                meta.setWidth((float) (scale.x()));
                meta.setHeight((float) (scale.y()));
                meta.setScale(scale); // Scale up for visual representation
            });
        }

        PhysicsXEnv.LOGGER.debug("Updated scale for BlockRigidBody to {}", scale);
    }

    private void updatePhysicsShape(Vec scale) {
        PxRigidDynamic rigidBody = getCollisionObject();

        try (MemoryStack mem = MemoryStack.stackPush()) {
            float currentMass = rigidBody.getMass();

            int numShapes = rigidBody.getNbShapes();
            if (numShapes > 0) {
                PxArray_PxShapePtr shapePtrs = PxArray_PxShapePtr.createAt(mem, MemoryStack::nmalloc, numShapes);
                int retrievedShapes = rigidBody.getShapes(shapePtrs.begin(), numShapes, 0);

                for (int i = 0; i < retrievedShapes; i++) {
                    PxShape shape = shapePtrs.get(i);
                    if (shape != null) {
                        rigidBody.detachShape(shape);
                        shape.release();
                    }
                }
            }

            PxBoxGeometry newBoxGeometry = PxBoxGeometry.createAt(mem, MemoryStack::nmalloc,
                    (float) (scale.x() * 0.5),
                    (float) (scale.y() * 0.5),
                    (float) (scale.z() * 0.5));

            PxShape newShape = PhysicsXEnv.physics.createShape(newBoxGeometry, PhysicsXEnv.defaultMaterial, true);
            newShape.setSimulationFilterData(PhysicsXEnv.defaultFilterData);

            rigidBody.attachShape(newShape);
            newShape.release();

            if (currentMass > 0) {
                PxRigidBodyExt.setMassAndUpdateInertia(rigidBody, currentMass);
            }
        }
    }

    public Vec getCurrentScale() {
        return currentScale;
    }

    public Block getBlock() {
        return block;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Set the material properties of this block
     */
    public void setMaterial(float staticFriction, float dynamicFriction, float restitution) {
        PxRigidDynamic rigidBody = getCollisionObject();

        try (MemoryStack mem = MemoryStack.stackPush()) {
            var customMaterial = PhysicsXEnv.physics.createMaterial(staticFriction, dynamicFriction, restitution);
            int numShapes = rigidBody.getNbShapes();
            if (numShapes > 0) {
                PxArray_PxShapePtr shapePtrs = PxArray_PxShapePtr.createAt(mem, MemoryStack::nmalloc, numShapes);

                int retrievedShapes = rigidBody.getShapes(shapePtrs.begin(), numShapes, 0);

                for (int i = 0; i < retrievedShapes; i++) {
                    PxShape shape = shapePtrs.get(i);
                    if (shape != null) {
                        PhysicsXEnv.LOGGER.debug("Updated material properties for BlockRigidBody shape {}", i);
                    }
                }
            }

             customMaterial.release();
        } catch (Exception e) {
            PhysicsXEnv.LOGGER.error("Failed to update material properties: {}", e.getMessage());
        }
    }

    /**
     * Apply an upward impulse to make the block "jump"
     */
    public void jump(float force) {
        addImpulse(new Vec(0, force, 0));
    }

    /**
     * Make the block "bounce" by applying a random impulse
     */
    public void bounce() {
        double randomX = (Math.random() - 0.5) * 2; // -1 to 1
        double randomZ = (Math.random() - 0.5) * 2; // -1 to 1
        double upwardForce = Math.random() * 5 + 2; // 2 to 7

        addImpulse(new Vec(randomX, upwardForce, randomZ));
    }

    @Override
    public Entity createEntity() {
        if (!visible) return null;

        // Uses an ITEM_DISPLAY instead of a BLOCK_DISPLAY as it is centered around the middle instead of the corner
        // although causes issues with certain items, it works for most
        Entity entity = new Entity(EntityType.ITEM_DISPLAY);
        entity.setNoGravity(true);

        entity.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setWidth((float) (currentScale.x()));
            meta.setHeight((float) (currentScale.y()));

            // Try to get material from block, fallback to diamond block
            Material material = getMaterialFromBlock(block);
            meta.setItemStack(ItemStack.of(material));
            meta.setScale(getSize());
        });

        PhysicsXEnv.LOGGER.debug("Created visual entity for BlockRigidBody with block {}", block);
        return entity;
    }

    /**
     * Convert a Minestom Block to appropriate Material for ItemStack
     */
    private Material getMaterialFromBlock(Block block) {
        try {
            Material material = Material.fromKey(block.key());
            if (material == null) material = Material.DIAMOND_BLOCK;
            return material;
        } catch (Exception e) {
            PhysicsXEnv.LOGGER.warn("Failed to convert block {} to material, using diamond block", block, e);
            return Material.DIAMOND_BLOCK;
        }
    }

    @Override
    public String toString() {
        return String.format("BlockRigidBody{block=%s, position=%s, scale=%s, visible=%s}",
                block, getPosition(), currentScale, visible);
    }
}