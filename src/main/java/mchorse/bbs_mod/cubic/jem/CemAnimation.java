package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.Variable;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * the {@link CemParser}, (4) seeds every bone's model variables ({@code <bone>.tx/rx/sx/...}) from the pose the
 * vanilla stage left on it, (5) evaluates the statements in order (assignments mutate the shared variables, so
 * later statements see earlier results — including cross-bone references), (6) writes the bone
 * variables back into each bone's transform, and (7) stores the entity variables back (they are the entity's,
 * shared with its other CEM models — see {@link CemVariables}).</p>
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
 * The seeds are the inverse of each writeback, so an un-driven (or only cross-referenced) bone writes back
 * exactly the pose it came in with.
 *
 * <p>A bone's kind is its place in the <em>file</em>, not in the bone tree. The parser accumulates pivots
 * by file nesting, and the vanilla rig may then hang a part on another part (see
 * {@link JemModelParser}): that part goes parent-relative, while its direct submodels stay direct — a
 * pack writes their positions for the direct mapping, and Fresh Animations' allay carried its head six
 * pixels too high while they were read as deeper ones.</p>
 */
public class CemAnimation
{
    /** The vertical offset CEM applies to top-level parts (entity model origin height). */
    private static final float Y_OFFSET = 24F;

    /**
     * Ticks added to a stand-in's age so a pack does not read it as freshly spawned.
     *
     * <p>Packs gate spawn behaviour on a small age: Fresh Animations' player drives its landing off
     * {@code age < 9}, and its cow waits for {@code age > 60} before it may leave the ground. A stand-in
     * starts counting at zero every time a film loads or a panel opens, so without this every film began
     * with the actor landing and every form editor showed it falling. Past the largest gate the packs
     * use, with room to spare. A real entity keeps its own age, so a mob that truly spawns still settles
     * the way its pack intends.</p>
     */
    private static final int SPAWN_SETTLED = 100;

    /**
     * Ticks of animation run off screen whenever a stand-in's state starts from zero.
     *
     * <p>A pack's {@code var.*} are a running simulation, and zero is not what they hold once an entity
     * has been standing around: Fresh Animations' player keeps {@code var.t_land} at 1 when settled, and
     * from 0 that timer sweeps its landing curve — the actor visibly crouched every time a film was
     * scrubbed and played, because a scrub re-seeds the state. Rather than guess a settled value for each
     * of a pack's hundreds of variables, run the pack's own arithmetic forward until it settles. Two
     * seconds covers the timers that matter ({@code var.t_land} climbs at 1.4 a second, the fall and run
     * drags at 3 to 6); the slow ones settle at zero, which is where they start.</p>
     */
    private static final int WARM_UP_TICKS = 40;

    /**
     * Ticks one warm-up pass covers. Coarser than a frame because a settle only has to arrive, not be
     * watched: the drags are linear in {@code frame_time} and reach the same place, and the followers
     * converge sooner with a longer step. It halves what the warm-up costs, and that cost lands on the
     * frame a film is scrubbed to.
     */
    private static final int WARM_UP_STEP = 2;

    /** Bone hierarchy kinds, which select the position mapping (see the class javadoc). */
    private static final int TOP = 0;
    private static final int SUB1 = 1;
    private static final int SUBN = 2;

    public final CemParser parser;

    /** The model file's name without its extension — {@code cold_cow_baby} — what a vanilla stage is made from. Set by the loader. */
    public String jem = "";

    private final List<Statement> statements = new ArrayList<>();
    private final List<Binding> bindings = new ArrayList<>();

    /** The bindings by bone, and the model's top-level bones, for the visibility walk — see {@link #show}. */
    private final Map<ModelGroup, Binding> byGroup = new HashMap<>();
    private List<ModelGroup> roots = Collections.emptyList();

    /**
     * The {@code var.*}/{@code varb.*} entity variables in a fixed order — the persistent slots of a
     * {@link CemState}. Collected once in {@link #setup}, after every statement has been parsed.
     */
    private final List<Variable> entityVariables = new ArrayList<>();

    /**
     * The file's parts — its top-level entries, one bone each — as opposed to the submodels under them.
     * Which mapping a bone takes is decided against this, so hanging a part on another leaves the
     * kinds of everything under it alone.
     */
    private final Set<ModelGroup> parts = new HashSet<>();

    /**
     * Parts the vanilla rig reparented (see {@link CemHierarchy}): the pack positions them against the
     * parent's rotation point, so they take the deeper-submodel mapping whatever their depth.
     */
    private final Set<ModelGroup> parentRelative = new HashSet<>();

