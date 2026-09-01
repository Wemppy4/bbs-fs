package mchorse.bbs_mod.ui.framework.elements.input.drag;

import mchorse.bbs_mod.utils.Axis;

/**
 * Resolves an edit request to the drag strategy that will drive it. The
 * ray-vs-additive decision lives here, in one place: the uniform scale always
 * takes the additive lever, a two-axis rotate has no ray implementation, and
 * anything without a drag snapshot falls back to additive as well.
 */
public final class DragStrategyFactory
{
    /** The control style of an edit, beyond its operation and axes. */
    public enum Variant
    {
        /** Plain axis or two-axis handle drag. */
        AXIS,
        /** Screen-space (view-plane) translate. */
        SCREEN,
        /** Uniform (three-axis) scale off one lever. */
        UNIFORM_SCALE,
        /** Rotation about the view axis (the outer ring). */
        VIEW,
        /** Free rotation by cursor motion. */
        TRACKBALL,
        /** Free rotation by gripping a point on the ball. */
        ARCBALL;
    }

    private DragStrategyFactory()
    {}

    public static DragStrategy create(DragContext ctx, TransformOp op, Axis axis, Axis axis2, Variant variant)
    {
        boolean rayAllowed = ctx.drag() != null;

        switch (variant)
        {
            case UNIFORM_SCALE:
                /* The ray's lever runs along a single axis, which reads wildly
                 * for a centre grab and makes the scale explode. */
                return new AdditiveDrag(ctx, TransformOp.SCALE, axis, axis2, true);

            case SCREEN:
                return rayAllowed ? new ScreenTranslateDrag(ctx) : new AdditiveDrag(ctx, TransformOp.TRANSLATE, axis, axis2, false);

            case VIEW:
                return rayAllowed ? new ViewRotateDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            case TRACKBALL:
                return rayAllowed ? new TrackballDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            case ARCBALL:
                return rayAllowed ? new ArcballDrag(ctx) : new AdditiveDrag(ctx, TransformOp.ROTATE, axis, axis2, false);

            default:
                break;
        }

        /* A two-axis rotate has no ray implementation, so it stays additive. */
        boolean ray = rayAllowed && (op != TransformOp.ROTATE || axis2 == null);

        if (!ray)
        {
            return new AdditiveDrag(ctx, op, axis, axis2, false);
        }

        switch (op)
        {
            case TRANSLATE:
                return new TranslateDrag(ctx, axis, axis2);
            case SCALE:
                return new ScaleDrag(ctx, axis, axis2);
            default:
                return new RingRotateDrag(ctx, axis);
        }
    }
}
