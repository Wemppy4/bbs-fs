package mchorse.bbs_mod.forge;

import net.minecraft.world.World;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** 1.12 uses one block ray trace for picking and third-person camera clipping.
 * Scope only the camera's calls so a non-solid model can still block its camera. */
public final class ModelCameraHooks
{
    private static final ThreadLocal<Boolean> CAMERA = ThreadLocal.withInitial(() -> false);
    private ModelCameraHooks() {}
    public static boolean isCameraTrace() { return CAMERA.get(); }
    public static RayTraceResult trace(World world, Vec3d start, Vec3d end)
    {
        boolean previous = CAMERA.get();
        CAMERA.set(true);
        try { return world.rayTraceBlocks(start, end); }
        finally { CAMERA.set(previous); }
    }
}
