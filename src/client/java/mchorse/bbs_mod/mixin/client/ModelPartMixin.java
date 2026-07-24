package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.MobRenderContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Applies MobForm pose and appearance state to vanilla model parts. The lower priority makes the
 * compatibility wrapper run before Iris' cancellable fast-render callback.
 */
@Mixin(value = ModelPart.class, priority = 500)
public abstract class ModelPartMixin
{
    @Inject(
        method = "rotate(Lnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void bbs$applyMobPose(MatrixStack matrices, CallbackInfo info)
    {
        ModelPart part = (ModelPart) (Object) this;
        PoseTransform transform = MobRenderContext.getTransform(part);

        if (!MobRenderContext.isTracked(part))
        {
            return;
        }

        float pivotX = part.pivotX;
        float pivotY = part.pivotY;
        float pivotZ = part.pivotZ;
        float pitch = part.pitch;
        float yaw = part.yaw;
        float roll = part.roll;
        float scaleX = part.xScale;
        float scaleY = part.yScale;
        float scaleZ = part.zScale;

        if (transform != null && transform.fix > 0F)
        {
            ModelTransform initial = part.getDefaultTransform();

            pivotX = Lerps.lerp(pivotX, initial.pivotX, transform.fix);
            pivotY = Lerps.lerp(pivotY, initial.pivotY, transform.fix);
            pivotZ = Lerps.lerp(pivotZ, initial.pivotZ, transform.fix);
            pitch = Lerps.lerp(pitch, initial.pitch, transform.fix);
            yaw = Lerps.lerp(yaw, initial.yaw, transform.fix);
            roll = Lerps.lerp(roll, initial.roll, transform.fix);
            scaleX = Lerps.lerp(scaleX, 1F, transform.fix);
            scaleY = Lerps.lerp(scaleY, 1F, transform.fix);
            scaleZ = Lerps.lerp(scaleZ, 1F, transform.fix);
        }

        MobRenderContext.captureRotationOffset(part, pitch, yaw, roll);

        if (transform != null)
        {
            pivotX += transform.translate.x;
            pivotY += transform.translate.y;
            pivotZ += transform.translate.z;
            pitch += transform.rotate.x;
            yaw += transform.rotate.y;
            roll += transform.rotate.z;
            scaleX += transform.scale.x - 1F;
            scaleY += transform.scale.y - 1F;
            scaleZ += transform.scale.z - 1F;
        }

        matrices.translate(pivotX / 16F, pivotY / 16F, pivotZ / 16F);
        MobRenderContext.captureOrigin(part, matrices.peek().getPositionMatrix());

        if (pitch != 0F || yaw != 0F || roll != 0F)
        {
            matrices.multiply(new Quaternionf().rotationZYX(roll, yaw, pitch));
        }

        if (transform != null && (transform.rotate2.x != 0F || transform.rotate2.y != 0F || transform.rotate2.z != 0F))
        {
            matrices.multiply(new Quaternionf().rotationZYX(transform.rotate2.z, transform.rotate2.y, transform.rotate2.x));
        }

        if (scaleX != 1F || scaleY != 1F || scaleZ != 1F)
        {
            matrices.scale(scaleX, scaleY, scaleZ);
        }

        MobRenderContext.captureMatrix(part, matrices.peek().getPositionMatrix());

        info.cancel();
    }

    @ModifyVariable(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private VertexConsumer bbs$disableFastRendering(VertexConsumer vertices)
    {
        if (!MobRenderContext.isActive() || vertices instanceof DirectVertexConsumer)
        {
            return vertices;
        }

        return new DirectVertexConsumer(vertices);
    }

    @ModifyArgs(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelPart;renderCuboids(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"
        )
    )
    private void bbs$applyMobAppearance(Args args)
    {
        ModelPart part = (ModelPart) (Object) this;
        PoseTransform transform = MobRenderContext.getTransform(part);
        Color color = MobRenderContext.getColor(part);
        int pickingOffset = MobRenderContext.getPickingOffset(part);

        if (pickingOffset >= 0)
        {
            args.set(2, pickingOffset);
        }

        if (transform == null && color == null)
        {
            return;
        }

        if (pickingOffset < 0 && transform != null)
        {
            int light = args.get(2);
            int u = light & '\uffff';
            int v = light >> 16 & '\uffff';

            u = (int) Lerps.lerp(u, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(transform.lighting, 0F, 1F));

            args.set(2, u | v << 16);
        }

        float r = color == null ? 1F : color.r;
        float g = color == null ? 1F : color.g;
        float b = color == null ? 1F : color.b;
        float a = color == null ? 1F : color.a;

        if (transform != null)
        {
            r *= transform.color.r;
            g *= transform.color.g;
            b *= transform.color.b;
            a *= transform.color.a;
        }

        args.set(4, (float) args.get(4) * r);
        args.set(5, (float) args.get(5) * g);
        args.set(6, (float) args.get(6) * b);
        args.set(7, (float) args.get(7) * a);
    }

    private static final class DirectVertexConsumer implements VertexConsumer
    {
        private final VertexConsumer consumer;

        private DirectVertexConsumer(VertexConsumer consumer)
        {
            this.consumer = consumer;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            this.consumer.vertex(x, y, z);

            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            this.consumer.color(red, green, blue, alpha);

            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            this.consumer.texture(u, v);

            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            this.consumer.overlay(u, v);

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
            this.consumer.normal(x, y, z);

            return this;
        }

        @Override
        public void next()
        {
            this.consumer.next();
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha)
        {
            this.consumer.fixedColor(red, green, blue, alpha);
        }

        @Override
        public void unfixColor()
        {
            this.consumer.unfixColor();
        }
    }
}
