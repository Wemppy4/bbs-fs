package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The Bones tab of a timeline (the film's replays editor, the form's animation state editor): the
 * mode in which an animator keys bones one by one.
 *
 * <ul>
 *     <li>Rows: only bones that have keys, plus the picked one, which is highlighted.</li>
 *     <li>Panel: {@link UIBoneTracksPanel}, the pose of the frame under the cursor, whatever is selected.</li>
 *     <li>A bone click picks the bone, the cursor stays. A right click keys it at the cursor.</li>
 *     <li>A click on a bone's key takes the cursor to it.</li>
 * </ul>
 *
 * <p>The picked bone is the editor's {@link BoneSelection} (shared with every pose panel), plus the
 * form it belongs to, kept here. Both outlive the timeline, which is rebuilt on every change of
 * replay, part or tab.</p>
 */
public class BoneTracksMode
{
    private final BoneSelection selection;

    /** The form (by path) whose bones the panel shows. */
    private String formPath = "";

    /** Every bone track of the shown forms by form path and bone, rows or not. */
    private final Map<String, Map<String, UIKeyframeSheet>> bones = new HashMap<>();

    private UIKeyframeEditor editor;
    private UIBoneTracksPanel panel;
    private boolean rowsChanged;

    public BoneTracksMode(BoneSelection selection)
    {
        this.selection = selection;
    }

    public static boolean isActive(boolean allMode, TrackCategory category)
    {
        return !allMode && category == TrackCategory.BONES;
    }

    /**
     * Remember every bone track of the forms on the timeline. Called with all the sheets, before
     * any filter, since the panel keys bones that have no row yet.
     */
    public void collect(List<UIKeyframeSheet> sheets, String activePart)
    {
        this.bones.clear();
        this.editor = null;
        this.panel = null;

        for (UIKeyframeSheet sheet : sheets)
        {
            TrackId track = sheet.isBoneTrack ? TrackId.parse(sheet.id, TrackKind.BONE) : null;

            if (track != null)
            {
                this.bones.computeIfAbsent(track.formPath(), (k) -> new LinkedHashMap<>()).put(track.subject(), sheet);
            }
        }

        if (!this.bones.containsKey(this.formPath))
        {
            this.formPath = this.bones.containsKey(activePart) || this.bones.isEmpty()
                ? activePart
                : this.bones.keySet().iterator().next();
        }
    }

    /**
     * Keep the rows the tab shows: bones with keys, and the picked bone even before it has any.
     * A limits track goes along with its bone's keys. {@code keep} lets a found track through.
     */
    public void filter(List<UIKeyframeSheet> sheets, Predicate<UIKeyframeSheet> keep)
    {
        String picked = this.selection.get();

        sheets.removeIf((sheet) ->
        {
            TrackId track = TrackId.parse(sheet.id);

            if (track == null || (track.kind() != TrackKind.BONE && track.kind() != TrackKind.BONE_CONSTRAINT))
            {
                return false;
            }

            boolean isPicked = track.formPath().equals(this.formPath) && track.subject().equals(picked);

            if (track.kind() == TrackKind.BONE)
            {
                sheet.highlighted = isPicked;
                sheet.alwaysOpen = true;
            }

            return !(isPicked || !sheet.channel.isEmpty() || keep.test(sheet));
        });
    }

    /** Hand the new timeline the tab's panel and its click jumps. */
    public void attach(UIKeyframeEditor editor)
    {
        this.editor = editor;
        this.panel = null;
        this.rowsChanged = false;

        editor.view.jumpOnClick = (keyframe) ->
        {
            UIKeyframeSheet sheet = editor.view.getGraph().getSheet(keyframe);

            return sheet != null && sheet.isBoneTrack;
        };
        editor.panels(this::panel);

        for (UIKeyframeSheet sheet : editor.view.getSheets())
        {
            if (sheet.highlighted)
            {
                editor.view.getDopeSheet().unfoldTo(sheet);
            }
        }
    }

