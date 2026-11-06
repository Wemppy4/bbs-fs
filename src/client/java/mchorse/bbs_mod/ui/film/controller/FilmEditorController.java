package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmEntityRenderer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

public class FilmEditorController extends BaseFilmController
{
    public UIFilmController controller;

    private int lastTick;
    private boolean fractionalPreview;

    public FilmEditorController(Film film, UIFilmController controller)
    {
        super(film);

        this.controller = controller;
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return this.controller.getActors();
    }

    @Override
    public int getTick()
    {
        return this.controller.panel.getCursor();
    }

    @Override
    protected void updateEntities(int ticks)
    {
        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        int step = ticks - this.lastTick;

        super.updateEntities(ticks);

        this.lastTick = ticks;

        /* Playback and stepping move the cursor one tick at a time. Anything else
         * is a jump in time, and what the forms simulate tick by tick (particles)
         * starts over where the cursor landed instead of carrying on from where
         * it was. Only after the update, so the ticks a jump forward replays
         * (see applyReplay) don't spill into the fresh start */
        if (step != 0 && step != 1)
        {
            for (IEntity entity : this.entities.values())
            {
                if (entity.getForm() != null)
                {
                    entity.getForm().restart();
                }
            }
        }
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (isPlaying && isActor)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        List<String> groups = this.controller.getRecordingGroups();
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (entity != this.controller.getControlled() || (this.controller.isRecording() && this.controller.getRecordingCountdown() <= 0 && groups != null))
        {
            replay.keyframes.apply(ticks, entity, entity == this.controller.getControlled() ? groups : null);
            replay.applyClientActions(ticks, entity, this.film);
        }

        if (entity == this.controller.getControlled() && this.controller.isRecording() && this.controller.panel.getRunner().isRunning())
        {
            replay.keyframes.record(this.controller.panel.getCursor(), entity, groups);
        }

        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        /* Special pausing logic */
        if (!isPlaying && isActor)
        {
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());

            /* Signed, not absolute: only a forward jump has intermediate ticks to replay. This loop is
             * the only thing that grows an actor age while the film is paused, so it IS the simulation
             * clock here - taking the absolute value made a backward scrub advance physics FORWARD by
             * the distance scrubbed, and every scrub back lurched the chains. */
            int diff = ticks - this.lastTick;

            while (diff > 0)
            {
                entity.update();

                if (entity.getForm() != null)
                {
                    entity.getForm().update(entity);
                }

                diff -= 1;
            }
        }
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        boolean current = this.isCurrent(entity) && this.controller.isControlling();

        if (current || this.controller.isPlaying())
        {
            return this.controller.panel.isRunning() ? this.controller.panel.getRunner().getTransition(transition) : transition;
        }

