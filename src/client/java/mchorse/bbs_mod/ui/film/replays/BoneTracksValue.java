package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The pose of the frame under the cursor, read from and written to the bone tracks of one form —
 * what the Bones tab's panel edits instead of a pose keyframe.
 *
 * <p>Reading samples every animated bone at the cursor; a bone without keys is not in the pose. An
 * edit runs on a copy, and every bone that came out different gets a key at the cursor (or its key
 * there changes). So everything the pose editor does — fields, gizmo, mirror, presets, paste, fix
 * in place — keys bones one by one without knowing it.</p>
 */
public class BoneTracksValue extends UITrackValue<Pose>
{
    /** Every bone track of the form, shown on the timeline or not, by bone name. */
    private final Map<String, UIKeyframeSheet> bones;
    private final Runnable rowsChanged;

    /**
     * The picked bone, which the rows and the panel must agree on. Handed over rather than looked up
     * through the widget tree: the panel is built before its timeline is on screen.
     */
    public final BoneSelection selection;

    /** Told which bones the panel's list picked. Here rather than on the panel: the list picks while the panel is still being built. */
    public Consumer<List<String>> picked;
    /** Set by every edit that wrote a key, so the panel can pick the bone's key at the cursor once the edit is over. */
    public boolean wrote;
    /**
     * The key the panel selected on its own: the picked bone's key at the cursor. Selected alone, it
     * is the cursor's frame rather than the animator's selection, so an edit still keys every bone
     * it changes there (a mirrored arm, a preset) instead of only that one key.
     */
    public Keyframe autoPicked;

    public BoneTracksValue(UIKeyframeSheet pose, UIKeyframes editor, Map<String, UIKeyframeSheet> bones, BoneSelection selection, Runnable rowsChanged)
    {
        super(pose, editor);

        this.bones = bones;
        this.selection = selection;
        this.rowsChanged = rowsChanged;
    }

    public UIKeyframeSheet getBoneSheet(String bone)
    {
        return this.bones.get(bone);
    }

    /**
     * The bone's own track in the frame the panel shows (see {@link #getValueTick}), or null when it
     * has no keys.
     */
    public PoseTransform getBone(String bone)
    {
        UIKeyframeSheet sheet = this.bones.get(bone);

        return sheet == null ? null : valueAt(sheet, this.getValueTick());
    }

    /** A bone track at a tick: its key there when there is one (see {@link #keyAt}), what it reads there otherwise; null with no keys. */
    private static PoseTransform valueAt(UIKeyframeSheet sheet, float tick)
    {
        if (sheet.channel.isEmpty())
        {
            return null;
        }

        Keyframe key = keyAt(sheet, tick);

        return (PoseTransform) (key != null ? sheet.channel.getFactory().copy(key.getValue()) : sheet.sample(tick));
    }

    /** Every animated bone of the form at a tick. */
    private Pose frameAt(float tick)
    {
        Pose pose = new Pose();

        for (Map.Entry<String, UIKeyframeSheet> entry : this.bones.entrySet())
        {
            PoseTransform transform = valueAt(entry.getValue(), tick);

            if (transform != null)
            {
                pose.transforms.put(entry.getKey(), transform);
            }
        }

        return pose;
    }

    /**
     * The picked key, when it is one of this form's bone keys. While there is one, the panel works
     * like every other key panel: it shows that key's frame and an edit goes into the selected keys.
     * With none, it is the frame under the cursor, and an edit keys the bones there.
     */
    private Keyframe getPickedKey()
    {
        Keyframe key = this.editor.getSelectedKeyframe();

        if (key == null || (key == this.autoPicked && this.countSelected() == 1))
        {
            return null;
        }

        for (UIKeyframeSheet sheet : this.bones.values())
        {
            if (sheet.selection.has(key))
            {
                return key;
            }
        }

        return null;
    }

    private int countSelected()
    {
        int count = 0;

        for (UIKeyframeSheet sheet : this.editor.getGraph().getSheets())
        {
            count += sheet.selection.getSelected().size();
        }

        return count;
    }

    /** Whether an edit goes into the selected bone keys rather than at the cursor (see {@link #getPickedKey}). */
    public boolean editsSelection()
    {
        return this.getPickedKey() != null;
    }

    /** The bone's key at the cursor, if there is one (see {@link #keyAt}). */
    public Keyframe getKeyAtCursor(String bone)
    {
        UIKeyframeSheet sheet = this.bones.get(bone);

        return sheet == null ? null : keyAt(sheet, this.editor.getTick());
    }

    /**
     * The track's key "at" a tick: the one exactly there, or else the one within the same whole tick.
     * With snapping on, the cursor stands on whole ticks only, so a key put down between them (a
     * Shift scrub, a scaled or imported animation) could never be reached — clicking it left the
     * cursor beside it, and the next edit put a second key next to it instead of changing it.
     */
    public static Keyframe keyAt(UIKeyframeSheet sheet, float tick)
    {
        float source = sheet.channel.getSourceTick(tick);
        float cell = (float) Math.floor(source);
        Keyframe near = null;

        for (Object object : sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) object;

            if (keyframe.getTick() == source)
            {
                return keyframe;
            }

            if (near == null && Math.floor(keyframe.getTick()) == cell)
            {
                near = keyframe;
            }
        }

