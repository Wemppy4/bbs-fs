package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.TransformSpace;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.cubic.ik.IKControl;
import mchorse.bbs_mod.cubic.ik.IKControls;
import mchorse.bbs_mod.cubic.ik.ModelIKConfig;
import mchorse.bbs_mod.cubic.ik.ModelIKIO;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsConfig;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsIO;
import mchorse.bbs_mod.cubic.physics.PhysicsControl;
import mchorse.bbs_mod.cubic.physics.PhysicsControls;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.PerLimbService;
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
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIPoseTransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UITransformKeyframeFactory;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValueLink;
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

                insertPoseTransformKeyframe((KeyframeChannel<PoseTransform>) channel, tick, null);
            }
        });
    }

    public static <T> Keyframe<T> ensureKeyframe(UIKeyframeSheet sheet, float tick)
    {
        if (sheet == null)
        {
            return null;
        }

        for (Keyframe<T> keyframe : (List<Keyframe<T>>) sheet.channel.getKeyframes())
        {
            if (keyframe.getTick() == tick)
            {
                return keyframe;
            }
        }

        KeyframeSegment<T> segment = sheet.channel.find(tick);
        BaseValueBasic property = sheet.property;
        Keyframe<T> template = null;
        T value;

        if (segment != null)
        {
            value = segment.createInterpolated();
            template = segment.a;
        }
        else if (property != null)
        {
            value = (T) sheet.channel.getFactory().copy(property.get());
        }
        else if (sheet.seed != null)
        {
            value = (T) sheet.seed.get();
        }
        else
        {
            value = (T) sheet.channel.getFactory().createEmpty();
        }

        int index = sheet.channel.insert(tick, value);
        Keyframe<T> keyframe = (Keyframe<T>) sheet.channel.get(index);

        if (template != null && template != keyframe)
        {
            keyframe.copyOverExtra(template);
        }

        return keyframe;
    }

    public static <T> void forEachSelectedKeyframe(UIKeyframes editor, Keyframe<?> keyframe, Consumer<Keyframe<T>> consumer)
    {
        if (editor == null || keyframe == null)
        {
            return;
        }

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel.getFactory() != keyframe.getFactory())
            {
                continue;
            }

            for (Keyframe selected : sheet.selection.getSelected())
            {
                consumer.accept((Keyframe<T>) selected);
            }
        }
    }

    public static <T> void forEachRecordedKeyframe(UIKeyframes editor, Keyframe<?> keyframe, int tick, Consumer<Keyframe<T>> consumer)
    {
        if (editor == null || keyframe == null)
        {
            return;
        }

        for (UIKeyframeSheet sheet : editor.getGraph().getSheets())
        {
            if (sheet.channel.getFactory() != keyframe.getFactory() || sheet.selection.getSelected().isEmpty())
            {
                continue;
            }

            Keyframe<T> recorded = ensureKeyframe(sheet, tick);

            if (recorded != null)
            {
                consumer.accept(recorded);
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

            out.add(new UIKeyframeSheet(boneKey, IKey.constant(title), color, false, channel, transform, true).form(modelForm));

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
            String title = path.isEmpty() ? "ik/" + controller : path + "/ik/" + controller;

            addTargetSheet(out, properties, modelForm, id, title, Colors.CYAN, null);
        }
    }

    /**
     * One IK-controls track per form (only if it has enabled chains): a single
     * keyframe sheet whose value holds the per-chain scalars (weight, softness,
     * pole, enabled), layered over the form's IK config at playback — mirrors the
     * single pose track. It is not a form property, so it carries its owning form
     * for the editor to list chains.
     */
    public static void addIKControlSheet(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        model.form = modelForm;

        if (ModelIKRuntime.getControllers(model).isEmpty())
        {
            return;
        }

        String path = FormUtils.getPath(modelForm);
        String id = PerLimbService.toIKControlKey(path);
        String title = path.isEmpty() ? "ik" : path + "/ik";

        KeyframeChannel channel = properties.registerChannel(id, KeyframeFactories.IK);

        out.add(new UIKeyframeSheet(id, IKey.constant(title), Colors.YELLOW, false, channel, null)
            .icon(Icons.LIMB).form(modelForm).seed(() -> buildIKControls(modelForm)));
    }

    /** A fully populated IK-controls value seeded from the form's IK config (one entry per enabled chain), so a fresh keyframe matches what the editor shows instead of an empty container that drifts to defaults. */
    private static IKControls buildIKControls(ModelForm modelForm)
    {
        IKControls controls = new IKControls();

        if (modelForm.ik.get() instanceof MapType map)
        {
            ModelIKConfig config = ModelIKIO.fromData(map);

            if (config != null && config.chains() != null)
            {
                for (ModelIKConfig.Chain chain : config.chains())
                {
                    if (chain == null || !chain.enabled() || chain.tip() == null || chain.tip().isEmpty())
                    {
                        continue;
                    }

                    IKControl control = controls.get(chain.tip());

                    control.weight = chain.weight();
                    control.softness = chain.softness();
                    control.pole = chain.pole();
                    control.enabled = chain.enabled();
                }
            }
        }

        return controls;
    }

    public static void addPoleTargetSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        model.form = modelForm;
        List<String> controllers = ModelIKRuntime.getPoleControllers(model);
        String path = FormUtils.getPath(modelForm);

        for (String controller : controllers)
        {
            if (controller == null || controller.isEmpty())
            {
                continue;
            }

            String id = PerLimbService.toPoleTargetKey(path, controller);
            String title = path.isEmpty() ? "pole/" + controller : path + "/pole/" + controller;

            addTargetSheet(out, properties, modelForm, id, title, Colors.ORANGE, null);
        }
    }

    /**
     * One physics-controls track per form (only if it has physics chains): a single
     * keyframe sheet whose value holds the per-chain scalars (weight, gravity,
     * damping, stiffness, enabled), keyed by root bone and layered over the form's
     * physics config at playback — mirrors {@link #addIKControlSheet}. It is not a
     * form property, so it carries its owning form for the editor to list chains.
     */
    public static void addPhysicsControlSheet(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelPhysicsConfig physics = null;

        if (modelForm.physics.get() instanceof MapType map)
        {
            physics = ModelPhysicsIO.fromData(map);
        }

        if (physics == null || physics.bones() == null || physics.bones().isEmpty())
        {
            return;
        }

        String path = FormUtils.getPath(modelForm);
        String id = PerLimbService.toPhysicsControlKey(path);
        String title = path.isEmpty() ? "physics" : path + "/physics";

        KeyframeChannel channel = properties.registerChannel(id, KeyframeFactories.PHYSICS);

        out.add(new UIKeyframeSheet(id, IKey.constant(title), Colors.GREEN, false, channel, null)
            .icon(Icons.DROP).form(modelForm).seed(() -> buildPhysicsControls(modelForm)));
    }

    /** A fully populated physics-controls value seeded from the form's physics config (one entry per chain root), mirroring {@link #buildIKControls}. */
    private static PhysicsControls buildPhysicsControls(ModelForm modelForm)
    {
        PhysicsControls controls = new PhysicsControls();

        if (modelForm.physics.get() instanceof MapType map)
        {
            ModelPhysicsConfig config = ModelPhysicsIO.fromData(map);

            if (config != null && config.bones() != null)
            {
                for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
                {
                    ModelPhysicsConfig.Bone bone = entry.getValue();

                    if (bone == null)
                    {
                        continue;
                    }

                    PhysicsControl control = controls.get(entry.getKey());

                    control.weight = bone.weight();
                    control.gravity = bone.gravity();
                    control.damping = bone.damping();
                    control.stiffness = bone.stiffness();
                }
            }
        }

        return controls;
    }

    public static void addPhysicsTargetSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        ModelPhysicsConfig physics = null;

        if (modelForm.physics.get() instanceof MapType map)
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
            String title = path.isEmpty() ? "physics/" + rootBone : path + "/physics/" + rootBone;

            addTargetSheet(out, properties, modelForm, id, title, Colors.MAGENTA, Icons.TIME);
        }
    }

    /**
     * One texture track per model material (OBJ material name / BOBJ mesh name), enumerated from
     * the loaded model. Each is a LINK channel layered over the material's static default at
     * playback - mirrors the bone tracks. Lives in the Model category beside the main texture track.
     */
    public static void addMaterialTextureSheets(ModelForm modelForm, FormProperties properties, List<UIKeyframeSheet> out)
    {
        ModelInstance model = ModelFormRenderer.getModel(modelForm);

        if (model == null)
        {
            return;
        }

        String path = FormUtils.getPath(modelForm);

        for (String material : model.materials)
        {
            if (material == null || material.isEmpty())
            {
                continue;
            }

            String id = PerLimbService.toMaterialTextureKey(path, material);
            String title = path.isEmpty() ? "texture/" + material : path + "/texture/" + material;
            KeyframeChannel channel = properties.registerChannel(id, KeyframeFactories.LINK);

            /* Seed the sheet's value with the material's current default texture (editor pick, else
             * folder/Kd, else the form/model default) so a new keyframe starts there instead of null -
             * the texture picker then opens at that texture rather than the root. */
            Link materialDefault = modelForm.materialTextures.getLink(material);

            if (materialDefault == null)
            {
                materialDefault = model.getMaterialTexture(material, model.texture);
            }

            ValueLink property = new ValueLink(id, materialDefault);

            out.add(new UIKeyframeSheet(id, IKey.constant(title), Colors.BLUE, false, channel, property).icon(Icons.MATERIAL).form(modelForm));
        }
    }

    /** Collect every track a single form contributes to the timeline (its own properties plus model sub-tracks), used to populate the per-form track filter. */
    public static List<UIKeyframeSheet> collectFormTrackSheets(Form form)
    {
        List<UIKeyframeSheet> sheets = new ArrayList<>();

        if (form == null)
        {
            return sheets;
        }

        FormProperties properties = new FormProperties("");

        for (BaseValue property : form.getAll())
        {
            if (!property.isVisible() || property.getId().equals("anchor"))
            {
                continue;
            }

            String key = property.getId();
            KeyframeChannel channel = properties.getOrCreate(form, key);

            if (channel == null)
            {
                continue;
            }

            BaseValueBasic formProperty = FormUtils.getProperty(form, key);

            sheets.add(new UIKeyframeSheet(UIReplaysEditor.getColor(key), false, channel, formProperty).icon(UIReplaysEditor.getIcon(key)));
        }

        if (form instanceof ModelForm modelForm)
        {
            addMaterialTextureSheets(modelForm, properties, sheets);
            addPhysicsControlSheet(modelForm, properties, sheets);
            addPhysicsTargetSheets(modelForm, properties, sheets);
            addBoneTrackSheets(modelForm, properties, sheets);
            addIKControlSheet(modelForm, properties, sheets);
            addIKTargetSheets(modelForm, properties, sheets);
            addPoleTargetSheets(modelForm, properties, sheets);
        }

        return sheets;
    }

    private static void addTargetSheet(List<UIKeyframeSheet> out, FormProperties properties, ModelForm modelForm, String id, String title, int color, Icon icon)
    {
        KeyframeChannel channel = properties.registerChannel(id, KeyframeFactories.ANCHOR);

        out.add(new UIKeyframeSheet(id, IKey.constant(title), color, false, channel, null).icon(icon).form(modelForm));
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
        Camera camera,
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

        Pair<String, TransformSpace> bone = keyframeEditor.getBone();
        Replay replay = panel.replayEditor.getReplay();
        IEntity entity = panel.getController().getCurrentEntity();

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

            Matrix4f m = BaseFilmController.getGizmoAnchorCompositeMatrix(
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
        List<String> poleControllers = ModelIKRuntime.getPoleControllers(model);
        String path = FormUtils.getPath(modelForm);

        BaseValue.edit(replay.properties, (props) ->
        {
            for (String controller : controllers)
            {
                removeChannel(props, PerLimbService.toIKTargetKey(path, controller));
            }

            for (String controller : poleControllers)
            {
                removeChannel(props, PerLimbService.toPoleTargetKey(path, controller));
            }
        });
    }

    private static void removeChannel(FormProperties props, String id)
    {
        KeyframeChannel channel = props.properties.get(id);

        if (channel != null)
        {
            channel.removeAll();
        }
    }

    /* Offer bone hierarchy options */

    /** Leaf bone pick for {@link #pickFormWithOffers}; {@code insert}
     *  distinguishes a left-click select from a middle-click insert. */
    public interface FormPicker
    {
        void pick(Form form, String bone, boolean insert);
    }

    /**
     * Shared viewport bone-pick gesture for the film, replay and animation
     * state editors: left / Ctrl+right select, middle inserts, Ctrl offers
     * adjacent bones, Shift offers the hierarchy. The leaf {@code picker}
     * supplies the editor-specific selection. Returns whether the click
     * was consumed.
     */
    public static boolean pickFormWithOffers(UIContext context, Pair<Form, String> pair, FormPicker picker)
    {
        boolean select = context.mouseButton == 0 || (context.mouseButton == 2 && Window.isCtrlPressed());
        boolean insert = context.mouseButton == 1;

        if (!select && !insert)
        {
            return false;
        }

        if (Window.isCtrlPressed())
        {
            offerAdjacent(context, pair.a, pair.b, (bone) -> picker.pick(pair.a, bone, insert));
        }
        else if (Window.isShiftPressed())
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
