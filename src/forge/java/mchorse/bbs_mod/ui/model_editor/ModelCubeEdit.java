package mchorse.bbs_mod.ui.model_editor;

import mchorse.bbs_mod.cubic.data.model.ModelCube;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * One edit of the numbers of a pick of cubes, carried from where the cubes stood when it began.
 *
 * <p>The cube rows show one cube of the pick — the leader — and what is dragged into them, or
 * pulled with the gizmo, is a CHANGE, which every cube of the pick takes from its own numbers:
 * cubes two, three and six wide are four, five and eight wide once the leader's width is dragged
 * from two to four. A number TYPED into a row is every cube's number instead — Blockbench's rule
 * for a pick, and the way it reads: typing 4 asks for four. The gizmo shares a change in its own
 * terms ({@link Source}): its scale handles scale, so every cube grows by the leader's FACTOR,
 * about its own pivot, where the rows grow each out of its corner; and its rings turn, so every
 * cube turns by the leader's TURN about its own pivot, where the rows add to each angle — about
 * the same axes, or on a ring of the leader's own frame each about its own, as Blockbench turns a
 * pick.</p>
 *
 * <p>Every number is worked out afresh from the start of the edit on every change rather than
 * nudged from where the last change left it. A drag's many small steps would otherwise pile up
 * rounding on every cube but the leader, and the gizmo's probes — which pull the leader's numbers
 * far off and back before a drag even starts — would leave it behind in the file; from the start,
 * a change that comes back to nothing lands every cube on exactly the numbers it began with. The
 * change is measured against the editor's own numbers as they stood before the edit ({@code from})
 * rather than against the leader cube, for the same reason: the editor keeps its rotation in
 * radians, and a change of nothing must stay nothing rather than a trip through degrees.</p>
 *
 * <p>Only what the edit has changed so far is written. A move of a picked group carries that
 * group's cubes along by itself, and a cube the edit merely holds for its size must not be put back
 * where it started underneath it. One edit changes one thing — a gesture that switches from moving
 * to scaling rewinds first — so the fields never fight; if they ever did, the corner is moved first
 * and grown about the moved pivot second, which is what the two in a row would do.</p>
 */
public class ModelCubeEdit
{
    /** How near flat a side can be before it has no factor to grow by. */
    private static final float FLAT = 1E-4F;

    /**
     * How small a turn is to count as none, in radians. A gesture let go with Escape puts the
     * editor's angles back through degrees, and radians don't make that round trip exactly — a
     * unit in the last place either way, never near this far.
     */
    private static final float HAIR = 1E-5F;

    /** What made a change of the editor's numbers — which decides how the cubes other than the leader take it. */
    public enum Source
    {
        /**
         * A row's pad dragged, scrolled or stepped — and anything else short of the gizmo's gesture,
         * like its probes before one starts, which put every number back as they found it. Every
         * cube by the leader's difference: a size out of its corner, a turn angle by angle, the way
         * the pose editor turns a pick of euler bones.
         */
        ROW(false),

        /** A number typed into a row: every cube to that number. */
        TYPED(false),

        /**
         * The gizmo's gesture: a size by the leader's factor, about each cube's own pivot; a turn by
         * the leader's turn about the same axes — a ring of the group's frame, the world's or the
         * view's, or a free turn. A cube of another group takes the turn in its own group's frame,
         * the way it takes a move: the same axes while the groups are turned alike.
         */
        GIZMO(true),

        /** The gizmo turning on a ring of the leader's own frame: as {@link #GIZMO}, but every cube about its own axes. */
        GIZMO_OWN_AXES(true);

        /** Whether the gizmo made it. */
        public final boolean gizmo;

        Source(boolean gizmo)
        {
            this.gizmo = gizmo;
        }
    }

    /**
     * A cube as the edit found it.
     *
     * @param picked whether its own row is picked, rather than only a group above it
     * @param outermost whether nothing else of the pick carries it — what moves its geometry
     */
    @com.github.bsideup.jabel.Desugar
    private record Start(ModelGroup group, ModelCube cube, boolean picked, boolean outermost, Vector3f origin, Vector3f size, Vector3f pivot, Vector3f rotate, float inflate)
    {}

    private final List<Start> starts = new ArrayList<>();
    private final Set<ModelGroup> groups = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The editor's numbers as they stood before the edit — what its changes are measured from. */
    private final Transform from = new Transform();

    private Start leader;

    /** What the edit has changed so far, each for the rest of it. */
    private boolean moving;
    private boolean resizing;
    private boolean turning;

    /** The pivot row's numbers as last given, starting from the leader's pivot. */
    private final Vector3f pivot = new Vector3f();

    public ModelCubeEdit(Transform from)
    {
        this.from.copy(from);
    }

