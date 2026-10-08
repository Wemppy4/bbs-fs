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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public interface IUIKeyframeGraph
{
    /** The first track starts right under the ruler - no gap left where a divider used to sit. */
    public static final int TOP_MARGIN = TimelineRulerRenderer.RULER_BLOCK_HEIGHT;

    public void resetView();

    public default void updateZoom()
    {}

    public default void stopZoom()
    {}

    /** The timeline this graph draws, so a graph can ask the editor about the playhead. */
    public UIKeyframes getKeyframes();

    public UIKeyframeSheet getLastSheet();

    /** Visible operation scope; the complete catalog belongs to UIKeyframes.getSheets(). */
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
        return this.getKeyframes().getSelectedKeyframe();
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

    public UIKeyframeSheet getSheet(int mouseX, int mouseY);

    /** The first row that is a track, for operations that must land somewhere when the cursor is over nothing. */
    public default UIKeyframeSheet getFirstTrackSheet()
    {
        List<UIKeyframeSheet> sheets = this.getSheets();

        return sheets.isEmpty() ? null : sheets.get(0);
    }

    public boolean addKeyframe(int mouseX, int mouseY);

    /** Create at exact time without converting the playhead through a screen pixel. */
    public boolean addKeyframeAt(float tick, int mouseY);

    public default Keyframe addKeyframe(UIKeyframeSheet sheet, float tick, Object value)
    {
        KeyframeSegment segment = sheet.channel.find(tick);
        Keyframe extra = null;
        BaseValueBasic property = sheet.property;

        if (value == null)
        {
            if (segment != null)
            {
                value = sheet.sample(tick);
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
        this.removeKeyframes(Collections.singletonList(keyframe));
    }

    /** Remove these keyframes as one edit, whichever tracks they are on. */
    public default void removeKeyframes(List<Keyframe> keyframes)
    {
        Map<UIKeyframeSheet, List<Keyframe>> bySheet = new LinkedHashMap<>();

        for (Keyframe keyframe : keyframes)
        {
            UIKeyframeSheet sheet = this.getSheet(keyframe);

            if (sheet != null) bySheet.computeIfAbsent(sheet, (k) -> new ArrayList<>()).add(keyframe);
        }

        if (bySheet.isEmpty()) return;
        for (UIKeyframeSheet sheet : bySheet.keySet()) sheet.channel.preNotify(IValueListener.FLAG_UNMERGEABLE);
        for (Map.Entry<UIKeyframeSheet, List<Keyframe>> entry : bySheet.entrySet())
        {
            for (Keyframe keyframe : entry.getValue()) entry.getKey().remove(keyframe);
        }
        for (UIKeyframeSheet sheet : bySheet.keySet()) sheet.channel.postNotify(IValueListener.FLAG_UNMERGEABLE);
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

    /**
     * Every keyframe a click at this point takes, the one {@link #findKeyframe} hits first. A track's
     * keyframe stands for itself; a heading's summary mark for its whole column.
     */
    public default List<Keyframe> findKeyframes(int mouseX, int mouseY)
    {
        Pair<Keyframe, KeyframeType> pair = this.findKeyframe(mouseX, mouseY);

        return pair == null ? Collections.emptyList() : Collections.singletonList(pair.a);
    }

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

    public default void setMotionShift(float shift, boolean dirty)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            for (Keyframe keyframe : sheet.selection.getSelected())
            {
                if (keyframe.supportsMotionShift()) keyframe.setMotionShift(shift, dirty);
            }
        }
        this.getKeyframes().triggerChange();
    }

    public default void setInterpolation(Interpolation interpolation)
    {
        for (UIKeyframeSheet sheet : this.getSheets())
        {
            sheet.setInterpolation(interpolation);
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