    public CemAnimation()
    {
        this.parser = new CemParser();

        /* Health is the one pair a pack may not read as zero (see setParameters), and a program can be
         * evaluated with no entity at all - a probe, or the 3-argument apply. Seeded once here rather
         * than branched on every frame: with an entity, setParameters writes over it. */
        this.parser.setValue("health", IEntity.FULL_HEALTH);
        this.parser.setValue("max_health", IEntity.FULL_HEALTH);
    }

    /** Note a bone as one of the file's parts — call before {@link #setup}. */
    public void markPart(ModelGroup group)
    {
        this.parts.add(group);
    }

    /** Note a bone as positioned relative to its parent's pivot — call before {@link #setup}. */
    public void markParentRelative(ModelGroup group)
    {
        this.parentRelative.add(group);
    }

    public boolean isEmpty()
    {
        return this.statements.isEmpty();
    }

    /** Whether any statement writes a channel of this bone. */
    public boolean mentions(String bone)
    {
        String prefix = bone + ".";

        for (Statement statement : this.statements)
        {
            if (statement.target.getName().startsWith(prefix))
            {
                return true;
            }
        }

        return false;
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
        this.byGroup.clear();
        this.roots = model.topGroups;

        for (ModelGroup group : model.getAllGroups())
        {
            Binding binding = new Binding(group, kind(group));

            this.bindings.add(binding);
            this.byGroup.put(group, binding);
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

    /**
     * Classify a bone by its place in the file — see {@link #TOP}/{@link #SUB1}/{@link #SUBN}: a part,
     * a part's direct submodel, or anything deeper. A reparented part is parent-relative whatever its
     * depth; a bone under a part is judged against the part, wherever the rig hung that part.
     */
    private int kind(ModelGroup group)
    {
        if (this.parts.contains(group))
        {
            return this.parentRelative.contains(group) ? SUBN : TOP;
        }

        return this.parts.contains(group.parent) ? SUB1 : SUBN;
    }

    /**
     * A state with a store of its own — for an instance with no entity to share one with (a UI preview,
     * a probe). On an entity the animator binds the state to {@link mchorse.bbs_mod.forms.entities.IEntity#getCemVariables()}
     * instead, so the entity's models see each other's variables.
     */
    public CemState createState()
    {
        return new CemState(new CemVariables());
    }

    /** Evaluate the animation for this frame on the given instance state and apply it to the model's bones. */
    public void apply(CemState state, IEntity target, float transition)
    {
        this.apply(state, target, transition, target == null, null);
    }

    /**
     * The same, told explicitly whether this is a preview rather than the world — CEM's {@code is_in_gui}.
     * A preview has no entity of its own, so the caller hands one over that stands in for it; without the
     * flag the two would be the same question and a stand-in would read as the world.
     *
     * <p>{@code status} carries the states the form sets by hand over the entity's own, and may be null
     * for a caller that has no form behind it — a probe, or a model rendered outside a form.</p>
     */
    public void apply(CemState state, IEntity target, float transition, boolean inGui, CemStatus status)
    {
        this.apply(state, target, transition, inGui, status, null);
    }

    /**
     * @param seed the vanilla frame the program starts from ({@link CemVanillaSeed}), or null to start
     *             from the rest pose — a probe, a test, an entity the game has no model for.
     */
    public void apply(CemState state, IEntity target, float transition, boolean inGui, CemStatus status, CemVanillaSeed seed)
    {
        if (this.statements.isEmpty())
        {
            return;
        }

        /* The entity clock is the frame id: constant across the passes of one frame, growing between
         * frames. Without an entity (a UI preview) the state falls back to wall time. */
        double frameTime = state.advance(target == null ? Double.NaN : target.getAge() + transition);

        state.load(this.entityVariables);

        /* Values that just started from zero are not what a settled entity holds - see WARM_UP_TICKS.
         * Only a stand-in: an entity that really spawned should play its pack's spawn animation. */
        if (!state.warmed)
        {
            state.warmed = true;

            if (target != null && target.isStandIn())
            {
                for (int ticksAgo = WARM_UP_TICKS; ticksAgo > 0; ticksAgo -= WARM_UP_STEP)
                {
                    this.evaluate(state, target, transition, inGui, status, seed, WARM_UP_STEP / 20D, -ticksAgo);
                }
            }
        }

        this.evaluate(state, target, transition, inGui, status, seed, frameTime, 0);

        state.store(this.entityVariables);
    }

    /**
     * One pass of the program: parameters in, bones out. {@code ticksAgo} is 0 for the frame being
     * rendered and negative for a warm-up pass, which stands that many ticks before it.
     */
    private void evaluate(CemState state, IEntity target, float transition, boolean inGui, CemStatus status, CemVanillaSeed seed, double frameTime, int ticksAgo)
    {
        this.parser.setValue("frame_time", frameTime);

        /* A warm-up pass must not look like a repeat of the frame before it, or a pack's own
         * "same frame" guard would hold every drag exactly where it started. */
        this.parser.setValue("frame_counter", state.frameCounter + ticksAgo);

        this.parser.setValue("is_in_gui", inGui ? 1 : 0);

        if (target != null)
        {
            this.setParameters(target, transition, status, ticksAgo);
        }

        for (Binding binding : this.bindings)
        {
            binding.reset(seed);
        }

        for (Statement statement : this.statements)
        {
            statement.target.set(statement.expression.get().doubleValue());
        }

        for (Binding binding : this.bindings)
        {
            binding.writeback();
        }

        for (ModelGroup root : this.roots)
        {
            this.show(root, true);
        }
    }

    /**
     * Visibility the way OptiFine reads it: a part written invisible takes its whole subtree with it,
     * and {@code visible_boxes} hides the part's own boxes alone. BBS's flag is per bone — a hidden
     * bone's children still draw — so after the statements the tree is walked and every bone's flag is
     * set from its own two variables and its ancestors'. Fresh Animations' evoker hides {@code arms}
     * while casting, and the crossed arms sit in a submodel of it: drawn, they were a second pair.
     */
    private void show(ModelGroup group, boolean parentShown)
    {
        Binding binding = this.byGroup.get(group);
        boolean shown = parentShown && (binding == null || binding.visible.doubleValue() != 0);

        group.visible = shown && (binding == null || binding.visibleBoxes.doubleValue() != 0);

        for (ModelGroup child : group.children)
        {
            this.show(child, shown);
        }
    }

    /** Feed the entity's render parameters into the parser for this frame. */
    private void setParameters(IEntity target, float transition, CemStatus status, int ticksAgo)
    {
        float headYaw = Lerps.lerp(target.getPrevHeadYaw(), target.getHeadYaw(), transition);
        float bodyYaw = Lerps.lerp(target.getPrevBodyYaw(), target.getBodyYaw(), transition);
        float pitch = Lerps.lerp(target.getPrevPitch(), target.getPitch(), transition);
        float yaw = Lerps.lerp(target.getPrevYaw(), target.getYaw(), transition);
        double age = target.getAge() + transition + ticksAgo + (target.isStandIn() ? SPAWN_SETTLED : 0);

        this.parser.setValue("limb_swing", target.getLimbPos(transition));
        this.parser.setValue("limb_speed", target.getLimbSpeed(transition));
        this.parser.setValue("age", age);
        this.parser.setValue("time", age);
        this.parser.setValue("head_yaw", headYaw - bodyYaw);
        this.parser.setValue("head_pitch", pitch);
        this.parser.setValue("swing_progress", target.getHandSwingProgress(transition));

        /* The position of THIS frame, like the angles above, not the tick's: the packs read it every
         * frame - Fresh Animations' cow takes its vertical speed from pos_y (var.vs = var.position - pos_y)
         * and detects falling by pos_y < var.pre_posy - and the jump/fall pose hangs off that. The tick
         * position stepped twenty times a second, so the pose sawtoothed with it. */
        double x = Lerps.lerp(target.getPrevX(), target.getX(), transition);
        double y = Lerps.lerp(target.getPrevY(), target.getY(), transition);
        double z = Lerps.lerp(target.getPrevZ(), target.getZ(), transition);

        this.parser.setValue("pos_x", x);
        this.parser.setValue("pos_y", y);
        this.parser.setValue("pos_z", z);

        /* The entity rotation is in RADIANS, unlike head_yaw/head_pitch (degrees, the packs torad() them):
         * Fresh Animations unwraps rot_y jumps of ±2π (villager's var.yrot_offset) and turns it into
         * degrees itself (cow's var.tq = todeg(rot_y - var.tr)). Degrees here made a 2° turn read as 115°
         * and every turn reaction slam into its clamp. */
        this.parser.setValue("rot_x", Math.toRadians(pitch));
        this.parser.setValue("rot_y", Math.toRadians(yaw));

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

        /* A name the program does not know reads as zero, and zero is a state of its own, not "unknown":
         * an iron golem written around if(health<=15, ...) posed as dying in every frame, a magma cube
         * divided by sqrt(max_health), and a cat could never sit. Measured over Fresh Animations and its
         * extensions, 56 of their 158 models read at least one of the ten below.
         *
         * The form's own states lie over the entity's rather than replacing them - see CemStatus - so a
         * caller with no form behind it (a probe) is exactly today's behaviour. */
        this.parser.setValue("health", target.getHealth() * (status == null ? 1F : status.health));
        this.parser.setValue("max_health", target.getMaxHealth());
        this.parser.setValue("is_burning", flag(target.isBurning(), status != null && status.burning));
        this.parser.setValue("is_in_lava", flag(target.isInLava(), status != null && status.inLava));
        this.parser.setValue("is_climbing", flag(target.isClimbing(), status != null && status.climbing));
        this.parser.setValue("is_crawling", flag(target.isCrawling(), status != null && status.crawling));
        this.parser.setValue("is_sitting", flag(target.isSitting(), status != null && status.sitting));
        this.parser.setValue("is_tamed", flag(target.isTamed(), status != null && status.tamed));
        this.parser.setValue("is_aggressive", flag(target.isAggressive(), status != null && status.aggressive));
        this.parser.setValue("is_on_shoulder", flag(target.isOnShoulder(), status != null && status.onShoulder));

        this.setHands(target);

        /* BBS has no CEM rules (.properties), so the matched rule is always the first one. */
        this.parser.setValue("rule_index", 0);

        /* OptiFine's "player" is the viewer. The nearest player is exactly that in singleplayer and the
         * sensible stand-in otherwise; with no one around the entity looks at itself. */
        World world = target.getWorld();
        PlayerEntity player = world == null ? null : world.getClosestPlayer(x, y, z, -1D, false);

        if (player != null)
        {
            Vec3d position = player.getLerpedPos(transition);

            this.parser.setValue("player_pos_x", position.x);
            this.parser.setValue("player_pos_y", position.y);
            this.parser.setValue("player_pos_z", position.z);
            this.parser.setValue("player_rot_x", Math.toRadians(player.getPitch(transition)));
            this.parser.setValue("player_rot_y", Math.toRadians(player.getYaw(transition)));
        }
        else
        {
            this.parser.setValue("player_pos_x", x);
            this.parser.setValue("player_pos_y", y);
            this.parser.setValue("player_pos_z", z);
            this.parser.setValue("player_rot_x", Math.toRadians(pitch));
            this.parser.setValue("player_rot_y", Math.toRadians(yaw));
        }
    }

    /**
     * The hands, which CEM asks about by arm rather than by hand. Vanilla thinks in a main hand and an
     * off hand and remembers which arm the main one is; a pack thinks in a left arm and a right one, so
     * the two swap for a left-handed entity — and Fresh Animations' player leans on that all the way
     * through, reading {@code is_right_handed} twenty times to decide which arm anything belongs to.
     */
    private void setHands(IEntity target)
    {
        boolean right = target.isRightHanded();
        boolean swingingMain = target.isSwinging() && !target.isSwingingOffHand();
        boolean swingingOff = target.isSwinging() && target.isSwingingOffHand();

        this.parser.setValue("is_right_handed", right ? 1 : 0);
        this.parser.setValue("is_using_item", target.isUsingItem() ? 1 : 0);
        this.parser.setValue("is_blocking", target.isBlocking() ? 1 : 0);
        this.parser.setValue("is_swinging_right_arm", arm(right, swingingMain, swingingOff));
        this.parser.setValue("is_swinging_left_arm", arm(!right, swingingMain, swingingOff));

        boolean main = held(target, EquipmentSlot.MAINHAND);
        boolean off = held(target, EquipmentSlot.OFFHAND);

        this.parser.setValue("is_holding_item_right", arm(right, main, off));
        this.parser.setValue("is_holding_item_left", arm(!right, main, off));

        this.parser.setValue("move_forward", target.getForwardSpeed());
        this.parser.setValue("move_strafing", target.getSidewaysSpeed());
    }

    /** What is true of an arm: the main hand's when that is the arm, the off hand's otherwise. */
    private static int arm(boolean isMainArm, boolean mainHand, boolean offHand)
    {
        return (isMainArm ? mainHand : offHand) ? 1 : 0;
    }

    private static boolean held(IEntity target, EquipmentSlot slot)
    {
        ItemStack stack = target.getEquipmentStack(slot);

        return stack != null && !stack.isEmpty();
    }

    /** A state the entity is in, or the form says it is in. */
    private static int flag(boolean entity, boolean form)
    {
        return entity || form ? 1 : 0;
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
         * Seed this bone's model variables: from the vanilla frame where it has the part, else from the
         * pose standing in {@code current} — the rest pose, the exact inverse of {@link #writeback()},
         * so a bone no statement mentions writes back exactly what it came in with.
         *
         * <p>The vanilla frame is what makes a statement that reads its own bone work: OptiFine
         * evaluates CEM on top of it, so {@code head.ry} arrives holding the vanilla head yaw. Fresh
         * Moves is written that way throughout — {@code head.ry = wraprad(head.ry)}, {@code
         * right_arm.rx = wraprad(right_arm.rx)} — and against a rest-pose seed those are the identity
         * on zero, which left the player's head and arms frozen; the fox reads the flat body vanilla
         * gives it, the hoglin the fifty degrees vanilla holds its head at.</p>
         */
        public void reset(CemVanillaSeed seed)
        {
            CemVanillaSeed.Part part = seed == null ? null : seed.get(this.group.id);

            if (part != null)
            {
                this.seed(part);

                return;
            }

            Transform current = this.group.current;

            this.resetPosition();

            this.rx.set(-Math.toRadians(current.rotate.x));
            this.ry.set(-Math.toRadians(current.rotate.y));
            this.rz.set(Math.toRadians(current.rotate.z));

            this.sx.set(current.scale.x);
            this.sy.set(current.scale.y);
            this.sz.set(current.scale.z);

            /* Nothing resets what the last frame wrote, so every frame starts from shown. */
            this.visible.set(1);
            this.visibleBoxes.set(1);
        }

        /**
         * The vanilla frame's values for this part — the part's own fields, which is what OptiFine's
         * variables are. The angle, the scale and the flag are vanilla's outright. The position is
         * vanilla's where vanilla's animation placed the part this frame (the blaze's rods, the magma
         * cube's segments) and the rest one — the file's — where it did not; a reparented part is placed
         * against its vanilla parent, a top-level one against the model.
         */
        private void seed(CemVanillaSeed.Part part)
        {
            if (part.moved)
            {
                boolean local = this.kind == SUBN;

                this.tx.set(local ? part.tx : part.ax);
                this.ty.set(local ? part.ty : part.ay);
                this.tz.set(local ? part.tz : part.az);
            }
            else
            {
                this.resetPosition();
            }

            this.rx.set(part.rx);
            this.ry.set(part.ry);
            this.rz.set(part.rz);

            this.sx.set(part.sx);
            this.sy.set(part.sy);
            this.sz.set(part.sz);

            this.visible.set(part.visible ? 1 : 0);
            this.visibleBoxes.set(1);
        }

        /** The position variables from the pose standing in {@code current}: the plain inverse of {@link #writeback()}'s split by kind. */
        private void resetPosition()
        {
            Vector3f translate = this.group.current.translate;
            Vector3f pivot = this.group.initial.translate;

            /* The writeback lays X down mirrored about the pivot; undo that first. */
            float x = 2F * pivot.x - translate.x;

            switch (this.kind)
            {
                case SUB1 ->
                {
                    this.tx.set(-x);
                    this.ty.set(-translate.y);
                    this.tz.set(translate.z);
                }
                case SUBN ->
                {
                    Vector3f parent = this.group.parent.initial.translate;

                    this.tx.set(parent.x - x);
                    this.ty.set(parent.y - translate.y);
                    this.tz.set(translate.z - parent.z);
                }
                default ->
                {
                    this.tx.set(-x);
                    this.ty.set(Y_OFFSET - translate.y);
                    this.tz.set(translate.z);
                }
            }
        }

        public void writeback()
        {
            Transform current = this.group.current;
            Vector3f pivot = this.group.initial.translate;
            float x, y, z;

            switch (this.kind)
            {
                case SUB1 ->
                {
                    x = safe(-this.tx.doubleValue());
                    y = safe(-this.ty.doubleValue());
                    z = safe(this.tz.doubleValue());
                }
                case SUBN ->
                {
                    Vector3f parent = this.group.parent.initial.translate;

                    x = safe(parent.x - this.tx.doubleValue());
                    y = safe(parent.y - this.ty.doubleValue());
                    z = safe(parent.z + this.tz.doubleValue());
                }
                default ->
                {
                    x = safe(-this.tx.doubleValue());
                    y = safe(Y_OFFSET - this.ty.doubleValue());
                    z = safe(this.tz.doubleValue());
                }
            }

            /* BBS lays a bone's X offset down mirrored (ICubicRenderer.translateGroup negates
             * translate.x - pivot.x), so the bone lands at X only when written as X's reflection
             * about the pivot. Y and Z go down as they are; the rest pose is unaffected either way. */
            current.translate.set(2F * pivot.x - x, y, z);

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

            /* Visibility is not written here: it is the tree's, not the bone's — see show(). */
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