        return this.controller.panel.getRunner().getTransition(0F);
    }

    @Override
    public void startRenderFrame(float transition)
    {
        boolean fractional = this.controller.panel.getRunner().getTransition(0F) != 0F;

        if (this.film != null && !this.controller.isPlaying() && (fractional || this.fractionalPreview))
        {
            /* Seeking inside one tick does not advance the simulation. Sample the actor's
             * movement as well as its form on every frame, without replaying actions. */
            List<Replay> replays = this.film.replays.getList();

            for (int i = 0; i < replays.size(); i++)
            {
                Replay replay = replays.get(i);
                IEntity entity = this.entities.get(replay.getId());

                if (entity != null && entity != this.controller.getControlled() && this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
                {
                    float tick = replay.getTick(this.getTick()) + this.getTransition(entity, transition);

                    replay.keyframes.apply(tick, entity);
                    entity.setPrevX(entity.getX());
                    entity.setPrevY(entity.getY());
                    entity.setPrevZ(entity.getZ());
                    entity.setPrevYaw(entity.getYaw());
                    entity.setPrevHeadYaw(entity.getHeadYaw());
                    entity.setPrevBodyYaw(entity.getBodyYaw());
                    entity.setPrevPitch(entity.getPitch());
                }
            }
        }

        this.fractionalPreview = fractional;
        super.startRenderFrame(transition);
    }

    @Override
    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        return super.canUpdate(i, replay, entity, updateMode)
            || this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FIRST_PERSON
            || !this.isCurrent(entity)
            || !this.controller.orbit.enabled;
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        boolean current = this.isCurrent(entity);

        if (!(this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && current))
        {
            super.renderEntity(context, replay, entity);
        }

        boolean isPlaying = this.controller.isPlaying();
        int ticks = replay.getTick(this.getTick());
        ValueOnionSkin onionSkin = this.controller.getOnionSkin();

        if (entity instanceof StubEntity && onionSkin.enabled.get() && (onionSkin.all.get() || current))
        {
            NavigableSet<Integer> keys = this.getOnionTicks(replay, onionSkin.group.get());

            if (!keys.isEmpty())
            {
                BBSProfiler.begin(BBSProfiler.Timer.ONION);
                this.renderOnion(replay, keys.headSet(ticks, false).descendingSet(), onionSkin.preColor.get(), onionSkin.preFrames.get(), context, entity);
                this.renderOnion(replay, keys.tailSet(ticks, false), onionSkin.postColor.get(), onionSkin.postFrames.get(), context, entity);
                BBSProfiler.end(BBSProfiler.Timer.ONION);

                float tick = ticks + this.getTransition(entity, context.tickDelta());
                replay.keyframes.apply(isPlaying ? ticks : tick, entity);
                Form form = entity.getForm();
                replay.properties.applyProperties(form, tick);

                /* Back on the current tick after the onion excursion. */
                RenderFrame.invalidate();

                if (!isPlaying)
                {
                    entity.setPrevX(entity.getX());
                    entity.setPrevY(entity.getY());
                    entity.setPrevZ(entity.getZ());
                    entity.setPrevYaw(entity.getYaw());
                    entity.setPrevHeadYaw(entity.getHeadYaw());
                    entity.setPrevBodyYaw(entity.getBodyYaw());
                    entity.setPrevPitch(entity.getPitch());
                }
            }
        }
    }

    /**
     * The ticks that get a ghost. A form's pose is keyed on its pose track and on its bone tracks
     * alike, so the pose group, or any one bone, stands for them all and a ghost is a whole column.
     * A form with no such keys shows the poses of the replay's other forms: the character may well
     * be a body part. Any other track is taken on its own.
     */
    private NavigableSet<Integer> getOnionTicks(Replay replay, String group)
    {
        NavigableSet<Integer> ticks = new TreeSet<>();
        TrackId track = TrackId.parse(group);

        if (track != null && !isPoseTrack(track))
        {
            KeyframeChannel channel = replay.properties.get(track);

            if (channel != null)
            {
                addTicks(ticks, channel);

                return ticks;
            }

            track = null;
        }

        String formPath = track == null ? "" : track.formPath();

        for (Map.Entry<TrackId, KeyframeChannel> entry : replay.properties.tracks.entrySet())
        {
            if (isPoseTrack(entry.getKey()) && entry.getKey().formPath().equals(formPath))
            {
                addTicks(ticks, entry.getValue());
            }
        }

        if (ticks.isEmpty())
        {
            for (Map.Entry<TrackId, KeyframeChannel> entry : replay.properties.tracks.entrySet())
            {
                if (isPoseTrack(entry.getKey()))
                {
                    addTicks(ticks, entry.getValue());
                }
            }
        }

        return ticks;
    }

    private static boolean isPoseTrack(TrackId track)
    {
        return track.is(TrackKind.BONE) || (track.is(TrackKind.PROPERTY) && FormProperties.POSE_PROPERTY.equals(track.subject()));
    }

    private static void addTicks(NavigableSet<Integer> ticks, KeyframeChannel channel)
    {
        for (Object keyframe : channel.getKeyframes())
        {
            ticks.add((int) ((Keyframe) keyframe).getTick());
        }
    }

    private void renderOnion(Replay replay, Iterable<Integer> ticks, int color, int frames, WorldRenderContext context, IEntity entity)
    {
        float alpha = Colors.getA(color);

        for (int tick : ticks)
        {
            if (frames <= 0)
            {
                break;
            }

            replay.keyframes.apply(tick, entity);
            Form form = entity.getForm();
            replay.properties.applyProperties(form, (float) tick);

            /* This pass re-poses the live entity for another tick; whatever the frame cache
             * holds for it no longer describes what is about to render. */
            RenderFrame.invalidate();

            FilmEntityRenderer.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, context)
                .color(Colors.setA(color, alpha))
                .transition(0F));

            frames -= 1;
            alpha *= alpha;
        }
    }

    @Override
    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        boolean recording = this.controller.panel.recorder.isRecording();

        /* One question, asked once. The gizmo only ever belongs to the replay being edited,
         * so every other actor in the film gets NONE. */
        FilmTarget target = this.isCurrent(entity) && !recording
            ? this.controller.getEditTarget()
            : FilmTarget.NONE;

        String aBone2 = replay.axesPreview.get() && !recording ? replay.axesPreviewBone.get() : null;

        return super.getFilmControllerContext(context, replay, entity)
            .transition(this.getTransition(entity, context.tickDelta()))
            .gizmoTarget(target)
            .gizmoMask(UIReplaysEditorUtils.getFilmGizmoMask(this.controller.panel, target))
            /* The UI camera is copied later in the frame. Use the view that is
             * actually drawing this pass, before any actor transforms go on it. */
            .gizmoView(context.matrixStack().peek().getPositionMatrix())
            .gizmoViewportHeight(this.controller.panel.preview.getViewport().h)
            .bone2(aBone2, TransformSpace.LOCAL);
    }

    private boolean isCurrent(IEntity entity)
    {
        return entity == this.controller.getCurrentEntity();
    }
}
