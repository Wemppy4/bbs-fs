package mchorse.bbs_mod.ui.framework.elements.input.keyframes.overlays;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.context.UIPopup;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeLoops;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoop;
import mchorse.bbs_mod.utils.keyframes.KeyframeLoops;

public class UIKeyframeLoopPopup extends UIPopup
{
    private final UITrackpad end;
    private final UITrackpad passes;
    private final UIKeyframeLoops editor;
    private final KeyframeChannel<?> channel;
    private final String id;

    public UIKeyframeLoopPopup(UIKeyframeLoops editor, KeyframeChannel<?> channel, String id)
    {
        super(L10n.lang("bbs.ui.keyframes.loop.title"));
        this.editor = editor;
        this.channel = channel;
        this.id = id;
        this.end = new UITrackpad(value -> this.update(value.floatValue()));
        this.passes = new UITrackpad(value ->
        {
            KeyframeLoop loop = this.channel.getLoop(this.id);
            if (loop != null) this.update(loop.start() + value.floatValue() * loop.period());
        });
        this.passes.limit(1).tooltip(L10n.lang("bbs.ui.keyframes.loop.passes_hint"));
        this.end.tooltip(L10n.lang("bbs.ui.keyframes.loop.end_hint"));
        this.column.add(
            UI.label(L10n.lang("bbs.ui.keyframes.loop.end")), this.end,
            UI.label(L10n.lang("bbs.ui.keyframes.loop.passes")), this.passes
        );
        this.refresh();
    }

    private void update(float end)
    {
        this.editor.setEnd(this.channel, this.id, end);
        this.refresh();
    }

    private void refresh()
    {
        KeyframeLoop loop = this.channel.getLoop(this.id);
        if (loop == null) return;
        this.end.limit(loop.sourceEnd(), KeyframeLoops.maxEnd(KeyframeLoops.members(this.channel, this.id), loop));
        this.end.setValue(loop.end());
        this.passes.setValue(loop.passes());
    }
}
