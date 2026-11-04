package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * The bone's own track at the cursor, or null when it has no keys: the value of its key at the
     * cursor when there is one (see {@link #keyAt}), what the track reads there otherwise.
     */
    public PoseTransform getBone(String bone)
    {
        UIKeyframeSheet sheet = this.bones.get(bone);

        if (sheet == null || sheet.channel.isEmpty())
        {
            return null;
        }

        Keyframe key = keyAt(sheet, this.editor.getTick());

        return (PoseTransform) (key != null ? sheet.channel.getFactory().copy(key.getValue()) : sheet.sample(this.editor.getTick()));
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

    /** The key an edit at the cursor lands on: the one already there (see {@link #keyAt}), or a new one. */
    public static Keyframe keyToEdit(UIKeyframeSheet sheet, float tick)
    {
        Keyframe key = keyAt(sheet, tick);

        return key != null ? key : sheet.ensureKeyframe(tick);
    }

    @Override
    public Pose getValue()
    {
        Pose pose = new Pose();

        for (String bone : this.bones.keySet())
        {
            PoseTransform transform = this.getBone(bone);

            if (transform != null)
            {
                pose.transforms.put(bone, transform);
            }
        }

        return pose;
    }

    @Override
    public float getValueTick()
    {
        return this.editor.getTick();
    }

    @Override
    public void edit(Consumer<Pose> edit)
    {
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
            Runnable write = () -> keyToEdit(sheet, tick).setValue(value, false);

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

    @Override
    public void setValue(Pose value, Pose before)
    {
        this.edit((pose) -> pose.copy(value));
    }
}
