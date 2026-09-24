package mchorse.bbs_mod.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the enchantment glint in step with the world while a video is being recorded.
 *
 * <p>Vanilla scrolls the glint texture by {@code Util.getMeasuringTimeMs()}, i.e. wall-clock
 * time, while {@link RenderTickCounterMixin} advances the game by a fixed {@code 20 / fps} ticks
 * per recorded frame no matter how long the frame took to render. On a slow export the world
 * therefore plays back at normal speed in the video while the glint races ahead. Here the glint
 * reads the recording's own clock instead - the ticks the recorder has advanced plus the current
 * partial tick - anchored to the wall-clock value at the moment recording started, so it neither
 * jumps on the first frame nor drifts afterwards.</p>
 */
@Mixin(RenderPhase.class)
public abstract class RenderPhaseMixin
{
    @Unique
    private static boolean recordingGlint;

    @Unique
    private static long recordingGlintAnchor;

    @ModifyExpressionValue(method = "setupGlintTexturing", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMeasuringTimeMs()J"))
    private static long onSetupGlintTexturing(long timeMs)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (!videoRecorder.isRecording())
        {
            recordingGlint = false;

            return timeMs;
        }

        if (!recordingGlint)
        {
            recordingGlint = true;
            recordingGlintAnchor = timeMs;
        }

        double ticks = videoRecorder.serverTicks + MinecraftClient.getInstance().getTickDelta();

        return recordingGlintAnchor + (long) (ticks * 50D);
    }
}
