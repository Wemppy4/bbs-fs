package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.cubic.physics.WindControl;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.items.FoldState;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.forms.forms.utils.FormMaterial;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIReplaysEditorUtils
{
    private static final int BONE_TRACK_HUE_COUNT = 12;

    /**
     * Key the pose at the tick, following what the timeline is showing: a pose track with its limbs
     * unfolded is keyed limb by limb, a folded one is keyed as a whole pose. The per-limb tracks are
     * where the pose actually lives once it has been split, but keying every bone of a form the user
     * has folded away (or never split at all) buries the timeline in keyframes nobody asked for.
     *
     * @param expandedPoseIds which pose tracks are unfolded, from {@link UIReplaysEditor#getExpandedPoseTabIds()}.
     */
    public static void insertPoseKeyframesAtTick(Replay replay, float tick, FoldState<String> expandedPoseIds)
    {
        if (replay == null)
        {
            return;
        }

        FoldState<String> expanded = expandedPoseIds == null ? new FoldState<>() : expandedPoseIds;
        Form form = replay.form.get();

        BaseValue.edit(replay.properties, (props) ->
        {
            for (Map.Entry<TrackId, KeyframeChannel> entry : props.tracks.entrySet())
            {
                TrackId track = entry.getKey();
                KeyframeChannel<?> channel = entry.getValue();

                if (track.is(TrackKind.BONE))
                {
                    if (expanded.isExpanded(poseTrackIdOf(track)))
                    {
                        insertPoseTransformKeyframe((KeyframeChannel<PoseTransform>) channel, tick, null);
                    }
                }
                else if (isWholePoseTrack(track, channel) && !expanded.isExpanded(track.toKey()))
                {
                    insertWholePoseKeyframe(form, (KeyframeChannel<Pose>) channel, tick);
                }
            }
        });
    }

    /** The pose track a per-limb track hangs under - the same id {@code flushForm} groups them by. */
    private static String poseTrackIdOf(TrackId track)
    {
        return TrackId.property(track.formPath(), FormProperties.POSE_PROPERTY).toKey();
    }

    /** The form's own pose track, as opposed to a per-limb one or a pose overlay. */
    private static boolean isWholePoseTrack(TrackId track, KeyframeChannel<?> channel)
    {
        return track.is(TrackKind.PROPERTY)
            && track.subject().equals(FormProperties.POSE_PROPERTY)
            && channel.getFactory() == KeyframeFactories.POSE;
    }

    private static void insertWholePoseKeyframe(Form form, KeyframeChannel<Pose> channel, float tick)
    {
        KeyframeSegment<Pose> segment = channel.find(tick);
        Pose pose;

        if (segment != null)
        {
            pose = segment.createInterpolated();
        }
        else
        {
            /* Nothing on the track yet, so the pose the form is standing in is the one to keep -
             * an empty keyframe would snap every bone to the factory defaults instead. */
            BaseValue property = form == null ? null : FormUtils.getProperty(form, channel.getId());
            Object value = property instanceof BaseValueBasic basic ? basic.get() : null;

            pose = value instanceof Pose formPose
                ? channel.getFactory().copy(formPose)
                : channel.getFactory().createEmpty();
        }

        int index = channel.insert(tick, pose);
        Keyframe<Pose> keyframe = channel.get(index);
        Keyframe<Pose> template = segment != null ? segment.a : null;

        if (template != null && template != keyframe)
        {
            keyframe.copyOverExtra(template);
        }
    }

    /**
     * Turn a form's catalog into timeline rows, hanging the tracks that fold under another one off
     * their parent row. The catalog decides what exists and where it sits; this only builds widgets,
     * which is why both timelines — a replay's and an animation state's — go through it.
     */
    public static void buildSheets(List<TrackDescriptor> catalog, List<UIKeyframeSheet> sheets)
    {
        Map<TrackId, UIKeyframeSheet> rows = new HashMap<>();

        for (TrackDescriptor track : catalog)
        {
            UIKeyframeSheet sheet = new UIKeyframeSheet(track);

            rows.put(track.id(), sheet);
            sheets.add(sheet);
        }

        for (TrackDescriptor track : catalog)
        {
            if (track.parent() != null)
            {
                rows.get(track.id()).setParent(rows.get(track.parent()));
            }
        }
    }

    /**
     * Make the tree of rows agree with the list of them, after a tab or a filter has taken rows out.
     * A row that left is still listed among its parent's children and still points back at its
     * parent, so {@link UIKeyframeSheet#children} would answer for a timeline other than this one:
     * a section would fold rows that are not there, and the summary it draws would count keyframes
     * this timeline is not showing.
     *
     * <p>Three things follow from a row leaving, and each can cause the next, so they settle
     * together rather than in a fixed order: its parent forgets it; a header left with nothing under
     * it names nothing and leaves as well (which is what the loop is for — a part holding only parts
     * empties one level at a time); and a row whose parent left is cut loose instead of dropped,
     * because a row pointing at a parent that is not there would be folded away with no arrow left
     * to unfold it.</p>
     */
    public static void pruneTree(List<UIKeyframeSheet> sheets)
    {
        boolean removed = true;

        while (removed)
        {
            Set<UIKeyframeSheet> present = new HashSet<>(sheets);

            for (UIKeyframeSheet sheet : sheets)
            {
                sheet.children.removeIf((child) -> !present.contains(child));
            }

            removed = sheets.removeIf((sheet) -> sheet.header && sheet.children.isEmpty());
        }

        Set<UIKeyframeSheet> present = new HashSet<>(sheets);

        for (UIKeyframeSheet sheet : sheets)
        {
            if (sheet.parent != null && !present.contains(sheet.parent))
            {
                sheet.parent = null;
            }
        }
    }

    /**
     * Run an edit over the keyframes it should land on: every selected keyframe of every track
     * that holds this kind of value, or &mdash; with auto-keyframing on &mdash; the keyframe each
     * of those tracks has at the playhead, brought into being from the interpolated value if the
     * track has none there yet.
     *
     * <p>This is the one place the film's value editors (pose, transform, IK, physics, wind...)
     * decide <em>which</em> keyframe they write into, so auto-keyframing reaches all of them
     * without any of them knowing about it.
     */
    public static <T> void forEachSelectedKeyframe(UIKeyframes editor, Keyframe<?> keyframe, Consumer<Keyframe<T>> consumer)
    {
        if (editor == null || keyframe == null)
        {
            return;
        }

        Integer tick = editor.getAutoKeyframeTick();

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel.getFactory() != keyframe.getFactory() || sheet.header)
            {
                continue;
            }

            if (tick == null)
            {
                for (Keyframe selected : sheet.selection.getSelected())
                {
                    consumer.accept((Keyframe<T>) selected);
                }
            }
            else if (!sheet.selection.getSelected().isEmpty())
            {
                Keyframe<T> target = sheet.ensureKeyframe(tick);

                if (target != null)
                {
                    consumer.accept(target);
                }
            }
        }
    }

    private static void insertPoseTransformKeyframe(KeyframeChannel<PoseTransform> channel, float tick, PoseTransform value)
    {
        KeyframeSegment<PoseTransform> segment = channel.find(tick);
        PoseTransform poseTransform = value == null
            ? segment != null ? segment.createInterpolated() : new PoseTransform()
            : (PoseTransform) value.copy();

        int index = channel.insert(tick, poseTransform);
        Keyframe<PoseTransform> keyframe = channel.get(index);
        Keyframe<PoseTransform> template = segment != null ? segment.a : null;

        if (template != null && template != keyframe)
        {
            keyframe.copyOverExtra(template);
        }
    }

    public static UIPropTransform getEditableTransform(UIKeyframeEditor editor)
    {
        if (editor == null || editor.editor == null)
        {
            return null;
        }

        if (editor.editor instanceof UITransformKeyframeFactory transformKeyframeFactory)
        {
            return transformKeyframeFactory.transform;
        }
        else if (editor.editor instanceof UIAnchorKeyframeFactory keyframeFactory)
        {
            return keyframeFactory.transform;
        }
        else if (editor.editor instanceof UIPoseKeyframeFactory keyframeFactory)
        {
            return keyframeFactory.poseEditor.transform;
        }
        else if (editor.editor instanceof UIPoseTransformKeyframeFactory keyframeFactory)
        {
            return keyframeFactory.transform;
        }

        return null;
    }

    public static boolean startFilmGizmo(UIFilmPanel panel, UIContext context, int stencilIndex, float gizmoTransition)
    {
        if (panel.isFlying())
        {
            return false;
        }

        UIPropTransform transform = getEditableTransform(panel.replayEditor.keyframeEditor);
        GizmoDrag drag = buildFilmGizmoDrag(
            panel,
            panel.getCamera(),
            panel.preview.getViewport(),
            transform,
            gizmoTransition
        );

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);
    }

    public static void configureFilmHotkeyDrag(UIFilmPanel panel, UIContext context)
    {
        UIPropTransform transform = getEditableTransform(panel.replayEditor.keyframeEditor);

        if (transform == null)
        {
            return;
        }

        transform.hotkeyDrag(() -> buildFilmGizmoDrag(
            panel,
            panel.getCamera(),
            panel.preview.getViewport(),
            transform,
            panel.replayEditor.getContext() == null ? 0F : panel.replayEditor.getContext().getTransition()
        ));

        /* World-space copy/paste only makes sense for an actor's bone in the scene, so the world
         * matrix provider is wired solely for the pose editor's transform (other tracks leave it off
         * and the world context actions stay hidden there). */
        boolean pose = panel.replayEditor.keyframeEditor.editor instanceof UIPoseKeyframeFactory;

        transform.worldTransform(pose ? new FilmBoneWorldProvider(panel) : null);
        transform.rotationConstrained(pose ? () -> isFilmBoneRotationConstrained(panel) : null);
    }

    /**
     * Whether the film pose editor's current bone rotation is owned by an
     * enabled IK chain of its (possibly nested) model form — the gizmo then
     * refuses rotation gestures and dims its rings (see
     * {@link ModelIKRuntime#isRotationConstrained}).
     */
    private static boolean isFilmBoneRotationConstrained(UIFilmPanel panel)
    {
        UIKeyframeEditor keyframeEditor = panel.replayEditor.keyframeEditor;

        if (keyframeEditor == null || !(keyframeEditor.editor instanceof UIPoseKeyframeFactory))
        {
            return false;
        }

        IEntity entity = panel.getController().getCurrentEntity();
        Pair<String, Boolean> bone = keyframeEditor.getBone();

        if (entity == null || bone == null || bone.a == null)
        {
            return false;
        }

        UIKeyframeSheet sheet = keyframeEditor.getSheet(keyframeEditor.editor.getKeyframe());
        BaseValueBasic property = sheet == null ? null : FormUtils.getProperty(entity.getForm(), sheet.id);
        Form owner = property == null ? null : FormUtils.getForm(property);

        if (!(owner instanceof ModelForm modelForm))
        {
            return false;
        }

        ModelInstance instance = ModelFormRenderer.getModel(modelForm);

        return instance != null && ModelIKRuntime.isRotationConstrained(instance.model, modelForm, StringUtils.fileName(bone.a));
    }

    /**
     * Ray gizmo context for the film / replay viewport: same camera-origin and
     * axes as {@link GizmoDrag#fromRenderedGizmo}, plus numeric
     * {@link GizmoDrag#computeRotateAxes} / {@link GizmoDrag#computeTranslateJacobian}
     * driven by the composite bone matrix {@code target.mul(bone)} so replay
     * {@code bodyYaw}, anchor parents, and other film-only transforms match
     * {@link BaseFilmController#renderEntity}. Also the one place the film's
     * GLOBAL frame is set on the drag &mdash; the replay's own facing
     * ({@link BaseFilmController#getReplayWorldAxes}).
     */
    public static GizmoDrag buildFilmGizmoDrag(
        UIFilmPanel panel,
        Camera camera,
        Area viewport,
        UIPropTransform transform,
        float transition
    )
    {
        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(camera, viewport);

        if (drag == null || panel == null)
        {
            return drag;
        }

        IEntity entity = panel.getController().getCurrentEntity();

        /* The GLOBAL frame of a film edit is the edited replay's own facing, not
         * the map's axes — set before any early return, since it is the gizmo's
         * frame for every track (it doesn't depend on the bone or the sampled
         * matrices below). The drawn handles get the same axes in
         * BaseFilmController#renderAxes; the two must not drift apart. */
        drag.setGlobalAxes(FilmMatrices.getReplayWorldAxes(entity, transition));

        if (transform == null || transform.getTransform() == null)
        {
            return drag;
        }

        UIKeyframeEditor keyframeEditor = panel.replayEditor.keyframeEditor;

        if (keyframeEditor == null)
        {
            return drag;
        }

        Pair<String, Boolean> bone = keyframeEditor.getBone();
        Replay replay = panel.replayEditor.getReplay();

        if (bone == null || bone.a == null || replay == null || entity == null)
        {
            /* The anchor track has no model bone: its transform parents the whole
             * form, so sample the form's resolved anchor matrix instead. */
            if (keyframeEditor.isFormAnchorTrack() && replay != null && entity != null)
            {
                buildAnchorGizmoDrag(panel, camera, drag, transform, replay, entity, transition);
            }

            return drag;
        }

        Supplier<Matrix4f> matrixSampler = () ->
        {
            Form form = entity.getForm();
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);

            if (form != null)
            {
                /* Force-apply the perturbed keyframe state to the entity's form 
                 * so that FormUtilsClient's matrix cache updates for this sample. */
                replay.properties.applyProperties(form, tick);
            }

            Matrix4f m = FilmMatrices.getGizmoBoneCompositeMatrix(
                panel.getController().getEntities(),
                entity,
                replay,
                camera.position.x,
                camera.position.y,
                camera.position.z,
                transition,
                bone.a,
                true
            );

            return m == null ? new Matrix4f() : m;
        };

        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform.getTransform(), matrixSampler));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(
            transform.getTransform(),
            () -> matrixSampler.get().getTranslation(new Vector3f())
        ));
        /* Restore the form to its unperturbed state */
        Form form = entity.getForm();
        if (form != null)
        {
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);
            replay.properties.applyProperties(form, tick);
        }

        /* After the restore, so the evaluated channels the base reads reflect
         * the unperturbed pose (the helper re-collects the capture itself). */
        drag.setAdditiveRotationBase(filmPoseRotationBase(keyframeEditor, entity, transition, bone.a));

        return drag;
    }

    /**
     * The additive euler base under the edited pose/overlay track's channels for
     * the current bone ({@link FormUtils#additivePoseRotationBase}): the pose
     * stack merges per-channel, so an overlay's drag deltas must compose at the
     * bone's EFFECTIVE angles, not the overlay's own near-zero channels. The
     * total comes from the bone's EVALUATED channels in the render capture
     * ({@link BaseFilmController#getGizmoBoneEvaluatedRotation}) — folding the
     * animator's actions and the model's rest rotation in — with the edited
     * track resolved through the sheet's property path on the LIVE form and its
     * own contribution subtracted. {@code null} (zero base) when the track isn't
     * a pose one or the merge for this bone isn't purely additive.
     */
    private static Vector3f filmPoseRotationBase(UIKeyframeEditor keyframeEditor, IEntity entity, float transition, String bonePath)
    {
        if (!(keyframeEditor.editor instanceof UIPoseKeyframeFactory))
        {
            return null;
        }

        UIKeyframeSheet sheet = keyframeEditor.getSheet(keyframeEditor.editor.getKeyframe());

        if (sheet == null)
        {
            return null;
        }

        BaseValueBasic property = FormUtils.getProperty(entity.getForm(), sheet.id);

        if (!(property instanceof ValuePose valuePose))
        {
            return null;
        }

        Vector3f evaluated = FilmMatrices.getGizmoBoneEvaluatedRotation(entity, transition, bonePath);

        return FormUtils.additivePoseRotationBase(valuePose, StringUtils.fileName(bonePath), evaluated);
    }

    /**
     * Numeric Jacobian / rotate-axes for the anchor gizmo: the sampler returns
     * the form's resolved anchor matrix ({@link BaseFilmController#getGizmoAnchorCompositeMatrix},
     * the same {@code target} the form renders with), so perturbing the keyframe's
     * {@code anchor.transform} reveals how it moves the form in world space —
     * exactly mirroring the bone path in {@link #buildFilmGizmoDrag}.
     */
    private static void buildAnchorGizmoDrag(
        UIFilmPanel panel,
        Camera camera,
        GizmoDrag drag,
        UIPropTransform transform,
        Replay replay,
        IEntity entity,
        float transition
    )
    {
        Supplier<Matrix4f> matrixSampler = () ->
        {
            Form form = entity.getForm();
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);

            if (form != null)
            {
                /* Push the perturbed keyframe state onto the form so the resolved
                 * anchor matrix reflects this sample. */
                replay.properties.applyProperties(form, tick);
            }

            Matrix4f m = FilmMatrices.getGizmoAnchorCompositeMatrix(
                panel.getController().getEntities(),
                entity,
                replay,
                camera.position.x,
                camera.position.y,
                camera.position.z,
                transition
            );

            return m == null ? new Matrix4f() : m;
        };

        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform.getTransform(), matrixSampler));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(
            transform.getTransform(),
            () -> matrixSampler.get().getTranslation(new Vector3f())
        ));
        /* Restore the form to its unperturbed state */
        Form form = entity.getForm();
        if (form != null)
        {
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);
            replay.properties.applyProperties(form, tick);
        }
    }

    /* Picking form and form properties */

    public static void pickForm(UIKeyframeEditor keyframeEditor, ICursor cursor, Form form, String bone)
    {
        pickForm(keyframeEditor, cursor, form, bone, false);
    }

    public static void pickForm(UIKeyframeEditor keyframeEditor, ICursor cursor, Form form, String bone, boolean insert)
    {
        if (form == null || keyframeEditor == null || bone.isEmpty())
        {
            return;
        }

        /* Ctrl multi-select: toggle the bone in the live pose editor without changing the
         * selected keyframe. Selecting a keyframe recreates the factory (see
         * UIKeyframeEditor#pickKeyframe), which resets the pose editor — so it caps the
         * multi-selection. When a pose factory is already up and owns this bone, just
         * toggle it and stop, so the selection accumulates. */
        if (!insert && Window.isCtrlPressed()
            && keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory
            && poseFactory.poseEditor.hasBone(bone))
        {
            poseFactory.poseEditor.selectBone(bone, true);

            return;
        }

        String path = FormUtils.getPath(form);
        String boneKey = TrackId.bone(path, bone).toKey();

        if (!insert)
        {
            IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
            Keyframe selected = graph.getSelected();
            UIKeyframeSheet currentSheet = selected != null ? graph.getSheet(selected) : null;
            TrackId currentPath = currentSheet == null ? null : TrackId.parse(currentSheet.id, TrackKind.BONE);
            if (currentPath != null && !path.equals(currentPath.formPath()))
            {
                return;
            }
            if (isPoseSheet(currentSheet, path))
            {
                int tick = cursor.getCursor();
                Keyframe closest = getClosestKeyframe(currentSheet, tick);
                if (closest != null)
                {
                    if (currentSheet.selection.getSelected().size() <= 1)
                    {
                        forceSelectInSheet(graph, currentSheet, closest);
                    }
                    cursor.setCursor((int) closest.getTick());
                }
                updatePoseEditorBoneSelection(keyframeEditor, bone);
                return;
            }
        }

        if (insert)
        {
            UIKeyframeSheet sheet = resolveBoneSheet(keyframeEditor, boneKey, path);

            if (sheet == null)
            {
                return;
            }

            /* When the per-limb bone track is empty/absent, resolveBoneSheet falls back
             * to the form's pose track. Insert there instead of doing nothing: select
             * the keyframe already at the cursor, or add a fresh one. */
            if (isPoseSheet(sheet, path))
            {
                insertIntoPoseSheet(keyframeEditor, cursor, bone, sheet);
                return;
            }

            /* Non-empty per-limb track: keep suppressing per-limb inserts while a pose
             * keyframe of this form is the active selection. */
            IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
            Keyframe selected = graph.getSelected();
            UIKeyframeSheet currentSheet = selected != null ? graph.getSheet(selected) : null;

            if (isPoseSheet(currentSheet, path))
            {
                return;
            }

            pickProperty(keyframeEditor, cursor, bone, sheet, true);
            return;
        }

        UIKeyframeSheet sheet = resolveBoneSheet(keyframeEditor, boneKey, path);

        if (sheet != null)
        {
            pickProperty(keyframeEditor, cursor, bone, sheet, false);
        }
    }

    private static UIKeyframeSheet resolveBoneSheet(UIKeyframeEditor keyframeEditor, String boneKey, String formPath)
    {
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        UIKeyframeSheet sheet = graph.getSheet(boneKey);

        if (sheet == null)
        {
            /* Fallback: match by id ignoring case (stencil may return "head", sheet id may be "pose.bones.Head") */
            for (UIKeyframeSheet s : graph.getSheets())
            {
                if (s.id != null && s.id.equalsIgnoreCase(boneKey))
                {
                    sheet = s;
                    break;
                }
            }
        }

        if (sheet != null)
        {
            /* Per-limb bone tracks are optional and frequently empty; when the matched track has no
             * keyframes, fall back to the form's main pose track so the click still selects the
             * closest pose keyframe (as a non-per-limb bone like the torso already does) instead of
             * doing nothing unless a pose keyframe happens to be selected already. */
            if (sheet.channel.isEmpty())
            {
                UIKeyframeSheet poseSheet = getPreferredPoseSheet(graph, formPath);

                if (poseSheet != null)
                {
                    return poseSheet;
                }
            }

            return sheet;
        }

        return getPreferredPoseSheet(graph, formPath);
    }

    private static UIKeyframeSheet getPoseSheet(IUIKeyframeGraph graph, String formPath)
    {
        for (UIKeyframeSheet sheet : graph.getSheets())
        {
            if (isPoseSheet(sheet, formPath))
            {
                return sheet;
            }
        }

        return null;
    }

    private static UIKeyframeSheet getPreferredPoseSheet(IUIKeyframeGraph graph, String formPath)
    {
        /* Prefer the pose track the user is actually working in - the currently selected pose keyframe, then
         * the last selected sheet (remembered across clicks) - so picks and inserts stay on that track (e.g.
         * an overlay) instead of snapping back to the form's top pose track. */
        Keyframe selected = graph.getSelected();
        UIKeyframeSheet current = selected != null ? graph.getSheet(selected) : null;

        if (isPoseSheet(current, formPath))
        {
            return current;
        }

        UIKeyframeSheet last = graph.getLastSheet();

        if (isPoseSheet(last, formPath))
        {
            return last;
        }

        return getPoseSheet(graph, formPath);
    }

    private static void pickProperty(UIKeyframeEditor keyframeEditor, ICursor cursor, String bone, String key, boolean insert)
    {
        UIKeyframeSheet sheet = keyframeEditor.view.getGraph().getSheet(key);

        if (sheet != null)
        {
            pickProperty(keyframeEditor, cursor, bone, sheet, insert);
        }
    }

    private static void pickProperty(UIKeyframeEditor keyframeEditor, ICursor filmPanel, String bone, UIKeyframeSheet sheet, boolean insert)
    {
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        int tick = filmPanel.getCursor();

        if (insert)
        {
            Keyframe keyframe = graph.addKeyframe(sheet, tick, null);
            graph.selectKeyframe(keyframe);
            return;
        }

        Keyframe closest = getClosestKeyframe(sheet, tick);

        TrackId path = TrackId.parse(sheet.id, TrackKind.BONE);
        String boneForEditor = path != null ? path.subject() : bone;

        if (closest != null)
        {
            if (sheet.selection.getSelected().size() <= 1)
            {
                forceSelectInSheet(graph, sheet, closest);
            }
            updatePoseEditorBoneSelection(keyframeEditor, boneForEditor);
            filmPanel.setCursor((int) closest.getTick());
        }
        else
        {
            updatePoseEditorBoneSelection(keyframeEditor, boneForEditor);
        }
    }

    private static Keyframe getClosestKeyframe(UIKeyframeSheet sheet, int tick)
    {
        KeyframeSegment segment = sheet.channel.find(tick);

        return segment != null ? segment.getClosest() : null;
    }

    private static Keyframe getKeyframeAt(UIKeyframeSheet sheet, int tick)
    {
        for (Object o : sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) o;

            if ((int) keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        return null;
    }

    /**
     * Insert fallback onto the form's pose track, used when a bone's per-limb
     * track is empty/absent: select the keyframe already sitting at the cursor
     * (so the gesture never duplicates it), otherwise add a fresh one. Either
     * way the bone is highlighted in the pose editor.
     */
    private static void insertIntoPoseSheet(UIKeyframeEditor keyframeEditor, ICursor cursor, String bone, UIKeyframeSheet poseSheet)
    {
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        int tick = cursor.getCursor();
        Keyframe existing = getKeyframeAt(poseSheet, tick);

        if (existing != null)
        {
            if (poseSheet.selection.getSelected().size() <= 1)
            {
                forceSelectInSheet(graph, poseSheet, existing);
            }
        }
        else
        {
            Keyframe keyframe = graph.addKeyframe(poseSheet, tick, null);
            graph.selectKeyframe(keyframe);
        }

        updatePoseEditorBoneSelection(keyframeEditor, bone);
    }

    private static boolean isPoseSheet(UIKeyframeSheet sheet, String formPath)
    {
        if (sheet == null || sheet.id == null)
        {
            return false;
        }

        String prefix = formPath.isEmpty() ? "" : formPath + FormUtils.PATH_SEPARATOR;

        /* The main pose track is matched exactly so per-limb bone tracks ("pose.bones.<bone>") are excluded,
         * while every overlay track - the default "pose_overlay" and the numbered ones ("pose_overlay0",
         * "pose_overlay1", ...) - is matched by prefix, consistent with FormUtils.isPoseProperty. */
        return sheet.id.equals(prefix + "pose") || sheet.id.startsWith(prefix + "pose_overlay");
    }

    private static void forceSelectInSheet(IUIKeyframeGraph graph, UIKeyframeSheet sheet, Keyframe keyframe)
    {
        /* World-pick must deterministically activate exactly clicked sheet/keyframe */
        graph.clearSelection();
        sheet.selection.add(keyframe);
        graph.pickKeyframe(keyframe);
    }

    private static void updatePoseEditorBoneSelection(UIKeyframeEditor keyframeEditor, String bone)
    {
        if (keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory)
        {
            poseFactory.poseEditor.selectBone(bone);
        }
    }

    /* Converting Blockbench model keyframes to pose keyframes */

    public static void animationToPoseKeyframes(
        UIKeyframeEditor keyframeEditor, UIKeyframeSheet sheet,
        ModelForm modelForm, IEntity entity,
        int tick, String animationKey, boolean onlyKeyframes, int length, int step
    ) {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);
        Animation animation = model.animations.get(animationKey);

        if (animation != null)
        {
            keyframeEditor.view.getDopeSheet().clearSelection();

            if (onlyKeyframes)
            {
                List<Float> list = getTicks(animation);

                for (float i : list)
                {
                    fillAnimationPose(sheet, i, model, entity, animation, tick);
                }
            }
            else
            {
                for (int i = 0; i < length; i += step)
                {
                    fillAnimationPose(sheet, i, model, entity, animation, tick);
                }
            }

            keyframeEditor.view.getDopeSheet().pickSelected();
        }
    }

    private static List<Float> getTicks(Animation animation)
    {
        Set<Float> integers = new HashSet<>();

        for (AnimationPart value : animation.parts.values())
        {
            for (KeyframeChannel<MolangExpression> channel : value.channels)
            {
                for (Keyframe<MolangExpression> keyframe : channel.getKeyframes())
                {
                    integers.add(keyframe.getTick());
                }
            }
        }

        ArrayList<Float> ticks = new ArrayList<>(integers);

        Collections.sort(ticks);

        return ticks;
    }

    private static void fillAnimationPose(UIKeyframeSheet sheet, float i, ModelInstance model, IEntity entity, Animation animation, int current)
    {
        model.model.resetPose();
        model.model.apply(entity, animation, i, 1F, 0F, false);

        int insert = sheet.channel.insert(current + i, model.model.createPose());

        sheet.selection.add(insert);
    }

    @SuppressWarnings("unchecked")
    public static void posesToLimbTracks(Replay replay, UIKeyframeSheet poseSheet, ModelForm modelForm)
    {
        if (replay == null || poseSheet == null || modelForm == null)
        {
            return;
        }

        String formPath = poseSheet.id.equals("pose") ? "" : poseSheet.id.substring(0, poseSheet.id.length() - (FormUtils.PATH_SEPARATOR + "pose").length());
        Form form = formPath.isEmpty() ? replay.form.get() : FormUtils.getForm(replay.form.get(), formPath);

        if (!(form instanceof ModelForm targetModelForm))
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(targetModelForm);

        if (model == null)
        {
            return;
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());

        bones.removeIf((bone) -> PoseBones.isHidden(model.getDisabledBones(), bone));

        List<Keyframe<Pose>> selectedKeyframes = (List<Keyframe<Pose>>) (List<?>) poseSheet.selection.getSelected();

        if (selectedKeyframes.isEmpty())
        {
            return;
        }

        for (Keyframe<Pose> keyframe : selectedKeyframes)
        {
            Pose pose = keyframe.getValue();

            if (pose == null)
            {
                continue;
            }

            float tick = keyframe.getTick();

            for (String bone : bones)
            {
                KeyframeChannel<PoseTransform> limbChannel = (KeyframeChannel<PoseTransform>) replay.properties.getOrCreate(TrackId.bone(formPath, bone));

                if (limbChannel == null)
                {
                    continue;
                }

                /* Every bone of the model, not just the posed ones: a bone the pose is silent
                 * about still gets a rest keyframe on its track, but reading must not grow the
                 * pose being laid out. */
                PoseTransform transform = pose.get(bone);
                PoseTransform copy = transform == null ? new PoseTransform() : (PoseTransform) transform.copy();
                int index = limbChannel.insert(tick, copy);
                Keyframe<PoseTransform> limbKf = limbChannel.get(index);

                limbKf.copyOverExtra(keyframe);
            }
        }
    }

    public static void clearIKTracks(Replay replay, ModelForm modelForm)
    {
        if (replay == null || modelForm == null)
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        List<String> controllers = ModelIKRuntime.getControllers(model);
        List<String> poleControllers = ModelIKRuntime.getPoleControllers(model);
        String path = FormUtils.getPath(modelForm);

        BaseValue.edit(replay.properties, (props) ->
        {
            for (String controller : controllers)
            {
                props.remove(TrackId.ikTarget(path, controller));
            }

            for (String controller : poleControllers)
            {
                props.remove(TrackId.poleTarget(path, controller));
            }
        });
    }

    /* Offer bone hierarchy options */

    /** Leaf bone pick for {@link #pickFormWithOffers}; {@code insert}
     *  distinguishes a left-click select from a middle-click insert. */
    public interface FormPicker
    {
        void pick(Form form, String bone, boolean insert);
    }

    /**
     * Shared viewport bone-pick gesture for the film / replay editor: left / Ctrl+middle
     * select, right inserts, Shift offers the hierarchy. Ctrl+click toggles the bone in
     * the pose editor's multi-selection (handled at the leaf), so it no longer opens the
     * adjacent-bones menu here. The leaf {@code picker} supplies the editor-specific
     * selection. Returns whether the click was consumed.
     */
    public static boolean pickFormWithOffers(UIContext context, Pair<Form, String> pair, FormPicker picker)
    {
        boolean select = context.mouseButton == 0 || (context.mouseButton == 2 && Window.isCtrlPressed());
        boolean insert = context.mouseButton == 1;

        if (!select && !insert)
        {
            return false;
        }

        /* Shift keeps the hierarchy menu; Ctrl now falls straight through to the pick,
         * where the modifier turns the selection additive (multi-bone). */
        if (Window.isShiftPressed())
        {
            offerHierarchy(context, pair.a, pair.b, (bone) -> picker.pick(pair.a, bone, insert));
        }
        else
        {
            picker.pick(pair.a, pair.b, insert);
        }

        return true;
    }

    public static void offerAdjacent(UIContext context, Form form, String bone, Consumer<String> consumer)
    {
        if (form == null)
        {
            return;
        }

        if (!bone.isEmpty() && form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : model.model.getAdjacentGroups(bone))
                {
                    if (PoseBones.isHidden(model.getDisabledBones(), modelGroup))
                    {
                        continue;
                    }

                    menu.action(Icons.LIMB, IKey.constant(modelGroup), () -> consumer.accept(modelGroup));
                }

                menu.autoKeys();
            });
        }
    }

    public static void offerHierarchy(UIContext context, Form form, String bone, Consumer<String> consumer)
    {
        if (form == null)
        {
            return;
        }

        if (!bone.isEmpty() && form instanceof ModelForm modelForm)
        {
            ModelInstance model = ModelFormRenderer.getModel(modelForm);

            if (model == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : model.model.getHierarchyGroups(bone))
                {
                    if (PoseBones.isHidden(model.getDisabledBones(), modelGroup))
                    {
                        continue;
                    }

                    menu.action(Icons.LIMB, IKey.constant(modelGroup), () -> consumer.accept(modelGroup));
                }

                menu.autoKeys();
            });
        }
    }
}
