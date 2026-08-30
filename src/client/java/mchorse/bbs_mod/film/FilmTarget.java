package mchorse.bbs_mod.film;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;

/**
 * What the film editor is editing right now, as one answer instead of a question every
 * consumer asks itself.
 *
 * <p>The cascade is: a selected bone (a pose bone, or a form's own transform, which
 * addresses itself by form path) wins; failing that the form's anchor track; failing that
 * the replay's own placement in the world. It exists because that same cascade used to be
 * re-derived independently by the gizmo's placement pass, its pick pass, the drag builder
 * and the motion path — and a level added to one of them and not the others is not a
 * cosmetic slip: a gizmo that is drawn by one pass and left out of the other is on screen
 * and cannot be clicked.
 *
 * <p>Deliberately film-only. The form and model-block editors host a gizmo too, but they
 * have no replay and no anchor, so folding them in would mean an abstraction shaped by
 * nothing in particular.
 */
public record FilmTarget(FilmTarget.Kind kind, String bone, TransformSpace space)
{
    public enum Kind
    {
        /** Nothing is being edited — no gizmo, no trajectory. */
        NONE,
        /** The replay's own placement: {@code keyframes.x/y/z} and the angles. */
        ROOT,
        /** The form's anchor track, which parents the whole form. */
        ANCHOR,
        /** A bone inside the form, or the form's own transform. {@link #bone} is its path. */
        BONE
    }

    public static final FilmTarget NONE = new FilmTarget(Kind.NONE, null, TransformSpace.LOCAL);

    public static FilmTarget bone(String bone, TransformSpace space)
    {
        return new FilmTarget(Kind.BONE, bone, space == null ? TransformSpace.LOCAL : space);
    }

    public static FilmTarget anchor(TransformSpace space)
    {
        return new FilmTarget(Kind.ANCHOR, null, space == null ? TransformSpace.LOCAL : space);
    }

    public static FilmTarget root(TransformSpace space)
    {
        return new FilmTarget(Kind.ROOT, null, space == null ? TransformSpace.LOCAL : space);
    }

    public boolean is(Kind kind)
    {
        return this.kind == kind;
    }

    public boolean isNone()
    {
        return this.kind == Kind.NONE;
    }

    /** The bone path when this target is a bone, {@code null} otherwise — so a caller that
     *  only deals in bones doesn't have to test the kind first. */
    public String boneOrNull()
    {
        return this.kind == Kind.BONE ? this.bone : null;
    }
}
