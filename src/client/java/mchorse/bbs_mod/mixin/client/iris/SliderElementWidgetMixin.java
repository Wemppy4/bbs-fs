package mchorse.bbs_mod.mixin.client.iris;

import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SliderElementWidget.class)
public abstract class SliderElementWidgetMixin
{
    @Inject(method = "whileDragging", at = @At("TAIL"), remap = false)
    private void onDragging(CallbackInfo info)
    {
        ((StringElementWidgetInvoker) (Object) this).bbs$queue();
    }
}