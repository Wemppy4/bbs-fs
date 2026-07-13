package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.renderers.MobRenderContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ModelPart.class)
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

        if (transform == null)
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

        if (transform.fix > 0F)
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

        pivotX += transform.translate.x;
        pivotY += transform.translate.y;
        pivotZ += transform.translate.z;
        pitch += transform.rotate.x;
        yaw += transform.rotate.y;
        roll += transform.rotate.z;
        scaleX += transform.scale.x - 1F;
        scaleY += transform.scale.y - 1F;
        scaleZ += transform.scale.z - 1F;

        matrices.translate(pivotX / 16F, pivotY / 16F, pivotZ / 16F);

        if (pitch != 0F || yaw != 0F || roll != 0F)
        {
            matrices.multiply(new Quaternionf().rotationZYX(roll, yaw, pitch));
        }

        if (transform.rotate2.x != 0F || transform.rotate2.y != 0F || transform.rotate2.z != 0F)
        {
            matrices.multiply(new Quaternionf().rotationZYX(transform.rotate2.z, transform.rotate2.y, transform.rotate2.x));
        }

        if (scaleX != 1F || scaleY != 1F || scaleZ != 1F)
        {
            matrices.scale(scaleX, scaleY, scaleZ);
        }

        info.cancel();
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
        PoseTransform transform = MobRenderContext.getTransform((ModelPart) (Object) this);

        if (transform == null)
        {
            return;
        }

        int light = args.get(2);
        int u = light & '\uffff';
        int v = light >> 16 & '\uffff';

        u = (int) Lerps.lerp(u, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(transform.lighting, 0F, 1F));

        args.set(2, u | v << 16);
        args.set(4, (float) args.get(4) * transform.color.r);
        args.set(5, (float) args.get(5) * transform.color.g);
        args.set(6, (float) args.get(6) * transform.color.b);
        args.set(7, (float) args.get(7) * transform.color.a);
    }
}
