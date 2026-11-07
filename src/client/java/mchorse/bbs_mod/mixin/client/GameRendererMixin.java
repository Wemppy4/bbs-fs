package mchorse.bbs_mod.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.PixelArt;
import mchorse.bbs_mod.items.GunZoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin
{
    /**
     * This injection cancels bobbing when camera controller takes over
     */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    public void onBob(CallbackInfo ci)
    {
        if (BBSModClient.getCameraController().getCurrent() != null)
        {
            ci.cancel();
        }
    }

    /**
     * This injection replaces the camera FOV when camera controller takes over
     */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void onGetFov(CallbackInfoReturnable<Double> info)
    {
        GunZoom gunZoom = BBSModClient.getGunZoom();

        if (gunZoom != null)
        {
            info.setReturnValue((double) gunZoom.getFOV(info.getReturnValue().floatValue()));

            return;
        }

        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
            info.setReturnValue(BBSRendering.widenFovForExportFrame(controller.getFOV()));
        }
    }

    /**
     * This injection replaces the camera roll when camera controller takes over
     */
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    public void onTiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo info)
    {
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null && !BBSRendering.isIrisShadowPass())
        {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(controller.getRoll()));

            info.cancel();
        }
    }

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    public void onRenderHand(CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (current instanceof PlayCameraController)
        {
            info.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderWorld")
    private void onWorldRenderBegin(CallbackInfo callbackInfo)
    {
        BBSRendering.onWorldRenderBegin();
    }

    /**
     * These two injections hand text over to the pixel art shaders while BBS's
     * UI is drawing, so glyphs stay even at a fractional ui_scale. Both text
     * programs are shared with the world's text, hence PixelArt gating them on
     * the UI actually being on screen.
     */
    @Inject(method = "getRenderTypeTextProgram", at = @At("HEAD"), cancellable = true)
    private static void onGetRenderTypeTextProgram(CallbackInfoReturnable<ShaderProgram> info)
    {
        ShaderProgram program = PixelArt.getTextProgram(false);

        if (program != null)
        {
            info.setReturnValue(program);
        }
    }

    @Inject(method = "getRenderTypeTextIntensityProgram", at = @At("HEAD"), cancellable = true)
    private static void onGetRenderTypeTextIntensityProgram(CallbackInfoReturnable<ShaderProgram> info)
    {
        ShaderProgram program = PixelArt.getTextProgram(true);

        if (program != null)
        {
            info.setReturnValue(program);
        }
    }

    /**
     * These two injections substitute an orthographic projection when the film
     * editor's orbit camera asks for one (see BBSRendering#getOrthoProjection).
     * The frustum culling matrix gets a loose lower bound on the frame size so
     * culling stays conservative when zoomed all the way in; the same bound
     * pushes its near plane back, so the frustum never culls a section the
     * render would still have drawn.
     */
    @ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;setupFrustum(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/util/math/Vec3d;Lorg/joml/Matrix4f;)V"
        ),
        index = 2
    )
    private Matrix4f onSetupFrustumProjection(Matrix4f projection)
    {
        return BBSRendering.getOrthoProjection((GameRenderer) (Object) this, projection, 20F);
    }

    @ModifyArg(
        method = "renderWorld",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"
        ),
        index = 7
    )
    private Matrix4f onRenderProjection(Matrix4f projection)
    {
        Matrix4f ortho = BBSRendering.getOrthoProjection((GameRenderer) (Object) this, projection, 0F);

        if (ortho != projection)
        {
            RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);
        }

        return ortho;
    }

    @Inject(at = @At("RETURN"), method = "renderWorld")
    private void onWorldRenderEnd(CallbackInfo callbackInfo)
    {
        BBSRendering.onWorldRenderEnd();
    }

    @Inject(method = "render", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;hudHidden:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void onBeforeHudRendering(float tickDelta, long startTime, boolean tick, CallbackInfo info)
    {
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (MinecraftClient.getInstance().options.hudHidden && current == null)
        {
            BBSRendering.onRenderBeforeScreen();
        }
    }
}