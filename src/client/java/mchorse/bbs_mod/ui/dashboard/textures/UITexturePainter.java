package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelActionBar;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.dashboard.textures.frames.UIFramesPanel;
import mchorse.bbs_mod.ui.dashboard.textures.layers.UILayersPanel;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIModelPicker;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.joml.Vector2i;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Texture editing chrome for a single {@link UITextureEditor}.
 *
 * <p>Layout:</p>
 * <pre>
 *   ┌─────────┬────────────────┬─────────┬─────┐
 *   │ preview ║ canvas         ║ options │tools│
 *   │         ║                ║         │ 20  │
 *   └─────────┴────────────────┴─────────┴─────┘
 * </pre>
 * The 20px strip on the right is a tool palette (brush/eraser/fill/eyedropper), highlighted via
 * {@link mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels#renderHighlight} with {@link mchorse.bbs_mod.utils.Direction#RIGHT}.
 * It shares the chrome surface with the options column beside it, so the two read as one column
 * of controls down the right edge.
 * The painter's actions — save, resize, extract frames, model preview — do not live here: the
 * owner hands them to its {@link mchorse.bbs_mod.ui.dashboard.panels.bar.UIPanelTopBar} through
 * {@link #installActions}, so they sit beside the tabs like every other panel's actions.
 * The right options column is a {@link UIScrollView} with a {@link UISplitter} whose
 * width is remembered in the settings.
 *
 * <p>The canvas is a {@link UITexturePane}, or two of them side by side while the screen is
 * split. The tools and the colours are the same for both; the options, the layers, the model
 * preview and the bar's actions belong to the texture worked on, and a change made to the other
 * one moves the work over to it.</p>
 *
 * <p>This painter does not own tabs: its owner (the texture picker) creates editors via
 * {@link #openEditor(Link)} and shows them with {@link #setEditors(UITextureEditor, UITextureEditor, UITextureEditor)}.</p>
 */
public class UITexturePainter extends UIElement
{
    /** Editor currently hosted in the canvas, or null when nothing is shown. */
    private UITextureEditor editor;

    private static final int TOOL_BAR_W = 20;
    /** The form editor's panel share, so the two side panels open alike. */
    private static final float DEFAULT_OPTIONS_WIDTH = 0.1F;
    private static final float DEFAULT_PREVIEW_WIDTH = 0.3F;
    private static final int MIN_OPTIONS_WIDTH = 140;
    private static final int MAX_BRUSH_SIZE = 1024;
    /** The left pane's share of a split screen, and how small either pane may get. */
    private static final float DEFAULT_SPLIT = 0.5F;
    private static final float MIN_SPLIT = 0.2F;

    /** Fold state of the option sections, kept across painters for the session. */
    private static final Map<String, Boolean> SECTION_FOLDS = new HashMap<>();

    public UISliderTrackpad brightness;
    public UITrackpad brushSize;
    public UISliderTrackpad brushSoftness;

    public UIColor primary;
    public UIColor secondary;
    private UIElement colorPickersRow;

    public UIIcon saveIcon;
    public UIIcon menuIcon;
    public UIIcon animationIcon;
    public UIIcon materialIcon;
    public UIIcon gridIcon;
    private UITrackpad gridStep;
    private UIElement gridStepRow;
    private UIElement materialPresets;
    private UIElement materialOverlayRow;
    private UISliderTrackpad materialOverlay;

    private TexturePaintTool activeTool = TexturePaintTool.BRUSH;
    private TextureStrokeShape activeStrokeShape = TextureStrokeShape.SQUARE;
    private boolean brushBuildUp;

    /**
     * Non-null while Alt is held to temporarily use the eyedropper; stores the tool to restore on Alt release.
     * Null means Alt-eyedropper is not active.
     */
    private TexturePaintTool toolBeforeAltPipette;

    /**
     * Non-null while the right mouse button drives the temporary eraser; stores the tool to restore
     * when the stroke ends. Null means the right-mouse-button eraser is not active.
     */
    private TexturePaintTool toolBeforeSecondaryEraser;

    private UIScrollView toolBar;
    private UIPanelActionBar actionBar;
    private UIElement editorHost;
    private UIScrollView options;
    private UISplitter optionsDraggable;

    private UIElement optionsHost;
    private UILayersPanel layersPanel;

    private UIIcon toolIconBrush;
    private UIIcon toolIconEraser;
    private UIIcon toolIconMove;
    private UIIcon toolIconFill;
    private UIIcon toolIconPipette;
    private UIIcon toolIconSelection;
    private UIIcon modelPreviewIcon;

    private UIElement modelPreviewHost;
    private UISplitter modelPreviewDraggable;
    private UIModelPreviewPanel modelPreviewPanel;

    /* The canvas column: one pane, or two side by side while the screen is split, with the work in panes[working] */
    private final UITexturePane[] panes = new UITexturePane[2];
    private UISplitter splitDraggable;
    private int working;

    private UIElement brushSizeRow;
    private UIElement brushSoftnessRow;
    private UIToggle roundBrushToggle;
    private UIToggle brushBuildUpToggle;
    private UIToggle alphaLockToggle;
    private UIElement eraserOpacityRow;
    private UISliderTrackpad eraserOpacity;

    /* The settings of the tool in hand, titled after it; gone for the tools with nothing to set */
    private UISection toolSection;

    /* The animation's own settings, there only for an animated texture */
    private UISection animationSection;
    private UITrackpad frametime;
    private UITrackpad frameWidth;
    private UITrackpad frameHeight;

    private UIElement content;
    private final Consumer<Link> saveCallback;

    /** Owner hook fired when a Save As changes an editor's link, so tabs can be relabelled/deduplicated. */
    private BiConsumer<UITextureEditor, Link> renameHandler;

    /** Owner hook fired when the texture beside the one worked on gets changed, so the work moves over to it. */
    private Consumer<UITextureEditor> switchHandler;

    public UITexturePainter(Consumer<Link> saveCallback)
    {
        this.saveCallback = saveCallback;

        this.content = new UIElement();
        this.content.relative(this).w(1F).h(1F);

        this.buildActions();
        this.buildToolBar();
        this.buildOptions();
        this.buildModelPreviewHost();
        this.buildEditorHost();

        /* The options and preview must resize before editorHost: the canvas starts after the
         * preview and ends at the options column. */
        this.content.add(new UIRenderable(this::renderPanelBackground),
            this.toolBar, this.optionsHost, this.modelPreviewHost, this.editorHost, this.optionsDraggable, this.modelPreviewDraggable);
        this.add(this.content);

        this.refreshToolUi();
        this.registerShortcuts();
    }

    /** Owner hook fired when a Save As changes an editor's link. */
    public UITexturePainter onRename(BiConsumer<UITextureEditor, Link> renameHandler)
    {
        this.renameHandler = renameHandler;

        return this;
    }

    /**
     * Owner hook fired with the tab's editor of the other pane once a change is made there: the
     * owner shows it as the one worked on, the way a click on its tab would.
     */
    public UITexturePainter onSwitch(Consumer<UITextureEditor> switchHandler)
    {
        this.switchHandler = switchHandler;

        return this;
    }

    /**
     * The painter's actions. They are built here but live in the owner's top bar — see
     * {@link #installActions}.
     */
    private void buildActions()
    {
        this.saveIcon = new UIIcon(() ->
        {
            UITextureEditor ed = this.getCurrentEditor();

            return ed != null && ed.isDirty() ? Icons.SAVE : Icons.SAVED;
        }, (b) ->
        {
            /* The button asks where to save; Shift skips the asking and writes in place, like Ctrl+S */
            if (Window.isShiftPressed())
            {
                this.withEditor(UITextureEditor::saveCurrentTexture);
            }
            else
            {
                this.withEditor(UITextureEditor::openSaveOverlay);
            }
        });
        this.saveIcon.tooltip(UIKeys.TEXTURES_SAVE_TOOLTIP);

        this.modelPreviewIcon = new UIIcon(Icons.POSE, (b) -> this.toggleModelPreview());
        this.modelPreviewIcon.tooltip(UIKeys.TEXTURES_PREVIEW_MODEL);
        this.animationIcon = new UIIcon(Icons.FILM, (b) -> this.withEditor(this::toggleAnimation));
        this.animationIcon.tooltip(UIKeys.TEXTURES_FRAMES_TOGGLE);
        this.materialIcon = new UIIcon(Icons.MATERIAL, (b) -> this.toggleMaterial());
        this.materialIcon.tooltip(UIKeys.TEXTURES_MATERIAL);
        this.gridIcon = new UIIcon(Icons.GALLERY, (b) -> this.toggleGrid());
        this.gridIcon.tooltip(UIKeys.TEXTURES_GRID);
        this.menuIcon = new UIIcon(Icons.GEAR, (b) -> this.openMenu());
        this.menuIcon.tooltip(UIKeys.TEXTURES_OPTIONS);
    }

    /** The grid is a way of looking, the same in every texture and every painter, so it lives in the settings. */
    private void toggleGrid()
    {
        BBSSettings.textureGrid.set(!BBSSettings.textureGrid.get());
    }

    /**
     * Everything the bar has no button for, as a list under the gear the way the film editor does
     * it: what is done to the whole texture, then the one-shot operations on the selection (or the
     * whole frame without one) of the active layer, clearing last.
     */
    private void openMenu()
    {
        if (this.editor == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.FULLSCREEN, UIKeys.TEXTURES_RESIZE, () -> this.withEditor(UITextureEditor::openResizeOverlay));
            menu.action(Icons.UPLOAD, UIKeys.TEXTURES_EXTRACT_FRAMES_TITLE, () -> this.withEditor(UITextureEditor::openExtractOverlay));
            menu.action(Icons.FLIP_HORIZONTAL, UIKeys.TEXTURES_MACROS_FLIP_H, () -> this.withEditor((editor) -> editor.applyMacroToWindow(PixelMacro.FLIP_HORIZONTAL)));
            menu.action(Icons.FLIP_VERTICAL, UIKeys.TEXTURES_MACROS_FLIP_V, () -> this.withEditor((editor) -> editor.applyMacroToWindow(PixelMacro.FLIP_VERTICAL)));
            menu.action(Icons.ERASER, UIKeys.TEXTURES_MACROS_CLEAR, () -> this.withEditor((editor) -> editor.applyMacroToWindow(PixelMacro.CLEAR)));
        });
    }

    /**
     * Hand the painter's actions to the panel's top bar. The owner shows and hides them with
     * {@link #setActionsVisible(boolean)} as it switches between the browser and an open texture.
     */
    public void installActions(UIPanelActionBar bar)
    {
        this.actionBar = bar;

        bar.action(this.materialIcon, () -> this.editor != null && this.editor.isMaterial())
            .action(this.animationIcon, this::isAnimated)
            .action(this.modelPreviewIcon, this.modelPreviewHost::isVisible)
            .action(this.gridIcon, BBSSettings.textureGrid::get)
            .common(this.saveIcon)
            .menu(this.menuIcon);
    }

    public void setActionsVisible(boolean visible)
    {
        this.saveIcon.setVisible(visible);
        this.menuIcon.setVisible(visible);
        this.animationIcon.setVisible(visible);
        this.modelPreviewIcon.setVisible(visible);
        this.materialIcon.setVisible(visible);
        this.gridIcon.setVisible(visible);

        if (this.actionBar != null)
        {
            this.actionBar.sync();
        }
    }

    private void buildToolBar()
    {
        this.toolBar = new UIScrollView();
        this.toolBar.scroll.cancelScrolling().noScrollbar();
        this.toolBar.scroll.scrollSpeed = 5;
        this.toolBar.relative(this.content).x(1F).anchorX(1F).w(TOOL_BAR_W).h(1F)
            .column(0).scroll().vertical();

        this.toolIconBrush = this.createToolIcon(Icons.BRUSH, UIKeys.TEXTURES_TOOLS_BRUSH, TexturePaintTool.BRUSH);
        this.toolIconEraser = this.createToolIcon(Icons.ERASER, UIKeys.TEXTURES_TOOLS_ERASER, TexturePaintTool.ERASER);
        this.toolIconMove = this.createToolIcon(Icons.ALL_DIRECTIONS, UIKeys.TEXTURES_TOOLS_MOVE, TexturePaintTool.MOVE);
        this.toolIconFill = this.createToolIcon(Icons.BUCKET, UIKeys.TEXTURES_TOOLS_FILL, TexturePaintTool.FILL);
        this.toolIconPipette = this.createToolIcon(Icons.EYEDROPPER, UIKeys.TEXTURES_TOOLS_EYEDROPPER, TexturePaintTool.PIPETTE);
        this.toolIconSelection = this.createToolIcon(Icons.OUTLINE, UIKeys.TEXTURES_TOOLS_SELECTION, TexturePaintTool.SELECTION);

        this.toolBar.add(this.toolIconBrush, this.toolIconEraser, this.toolIconMove,
            this.toolIconFill, this.toolIconPipette, this.toolIconSelection);
    }

    /** The bar button flips the preview: open it when closed, close it when open. */
    private void toggleModelPreview()
    {
        if (this.modelPreviewHost.isVisible())
        {
            this.closeModelPreview();
        }
        else
        {
            this.openModelPreview();
        }
    }

    private UIIcon createToolIcon(Icon icon, IKey tooltip, TexturePaintTool tool)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.userSelectTool(tool));

        button.highlight(() -> this.activeTool == tool, Direction.RIGHT);

        if (tooltip != null)
        {
            button.tooltip(tooltip, Direction.LEFT);
        }

        return button;
    }

    private void buildOptions()
    {
        /* The layers are as tall as they need, up to half the column, which is only known once the
         * column itself is laid out — so they are measured right then, before the children */
        this.optionsHost = new UIElement()
        {
            @Override
            protected void afterResizeApplied()
            {
                if (UITexturePainter.this.layersPanel != null)
                {
                    UITexturePainter.this.layersPanel.fit(this.area.h / 2);
                }
            }
        };

        this.optionsDraggable = UISplitter.fraction("texture_painter.options", DEFAULT_OPTIONS_WIDTH, 0F, 0.5F);
        this.optionsDraggable.measure(this.optionsHost, this.content).fromEnd().onChange(() ->
        {
            this.optionsHost.w(this.optionsDraggable.getValue());
            this.content.resize();
            this.optionsDraggable.resize();
        });

        this.optionsHost.relative(this.content).x(1F, -TOOL_BAR_W).anchorX(1F)
            .w(this.optionsDraggable.getValue())
            .minW(MIN_OPTIONS_WIDTH).h(1F);

        /* Its height is the section's own, so only where it sits is said here — inset like the
         * sections of the scroll view above, so the edges of all of them line up (the way the
         * replay list holds its body parts section) */
        int inset = UIConstants.SCROLL_PADDING;

        this.layersPanel = new UILayersPanel(this);
        this.layersPanel.remember(SECTION_FOLDS, "layers", true);
        this.layersPanel.relative(this.optionsHost).x(inset).y(1F, -inset).w(1F, -inset * 2).anchorY(1F);

        /* The options take what the layers leave above them; added after the layers, because hTo
         * reads the target's area from this pass only if the target was laid out first */
        this.options = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.options.scroll.cancelScrolling();
        this.options.relative(this.optionsHost).w(1F).hTo(this.layersPanel.area, 0F);

        this.optionsHost.add(this.layersPanel, this.options);

        this.optionsDraggable.relative(this.optionsHost).x(0F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.primary = new UIColor((c) -> {}).noLabel().withAlpha();
        this.primary.direction(Direction.LEFT).h(UIConstants.CONTROL_HEIGHT);
        this.primary.setColor(Colors.A100);
        this.primary.tooltip(UIKeys.TEXTURES_COLOR_PRIMARY);
        this.secondary = new UIColor((c) -> {}).noLabel().withAlpha();
        this.secondary.direction(Direction.LEFT).h(UIConstants.CONTROL_HEIGHT);
        this.secondary.setColor(Colors.WHITE);
        this.secondary.tooltip(UIKeys.TEXTURES_COLOR_SECONDARY);
        this.colorPickersRow = UI.row(UIConstants.MARGIN, this.primary, this.secondary);
        this.colorPickersRow.row().preferred(0).height(UIConstants.CONTROL_HEIGHT);

        /* A material map is painted in these colours, so they sit with the colours; the map's
         * overlay is a way of looking at it, so it goes with the view */
        this.materialPresets = UI.row(UIConstants.MARGIN,
            this.materialPreset(0xFF000000, UIKeys.TEXTURES_MATERIAL_NONE),
            this.materialPreset(0xFF0000FF, UIKeys.TEXTURES_MATERIAL_EMISSION),
            this.materialPreset(0xFFFF0000, UIKeys.TEXTURES_MATERIAL_SMOOTHNESS),
            this.materialPreset(0xFF00FF00, UIKeys.TEXTURES_MATERIAL_METAL),
            this.materialPreset(0xFFFFFF00, UIKeys.TEXTURES_MATERIAL_SMOOTH_METAL),
            this.materialPreset(0xFFFF00FF, UIKeys.TEXTURES_MATERIAL_SMOOTH_EMISSION)
        );
        this.materialPresets.row().preferred(0).height(UIConstants.CONTROL_HEIGHT);
        this.materialPresets.setVisible(false);
        this.materialOverlay = new UISliderTrackpad((v) -> this.withEditor((editor) -> editor.setMaterialOverlay(v.floatValue() / 100F)));
        this.materialOverlay.integer().limit(0, 100).setValue(50);
        this.materialOverlayRow = UI.labelRow(UIKeys.TEXTURES_MATERIAL_OVERLAY, this.materialOverlay);
        this.materialOverlayRow.setVisible(false);

        this.alphaLockToggle = new UIToggle(UIKeys.TEXTURES_ALPHA_LOCK, false, (b) -> {});
        this.alphaLockToggle.h(UIConstants.CONTROL_HEIGHT);
        this.alphaLockToggle.tooltip(UIKeys.TEXTURES_ALPHA_LOCK_TOOLTIP);

        this.brightness = new UISliderTrackpad();
        this.brightness.limit(0, 1).setValue(0.7);
        this.brightness.tooltip(UIKeys.TEXTURES_VIEWER_BRIGHTNESS);

        this.gridStep = new UITrackpad((v) -> BBSSettings.textureGridStep.set(v.intValue()));
        this.gridStep.integer().limit(1, 256, true);
        this.gridStep.tooltip(UIKeys.TEXTURES_GRID_STEP_TOOLTIP);
        this.gridStepRow = UI.labelRow(UIKeys.TEXTURES_GRID_STEP, this.gridStep);

        this.brushSize = new UITrackpad((v) -> this.setBrushSize(v.intValue()));
        this.brushSize.integer().limit(1, MAX_BRUSH_SIZE, true).setValue(1);
        this.brushSoftness = new UISliderTrackpad((v) -> {});
        this.brushSoftness.integer().limit(0, 100, true).setValue(0);

        this.brushSizeRow = UI.labelRow(UIKeys.TEXTURES_BRUSH_SIZE, this.brushSize);
        this.brushSoftnessRow = UI.labelRow(UIKeys.TEXTURES_BRUSH_SOFTNESS, this.brushSoftness);
        this.roundBrushToggle = new UIToggle(UIKeys.TEXTURES_BRUSH_SHAPE_CIRCLE, this.activeStrokeShape == TextureStrokeShape.CIRCLE, (b) -> this.setRoundBrushEnabled(b.getValue()));
        this.roundBrushToggle.h(UIConstants.CONTROL_HEIGHT);
        this.brushBuildUpToggle = new UIToggle(UIKeys.TEXTURES_BRUSH_ACCUMULATIVE, this.brushBuildUp, (b) -> this.brushBuildUp = b.getValue());
        this.brushBuildUpToggle.h(UIConstants.CONTROL_HEIGHT);

        this.eraserOpacity = new UISliderTrackpad((v) -> {});
        this.eraserOpacity.limit(0, 100).setValue(100);
        this.eraserOpacityRow = UI.labelRow(UIKeys.TEXTURES_ERASER_OPACITY, this.eraserOpacity);

        this.frametime = new UITrackpad((v) -> this.withEditor((editor) -> editor.setFrametime(v.intValue())));
        this.frametime.limit(1, UIFramesPanel.MAX_TIME).integer();
        this.frameWidth = new UITrackpad((v) -> this.withEditor((editor) ->
        {
            editor.setFrameSize(v.intValue(), (int) this.frameHeight.getValue());
            this.workingPane().frames.sync();
        }));
        this.frameWidth.limit(1, 4096).integer();
        this.frameHeight = new UITrackpad((v) -> this.withEditor((editor) ->
        {
            editor.setFrameSize((int) this.frameWidth.getValue(), v.intValue());
            this.workingPane().frames.sync();
        }));
        this.frameHeight.limit(1, 4096).integer();

        this.animationSection = new UISection(UIKeys.TEXTURES_FRAMES_SECTION).remember(SECTION_FOLDS, "animation", true);
        this.animationSection.fields.add(
            UI.labelRow(UIKeys.TEXTURES_FRAMES_FRAMETIME, this.frametime),
            UI.labelRow(UIKeys.TEXTURES_FRAMES_FRAME_WIDTH, this.frameWidth),
            UI.labelRow(UIKeys.TEXTURES_FRAMES_FRAME_HEIGHT, this.frameHeight)
        );
        this.animationSection.setVisible(false);

        /* Colour first and always, with no section to fold it away: every stroke starts there. The
         * tool in hand comes next, then the texture's animation, and the way the canvas looks last
         * and folded — it is set once and forgotten. The layers sit on the column's bottom edge. */
        this.toolSection = new UISection().remember(SECTION_FOLDS, "tool", true);
        this.toolSection.fields.add(
            this.brushSizeRow,
            this.brushSoftnessRow,
            this.roundBrushToggle,
            this.brushBuildUpToggle,
            this.eraserOpacityRow,
            this.alphaLockToggle
        );

        UISection viewSection = new UISection(UIKeys.TEXTURES_SECTION_VIEW).remember(SECTION_FOLDS, "view", false);

        viewSection.fields.add(
            UI.labelRow(UIKeys.TEXTURES_BACKGROUND, this.brightness),
            this.gridStepRow,
            this.materialOverlayRow
        );

        this.options.add(this.colorPickersRow, this.materialPresets, this.toolSection, this.animationSection, viewSection);
    }

    private UIButton materialPreset(int color, IKey label)
    {
        UIButton button = new UIButton(IKey.EMPTY, (b) -> this.primary.setColor(color)).color(color);

        button.tooltip(label).tooltipImmediate();

        return button;
    }

    private void toggleMaterial()
    {
        if (this.editor == null || this.editor.dragging)
        {
            return;
        }

        UITextureEditor base = this.editor.getBaseEditor();
        Link link = base.getTexture();

        if (link == null || !Link.isAssets(link) || !link.path.endsWith(".png"))
        {
            this.getContext().notifyError(UIKeys.TEXTURES_SAVE_WRONG_PATH);

            return;
        }

        if (base.getMaterialEditor() == null)
        {
            Link materialLink = UITextureEditor.materialLink(link);
            File file = UITextureEditor.materialFile(link);
            Document source = base.getDocument();
            Document material;
            boolean existing = file != null && file.isFile();

            if (existing)
            {
                material = this.loadDocument(materialLink, file);

                if (material == null)
                {
                    this.getContext().notifyError(UIKeys.TEXTURES_MATERIAL_LOAD_ERROR);

                    return;
                }
            }
            else
            {
                Pixels pixels = Pixels.fromSize(source.width, source.height);
                Color black = new Color(0F, 0F, 0F, 1F);

                for (int i = 0; i < pixels.getCount(); i++)
                {
                    pixels.setColor(i, black);
                }

                pixels.rewindBuffer();
                material = Document.fromPixels(materialLink, pixels);
            }

            if (material.animation == null && source.animation != null
                && material.width == source.width && material.height == source.height)
            {
                material.animation = new TextureAnimation();
                material.animation.fromData(source.animation.toData());
            }

            UITextureEditor editor = this.createEditor(material);

            base.setMaterialEditor(editor);
            editor.setDirty(!existing);
        }

        base.toggleMaterial();
        this.setEditors(this.panes[0].getTexture(), this.panes[1].getTexture(), base);
    }

    private void buildModelPreviewHost()
    {
        this.modelPreviewHost = new UIElement();
        this.modelPreviewHost.relative(this.content).x(0F).h(1F).w(0);
        this.modelPreviewHost.setVisible(false);

        this.modelPreviewPanel = new UIModelPreviewPanel(this);
        this.modelPreviewPanel.relative(this.modelPreviewHost).w(1F).h(1F);

        this.modelPreviewDraggable = UISplitter.fraction("texture_painter.preview", DEFAULT_PREVIEW_WIDTH, 0.1F, 0.8F);
        this.modelPreviewDraggable.measure(this.content).onChange(() ->
        {
            this.modelPreviewHost.w(this.modelPreviewDraggable.getValue());
            this.content.resize();
            this.modelPreviewDraggable.resize();
        });
        this.modelPreviewDraggable.relative(this.modelPreviewHost).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.modelPreviewDraggable.setVisible(false);
    }

    private void buildEditorHost()
    {
        this.editorHost = new UIElement();
        this.editorHost.relative(this.modelPreviewHost).x(1F).h(1F)
            .wTo(this.optionsHost.area, 0F, -UIConstants.MARGIN);

        UITexturePane left = new UITexturePane(this);
        UITexturePane right = new UITexturePane(this);

        /* The right pane starts past the seam after the left one, so the left one is laid out first */
        left.relative(this.editorHost).w(1F).h(1F);
        right.relative(left).x(1F, UIConstants.MARGIN).h(1F).wTo(this.editorHost.area, 1F);
        right.setVisible(false);

        this.splitDraggable = UISplitter.fraction("texture_painter.split", DEFAULT_SPLIT, MIN_SPLIT, 1F - MIN_SPLIT);
        this.splitDraggable.measure(this.editorHost).onChange(() ->
        {
            /* What is in the middle of either half stays there as the seam moves */
            for (UITexturePane pane : this.panes)
            {
                if (pane.getEditor() != null)
                {
                    pane.getEditor().anchorToMiddle();
                }
            }

            left.w(this.splitDraggable.getValue());
            this.editorHost.resize();
            this.splitDraggable.resize();
        });
        this.splitDraggable.relative(left).x(1F, UIConstants.MARGIN / 2).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.splitDraggable.setVisible(false);

        this.panes[0] = left;
        this.panes[1] = right;
        this.editorHost.add(left, right, this.splitDraggable);
    }

    private UITexturePane workingPane()
    {
        return this.panes[this.working];
    }

    private boolean isSplit()
    {
        return this.panes[1].getTexture() != null;
    }

    private void registerShortcuts()
    {
        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;

        this.keys().register(Keys.PIXEL_SWAP, this::swapColors).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_BRUSH, () -> this.userSelectTool(TexturePaintTool.BRUSH)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_ERASER, () -> this.userSelectTool(TexturePaintTool.ERASER)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_MOVE, () -> this.userSelectTool(TexturePaintTool.MOVE)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_FILL, () -> this.userSelectTool(TexturePaintTool.FILL)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_SELECTION, () -> this.userSelectTool(TexturePaintTool.SELECTION)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_DEC, () -> this.adjustBrushSize(-1)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_INC, () -> this.adjustBrushSize(1)).inside().category(category);
        this.keys().register(Keys.PIXEL_GRID, this::toggleGrid).inside().active(() -> this.editor != null).category(category);
        /* The plain steps are strict, so they step aside for their Shift and Alt+Shift variants */
        this.keys().register(Keys.PIXEL_FRAME_PREV, () -> this.workingPane().frames.step(-1)).inside().strict().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_NEXT, () -> this.workingPane().frames.step(1)).inside().strict().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_FIRST, () -> this.workingPane().frames.first()).inside().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_LAST, () -> this.workingPane().frames.last()).inside().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_ADD, () -> this.workingPane().frames.addFrame(true)).inside().strict().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_ADD_EMPTY, () -> this.workingPane().frames.addFrame(false)).inside().active(this::isAnimated).category(category);
        this.keys().register(Keys.PIXEL_FRAME_PLAY, () -> this.workingPane().frames.togglePlaying()).inside().active(this::isAnimated).category(category);

        /* Undo has to answer wherever the cursor is in the editor — over the strip, the layers,
         * the options — not only over the canvas, which keeps its own binds for when it's there
         * (a key stops at the first element that takes it, so the two never both fire) */
        this.keys().register(Keys.UNDO, () -> this.withEditor(UITextureEditor::undo)).inside().active(() -> this.editor != null).category(category);
        this.keys().register(Keys.REDO, () -> this.withEditor(UITextureEditor::redo)).inside().active(() -> this.editor != null).category(category);

        /* Ctrl+S lives on its own element so it outranks the other keybinds, the way the data
         * panels do it — the painter isn't one of those, so nothing registered it before */
        UIElement savePlease = new UIElement().noCulling();

        savePlease.keys().register(Keys.SAVE, () ->
        {
            UIUtils.playClick();
            this.withEditor(UITextureEditor::saveCurrentTexture);
        }).active(() -> this.editor != null && this.isVisible()).category(category);

        this.add(savePlease);
    }

    private void renderPanelBackground(UIContext context)
    {
        /* The base surface is the canvas backdrop spanning the whole editor; the chrome
         * surface tints the tool strip and the options column so the two read as one column
         * of controls down the right edge, matching the surfaces used across the dashboard
         * (see UIFilmPanel). */
        this.content.area.render(context.batcher, BBSSettings.baseSurface());

        this.renderChromeSurface(context, this.toolBar.area);
        this.renderChromeSurface(context, this.optionsHost.area);

        /* Two canvases stand on the same backdrop, so the seam between them is a strip of chrome */
        if (this.isSplit())
        {
            Area left = this.panes[0].area;

            context.batcher.box(left.ex(), left.y, this.panes[1].area.x, left.ey(), BBSSettings.chromeSurface());
        }
    }

    private void renderChromeSurface(UIContext context, Area area)
    {
        area.render(context.batcher, BBSSettings.chromeSurface());
    }

    public void openModelPreview()
    {
        UIModelPicker.open(this.getContext(), this.modelPreviewPanel.getModel(), this::openModelPreview);
    }

    public void openModelPreview(String model)
    {
        this.modelPreviewPanel.setModel(model);
        this.modelPreviewHost.add(this.modelPreviewPanel);
        /* The host is collapsed to 0 while closed; reopen at the width the user last dragged it to. */
        this.modelPreviewHost.w(this.modelPreviewDraggable.getValue());
        this.modelPreviewHost.setVisible(true);
        this.modelPreviewDraggable.setVisible(true);

        this.editorHost.x(1F, UIConstants.MARGIN);
        this.resize();
    }

    public void closeModelPreview()
    {
        this.modelPreviewPanel.removeFromParent();
        this.modelPreviewPanel.cleanUp();
        this.modelPreviewHost.w(0);
        this.modelPreviewHost.setVisible(false);
        this.modelPreviewDraggable.setVisible(false);

        this.editorHost.x(1F);
        this.content.resize();
    }

    private void withEditor(Consumer<UITextureEditor> action)
    {
        UITextureEditor editor = this.getCurrentEditor();

        if (editor != null)
        {
            action.accept(editor);
        }
    }

    /* The animation strip */

    private boolean isAnimated()
    {
        return this.editor != null && this.editor.isAnimated();
    }

    /**
     * The bar button flips the animation: on for a plain texture; off for an animated one —
     * after asking, when there is a .mcmeta on disk that saving would then remove.
     */
    private void toggleAnimation(UITextureEditor editor)
    {
        if (!editor.isAnimated())
        {
            this.setAnimated(editor, true);

            return;
        }

        Link link = editor.getTexture();
        File mcmeta = link != null && Link.isAssets(link) ? TextureAnimation.file(BBSMod.getProvider().getFile(link)) : null;

        if (mcmeta != null && mcmeta.isFile())
        {
            UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.TEXTURES_FRAMES_DISABLE_TITLE, UIKeys.TEXTURES_FRAMES_DISABLE_MESSAGE, (confirmed) ->
            {
                if (confirmed)
                {
                    this.setAnimated(editor, false);
                }
            });

            UIOverlay.addOverlay(this.getContext(), panel);
        }
        else
        {
            this.setAnimated(editor, false);
        }
    }

    private void setAnimated(UITextureEditor editor, boolean animated)
    {
        UITexturePane pane = this.workingPane();

        editor.setAnimated(animated);
        pane.frames.sync();
        pane.updateFrames();
        this.updateAnimationSection();
    }

    /** Turn the animation of the texture on show on, if it isn't already — the browser's way in. */
    public void enableAnimation()
    {
        this.withEditor((editor) ->
        {
            if (!editor.isAnimated())
            {
                this.setAnimated(editor, true);
            }
        });
    }

    /** The animation section is there for an animated texture worked on and gone otherwise; each pane sees to its own strip. */
    private void updateAnimationSection()
    {
        this.animationSection.setVisible(this.isAnimated());
        this.syncAnimationSettings();
        this.optionsHost.resize();
    }

    /** The section reads the animation on show; setValue doesn't fire the trackpads, so this can't loop. */
    private void syncAnimationSettings()
    {
        Document document = this.editor == null ? null : this.editor.getDocument();

        if (document == null || document.animation == null)
        {
            return;
        }

        this.frametime.setValue(document.animation.frametime);
        this.frameWidth.setValue(document.frameWidth());
        this.frameHeight.setValue(document.frameHeight());
    }

    private void adjustBrushSize(int delta)
    {
        int n = MathUtils.clamp((int) this.brushSize.getValue() + delta, 1, MAX_BRUSH_SIZE);

        if (n == (int) this.brushSize.getValue())
        {
            return;
        }

        this.brushSize.setValue(n);
        this.setBrushSize(n);
    }

    /**
     * Current pixel tool for the texture editor (single source of truth for UI and canvas input).
     */
    public TexturePaintTool getActiveTexturePaintTool()
    {
        return this.activeTool;
    }

    public TextureStrokeShape getActiveTextureStrokeShape()
    {
        return this.activeStrokeShape;
    }

    public boolean isBrushBuildUpEnabled()
    {
        return this.brushBuildUp;
    }

    private void setActiveTool(TexturePaintTool tool)
    {
        if (this.activeTool == tool)
        {
            return;
        }

        this.activeTool = tool;
        this.refreshToolUi();
    }

    /**
     * Tool change from UI or keyboard shortcuts; also cancels any active temporary mode (Alt-eyedropper or
     * right-mouse-button eraser) so releasing the modifier afterwards does not unexpectedly restore the
     * old tool.
     */
    private void userSelectTool(TexturePaintTool tool)
    {
        this.toolBeforeAltPipette = null;
        this.toolBeforeSecondaryEraser = null;
        this.setActiveTool(tool);
    }

    private void setRoundBrushEnabled(boolean value)
    {
        this.activeStrokeShape = value ? TextureStrokeShape.CIRCLE : TextureStrokeShape.SQUARE;
    }

    private void updateAltPipetteHold()
    {
        boolean alt = Window.isAltPressed();

        if (alt && this.toolBeforeAltPipette == null && this.toolBeforeSecondaryEraser == null && this.activeTool != TexturePaintTool.PIPETTE)
        {
            this.toolBeforeAltPipette = this.activeTool;
            this.setActiveTool(TexturePaintTool.PIPETTE);
        }
        else if (!alt && this.toolBeforeAltPipette != null)
        {
            this.setActiveTool(this.toolBeforeAltPipette);
            this.toolBeforeAltPipette = null;
        }
    }

    /**
     * Engages ({@code engage == true}) or disengages the right-mouse-button eraser. While engaged the
     * active tool is temporarily switched to the eraser; on release the previously selected tool (the
     * brush) is restored. Driven by {@link UIPixelsEditor}'s canvas input, mirroring the Alt-eyedropper
     * temporary mode in {@link #updateAltPipetteHold()}.
     */
    private void setSecondaryEraser(boolean engage)
    {
        if (engage)
        {
            if (this.toolBeforeSecondaryEraser == null)
            {
                this.toolBeforeSecondaryEraser = this.activeTool;
                this.setActiveTool(TexturePaintTool.ERASER);
            }
        }
        else if (this.toolBeforeSecondaryEraser != null)
        {
            this.setActiveTool(this.toolBeforeSecondaryEraser);
            this.toolBeforeSecondaryEraser = null;
        }
    }

    private void refreshToolUi()
    {
        /* Alt's eyedropper and the right button's eraser are only borrowed: the panel stays on the
         * tool they hand back to, rather than jumping about for the length of a click */
        TexturePaintTool tool = this.toolBeforeAltPipette != null ? this.toolBeforeAltPipette
            : this.toolBeforeSecondaryEraser != null ? this.toolBeforeSecondaryEraser : this.activeTool;
        boolean brush = tool == TexturePaintTool.BRUSH;
        boolean eraser = tool == TexturePaintTool.ERASER;
        boolean fill = tool == TexturePaintTool.FILL;

        this.brushSizeRow.setVisible(brush || eraser);
        this.brushSoftnessRow.setVisible(brush || eraser);
        this.roundBrushToggle.setVisible(brush || eraser);
        this.brushBuildUpToggle.setVisible(brush || eraser);
        this.eraserOpacityRow.setVisible(eraser);
        this.alphaLockToggle.setVisible(brush || fill);

        /* Selecting, moving and picking a colour have nothing to set */
        this.toolSection.setVisible(brush || eraser || fill);
        this.toolSection.title(brush ? UIKeys.TEXTURES_TOOLS_BRUSH : eraser ? UIKeys.TEXTURES_TOOLS_ERASER : UIKeys.TEXTURES_TOOLS_FILL);

        this.optionsHost.resize();
    }

    /**
     * Loads the texture behind {@code link} into a fresh, fully-wired editor without showing it. The
     * caller owns the returned editor (it must be shown via {@link #setEditors} and
     * freed via {@link UITextureEditor#deleteTexture()}). Returns null when the texture cannot be read.
     */
    public UITextureEditor openEditor(Link link)
    {
        Document document = this.loadDocument(link);

        return document == null ? null : this.createEditor(document);
    }

    private UITextureEditor createEditor(Document document)
    {
        UITextureEditor editor = new UITextureEditor();

        editor.setDocument(document);

        return editor;
    }

    /**
     * (Re)binds {@code editor}'s tools, colours and save/rename callbacks to this painter, and its
     * frames to the pane showing it. Called on every {@link #setEditors} so a shared editor works in
     * whichever picker's painter, and whichever of its panes, currently hosts it. A change made in
     * the pane not {@code working} on moves the work over there.
     */
    private void bind(UITextureEditor editor, UITexturePane pane, boolean working)
    {
        editor.saveCallback(this.saveCallback);
        editor.renameCallback((newLink) ->
        {
            if (this.renameHandler != null)
            {
                this.renameHandler.accept(editor, newLink);
            }
        });
        editor.colorSupplier(() -> this.primary.picker.color);
        editor.pickColorConsumer((color) -> this.primary.setColor(color.getARGBColor()));
        editor.backgroundSupplier(() -> (float) this.brightness.getValue());
        editor.gridSupplier(BBSSettings.textureGrid::get, BBSSettings.textureGridStep::get);
        editor.toolSupplier(this::getActiveTexturePaintTool);
        editor.strokeShapeSupplier(this::getActiveTextureStrokeShape);
        editor.strokeBuildUpSupplier(this::isBrushBuildUpEnabled);
        editor.alphaLockSupplier(() -> this.alphaLockToggle.getValue());
        editor.brushSoftnessSupplier(() -> (float) this.brushSoftness.getValue() / 100.0F);
        editor.eraserOpacitySupplier(() -> (float) this.eraserOpacity.getValue() / 100.0F);
        editor.secondaryEraserToggle(this::setSecondaryEraser);
        editor.frameStepper(pane.frames::step);
        editor.layersChangedCallback(() ->
        {
            /* An undo may have brought frames back or taken the animation away altogether */
            pane.frames.sync();
            pane.updateFrames();

            if (this.layersPanel != null && this.getCurrentEditor() == editor)
            {
                this.layersPanel.updateLayers();
                this.updateAnimationSection();
            }
        });
        editor.changeCallback(working || this.switchHandler == null ? null : () -> this.switchHandler.accept(pane.getTexture()));
        editor.setBrushSize((int) this.brushSize.getValue());
    }

    /**
     * Shows {@code left} on the canvas and {@code right} in the right half of a split screen
     * (without it the left one has the canvas to itself, and without either nothing is on show),
     * working on {@code working}, one of the two. Editors no longer on show are detached but not
     * freed — their owner keeps them alive for their tabs.
     */
    public void setEditors(UITextureEditor left, UITextureEditor right, UITextureEditor working)
    {
        boolean wasSplit = this.isSplit();
        boolean split = right != null;
        UITextureEditor[] textures = {left, right};
        UITextureEditor[] before = {this.panes[0].getTexture(), this.panes[1].getTexture()};

        /* A texture that stays in its half as the screen splits or joins keeps what is in its middle there */
        for (int i = 0; i < this.panes.length; i++)
        {
            UITextureEditor shown = this.panes[i].getEditor();

            if (shown != null && wasSplit != split && before[i] == textures[i])
            {
                shown.anchorToMiddle();
            }
        }

        this.working = split && working == right ? 1 : 0;
        this.panes[0].setTexture(left);
        this.panes[1].setTexture(right);
        this.editor = this.workingPane().getEditor();

        for (int i = 0; i < this.panes.length; i++)
        {
            UITextureEditor shown = this.panes[i].getEditor();

            if (shown == null)
            {
                continue;
            }

            /* and one coming into a half of a split screen comes in centred */
            if ((wasSplit || split) && before[i] != textures[i])
            {
                shown.centerView();
            }

            this.bind(shown, this.panes[i], i == this.working);
        }

        this.panes[0].w(split ? this.splitDraggable.getValue() : 1F);
        this.panes[1].setVisible(split);
        this.splitDraggable.setVisible(split);

        if (this.layersPanel != null)
        {
            this.layersPanel.setEditor(this.editor);
        }

        this.materialPresets.setVisible(this.editor != null && this.editor.isMaterial());
        this.materialOverlayRow.setVisible(this.editor != null && this.editor.isMaterial());
        if (this.editor != null)
        {
            this.materialOverlay.setValue(this.editor.getMaterialOverlay() * 100F);
        }
        /* Another painter may have changed the grid's step since this one was on screen */
        this.gridStep.setValue(BBSSettings.textureGridStep.get());
        this.updateAnimationSection();
        this.resize();
    }

    public UITextureEditor getCurrentEditor()
    {
        return this.editor;
    }

    /** The checkerboard brightness the user set for the canvas, 0..1 — the strip's cells go by it too. */
    public float getBackgroundBrightness()
    {
        return (float) this.brightness.getValue();
    }

    /**
     * Loads the editable document for {@code link}: the project from the {@code .dat} sidecar (or a
     * fresh single-layer one from the texture's pixels), and the animation from the {@code .mcmeta}
     * — which lives there alone, whether or not there was a project.
     */
    private Document loadDocument(Link link)
    {
        return this.loadDocument(link, null);
    }

    private Document loadDocument(Link link, File file)
    {
        Document document = this.loadProject(link, file);

        if (document != null)
        {
            document.animation = file == null ? TextureAnimation.read(link, document.width, document.height)
                : TextureAnimation.read(file, document.width, document.height);
        }

        return document;
    }

    /**
     * Deserializes the {@code NAME_INCLUDING_EXTENSION.dat} sidecar next to the texture when present,
     * otherwise builds a fresh single-layer document from the texture's pixels.
     */
    private Document loadProject(Link link, File file)
    {
        File source = file == null ? BBSMod.getProvider().getFile(link) : file;
        File datFile = source == null ? null : Document.datFile(source);

        if (datFile != null && datFile.isFile())
        {
            Document document = Document.read(link, datFile);

            if (document != null)
            {
                return document;
            }
        }

        if (file != null)
        {
            try (FileInputStream stream = new FileInputStream(file))
            {
                return Document.fromPixels(link, toRGBA(Pixels.fromPNGStream(stream)));
            }
            catch (Exception e)
            {
                return null;
            }
        }

        Pixels pixels = loadPixels(link);

        return pixels == null ? null : Document.fromPixels(link, pixels);
    }

    /**
     * The pixels of the texture file itself, four channels per pixel.
     *
     * <p>Not the GL texture: an animated texture is cut into frames when it is loaded, and
     * {@link mchorse.bbs_mod.graphics.texture.TextureManager#getTexture} hands out the frame on
     * show, so reading it back would give the editor one image of the strip in place of the whole
     * strip — every frame past the first would then point outside the document and come out
     * empty. The GL texture stays as the fallback for what the provider can't read (a downloaded
     * texture, say).</p>
     */
    private static Pixels loadPixels(Link link)
    {
        try
        {
            Pixels pixels = BBSModClient.getTextures().getPixels(link);

            if (pixels != null)
            {
                return toRGBA(pixels);
            }
        }
        catch (Exception e)
        {}

        return Texture.pixelsFromTexture(BBSModClient.getTextures().getTexture(link));
    }

    /** A PNG without an alpha channel reads back three bytes per pixel; the editor paints with four. */
    private static Pixels toRGBA(Pixels pixels)
    {
        if (pixels.bits == 4)
        {
            return pixels;
        }

        Pixels rgba = Pixels.fromSize(pixels.width, pixels.height);

        for (int y = 0; y < pixels.height; y++)
        {
            for (int x = 0; x < pixels.width; x++)
            {
                rgba.setColor(x, y, pixels.getColor(x, y));
            }
        }

        pixels.delete();
        rgba.rewindBuffer();

        return rgba;
    }

    private void swapColors()
    {
        int swap = this.primary.picker.color.getARGBColor();

        this.primary.setColor(this.secondary.picker.color.getARGBColor());
        this.secondary.setColor(swap);
    }

    /** The brush is one for both panes: the one beside outlines it under the cursor all the same. */
    private void setBrushSize(int size)
    {
        for (UITexturePane pane : this.panes)
        {
            UITextureEditor editor = pane.getEditor();

            if (editor != null)
            {
                editor.setBrushSize(size);
            }
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.updateAltPipetteHold();

        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = false;
        }

        for (UITexturePane pane : this.panes)
        {
            UITextureEditor editor = pane.getEditor();

            if (pane.isVisible() && editor != null && editor.area.isInside(context) && editor.getPixels() != null)
            {
                this.renderHoverInfo(context, editor);
            }
        }
    }

    private void renderHoverInfo(UIContext context, UITextureEditor editor)
    {
        Document document = editor.getDocument();
        Vector2i hover = editor.getHoverPixel(context.mouseX, context.mouseY);
        /* Size and position within the frame on show — what the user is looking at */
        int tw = editor.getWidth();
        int th = editor.getHeight();
        int px = tw <= 0 ? 0 : MathUtils.clamp(hover.x - editor.getFrameX(), 0, tw - 1);
        int py = th <= 0 ? 0 : MathUtils.clamp(hover.y - editor.getFrameY(), 0, th - 1);
        /* Read the merged colour across all layers at the cursor (document space). */
        Color color = editor.getMergedColor(hover.x, hover.y);

        int r = 0;
        int g = 0;
        int b = 0;
        int a = 0;

        if (color != null)
        {
            r = (int) Math.floor(color.r * 255);
            g = (int) Math.floor(color.g * 255);
            b = (int) Math.floor(color.b * 255);
            a = (int) Math.floor(color.a * 255);
        }

        String size = tw + "x" + th + " (" + px + ", " + py + ")";
        String rgba = "\u00A7cR\u00A7aG\u00A79B\u00A7rA (" + r + ", " + g + ", " + b + ", " + a + ")";
        String[] lines = document != null && document.animation != null
            ? new String[] {size, rgba, UIKeys.TEXTURES_FRAME_COUNTER.format(String.valueOf(editor.getFrame() + 1), String.valueOf(document.animation.frames.size())).get()}
            : new String[] {size, rgba};

        FontRenderer font = context.batcher.getFont();
        int margin = 10;
        int ty = editor.area.y + margin;

        for (String line : lines)
        {
            context.batcher.textShadow(line, editor.area.ex() - margin - font.getWidth(line), ty);

            ty += font.getHeight() + 2;
        }
    }
}
