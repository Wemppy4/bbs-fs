package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.particles.vanilla.VanillaParticlePreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

/** Small, static samples: opening the picker never spawns or ticks world particles. */
public class UIVanillaParticleList extends UIStringList
{
    private static final int PREVIEW_SIZE = 16;

    public UIVanillaParticleList(Consumer<List<String>> callback)
    {
        super(callback);

        this.scroll.scrollItemSize = PREVIEW_SIZE + 4;
        this.preview = (context, item, x, y, size) -> VanillaParticlePreview.render(context, new ResourceLocation(item), x, y, size, mchorse.bbs_mod.utils.colors.Colors.WHITE);
    }

    @Override
    protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
    {
        int h = this.scroll.scrollItemSize;
        int left = x + this.rowContentX(element);
        int top = y + (h - PREVIEW_SIZE) / 2;

        VanillaParticlePreview.render(context, new ResourceLocation(element), left, top, PREVIEW_SIZE, RowStyle.iconColor(hover || selected));
        context.batcher.textShadow(element, left + PREVIEW_SIZE + 4, y + (h - context.batcher.getFont().getHeight()) / 2,
            RowStyle.textColor(hover || selected));
    }
}
