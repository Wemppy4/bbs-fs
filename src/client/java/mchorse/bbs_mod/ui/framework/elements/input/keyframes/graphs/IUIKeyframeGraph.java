package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.renderers.TimelineRulerRenderer;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.List;

public interface IUIKeyframeGraph
{
    /** The first track starts right under the ruler - no gap left where a divider used to sit. */
    public static final int TOP_MARGIN = TimelineRulerRenderer.RULER_BLOCK_HEIGHT;

    public void resetView();

    /** The timeline this graph draws, so a graph can ask the editor about the playhead. */
    public UIKeyframes getKeyframes();

    /**
     * The tick auto-keyframing writes at, or {@code null} when edits land on the keyframes they
     * were made on. See {@link UIKeyframes#getAutoKeyframeTick()}.
     */
    public default Integer getAutoKeyframeTick()
    {
        UIKeyframes keyframes = this.getKeyframes();

        return keyframes == null ? null : keyframes.getAutoKeyframeTick();
    }

    /**
     * The keyframe an edit made on {@code keyframe} should actually land on: itself normally, or
     * the keyframe of its own track at the playhead when auto-keyframing &mdash; brought into
     * being from the track's interpolated value if there is none there yet.
     */
    public default <T> Keyframe<T> getEditTarget(Keyframe<T> keyframe)
    {
        Integer tick = this.getAutoKeyframeTick();
        UIKeyframeSheet sheet = tick == null ? null : this.getSheet(keyframe);

        if (sheet == null || sheet.header)
        {
            return keyframe;
        }

        Keyframe<T> target = sheet.ensureKeyframe(tick);

        return target == null ? keyframe : target;
    }

    public UIKeyframeSheet getLastSheet();

    public List<UIKeyframeSheet> getSheets();

    /* Selection */

