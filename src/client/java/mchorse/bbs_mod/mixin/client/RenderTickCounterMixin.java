package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.VideoRecorder;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class RenderTickCounterMixin
{
    @Shadow
    public float tickProgress;

    @Shadow
    public float dynamicDeltaTicks;

    @Shadow
    private long lastTimeMillis;

    private int heldFrames;

    private long lastFrameTime;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    public void onBeginRenderTick(long timeMillis, boolean tick, CallbackInfoReturnable<Integer> info)
    {
        VideoRecorder videoRecorder = BBSModClient.getVideoRecorder();

        if (videoRecorder.isRecording())
        {
            if (videoRecorder.getCounter() == 0)
            {
                this.tickProgress = 0;
            }

            if (this.heldFrames == 0)
            {
                /* When frame rate limiting is enabled, throttle the recording to the video
                 * frame rate in real time, so a powerful machine doesn't render the recording
                 * faster than wall-clock time. We hold the frame (without advancing the world)
                 * until enough real time has passed to produce the next frame. Same logic as
                 * 1.21.1; only the counter fields were renamed (tickDelta -> tickProgress,
                 * lastFrameDuration -> dynamicDeltaTicks, prevTimeMillis -> lastTimeMillis). */
                if (BBSSettings.videoLimitFrameRate.get())
                {
                    long frameInterval = (long) (1000F / BBSRendering.getVideoFrameRate());

                    if (timeMillis - this.lastFrameTime < frameInterval)
                    {
                        BBSRendering.canRender = false;

                        info.setReturnValue(0);

                        return;
                    }

                    this.lastFrameTime = timeMillis;
                }

                this.dynamicDeltaTicks = 20F / (float) BBSRendering.getVideoFrameRate();
                this.lastTimeMillis = timeMillis;
                this.tickProgress += this.dynamicDeltaTicks;

                int i = (int) this.tickProgress;

                this.tickProgress -= (float) i;

                videoRecorder.serverTicks += i;
                BBSRendering.canRender = true;

                info.setReturnValue(i);
            }
            else
            {
                BBSRendering.canRender = false;

                info.setReturnValue(0);
            }

            this.heldFrames += 1;

            if (this.heldFrames >= BBSSettings.videoHeldFrames.get())
            {
                this.heldFrames = 0;
            }
        }
        else
        {
            this.heldFrames = 0;
            this.lastFrameTime = 0;
        }
    }
}