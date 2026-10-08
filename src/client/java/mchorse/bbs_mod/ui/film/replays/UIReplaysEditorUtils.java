package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.ui.utils.SplineKeyframeEditor;

import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.FilmMatrices;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackSearchEntry;
import mchorse.bbs_mod.ui.forms.editors.UIForms;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.IPosedForm;
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
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;

public class UIReplaysEditorUtils
{
    private static final int BONE_TRACK_HUE_COUNT = 12;

    public static boolean isTrackHidden(String id, String filterKey, Form owner)
    {
        for (String disabled : BBSSettings.disabledSheets.get())
        {
            if (filterKey.equals(disabled) || id.equals(disabled) || id.endsWith("/" + disabled)) return true;
        }
        return owner != null && (owner.disabledTracks.get().contains(Form.DISABLED_ALL)
            || owner.disabledTracks.get().contains(filterKey));
    }

    /** Shared read-only search catalog for film and state editors. */
    public static List<TrackSearchEntry> formSearchEntries(Form root, UIForms parts, boolean animationState)
    {
        List<TrackSearchEntry> entries = new ArrayList<>();
        /* No FormProperties: opening the navigator must not create channels. */
        List<TrackDescriptor> catalog = TrackCatalog.of(root);
        Map<String, String> names = new HashMap<>();
        for (var part : parts.getList()) names.put(part.getPath(), part.toString());

        for (TrackDescriptor track : catalog)
        {
            if (animationState && track.kind().isSolver()) continue;
            String path = track.id().formPath();
            String location = names.getOrDefault("", "");
            String prefix = "";
            if (!path.isEmpty())
            {
                for (String segment : path.split("/"))
                {
                    prefix = prefix.isEmpty() ? segment : prefix + "/" + segment;
                    location += " → " + names.getOrDefault(prefix, segment);
                }
            }
            location += " · " + UIReplaysEditor.categoryOf(track.id(), true).label.get();
            entries.add(new TrackSearchEntry(track.key(), path, true,
                BBSSettings.trackStyles.name(track.filterKey(), track.title().get()), track.title().get(), location,
                BBSSettings.trackStyles.color(track.filterKey(), track.color()), track.icon(),
                UIReplaysEditorUtils.isTrackHidden(track.key(), track.filterKey(), track.owner())));
        }
        return entries;
    }

    public static boolean revealSearchTrack(UIKeyframeEditor editor, TrackSearchEntry entry, boolean createKeyframe, UIContext context)
    {
        for (UIKeyframeSheet sheet : editor.view.getDopeSheet().getSheets())
        {
            if (sheet.id.equals(entry.key()) && entry.owned() == (UIReplaysEditor.getSheetForm(sheet) != null))
            {
                editor.view.getDopeSheet().revealSheet(sheet);
                if (createKeyframe)
                {
                    var graph = editor.view.getDopeSheet();
                    float tick = editor.view.getPlayheadTick(context);
                    float sourceTick = sheet.channel.getSourceTick(tick);
                    /* Existing keys (including a loop's source key) are selected without rewriting them. */
                    for (int i = 0; i < sheet.channel.getKeyframes().size(); i++)
                    {
                        var keyframe = sheet.channel.get(i);
                        if (keyframe.getTick() == sourceTick)
                        {
                            graph.clearSelection();
                            sheet.selection.add(i);
                            graph.pickKeyframe(keyframe);
                            return true;
                        }
                    }
                    graph.addKeyframeManually(sheet, tick, null);
                }
                return true;
            }
        }
        return false;
    }

