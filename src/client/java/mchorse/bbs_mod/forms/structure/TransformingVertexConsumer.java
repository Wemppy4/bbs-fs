package mchorse.bbs_mod.forms.structure;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Applies a fixed position/normal transform (plus a per-block offset) to incoming vertices.
 * Needed for vanilla fluid rendering: {@code BlockRenderManager.renderFluid} ignores the
 * {@code MatrixStack} and emits chunk-local coordinates ({@code pos & 15}), so this wrapper
 * re-adds the chunk base offset and applies the current pose matrix manually.
 */
public class TransformingVertexConsumer implements VertexConsumer
{
    private final Matrix4f positionMatrix;
    private final Matrix3f normalMatrix;
    private final Vector4f position = new Vector4f();
    private final Vector3f normal = new Vector3f();

    private VertexConsumer delegate;
    private float offsetX;
    private float offsetY;
    private float offsetZ;

    public TransformingVertexConsumer(Matrix4f positionMatrix, Matrix3f normalMatrix)
    {
        this.positionMatrix = positionMatrix;
        this.normalMatrix = normalMatrix;
    }

    public TransformingVertexConsumer target(VertexConsumer delegate, float offsetX, float offsetY, float offsetZ)
    {
        this.delegate = delegate;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;

        return this;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z)
    {
        this.position.set((float) x + this.offsetX, (float) y + this.offsetY, (float) z + this.offsetZ, 1F);
        this.positionMatrix.transform(this.position);
        this.delegate.vertex(this.position.x, this.position.y, this.position.z);

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.delegate.color(red, green, blue, alpha);

        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.delegate.texture(u, v);

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        this.delegate.overlay(u, v);

        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.delegate.light(u, v);

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.normal.set(x, y, z);
        this.normalMatrix.transform(this.normal);
        this.delegate.normal(this.normal.x, this.normal.y, this.normal.z);

        return this;
    }

    @Override
    public void next()
    {
        this.delegate.next();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha)
    {
        this.delegate.fixedColor(red, green, blue, alpha);
    }

    @Override
    public void unfixColor()
    {
        this.delegate.unfixColor();
    }
}
