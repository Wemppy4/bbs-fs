package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.resources.Link;
import org.junit.Test;
import static org.junit.Assert.*;

public class OptiFinePbrTest
{
    @Test public void originalLabPbrChannelsArePreserved()
    {
        Pixels pixels=OptiFinePbr.specular(new float[]{.5F,1F,.5F,.5F,0});
        try {assertArrayEquals(new int[]{128,255,160,127},rgba(pixels));}
        finally {pixels.delete();}
        pixels=OptiFinePbr.specular(new float[5]);
        try {assertArrayEquals(new int[]{0,10,0,255},rgba(pixels));}
        finally {pixels.delete();}
        assertEquals(Link.assets("textures/test_s.png"),OptiFinePbr.suffixed(Link.assets("textures/test.png"),"_s.png"));
    }
    @Test public void reliefProducesDirectionalNormalsAndHeight()
    {
        Pixels albedo=Pixels.fromSize(3,1);
        albedo.setColor(0,0,new Color(0,0,0,1));albedo.setColor(1,0,new Color(.5F,.5F,.5F,1));albedo.setColor(2,0,new Color(1,1,1,1));
        Pixels normal=OptiFinePbr.normal(albedo,1);
        try {Color center=normal.getColor(1,0);assertTrue(center.r<.1F);assertEquals(.5F,center.g,.005F);assertEquals(1F,center.b,0);assertEquals(.5F,center.a,.005F);}
        finally {albedo.delete();normal.delete();}
    }
    @Test public void tangentsTrackMirroredUvsAndActualThreeDimensionalTriangle()
    {
        float[] p={0,0,0,0,0,1,0,1,0},n={-1,0,0,-1,0,0,-1,0,0},uv={0,0,1,0,0,1};
        float[] tangents=ModelTangents.calculate(p,n,uv);
        for(int i=0;i<3;i++)assertArrayEquals(new float[]{0,0,1,-1},java.util.Arrays.copyOfRange(tangents,i*4,i*4+4),.00001F);
        tangents=ModelTangents.calculate(p,n,new float[]{0,0,-1,0,0,1});
        assertArrayEquals(new float[]{0,0,-1,1},java.util.Arrays.copyOfRange(tangents,0,4),.00001F);
        tangents=ModelTangents.calculate(p,n,new float[6]);
        for(float value:tangents)assertTrue(Float.isFinite(value));
        assertArrayEquals(new float[]{.5F,.5F,.5F,.5F,.5F,.5F},ModelTangents.midUvs(uv),0);
    }
    private static int[] rgba(Pixels pixels)
    {int[] out=new int[4];for(int i=0;i<4;i++)out[i]=pixels.getBuffer().get(i)&255;return out;}
}
