package mchorse.bbs_mod.film;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.RigBone;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.AnchorResolver;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Baking IK into pose keyframes — Blender's "Bake Action" with visual keying, for the chains
 * of a replay's model form.
 *
 * <p>The film is played out of sight over the range, on scratch entities: the live ones are
 * never touched, so no cursor moves, no sound plays, no actor's body is yanked around. At every
 * sampled tick the form is evaluated the way the render evaluates it — rest, actions, pose, then
 * the IK solve with the film's targets — and each chain bone's solved rotation is written into
 * the bone's own pose track as a keyframe. The key holds what the track already contributes at
 * that tick plus the turn IK added, so the FK pose lands exactly where the solve put it, and with
 * the chains keyed off on the range (the form's {@code ik} track) the film shows the same motion
 * as plain keyframes.</p>
 *
 * <p>Measured, not derived: the solved pose comes out of the same pipeline the render runs, so
 * whatever the solve does — weight, softness, pole, tip rotation, the classic path, a target
 * anchored to another replay — is what gets baked. A chain that stretched is baked too: the
 * telescoping the solve writes outside the pose becomes the key's translation (see {@link
 * #stretchTranslate}). What the procedural actions add underneath is evaluated afresh here, as
 * it is on every playback.</p>
 */
public class IKBake
{
    /** A turn smaller than this (radians) is the solve leaving the bone alone. */
    private static final float MOVED_EPSILON = 1.0E-4F;

    /** A keyframe to lay: the replay's tick and the track's value there. */
    private record Keyed(float tick, PoseTransform value)
    {
    }

    /**
     * Bake the chains ending at {@code tips} of the replay's model form into pose keyframes, one
     * every {@code step} ticks from {@code start} to {@code end} inclusive; with {@code disable}
     * the chains are keyed off on that range. Returns false when there is nothing to sample
     * against — no world, no model, no chain of those names.
     */
    public static boolean bake(Film film, Replay replay, Collection<String> tips, int start, int end, int step, boolean disable)
    {
        World world = MinecraftClient.getInstance().world;

        if (film == null || replay == null || world == null || tips == null || tips.isEmpty()
            || !(replay.form.get() instanceof ModelForm form))
        {
            return false;
        }

        ModelInstance instance = ModelFormRenderer.getModel(form);

        if (instance == null || instance.model == null)
        {
            return false;
        }

        Map<String, List<String>> chains = ModelIKRuntime.getChains(instance.model, form);
        LinkedHashSet<String> bones = new LinkedHashSet<>();
        List<String> baked = new ArrayList<>();

        for (String tip : tips)
        {
            List<String> chain = chains.get(tip);

            if (chain != null)
            {
                bones.addAll(chain);
                baked.add(tip);
            }
        }

        if (bones.isEmpty())
        {
            return false;
        }

        if (end < start)
        {
            int swap = start;

            start = end;
            end = swap;
        }

        final int first = Math.max(0, start);
        final int last = end;
        final int every = Math.max(1, step);

        Sampler sampler = Sampler.create(film, replay, world);

        if (sampler == null)
        {
            return false;
        }

        Map<String, List<Keyed>> keys = new LinkedHashMap<>();
        Set<String> moved = new HashSet<>();

        for (String bone : bones)
        {
            keys.put(bone, new ArrayList<>());
        }

        /* Stepped from the film's first tick even when the range starts later: the actions'
         * state (which one plays, how far it has faded) is what playback from the start has. */
        for (int tick = 0; tick <= last; tick++)
        {
            sampler.step(tick);

            if (tick >= first && (tick - first) % every == 0)
            {
                sampler.sample(tick, bones, keys, moved);
            }
        }

        /* The samples dropped the frame's caches as they went; what the render reads next is its own. */
        RenderFrame.invalidate();

        /* Read before the bake's own keys extend it: where the replay's authored motion ends. */
        float lastKeyframe = replay.getLastKeyframeTick();

        BaseValue.edit(replay.properties, (properties) ->
        {
            for (Map.Entry<String, List<Keyed>> entry : keys.entrySet())
            {
                /* A bone the solve never turned — the auto-tail marker, a tip without tip rotation,
                 * a fully locked joint — keeps its FK as it is; a track of keys saying so is noise. */
                if (!moved.contains(entry.getKey()))
                {
                    continue;
                }

                KeyframeChannel<PoseTransform> channel = properties.getOrCreate(TrackId.bone("", entry.getKey()));

                for (Keyed keyed : entry.getValue())
                {
                    channel.insertInheriting(keyed.tick(), keyed.value());
                }
            }

            if (disable)
            {
                disableChains(properties, form, baked, first, last, lastKeyframe);
            }
        });

        return true;
    }

    /**
     * Key the chains off on the range through the form's {@code ik} track — the film's own switch,
     * undone with the keys — so the baked keyframes show instead of the solve. Every key already
     * inside the range switches the chains off too. A range that stops while the replay's motion
     * goes on ({@code lastKeyframe} lies past it) is followed by a key restoring what the track had
     * there, so IK carries on over what was not baked; a range reaching the end of the motion
     * leaves the chains off for good — the bake replaced them, as Blender's does.
     */
    private static void disableChains(FormProperties properties, ModelForm form, List<String> tips, int start, int end, float lastKeyframe)
    {
        KeyframeChannel<IKControls> channel = properties.getOrCreate(TrackId.ikControls(""));

        /* Both ends are read before either is written: the key at the start changes what the
         * track reads everywhere after it. */
        IKControls atStart = controlsAt(channel, form, start);
        IKControls afterEnd = end < lastKeyframe ? controlsAt(channel, form, end + 1) : null;

        for (Keyframe<IKControls> keyframe : channel.getKeyframes())
        {
            float tick = keyframe.getTick();

            if (tick > start && tick <= end)
            {
                switchOff(keyframe.getValue(), tips);
            }
        }

        switchOff(atStart, tips);
        channel.insertInheriting(start, atStart);

        if (afterEnd != null)
        {
            channel.insertInheriting(end + 1, afterEnd);
        }
    }

    /** What the {@code ik} track reads at the tick: its interpolation, or the form's own settings before any keyframe. */
    private static IKControls controlsAt(KeyframeChannel<IKControls> channel, ModelForm form, float tick)
    {
        KeyframeSegment<IKControls> segment = channel.find(tick);

        return segment == null ? TrackCatalog.ikControls(form) : segment.createInterpolated();
    }

    private static void switchOff(IKControls controls, List<String> tips)
    {
        for (String tip : tips)
        {
            controls.get(tip).enabled = false;
        }
    }

    /**
     * A bone's FK state before the solve: its euler channels (radians, whichever unit the
     * skeleton keeps), its evaluated rotation, and whether the pose was the first layer to
     * compose that rotation — see {@link #applyDelta} for why that decides the arithmetic.
     */
    private record Captured(Vector3f channels, Quaternionf evaluated, boolean additive)
    {
        static Captured of(RigBone bone)
        {
            Quaternionf orient = bone.getOrient();
            boolean additive = orient == null || sameRotation(orient, bone.orientFromEuler(bone.getBoneTransform().rotate));

            return new Captured(bone.getChannelRotation(new Vector3f()), bone.evaluatedRotation(), additive);
        }
    }

    /**
     * Turn the track's value so the bone lands on the solved rotation. Which arithmetic does it
     * depends on how the render composes the pose onto the bone:
     *
     * <ul>
     * <li>a euler pose that is the FIRST layer to compose the bone's orientation is added to the
     * channels angle by angle (rest + actions + pose is one sum, and the orientation is built from
     * the sum), so the key gets the euler difference between the solved rotation and the channels,
     * read on the channels' own branch;</li>
     * <li>a euler pose over a layer that already composed the orientation (an action's post pass)
     * multiplies in as a quaternion, so the delta turns the pose's whole rotation — static and
     * tracks together — and the key gets what changed in it;</li>
     * <li>a quaternion pose always multiplies, and the track's own value is its last factor.</li>
     * </ul>
     *
     * <p>{@code delta} is the solve's turn in the bone's own frame, {@code conj(evaluated) ·
     * solved}, so in every case the composed rotation ends up exactly at {@code solved}.</p>
     */
    private static void applyDelta(PoseTransform value, PoseTransform total, Captured fk, Quaternionf delta, Quaternionf solved)
    {
        if (total != null && total.rotationMode == Transform.RotationMode.QUATERNION)
        {
            if (value.rotationMode == Transform.RotationMode.QUATERNION)
            {
                value.quat.mul(delta);
            }
            else
            {
                Quaternionf turned = Matrices.toLocalRotationZYXRadians(value.rotate).mul(delta);

                Matrices.toCompatibleEulerZYXRadians(turned, value.rotate, value.rotate);
            }

            return;
        }

        if (fk.additive())
        {
            Vector3f target = Matrices.toCompatibleEulerZYXRadians(solved, fk.channels(), new Vector3f());

            value.rotate.add(target.sub(fk.channels()));

            return;
        }

        Vector3f base = total == null ? new Vector3f() : total.rotate;
        Quaternionf turned = Matrices.toLocalRotationZYXRadians(base).mul(delta);
        Vector3f after = Matrices.toCompatibleEulerZYXRadians(turned, base, new Vector3f());

        value.rotate.add(after.sub(base));
    }

    /**
     * The pose translation that puts the bone where its stretch offset put it — the same
     * displacement, moved from the solve's transient channel into the pose's.
     *
     * <p>A cubic bone's offset is already the local step in its parent's frame, in blocks, applied
     * right before the pose translation in the same frame (see {@code ICubicRenderer}); the pose
     * channel is pixels with X mirrored, so it is only a change of units. A BOBJ bone's offset is
     * the chain's CUMULATIVE shift in model space, laid on the skinning matrix alone; the bone's own
     * share is what is left after its parent's, brought into the frame the bone translates in and
     * divided by that frame's scale — the very arithmetic the solver's cubic branch does when it
     * writes the offset. From then on the shift travels down the hierarchy like any translation,
     * which is what a pose translation does anyway.</p>
     *
     * @param frames the solved pivot frames, needed for a BOBJ bone; null when it is not one
     */
    private static Vector3f stretchTranslate(RigBone bone, Vector3f offset, Map<String, PivotFrame> frames)
    {
        if (!bone.usesWorldStretchOffset())
        {
            return new Vector3f(-offset.x, offset.y, offset.z).mul(16F);
        }

        PivotFrame frame = frames == null ? null : frames.get(bone.getBoneName());

        if (frame == null)
        {
            return null;
        }

        Vector3f share = new Vector3f(offset);
        RigBone parent = bone.getParentBone();

        if (parent != null && parent.getOffset() != null)
        {
            share.sub(parent.getOffset());
        }

        Vector3f local = new Quaternionf(frame.parentRotation()).conjugate().transform(share);
        Vector3f scale = frame.scale();

        if (scale != null)
        {
            local.set(divide(local.x, scale.x), divide(local.y, scale.y), divide(local.z, scale.z));
        }

        return local;
    }

    private static float divide(float value, float by)
    {
        return Math.abs(by) < 1.0E-6F ? value : value / by;
    }

    private static boolean sameRotation(Quaternionf a, Quaternionf b)
    {
        return Math.abs(a.dot(b)) >= 1F - 1.0E-6F;
    }

    /** The angle of a rotation, radians. */
    private static float turn(Quaternionf rotation)
    {
        float w = Math.min(1F, Math.abs(new Quaternionf(rotation).normalize().w));

        return 2F * (float) Math.acos(w);
    }

    /**
     * The film played out of sight: a scratch entity per replay, stepped tick by tick the way the
     * film controller steps the live ones — minus everything a tick does to the world (sounds,
     * particles, the actors' bodies) — and posed by the replays' tracks at every sampled tick, so
     * a target anchored to another replay's bone finds that bone where the frame would have it.
     */
    private static final class Sampler
    {
        private final Film film;
        private final Replay replay;

        /** Scratch entities by replay id, in the film's order — the same shape the controller keeps. */
        private final Map<String, IEntity> entities = new LinkedHashMap<>();
        private final AnchorResolver anchors = (anchor, transition) -> BaseFilmController.resolveAnchor(this.entities, anchor, transition);

        private IEntity entity;
        private ModelFormRenderer renderer;

        private Sampler(Film film, Replay replay)
        {
            this.film = film;
            this.replay = replay;
        }

        /** Null when the replay has no entity to sample — disabled, or its form has no model renderer. */
        static Sampler create(Film film, Replay replay, World world)
        {
            Sampler sampler = new Sampler(film, replay);

            for (Replay other : film.replays.getList())
            {
                if (!other.enabled.get())
                {
                    continue;
                }

                StubEntity entity = new StubEntity(world);

                entity.setForm(FormUtils.copy(other.form.get()));
                sampler.entities.put(other.getId(), entity);

                if (other == replay)
                {
                    sampler.entity = entity;
                }
            }

            if (sampler.entity == null || !(FormUtilsClient.getRenderer(sampler.entity.getForm()) instanceof ModelFormRenderer renderer))
            {
                return null;
            }

            sampler.renderer = renderer;

            return sampler;
        }

        /**
         * One tick of the film for every scratch entity: the entity advances, then the keyframes
         * place it. Only the baked form's animator ticks along — the other forms just stand where
         * their bones are needed, the way the paused editor holds them, and a body part of theirs
         * that emits into the world (vanilla particles) must not start doing so from a copy.
         */
        void step(int tick)
        {
            for (Replay other : this.film.replays.getList())
            {
                IEntity entity = this.entities.get(other.getId());

                if (entity == null)
                {
                    continue;
                }

                entity.update();

                if (entity == this.entity)
                {
                    entity.getForm().update(entity);
                }

                other.keyframes.apply(other.getTick(tick), entity);
            }
        }

        /**
         * Pose the baked form at the tick as a rendered frame would, solve its IK, and record
         * for every chain bone the track value that reproduces the solved rotation and stretch.
         */
        void sample(int tick, Set<String> bones, Map<String, List<Keyed>> keys, Set<String> moved)
        {
            /* Nothing cached by the last sample may answer for this one: the entities moved
             * without the version accounting seeing it (the onion skin's reason, see RenderFrame). */
            RenderFrame.invalidate();

            /* This frame's tracks, every replay in the film's order as a rendered frame lays them,
             * the baked one last so its targets find the others already standing at this tick. */
            for (Replay other : this.film.replays.getList())
            {
                if (other != this.replay)
                {
                    this.applyTracks(other, tick);
                }
            }

            this.applyTracks(this.replay, tick);

            /* Resolved before the form is posed: an anchor's frame is another form's evaluation,
             * which re-poses a model this form may share. */
            Matrix4f world = this.entityWorld();
            Form root = this.entity.getForm();
            int ticks = this.replay.getTick(tick);

            root.applyStates(0F);

            ModelInstance model = this.renderer.evaluateChannels(this.entity, 0F);

            if (model != null)
            {
                Pose pose = this.renderer.getPose();
                Map<String, Captured> before = new HashMap<>();

                for (String bone : bones)
                {
                    RigBone rig = model.model.getBone(bone);

                    if (rig != null)
                    {
                        before.put(bone, Captured.of(rig));
                    }
                }

                this.renderer.solveIK(model, world, 0F);

                /* The solved pivot frames, collected once and only when a BOBJ bone stretched —
                 * its share has to be brought into its own frame (see stretchTranslate). */
                Map<String, PivotFrame> frames = null;

                for (Map.Entry<String, Captured> entry : before.entrySet())
                {
                    String bone = entry.getKey();
                    Captured fk = entry.getValue();
                    RigBone rig = model.model.getBone(bone);
                    Quaternionf solved = rig.evaluatedRotation();
                    Quaternionf delta = new Quaternionf(fk.evaluated()).conjugate().mul(solved);

                    if (turn(delta) > MOVED_EPSILON)
                    {
                        moved.add(bone);
                    }

                    PoseTransform value = this.trackValue(bone, ticks);

                    applyDelta(value, pose.get(bone), fk, delta, solved);

                    Vector3f offset = rig.getOffset();

                    if (offset != null && offset.lengthSquared() > MOVED_EPSILON * MOVED_EPSILON)
                    {
                        if (rig.usesWorldStretchOffset() && frames == null)
                        {
                            frames = new HashMap<>();
                            ModelPivotFrames.collect(model.model, bones, frames, null);
                        }

                        Vector3f shift = stretchTranslate(rig, offset, frames);

                        if (shift != null)
                        {
                            value.translate.add(shift);
                            moved.add(bone);
                        }
                    }

                    keys.get(bone).add(new Keyed(ticks, value));
                }
            }

            root.unapplyStates();
        }

        /** The frame's overrides dropped and the replay's tracks laid over its scratch form, as the controller does per frame. */
        private void applyTracks(Replay other, int tick)
        {
            IEntity entity = this.entities.get(other.getId());
            Form root = entity == null ? null : entity.getForm();

            if (root == null)
            {
                return;
            }

            TrackBehaviours.clearOverrides(root);
            other.properties.apply(TrackContext.frame(root, 0F, this.anchors), other.getTick(tick), 1F);
        }

        /** Where the film stands the baked entity, the way {@link FilmEntityRenderer} composes it against the world origin. */
        private Matrix4f entityWorld()
        {
            if (this.replay.relative.get())
            {
                Vector3d origin = this.replay.getRelativeOrigin();

                return FilmMatrices.getMatrixForRenderWithRotation(this.entity, origin.x, origin.y, origin.z, 0F);
            }

            Matrix4f base = FilmMatrices.getMatrixForRenderWithRotation(this.entity, 0D, 0D, 0D, 0F);
            Pair<Matrix4f, Float> pair = FilmMatrices.getTotalMatrix(this.entities, this.entity.getForm().anchor.get(), base, 0D, 0D, 0D, 0F, 0, false, null);

            return pair.a != null ? pair.a : base;
        }

        /** What the bone's own track contributes at the tick — its interpolation, or nothing at all without one. */
        private PoseTransform trackValue(String bone, int tick)
        {
            KeyframeChannel<PoseTransform> channel = this.replay.properties.get(TrackId.bone("", bone));
            KeyframeSegment<PoseTransform> segment = channel == null ? null : channel.find(tick);

            return segment == null ? new PoseTransform() : segment.createInterpolated();
        }
    }
}
