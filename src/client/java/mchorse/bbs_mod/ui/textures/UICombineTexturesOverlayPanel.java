package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.dashboard.textures.frames.UIFramesPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;

/**
 * Asks the two things the pictures can't say about the animation they are about to become: what
 * it is called and how long a frame lasts. The name and the size go the way the new texture
 * dialog puts them, so the two read alike.
 *
 * <p>The order of the frames isn't asked for here: it is the order the browser shows them in,
 * and the frame strip of the texture editor is where it is changed afterwards.</p>
 */
public class UICombineTexturesOverlayPanel extends UIMessageBarOverlayPanel
{
    public UITextbox name;
    public UITrackpad frametime;
    public UIButton combine;

    private final Callback callback;

    public interface Callback
    {
        public void combine(String name, int frametime);
    }

    public UICombineTexturesOverlayPanel(IKey message, String name, Callback callback)
    {
        super(UIKeys.TEXTURES_BROWSER_COMBINE_TITLE, message);

        this.callback = callback;

        this.name = new UITextbox(100, null).filename();
        this.name.placeholder(UIKeys.TEXTURES_BROWSER_COMBINE_NAME);
        this.name.setText(name);
        this.name.relative(this.bar).y(-5).w(1F).h(20).anchorY(1F);

        this.frametime = new UITrackpad();
        this.frametime.limit(1, UIFramesPanel.MAX_TIME, true).setValue(TextureAnimation.NEW_FRAMETIME);
        this.frametime.tooltip(UIKeys.TEXTURES_BROWSER_COMBINE_FRAMETIME);
        this.combine = new UIButton(UIKeys.TEXTURES_BROWSER_COMBINE_CONFIRM, (b) -> this.confirm());
        this.combine.w(80);

        this.bar.remove(this.confirm);
        this.bar.add(this.frametime, this.combine);
        this.content.add(this.name);
    }

    @Override
    public int getContentHeight()
    {
        int height = super.getContentHeight();

        /* The name field sits above the bar, so it is not part of the sum the base panel took */
        return height < 0 ? height : height + this.name.area.h - this.name.getFlex().y.offset;
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.name.textbox.moveCursorToEnd();
        parent.getContext().focus(this.name);
    }

    @Override
    public void confirm()
    {
        String name = this.name.getText().trim();

        if (!name.isEmpty() && this.callback != null)
        {
            this.callback.combine(name, (int) this.frametime.getValue());
        }

        super.confirm();
    }
}