    /**
     * The panel for the picked key: the bones panel, unless it is a key of another kind (a bone's
     * limits, say), which keeps its own. A bone key picks its bone.
     */
    private UIKeyframeFactory panel(UIKeyframeSheet picked)
    {
        if (picked != null)
        {
            TrackId track = picked.isBoneTrack ? TrackId.parse(picked.id, TrackKind.BONE) : null;

            if (track == null)
            {
                return null;
            }

            if (!track.formPath().equals(this.formPath))
            {
                this.formPath = track.formPath();
                this.panel = null;
            }

            this.selection.set(track.subject());

            if (this.panel != null && !track.subject().equals(this.panel.getBone()))
            {
                this.panel.pickedOnTimeline = true;
                this.panel.poseEditor.selectBone(track.subject());
            }
        }

        if (this.panel == null)
        {
            this.panel = this.createPanel();
        }

        return this.panel;
    }

    private UIBoneTracksPanel createPanel()
    {
        Map<String, UIKeyframeSheet> bones = this.bones.get(this.formPath);

        if (this.editor == null || bones == null || bones.isEmpty())
        {
            return null;
        }

        Form form = UIReplaysEditor.getSheetForm(bones.values().iterator().next());

        if (!(form instanceof IPosedForm posed))
        {
            return null;
        }

        String id = TrackId.property(this.formPath, FormProperties.POSE_PROPERTY).toKey();
        UIKeyframeSheet pose = new UIKeyframeSheet(id, TrackCategory.BONES.label, 0, new KeyframeChannel(id, KeyframeFactories.POSE), posed.getPose(), false);
        BoneTracksValue value = new BoneTracksValue(pose, this.editor.view, bones, this.selection, () -> this.rowsChanged = true);

        value.picked = (list) -> this.picked(bones, list);

        return new UIBoneTracksPanel(value, this.editor.view, this.formPath);
    }

    /** The panel's list picked bones: light the first one's row, and give it one if it has none. */
    private void picked(Map<String, UIKeyframeSheet> bones, List<String> list)
    {
        String primary = list.isEmpty() ? null : list.get(0);

        for (Map.Entry<String, UIKeyframeSheet> entry : bones.entrySet())
        {
            entry.getValue().highlighted = entry.getKey().equals(primary);
        }

        UIKeyframeSheet sheet = primary == null ? null : bones.get(primary);

        if (sheet != null && this.editor != null && !this.editor.view.getDopeSheet().unfoldTo(sheet))
        {
            this.rowsChanged = true;
        }
    }

    /**
     * A bone picked in the viewport: the panel turns to it (to its form first, if it is another
     * part's), and a right click keys it at the cursor as it stands. The cursor stays.
     *
     * @return whether the bone is one of the tab's, i.e. the pick was handled here
     */
    public boolean pick(Form form, String bone, boolean insert, boolean additive)
    {
        String path = FormUtils.getPath(form);
        Map<String, UIKeyframeSheet> bones = this.bones.get(path);
        UIKeyframeSheet sheet = bones == null ? null : bones.get(bone);

        if (sheet == null || this.editor == null)
        {
            return false;
        }

        if (insert)
        {
            sheet.channel.preNotify();
            sheet.ensureKeyframe(this.editor.view.getTick());
            sheet.channel.postNotify();
        }

        if (!this.editor.view.getSheets().contains(sheet))
        {
            this.rowsChanged = true;
        }

        if (!path.equals(this.formPath) || this.panel == null)
        {
            this.formPath = path;
            this.selection.set(bone);
            this.panel = null;
            this.editor.refreshSelection();
        }
        else
        {
            this.panel.poseEditor.selectBone(bone, additive);
        }

        if (insert && this.panel != null)
        {
            this.panel.keyed();
        }

        return true;
    }

    /** Whether some bone needs a row it doesn't have yet. Rebuild the timeline once nothing is being dragged. */
    public boolean takeRowsChanged()
    {
        boolean changed = this.rowsChanged;

        this.rowsChanged = false;

        return changed;
    }

    public boolean hasRowsChanged()
    {
        return this.rowsChanged;
    }

    public UIBoneTracksPanel getPanel()
    {
        return this.panel;
    }
}
