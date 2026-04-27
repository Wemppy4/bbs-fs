package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.PerLimbService;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
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

public class UIReplaysEditorUtils
{
    private static final int BONE_TRACK_HUE_COUNT = 12;

    public static void insertPoseKeyframesAtTick(Replay replay, float tick)
    {
        if (replay == null)
        {
            return;
        }

        BaseValue.edit(replay.properties, (props) ->
        {
            for (KeyframeChannel<?> channel : props.properties.values())
            {
                if (!PerLimbService.isPoseBoneChannel(channel.getId()))
                {
                    continue;
                }

                KeyframeChannel<PoseTransform> poseChannel = (KeyframeChannel<PoseTransform>) channel;
                KeyframeSegment<PoseTransform> segment = poseChannel.find(tick);
                PoseTransform value = segment != null ? segment.createInterpolated() : new PoseTransform();

                int index = poseChannel.insert(tick, value);
                Keyframe<PoseTransform> kf = poseChannel.get(index);

                Keyframe<PoseTransform> template = segment != null ? segment.a : null;
                if (template != null && template != kf)
                {
                    kf.copyOverExtra(template);
                }
            }
        });
    }

    public static void addBoneTrackSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        addBoneTrackSheets(modelForm, properties, out, null);
    }

    public static void addBoneTrackSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out, Map<String, Integer> depthBySheetId)
    {
        if (!modelForm.boneTracks.get())
        {
            return;
        }

        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        IModel iModel = model.model;
        List<String> bones = iModel.getGroupKeysInHierarchyOrder();
        Map<String, Integer> parentToColor = new HashMap<>();
        int[] hueIndex = {0};

        for (String bone : bones)
        {
            if (model.disabledBones.contains(bone))
            {
                continue;
            }

            String parent = iModel.getParentGroupKey(bone);
            int color = parentToColor.computeIfAbsent(parent, (p) ->
                Colors.HSVtoRGB((hueIndex[0]++ % BONE_TRACK_HUE_COUNT) / (float) BONE_TRACK_HUE_COUNT, 0.7F, 0.7F).getRGBColor()
            );

            String path = FormUtils.getPath(modelForm);
            String boneKey = PerLimbService.toPoseBoneKey(path, bone);
            String title = path.isEmpty() ? bone : path + "/" + bone;
            KeyframeChannel channel = properties.registerChannel(boneKey, KeyframeFactories.POSE_TRANSFORM);
            ValueTransform transform = new ValueTransform(boneKey, new PoseTransform());

            out.add(new UIKeyframeSheet(boneKey, IKey.constant(title), color, false, channel, transform, true));

            if (depthBySheetId != null)
            {
                depthBySheetId.put(boneKey, getBoneDepth(iModel, bone));
            }
        }
    }

    public static void addIKTargetSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        addIKTargetSheets(modelForm, properties, out, null);
    }

    public static void addIKTargetSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out, Map<String, Integer> depthBySheetId)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        model.form = modelForm;
        List<String> controllers = ModelIKRuntime.getControllers(model);
        String path = FormUtils.getPath(modelForm);

        for (String controller : controllers)
        {
            if (controller == null || controller.isEmpty())
            {
                continue;
            }

            String id = PerLimbService.toIKTargetKey(path, controller);
            String title = path.isEmpty() ? "IK/" + controller : path + "/IK/" + controller;

            addTargetSheet(out, properties, id, title, Colors.CYAN, Icons.LIMB);
        }
    }

    public static void addPhysicsTargetSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        ModelPhysicsConfig physics = null;
        if (modelForm.physics.get() instanceof mchorse.bbs_mod.data.types.MapType map)
        {
            physics = ModelPhysicsIO.fromData(map);
        }

        if (physics == null || physics.bones() == null)
        {
            return;
        }

        String path = FormUtils.getPath(modelForm);

        for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : physics.bones().entrySet())
        {
            String rootBone = entry.getKey();
            String id = PerLimbService.toPhysicsTargetKey(path, rootBone);
            String title = path.isEmpty() ? "Physics/" + rootBone : path + "/Physics/" + rootBone;

            addTargetSheet(out, properties, id, title, Colors.MAGENTA, Icons.TIME);
        }
    }

    private static void addTargetSheet(List<UIKeyframeSheet> out, FormProperties properties, String id, String title, int color, Icon icon)
    {
        KeyframeChannel channel = properties.registerChannel(id, KeyframeFactories.ANCHOR);

        out.add(new UIKeyframeSheet(id, IKey.constant(title), color, false, channel, null).icon(icon));
    }

    private static int getBoneDepth(IModel model, String bone)
    {
        int depth = 0;
        String current = bone;

        while (current != null && !current.isEmpty())
        {
            current = model.getParentGroupKey(current);

            if (current != null && !current.isEmpty())
            {
                depth++;
            }
        }

        return Math.max(0, depth);
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
    }

    /**
     * Ray gizmo context for the film / replay viewport: same camera-origin and
     * axes as {@link GizmoDrag#fromRenderedGizmo}, plus numeric
     * {@link GizmoDrag#computeRotateAxes} / {@link GizmoDrag#computeTranslateJacobian}
     * driven by the composite bone matrix {@code target.mul(bone)} so replay
     * {@code bodyYaw}, anchor parents, and other film-only transforms match
     * {@link BaseFilmController#renderEntity}.
     */
    public static GizmoDrag buildFilmGizmoDrag(
        UIFilmPanel panel,
        mchorse.bbs_mod.camera.Camera camera,
        Area viewport,
        UIPropTransform transform,
        float transition
    )
    {
        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(camera, viewport);

        if (drag == null || transform == null || transform.getTransform() == null || panel == null)
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
        IEntity entity = panel.getController().getCurrentEntity();

        if (bone == null || bone.a == null || replay == null || entity == null)
        {
            return drag;
        }

        java.util.function.Supplier<Matrix4f> matrixSampler = () ->
        {
            Form form = entity.getForm();
            float tick = panel.getCursor() + (panel.getRunner().isRunning() ? transition : 0F);

            if (form != null)
            {
                /* Force-apply the perturbed keyframe state to the entity's form 
                 * so that FormUtilsClient's matrix cache updates for this sample. */
                replay.properties.applyProperties(form, tick);
            }

            Matrix4f m = BaseFilmController.getGizmoBoneCompositeMatrix(
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

        return drag;
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

        String path = FormUtils.getPath(form);
        String boneKey = PerLimbService.toPoseBoneKey(path, bone);

        if (!insert)
        {
            IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
            Keyframe selected = graph.getSelected();
            UIKeyframeSheet currentSheet = selected != null ? graph.getSheet(selected) : null;
            PerLimbService.PoseBonePath currentPath = currentSheet != null && currentSheet.id != null ? PerLimbService.parsePoseBonePath(currentSheet.id) : null;
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
            IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
            Keyframe selected = graph.getSelected();
            UIKeyframeSheet currentSheet = selected != null ? graph.getSheet(selected) : null;

            if (isPoseSheet(currentSheet, path))
            {
                return;
            }

            pickProperty(keyframeEditor, cursor, bone, boneKey, true);
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

        if (sheet != null)
        {
            return sheet;
        }

        /* Fallback: match by id ignoring case (stencil may return "head", sheet id may be "pose.bones.Head") */
        for (UIKeyframeSheet s : graph.getSheets())
        {
            if (s.id != null && s.id.equalsIgnoreCase(boneKey))
            {
                return s;
            }
        }

        return getActivePoseSheet(keyframeEditor, formPath);
    }

    private static UIKeyframeSheet getActivePoseSheet(UIKeyframeEditor keyframeEditor, String formPath)
    {
        IUIKeyframeGraph graph = keyframeEditor.view.getGraph();
        Keyframe selected = graph.getSelected();
        UIKeyframeSheet sheet = selected != null ? graph.getSheet(selected) : graph.getLastSheet();

        if (sheet == null || sheet.id == null)
        {
            return null;
        }

        String name = StringUtils.fileName(sheet.id);

        if (!name.startsWith("pose"))
        {
            return null;
        }

        if (sheet.property != null)
        {
            Form sheetForm = FormUtils.getForm(sheet.property);

            if (sheetForm != null)
            {
                return FormUtils.getPath(sheetForm).equals(formPath) ? sheet : null;
            }
        }

        if (formPath.isEmpty())
        {
            return sheet.id.contains(FormUtils.PATH_SEPARATOR) ? null : sheet;
        }

        String prefix = formPath + FormUtils.PATH_SEPARATOR;

        return sheet.id.startsWith(prefix) ? sheet : null;
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

        PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);
        String boneForEditor = path != null ? path.bone() : bone;

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

    private static boolean isPoseSheet(UIKeyframeSheet sheet, String formPath)
    {
        if (sheet == null || sheet.id == null)
        {
            return false;
        }

        String prefix = formPath.isEmpty() ? "" : formPath + FormUtils.PATH_SEPARATOR;

        return sheet.id.equals(prefix + "pose") || sheet.id.equals(prefix + "pose_overlay");
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

        bones.removeIf(model.disabledBones::contains);

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
                String boneKey = PerLimbService.toPoseBoneKey(formPath, bone);
                KeyframeChannel<PoseTransform> limbChannel = (KeyframeChannel<PoseTransform>) replay.properties.getOrCreate(form, boneKey);

                if (limbChannel == null)
                {
                    continue;
                }

                PoseTransform transform = pose.get(bone);
                PoseTransform copy = (PoseTransform) transform.copy();
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
        String path = FormUtils.getPath(modelForm);

        BaseValue.edit(replay.properties, (props) ->
        {
            for (String controller : controllers)
            {
                String id = PerLimbService.toIKTargetKey(path, controller);
                KeyframeChannel channel = props.properties.get(id);

                if (channel != null)
                {
                    channel.removeAll();
                }
            }
        });
    }

    /* Offer bone hierarchy options */

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
                    if (model.disabledBones.contains(modelGroup))
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
                    if (model.disabledBones.contains(modelGroup))
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
