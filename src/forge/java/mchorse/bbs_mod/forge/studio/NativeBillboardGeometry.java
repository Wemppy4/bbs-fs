package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MathUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** The original BillboardFormRenderer's crop, aspect ratio and UV transform. */
public final class NativeBillboardGeometry
{
    private NativeBillboardGeometry() {}

    public static AABB bounds(BillboardForm form, int width, int height)
    {
        float w = width, h = height;
        Vector4f crop = form.crop.get();
        float left = crop.x / w, top = crop.y / h;
        float right = 1F - crop.z / w, bottom = 1F - crop.w / h;
        if (form.resizeCrop.get())
        {
            w -= crop.x + crop.z;
            h -= crop.y + crop.w;
            left = top = 0F;
            right = bottom = 1F;
        }
        float ratioX = w > h ? h / w : 1F;
        float ratioY = h > w ? w / h : 1F;
        return AABB.fromTwoPoints((left - 0.5F) * ratioY, -(bottom - 0.5F) * ratioX, 0,
            (right - 0.5F) * ratioY, -(top - 0.5F) * ratioX, 0);
    }

    public static NativeTextureMesh create(BillboardForm form, int width, int height)
    {
        Vector4f crop = form.crop.get();
        float left = crop.x / width, top = crop.y / height;
        float right = 1F - crop.z / width, bottom = 1F - crop.w / height;
        Vector3f[] uv = {new Vector3f(left, top, 0), new Vector3f(right, top, 0),
            new Vector3f(left, bottom, 0), new Vector3f(right, bottom, 0)};
        float offsetX = form.offsetX.get(), offsetY = form.offsetY.get(), rotation = form.rotation.get();
        if (offsetX != 0F || offsetY != 0F || rotation != 0F)
        {
            float centerX = (crop.x + (width - crop.z)) / 2F / width;
            /* Keep the original UV rotation pivot, including its width denominator. */
            float centerY = (crop.y + (height - crop.w)) / 2F / width;
            Matrix4f matrix = new Matrix4f().translate(centerX, centerY, 0)
                .rotateZ(MathUtils.toRad(rotation)).translate(offsetX / width, offsetY / height, 0)
                .translate(-centerX, -centerY, 0);
            for (Vector3f point : uv) matrix.transformPosition(point);
        }

        float w = width, h = height;
        if (form.resizeCrop.get())
        {
            left = top = 0;
            right = bottom = 1;
            w -= crop.x + crop.z;
            h -= crop.y + crop.w;
        }
        float ratioX = w > h ? h / w : 1F, ratioY = h > w ? w / h : 1F;
        float x1 = (left - .5F) * ratioY, x2 = (right - .5F) * ratioY;
        float y1 = -(top - .5F) * ratioX, y2 = -(bottom - .5F) * ratioX;
        float[] x = {x1, x2, x1, x2}, y = {y1, y1, y2, y2};
        int[] indices = {2, 1, 0, 2, 3, 1, 0, 1, 2, 1, 3, 2};
        float[] positions = new float[36], normals = new float[36], uvs = new float[24];
        for (int i = 0; i < indices.length; i++)
        {
            int index = indices[i];
            positions[i * 3] = x[index];
            positions[i * 3 + 1] = y[index];
            normals[i * 3 + 2] = i < 6 ? 1F : -1F;
            uvs[i * 2] = uv[index].x;
            uvs[i * 2 + 1] = uv[index].y;
        }
        return new NativeTextureMesh(positions, normals, uvs);
    }
}