    /** Overlay counts are global; both timeline editors use the same creation action. */
    public static void addOverlayTrackAction(ContextMenuManager menu, UIKeyframeSheet sheet, Consumer<TrackId> refresh)
    {
        if (sheet == null) return;
        TrackId track = TrackId.parse(sheet.id);
        Form owner = UIReplaysEditor.getSheetForm(sheet);
        if (track == null || track.kind() != TrackKind.PROPERTY || owner == null) return;

        String name = track.subject();
        boolean pose = name.equals("pose") || name.startsWith("pose_overlay");
        boolean transform = name.equals("transform") || name.startsWith("transform_overlay");
        if ((!pose && !transform) || (pose && !(owner instanceof IPosedForm))) return;

        ValueInt count = pose ? BBSSettings.recordingPoseOverlays : BBSSettings.recordingTransformOverlays;
        if (count.get() >= count.getMax()) return;
        menu.action(Icons.ADD, L10n.lang(pose ? "bbs.ui.keyframes.context.add_pose_track" : "bbs.ui.keyframes.context.add_transform_track"), () ->
        {
            if (count.get() >= count.getMax()) return;
            count.set(count.get() + 1);
            owner.syncOverlayTracks();
            refresh.accept(TrackId.property(track.formPath(), pose ? "pose_overlay" : "transform_overlay"));
        });
    }

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

