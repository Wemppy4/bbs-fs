package mchorse.bbs_mod.forms.renderers.mob;

import net.minecraft.client.render.VertexConsumer;

/**
 * A pass-through vertex consumer whose only job is to be unrecognisable to Sodium.
 *
 * <p>Sodium accelerates entity rendering by injecting into {@code ModelPart.render}: it converts
 * the consumer to its own {@code VertexBufferWriter}, CANCELS the vanilla method and draws the
 * whole subtree itself from a copied part tree — passing one light value down to every part it
 * draws. That is fatal to per-bone picking, which is exactly a per-part light value, and it also
 * means vanilla's own recursion (and therefore any hook on it) never runs.
 *
 * <p>Sodium's conversion returns null for a consumer it doesn't know, and it then declines instead
 * of cancelling. So for the pick pass — one entity, once, off the hot path — the buffer is handed
 * over wrapped, Sodium steps aside, and vanilla walks the parts one at a time the way the id
 * mixin needs.</p>
 */
public class MobPickerVertexConsumer implements VertexConsumer
{
    private final VertexConsumer consumer;

    public MobPickerVertexConsumer(VertexConsumer consumer)
    {
        this.consumer = consumer;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        return this.consumer.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this.consumer.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this.consumer.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this.consumer.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this.consumer.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this.consumer.normal(x, y, z);
    }
}