        return near;
    }

    /**
     * Before a bone's very first key: key it, as it stands, at the nearest keyframes of the form's
     * other bones on either side of the cursor (setting "Hold keys for a new bone"). A lone key holds
     * its value over the whole timeline, so turning a head at tick 40 would turn it from the first
     * frame on; held by its neighbours' columns, the change stays between them, the way a pose key's
     * would. Only the track's first key does this — the bone's keys are its own after that.
     */
    public static void holdNeighbours(UIKeyframeSheet sheet, float tick, Collection<UIKeyframeSheet> others)
    {
        if (!BBSSettings.timelineBoneHoldKeys.get() || !sheet.channel.isEmpty())
        {
            return;
        }

        float cell = (float) Math.floor(tick);
        Float before = null;
        Float after = null;

        for (UIKeyframeSheet other : others)
        {
            if (other == sheet)
            {
                continue;
            }

            for (Object object : other.channel.getKeyframes())
            {
                float at = ((Keyframe) object).getTick();

                if (at < cell && (before == null || at > before))
                {
                    before = at;
                }
                else if (at >= cell + 1 && (after == null || at < after))
                {
                    after = at;
                }
            }
        }

        if (before != null)
        {
            sheet.ensureKeyframe(before);
        }

        if (after != null)
        {
            sheet.ensureKeyframe(after);
        }
    }

    /** The key an edit at the cursor lands on: the one already there (see {@link #keyAt}), or a new one. */
    public static Keyframe keyToEdit(UIKeyframeSheet sheet, float tick)
    {
        Keyframe key = keyAt(sheet, tick);

        return key != null ? key : sheet.ensureKeyframe(tick);
    }

    @Override
    public Pose getValue()
    {
        return this.frameAt(this.getValueTick());
    }

    @Override
    public float getValueTick()
    {
        Keyframe picked = this.getPickedKey();

        return picked != null ? picked.getTick() : this.editor.getTick();
    }

    @Override
    public void edit(Consumer<Pose> edit)
    {
        if (this.editsSelection())
        {
            this.editSelection(edit);

            return;
        }

        Pose before = this.getValue();
        Pose after = before.copy();

        edit.accept(after);

        Set<String> touched = new HashSet<>(before.transforms.keySet());

        touched.addAll(after.transforms.keySet());

        float tick = this.editor.getTick();
        boolean changed = false;
        boolean newRows = false;

        for (String bone : touched)
        {
            UIKeyframeSheet sheet = this.bones.get(bone);
            PoseTransform a = before.transforms.get(bone);
            PoseTransform b = after.transforms.get(bone);

            if (sheet == null)
            {
                continue;
            }

            a = a == null ? new PoseTransform() : a;
            b = b == null ? new PoseTransform() : b;

            if (a.equals(b))
            {
                continue;
            }

            PoseTransform value = (PoseTransform) b.copy();
            Runnable write = () ->
            {
                holdNeighbours(sheet, tick, this.bones.values());
                keyToEdit(sheet, tick).setValue(value, false);
            };

            if (this.editor.getSheets().contains(sheet))
            {
                this.editor.applyValueChange(sheet, write);
            }
            else
            {
                /* Not a row yet, so no value gesture covers it: notify on our own. It gets its row
                 * once the edit is over. */
                sheet.channel.preNotify();
                write.run();
                sheet.channel.postNotify();

                newRows = true;
            }

            changed = true;
        }

        if (changed)
        {
            this.wrote = true;
            this.editor.triggerChange();
        }

        if (newRows && this.rowsChanged != null)
        {
            this.rowsChanged.run();
        }
    }

    /**
     * The edit, run once per tick that has selected keys on that tick's frame, lands on the selected
     * keys only: a bone the edit changed but whose key there isn't selected stays as it is, and no
     * key is made. Fields edit by difference, so every selected key moves by the same amount.
     */
    private void editSelection(Consumer<Pose> edit)
    {
        Map<Float, List<Map.Entry<String, UIKeyframeSheet>>> byTick = new TreeMap<>();

        for (Map.Entry<String, UIKeyframeSheet> entry : this.bones.entrySet())
        {
            for (Object object : entry.getValue().selection.getSelected())
            {
                byTick.computeIfAbsent(((Keyframe) object).getTick(), (t) -> new ArrayList<>()).add(entry);
            }
        }

        boolean changed = false;

        for (Map.Entry<Float, List<Map.Entry<String, UIKeyframeSheet>>> column : byTick.entrySet())
        {
            float tick = column.getKey();
            Pose before = this.frameAt(tick);
            Pose after = before.copy();

            edit.accept(after);

            for (Map.Entry<String, UIKeyframeSheet> bone : column.getValue())
            {
                PoseTransform a = before.transforms.get(bone.getKey());
                PoseTransform b = after.transforms.get(bone.getKey());

                a = a == null ? new PoseTransform() : a;
                b = b == null ? new PoseTransform() : b;

                if (a.equals(b))
                {
                    continue;
                }

                UIKeyframeSheet sheet = bone.getValue();
                Keyframe key = keyAt(sheet, tick);
                PoseTransform value = (PoseTransform) b.copy();

                if (key != null)
                {
                    this.editor.applyValueChange(sheet, () -> key.setValue(value, false));
                    changed = true;
                }
            }
        }

        if (changed)
        {
            this.wrote = true;
            this.editor.triggerChange();
        }
    }

    @Override
    public void setValue(Pose value, Pose before)
    {
        this.edit((pose) -> pose.copy(value));
    }
}
