package mchorse.bbs_mod.forms.renderers.utils;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Feeds the fluid renderer's chunk format vertices into an entity format buffer.
 *
 * <p>{@link net.minecraft.client.render.block.FluidRenderer} writes position, colour, texture,
 * light and normal straight into the buffer, in world (well, chunk local) coordinates and
 * without a matrix, because chunk geometry never needs one. Forms need the entity format
 * instead: it carries an overlay, and it is the format the picking shader is compiled for.
 * So this applies the form's matrix to the vertices and slips the overlay in exactly where
 * the entity format expects it, right after the texture coordinates.</p>
 *
 * <p>Every method returns {@code this}: the fluid renderer chains its calls, and handing back
 * the wrapped buffer at any point in the chain would let the rest of the vertex bypass the
 * overlay.</p>
 */
public class FluidVertexConsumer implements VertexConsumer
{
    private final VertexConsumer consumer;
    private final Matrix4f position;
    private final Matrix3f normal;
    private final int overlay;

    private final Vector3f temporary = new Vector3f();

    public FluidVertexConsumer(VertexConsumer consumer, MatrixStack.Entry entry, int overlay)
    {
        this.consumer = consumer;
        this.position = entry.getPositionMatrix();
        this.normal = entry.getNormalMatrix();
        this.overlay = overlay;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        this.position.transformPosition(x, y, z, this.temporary);
        this.consumer.vertex(this.temporary.x, this.temporary.y, this.temporary.z);

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        this.consumer.color(red, green, blue, alpha);

        return this;
    }

    @Override
    public VertexConsumer color(int argb)
    {
        this.consumer.color(argb);

        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.consumer.texture(u, v);
        this.consumer.overlay(this.overlay & 0xFFFF, this.overlay >> 16 & 0xFFFF);

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.consumer.light(u, v);

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        this.normal.transform(x, y, z, this.temporary);
        this.consumer.normal(this.temporary.x, this.temporary.y, this.temporary.z);

        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width)
    {
        this.consumer.lineWidth(width);

        return this;
    }
}
