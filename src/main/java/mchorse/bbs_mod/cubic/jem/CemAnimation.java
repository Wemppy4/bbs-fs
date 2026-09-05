package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A live, procedural OptiFine CEM animation program: an ordered list of {@code variable = expression}
 * statements that are evaluated every frame and written into the model's bones.
 *
 * <p>Unlike BBS's keyframe {@link mchorse.bbs_mod.cubic.data.animation.Animation}, this is not
 * interpolated — it's a per-frame script. The program (parser, statements, bone bindings) is shared by
 * every instance of the model; what persists between frames for one instance lives in a
 * {@link CemState}, which the owning {@link CemAnimator} keeps. Each frame {@link #apply} (1) advances
 * the state's clock (which may re-seed the state — see {@link CemState#advance}), (2) loads the state's
 * {@code var.*}/{@code varb.*} values into the shared variables, (3) feeds render/entity parameters into
 * the {@link CemParser}, (4) resets every bone's model variables ({@code <bone>.tx/rx/sx/...}) to their
 * rest defaults, (5) evaluates the statements in order (assignments mutate the shared variables, so
 * later statements see earlier results — including cross-bone references), (6) writes the bone
 * variables back into each bone's transform, and (7) stores the entity variables back into the state
 * (CEM uses them for smoothing/drag state).</p>
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
    /** The vertical offset CEM applies to top-level parts (entity model origin height). */
    private static final float Y_OFFSET = 24F;

    /** Bone hierarchy kinds, which select the position mapping (see the class javadoc). */
    private static final int TOP = 0;
    private static final int SUB1 = 1;
    private static final int SUBN = 2;

    public final CemParser parser;

    private final List<Statement> statements = new ArrayList<>();
    private final List<Binding> bindings = new ArrayList<>();

    /**
     * The {@code var.*}/{@code varb.*} entity variables in a fixed order — the persistent slots of a
     * {@link CemState}. Collected once in {@link #setup}, after every statement has been parsed.
     */
    private final List<Variable> entityVariables = new ArrayList<>();

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

    /** Build the per-bone variable bindings and the entity variable list once the model hierarchy is known. */
    public void setup(Model model)
    {
        this.bindings.clear();

        for (ModelGroup group : model.getAllGroups())
        {
            this.bindings.add(new Binding(group, kind(group)));
        }

        this.entityVariables.clear();

        for (Variable variable : this.parser.variables.values())
        {
            if (isEntityVariable(variable.getName()))
            {
                this.entityVariables.add(variable);
            }
        }

        /* A fixed order, so a state's slots mean the same thing however the parser's map iterates. */
        this.entityVariables.sort(Comparator.comparing(Variable::getName));
    }

    /** CEM's entity variables: the only ones that persist between frames. */
    private static boolean isEntityVariable(String name)
    {
        return name.startsWith("var.") || name.startsWith("varb.");
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

    /** A fresh per-instance state for this program; sized by the entity variables, so call it after {@link #setup}. */
    public CemState createState()
    {
        return new CemState(this.entityVariables.size());
    }

    /** Evaluate the animation for this frame on the given instance state and apply it to the model's bones. */
    public void apply(CemState state, IEntity target, float transition)
    {
        if (this.statements.isEmpty())
        {
            return;
        }

        /* The entity clock is the frame id: constant across the passes of one frame, growing between
         * frames. Without an entity (a UI preview) the state falls back to wall time. */
        double frameTime = state.advance(target == null ? Double.NaN : target.getAge() + transition);

        state.load(this.entityVariables);

        this.parser.setValue("frame_time", frameTime);
        this.parser.setValue("frame_counter", state.frameCounter);

        /* No entity means a UI preview - which is what CEM's is_in_gui asks about. */
        this.parser.setValue("is_in_gui", target == null ? 1 : 0);

        if (target != null)
        {
            this.setParameters(target, transition);
        }

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

        state.store(this.entityVariables);
    }

    /** Feed the entity's render parameters into the parser for this frame. */
    private void setParameters(IEntity target, float transition)
    {
        float headYaw = Lerps.lerp(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
        float bodyYaw = Lerps.lerp(target.getPrevBodyYaw(), target.getBodyYaw(), transition);
        float pitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        float yaw = Lerps.lerp(target.getPrevYaw(), target.getYaw(), transition);
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
        this.parser.setValue("rot_y", yaw);

        int hurtTime = target.getHurtTimer();
        int deathTime = target.getDeathTime();

        this.parser.setValue("id", target.getId());
        this.parser.setValue("hurt_time", hurtTime);
        this.parser.setValue("is_hurt", hurtTime > 0 ? 1 : 0);
        this.parser.setValue("death_time", deathTime);
        this.parser.setValue("is_alive", deathTime == 0 ? 1 : 0);

        this.parser.setValue("is_sneaking", target.isSneaking() ? 1 : 0);
        this.parser.setValue("is_sprinting", target.isSprinting() ? 1 : 0);
        this.parser.setValue("is_on_ground", target.isOnGround() ? 1 : 0);
        this.parser.setValue("is_in_water", target.isTouchingWater() ? 1 : 0);
        this.parser.setValue("is_swimming", target.isSwimming() ? 1 : 0);
        this.parser.setValue("is_gliding", target.isFallFlying() ? 1 : 0);
        this.parser.setValue("is_riding", target.isRiding() ? 1 : 0);
        this.parser.setValue("is_ridden", target.isRidden() ? 1 : 0);
        this.parser.setValue("is_child", target.isChild() ? 1 : 0);

        /* BBS has no CEM rules (.properties), so the matched rule is always the first one. */
        this.parser.setValue("rule_index", 0);

        /* OptiFine's "player" is the viewer. The nearest player is exactly that in singleplayer and the
         * sensible stand-in otherwise; with no one around the entity looks at itself. */
        World world = target.getWorld();
        PlayerEntity player = world == null ? null : world.getClosestPlayer(target.getX(), target.getY(), target.getZ(), -1D, false);

        if (player != null)
        {
            Vec3d position = player.getLerpedPos(transition);

            this.parser.setValue("player_pos_x", position.x);
            this.parser.setValue("player_pos_y", position.y);
            this.parser.setValue("player_pos_z", position.z);
            this.parser.setValue("player_rot_x", player.getPitch(transition));
            this.parser.setValue("player_rot_y", player.getYaw(transition));
        }
        else
        {
            this.parser.setValue("player_pos_x", target.getX());
            this.parser.setValue("player_pos_y", target.getY());
            this.parser.setValue("player_pos_z", target.getZ());
            this.parser.setValue("player_rot_x", pitch);
            this.parser.setValue("player_rot_y", yaw);
        }
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