        channel.insertInheriting(tick, pose);
    }

    /**
     * Turn a form's catalog into timeline rows, hanging the tracks that fold under another one off
     * their parent row. The catalog decides what exists and where it sits; this only builds widgets,
     * which is why both timelines — a replay's and an animation state's — go through it.
     */
    public static void buildSheets(List<TrackDescriptor> catalog, List<UIKeyframeSheet> sheets, FormProperties properties)
    {
        Map<TrackId, UIKeyframeSheet> rows = new HashMap<>();

        for (TrackDescriptor track : catalog)
        {
            /* Creating an editable row needs a channel. Catalog/search enumeration does not. */
            if (track.channel() == null)
            {
                KeyframeChannel channel = properties.getOrCreate(FormUtils.getRoot(track.owner()), track.id());
                if (channel == null) continue;
                track = new TrackDescriptor(track.id(), channel, track.owner(), track.title(), track.icon(), track.color(), track.property(), track.seed(), track.parent());
            }
            UIKeyframeSheet sheet = new UIKeyframeSheet(track);

            rows.put(track.id(), sheet);
            sheets.add(sheet);
        }

        for (TrackDescriptor track : catalog)
        {
            if (track.parent() != null)
            {
                if (rows.containsKey(track.id())) rows.get(track.id()).setParent(rows.get(track.parent()));
            }
        }
    }

    /** Remove links to filtered rows while keeping their surviving children accessible. */
    public static void pruneTree(List<UIKeyframeSheet> sheets)
    {
        Set<UIKeyframeSheet> present = new HashSet<>(sheets);

        for (UIKeyframeSheet sheet : sheets)
        {
            sheet.children.removeIf(child -> !present.contains(child));

            if (sheet.parent != null && !present.contains(sheet.parent))
            {
                sheet.parent = null;
            }
        }
    }

    private static void insertPoseTransformKeyframe(KeyframeChannel<PoseTransform> channel, float tick, PoseTransform value)
    {
        KeyframeSegment<PoseTransform> segment = channel.find(tick);
        PoseTransform poseTransform = value == null
            ? segment != null ? segment.createInterpolated() : new PoseTransform()
            : (PoseTransform) value.copy();

        channel.insertInheriting(tick, poseTransform);
    }

    public static UIPropTransform getEditableTransform(UIKeyframeEditor editor)
    {
        if (editor == null || editor.editor == null)
        {
            return null;
        }

        editor.editor.update();

        if (editor.editor instanceof SplineKeyframeEditor spline)
        {
            return spline.pointPath() == null ? null : spline.pointEditor().position;
        }
        else if (editor.editor instanceof UITransformKeyframeFactory transformKeyframeFactory)
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

    /**
     * The transform the film's gizmo edits right now: whatever the keyframe editor offers
     * for the selected bone or track, and — when nothing there claims it — the replay's own
     * placement. Same fallback rule as {@link UIFilmController#isReplayGizmo}, so what is
     * drawn and what a drag writes to can never be two different things.
     */
    public static UIPropTransform getFilmGizmoTransform(UIFilmPanel panel, float transition)
    {
        if (panel.getController().getEditTarget().is(FilmTarget.Kind.ROOT))
        {
            UIReplayPropTransform replayTransform = panel.replayEditor.replayTransform;

            replayTransform.syncFromReplay(
                panel.replayEditor.getReplay(),
                panel.getController().getCurrentEntity(),
                panel.getKeyframeCursor(transition)
            );

            return replayTransform;
        }

        return getEditableTransform(panel.replayEditor.keyframeEditor);
    }

    public static boolean startFilmGizmo(UIFilmPanel panel, UIContext context, int stencilIndex, float gizmoTransition)
    {
        if (panel.isFlying())
        {
            return false;
        }

        UIPropTransform transform = getFilmGizmoTransform(panel, gizmoTransition);
        GizmoDrag drag = buildFilmGizmoDrag(
            panel,
            panel.getCamera(),
            panel.preview.getViewport(),
            transform,
            gizmoTransition
        );

        boolean started = Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);

        /* Only once the gesture is real, and only for the replay's own placement: the drag is
         * about to write x/y/z and the angles, so put the tracks it writes to on the timeline.
         * A refused handle must not move the timeline out from under the user. */
        if (started && transform instanceof UIReplayPropTransform)
        {
            panel.replayEditor.showReplayTracks();
        }

        return started;
    }

    public static void configureFilmHotkeyDrag(UIFilmPanel panel, UIContext context)
    {
        float transition = panel.replayEditor.getContext() == null ? 0F : panel.replayEditor.getContext().getTransition();

        /* The replay's own placement is a target of its own, refreshed every frame so a
         * gesture always starts from the pose on screen. It is configured beside the
         * keyframe editor's transform rather than instead of it: the two never hold the
         * gizmo at the same time, but a track can own the value pads without owning the
         * gizmo (a form-transform property track), and that one must keep its wiring. */
        UIReplayPropTransform replayTransform = panel.replayEditor.replayTransform;

        replayTransform.syncFromReplay(
            panel.replayEditor.getReplay(),
            panel.getController().getCurrentEntity(),
            panel.getKeyframeCursor(transition)
        );
        replayTransform.hotkeyDrag(() -> buildFilmGizmoDrag(
            panel,
            panel.getCamera(),
            panel.preview.getViewport(),
            replayTransform,
            transition
        ));

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
         * and the world context actions stay hidden there). The replay's own placement has no
         * keyframe editor behind it at all, which is also why this is read defensively. */
        UIKeyframeEditor hotkeyEditor = panel.replayEditor.keyframeEditor;
        boolean pose = hotkeyEditor != null && hotkeyEditor.editor instanceof UIPoseKeyframeFactory;

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
        Pair<String, TransformSpace> bone = keyframeEditor.getBone();

        if (entity == null || bone == null || bone.a == null)
        {
            return false;
        }

        UIKeyframeSheet sheet = keyframeEditor.editor.getSheet();
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
     * Ray gizmo context for the film viewport: {@link GizmoDrag#fromRenderedGizmo} plus the
     * numeric samplers, driven by the composite bone matrix so replay {@code bodyYaw}, anchor
     * parents and other film-only transforms match what the renderer draws. Also the one place
     * the film's GLOBAL frame is set on the drag — the replay's own facing.
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

        if (transform instanceof UIReplayPropTransform && entity != null)
        {
            buildReplayGizmoDrag(camera, drag, entity, transition);

            return drag;
        }

        UIKeyframeEditor keyframeEditor = panel.replayEditor.keyframeEditor;

        if (keyframeEditor == null)
        {
            return drag;
        }

        String splinePath = SplineEditorUtils.selectedPath(keyframeEditor);
        if (splinePath != null && entity != null)
        {
            var point = SplineEditorUtils.resolve(entity.getForm(), splinePath);
            Matrix4f parent = FilmMatrices.getSplineParentCompositeMatrix(panel.getController().getEntities(), entity,
                panel.replayEditor.getReplay(), camera.position.x, camera.position.y, camera.position.z, transition, point);
            if (parent == null || Math.abs(parent.determinant()) < 1E-8F) return null;
            drag.setJacobian(new org.joml.Matrix3f(parent));
            drag.setFrameAxes(parent, parent);
            return drag;
        }

        Pair<String, TransformSpace> bone = keyframeEditor.getBone();
        Replay replay = panel.replayEditor.getReplay();

        if (bone == null || bone.a == null || replay == null || entity == null)
        {
            /* The anchor track has no model bone: its transform parents the whole
             * form, so sample the form's resolved anchor matrix instead. */
            if (keyframeEditor.isReplayAnchorTrack() && replay != null && entity != null)
            {
                buildAnchorGizmoDrag(panel, camera, drag, transform, replay, entity, transition);
            }

            return drag;
        }

        sampleGizmoAxes(panel, drag, transform, replay, entity, transition, () -> FilmMatrices.getGizmoBoneCompositeMatrix(
            panel.getController().getEntities(),
            entity,
            replay,
            camera.position.x,
            camera.position.y,
            camera.position.z,
            transition,
            bone.a,
            true
        ));

        /* Both of the bone's frames — its own and its parent's — so the snapshot can
         * answer for either instead of only for the one the handles were drawn in: the
         * axis-key walk moves a live gesture between LOCAL and PARENT
         * (UIPropTransform#setEditingAxis). Same composite the gizmo is placed on, read
         * with and without the bone's own rotation, and after the restore above so both
         * describe the unperturbed pose. */
        drag.setFrameAxes(
            FilmMatrices.getGizmoBoneCompositeMatrix(
                panel.getController().getEntities(), entity, replay,
                camera.position.x, camera.position.y, camera.position.z, transition, bone.a, true
            ),
            FilmMatrices.getGizmoBoneCompositeMatrix(
                panel.getController().getEntities(), entity, replay,
                camera.position.x, camera.position.y, camera.position.z, transition, bone.a, false
            )
        );

        /* After the restore, so the evaluated channels the base reads reflect
         * the unperturbed pose (the helper re-collects the capture itself). */
        drag.setAdditiveRotationBase(filmPoseRotationBase(keyframeEditor, entity, transition, bone.a));

        return drag;
    }

    /**
     * Ray context for a drag on the replay's own placement. Everything here is analytic
     * rather than sampled: the composition behind a record is just
     * {@code translate(x, y, z) · Ry(-yaw) · Rx(pitch)}, so there is nothing to measure
     * numerically the way a bone's chain has to be.
     *
     * <ul>
     * <li>The translate Jacobian stays the identity {@link GizmoDrag} defaults to — one
     *     channel unit is one block.</li>
     * <li>The rotate axes are the drawn gizmo's own axes. The gizmo is placed on
     *     {@code Ry(-bodyYaw)}, so its Y column is the world's up (which is what the yaw
     *     channels turn about) and its X column is the actor's right (which is what pitch
     *     turns about) — exactly the rings the user grabs.</li>
     * <li>Both frames are that same placement: a record sits in no container, so its own
     *     frame and its "parent" frame are one and the same.</li>
     * </ul>
     */
    private static void buildReplayGizmoDrag(Camera camera, GizmoDrag drag, IEntity entity, float transition)
    {
        drag.setRotateAxes(drag.gizmoWorldAxes);

        Matrix4f placement = FilmMatrices.getMatrixForRenderWithRotation(
            entity, camera.position.x, camera.position.y, camera.position.z, transition
        );

        drag.setFrameAxes(placement, placement);
    }

    /**
     * The additive euler base under the edited pose/overlay track for the current bone: the
     * pose stack merges per-channel, so drag deltas must compose at the bone's EFFECTIVE
     * angles, not the overlay's own near-zero ones. Taken from the bone's evaluated channels
     * in the render capture (actions and rest rotation folded in), minus the edited track's own
     * contribution. {@code null} when the track isn't a pose one or the merge isn't additive.
     */
    private static Vector3f filmPoseRotationBase(UIKeyframeEditor keyframeEditor, IEntity entity, float transition, String bonePath)
    {
        if (!(keyframeEditor.editor instanceof UIPoseKeyframeFactory))
        {
            return null;
        }

        UIKeyframeSheet sheet = keyframeEditor.editor.getSheet();

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
        sampleGizmoAxes(panel, drag, transform, replay, entity, transition, () -> FilmMatrices.getGizmoAnchorCompositeMatrix(
            panel.getController().getEntities(),
            entity,
            replay,
            camera.position.x,
            camera.position.y,
            camera.position.z,
            transition
        ));
    }

    /**
     * Probe the edited bone across the selected keys of its track. Probing only the primary key
     * measures no response when the playhead is on a different selected key. This is a silent,
     * temporary channel delta, not an editor operation (which would notify and record undo).
     */
    public static <T> Supplier<T> keyframeGizmoSampler(UIKeyframeEditor editor, UIPropTransform transform, Supplier<T> sampler)
    {
        if (editor == null || editor.editor == null) return sampler;

        UIKeyframeSheet sheet = editor.editor.getSheet();
        if (sheet == null) return sampler;

        List<Transform> targets = new ArrayList<>();
        boolean cursor = editor.view.getAutoKeyframeTick() != null || !sheet.selection.hasAny();
        KeyframeChannel draft = new KeyframeChannel(sheet.id, sheet.channel.getFactory());
        draft.fromData(sheet.channel.toData());
        UIKeyframeSheet draftSheet = new UIKeyframeSheet(sheet.id, sheet.title, sheet.color, draft, sheet.property, sheet.isBoneTrack);
        draftSheet.seed = sheet.seed;
        draftSheet.selection.addAll(sheet.selection.getIndices());
        List<Keyframe> keys = cursor ? List.of(draftSheet.ensureKeyframe(editor.view.getTick()))
            : new ArrayList<>(draftSheet.selection.getSelected());

        for (Keyframe key : keys)
        {
            Transform target = editor.editor.getGizmoTransform(key.getValue());
            if (target != null) targets.add(target);
        }

        /* The probe installs only its private channel copy, then restores the original
         * key objects and selection. It never notifies history or leaves a new key behind. */
        return GizmoDrag.withTransformSelection(transform.getTransform(), targets, () ->
        {
            List<Keyframe> original = new ArrayList<>(sheet.channel.getKeyframes());
            try
            {
                sheet.channel.getAllTyped().clear();
                sheet.channel.getAllTyped().addAll(draft.getKeyframes());
                return sampler.get();
            }
            finally
            {
                sheet.channel.getAllTyped().clear();
                sheet.channel.getAllTyped().addAll(original);
            }
        });
    }

    /** Re-evaluate the displayed form for every probe, then restore its unperturbed pose. */
    private static void sampleGizmoAxes(UIFilmPanel panel, GizmoDrag drag, UIPropTransform transform, Replay replay, IEntity entity, float transition, Supplier<Matrix4f> composite)
    {
        Supplier<Matrix4f> matrixSampler = keyframeGizmoSampler(panel.replayEditor.keyframeEditor, transform, () ->
        {
            applyFormProperties(panel, replay, entity, transition);

            Matrix4f matrix = composite.get();

            return matrix == null ? new Matrix4f() : matrix;
        });

        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform.getTransform(), matrixSampler));
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(
            transform.getTransform(),
            () -> matrixSampler.get().getTranslation(new Vector3f())
        ));

        /* Restore the form to its unperturbed state */
        applyFormProperties(panel, replay, entity, transition);
    }

    /** Lay the replay's properties onto its form at the cursor, the pose everything here measures from. */
    private static void applyFormProperties(UIFilmPanel panel, Replay replay, IEntity entity, float transition)
    {
        replay.anchor.setRuntimeValue(replay.evaluateAnchor(replay.getTick(panel.getCursor()) + panel.getRunner().getTransition(transition)));
        Form form = entity.getForm();

        if (form != null)
        {
            replay.properties.applyProperties(form, replay.getTick(panel.getCursor()) + panel.getRunner().getTransition(transition));
        }
    }

    /* Picking form and form properties */

    public static void pickForm(UIKeyframeEditor keyframeEditor, ICursor cursor, Form form, String bone)
    {
        pickForm(keyframeEditor, cursor, form, bone, false);
    }

    public static void pickForm(UIKeyframeEditor keyframeEditor, ICursor cursor, Form form, String bone, boolean insert)
    {
        if (form == null || keyframeEditor == null)
        {
            return;
        }

        if (!(form instanceof IPosedForm))
        {
            UIKeyframeSheet sheet = getPreferredPropertySheet(keyframeEditor.view.getGraph(), FormUtils.getPath(form), "transform");

            if (sheet != null)
            {
                if (insert)
                {
                    insertIntoPropertySheet(keyframeEditor, "", sheet);
                }
                else
                {
                    pickProperty(keyframeEditor, cursor, "", sheet, false);
                }
            }

            return;
        }

        if (bone == null || bone.isEmpty())
        {
            return;
        }

        /* Ctrl multi-select: toggle the bone in the live pose editor without changing the
         * selected keyframe. Picking a keyframe on another track recreates the panel, which
         * resets the pose editor — so it caps the multi-selection. When a pose factory is
         * already up and owns this bone, just toggle it and stop, so the selection accumulates. */
        if (!insert && Window.isCtrlPressed()
            && keyframeEditor.editor instanceof UIPoseKeyframeFactory poseFactory
            && poseFactory.poseEditor.hasBone(bone))
        {
            IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
            keyframeEditor.view.getDopeSheet().revealSheet(graph.getSheet(graph.getSelected()));
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
                keyframeEditor.view.getDopeSheet().revealSheet(currentSheet);
                float tick = keyframeEditor.view.getTick();
                Keyframe closest = getClosestKeyframe(currentSheet, tick);
                if (closest != null)
                {
                    if (currentSheet.selection.getSelected().size() <= 1)
                    {
                        forceSelectInSheet(graph, currentSheet, closest);
                    }
                    cursor.setCursor(closest.getTick());
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
                insertIntoPropertySheet(keyframeEditor, bone, sheet);
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
        UIKeyframeSheet sheet = keyframeEditor.view.getDopeSheet().getSheet(boneKey);

        if (sheet == null)
        {
            /* Fallback: match by id ignoring case (stencil may return "head", sheet id may be "pose.bones.Head") */
            for (UIKeyframeSheet s : keyframeEditor.view.getSheets())
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
                UIKeyframeSheet poseSheet = getPreferredPropertySheet(graph, formPath, "pose");

                if (poseSheet != null)
                {
                    return poseSheet;
                }
            }

            return sheet;
        }

        return getPreferredPropertySheet(graph, formPath, "pose");
    }

    private static UIKeyframeSheet getPropertySheet(IUIKeyframeGraph graph, String formPath, String property)
    {
        for (UIKeyframeSheet sheet : graph.getKeyframes().getSheets())
        {
            if (isPropertySheet(sheet, formPath, property))
            {
                return sheet;
            }
        }

        return null;
    }

    private static UIKeyframeSheet getPreferredPropertySheet(IUIKeyframeGraph graph, String formPath, String property)
    {
        /* Prefer the property track the user is actually working in - the currently selected keyframe, then
         * the last selected sheet (remembered across clicks) - so picks and inserts stay on that track (e.g.
         * an overlay) instead of snapping back to the form's base track. */
        Keyframe selected = graph.getSelected();
        UIKeyframeSheet current = selected != null ? graph.getSheet(selected) : null;

        if (isPropertySheet(current, formPath, property))
        {
            return current;
        }

        UIKeyframeSheet last = graph.getLastSheet();

        if (isPropertySheet(last, formPath, property))
        {
            return last;
        }

        return getPropertySheet(graph, formPath, property);
    }

    /** Viewport property controls use the same nearest-key selection and insertion as pose. */
    public static void pickPropertyTrack(UIKeyframeEditor keyframeEditor, ICursor cursor, String key, boolean insert)
    {
        if (keyframeEditor == null) return;
        UIKeyframeSheet sheet = keyframeEditor.view.getDopeSheet().getSheet(key);
        if (sheet == null) return;

        if (insert) insertIntoPropertySheet(keyframeEditor, "", sheet);
        else pickProperty(keyframeEditor, cursor, "", sheet, false);
    }

    private static void pickProperty(UIKeyframeEditor keyframeEditor, ICursor cursor, String bone, String key, boolean insert)
    {
        UIKeyframeSheet sheet = keyframeEditor.view.getDopeSheet().getSheet(key);

        if (sheet != null)
        {
            pickProperty(keyframeEditor, cursor, bone, sheet, insert);
        }
    }

    private static void pickProperty(UIKeyframeEditor keyframeEditor, ICursor filmPanel, String bone, UIKeyframeSheet sheet, boolean insert)
    {
        keyframeEditor.view.getDopeSheet().revealSheet(sheet);
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        float tick = keyframeEditor.view.getTick();

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
            filmPanel.setCursor(closest.getTick());
        }
        else
        {
            updatePoseEditorBoneSelection(keyframeEditor, boneForEditor);
        }
    }

    private static Keyframe getClosestKeyframe(UIKeyframeSheet sheet, float tick)
    {
        KeyframeSegment segment = sheet.channel.find(tick);

        return segment != null ? segment.getClosest() : null;
    }

    private static Keyframe getKeyframeAt(UIKeyframeSheet sheet, float tick)
    {
        for (Object o : sheet.channel.getKeyframes())
        {
            Keyframe keyframe = (Keyframe) o;

            if (keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        return null;
    }

    /**
     * Insert into a pose or transform track: select the keyframe already sitting at the cursor
     * (so the gesture never duplicates it), otherwise add a fresh one. For pose tracks,
     * the bone is also highlighted in the pose editor.
     */
    private static void insertIntoPropertySheet(UIKeyframeEditor keyframeEditor, String bone, UIKeyframeSheet sheet)
    {
        keyframeEditor.view.getDopeSheet().revealSheet(sheet);
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        float tick = keyframeEditor.view.getTick();
        Keyframe existing = getKeyframeAt(sheet, tick);

        if (existing != null)
        {
            if (sheet.selection.getSelected().size() <= 1)
            {
                forceSelectInSheet(graph, sheet, existing);
            }
        }
        else
        {
            Keyframe keyframe = graph.addKeyframe(sheet, tick, null);
            graph.selectKeyframe(keyframe);
        }

        updatePoseEditorBoneSelection(keyframeEditor, bone);
    }

    private static boolean isPoseSheet(UIKeyframeSheet sheet, String formPath)
    {
        return isPropertySheet(sheet, formPath, "pose");
    }

    private static boolean isPropertySheet(UIKeyframeSheet sheet, String formPath, String property)
    {
        if (sheet == null || sheet.id == null)
        {
            return false;
        }

        String prefix = formPath.isEmpty() ? "" : formPath + FormUtils.PATH_SEPARATOR;

        /* Match the base property exactly to exclude per-bone tracks, plus all its overlays. */
        return sheet.id.equals(prefix + property) || sheet.id.startsWith(prefix + property + "_overlay");
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
        float tick, String animationKey, boolean onlyKeyframes, int length, int step
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

    private static void fillAnimationPose(UIKeyframeSheet sheet, float i, ModelInstance model, IEntity entity, Animation animation, float current)
    {
        model.model.resetPose();
        model.model.apply(entity, animation, i, 1F, 0F, false);

        int insert = sheet.channel.insert(current + i, model.model.createPose());

        sheet.selection.add(insert);
    }

    @SuppressWarnings("unchecked")
    public static boolean posesToLimbTracks(FormProperties properties, UIKeyframeSheet poseSheet)
    {
        if (properties == null || poseSheet == null || poseSheet.getPosedForm() == null)
        {
            return false;
        }

        TrackId track = TrackId.parse(poseSheet.id);
        if (track == null) return false;
        String formPath = track.formPath();
        Form form = UIReplaysEditor.getSheetForm(poseSheet);
        IBoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(form);

        if (!(form instanceof IPosedForm) || hierarchy == null)
        {
            return false;
        }

        ModelInstance model = form instanceof ModelForm targetModelForm ? ModelFormRenderer.getModel(targetModelForm) : null;
        List<String> bones = new ArrayList<>(hierarchy.getGroupKeysInHierarchyOrder());

        bones.removeIf((bone) -> model != null && PoseBones.isHidden(model.getDisabledBones(), bone));

        List<Keyframe<Pose>> selectedKeyframes = (List<Keyframe<Pose>>) (List<?>) poseSheet.selection.getSelected();

        if (selectedKeyframes.isEmpty() || bones.isEmpty() || selectedKeyframes.stream().anyMatch(keyframe -> keyframe.getValue() == null))
        {
            return false;
        }

        /* Keep rest keys for animated bones, but do not create tracks for untouched bones. */
        bones.removeIf(bone -> selectedKeyframes.stream().noneMatch(keyframe ->
        {
            PoseTransform transform = keyframe.getValue().get(bone);

            return transform != null && !transform.isDefault();
        }));

        if (bones.isEmpty())
        {
            return false;
        }

        /* Capture the whole track collection before creating channels, so undo also removes them. */
        BaseValue.edit(properties, target ->
        {
            for (Keyframe<Pose> keyframe : selectedKeyframes)
            {
                Pose pose = keyframe.getValue();
                float tick = keyframe.getTick();

                for (String bone : bones)
                {
                    KeyframeChannel<PoseTransform> limbChannel = (KeyframeChannel<PoseTransform>) target.getOrCreate(TrackId.bone(formPath, bone));

                    if (limbChannel == null)
                    {
                        continue;
                    }

                    /* Include rest keys so the bone can return to its original pose. */
                    PoseTransform transform = pose.get(bone);
                    PoseTransform copy = transform == null ? new PoseTransform() : (PoseTransform) transform.copy();
                    int index = limbChannel.insert(tick, copy);
                    Keyframe<PoseTransform> limbKf = limbChannel.get(index);

                    limbKf.copyOverExtra(keyframe);
                }
            }

            poseSheet.selection.removeSelected();
        });

        return true;
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

        if (!bone.isEmpty())
        {
            IBoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(form);
            ModelInstance model = form instanceof ModelForm modelForm ? ModelFormRenderer.getModel(modelForm) : null;

            if (hierarchy == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : hierarchy.getAdjacentGroups(bone))
                {
                    if (model != null && PoseBones.isHidden(model.getDisabledBones(), modelGroup))
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

        if (!bone.isEmpty())
        {
            IBoneHierarchy hierarchy = FormUtilsClient.getBoneHierarchy(form);
            ModelInstance model = form instanceof ModelForm modelForm ? ModelFormRenderer.getModel(modelForm) : null;

            if (hierarchy == null)
            {
                return;
            }

            context.replaceContextMenu((menu) ->
            {
                for (String modelGroup : hierarchy.getHierarchyGroups(bone))
                {
                    if (model != null && PoseBones.isHidden(model.getDisabledBones(), modelGroup))
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
