package mchorse.bbs_mod.ui.film.utils.keyframes;

import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.api.client.events.TimelineEvents;
import mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCanvas;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.markers.FilmMarkers;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.markers.UIMarkersController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIFilmKeyframes extends UIKeyframes
{
    public IUIClipsDelegate editor;
    public boolean absolute;

    /**
     * Every film keyframe timeline knows both the film and its own clip offset, so wiring the
     * markers here covers the replay dope sheet and every nested clip keyframe editor at once.
     */
    private final UIMarkersController markers = new UIMarkersController(this::getFilmMarkers);

    public UIFilmKeyframes(IUIClipsDelegate delegate, Runnable callback)
    {
        super(callback);

        this.editor = delegate;
    }

    @Override
    public void triggerChange()
    {
        super.triggerChange();

        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        if (panel == null || panel.getController().isControlling()) return;
        var replay = panel.replayEditor.getReplay();
        var entity = panel.getController().getCurrentEntity();
        if (replay == null || entity == null) return;

        var channels = replay.keyframes;
        boolean position = false;
        boolean rotation = false;
        for (var sheet : this.getOperationSheets())
        {
            if (!sheet.selection.hasAny()) continue;
            position |= sheet.channel == channels.x || sheet.channel == channels.y || sheet.channel == channels.z;
            rotation |= sheet.channel == channels.yaw || sheet.channel == channels.pitch
                || sheet.channel == channels.headYaw || sheet.channel == channels.bodyYaw;
        }
        if (!position && !rotation) return;

        float cursor = this.getTick();
        float tick = replay.getTick((int) cursor) + (cursor - (int) cursor);
        /* As with the replay gizmo, refresh the live placement now instead of waiting for
         * the 20 Hz entity tick. Sample the edited channels, including after cancellation. */
        if (position)
        {
            double x = channels.x.interpolate(tick);
            double y = channels.y.interpolate(tick);
            double z = channels.z.interpolate(tick);
            entity.setPosition(x, y, z);
            entity.setPrevX(x);
            entity.setPrevY(y);
            entity.setPrevZ(z);
        }
        if (rotation)
        {
            float yaw = channels.yaw.interpolate(tick).floatValue();
            float pitch = channels.pitch.interpolate(tick).floatValue();
            float headYaw = channels.headYaw.interpolate(tick).floatValue();
            float bodyYaw = channels.bodyYaw.interpolate(tick).floatValue();
            entity.setYaw(yaw);
            entity.setPitch(pitch);
            entity.setHeadYaw(headYaw);
            entity.setBodyYaw(bodyYaw);
            entity.setPrevYaw(yaw);
            entity.setPrevPitch(pitch);
            entity.setPrevHeadYaw(headYaw);
            entity.setPrevBodyYaw(bodyYaw);
        }
    }

    @Override
    public void editForm(Form form, Runnable edit)
    {
        Film film = this.editor == null ? null : this.editor.getFilm();
        if (film == null) return;
        var root = FormUtils.getRoot(form);
        for (var replay : film.replays.getList())
        {
            if (replay.form.get() != root) continue;
            BaseValue.edit(replay.form,
                IValueListener.FLAG_UNMERGEABLE, value -> edit.run());
            var panel = this.getParent(UIFilmPanel.class);
            if (panel != null) panel.getController().refreshReplayForm(replay);
            return;
        }
    }

    private FilmMarkers getFilmMarkers()
    {
        Film film = this.editor == null ? null : this.editor.getFilm();

        return film == null ? null : film.markers;
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

    public float getOffset()
    {
        if (this.editor == null)
        {
            return 0;
        }

        UIContext context = this.getContext();

        return this.editor.getKeyframeCursor(context == null ? 0F : context.getTransition()) - this.getClipOffset();
    }

    @Override
    public float getTick()
    {
        return this.getOffset();
    }

    @Override
    public float getPlayheadTick(UIContext context)
    {
        return this.editor == null ? 0F : this.editor.getTimelineCursor(context.getTransition()) - this.getClipOffset();
    }

    /**
     * The playhead in this timeline's own tick space &mdash; {@link #getOffset()} rather than the
     * raw cursor, so a keyframe clip keys where its cursor is drawn instead of at the film's tick.
     */
    @Override
    public Float getAutoKeyframeTick()
    {
        if (this.editor == null || !BBSSettings.autoKeyframe.get() && !this.editor.isRunning())
        {
            return null;
        }

        return this.getOffset();
    }

    @Override
    public boolean stopPlaybackOnValueChange()
    {
        if (this.editor == null || BBSSettings.autoKeyframe.get() || !this.editor.isRunning())
        {
            return false;
        }

        float tick = this.getOffset() + this.getClipOffset();
        this.editor.togglePlayback();
        this.editor.setCursor(tick);

        return true;
    }

    @Override
    protected void selectNextKeyframe(int direction)
    {
        super.selectNextKeyframe(direction);

        Keyframe keyframe = this.getGraph().getSelected();

        if (keyframe != null)
        {
            this.editor.setCursor(keyframe.getTick() + this.getClipOffset());
        }
    }

    @Override
    protected boolean hasCursor()
    {
        return this.editor != null;
    }

    @Override
    protected void moveNoKeyframes(UIContext context)
    {
        if (this.editor != null)
        {
            long offset = this.getClipOffset();

            this.editor.stopPlaybackOnScrub();
            this.editor.setCursor(Math.max(0F, this.fromGraphCursor(context.mouseX) + offset));
        }
    }

    @Override
    protected void renderOverlay(UIContext context)
    {
        if (this.editor != null)
        {
            float cursor = this.getPlayheadTick(context);
            int cx = this.toGraphX(cursor);
            String label = TimeUtils.formatCursorTime(cursor) + "/" + TimeUtils.formatTime(this.getDuration());

            this.markers.render(context, this.graphArea, this.getXAxis(), (int) this.getClipOffset());

            context.batcher.clip(this.graphArea, context);
            UITimelineCanvas.renderCursor(context, label, this.area, cx - 1);
            context.batcher.unclip(context);
        }

        super.renderOverlay(context);

        if (this.editor != null)
        {
            TimelineEvents.OVERLAY.invoker().render(this.editor.getFilm(), context, this.graphArea,
                tick -> this.toGraphX((float) (tick - this.getClipOffset())));
        }
    }
}
