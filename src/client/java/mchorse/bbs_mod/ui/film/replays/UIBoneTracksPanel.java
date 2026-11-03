package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UITrackValue;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.List;

/**
 * The Bones tab's panel: the pose editor (bone list, fields, presets, mirror, fix in place) over
 * the frame under the cursor, keying each bone on its own track — see {@link BoneTracksValue}.
 *
 * <p>It follows the cursor, and the key parameters on top belong to the picked bone's key at the
 * cursor: that key gets selected on the timeline whenever the bone, the frame or the key changes,
 * and with none there the parameters stay grey.</p>
 */
public class UIBoneTracksPanel extends UIPoseKeyframeFactory
{
    public final String formPath;
    private final BoneTracksValue bones;

    private float shownTick = Float.NaN;
    private String shownBone;

    /** The bone was picked by clicking its key on the timeline: whatever the animator selected there stays. */
    public boolean pickedOnTimeline;

    public UIBoneTracksPanel(BoneTracksValue track, UIKeyframes editor, String formPath)
    {
        super(track, editor);

        this.bones = track;
        this.formPath = formPath;
    }

    @Override
    protected UIPoseFactoryEditor createPoseEditor(UIKeyframes editor, UITrackValue<Pose> track)
    {
        BoneTracksValue bones = (BoneTracksValue) track;

        return new UIPoseFactoryEditor(editor, track)
        {
            @Override
            protected BoneSelection boneSelection()
            {
                return bones.selection;
            }

            @Override
            protected void pickBones(List<String> list)
            {
                super.pickBones(list);

                if (bones.picked != null)
                {
                    bones.picked.accept(list);
                }
            }
        };
    }

    /** A key was put down from outside the panel (a right click on the bone): show it and pick it. */
    public void keyed()
    {
        this.bones.wrote = true;
        this.requestUpdate();
    }

    public String getBone()
    {
        return this.poseEditor.groups.list.getCurrentFirst();
    }

    /** The picked bone's own track at the cursor, for the gizmo's additive base. */
    public PoseTransform getBoneValue(String bone)
    {
        return this.bones.getBone(bone);
    }

    public UIKeyframeSheet getBoneSheet(String bone)
    {
        return this.bones.getBoneSheet(bone);
    }

    @Override
    public void render(UIContext context)
    {
        float tick = this.editor.getTick();
        String bone = this.getBone();

        if (!this.isUserEditing())
        {
            boolean moved = Float.compare(tick, this.shownTick) != 0;
            boolean picked = bone == null ? this.shownBone != null : !bone.equals(this.shownBone);

            if (moved)
            {
                this.requestUpdate();
            }

            if (moved || picked || this.bones.wrote)
            {
                this.bones.wrote = false;
                this.shownTick = tick;
                this.shownBone = bone;
                this.pickKeyAtCursor(bone, picked && !this.pickedOnTimeline);
            }
        }

        this.pickedOnTimeline = false;

        super.render(context);
    }

    /**
     * Select the bone's key at the cursor, so the parameters on top are its. A selection of the
     * animator's own (several keys, or a key elsewhere) is left alone unless the bone itself changed.
     */
    private void pickKeyAtCursor(String bone, boolean force)
    {
        IUIKeyframeGraph graph = this.editor.getGraph();
        Keyframe selected = graph.getSelected();
        UIKeyframeSheet selectedSheet = selected == null ? null : graph.getSheet(selected);
        boolean ours = selected == null || (selectedSheet != null && selectedSheet.isBoneTrack && selectedSheet.selection.getSelected().size() == 1);

        if (!force && !ours)
        {
            return;
        }

        UIKeyframeSheet sheet = bone == null ? null : this.bones.getBoneSheet(bone);
        Keyframe key = bone == null ? null : this.bones.getKeyAtCursor(bone);

        if (key != null && this.editor.getSheets().contains(sheet))
        {
            if (selected != key)
            {
                graph.clearSelection();
                sheet.selection.add(key);
                graph.pickKeyframe(key);
            }
        }
        else if (selected != null)
        {
            graph.clearSelection();
        }
    }
}
