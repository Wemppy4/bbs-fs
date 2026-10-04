package mchorse.bbs_mod.utils.iris;

import mchorse.bbs_mod.client.BBSRendering;
import org.lwjgl.opengl.GL11;

/** Keep OptiFine's world-to-light camera and world-space light direction in agreement. */
public final class OptiFineSunRotation
{
    private static long cameraUpdates,lightUpdates;
    public static long getCameraUpdates(){return cameraUpdates;}
    public static long getLightUpdates(){return lightUpdates;}
    private OptiFineSunRotation() {}

    /** Called after the native day/night light rotations and before camera-grid snapping. */
    public static void rotateShadowCamera()
    {
        float yaw=BBSRendering.getSunHorizontalRotation();
        cameraUpdates++;
        if(yaw!=0F)GL11.glRotatef(-yaw,0F,1F,0F);
    }

    /** The native method rebuilds this vector every frame, so the adjustment never accumulates. */
    public static void rotateShadowLight(float[] direction)
    { lightUpdates++;rotate(direction,BBSRendering.getSunHorizontalRotation()); }

    public static void rotate(float[] direction,float degrees)
    {
        if(degrees==0F)return;
        double angle=Math.toRadians(degrees),c=Math.cos(angle),s=Math.sin(angle);
        float x=direction[0],z=direction[2];
        direction[0]=(float)(c*x+s*z);direction[2]=(float)(c*z-s*x);
    }
}
