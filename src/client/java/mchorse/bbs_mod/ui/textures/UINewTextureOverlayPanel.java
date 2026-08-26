package mchorse.bbs_mod.ui.textures;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageBarOverlayPanel;

/**
 * Asks for the name and the size of a texture to create — a blank canvas to paint on. The
 * name goes in the message area, the size and the button on the bar, the way the resize
 * dialog of the texture editor is laid out.
 */
public class UINewTextureOverlayPanel extends UIMessageBarOverlayPanel
{
    public UITextbox name;
    public UITrackpad width;
    public UITrackpad height;
    public UIButton create;

    private final Callback callback;

    public interface Callback
    {
        public void create(String name, int width, int height);
    }

    public UINewTextureOverlayPanel(Callback callback)
    {
        super(UIKeys.TEXTURES_BROWSER_NEW_TEXTURE_TITLE, UIKeys.TEXTURES_BROWSER_NEW_TEXTURE_DESCRIPTION);

        this.callback = callback;

        this.name = new UITextbox(100, null).filename();
        this.name.placeholder(UIKeys.TEXTURES_BROWSER_NEW_TEXTURE_NAME);
        this.name.relative(this.bar).y(-5).w(1F).h(20).anchorY(1F);

        this.width = new UITrackpad();
        this.width.limit(1, 4096, true).setValue(64);
        this.width.tooltip(UIKeys.SNOWSTORM_APPEARANCE_WIDTH);
        this.height = new UITrackpad();
        this.height.limit(1, 4096, true).setValue(64);
        this.height.tooltip(UIKeys.SNOWSTORM_APPEARANCE_HEIGHT);
        this.create = new UIButton(UIKeys.GENERAL_ADD, (b) -> this.confirm());
        this.create.w(80);

        this.bar.remove(this.confirm);
        this.bar.add(this.width, this.height, this.create);
        this.content.add(this.name);
    }

    @Override
    public void confirm()
    {
        String name = this.name.getText().trim();

        if (!name.isEmpty() && this.callback != null)
        {
            this.callback.create(name, (int) this.width.getValue(), (int) this.height.getValue());
        }

        super.confirm();
    }
}
