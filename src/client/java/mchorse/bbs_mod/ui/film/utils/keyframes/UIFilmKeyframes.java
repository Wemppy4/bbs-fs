package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import java.util.function.Consumer;

public class UIFilmKeyframes extends UIKeyframes
{
    public IUIClipsDelegate editor;
    public boolean absolute;

    public UIFilmKeyframes(IUIClipsDelegate delegate, Consumer<Keyframe> callback)
    {
        super(callback);

        this.editor = delegate;
    }

    public UIFilmKeyframes absolute()
    {
        this.absolute = true;

        return this;
    }

    public long getClipOffset()
    {
        if (this.absolute)
        {
            return 0;
        }

        if (this.editor == null || this.editor.getClip() == null)
        {
            return 0;
        }

        return this.editor.getClip().tick.get();
    }

    public int getOffset()
    {
        if (this.editor == null)
        {
            return 0;
        }

        return (int) (this.editor.getCursor() - this.getClipOffset());
    }

    @Override
    public float getTick()
    {
        return this.getOffset();
    }

    @Override
    protected void selectNextKeyframe(int direction)
    {
        super.selectNextKeyframe(direction);

        Keyframe keyframe = this.getGraph().getSelected();

        if (keyframe != null)
        {
            this.editor.setCursor((int) keyframe.getTick());
        }
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (this.editor != null)
        {
            long offset = this.getClipOffset();

            this.editor.stopPlaybackOnScrub();
            this.editor.setCursor(Math.max(0, (int) (Math.round(this.fromGraphX(context.mouseX)) + offset)));
        }
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        if (this.editor != null)
        {
            int cx = this.toGraphX(this.getOffset());
            String label = TimeUtils.formatTime(this.getOffset()) + "/" + TimeUtils.formatTime(this.getDuration());

            context.batcher.clip(this.graphArea, context);
            UIClips.renderCursor(context, label, this.area, cx - 1);
            context.batcher.unclip(context);
        }

        super.renderOverlay(context);
    }
}