    /**
     * Take a cube into the edit as it stands now.
     *
     * @param picked whether its own row is picked, rather than only a group above it
     * @param outermost whether nothing else of the pick carries it
     * @param leader whether it is the cube the editor shows
     */
    public ModelCubeEdit add(ModelGroup group, ModelCube cube, boolean picked, boolean outermost, boolean leader)
    {
        Start start = new Start(group, cube, picked, outermost, new Vector3f(cube.origin), new Vector3f(cube.size), new Vector3f(cube.pivot), new Vector3f(cube.rotate), cube.inflate);

        this.starts.add(start);
        this.groups.add(group);

        if (leader)
        {
            this.leader = start;
            this.pivot.set(cube.pivot);
        }

        return this;
    }

    /** How many cubes the edit holds. */
    public int size()
    {
        return this.starts.size();
    }

    /** The groups whose cubes the edit holds — what a changed number leaves to be rebuilt. */
    public Set<ModelGroup> groups()
    {
        return this.groups;
    }

    /**
     * Carry the editor's numbers into the cubes: its corner as a move, its size as a resize, its
     * rotation as a turn — each of them only once it has changed.
     *
     * @param geometry whether a move moves the cubes (corner and pivot) or their pivots alone;
     *                 geometry moves the cubes nothing else of the pick carries, pivots move for
     *                 every cube whose own row is picked
     * @param source what made the change, which decides how the other cubes take it — a typed
     *               corner still moves each cube whole, pivot along
     * @return whether any number of any cube changed
     */
    public boolean carry(Transform standin, boolean geometry, Source source)
    {
        Quaternionf now = quaternion(standin);
        Quaternionf was = quaternion(this.from);
        boolean quaternion = standin.rotationMode == Transform.RotationMode.QUATERNION;
        boolean absolute = source == Source.TYPED;

        this.moving |= !standin.translate.equals(this.from.translate);
        this.resizing |= !standin.scale.equals(this.from.scale);
        this.turning |= quaternion ? !now.equals(was) : !standin.rotate.equals(this.from.rotate);

        if (this.leader == null || (!this.moving && !this.resizing && !this.turning))
        {
            return false;
        }

        Vector3f move = new Vector3f(standin.translate).sub(this.from.translate);
        boolean changed = false;

        for (Start start : this.starts)
        {
            boolean leader = start == this.leader;
            boolean moves = this.moving && (geometry ? start.outermost : start.picked);
            boolean grows = this.resizing && source.gizmo;

            if (moves || grows)
            {
                Vector3f origin = new Vector3f(start.origin);
                Vector3f pivot = new Vector3f(start.pivot);

                if (moves)
                {
                    for (int i = 0; i < 3; i++)
                    {
                        float moved = move.get(i);

                        if (moved == 0F)
                        {
                            continue;
                        }

                        if (!geometry || !(leader || absolute))
                        {
                            pivot.setComponent(i, start.pivot.get(i) + moved);

                            if (geometry)
                            {
                                origin.setComponent(i, start.origin.get(i) + moved);
                            }

                            continue;
                        }

                        /* The leader's corner is the number its row shows, to the bit; a typed corner
                         * is every cube's, each taking its pivot along by however far that is. */
                        float corner = standin.translate.get(i);

                        pivot.setComponent(i, leader ? start.pivot.get(i) + moved : start.pivot.get(i) + (corner - start.origin.get(i)));
                        origin.setComponent(i, corner);
                    }
                }

                if (grows)
                {
                    for (int i = 0; i < 3; i++)
                    {
                        float factor = this.factor(standin, i);

                        if (factor != 1F && Math.abs(start.size.get(i)) >= FLAT)
                        {
                            origin.setComponent(i, pivot.get(i) + (origin.get(i) - pivot.get(i)) * factor);
                        }
                    }
                }

                changed |= set(start.cube.origin, origin);

                if (moves)
                {
                    changed |= set(start.cube.pivot, pivot);
                }
            }

            if (this.resizing)
            {
                changed |= set(start.cube.size, this.resized(start, standin, source));
            }

            if (this.turning)
            {
                changed |= set(start.cube.rotate, this.turned(start, standin, now, was, source));
            }
        }

        return changed;
    }

    /** A cube's size for the editor's: the leader's as shown, the rest by its factor, its difference, or the typed number. */
    private Vector3f resized(Start start, Transform standin, Source source)
    {
        Vector3f size = new Vector3f(start.size);

        for (int i = 0; i < 3; i++)
        {
            if (start == this.leader)
            {
                size.setComponent(i, standin.scale.get(i));

                continue;
            }

            if (source.gizmo)
            {
                float factor = this.factor(standin, i);

                if (factor != 1F)
                {
                    size.setComponent(i, start.size.get(i) * factor);
                }

                continue;
            }

            float grown = standin.scale.get(i) - this.from.scale.get(i);

            /* A cube narrower than the change stops at flat rather than turning inside out; it comes
             * back to its own width with the change, since every number is taken from the start. */
            if (grown != 0F)
            {
                size.setComponent(i, source == Source.TYPED ? standin.scale.get(i) : Math.max(0F, start.size.get(i) + grown));
            }
        }

        return size;
    }