    public default void clearSelection()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.clear();
        }

        this.pickKeyframe(null);
    }

    public default void selectAll()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.all();
        }

        this.pickSelected();
    }

    public default void selectAfter(float tick, int direction)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.after(tick, direction);
        }

        this.pickSelected();
    }

    public void selectByX(int mouseX);

    public void selectInArea(Area area);

    public default Keyframe getSelected()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            Keyframe first = sheet.selection.getFirst();

            if (first != null)
            {
                return first;
            }
        }

        return null;
    }

    /* Keyframe management */

    public default UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        if (keyframe == null)
        {
            return null;
        }

        KeyframeChannel channel = (KeyframeChannel) keyframe.getParent();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.channel == channel)
            {
                return sheet;
            }
        }

        return null;
    }

    public default UIKeyframeSheet getSheet(String id)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.id.equals(id))
            {
                return sheet;
            }
        }

        return null;
    }

    public UIKeyframeSheet getSheet(int mouseY);

    /**
     * The row under the cursor that is a <em>track</em> — something holding a value that can be
     * keyed, pasted into, curve-edited and restyled. A body part's section is a heading, not a track:
     * it names a part, holds no value, and its channel belongs to no replay, so anything written
     * into it would be dropped on save without a word.
     *
     * <p>Every operation that acts on a track resolves its target through this and not through
     * {@link #getSheet(int)}, which answers the plainer question of which row the cursor is over —
     * that one is still what hit-testing and folding need.</p>
     */
    public default UIKeyframeSheet getTrackSheet(int mouseY)
    {
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        return sheet != null && sheet.header ? null : sheet;
    }

    /** The first row that is a track, for operations that must land somewhere when the cursor is over nothing. */
    public default UIKeyframeSheet getFirstTrackSheet()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (!sheet.header)
            {
                return sheet;
            }
        }

        return null;
    }

    public boolean addKeyframe(int mouseX, int mouseY);

    public default Keyframe addKeyframe(UIKeyframeSheet sheet, float tick, Object value)
    {
        if (sheet.header)
        {
            /* A header names a body part; there is no value to key. Its channel belongs to no
             * replay, so a keyframe placed here would vanish on save without a word. */
            return null;
        }

        KeyframeSegment segment = sheet.channel.find(tick);
        Keyframe extra = null;
        BaseValueBasic property = sheet.property;

        if (value == null)
        {
            if (segment != null)
            {
                value = segment.createInterpolated();
                extra = segment.a;
            }
            else if (sheet.seed != null)
            {
                /* Before the property: a sheet with both uses the seed to IMPROVE on the raw
                 * property value (the color overlay seeds at full strength so a fresh keyframe
                 * is visible; the property's default is fully transparent). */
                value = sheet.seed.get();
            }
            else if (property != null)
            {
                value = sheet.channel.getFactory().copy(property.get());
            }
            else
            {
                value = sheet.channel.getFactory().createEmpty();
            }
        }

        /* Adding a keyframe is a discrete edit: seal the undo so several keyframes made
         * in a row (within the merge window) each undo separately, not all at once. */
        sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);

        int index = sheet.channel.insert(tick, value);
        Keyframe keyframe = sheet.channel.get(index);

        if (extra != null)
        {
            keyframe.copyOverExtra(extra);
        }

        this.clearSelection();
        this.pickKeyframe(keyframe);
        sheet.selection.add(index);

        return keyframe;
    }

    /**
     * Same as {@link #addKeyframe(UIKeyframeSheet, float, Object)}, but for keyframes the user
     * creates by hand (clicking/keybinding in the editor). Inheritance from a neighbour is kept
     * exactly as before; only the "empty spot" case - where the keyframe would otherwise default
     * to linear - is stamped with the configured default interpolation
     * ({@link BBSSettings#getDefaultKeyframeInterpolation()}). Automated inserts (recording, pose
     * capture, animation baking) call the plain {@link #addKeyframe} so they are never affected.
     */
    public default Keyframe addKeyframeManually(UIKeyframeSheet sheet, float tick, Object value)
    {
        /* addKeyframe inherits (copyOverExtra) only when no explicit value is given and the
         * channel already has keyframes; in every other case the new keyframe is left at linear. */
        boolean inherits = value == null && !sheet.channel.isEmpty();
        Keyframe keyframe = this.addKeyframe(sheet, tick, value);

        if (keyframe != null && !inherits)
        {
            keyframe.getInterpolation().setInterp(BBSSettings.getDefaultKeyframeInterpolation());
        }

        return keyframe;
    }

    public default void removeKeyframe(Keyframe keyframe)
    {
        UIKeyframeSheet sheet = this.getSheet(keyframe);

        sheet.remove(keyframe);
        sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.clearSelection();
        this.pickKeyframe(null);
    }

    public default void removeSelected()
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.selection.removeSelected();
        }

        this.pickKeyframe(null);
    }

    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY);

    public default void pickSelected()
    {
        this.pickKeyframe(this.getSelected());
    }

    public default void onCallback(Keyframe keyframe)
    {}

    public default void pickKeyframe(Keyframe keyframe)
    {
        this.getKeyframes().pickKeyframe(keyframe);
    }

    public void selectKeyframe(Keyframe keyframe);

    public default void setTick(float tick, boolean dirty)
    {
        Keyframe selected = this.getSelected();

        if (selected == null)
        {
            return;
        }

        float diff = tick - selected.getTick();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setTickBy(diff, dirty);
        }
    }

    /** Move all selected keyframes on all sheets by the given tick delta. */
    public default void moveSelectedBy(float diff, boolean dirty)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setTickBy(diff, dirty);
        }
    }

    public default void setDuration(float duration)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setDuration(duration);
        }
    }

    public default void setInterpolation(Interpolation interpolation)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setInterpolation(interpolation);
        }
    }

    /**
     * Both this and {@link #setTick(float, boolean)} are driven by the keyframe properties panel,
     * which outlives the selection it was built for: an undo or a removed keyframe can empty the
     * selection without rebuilding the panel. Without a selected keyframe there is nothing to edit
     * relative to, so the edit is dropped rather than applied blindly to the whole channel.
     */
    public default void setValue(Object value, boolean unmergeable)
    {
        this.setValue(value, unmergeable, false);
    }

    /**
     * @param fromEditor the edit came from the keyframe's editor panel rather than from dragging
     *                   the keyframe itself, so auto-keyframing may move it onto the playhead.
     *                   Dragging a keyframe in the graph is direct manipulation of that keyframe
     *                   and must always land on it, wherever the playhead stands.
     */
    public default void setValue(Object value, boolean unmergeable, boolean fromEditor)
    {
        Keyframe selected = this.getSelected();

        if (selected == null)
        {
            return;
        }

        IKeyframeFactory factory = selected.getFactory();

        this.applyValue(factory, value, selected, unmergeable, fromEditor);
    }

    /**
     * Fan a value edit out over every track taking part in it: the selected keyframes normally, or
     * the keyframe at the playhead of every track with a selection when auto-keyframing an edit
     * made in the editor panel.
     *
     * @param primary the keyframe the edit was made on, whose value before the edit the numeric
     *                delta of the other keyframes is measured against
     */
    public default void applyValue(IKeyframeFactory factory, Object value, Keyframe primary, boolean unmergeable, boolean fromEditor)
    {
        Integer tick = fromEditor ? this.getAutoKeyframeTick() : null;

        /* The value the edit is measured against is the one on the keyframe it actually lands on,
         * which auto-keyframing moves to the playhead. Reading it off the selected keyframe would
         * measure the delta against a keyframe at another tick and land the change twice. */
        Object before = factory.copy((tick == null ? primary : this.getEditTarget(primary)).getValue());

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.channel.getFactory() != factory || sheet.header)
            {
                continue;
            }

            if (tick == null)
            {
                sheet.setValue(value, before, unmergeable);
            }
            else if (!sheet.selection.getSelected().isEmpty())
            {
                Keyframe target = sheet.ensureKeyframe(tick);

                if (target != null)
                {
                    sheet.setValueOn(target, value, before, unmergeable);
                }
            }
        }
    }

    public void resize();

    /* Input handling */

    public boolean mouseClicked(UIContext context);

    public void mouseReleased(UIContext context);

    public void mouseScrolled(UIContext context);

    public void handleMouse(UIContext context, int lastX, int lastY);

    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV);

    /* Rendering */

    public void render(UIContext context);

    public void postRender(UIContext context);

    public default void renderTopmostKeyframes(UIContext context)
    {}

    /* State recovery */

    public void saveState(MapType extra);

    public void restoreState(MapType extra);
}
