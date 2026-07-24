package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

import java.util.List;

public interface IUIKeyframeGraph
{
    public static final int TOP_MARGIN = 25;

    public void resetView();

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

    public boolean addKeyframe(int mouseX, int mouseY);

    public default Keyframe addKeyframe(UIKeyframeSheet sheet, float tick, Object value)
    {
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
            else if (property != null)
            {
                value = sheet.channel.getFactory().copy(property.get());
            }
            else if (sheet.seed != null)
            {
                value = sheet.seed.get();
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

    public void pickKeyframe(Keyframe keyframe);

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
        Keyframe selected = this.getSelected();

        if (selected == null)
        {
            return;
        }

        IKeyframeFactory factory = selected.getFactory();
        Object keyframe = factory.copy(selected.getValue());

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.channel.getFactory() == factory)
            {
                sheet.setValue(value, keyframe, unmergeable);
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