    /**
     * A cube's rotation for the editor's. The leader takes the editor's angles as they are. The rest
     * take the leader's change: from a row, angle by angle, the way the pose editor turns a pick of
     * euler bones (a typed angle is every cube's); from the gizmo — or from an editor switched to
     * quaternions, which has no angles to go by — as one turn composed onto each cube's own
     * rotation, before it for the same axes and after it for each cube's own ({@link Source}), and
     * read back on the branch the cube started on, wound on from where it stands now.
     *
     * <p>A turn within a hair of nothing leaves the numbers as they began, and so does every angle
     * the turn leaves within a hair of its start.</p>
     */
    private Vector3f turned(Start start, Transform standin, Quaternionf now, Quaternionf was, Source source)
    {
        Vector3f rotate = new Vector3f(start.rotate);
        boolean leader = start == this.leader;
        boolean euler = standin.rotationMode == Transform.RotationMode.EULER;

        if (euler && (leader || !source.gizmo))
        {
            for (int i = 0; i < 3; i++)
            {
                float turned = standin.rotate.get(i) - this.from.rotate.get(i);

                if (Math.abs(turned) >= HAIR)
                {
                    rotate.setComponent(i, leader || source == Source.TYPED ? MathUtils.toDeg(standin.rotate.get(i)) : start.rotate.get(i) + MathUtils.toDeg(turned));
                }
            }

            return rotate;
        }

        if (euler ? standin.rotate.equals(this.from.rotate, HAIR) : now.equals(was))
        {
            return rotate;
        }

        Quaternionf own = Matrices.toQuaternionZYXDegrees(start.rotate.x, start.rotate.y, start.rotate.z);
        Quaternionf rotation;

        if (leader)
        {
            rotation = new Quaternionf(now);
        }
        else if (source == Source.GIZMO_OWN_AXES)
        {
            rotation = own.mul(new Quaternionf(was).invert().mul(now));
        }
        else
        {
            rotation = new Quaternionf(now).mul(new Quaternionf(was).invert()).mul(own);
        }

        Vector3f branch = radians(start.rotate);
        Vector3f radians = Matrices.toCompatibleEulerZYXRadians(new Matrix3f().rotation(rotation.normalize()), branch, radians(start.cube.rotate), new Vector3f());

        for (int i = 0; i < 3; i++)
        {
            if (Math.abs(radians.get(i) - branch.get(i)) >= HAIR)
            {
                rotate.setComponent(i, MathUtils.toDeg(radians.get(i)));
            }
        }

        return rotate;
    }

    /** Angles in degrees, in radians. */
    private static Vector3f radians(Vector3f degrees)
    {
        return new Vector3f(MathUtils.toRad(degrees.x), MathUtils.toRad(degrees.y), MathUtils.toRad(degrees.z));
    }

    /**
     * The pivot row's number on one axis: the leader's pivot to it, every other cube's pivot by the
     * same difference — or, typed, to the number too. The other two axes stay as the row last left
     * them.
     *
     * @return whether any pivot changed
     */
    public boolean pivot(int axis, float value, boolean absolute)
    {
        if (this.leader == null)
        {
            return false;
        }

        this.pivot.setComponent(axis, value);

        boolean changed = false;

        for (Start start : this.starts)
        {
            Vector3f pivot = new Vector3f(start.pivot);

            for (int i = 0; i < 3; i++)
            {
                float moved = this.pivot.get(i) - this.leader.pivot.get(i);

                if (moved != 0F)
                {
                    pivot.setComponent(i, start == this.leader || absolute ? this.pivot.get(i) : start.pivot.get(i) + moved);
                }
            }

            changed |= set(start.cube.pivot, pivot);
        }

        return changed;
    }

    /**
     * The inflate row's number: the leader's inflate to it, every other cube's by the same
     * difference — or, typed, to the number too.
     *
     * @return whether any inflate changed
     */
    public boolean inflate(float value, boolean absolute)
    {
        if (this.leader == null)
        {
            return false;
        }

        float grown = value - this.leader.inflate;
        boolean changed = false;

        for (Start start : this.starts)
        {
            float inflate = grown == 0F ? start.inflate : start == this.leader || absolute ? value : start.inflate + grown;

            if (start.cube.inflate != inflate)
            {
                start.cube.inflate = inflate;
                changed = true;
            }
        }

        return changed;
    }

    /** How many times the leader's size on an axis has grown; a leader flat on it has no factor, so 1. */
    private float factor(Transform standin, int axis)
    {
        float was = this.from.scale.get(axis);

        return Math.abs(was) < FLAT ? 1F : standin.scale.get(axis) / was;
    }

    /** The editor's rotation as a quaternion, whichever way it keeps it. */
    private static Quaternionf quaternion(Transform transform)
    {
        return transform.rotationMode == Transform.RotationMode.QUATERNION
            ? new Quaternionf(transform.quat)
            : Matrices.toQuaternionZYXRadians(transform.rotate.x, transform.rotate.y, transform.rotate.z);
    }

    private static boolean set(Vector3f target, Vector3f value)
    {
        if (target.equals(value))
        {
            return false;
        }

        target.set(value);

        return true;
    }
}
