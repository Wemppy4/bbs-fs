package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iris only queues a slider's value when the drag ends, so a curve's default would lag a whole gesture
 * behind what the screen shows. Queue on every step of the drag instead — the queue is where
 * {@link mchorse.bbs_mod.utils.iris.QueueMap} picks the value up.
 */
@Mixin(SliderElementWidget.class)
public abstract class SliderElementWidgetMixin
{
    @Inject(method = "whileDragging", at = @At("TAIL"), remap = false, require = 0)
    private void onDragging(int mouseX, CallbackInfo info)
    {
        ((StringElementWidgetInvoker) (Object) this).bbs$queue();
    }
}
