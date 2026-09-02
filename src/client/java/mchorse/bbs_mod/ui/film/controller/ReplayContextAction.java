package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

public class ReplayContextAction extends ContextAction
{
    private final Replay replay;
    private final int color;

    public ReplayContextAction(Replay replay, IKey label, Runnable runnable, int color)
    {
        super(Icons.NONE, label, runnable);
        this.replay = replay;
        this.color = color;
    }

    @Override
    protected void renderBackground(UIContext context, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        super.renderBackground(context, x, y, w, h, hover, selected);

        if (this.color != 0)
        {
            context.batcher.box(x, y, x + 2, y + h, Colors.A100 | this.color);
            context.batcher.gradientHBox(x + 2, y, x + 24, y + h, Colors.A25 | this.color, this.color);
        }
    }

    @Override
    public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
    {
        this.renderBackground(context, x, y, w, h, hover, selected);

        Form form = this.replay.form.get();
        int offset = 22;

        if (form != null)
        {
            /* Flush batcher before rendering model */
            context.batcher.flush();

            int size = h - 4;
            int px = x + 2;
            int py = y + 2;

            FormUtilsClient.renderUI(form, context, px, py, px + size, py + size);
            
            offset = size + 6;
        }
        else
        {
            context.batcher.icon(Icons.FILM, x + 2, y + h / 2, 0, 0.5F);
        }

        context.batcher.text(this.label.get(), x + offset, y + (h - font.getHeight()) / 2 + 1, Colors.WHITE, false);
    }

    @Override
    public int getWidth(FontRenderer font)
    {
        return 34 + font.getWidth(this.label.get());
    }
}
