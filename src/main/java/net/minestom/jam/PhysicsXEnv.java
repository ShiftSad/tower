package net.minestom.jam;

import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import physx.PxTopLevelFunctions;
import physx.common.*;
import physx.cooking.PxCookingParams;
import physx.extensions.PxRigidBodyExt;
import physx.geometry.PxBoxGeometry;
import physx.geometry.PxGeometry;
import physx.geometry.PxPlaneGeometry;
import physx.physics.*;
import physx.vehicle2.PxVehicleTopLevelFunctions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PhysicsXEnv {

    public static final Logger LOGGER = LoggerFactory.getLogger(PhysicsXEnv.class);
    public static final int numThreads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 2)); // Max 8, min 1, leave 2 for OS
    public static final int PX_PHYSICS_VERSION = PxTopLevelFunctions.getPHYSICS_VERSION();

    public static final PxFoundation foundation;
    public static final PxPhysics physics;
    public static final PxCookingParams cookingParams;
    public static final PxCudaContextManager cudaContextManager;

    public static final PxCpuDispatcher defaultDispatcher;
    public static final PxMaterial defaultMaterial;
    public static final PxFilterData defaultFilterData;

    private static final boolean CUDA_AVAILABLE;

    static class CustomErrorCallback extends PxErrorCallbackImpl {
        private final Map<PxErrorCodeEnum, String> codeNames = new HashMap<>() {{
            put(PxErrorCodeEnum.eDEBUG_INFO, "DEBUG_INFO");
            put(PxErrorCodeEnum.eDEBUG_WARNING, "DEBUG_WARNING");
            put(PxErrorCodeEnum.eINVALID_PARAMETER, "INVALID_PARAMETER");
            put(PxErrorCodeEnum.eINVALID_OPERATION, "INVALID_OPERATION");
            put(PxErrorCodeEnum.eOUT_OF_MEMORY, "OUT_OF_MEMORY");
            put(PxErrorCodeEnum.eINTERNAL_ERROR, "INTERNAL_ERROR");
            put(PxErrorCodeEnum.eABORT, "ABORT");
            put(PxErrorCodeEnum.ePERF_WARNING, "PERF_WARNING");
        }};

        @Override
        public void reportError(PxErrorCodeEnum code, String message, String file, int line) {
            String codeName = codeNames.getOrDefault(code, "code: " + code);
            LOGGER.warn("[{}] {} ({}:{})", codeName, message, file, line);
        }
    }

    static {
        // create PhysX foundation object
        PxDefaultAllocator allocator = new PxDefaultAllocator();
        PxErrorCallback errorCb = new CustomErrorCallback();
        foundation = PxTopLevelFunctions.CreateFoundation(PX_PHYSICS_VERSION, allocator, errorCb);

        // create PhysX main physics object
        PxTolerancesScale tolerances = new PxTolerancesScale();
        physics = PxTopLevelFunctions.CreatePhysics(PX_PHYSICS_VERSION, foundation, tolerances);
        defaultMaterial = physics.createMaterial(0.5f, 0.5f, 0.5f);
        defaultFilterData = new PxFilterData(0, 0, 0, 0);
        defaultFilterData.setWord0(1);          // collision group: 0 (i.e. 1 << 0)
        defaultFilterData.setWord1(0xffffffff); // collision mask: collide with everything
        defaultFilterData.setWord2(0);          // no additional collision flags
        defaultFilterData.setWord3(0);          // word3 is currently not used

        cookingParams = new PxCookingParams(tolerances);
        defaultDispatcher = PxTopLevelFunctions.DefaultCpuDispatcherCreate(numThreads); // Increased for better performance

        // Initialize CUDA context manager
        PxCudaContextManager tempCudaMgr = null;
        boolean cudaAvailable = false;

        try {
            // Check if CUDA is available on this platform
            if (isCudaSupportedPlatform()) {
                // Try to get suggested CUDA device
                int deviceOrdinal = PxCudaTopLevelFunctions.GetSuggestedCudaDeviceOrdinal(foundation);
                if (deviceOrdinal >= 0) {
                    try (MemoryStack mem = MemoryStack.stackPush()) {
                        PxCudaContextManagerDesc cudaDesc = PxCudaContextManagerDesc.createAt(mem, MemoryStack::nmalloc);
                        tempCudaMgr = PxCudaTopLevelFunctions.CreateCudaContextManager(foundation, cudaDesc);

                        if (tempCudaMgr != null && tempCudaMgr.contextIsValid()) {
                            cudaAvailable = true;
                            LOGGER.info("CUDA initialized successfully!");
                            LOGGER.info("CUDA device: {}", tempCudaMgr.getDeviceName());
                            LOGGER.info("CUDA memory: {} MB", String.format(Locale.ENGLISH, "%.2f", tempCudaMgr.getDeviceTotalMemBytes() / 1024.0 / 1024.0));
                        } else {
                            LOGGER.error("Failed to create valid CUDA context manager");
                            if (tempCudaMgr != null) {
                                tempCudaMgr.release();
                                tempCudaMgr = null;
                            }
                        }
                    }
                } else LOGGER.info("No suitable CUDA device found");
            } else LOGGER.info("CUDA is not supported on this platform");

        } catch (Exception | ExceptionInInitializerError e) {
            LOGGER.error("CUDA initialization failed: {}", e.getMessage());
            if (tempCudaMgr != null) {
                try {
                    tempCudaMgr.release();
                } catch (Exception ignored) {}
                tempCudaMgr = null;
            }
            cudaAvailable = false;
        }

        cudaContextManager = tempCudaMgr;
        CUDA_AVAILABLE = cudaAvailable;

        if (CUDA_AVAILABLE) LOGGER.info("PhysX initialized with CUDA acceleration");
        else LOGGER.info("PhysX initialized with CPU-only processing");

        PxTopLevelFunctions.InitExtensions(physics);
        PxVehicleTopLevelFunctions.InitVehicleExtension(foundation);
    }

    /**
     * Check if CUDA is supported on the current platform
     */
    private static boolean isCudaSupportedPlatform() {
        String osName = System.getProperty("os.name").toLowerCase();
        // CUDA is not supported on macOS
        return !osName.contains("mac");
    }

    static void init() {
        // empty, the actual init happens on class load in static block
    }

    /**
     * Check if CUDA is available and initialized
     */
    public static boolean isCudaAvailable() {
        return CUDA_AVAILABLE;
    }

    /**
     * Get the CUDA context manager (may be null if CUDA is not available)
     */
    public static PxCudaContextManager getCudaContextManager() {
        return cudaContextManager;
    }

    /**
     * Create an empty scene with optimal settings based on available hardware
     */
    public static PxScene createEmptyScene() {
        return createEmptyScene(CUDA_AVAILABLE);
    }

    /**
     * Create an empty scene with the option to force CPU or GPU mode
     */
    public static PxScene createEmptyScene(boolean useCuda) {
        try (MemoryStack mem = MemoryStack.stackPush()) {
            PxSceneDesc sceneDesc = PxSceneDesc.createAt(mem, MemoryStack::nmalloc, physics.getTolerancesScale());
            sceneDesc.setGravity(PxVec3.createAt(mem, MemoryStack::nmalloc, 0f, -9.81f, 0f));
            sceneDesc.setCpuDispatcher(defaultDispatcher);
            sceneDesc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());

            if (useCuda && CUDA_AVAILABLE && cudaContextManager != null) {
                // Configure for CUDA acceleration
                sceneDesc.setCudaContextManager(cudaContextManager);
                sceneDesc.setStaticStructure(PxPruningStructureTypeEnum.eDYNAMIC_AABB_TREE);
                sceneDesc.getFlags().raise(PxSceneFlagEnum.eENABLE_PCM);
                sceneDesc.getFlags().raise(PxSceneFlagEnum.eENABLE_GPU_DYNAMICS);
                sceneDesc.setBroadPhaseType(PxBroadPhaseTypeEnum.eGPU);
                sceneDesc.setSolverType(PxSolverTypeEnum.eTGS);
                LOGGER.info("Created CUDA-accelerated scene");
            } else {
                // Configure for CPU processing
                sceneDesc.setBroadPhaseType(PxBroadPhaseTypeEnum.eABP);
                sceneDesc.setSolverType(PxSolverTypeEnum.ePGS);
                LOGGER.info("Created CPU-based scene");
            }

            return physics.createScene(sceneDesc);
        }
    }

    public static PxRigidDynamic createDefaultBox(float posX, float posY, float posZ) {
        return createDefaultBox(posX, posY, posZ, defaultFilterData);
    }

    public static PxRigidDynamic createDefaultBox(float posX, float posY, float posZ, PxFilterData simFilterData) {
        try (MemoryStack mem = MemoryStack.stackPush()) {
            PxBoxGeometry box = PxBoxGeometry.createAt(mem, MemoryStack::nmalloc, 0.5f, 0.5f, 0.5f);
            PxTransform pose = PxTransform.createAt(mem, MemoryStack::nmalloc, PxIDENTITYEnum.PxIdentity);
            pose.setP(PxVec3.createAt(mem, MemoryStack::nmalloc, posX, posY, posZ));

            PxShape shape = physics.createShape(box, defaultMaterial, true);
            PxRigidDynamic body = physics.createRigidDynamic(pose);
            shape.setSimulationFilterData(simFilterData);
            body.attachShape(shape);
            shape.release();
            PxRigidBodyExt.setMassAndUpdateInertia(body, 1f);
            return body;
        }
    }

    public static PxRigidStatic createGroundPlane(float posY) {
        try (MemoryStack mem = MemoryStack.stackPush()) {
            PxPlaneGeometry plane = PxPlaneGeometry.createAt(mem, MemoryStack::nmalloc);
            PxShape shape = physics.createShape(plane, defaultMaterial, true);

            float r = 1f / (float) Math.sqrt(2f);
            PxQuat q = PxQuat.createAt(mem, MemoryStack::nmalloc, 0f, 0f, r, r);
            PxVec3 p = PxVec3.createAt(mem, MemoryStack::nmalloc, 0f, 0f, 0f);
            shape.setLocalPose(PxTransform.createAt(mem, MemoryStack::nmalloc, p, q));
            shape.setSimulationFilterData(defaultFilterData);
            return createStaticBody(shape, 0f, posY, 0f);
        }
    }

    public static PxRigidStatic createStaticBody(PxGeometry fromGeometry, float posX, float posY, float posZ) {
        PxShape shape = physics.createShape(fromGeometry, defaultMaterial, true);
        shape.setSimulationFilterData(defaultFilterData);
        return createStaticBody(shape, posX, posY, posZ);
    }

    public static PxRigidStatic createStaticBody(PxShape fromShape, float posX, float posY, float posZ) {
        try (MemoryStack mem = MemoryStack.stackPush()) {
            PxTransform pose = PxTransform.createAt(mem, MemoryStack::nmalloc, PxIDENTITYEnum.PxIdentity);
            pose.setP(PxVec3.createAt(mem, MemoryStack::nmalloc, posX, posY, posZ));

            PxRigidStatic body = physics.createRigidStatic(pose);
            body.attachShape(fromShape);
            return body;
        }
    }

    public static void simulateScene(PxScene scene, float duration, PxRigidActor printActor) {
        float step = 1/60f;
        float t = 0;
        for (int i = 0; i < duration / step; i++) {
            // print position of printActor 2 times per simulated sec
            if (printActor != null && i % 30 == 0) {
                PxVec3 pos = printActor.getGlobalPose().getP();
                LOGGER.info("t = {} s, pos({}, {}, {})", String.format(Locale.ENGLISH, "%.2f", t),
                        String.format(Locale.ENGLISH, "%6.3f", pos.getX()),
                        String.format(Locale.ENGLISH, "%6.3f", pos.getY()),
                        String.format(Locale.ENGLISH, "%6.3f", pos.getZ()));
            }
            scene.simulate(step);
            scene.fetchResults(true);
            t += step;
        }
    }

    /**
     * Cleanup method to properly release CUDA resources
     */
    public static void cleanup() {
        if (cudaContextManager != null) {
            try {
                cudaContextManager.release();
                LOGGER.info("CUDA context manager released");
            } catch (Exception e) {
                LOGGER.error("Error releasing CUDA context manager: {}", e.getMessage());
            }
        }
    }
}