package mchorse.bbs_mod.ui.forms.editors.states.keyframes;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.PoseForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.settings.values.base.BaseValueBasic;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.film.replays.overlays.UIAnimationToPoseOverlayPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.UIKeyframeDopeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class UIAnimationStateEditor extends UIElement
{
    public UIKeyframeEditor keyframeEditor;

    public UIFormEditor editor;
    public UIElement editArea;

    private AnimationState state;
    private Set<String> keys = new LinkedHashSet<>();

    public UIAnimationStateEditor(UIFormEditor editor)
    {
        this.editor = editor;

        this.editArea = new UIElement();
        this.editArea.relative(this)
            .x(BBSSettings.editorLayoutSettings.getStateEditorSizeH())
            .wTo(this.area, 1F)
            .h(1F);

        UIDraggable draggable = new UIDraggable((context) ->
        {
            float fx = (context.mouseX - this.area.x) / (float) this.area.w;
            float fy = -(context.mouseY - this.getParent().area.ey()) / (float) this.getParent().area.h;

            BBSSettings.editorLayoutSettings.setStateEditorSizeV(fy);
            BBSSettings.editorLayoutSettings.setStateEditorSizeH(fx);

            this.h(BBSSettings.editorLayoutSettings.getStateEditorSizeV());
            this.editArea.x(BBSSettings.editorLayoutSettings.getStateEditorSizeH());
            this.getParent().resize();
        });
        draggable.cursors(GLFW.GLFW_CROSSHAIR_CURSOR, GLFW.GLFW_CROSSHAIR_CURSOR);

        draggable.reference(() -> new Vector2i(this.editArea.area.x, this.area.y));
        draggable.rendering((context) ->
        {
            int size = 5;
            int x = this.editArea.area.x + 3;
            int y = this.editArea.area.y + 3;

            context.batcher.box(x, y, x + 1, y + size, Colors.WHITE);
            context.batcher.box(x, y - 1, x + size, y, Colors.WHITE);

            x = this.editArea.area.x - 3;
            y = this.editArea.area.y + 3;

            context.batcher.box(x - 1, y, x, y + size, Colors.WHITE);
            context.batcher.box(x - size, y - 1, x, y, Colors.WHITE);
        });

        draggable.hoverOnly().relative(this.editArea).w(40).h(6).anchorX(0.5F);

        this.add(this.editArea, draggable);
    }

    public AnimationState getState()
    {
        return this.state;
    }

    public void setState(AnimationState state)
    {
        UIKeyframes lastEditor = null;
        Set<String> expandedPoseIds = Collections.emptySet();

        if (this.keyframeEditor != null)
        {
            lastEditor = this.keyframeEditor.view;
            expandedPoseIds = new LinkedHashSet<>(lastEditor.getDopeSheet().getExpandedPoseTabIds());

            this.keyframeEditor.removeFromParent();
            this.keyframeEditor = null;
        }

        this.state = state;

        if (this.state == null)
        {
            return;
        }

        List<UIKeyframeSheet> sheets = new ArrayList<>();
        Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs = new HashMap<>();
        Map<UIKeyframeSheet, Integer> poseTabDepths = new HashMap<>();

        /* Form properties */
        Form lastForm = null;
        List<UIKeyframeSheet> formSheets = new ArrayList<>();

        for (String key : FormUtils.collectPropertyPaths(this.editor.form))
        {
            KeyframeChannel property = this.state.properties.getOrCreate(this.editor.form, key);

            if (property != null)
            {
                BaseValueBasic formProperty = FormUtils.getProperty(this.editor.form, key);
                Form form = formProperty.getParent() instanceof Form f ? f : null;

                if (form != lastForm)
                {
                    if (lastForm != null)
                    {
                        this.flushForm(sheets, formSheets, lastForm, poseTabs, poseTabDepths);
                    }

                    lastForm = form;
                }

                UIKeyframeSheet sheet = new UIKeyframeSheet(UIReplaysEditor.getColor(key), false, property, formProperty);

                formSheets.add(sheet.icon(UIReplaysEditor.getIcon(key)));
            }
        }

        if (lastForm != null)
        {
            this.flushForm(sheets, formSheets, lastForm, poseTabs, poseTabDepths);
        }

        this.keys.clear();
        Map<String, Integer> keyToColor = new HashMap<>();
        Map<String, String> keyToLabel = new HashMap<>();

        for (UIKeyframeSheet sheet : sheets)
        {
            String filterKey = UIReplaysEditor.getSheetFilterKey(sheet);

            this.keys.add(filterKey);
            keyToColor.put(filterKey, sheet.color);
            keyToLabel.put(filterKey, sheet.title.get());
        }

        sheets.removeIf((v) ->
        {
            if (v.id.equals("anchor"))
            {
                return true;
            }

            String filterKey = UIReplaysEditor.getSheetFilterKey(v);

            for (String s : BBSSettings.disabledSheets.get())
            {
                if (filterKey.equals(s) || v.id.equals(s) || v.id.endsWith("/" + s))
                {
                    return true;
                }
            }

            Form owner = UIReplaysEditor.getSheetForm(v);

            if (owner != null)
            {
                Set<String> ownerDisabled = owner.disabledTracks.get();

                return ownerDisabled.contains(Form.DISABLED_ALL) || ownerDisabled.contains(filterKey);
            }

            return false;
        });

        Set<UIKeyframeSheet> kept = new LinkedHashSet<>(sheets);
        poseTabs.entrySet().removeIf((entry) ->
        {
            entry.getValue().removeIf((child) -> !kept.contains(child));

            return !kept.contains(entry.getKey()) || entry.getValue().isEmpty();
        });
        poseTabDepths.keySet().retainAll(kept);

        lastForm = null;

        for (UIKeyframeSheet sheet : sheets)
        {
            Form form = UIReplaysEditor.getSheetForm(sheet);

            if (!Objects.equals(lastForm, form))
            {
                sheet.separator = true;
            }

            lastForm = form;
        }

        if (!sheets.isEmpty())
        {
            this.keyframeEditor = new UIKeyframeEditor((consumer) -> new UIAnimationStateKeyframes(this.editor, consumer)).target(this.editArea);
            this.keyframeEditor.relative(this).h(1F).wTo(this.editArea.area);
            this.keyframeEditor.setUndoId("form_animation_state_keyframe_editor");

            /* Reset */
            if (lastEditor != null)
            {
                this.keyframeEditor.view.copyViewport(lastEditor);
            }

            this.keyframeEditor.view.duration(() -> this.state.duration.get());
            this.keyframeEditor.view.context((menu) ->
            {
                if (this.editor.form instanceof ModelForm modelForm)
                {
                    int mouseY = this.getContext().mouseY;
                    UIKeyframeSheet sheet = this.keyframeEditor.view.getGraph().getSheet(mouseY);

                    if (sheet != null && sheet.channel.getFactory() == KeyframeFactories.POSE && sheet.id.equals("pose"))
                    {
                        menu.action(Icons.POSE, UIKeys.FILM_REPLAY_CONTEXT_ANIMATION_TO_KEYFRAMES, () ->
                        {
                            ModelInstance model = ModelFormRenderer.getModel(modelForm);

                            if (model != null)
                            {
                                UIOverlay.addOverlay(this.getContext(), new UIAnimationToPoseOverlayPanel((animationKey, onlyKeyframes, length, step) ->
                                {
                                    int current = this.editor.getCursor();
                                    IEntity entity = this.editor.renderer.getTargetEntity();

                                    UIReplaysEditorUtils.animationToPoseKeyframes(this.keyframeEditor, sheet, modelForm, entity, current, animationKey, onlyKeyframes, length, step);
                                }, modelForm, sheet), 200, 197);
                            }
                        });
                    }
                }

                if (this.keyframeEditor.view.getGraph() instanceof UIKeyframeDopeSheet)
                {
                    menu.action(Icons.FILTER, UIKeys.FILM_REPLAY_FILTER_SHEETS, () ->
                    {
                        UIKeyframeSheetFilterOverlayPanel panel = new UIKeyframeSheetFilterOverlayPanel(
                            BBSSettings.disabledSheets.get(),
                            this.keys,
                            keyToColor,
                            keyToLabel
                        );

                        UIOverlay.addOverlay(this.getContext(), panel, 240, 0.9F);

                        panel.onClose((e) ->
                        {
                            this.setState(this.state);
                            BBSSettings.disabledSheets.set(BBSSettings.disabledSheets.get());
                        });
                    });
                }
            });

            for (UIKeyframeSheet sheet : sheets)
            {
                this.keyframeEditor.view.addSheet(sheet);
            }

            this.keyframeEditor.view.getDopeSheet().configurePoseTabs(poseTabs, poseTabDepths, expandedPoseIds);

            this.addAfter(this.editArea, this.keyframeEditor);
        }

        this.resize();

        if (this.keyframeEditor != null && lastEditor == null)
        {
            this.keyframeEditor.view.resetView();
        }
    }

    private void flushForm(
        List<UIKeyframeSheet> sheets,
        List<UIKeyframeSheet> formSheets,
        Form form,
        Map<UIKeyframeSheet, List<UIKeyframeSheet>> poseTabs,
        Map<UIKeyframeSheet, Integer> poseTabDepths
    )
    {
        String path = FormUtils.getPath(form);
        String poseId = path.isEmpty() ? "pose" : path + FormUtils.PATH_SEPARATOR + "pose";
        UIKeyframeSheet poseSheet = null;

        for (UIKeyframeSheet sheet : formSheets)
        {
            if (poseId.equals(sheet.id) && sheet.channel.getFactory() == KeyframeFactories.POSE)
            {
                poseSheet = sheet;
                break;
            }
        }

        List<UIKeyframeSheet> orderedFormSheets = new ArrayList<>(formSheets);
        formSheets.clear();

        if (form instanceof PoseForm)
        {
            List<UIKeyframeSheet> boneSheets = new ArrayList<>();
            Map<String, Integer> depthBySheetId = new HashMap<>();

            UIReplaysEditorUtils.addBoneTrackSheets(form, this.state.properties, boneSheets, depthBySheetId);

            for (UIKeyframeSheet boneSheet : boneSheets)
            {
                poseTabDepths.put(boneSheet, depthBySheetId.getOrDefault(boneSheet.id, 0));
            }

            if (poseSheet != null && !boneSheets.isEmpty())
            {
                poseTabs.put(poseSheet, boneSheets);

                int poseIndex = orderedFormSheets.indexOf(poseSheet);

                orderedFormSheets.addAll(poseIndex < 0 ? orderedFormSheets.size() : poseIndex + 1, boneSheets);
            }
            else
            {
                orderedFormSheets.addAll(boneSheets);
            }
        }

        sheets.addAll(orderedFormSheets);
    }

    public boolean clickViewport(UIContext context, StencilFormFramebuffer stencil)
    {
        if (stencil.hasPicked() && this.state != null)
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null)
            {
                return UIReplaysEditorUtils.pickFormWithOffers(context, pair, (form, bone, insert) ->
                    UIReplaysEditorUtils.pickForm(this.keyframeEditor, this.editor, form, bone, insert));
            }
        }

        return false;
    }

    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(this.keyframeEditor);
        GizmoDrag drag = this.buildGizmoDrag(transform, context.getTransition());

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);
    }

    public void pickForm(Form form, String bone)
    {
        UIReplaysEditorUtils.pickForm(this.keyframeEditor, this.editor, form, bone, false);
    }

    /**
     * Bone selection for the renderer's deferred sphere pick (a click that didn't turn into a
     * trackball drag). Mirrors the left-click branch of {@link #clickViewport}.
     */
    public void pickFormFromRenderer(Pair<Form, String> pair)
    {
        if (Window.isCtrlPressed()) UIReplaysEditorUtils.offerAdjacent(this.getContext(), pair.a, pair.b, (bone) -> this.pickForm(pair.a, bone));
        else if (Window.isShiftPressed()) UIReplaysEditorUtils.offerHierarchy(this.getContext(), pair.a, pair.b, (bone) -> this.pickForm(pair.a, bone));
        else this.pickForm(pair.a, pair.b);
    }

    private GizmoDrag buildGizmoDrag(UIPropTransform transform, float transition)
    {
        if (transform == null || transform.getTransform() == null)
        {
            return null;
        }

        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.editor.renderer.camera, this.editor.renderer.area);

        if (drag != null)
        {
            float tick = this.editor.getSamplingTick();
            Supplier<Matrix4f> rotationSampler = () ->
            {
                this.editor.applyStateForSampling(tick);

                Matrix4f origin = this.getOriginMatrix(transition);

                return origin == null ? new Matrix4f() : MatrixStackUtils.stripScale(origin);
            };

            /* The bone matrices come from the previewed form, which only reflects a keyframe edit
             * once the animation state is re-applied. computeRotateAxes / computeTranslateJacobian
             * perturb the keyframe transform, so re-pose the form before each sample (mirroring the
             * film's buildFilmGizmoDrag); otherwise the perturbation leaves no trace and the gizmo
             * axes collapse to identity, breaking the trackball and view rotation. */
            drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                transform.getTransform(),
                () ->
                {
                    this.editor.applyStateForSampling(tick);

                    Matrix4f origin = this.getOrigin(transition);

                    return origin == null ? new Vector3f() : origin.getTranslation(new Vector3f());
                }
            ));
            drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                transform.getTransform(),
                rotationSampler
            ));
            drag.setRotate2Axes(GizmoDrag.computeRotateAxes(
                transform.getTransform(),
                true,
                rotationSampler
            ));

            /* Restore the previewed form to the unperturbed pose: the compute* helpers above have
             * already reverted the transform, so re-applying now poses it with the original values. */
            this.editor.applyStateForSampling(tick);
            Vector3f rotationOffset = this.getMatrixEntry(transition).rotationOffset();

            drag.setRotationParents(transform.getTransform(), rotationOffset, rotationSampler);
            this.editor.applyStateForSampling(tick);
        }

        return drag;
    }

    public Matrix4f getOrigin(float transition)
    {
        return this.getOriginInternal(transition, false);
    }

    /**
     * Same as {@link #getOrigin(float)} but always returns the rotation-bearing
     * matrix regardless of the keyframe's GLOBAL flag. Required for the
     * sampling-based rotation-axis helper in {@link GizmoDrag}.
     */
    public Matrix4f getOriginMatrix(float transition)
    {
        return this.getOriginInternal(transition, true);
    }

    private Matrix4f getOriginInternal(float transition, boolean forceMatrix)
    {
        MatrixCacheEntry entry = this.getMatrixEntry(transition);
        Pair<String, Boolean> bone = this.keyframeEditor == null ? null : this.keyframeEditor.getBone();

        if (bone == null)
        {
            return Matrices.EMPTY_4F;
        }

        Matrix4f matrix = (!forceMatrix && bone.b) ? entry.origin() : entry.matrix();

        return matrix == null ? Matrices.EMPTY_4F : matrix;
    }

    private MatrixCacheEntry getMatrixEntry(float transition)
    {
        if (this.keyframeEditor == null)
        {
            return new MatrixCacheEntry(null, null);
        }

        Pair<String, Boolean> bone = this.keyframeEditor.getBone();

        if (bone == null)
        {
            return new MatrixCacheEntry(null, null);
        }

        Form root = FormUtils.getRoot(this.editor.form);
        MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(this.editor.renderer.getTargetEntity(), transition);
        return map.get(bone.a);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.keyframeEditor != null)
        {
            UIPropTransform transform = UIReplaysEditorUtils.getEditableTransform(this.keyframeEditor);

            if (transform != null)
            {
                transform.hotkeyDrag(() ->
                {
                    UIContext current = this.getContext();

                    return this.buildGizmoDrag(transform, current == null ? 0F : current.getTransition());
                });
            }

            this.editArea.area.render(context.batcher, Colors.A75);
        }

        super.render(context);
    }
}
