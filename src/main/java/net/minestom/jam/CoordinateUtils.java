package net.minestom.jam;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import physx.common.PxQuat;
import physx.common.PxVec3;

public class CoordinateUtils {

    public static Vec toVec(PxVec3 pxVec) {
        return new Vec(pxVec.getX(), pxVec.getY(), pxVec.getZ());
    }

    public static PxVec3 fromVec(Vec vec) {
        return new PxVec3((float) vec.x(), (float) vec.y(), (float) vec.z());
    }

    public static Pos toPos(PxVec3 pxVec) {
        return new Pos(pxVec.getX(), pxVec.getY(), pxVec.getZ());
    }

    public static PxVec3 fromPos(Pos pos) {
        return new PxVec3((float) pos.x(), (float) pos.y(), (float) pos.z());
    }

    public static float[] toFloats(PxQuat quat) {
        return new float[]{quat.getX(), quat.getY(), quat.getZ(), quat.getW()};
    }
}