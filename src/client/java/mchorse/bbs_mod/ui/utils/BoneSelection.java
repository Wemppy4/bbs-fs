package mchorse.bbs_mod.ui.utils;

/**
 * The bone the animator is currently working on, owned by the editor that shows it.
 *
 * <p>Two things depend on it. First, PERSISTENCE: clicking a body part in the viewport rebuilds
 * the whole form editor from scratch (see {@code UIFormEditor.pickFormBone}), so a panel's own
 * field cannot survive — the freshly built panel reads the bone back from here instead of
 * dropping the selection or snapping to the first bone in the list. Second, CONTINUITY ACROSS
 * TABS: posing a hand, then opening IK or physics, lands on that same hand rather than wherever
 * that tab was left.</p>
 *
 * <p>This used to be a static field shared by the whole mod, which meant the film editor and the
 * form editor silently fought over one bone. It belongs to an {@link IBoneSelectionHost} instead
 * — the editor that outlives the panels showing it. A panel's own {@code selectedBone} field is
 * not a second source of truth: it is what that panel currently displays, always derived from
 * here and written back here when the animator picks something.</p>
 */
public class BoneSelection
{
    private String bone = "";

    public String get()
    {
        return this.bone;
    }

    public void set(String picked)
    {
        this.bone = picked == null ? "" : picked;
    }

    public boolean isEmpty()
    {
        return this.bone.isEmpty();
    }
}
