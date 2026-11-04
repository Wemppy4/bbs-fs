package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.api.client.editor.TrackCategory;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.IPosedForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
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

    /**
     * The last bone the panel turned to (with its form). The panel re-picks its bone on every refresh,
     * so only a different one may open what its row sits in — the animator folds the rest.
     */
    private String lastPicked;
    /** A new pick whose row is still coming with the rebuild: open what it sits in once it is there. */
    private boolean revealPending;

    /**
     * The picked bone whose empty row the animator removed (form and bone, as {@link #lastPicked}).
     * It stays picked in the panel but has no row until it gets a key or is picked again.
     */
    private String dismissed;

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
     * {@code keep} lets a found track through.
     */
    public void filter(List<UIKeyframeSheet> sheets, Predicate<UIKeyframeSheet> keep)
    {
        String picked = this.selection.get();

        sheets.removeIf((sheet) ->
        {
            TrackId track = TrackId.parse(sheet.id);

            if (track == null || track.kind() != TrackKind.BONE)
            {
                return false;
            }

            boolean isPicked = track.formPath().equals(this.formPath) && track.subject().equals(picked);
            String key = this.formPath + "|" + picked;

            /* Keyed since its row was removed: it is a track again, like any other. */
            if (isPicked && !sheet.channel.isEmpty() && key.equals(this.dismissed))
            {
                this.dismissed = null;
            }

            boolean shown = isPicked && !key.equals(this.dismissed);

            sheet.highlighted = isPicked;
            sheet.alwaysOpen = true;

            return !(shown || !sheet.channel.isEmpty() || keep.test(sheet));
        });
    }

    /** Hand the new timeline the tab's panel. */
    public void attach(UIKeyframeEditor editor)
    {
        this.editor = editor;
        this.panel = null;
        this.rowsChanged = false;

        editor.panels(this::panel);

        if (this.revealPending)
        {
            this.revealPending = false;

            for (UIKeyframeSheet sheet : editor.view.getSheets())
            {
                if (sheet.highlighted)
                {
                    editor.view.getDopeSheet().unfoldTo(sheet);
                }
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

    /**
     * The panel's list picked bones: light the first one's row. A bone it didn't have picked before
     * is brought into view, getting a row if it has none.
     */
    private void picked(Map<String, UIKeyframeSheet> bones, List<String> list)
    {
        String primary = list.isEmpty() ? null : list.get(0);
        String key = this.formPath + "|" + primary;
        boolean changed = !key.equals(this.lastPicked);

        this.lastPicked = key;

        if (changed)
        {
            this.dismissed = null;
        }

        for (Map.Entry<String, UIKeyframeSheet> entry : bones.entrySet())
        {
            entry.getValue().highlighted = entry.getKey().equals(primary);
        }

        UIKeyframeSheet sheet = primary == null ? null : bones.get(primary);

        if (!changed || this.editor == null)
        {
            return;
        }

        /* The bone picked before may have had a row only for being picked: it goes with the pick. */
        for (UIKeyframeSheet shown : this.editor.view.getSheets())
        {
            if (shown.isBoneTrack && shown != sheet && shown.channel.isEmpty())
            {
                this.rowsChanged = true;
            }
        }

        if (sheet != null && !this.editor.view.getDopeSheet().unfoldTo(sheet))
        {
            this.rowsChanged = true;
            this.revealPending = true;
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

        /* Picked again: a removed row comes back. */
        this.dismissed = null;

        if (insert)
        {
            sheet.channel.preNotify();
            BoneTracksValue.keyToEdit(sheet, this.editor.view.getTick());
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

    /**
     * Offer to take a bone's track away: all its keys go in one step (the pose under it shows through
     * again), and its row with them. An empty row — the picked bone's — goes too: the bone stays
     * picked in the panel and gets its row back with a key or a new pick.
     */
    public void addRemoveAction(ContextMenuManager menu, UIKeyframeSheet sheet, Runnable refresh)
    {
        TrackId track = sheet == null || !sheet.isBoneTrack ? null : TrackId.parse(sheet.id, TrackKind.BONE);

        if (track == null)
        {
            return;
        }

        menu.action(Icons.REMOVE, L10n.lang("bbs.ui.film.replays.bones.remove_track"), () ->
        {
            sheet.selection.clear();

            if (!sheet.channel.isEmpty())
            {
                sheet.channel.removeAll();
            }

            if (track.formPath().equals(this.formPath) && track.subject().equals(this.selection.get()))
            {
                this.dismissed = this.formPath + "|" + track.subject();
            }

            if (this.editor != null)
            {
                this.editor.view.pickKeyframe(null);
            }

            refresh.run();
        });
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
