package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A live, procedural OptiFine CEM animation: an ordered list of {@code variable = expression}
 * statements that are evaluated every frame and written into the model's bones.
 *
 * <p>Unlike BBS's keyframe {@link mchorse.bbs_mod.cubic.data.animation.Animation}, this is not
 * interpolated — it's a per-frame script. Each frame it (1) feeds render/entity parameters into the
 * {@link CemParser}, (2) resets every bone's model variables ({@code <bone>.tx/rx/sx/...}) to their
 * rest defaults, (3) evaluates the statements in order (assignments mutate the shared variables, so
 * later statements see earlier results — including cross-bone references), then (4) writes the bone
 * variables back into each bone's transform. The {@code var.*}/{@code varb.*} entity variables are NOT
 * reset, so they persist between frames (CEM uses them for smoothing/drag state).</p>
 *
 * <p>The variable-to-transform mapping matches Blockbench's CEM animation editor (the reference
 * implementation, {@code blockbench-plugins/.../cem_template_loader.js}). For every bone the rotation
 * is {@code (-rx, -ry, rz)} (radians) and the scale is {@code (sx, sy, sz)}. The position depends on
 * the bone's place in the hierarchy. Blockbench drives a bone's <em>local</em> mesh position (its
 * origin relative to the parent's origin), whereas BBS stores it as a delta off the bone's absolute
 * pivot {@code O = initial.translate}; converting between the two cancels the parent pivot for a
 * direct submodel but not for a deeper one. With {@code Op = parent.initial.translate}:</p>
 * <ul>
 *     <li><b>Top-level part:</b> {@code translate = (-tx, 24 - ty, tz)} (carries the
 *     {@code Y_OFFSET = 24} entity origin).</li>
 *     <li><b>Direct submodel of a top-level part:</b> {@code translate = (-tx, -ty, tz)}.</li>
 *     <li><b>Deeper submodel:</b> {@code translate = (Op.x - tx, Op.y - ty, Op.z + tz)}.</li>
 * </ul>
 * The rest defaults are the inverse of each writeback, so an un-driven (or only cross-referenced) bone
 * reproduces its rest pose exactly.
 */
public class CemAnimation
{
    /** Debug switch: set false to render CEM models at rest (no procedural animation). */
    public static boolean ENABLED = true;

    /** The vertical offset CEM applies to top-level parts (entity model origin height). */
    private static final float Y_OFFSET = 24F;

    /** Bone hierarchy kinds, which select the position mapping (see the class javadoc). */
    private static final int TOP = 0;
    private static final int SUB1 = 1;
    private static final int SUBN = 2;

    public final CemParser parser;

    private final List<Statement> statements = new ArrayList<>();
    private final List<Binding> bindings = new ArrayList<>();

    /** Per-frame timestamp (entity age in ticks) of the last advanced frame; NaN until the first. */
    private double lastFrameStamp = Double.NaN;
    /** Wall-clock fallback timestamp for when there is no entity clock (e.g. a UI preview). */
    private long lastNanos;
    private int frameCounter;

    public CemAnimation()
    {
        this.parser = new CemParser();
    }

    public boolean isEmpty()
    {
        return this.statements.isEmpty();
    }

    /** Record a {@code variable = expression} statement, preserving evaluation order. */
    public void addStatement(String target, String expression)
    {
        this.statements.add(new Statement(this.parser.getOrCreateVariable(target), this.parser.parseExpression(expression)));
    }

    /** Build the per-bone variable bindings once the model hierarchy is known. */
    public void setup(Model model)
    {
        this.bindings.clear();

        for (ModelGroup group : model.getAllGroups())
        {
            this.bindings.add(new Binding(group, kind(group)));
        }
    }

    /** Classify a bone by its depth in the (flat-rooted) hierarchy — see {@link #TOP}/{@link #SUB1}/{@link #SUBN}. */
    private static int kind(ModelGroup group)
    {
        if (group.parent == null)
        {
            return TOP;
        }

        return group.parent.parent == null ? SUB1 : SUBN;
    }

    /** Evaluate the animation for this frame and apply it to the model's bones. */
    public void apply(IEntity target, float transition)
    {
        if (!ENABLED || this.statements.isEmpty())
        {
            return;
        }

        this.setParameters(target, transition);

        for (Binding binding : this.bindings)
        {
            binding.reset();
        }

        for (Statement statement : this.statements)
        {
            statement.target.set(statement.expression.get().doubleValue());
        }

        for (Binding binding : this.bindings)
        {
            binding.writeback();
        }
    }

    private void setParameters(IEntity target, float transition)
    {
        /* Advance the procedural clock once per RENDERED FRAME, not once per render pass. A model can
         * be drawn several times per frame (main pass, stencil/picking, ...); the entity's age (ticks)
         * plus the partial tick is constant across those passes but strictly grows between frames, so
         * it is the right "frame id". On a repeated pass frame_time is 0 and frame_counter is frozen,
         * which (together with the animations' own frame_counter == var.pre_frame_counter guard) makes
         * the re-evaluation reproduce the exact same pose instead of double-stepping the var.* drags. */
        double frameTime = 0;

        if (target != null)
        {
            double stamp = target.getAge() + transition;

            if (stamp != this.lastFrameStamp)
            {
                if (!Double.isNaN(this.lastFrameStamp))
                {
                    frameTime = Math.max(0D, Math.min(0.5D, (stamp - this.lastFrameStamp) / 20D));
                }

                this.lastFrameStamp = stamp;
                this.frameCounter = (this.frameCounter + 1) % 27720;
            }
        }
        else
        {
            /* No entity clock: fall back to wall time and treat every call as its own frame. */
            long now = System.nanoTime();

            frameTime = this.lastNanos == 0 ? 0 : Math.min(0.5D, (now - this.lastNanos) / 1.0e9D);
            this.lastNanos = now;
            this.frameCounter = (this.frameCounter + 1) % 27720;
        }

        this.parser.setValue("frame_time", frameTime);
        this.parser.setValue("frame_counter", this.frameCounter);

        if (target == null)
        {
            return;
        }

        float headYaw = Lerps.lerp(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
        float bodyYaw = Lerps.lerp(target.getPrevBodyYaw(), target.getBodyYaw(), transition);
        float pitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        double age = target.getAge() + transition;

        this.parser.setValue("limb_swing", target.getLimbPos(transition));
        this.parser.setValue("limb_speed", target.getLimbSpeed(transition));
        this.parser.setValue("age", age);
        this.parser.setValue("time", age);
        this.parser.setValue("head_yaw", headYaw - bodyYaw);
        this.parser.setValue("head_pitch", pitch);
        this.parser.setValue("swing_progress", target.getHandSwingProgress(transition));

        this.parser.setValue("pos_x", target.getX());
        this.parser.setValue("pos_y", target.getY());
        this.parser.setValue("pos_z", target.getZ());
        this.parser.setValue("rot_x", pitch);
        this.parser.setValue("rot_y", Lerps.lerp(target.getPrevYaw(), target.getYaw(), transition));

        this.parser.setValue("is_sneaking", target.isSneaking() ? 1 : 0);
        this.parser.setValue("is_sprinting", target.isSprinting() ? 1 : 0);
        this.parser.setValue("is_on_ground", target.isOnGround() ? 1 : 0);
        this.parser.setValue("is_in_water", target.isTouchingWater() ? 1 : 0);
        this.parser.setValue("is_riding", 0);
        this.parser.setValue("is_child", 0);
        this.parser.setValue("is_alive", 1);
    }

    private record Statement(Variable target, IExpression expression)
    {}

    /** Binds a bone to its eleven CEM model variables and writes them into the bone's transform. */
    private class Binding
    {
        private final ModelGroup group;
        private final int kind;

        private final Variable tx, ty, tz;
        private final Variable rx, ry, rz;
        private final Variable sx, sy, sz;
        private final Variable visible, visibleBoxes;

        public Binding(ModelGroup group, int kind)
        {
            this.group = group;
            this.kind = kind;

            CemParser p = CemAnimation.this.parser;
            String id = group.id;

            this.tx = p.getOrCreateVariable(id + ".tx");
            this.ty = p.getOrCreateVariable(id + ".ty");
            this.tz = p.getOrCreateVariable(id + ".tz");
            this.rx = p.getOrCreateVariable(id + ".rx");
            this.ry = p.getOrCreateVariable(id + ".ry");
            this.rz = p.getOrCreateVariable(id + ".rz");
            this.sx = p.getOrCreateVariable(id + ".sx");
            this.sy = p.getOrCreateVariable(id + ".sy");
            this.sz = p.getOrCreateVariable(id + ".sz");
            this.visible = p.getOrCreateVariable(id + ".visible");
            this.visibleBoxes = p.getOrCreateVariable(id + ".visible_boxes");
        }

        /**
         * Reset this bone's model variables to the values that reproduce its rest pose, so an
         * un-driven or only cross-referenced bone stays put. These are the exact inverse of
         * {@link #writeback()}.
         */
        public void reset()
        {
            Transform initial = this.group.initial;
            Vector3f pivot = initial.translate;

            switch (this.kind)
            {
                case SUB1 ->
                {
                    this.tx.set(-pivot.x);
                    this.ty.set(-pivot.y);
                    this.tz.set(pivot.z);
                }
                case SUBN ->
                {
                    Vector3f parent = this.group.parent.initial.translate;

                    this.tx.set(parent.x - pivot.x);
                    this.ty.set(parent.y - pivot.y);
                    this.tz.set(pivot.z - parent.z);
                }
                default ->
                {
                    this.tx.set(-pivot.x);
                    this.ty.set(Y_OFFSET - pivot.y);
                    this.tz.set(pivot.z);
                }
            }

            /* Rotation defaults invert the writeback so a bone with a rest CEM rotation keeps it. */
            this.rx.set(-Math.toRadians(initial.rotate.x));
            this.ry.set(-Math.toRadians(initial.rotate.y));
            this.rz.set(Math.toRadians(initial.rotate.z));

            this.sx.set(initial.scale.x);
            this.sy.set(initial.scale.y);
            this.sz.set(initial.scale.z);
            this.visible.set(1);
            this.visibleBoxes.set(1);
        }

        public void writeback()
        {
            Transform current = this.group.current;

            switch (this.kind)
            {
                case SUB1 -> current.translate.set(
                    safe(-this.tx.doubleValue()),
                    safe(-this.ty.doubleValue()),
                    safe(this.tz.doubleValue())
                );
                case SUBN ->
                {
                    Vector3f parent = this.group.parent.initial.translate;

                    current.translate.set(
                        safe(parent.x - this.tx.doubleValue()),
                        safe(parent.y - this.ty.doubleValue()),
                        safe(parent.z + this.tz.doubleValue())
                    );
                }
                default -> current.translate.set(
                    safe(-this.tx.doubleValue()),
                    safe(Y_OFFSET - this.ty.doubleValue()),
                    safe(this.tz.doubleValue())
                );
            }

            current.rotate.set(
                safe(-Math.toDegrees(this.rx.doubleValue())),
                safe(-Math.toDegrees(this.ry.doubleValue())),
                safe(Math.toDegrees(this.rz.doubleValue()))
            );

            current.scale.set(
                safeScale(this.sx.doubleValue()),
                safeScale(this.sy.doubleValue()),
                safeScale(this.sz.doubleValue())
            );

            this.group.visible = this.visible.doubleValue() != 0 && this.visibleBoxes.doubleValue() != 0;
        }

        private float safe(double value)
        {
            return Double.isFinite(value) ? (float) value : 0F;
        }

        private float safeScale(double value)
        {
            return Double.isFinite(value) ? (float) value : 1F;
        }
    }
}
