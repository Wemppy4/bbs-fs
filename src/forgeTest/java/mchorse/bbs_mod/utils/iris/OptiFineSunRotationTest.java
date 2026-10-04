package mchorse.bbs_mod.utils.iris;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptiFineSunRotationTest
{
    @Test public void horizontalDirectionAndInverseLightCameraAgree()
    {
        float[] direction={1F,2F,3F,0F};
        OptiFineSunRotation.rotate(direction,90F);
        assertArrayEquals(new float[]{3F,2F,-1F,0F},direction,.00001F);
        Vector3f recovered=new Matrix4f().rotateY((float)Math.toRadians(-90F)).transformDirection(new Vector3f(direction[0],direction[1],direction[2]));
        assertEquals(1F,recovered.x,.00001F);assertEquals(2F,recovered.y,.00001F);assertEquals(3F,recovered.z,.00001F);
        OptiFineSunRotation.rotate(direction,-90F);
        assertArrayEquals(new float[]{1F,2F,3F,0F},direction,.00001F);
    }
}
