package mchorse.bbs_mod.ui.forms.editors;

import mchorse.bbs_mod.ui.utils.shapes.ShapeControllerOverlay;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIShapeKeysKeyframeFactory;
import mchorse.bbs_mod.ui.utils.SplineKeyframeEditor;
import mchorse.bbs_mod.cubic.spline.SplineIK;
import mchorse.bbs_mod.forms.forms.SplineForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UISplineForm;
import mchorse.bbs_mod.ui.utils.SplineFormTool;
import mchorse.bbs_mod.api.client.editor.FormEditorTool;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.BodyPartManager;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.states.AnimationState;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.forms.IUIFormList;
import mchorse.bbs_mod.ui.forms.UIFormList;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.editors.forms.UIAnchorForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIBillboardForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIBlockForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIExtrudedForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIFramebufferForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIItemForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UILabelForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIMobForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIModelForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIParticleForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIStructureForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UITrailForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIVanillaParticleForm;
import mchorse.bbs_mod.ui.forms.editors.forms.UIVideoForm;
import mchorse.bbs_mod.ui.forms.editors.states.UIAnimationStatesOverlayPanel;
import mchorse.bbs_mod.ui.forms.editors.states.keyframes.UIAnimationStateEditor;
import mchorse.bbs_mod.ui.forms.editors.utils.UIPickableFormRenderer;
import mchorse.bbs_mod.ui.forms.editors.utils.UISetupFaceOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UIUndoKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.utils.BoneSelection;
import mchorse.bbs_mod.ui.utils.IBoneSelectionHost;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.SplineOverlay;
import mchorse.bbs_mod.ui.utils.SplineEditorUtils;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.resources.FilteredLink;
import mchorse.bbs_mod.utils.resources.MultiLink;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIFormEditor extends UIElement implements IUIFormList, ICursor, IBoneSelectionHost
{
    private final BoneSelection boneSelection = new BoneSelection();
    public final ShapeControllerOverlay shapeControllerOverlay = new ShapeControllerOverlay();

    public void renderShapeControllers(UIContext context)
    {
        if (this.statesEditor.isVisible())
        {
            /* No state picked, or one without tracks, leaves the states editor without a timeline. */
            if (this.statesKeyframes.keyframeEditor != null && this.statesKeyframes.keyframeEditor.editor instanceof UIShapeKeysKeyframeFactory keys)
                this.shapeControllerOverlay.preview(context, this.renderer, keys.form, keys.controls, keys::beginControllerGesture, keys::endControllerGesture);
            else this.shapeControllerOverlay.clear();
        }
        else if (this.editor != null && this.editor.view instanceof mchorse.bbs_mod.ui.forms.editors.panels.UIModelFormPanel panel)
        {
            this.shapeControllerOverlay.preview(context, this.renderer, (mchorse.bbs_mod.forms.forms.ModelForm) this.editor.form,
                panel.shapeControllers, panel.shapeControllers::endGesture, panel.shapeControllers::endGesture);
        }
        else this.shapeControllerOverlay.clear();
    }

    private final SplineOverlay splineOverlay = new SplineOverlay();
    private static final Gizmo.HandleMask NO_GIZMO_HANDLES = Gizmo.HandleMask.of(
        java.util.EnumSet.noneOf(Gizmo.Op.class), java.util.EnumSet.noneOf(mchorse.bbs_mod.utils.Axis.class));

    private static Map<Class, Supplier<UIForm>> panels = new HashMap<>();

    /* Palette for picking a form for body parts */
    public UIFormPalette palette;

    /* Main form editor element */
    public UIElement formEditor;
    public UIPickableFormRenderer renderer;
    public UIForm editor;

    /* States editor */
    public UIElement statesEditor;
    public UIAnimationStateEditor statesKeyframes;
    public UIIcon openStates;
    public UIIcon plause;
    public UIIcon shiftDuration;
    public UIIcon autoKeyframe;

    /* Forms sidebar */
    public UIElement forms;
    public UIForms formsList;
    private UIIcon addFormList;
    private UIIcon bodyPartGizmoIcon;
    private UIIcon copyFormList;
    private UIIcon pasteFormList;
    private UIIcon removeFormList;
    public UIBodyPartEditor bodyPartEditor;

    /* When on, the viewport gizmo edits the selected body part's attachment transform instead of the form's own */
    private boolean bodyPartGizmo;

    /* Sidebar icons */
    public UIElement icons;
    public UIIcon finish;
    public UIIcon openStateEditor;

    public Form form;

    private Consumer<Form> callback;
    private UICopyPasteController copyPasteController;
    private UIFormUndoHandler undoHandler;

    private int lastTick;
    private int cursor;
    private float cursorFraction;
    private boolean playing;

    /** Armed viewport eyedropper (see {@link #startBonePicking}); null when idle. */
    private Consumer<Pair<Form, String>> bonePicking;

    /** Upper part of the sidebar (toolbar + forms list); shrinks by the body part editor's height. */
    private UIElement listSection;

    /**
     * Fills the registry. Called by BBS while it initialises, and followed by the event that
     * lets addons add to it.
     *
     * <p>This used to be a static initialiser, which ran whenever something first touched the
     * class — a moment nobody chose and an addon could not aim at.</p>
     */
    public static void setup()
    {
        register(BillboardForm.class, UIBillboardForm::new);
        register(VideoForm.class, UIVideoForm::new);
        register(ExtrudedForm.class, UIExtrudedForm::new);
        register(LabelForm.class, UILabelForm::new);
        register(ModelForm.class, UIModelForm::new);
        register(ParticleForm.class, UIParticleForm::new);
        register(BlockForm.class, UIBlockForm::new);
        register(ItemForm.class, UIItemForm::new);
        register(AnchorForm.class, UIAnchorForm::new);
        register(SplineForm.class, UISplineForm::new);
        register(MobForm.class, UIMobForm::new);
        register(VanillaParticleForm.class, UIVanillaParticleForm::new);
        register(TrailForm.class, UITrailForm::new);
        register(FramebufferForm.class, UIFramebufferForm::new);
        register(StructureForm.class, UIStructureForm::new);
    }

    public static void register(Class clazz, Supplier<UIForm> supplier)
    {
        panels.put(clazz, supplier);
    }

    public static UIForm createPanel(Form form)
    {
        if (form == null)
        {
            return null;
        }

        Supplier<UIForm> supplier = findPanel(form.getClass());

        return supplier == null ? null : supplier.get();
    }

    /**
     * The panel registered for a form's own class, or for the nearest class it extends, so a
     * form built on top of one of BBS's own is editable through its parent's panel until it
     * brings one of its own.
     */
    private static Supplier<UIForm> findPanel(Class clazz)
    {
        while (clazz != null && clazz != Object.class)
        {
            Supplier<UIForm> supplier = panels.get(clazz);

            if (supplier != null)
            {
                return supplier;
            }

            clazz = clazz.getSuperclass();
        }

        return null;
    }

    public UIFormEditor(UIFormPalette palette)
    {
        this.palette = palette;

        this.undoHandler = new UIFormUndoHandler(this);
        this.copyPasteController = new UICopyPasteController(PresetManager.BODY_PARTS, "_FormEditorBodyPart")
            .supplier(this::copyBodyPart)
            .consumer(this::pasteBodyPart)
            .canCopy(() ->
            {
                UIForms.FormEntry current = this.formsList.getCurrentFirst();

                return current != null && current.part != null;
            })
            .canPaste(() ->
            {
                UIForms.FormEntry current = this.formsList.getCurrentFirst();

                return current != null && current.getForm() != null;
            })
            .labels(UIKeys.FORMS_EDITOR_CONTEXT_COPY, UIKeys.FORMS_EDITOR_CONTEXT_PASTE);

        this.forms = new UIElement();

        /* The tree's share is of the whole editor, measured from its left edge. */
        UISplitter draggable = UISplitter.fraction("form_editor.tree", 0.1F, 0F, 0.5F);
        draggable.measure(this.forms, this).onChange(() -> this.forms.w(draggable.getValue()).resize());

        this.forms.relative(this).w(draggable.getValue()).minW(140).h(1F);

        this.formsList = new UIForms((l) ->
        {
            this.pickForm(l.get(0));
            this.updateFormListButtons();
        });
        this.formsList.onReorder = this::dropBodyPart;
        this.formsList.context(this::createFormContextMenu);
        this.formsList.keys().register(Keys.COPY, () ->
        {
            if (this.copyPasteController.copy())
            {
                this.updateFormListButtons();
            }
        }).inside().label(UIKeys.FORMS_EDITOR_CONTEXT_COPY).active(this.copyPasteController::canCopy);
        this.formsList.keys().register(Keys.PASTE, () -> this.copyPasteController.paste(0, 0))
            .inside().label(UIKeys.FORMS_EDITOR_CONTEXT_PASTE).active(this.copyPasteController::canPaste);
        this.formsList.keys().register(Keys.DELETE, this::removeBodyPart)
            .inside().label(UIKeys.FORMS_EDITOR_CONTEXT_REMOVE)
            .active(() ->
            {
                UIForms.FormEntry current = this.formsList.getCurrentFirst();
                return current != null && current.part != null;
            });

        this.listSection = new UIElement();
        this.listSection.relative(this.forms).w(1F).h(1F);
        UIElement listSection = this.listSection;
        UIElement listToolbarBg = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                this.area.render(context.batcher, BBSSettings.chromeSurface());
            }
        };
        listToolbarBg.relative(listSection).xy(0, 0).w(1F).h(20);
        this.addFormList = new UIIcon(Icons.ADD, (b) -> this.addBodyPart(new BodyPart("")));
        this.addFormList.tooltip(UIKeys.FORMS_EDITOR_CONTEXT_ADD);
        this.bodyPartGizmoIcon = new UIIcon(Icons.ALL_DIRECTIONS, (b) -> this.bodyPartGizmo = !this.bodyPartGizmo)
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (UIFormEditor.this.bodyPartGizmo)
                {
                    context.batcher.highlight(this.area, Direction.BOTTOM);
                }

                super.renderSkin(context);
            }
        };
        this.bodyPartGizmoIcon.tooltip(UIKeys.FORMS_EDITOR_BODY_PART_GIZMO);
        this.bodyPartGizmoIcon.keys().register(Keys.FORMS_TOGGLE_BODY_PART_GIZMO, this.bodyPartGizmoIcon::clickItself);
        this.copyFormList = new UIIcon(Icons.COPY, (b) ->
        {
            if (this.copyPasteController.copy())
            {
                this.updateFormListButtons();
            }
        });
        this.copyFormList.tooltip(UIKeys.FORMS_EDITOR_CONTEXT_COPY);
        this.pasteFormList = new UIIcon(Icons.PASTE, (b) -> this.copyPasteController.paste(0, 0));
        this.pasteFormList.tooltip(UIKeys.FORMS_EDITOR_CONTEXT_PASTE);
        this.removeFormList = new UIIcon(Icons.REMOVE, (b) -> this.removeBodyPart());
        this.removeFormList.tooltip(UIKeys.FORMS_EDITOR_CONTEXT_REMOVE);

        UIElement listToolbar = new UIElement();
        listToolbar.relative(listSection).w(1F).h(20).row(0).padding(0).height(20);
        for (UIIcon icon : new UIIcon[] {this.addFormList, this.bodyPartGizmoIcon, this.copyFormList, this.pasteFormList, this.removeFormList})
        {
            UIElement cell = new UIElement();
            icon.relative(cell).x(0.5F).y(0.5F).anchor(0.5F, 0.5F);
            cell.add(icon);
            listToolbar.add(cell);
        }
        this.formsList.relative(listSection).y(20).h(1F, -20).w(1F);
        listSection.add(listToolbarBg, listToolbar, this.formsList);

        /* Pinned to the sidebar's bottom at content height (see resizeSidebar) —
         * the forms list above takes whatever the editor doesn't need. */
        this.bodyPartEditor = new UIBodyPartEditor(this);
        this.bodyPartEditor.relative(this.forms).w(1F).y(1F).h(0).anchorY(1F);

        this.formEditor = new UIElement();
        this.formEditor.full(this);

        this.statesEditor = new UIElement();
        this.statesEditor.full(this);
        this.statesEditor.setVisible(false);
        this.statesKeyframes = new UIAnimationStateEditor(this);
        this.statesKeyframes.relative(this.statesEditor).w(1F, -20).h(1F);

        this.openStates = new UIIcon(Icons.MORE, (b) ->
        {
            UIAnimationStatesOverlayPanel panel = new UIAnimationStatesOverlayPanel(this.form.states, this.statesKeyframes.getState(), (state) -> this.pickState(state));

            panel.setUndoId("animation_states_overlay_panel");
            UIOverlay.addOverlay(this.getContext(), panel, 280, 0.5F).eventPropagataion(EventPropagation.PASS);
        });
        this.openStates.relative(this.statesEditor).x(1F, -20);
        this.openStates.tooltip(UIKeys.FORMS_EDITOR_STATES_OPEN, Direction.LEFT);
        this.plause = new UIIcon(() -> this.playing ? Icons.PAUSE : Icons.PLAY, (b) -> this.plause());
        this.plause.relative(this.openStates).y(1F);
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE, Direction.LEFT);
        this.shiftDuration = new UIIcon(Icons.SHIFT_TO, (b) ->
        {
            AnimationState state = this.statesKeyframes.getState();

            if (state != null)
            {
                state.duration.set(this.cursor);
            }
        });
        this.shiftDuration.relative(this.plause).y(1F);
        this.shiftDuration.tooltip(UIKeys.CAMERA_TIMELINE_CONTEXT_SHIFT_DURATION, Direction.LEFT);
        this.shiftDuration.keys().register(Keys.CLIP_SHIFT, () -> this.shiftDuration.clickItself());

        this.autoKeyframe = new UIIcon(Icons.KEY, (b) -> BBSSettings.autoKeyframe.set(!BBSSettings.autoKeyframe.get()));
        this.autoKeyframe.relative(this.shiftDuration).y(1F);
        this.autoKeyframe.highlight(() -> BBSSettings.autoKeyframe.get(), Direction.RIGHT);
        this.autoKeyframe.tooltip(UIKeys.FILM_AUTO_KEYFRAME, Direction.LEFT);

        this.renderer = new UIPickableFormRenderer(this);
        this.renderer.full(this);

        this.finish = new UIIcon(Icons.IN, (b) -> this.palette.exit());
        this.finish.tooltip(UIKeys.FORMS_EDITOR_FINISH, Direction.LEFT);
        this.openStateEditor = new UIIcon(Icons.GALLERY, (b) -> this.toggleStateEditor())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                if (UIFormEditor.this.statesEditor.isVisible())
                {
                    context.batcher.highlight(this.area, Direction.RIGHT);
                }

                super.renderSkin(context);
            }
        };
        this.openStateEditor.tooltip(UIKeys.FORMS_EDITOR_STATES_TOGGLE, Direction.LEFT);
        this.icons = UI.column(this.openStateEditor, this.finish);
        this.icons.relative(this).x(1F, -20).y(1F).w(20).anchorY(1F);

        UIRenderable background = new UIRenderable((context) ->
        {
            if (this.forms.isVisible())
            {
                this.forms.area.render(context.batcher, BBSSettings.deepSurface());
            }
        });

        UIRenderable backgroundStates = new UIRenderable((context) ->
        {
            context.batcher.box(this.area.ex() - 20, this.area.y, this.area.ex(), this.area.ey(), BBSSettings.chromeSurface());
        });

        draggable.relative(this.forms).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.forms.add(background, listSection, this.bodyPartEditor, draggable);
        this.formEditor.add(this.forms);
        this.statesEditor.add(backgroundStates, this.openStates, this.plause, this.shiftDuration, this.autoKeyframe, this.statesKeyframes);
        this.add(this.renderer, this.formEditor, this.statesEditor, this.icons);
        this.add(new UIUndoKeys(this::undo, this::redo).full(this));

        this.keys().register(Keys.FORMS_CENTER_CAMERA, this::centerCamera).strict();
        this.keys().register(Keys.FORMS_OPEN_STATES_EDITOR, () ->
        {
            if (!this.statesEditor.isVisible())
            {
                this.toggleStateEditor();
            }

            if (!UIOverlay.has(this.getContext()))
            {
                this.openStates.clickItself();
            }
        });
        this.plause.keys().register(Keys.PLAUSE, () ->
        {
            this.plause();
            UIUtils.playClick();
        });

        this.setUndoId("form_editor");
    }

    /**
     * Arm the viewport eyedropper: the next left click reports what the stencil has
     * under the cursor — the picked (form, bone) pair, or null on a miss — instead
     * of selecting it in the editor. One-shot; see {@link UIBonePicker.Viewport}.
     */
    public void startBonePicking(Consumer<Pair<Form, String>> callback)
    {
        this.stopBonePicking();
        this.bonePicking = callback;
    }

    public boolean isBonePicking()
    {
        return this.bonePicking != null;
    }

    /** Cancel an armed eyedropper, delivering null so its owner can reset its state. */
    public void stopBonePicking()
    {
        Consumer<Pair<Form, String>> callback = this.bonePicking;

        this.bonePicking = null;

        if (callback != null)
        {
            callback.accept(null);
        }
    }

    public boolean clickViewport(UIContext context, StencilFormFramebuffer stencil)
    {
        /* An armed eyedropper wins over everything the click could otherwise do
         * (gizmo, selection) — that is the whole point of the mode. A left click
         * disarms it: a bone delivers, a miss cancels; right click just cancels. */
        if (this.bonePicking != null)
        {
            if (context.mouseButton == 1)
            {
                this.stopBonePicking();

                return true;
            }

            if (context.mouseButton == 0)
            {
                /* Shift+click keeps the editor's regular pick reachable while armed:
                 * disarm first (the pick rebuilds the panels, which would orphan the
                 * armed state), then fall through to the normal selection below. */
                if (Window.isShiftPressed())
                {
                    this.stopBonePicking();
                }
                else
                {
                    Consumer<Pair<Form, String>> callback = this.bonePicking;
                    Pair<Form, String> pair = stencil.hasPicked() ? stencil.getPicked() : null;

                    this.bonePicking = null;
                    callback.accept(pair);

                    return true;
                }
            }
        }

        if (this.shapeControllerOverlay.click(context)) return true;

        if (!this.statesEditor.isVisible() && !context.isFocused() && context.mouseButton == 0 && Window.isCtrlPressed()
            && this.renderer.area.isInside(context) && this.editor != null
            && this.editor.view instanceof SplineFormTool panel && !panel.pointEditor().position.isEditing())
        {
            SplineOverlay.CurveHit hit = this.splineOverlay.insertionAt(context.mouseX, context.mouseY);
            if (hit != null && panel.splineSources().contains(hit.chain()))
            {
                panel.selectSpline(hit.chain());
                panel.pointEditor().insert(hit.after(), hit.position());
                return true;
            }
        }

        if (this.renderer.getGizmoInteraction().mouseClicked(context))
        {
            return true;
        }

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.pickSplinePoint(context) || this.statesKeyframes.clickViewport(context, stencil);
        }

        if (context.mouseButton == 0 && this.editor != null && this.editor.view instanceof SplineFormTool panel)
        {
            SplineOverlay.Hit hit = this.splineOverlay.pick(context.mouseX, context.mouseY);
            if (hit != null && panel.splineSources().contains(hit.chain()))
            {
                panel.selectSpline(hit.chain());
                panel.pointEditor().selectInViewport(hit.point().getId(), Window.isCtrlPressed() || Window.isShiftPressed());
                return true;
            }
        }

        var splineSource = this.splineOverlay.pickSource(context.mouseX, context.mouseY);
        if (context.mouseButton == 0 && splineSource instanceof SplineForm spline)
        {
            this.pickFormFromRenderer(new Pair<>(spline, ""));
            var hit = this.splineOverlay.pick(context.mouseX, context.mouseY);
            if (hit != null && this.editor.defaultPanel instanceof SplineFormTool tool)
            {
                this.editor.setPanel(this.editor.defaultPanel);
                tool.pointEditor().select(hit.point().getId());
            }
            return true;
        }

        if (stencil.hasPicked() && (context.mouseButton == 0 || (context.mouseButton == 2 && Window.isCtrlPressed())))
        {
            Pair<Form, String> pair = stencil.getPicked();

            if (pair != null)
            {
                this.pickFormFromRenderer(pair);

                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused() && context.isPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE)
            && this.renderer.area.isInside(context) && this.statesEditor.isVisible()
            && this.statesKeyframes.keyframeEditor.editor instanceof SplineKeyframeEditor spline)
            return spline.pointEditor().removeSelected();
        if (!context.isFocused() && context.getKeyAction() == mchorse.bbs_mod.ui.utils.keys.KeyAction.PRESSED
            && context.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
            && this.renderer.area.isInside(context) && !this.statesEditor.isVisible()
            && this.editor != null && this.editor.view instanceof SplineFormTool panel)
            return panel.pointEditor().removeSelected();
        return super.subKeyPressed(context);
    }

    /** Translation-only handles for a spline control; ordinary form tools retain their mask. */
    public Gizmo.HandleMask getGizmoHandleMask()
    {
        if (this.statesEditor.isVisible())
        {
            return SplineEditorUtils.selectedPath(this.statesKeyframes.keyframeEditor) == null ? Gizmo.HandleMask.ALL : SplineEditorUtils.HANDLES;
        }
        if (this.editor != null && this.editor.view instanceof SplineFormTool panel)
        {
            return panel.getGizmoTransform() == null ? NO_GIZMO_HANDLES : SplineEditorUtils.HANDLES;
        }
        return Gizmo.HandleMask.ALL;
    }

    public void renderSplineOverlay(UIContext context)
    {
        if (this.editor != null && this.editor.view instanceof SplineFormTool panel) panel.pointEditor().points.viewportHover = ("");
        SplineEditorUtils.viewportHover(this.statesKeyframes.keyframeEditor, null);
        if (!this.splineOverlay.begin()) return;
        if (this.statesEditor.isVisible())
        {
            this.statesKeyframes.renderSplineOverlay(context);
            return;
        }
        if (this.editor == null) return;
        SplineFormTool panel = this.editor.view instanceof SplineFormTool tool ? tool : null;
        var sources = SplineEditorUtils.sourcesInTree(FormUtils.getRoot(this.editor.form));
        for (var chain : sources)
        {
            if (chain instanceof SplineIK && (panel == null || !panel.splineSources().contains(chain))) continue;
            Form owner = SplineEditorUtils.owner(chain);
            Matrix4f parent = SplineEditorUtils.parentMatrix(FormUtils.getRoot(owner), this.renderer.getTargetEntity(), context.getTransition(), owner, chain);
            if (parent == null) continue;
            Matrix4f modelView = new Matrix4f(this.renderer.camera.view)
                .translate((float) -this.renderer.camera.position.x, (float) -this.renderer.camera.position.y, (float) -this.renderer.camera.position.z)
                .mul(this.renderer.toSceneMatrix(parent));
            this.splineOverlay.draw(context, modelView, this.renderer.camera.projection, this.renderer.area, chain,
                panel != null && chain == panel.activeSpline() ? panel.pointEditor().selected() : java.util.Set.of(),
                panel != null && chain == panel.activeSpline() ? panel.pointEditor().points.hoveredPoint(context) : "");
        }
        this.splineOverlay.finish(context, this.renderer.area);
        if (panel != null && Window.isCtrlPressed() && !context.isFocused() && !panel.pointEditor().position.isEditing())
            this.splineOverlay.drawInsertionPreview(context, this.renderer.area);
        SplineOverlay.Hit hovered = this.renderer.area.isInside(context) ? this.splineOverlay.pick(context.mouseX, context.mouseY) : null;
        if (panel != null) panel.pointEditor().points.viewportHover = (hovered != null && hovered.chain() == panel.activeSpline() ? hovered.point().getId() : "");
    }

    private FormEditorTool getPanelTool()
    {
        return !this.statesEditor.isVisible() && this.editor != null
            && this.editor.view instanceof FormEditorTool tool
            && tool.getGizmoTransform() != null ? tool : null;
    }

    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        if (!this.statesEditor.isVisible() && this.editor != null
            && this.editor.view instanceof SplineFormTool panel && panel.getGizmoTransform() == null)
        {
            return false;
        }
        var tool = this.getPanelTool();
        if (tool != null)
        {
            UIPropTransform transform = tool.getGizmoTransform();
            return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, this.buildHotkeyDrag(transform));
        }

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.startGizmo(context, stencilIndex);
        }

        UIPropTransform transform = this.isBodyPartGizmoMode() ? this.bodyPartEditor.transform : this.editor.getEditableTransform();
        GizmoDrag drag = this.buildGizmoDrag(transform, context.getTransition());

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, transform, drag);
    }

    /**
     * Whether the viewport gizmo edits the selected body part's attachment transform. Requires the
     * mode toggle to be on AND a body part (not the root form) to be selected.
     */
    public boolean isBodyPartGizmoMode()
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        return this.bodyPartGizmo && current != null && current.part != null && current.getForm() != null;
    }

    private GizmoDrag buildGizmoDrag(UIPropTransform transform, float transition)
    {
        if (transform == null || transform.getTransform() == null)
        {
            return null;
        }

        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.renderer.camera, this.renderer.area);

        if (drag != null)
        {
            /* Same frame GLOBAL is DRAWN in (UIPickableFormRenderer#renderAxes):
             * the preview's scene axes, which the model block's immersive editing
             * turns with the block. Both sides must read it from the renderer. */
            drag.setGlobalAxes(this.renderer.getSceneAxes());
            /* Sampled matrices live in the form's own frame, while the gizmo's
             * origin and axes are read back out of the render matrix — which
             * carries the renderer's transform. Lift the samples into the same
             * frame (a no-op in a plain preview, the block's rotation inside an
             * immersively edited model block) or the drag runs off the handles. */
            drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                transform.getTransform(),
                () ->
                {
                    Matrix4f origin = this.getOrigin(transition);

                    return origin == null ? new Vector3f() : this.renderer.toSceneMatrix(origin).getTranslation(new Vector3f());
                }
            ));
            drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                transform.getTransform(),
                () ->
                {
                    /* Always sample the rotation-bearing matrix &mdash; the
                     * UI's GLOBAL toggle would otherwise hand us an origin
                     * matrix without rotation, in which the perturbation we
                     * apply leaves no trace and axis extraction silently
                     * collapses to identity. */
                    Matrix4f origin = this.getOriginMatrix(transition);

                    return origin == null ? new Matrix4f() : MatrixStackUtils.stripScale(this.renderer.toSceneMatrix(origin));
                }
            ));
            drag.setAdditiveRotationBase(this.poseRotationBase(transform, transition));
            /* Both bone frames, lifted into the gizmo's frame like the samples above:
             * LOCAL and PARENT are different frames, and the snapshot must be able to
             * answer for either — the walk on the axis keys moves a live gesture
             * between them (UIPropTransform#setEditingAxis). */
            drag.setFrameAxes(
                this.renderer.toSceneMatrix(this.getOriginMatrix(transition)),
                this.renderer.toSceneMatrix(this.getParentOriginMatrix(transition))
            );
        }

        return drag;
    }

    /**
     * The additive euler base under the pose editor's channels for the picked
     * bone ({@link UIModelForm#poseRotationBase}) — the model form's pose stack
     * (and the animator's actions) sits under the pose track the panel edits.
     * {@code null} (zero base) for every other transform editor here (body
     * part, general form transform, states), whose values aren't pose-stacked.
     */
    private Vector3f poseRotationBase(UIPropTransform transform, float transition)
    {
        if (this.isBodyPartGizmoMode() || this.statesEditor.isVisible() || this.editor == null)
        {
            return null;
        }

        return this.editor.poseRotationBase(transform, transition);
    }

    public GizmoDrag buildHotkeyDrag(UIPropTransform transform)
    {
        UIContext context = this.getContext();

        return this.buildGizmoDrag(transform, context == null ? 0F : context.getTransition());
    }

    public void pickFormFromRenderer(Pair<Form, String> pair)
    {
        if (this.statesEditor.isVisible())
        {
            this.statesKeyframes.pickFormFromRenderer(pair);

            return;
        }

        if (Window.isCtrlPressed() && !pair.b.isEmpty())
        {
            /* Ctrl + a valid parent bone attaches the active body part to it; otherwise it
             * toggles the bone in the pose editor's multi-selection (no rebuild, so the
             * selection accumulates), matching the film editor. */
            if (this.bodyPartEditor.pickBone(pair) || (this.editor != null && this.editor.toggleBoneSelection(pair.b)))
            {
                return;
            }
        }

        if (Window.isShiftPressed()) UIReplaysEditorUtils.offerHierarchy(this.getContext(), pair.a, pair.b, (bone) -> this.pickFormBone(pair.a, bone));
        else this.pickFormBone(pair.a, pair.b);
    }

    @Override
    public BoneSelection getBoneSelection()
    {
        return this.boneSelection;
    }

    private void pickFormBone(Form form, String bone)
    {
        /* Captured before pickForm rebuilds the editor, so the bone can be routed back to the tab
         * that was open when the body part was clicked. */
        Class<?> activePanel = this.editor == null ? null : this.editor.getActivePanelClass();

        this.formsList.setCurrentForm(form);
        this.pickForm(this.formsList.getCurrentFirst());

        if (!bone.isEmpty())
        {
            this.editor.pickBoneFromViewport(bone, activePanel);
        }
    }

    public void refillState()
    {
        if (this.statesKeyframes.getState() != null)
        {
            this.pickState(this.statesKeyframes.getState());
        }
    }

    private void pickState(AnimationState state)
    {
        this.statesKeyframes.setState(state);
    }

    private void plause()
    {
        if (this.playing)
        {
            this.cursorFraction = BBSSettings.editorSnapToTicks.get() ? 0F : this.getSamplingTick() - this.cursor;
        }

        this.playing = !this.playing;
    }

    public void stopPlaybackOnScrub()
    {
        if (BBSSettings.editorStopPlaybackOnScrub.get())
        {
            this.stopPlayback();
        }
    }

    public void stopPlayback()
    {
        if (this.playing)
        {
            this.plause();
        }
    }

    private void toggleStateEditor()
    {
        this.formEditor.toggleVisible();
        this.statesEditor.toggleVisible();
    }

    /**
     * Put the view back onto the middle of what is being edited, wherever it has been dragged
     * off to - the same thing C does to the orbit of a film or of a model block.
     */
    private void centerCamera()
    {
        float height = this.form == null ? 1F : this.form.hitboxHeight.get();

        this.renderer.focus(0F, height / 2F, 0F);
        UIUtils.playClick();
    }

    private void createFormContextMenu(ContextMenuManager menu)
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        if (current == null)
        {
            return;
        }

        this.copyPasteController.install(menu, this.getContext());

        menu.icon(MenuVerb.ADD, () -> this.addBodyPart(new BodyPart(""))).label(UIKeys.FORMS_EDITOR_CONTEXT_ADD).enabled(current.getForm() != null);
        menu.icon(MenuVerb.REMOVE, this::removeBodyPart).label(UIKeys.FORMS_EDITOR_CONTEXT_REMOVE).enabled(current.part != null);

        if (current.part != null)
        {
            if (current.getForm() instanceof FramebufferForm framebuffer && current.form instanceof ModelForm parent)
            {
                menu.action(Icons.CAMERA, UIKeys.FORMS_EDITOR_CONTEXT_SETUP_FACE, () ->
                {
                    UIOverlay.addOverlay(this.getContext(), new UISetupFaceOverlayPanel((model, offset) -> this.setupFace(framebuffer, parent, model, offset)), 240, 170);
                });
            }

            List<BodyPart> all = current.part.getManager().getAllTyped();

            if (all.size() > 1)
            {
                int index = all.indexOf(current.part);

                if (index > 0) menu.action(Icons.ARROW_UP, UIKeys.FORMS_EDITOR_CONTEXT_MOVE_UP, () -> this.moveBodyPart(current.part, index - 1));
                if (index < all.size() - 1) menu.action(Icons.ARROW_DOWN, UIKeys.FORMS_EDITOR_CONTEXT_MOVE_DOWN, () -> this.moveBodyPart(current.part, index + 1));
            }
        }
    }

    /**
     * Fill a framebuffer form with the pieces a face is made of: the parent model's own texture
     * as a flat billboard, its face square erased from the parent so the framebuffer shows
     * through, and a rig for the eyes on top of it.
     */
    private void setupFace(FramebufferForm framebuffer, ModelForm parent, String model, double verticalOffset)
    {
        BaseValue.edit(parent, (v) ->
        {
            BillboardForm face = new BillboardForm();
            ModelForm eyes = new ModelForm();
            BodyPart facePart = new BodyPart("");
            BodyPart eyesPart = new BodyPart("");

            face.texture.set(parent.texture.get());

            if (face.texture.get() == null)
            {
                ModelInstance parentModel = ModelFormRenderer.getModel(parent);

                if (parentModel != null)
                {
                    face.texture.set(parentModel.getTexture());
                }
            }

            Link texture = face.texture.get();
            Vector4f crop = new Vector4f(8F, 8F, 48F, 48F);

            if (texture != null)
            {
                Texture skin = BBSModClient.getTextures().getTexture(texture);
                int textureScale = 1;

                if (skin.width >= 64 && skin.width % 64 == 0)
                {
                    textureScale = skin.width / 64;
                    crop.set(8F * textureScale, 8F * textureScale, skin.width - 16F * textureScale, skin.height - 16F * textureScale);
                }

                MultiLink multi = texture instanceof MultiLink existing ? (MultiLink) existing.copy() : new MultiLink();

                if (!(texture instanceof MultiLink))
                {
                    multi.children.add(new FilteredLink(texture));
                }

                FilteredLink erase = new FilteredLink(Link.assets("textures/pixel.png"));

                erase.erase = true;
                erase.shiftX = 8 * textureScale;
                erase.shiftY = 8 * textureScale;
                erase.scale = 8F * textureScale;
                multi.children.add(erase);
                multi.recalculateId();
                parent.texture.set(multi);
            }

            face.pickable.set(false);
            face.resizeCrop.set(true);
            face.crop.set(crop);
            facePart.setForm(face);
            facePart.transform.get().translate.set(0F, -0.5F, 0F);

            eyes.model.set(model);
            eyesPart.setForm(eyes);
            eyesPart.transform.get().translate.set(0F, (float) (-1D + verticalOffset / 8D), -0.495F);
            eyesPart.transform.get().scale.set(2F);

            framebuffer.transform.get().translate.set(0F, 0.5F, 0.25F);
            framebuffer.parts.addBodyPart(facePart);
            framebuffer.parts.addBodyPart(eyesPart);
        });

        this.refreshFormList();
        this.switchEditor(framebuffer);
        this.refillState();
    }

    /**
     * Put a body part at a place among its siblings, wherever that place came from - the menu's
     * step up or down, or a row carried there and let go.
     *
     * @param index where the part is to end up, counted with the part itself still in the list
     */
    private void moveBodyPart(BodyPart part, int index)
    {
        BodyPartManager manager = part.getManager();
        List<BodyPart> all = manager.getAllTyped();
        int from = all.indexOf(part);
        int newIndex = MathUtils.clamp(index, 0, all.size() - 1);

        if (from != -1 && newIndex != from)
        {
            manager.moveBodyPart(part, newIndex);
            this.formsList.setForm(this.form);

            UIForms.FormEntry selection = null;

            for (UIForms.FormEntry entry : this.formsList.getList())
            {
                if (entry.part == part)
                {
                    selection = entry;

                    break;
                }
            }

            if (selection != null)
            {
                this.formsList.setCurrentScroll(selection);
                this.pickForm(selection);
            }
        }

        this.refillState();
    }

    /**
     * A carried part was let go at a slot between its siblings. The slot counts the gaps, the
     * move counts the parts: dropping past where the part already sits skips over the gap it
     * left behind, so the slot is one ahead of the place it lands in.
     */
    private void dropBodyPart(BodyPart part, int slot)
    {
        List<BodyPart> all = part.getManager().getAllTyped();
        int from = all.indexOf(part);

        this.moveBodyPart(part, from != -1 && slot > from ? slot - 1 : slot);
    }

    private void addBodyPart(BodyPart part)
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        current.getForm().parts.addBodyPart(part);
        this.refreshFormList();
    }

    private MapType copyBodyPart()
    {
        return this.formsList.getCurrentFirst().part.toData().asMap();
    }

    private void pasteBodyPart(MapType data, int mouseX, int mouseY)
    {
        BodyPart part = new BodyPart("");

        part.fromData(data);
        this.addBodyPart(part);
        this.refillState();
    }

    private void removeBodyPart()
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        if (current == null || current.part == null)
        {
            return;
        }

        int index = this.formsList.getIndex();
        current.form.parts.removeBodyPart(current.part);
        this.refreshFormList();
        this.formsList.setIndex(Math.max(0, index - 1));
        UIForms.FormEntry next = this.formsList.getCurrentFirst();
        if (next != null)
        {
            this.pickForm(next);
        }
        this.updateFormListButtons();
        this.refillState();
    }

    private void updateFormListButtons()
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        this.addFormList.setEnabled(current != null && current.getForm() != null);
        this.bodyPartGizmoIcon.setEnabled(current != null && current.part != null && current.getForm() != null);
        this.copyFormList.setEnabled(this.copyPasteController.canCopy());
        this.pasteFormList.setEnabled(this.copyPasteController.canPaste());
        this.removeFormList.setEnabled(current != null && current.part != null);
    }

    private void pickForm(UIForms.FormEntry entry)
    {
        if (entry == null)
        {
            this.bodyPartEditor.setVisible(false);
            this.resizeSidebar();
            return;
        }

        this.bodyPartEditor.setVisible(entry.part != null);

        if (entry.part != null)
        {
            this.bodyPartEditor.setPart(entry.part, entry.form);
        }

        this.resizeSidebar();
        this.switchEditor(entry.getForm());
    }

    /**
     * Fit the sidebar around the body part editor's actual content: the editor hugs
     * the bottom at exactly its content height (capped at half the sidebar, the rest
     * scrolls), the forms list gets everything above. A hidden editor releases the
     * whole sidebar to the list — no half reserved for nothing.
     */
    private void resizeSidebar()
    {
        int h = 0;

        if (this.bodyPartEditor.isVisible())
        {
            h = Math.min((int) this.bodyPartEditor.scroll.scrollSize, this.forms.area.h / 2);
        }

        this.bodyPartEditor.h(h);
        this.listSection.h(1F, -h);
        this.forms.resize();
    }

    public void openFormList(Form current, Consumer<Form> callback)
    {
        UIFormEditorList list = new UIFormEditorList(this);

        list.setSelected(current);
        this.callback = callback;

        list.full(this);
        list.resize();
        this.add(list);
    }

    public boolean isEditing()
    {
        return this.form != null;
    }

    public boolean edit(Form form)
    {
        this.form = null;

        if (form == null)
        {
            return false;
        }

        form = FormUtils.copy(form);

        this.bodyPartEditor.setVisible(false);
        this.resizeSidebar();

        if (this.switchEditor(form))
        {
            this.undoHandler.reset();

            if (this.statesEditor.isVisible())
            {
                this.toggleStateEditor();
            }

            this.form = form;
            this.form.setId("form");
            this.form.preCallback(this.undoHandler::handlePreValues);

            AnimationState main = form.states.getMain();

            if (main == null)
            {
                main = CollectionUtils.getSafe(form.states.getAllTyped(), 0);
            }

            this.pickState(main);

            this.palette.accept(form);
            this.renderer.reset();
            this.renderer.form = form;
            this.refreshFormList();
            this.formsList.setIndex(0);
            this.updateFormListButtons();

            this.form.clearStatePlayers();

            return true;
        }

        return false;
    }

    public void undo()
    {
        if (this.form != null && this.undoHandler.getUndoManager().undo(this.form)) UIUtils.playClick();
    }

    public void redo()
    {
        if (this.form != null && this.undoHandler.getUndoManager().redo(this.form)) UIUtils.playClick();
    }

    public void refreshFormList()
    {
        UIForms.FormEntry current = this.formsList.getCurrentFirst();

        this.formsList.setForm(this.form);
        this.formsList.setCurrentScroll(current);
        this.updateFormListButtons();
    }

    public boolean switchEditor(Form form)
    {
        UIForm editor = createPanel(form);

        if (editor == null)
        {
            return false;
        }

        editor.setUndoId("form_panel");

        Class<?> activePanel = this.editor == null ? null : this.editor.getActivePanelClass();

        if (this.editor != null)
        {
            this.editor.removeFromParent();
        }

        this.editor = editor;

        this.formEditor.prepend(this.editor);

        this.editor.setEditor(this);
        this.editor.startEdit(form, activePanel);
        this.editor.full(this.formEditor).resize();
        this.refillState();

        return true;
    }

    public Form finish()
    {
        Form form = this.form;

        this.form.setId("");
        this.form.resetCallbacks();
        this.form.states.cleanUp();
        this.exit();

        this.editor.finishEdit();
        this.editor.removeFromParent();
        this.editor = null;
        this.form = null;

        return form;
    }

    @Override
    public void exit()
    {
        this.callback = null;

        List<UIFormList> children = this.getChildren(UIFormList.class);

        if (!children.isEmpty())
        {
            children.get(0).removeFromParent();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        /* Window/layout changes re-derive the sidebar split from the content height
         * the pass above just computed. */
        this.resizeSidebar();
        this.updateFormListButtons();
    }

    @Override
    public void toggleEditor()
    {}

    @Override
    public void accept(Form form)
    {
        if (this.callback != null)
        {
            this.callback.accept(form);
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("body_part", this.formsList.getIndex());
    }

    @Override
    public void applyAllUndoData(MapType data)
    {
        if (this.editor != null && this.form != null)
        {
            this.switchEditor(this.form);
        }

        super.applyAllUndoData(data);
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.refreshFormList();

        UIForms.FormEntry bodyPart = CollectionUtils.getSafe(this.formsList.getList(), data.getInt("body_part"));

        if (bodyPart != null)
        {
            this.formsList.setCurrentScroll(bodyPart);
            this.pickForm(bodyPart);
        }

        this.refillState();
    }

    public void preFormRender(UIContext context, Form form)
    {
        int tick = (int) context.getTick();

        if (this.statesEditor.isVisible())
        {
            AnimationState state = this.statesKeyframes.getState();

            if (state != null)
            {
                if (this.playing)
                {
                    if (tick != this.lastTick)
                    {
                        this.cursor += 1;
                        this.cursorFraction = 0F;
                    }

                    if (this.cursor >= state.duration.get())
                    {
                        this.playing = false;
                        this.setCursor(0);
                    }
                }

                state.properties.applyProperties(form, this.getCursor(context.getTransition()));
            }
        }

        this.lastTick = tick;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.undoHandler != null)
        {
            this.undoHandler.submitUndo();
        }

        super.render(context);
    }

    public Matrix4f getOrigin(float transition)
    {
        var tool = this.getPanelTool();
        if (tool != null)
        {
            Matrix4f matrix = tool.getGizmoOrigin(transition, tool.getGizmoTransform().getSpace());
            if (matrix != null) return matrix;
        }

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.getOrigin(transition);
        }

        if (this.isBodyPartGizmoMode())
        {
            return this.editor.getBodyPartGizmoOrigin(transition, this.bodyPartEditor.transform.getSpace());
        }

        return this.editor.getOrigin(transition);
    }

    /** The space the displayed gizmo should be drawn in, matching the active
     *  editing panel (mirrors {@link #getOrigin(float)}'s dispatch). */
    public TransformSpace getGizmoSpace()
    {
        var tool = this.getPanelTool();
        if (tool != null) return tool.getGizmoTransform().getSpace();

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.getGizmoSpace();
        }

        if (this.isBodyPartGizmoMode())
        {
            return this.bodyPartEditor.transform.getSpace();
        }

        return this.editor.getGizmoSpace();
    }

    /**
     * Same as {@link #getOrigin(float)} but always returns the rotation-bearing
     * matrix regardless of LOCAL/GLOBAL UI state. Used by sampling helpers in
     * {@link GizmoDrag} that derive rotation axes from finite differences of
     * the bone's linear transform.
     */
    public Matrix4f getOriginMatrix(float transition)
    {
        var tool = this.getPanelTool();
        if (tool != null)
        {
            Matrix4f matrix = tool.getGizmoOrigin(transition, TransformSpace.LOCAL);
            if (matrix != null) return matrix;
        }

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.getOriginMatrix(transition);
        }

        if (this.isBodyPartGizmoMode())
        {
            return this.editor.getBodyPartGizmoOrigin(transition, TransformSpace.LOCAL);
        }

        return this.editor.getOriginMatrix(transition);
    }

    /** The parent-frame twin of {@link #getOriginMatrix}, dispatched the same way;
     *  the pair is what the drag snapshot carries as its two bone frames. */
    public Matrix4f getParentOriginMatrix(float transition)
    {
        var tool = this.getPanelTool();
        if (tool != null)
        {
            Matrix4f matrix = tool.getGizmoOrigin(transition, TransformSpace.PARENT);
            if (matrix != null) return matrix;
        }

        if (this.statesEditor.isVisible())
        {
            return this.statesKeyframes.getParentOriginMatrix(transition);
        }

        if (this.isBodyPartGizmoMode())
        {
            return this.editor.getBodyPartGizmoOrigin(transition, TransformSpace.PARENT);
        }

        return this.editor.getParentOriginMatrix(transition);
    }

    /**
     * Tick at which the previewed form is currently posed, matching {@link #preFormRender}.
     */
    public float getSamplingTick()
    {
        UIContext context = this.getContext();

        return this.getCursor(context == null ? 0F : context.getTransition());
    }

    /**
     * Re-applies the active animation state to the previewed form at {@code tick}. Gizmo sampling
     * (see {@link GizmoDrag#computeRotateAxes}) perturbs a keyframe
     * transform, which only reaches the bone matrices once the state is re-applied &mdash; the same
     * pose {@link #preFormRender} performs each frame for rendering.
     */
    public void applyStateForSampling(float tick)
    {
        if (!this.statesEditor.isVisible())
        {
            return;
        }

        AnimationState state = this.statesKeyframes.getState();

        if (state != null && this.renderer.form != null)
        {
            state.properties.applyProperties(this.renderer.form, tick);
        }
    }

    @Override
    public boolean isRunning()
    {
        return this.playing;
    }

    @Override
    public int getCursor()
    {
        return this.cursor;
    }

    @Override
    public void setCursor(int tick)
    {
        this.setCursor((float) tick);
    }

    @Override
    public float getCursor(float transition)
    {
        return this.cursor + (this.playing ? Math.max(this.cursorFraction, transition) : this.cursorFraction);
    }

    @Override
    public void setCursor(float tick)
    {
        tick = Math.max(0F, tick);
        this.cursor = (int) tick;
        this.cursorFraction = tick - this.cursor;
    }
}
