package mchorse.bbs_mod;

import mchorse.bbs_mod.forge.studio.NativeBillboardGeometry;
import mchorse.bbs_mod.forge.studio.NativeTextureExtruder;
import mchorse.bbs_mod.forge.studio.NativeTextureMesh;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class ForgeTextureFormsTest
{
    @BeforeClass public static void setup() { KeyframeFactories.setup(); }

    @Test public void cropPreservesPixelDensityUnlessResizeIsEnabled()
    {
        BillboardForm form = new BillboardForm();
        form.crop.get().set(2, 1, 4, 2);
        AABB bounds = NativeBillboardGeometry.bounds(form, 16, 8);
        assertEquals(-.375, bounds.x, 1E-6);
        assertEquals(.25, bounds.maxX(), 1E-6);
        assertEquals(-.125, bounds.y, 1E-6);
        assertEquals(.1875, bounds.maxY(), 1E-6);
        NativeTextureMesh mesh = NativeBillboardGeometry.create(form, 16, 8);
        form.resizeCrop.set(true);
        AABB resized = NativeBillboardGeometry.bounds(form, 16, 8);
        assertEquals(1, resized.maxX() - resized.x, 1E-6);
        assertEquals(.5, resized.maxY() - resized.y, 1E-6);
        assertArrayEquals(mesh.uvs, NativeBillboardGeometry.create(form, 16, 8).uvs, 1E-6F);
        form.offsetX.set(4F);
        NativeTextureMesh shifted = NativeBillboardGeometry.create(form, 16, 8);
        for (int i = 0; i < shifted.uvs.length; i += 2)
        {
            assertEquals(mesh.uvs[i] + .25F, shifted.uvs[i], 1E-6F);
            assertEquals(mesh.uvs[i + 1], shifted.uvs[i + 1], 1E-6F);
        }
    }

    @Test public void extrusionHasOuterAndHoleWallsButNoSharedPixelWalls()
    {
        Pixels pixels = Pixels.fromSize(3, 3);
        try
        {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++)
                if (x != 1 || y != 1) pixels.setColor(x, y, Color.white());
            NativeTextureMesh mesh = new NativeTextureExtruder().generate(pixels);
            /* Twelve perimeter edges and four edges around the hole, plus front/back. */
            assertEquals((16 * 6 + 12) * 3, mesh.positions.length);
            for (int i = 0; i < mesh.positions.length; i += 3)
                assertEquals(1F / 32F, Math.abs(mesh.positions[i + 2]), 1E-6F);
            for (int i = 0; i < mesh.positions.length; i += 9)
            {
                org.joml.Vector3f a = new org.joml.Vector3f(mesh.positions[i], mesh.positions[i + 1], mesh.positions[i + 2]);
                org.joml.Vector3f b = new org.joml.Vector3f(mesh.positions[i + 3], mesh.positions[i + 4], mesh.positions[i + 5]);
                org.joml.Vector3f c = new org.joml.Vector3f(mesh.positions[i + 6], mesh.positions[i + 7], mesh.positions[i + 8]);
                assertTrue(b.sub(a).cross(c.sub(a)).dot(mesh.normals[i], mesh.normals[i + 1], mesh.normals[i + 2]) > 0);
            }
        }
        finally { pixels.delete(); }
    }

    @Test public void extrusionKeepsOriginalOpaqueEdgeThresholdAndNonSquareBounds()
    {
        Pixels pixels = Pixels.fromSize(2, 1);
        try
        {
            pixels.setColor(0, 0, Color.white());
            pixels.setColor(1, 0, new Color(1, 1, 1, .5F));
            NativeTextureMesh mesh = new NativeTextureExtruder().generate(pixels);
            assertEquals(36 * 3, mesh.positions.length);
            AABB bounds = NativeTextureExtruder.getBounds(2, 1);
            assertEquals(1, bounds.maxX() - bounds.x, 1E-6);
            assertEquals(.5, bounds.maxY() - bounds.y, 1E-6);
            assertEquals(1F / 16F, bounds.maxZ() - bounds.z, 1E-6);
        }
        finally { pixels.delete(); }
    }
}
