package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIFileLinkList;

import java.util.function.Consumer;

public class UIFolderOverlayPanel extends UIMessageBarOverlayPanel
{
    public UIFileLinkList list;

    private Consumer<Link> callback;

    public UIFolderOverlayPanel(IKey title, IKey message, Consumer<Link> callback)
    {
        super(title, message);

        this.callback = callback;

        this.list = new UIFileLinkList((l) -> {});
        this.list.filter((l) -> l.path.endsWith("/"));
        this.list.background();
        this.list.relative(this.content).xy(6, 36).w(1F, -12).h(1F, -70);
        this.list.setPath(null);

        this.confirm.label = UIKeys.GENERAL_PICK;
        this.confirm.w(100);

        this.content.add(this.list);

        this.fillsOverlay();
    }

    public UIFolderOverlayPanel confirmLabel(IKey key)
    {
        this.confirm.label = key;

        return this;
    }

    @Override
    public void confirm()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.list.path);
        }

        super.confirm();
    }
}
