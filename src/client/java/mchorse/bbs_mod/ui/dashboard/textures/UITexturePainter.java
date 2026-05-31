package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.textures.layers.UILayersPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Root texture editor panel.
 *
 * <p>Layout mirrors the mod's film editor (see
 * {@link mchorse.bbs_mod.ui.dashboard.panels.UISidebarDashboardPanel} and
 * {@link mchorse.bbs_mod.ui.film.UIFilmPanel}):</p>
 * <pre>
 *   ┌──────────────── tabs ─────────────────┐
 *   │ options ║ canvas                │ ico │
 *   │  (left) ║                       │ 20  │
 *   └─────────────────────────────────┴─────┘
 * </pre>
 * The 20px icon strip on the right combines action icons (save/resize/extract) and
 * tool icons (brush/eraser/fill/eyedropper) separated by a small gap, styled via
 * {@link mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels#renderHighlightHorizontal}.
 * The left options column is a {@link UIScrollView} with a draggable splitter whose
 * width is persisted per panel class. Closing is handled by Escape in
 * {@link mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker}.
 */
public class UITexturePainter extends UIElement
{
    private List<Document> documents = new ArrayList<>();
    private int currentIndex = -1;

    /** Persisted fractional width of the tool options column, keyed by panel class. */
    private static final Map<Class, Float> widths = new HashMap<>();

    private static final int ICON_BAR_W = 20;
    private static final int TOOL_SEPARATOR_GAP = 9;
    private static final float DEFAULT_OPTIONS_WIDTH = 0F;
    private static final int MIN_OPTIONS_WIDTH = 140;
    private static final int MAX_BRUSH_SIZE = 1024;

    public static final class Document
    {
        public Link link;
        public final Pixels pixels;
        public final UITextureEditor editor;

        public Document(Link link, Pixels pixels, UITextureEditor editor)
        {
            this.link = link;
            this.pixels = pixels;
            this.editor = editor;
        }
    }

    public UITrackpad brightness;
    public UITrackpad brushSize;
    public UITrackpad brushSoftness;

    public UIColor primary;
    public UIColor secondary;
    private UIElement colorPickersRow;

    public UIIcon saveIcon;
    public UIIcon resizeIcon;
    public UIIcon extractIcon;

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

    private UIScrollView iconBar;
    private UIElement editorHost;
    private UIScrollView options;
    private UIDraggable optionsDraggable;

    private UIElement optionsHost;
    private UILayersPanel layersPanel;

    private UIIcon toolIconBrush;
    private UIIcon toolIconEraser;
    private UIIcon toolIconFill;
    private UIIcon toolIconPipette;
    private UIIcon toolIconSelection;
    private UIIcon modelPreviewIcon;
    
    private UIElement modelPreviewHost;
    private UIDraggable modelPreviewDraggable;
    private UIModelPreviewPanel modelPreviewPanel;

    private UILabel brushSizeLabel;
    private UILabel brushSoftnessLabel;
    private UIToggle roundBrushToggle;
    private UIToggle brushBuildUpToggle;
    private UIToggle alphaLockToggle;
    private UILabel eraserOpacityLabel;
    private UITrackpad eraserOpacity;

    private UITextureTabs tabs;
    private UIElement content;
    private final Consumer<Link> saveCallback;

    public UITexturePainter(Consumer<Link> saveCallback)
    {
        this.saveCallback = saveCallback;

        this.tabs = new UITextureTabs(this);
        this.tabs.relative(this).w(1F).h(UITextureTabs.TABS_HEIGHT_PX);

        this.content = new UIElement();
        this.content.relative(this.tabs).y(1F).w(1F).hTo(this.area, 1F);

        this.buildIconBar();
        this.buildOptions();
        this.buildModelPreviewHost();
        this.buildEditorHost();

        /* modelPreviewHost must be added (and thus resized) before editorHost: when the preview
         * is open editorHost.wTo(modelPreviewHost.area), so the canvas width is computed from the
         * preview's area, which has to be up to date by the time editorHost resizes. */
        this.content.add(new UIRenderable(this::renderPanelBackground),
            this.iconBar, this.optionsHost, this.modelPreviewHost, this.editorHost, this.optionsDraggable, this.modelPreviewDraggable);
        this.add(this.tabs, this.content);

        this.syncTabs();
        this.showCurrentEditor();
        this.refreshToolUi();
        this.registerShortcuts();
    }

    private void buildIconBar()
    {
        this.iconBar = new UIScrollView();
        this.iconBar.scroll.cancelScrolling().noScrollbar();
        this.iconBar.scroll.scrollSpeed = 5;
        this.iconBar.relative(this.content).x(1F).w(ICON_BAR_W).h(1F).anchorX(1F)
            .column(0).scroll().vertical();
        this.iconBar.preRender(this::renderActiveToolHighlight);

        this.saveIcon = new UIIcon(() ->
        {
            UITextureEditor ed = this.getCurrentEditor();

            return ed != null && ed.isDirty() ? Icons.SAVE : Icons.SAVED;
        }, (b) -> this.withEditor(UITextureEditor::saveCurrentTexture));
        this.saveIcon.tooltip(UIKeys.GENERAL_SAVE, Direction.LEFT);
        this.saveIcon.context((menu) -> menu.action(Icons.SAVED, UIKeys.TEXTURES_SAVE_AS, () -> this.withEditor(UITextureEditor::openSaveOverlay)));

        this.resizeIcon = new UIIcon(Icons.FULLSCREEN, (b) -> this.withEditor(UITextureEditor::openResizeOverlay));
        this.resizeIcon.tooltip(UIKeys.TEXTURES_RESIZE, Direction.LEFT);
        this.extractIcon = new UIIcon(Icons.UPLOAD, (b) -> this.withEditor(UITextureEditor::openExtractOverlay));
        this.extractIcon.tooltip(UIKeys.TEXTURES_EXTRACT_FRAMES_TITLE, Direction.LEFT);

        this.toolIconBrush = this.createToolIcon(Icons.BRUSH, UIKeys.TEXTURES_TOOLS_BRUSH, TexturePaintTool.BRUSH);
        this.toolIconEraser = this.createToolIcon(Icons.ERASER, UIKeys.TEXTURES_TOOLS_ERASER, TexturePaintTool.ERASER);
        this.toolIconFill = this.createToolIcon(Icons.BUCKET, UIKeys.TEXTURES_TOOLS_FILL, TexturePaintTool.FILL);
        this.toolIconPipette = this.createToolIcon(Icons.EYEDROPPER, UIKeys.TEXTURES_TOOLS_EYEDROPPER, TexturePaintTool.PIPETTE);
        this.toolIconSelection = this.createToolIcon(Icons.OUTLINE, UIKeys.TEXTURES_TOOLS_SELECTION, TexturePaintTool.SELECTION);
        this.modelPreviewIcon = new UIIcon(Icons.POSE, (b) -> this.openModelPreview());
        this.modelPreviewIcon.tooltip(UIKeys.TEXTURES_PREVIEW_MODEL, Direction.LEFT);

        this.iconBar.add(this.saveIcon, this.resizeIcon, this.extractIcon,
            this.toolIconBrush.marginTop(TOOL_SEPARATOR_GAP),
            this.toolIconEraser, this.toolIconFill, this.toolIconPipette,
            this.toolIconSelection,
            this.modelPreviewIcon.marginTop(TOOL_SEPARATOR_GAP));
    }

    private UIIcon createToolIcon(Icon icon, IKey tooltip, TexturePaintTool tool)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.userSelectTool(tool));

        if (tooltip != null)
        {
            button.tooltip(tooltip, Direction.LEFT);
        }

        return button;
    }

    private void buildOptions()
    {
        this.optionsHost = new UIElement();
        this.optionsHost.relative(this.content).x(0F)
            .w(widths.getOrDefault(this.getClass(), DEFAULT_OPTIONS_WIDTH))
            .minW(MIN_OPTIONS_WIDTH).h(1F);

        this.options = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.options.scroll.cancelScrolling();
        this.options.relative(this.optionsHost).w(1F).h(0.5F);

        this.layersPanel = new UILayersPanel(this);
        this.layersPanel.relative(this.optionsHost).y(0.5F).w(1F).h(0.5F);
        
        this.optionsHost.add(this.options, this.layersPanel);

        this.optionsDraggable = new UIDraggable((context) ->
        {
            float f = (context.mouseX - this.optionsHost.area.x) / (float) this.content.area.w;
            float w = MathUtils.clamp(f, 0F, 0.5F);

            this.optionsHost.w(w);
            widths.put(this.getClass(), w);
            this.content.resize();
            this.optionsDraggable.resize();
        });
        this.optionsDraggable.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        this.optionsDraggable.relative(this.optionsHost).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

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

        this.alphaLockToggle = new UIToggle(UIKeys.TEXTURES_ALPHA_LOCK, false, (b) -> {});
        this.alphaLockToggle.h(UIConstants.CONTROL_HEIGHT);

        this.brightness = new UITrackpad();
        this.brightness.limit(0, 1).setValue(0.7);
        this.brightness.tooltip(UIKeys.TEXTURES_VIEWER_BRIGHTNESS);

        this.brushSize = new UITrackpad((v) -> this.setBrushSize(v.intValue()));
        this.brushSize.integer().limit(1, MAX_BRUSH_SIZE, true).setValue(1);
        this.brushSoftness = new UITrackpad((v) -> {});
        this.brushSoftness.integer().limit(0, 100, true).setValue(0);

        this.brushSizeLabel = UI.label(UIKeys.TEXTURES_BRUSH_SIZE);
        this.brushSoftnessLabel = UI.label(UIKeys.TEXTURES_BRUSH_SOFTNESS);
        this.roundBrushToggle = new UIToggle(UIKeys.TEXTURES_BRUSH_SHAPE_CIRCLE, this.activeStrokeShape == TextureStrokeShape.CIRCLE, (b) -> this.setRoundBrushEnabled(b.getValue()));
        this.roundBrushToggle.h(UIConstants.CONTROL_HEIGHT);
        this.brushBuildUpToggle = new UIToggle(UIKeys.TEXTURES_BRUSH_ACCUMULATIVE, this.brushBuildUp, (b) -> this.brushBuildUp = b.getValue());
        this.brushBuildUpToggle.h(UIConstants.CONTROL_HEIGHT);

        this.eraserOpacityLabel = UI.label(UIKeys.TEXTURES_ERASER_OPACITY);
        this.eraserOpacity = new UITrackpad((v) -> {});
        this.eraserOpacity.limit(0, 100).setValue(100);

        this.options.add(
            this.colorPickersRow,
            this.alphaLockToggle,
            this.brightness,
            this.brushSizeLabel, this.brushSize,
            this.brushSoftnessLabel, this.brushSoftness,
            this.roundBrushToggle,
            this.brushBuildUpToggle,
            this.eraserOpacityLabel, this.eraserOpacity
        );
    }

    private void buildModelPreviewHost()
    {
        this.modelPreviewHost = new UIElement();
        this.modelPreviewHost.relative(this.content).x(1F, -ICON_BAR_W).h(1F).w(0).anchorX(1F);
        this.modelPreviewHost.setVisible(false);

        this.modelPreviewPanel = new UIModelPreviewPanel(this);
        this.modelPreviewPanel.relative(this.modelPreviewHost).w(1F).h(1F);

        this.modelPreviewDraggable = new UIDraggable((context) ->
        {
            float f = (this.iconBar.area.x - context.mouseX) / (float) this.content.area.w;
            float w = MathUtils.clamp(f, 0.1F, 0.8F);

            this.modelPreviewHost.w(w);
            this.content.resize();
            this.modelPreviewDraggable.resize();
        });
        this.modelPreviewDraggable.cursors(GLFW.GLFW_HRESIZE_CURSOR, GLFW.GLFW_HRESIZE_CURSOR);
        this.modelPreviewDraggable.relative(this.modelPreviewHost).x(0F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);
        this.modelPreviewDraggable.setVisible(false);
    }

    private void buildEditorHost()
    {
        this.editorHost = new UIElement();
        this.editorHost.relative(this.optionsHost).x(1F, UIConstants.MARGIN).h(1F)
            .wTo(this.iconBar.area, 0F, -UIConstants.MARGIN);
    }

    private void registerShortcuts()
    {
        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;

        this.keys().register(Keys.PIXEL_SWAP, this::swapColors).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_BRUSH, () -> this.userSelectTool(TexturePaintTool.BRUSH)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_ERASER, () -> this.userSelectTool(TexturePaintTool.ERASER)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_FILL, () -> this.userSelectTool(TexturePaintTool.FILL)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_SELECTION, () -> this.userSelectTool(TexturePaintTool.SELECTION)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_DEC, () -> this.adjustBrushSize(-1)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_INC, () -> this.adjustBrushSize(1)).inside().category(category);
        this.keys().register(Keys.CYCLE_PANELS, this::cycleTabs).inside().category(category);
    }

    private void renderPanelBackground(UIContext context)
    {
        /* The base surface is the canvas backdrop spanning the whole editor; the chrome
         * surface tints the tool/layer columns and the icon bar to group the editing
         * controls, matching the surfaces used across the dashboard (see UIFilmPanel). A
         * soft fade still spills off the icon bar onto the canvas, anchoring it visually. */
        this.content.area.render(context.batcher, BBSSettings.baseSurface());
        this.content.area.render(context.batcher, BBSSettings.backgroundTint(Colors.A6));

        this.renderChromeSurface(context, this.optionsHost.area);
    }

    private void renderChromeSurface(UIContext context, Area area)
    {
        area.render(context.batcher, BBSSettings.chromeSurface());
        area.render(context.batcher, BBSSettings.backgroundTint(Colors.A6));
    }

    private void renderActiveToolHighlight(UIContext context)
    {
        UIIcon active = this.getActiveToolIcon();

        if (active != null)
        {
            UIDashboardPanels.renderHighlightHorizontal(context.batcher, active.area);
        }
    }

    private UIIcon getActiveToolIcon()
    {
        return switch (this.activeTool)
        {
            case BRUSH -> this.toolIconBrush;
            case ERASER -> this.toolIconEraser;
            case FILL -> this.toolIconFill;
            case PIPETTE -> this.toolIconPipette;
            case SELECTION -> this.toolIconSelection;
        };
    }

    public void openModelPreview()
    {
        UIListOverlayPanel list = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, this::openModelPreview);

        list.addValues(BBSModClient.getModels().getAvailableKeys());
        list.list.list.sort();

        UIOverlay.addOverlay(this.getContext(), list);
    }

    public void openModelPreview(String model)
    {
        this.modelPreviewPanel.setModel(model);
        this.modelPreviewHost.add(this.modelPreviewPanel);
        this.modelPreviewHost.w(0.3F);
        this.modelPreviewHost.setVisible(true);
        this.modelPreviewDraggable.setVisible(true);
        
        this.editorHost.wTo(this.modelPreviewHost.area, 0F, -UIConstants.MARGIN);
        this.resize();
    }

    public void closeModelPreview()
    {
        this.modelPreviewPanel.removeFromParent();
        this.modelPreviewPanel.cleanUp();
        this.modelPreviewHost.w(0);
        this.modelPreviewHost.setVisible(false);
        this.modelPreviewDraggable.setVisible(false);
        
        this.editorHost.wTo(this.iconBar.area, 0F, -UIConstants.MARGIN);
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
        boolean strokeTool = this.activeTool == TexturePaintTool.BRUSH || this.activeTool == TexturePaintTool.ERASER;
        boolean eraserTool = this.activeTool == TexturePaintTool.ERASER;

        this.brushSizeLabel.setVisible(strokeTool);
        this.brushSize.setVisible(strokeTool);
        this.brushSoftnessLabel.setVisible(strokeTool);
        this.brushSoftness.setVisible(strokeTool);
        this.roundBrushToggle.setVisible(strokeTool);
        this.brushBuildUpToggle.setVisible(strokeTool);
        this.eraserOpacityLabel.setVisible(eraserTool);
        this.eraserOpacity.setVisible(eraserTool);

        this.optionsHost.resize();
    }

    private void cycleTabs()
    {
        if (documents.size() <= 1)
        {
            return;
        }

        this.switchTab(MathUtils.cycler(currentIndex + (Window.isShiftPressed() ? -1 : 1), documents));

        UIUtils.playClick();
    }

    public int getTabCount()
    {
        return documents.size();
    }

    public int getCurrentTabIndex()
    {
        return currentIndex;
    }

    public IKey getTabLabel(int index)
    {
        return IKey.raw(StringUtils.fileName(documents.get(index).link.path));
    }

    public IKey getTabTooltip(int index)
    {
        return IKey.raw(documents.get(index).link.path);
    }

    public Icon getTabIcon(int index)
    {
        return Icons.MATERIAL;
    }

    public boolean canCloseTab(int index)
    {
        return documents.size() > 1 && index >= 0 && index < documents.size();
    }

    public void switchTab(int index)
    {
        if (index < 0 || index >= documents.size() || currentIndex == index)
        {
            return;
        }

        currentIndex = index;
        this.showCurrentEditor();
        this.syncTabs();
        
        if (this.layersPanel != null)
        {
            this.layersPanel.setEditor(this.getCurrentEditor());
        }
    }

    private UITextureEditor createEditor(Link link, Pixels pixels)
    {
        UITextureEditor editor = new UITextureEditor().saveCallback(this.saveCallback);
        editor.renameCallback((newLink) -> this.renameDocument(editor, newLink));
        editor.colorSupplier(() -> this.primary.picker.color);
        editor.pickColorConsumer((color) -> this.primary.setColor(color.getARGBColor()));
        editor.backgroundSupplier(() -> (float) this.brightness.getValue());
        editor.toolSupplier(this::getActiveTexturePaintTool);
        editor.strokeShapeSupplier(this::getActiveTextureStrokeShape);
        editor.strokeBuildUpSupplier(this::isBrushBuildUpEnabled);
        editor.alphaLockSupplier(() -> this.alphaLockToggle.getValue());
        editor.brushSoftnessSupplier(() -> (float) this.brushSoftness.getValue() / 100.0F);
        editor.eraserOpacitySupplier(() -> (float) this.eraserOpacity.getValue() / 100.0F);
        editor.secondaryEraserToggle(this::setSecondaryEraser);
        editor.setBrushSize((int) this.brushSize.getValue());
        editor.setDocument(link, pixels);
        editor.full(this.editorHost);
        return editor;
    }

    /**
     * Reassigns {@code editor}'s document to {@code newLink} after a Save As, closing any
     * pre-existing tab that already referenced {@code newLink} (its contents are discarded
     * since the file on disk was just overwritten).
     */
    private void renameDocument(UITextureEditor editor, Link newLink)
    {
        int editorIndex = -1;
        int duplicateIndex = -1;

        for (int i = 0; i < documents.size(); i++)
        {
            Document doc = documents.get(i);

            if (doc.editor == editor)
            {
                editorIndex = i;
            }
            else if (doc.link.equals(newLink))
            {
                duplicateIndex = i;
            }
        }

        if (editorIndex < 0)
        {
            return;
        }

        if (duplicateIndex >= 0)
        {
            this.removeTab(duplicateIndex);

            if (duplicateIndex < editorIndex)
            {
                editorIndex -= 1;
            }
        }

        documents.get(editorIndex).link = newLink;
        this.finishTabMutation();
    }

    private void showCurrentEditor()
    {
        if (documents.isEmpty())
        {
            return;
        }

        List<IUIElement> hostChildren = this.editorHost.getChildren();

        if (!hostChildren.isEmpty() && hostChildren.get(0) instanceof UITextureEditor currentInHost)
        {
            this.editorHost.remove(currentInHost);
        }

        UITextureEditor toShow = documents.get(currentIndex).editor;

        toShow.removeFromParent();
        this.editorHost.prepend(toShow);
        toShow.full(this.editorHost);
        toShow.setBrushSize((int) this.brushSize.getValue());

        this.resize();
    }

    public UITextureEditor getCurrentEditor()
    {
        return documents.isEmpty() || currentIndex < 0 || currentIndex >= documents.size()
            ? null
            : documents.get(currentIndex).editor;
    }

    private void addTabFromPath(String path)
    {
        this.openOrAddDocument(Link.create(path), false);
    }

    public void closeTab(int index)
    {
        if (!this.canCloseTab(index))
        {
            return;
        }

        this.removeTab(index);
        this.finishTabMutation();
    }

    public void closeOtherTabs(int index)
    {
        if (index < 0 || index >= documents.size() || documents.size() <= 1)
        {
            return;
        }

        for (int i = documents.size() - 1; i >= 0; i--)
        {
            if (i != index)
            {
                this.removeTab(i);
            }
        }

        this.finishTabMutation();
    }

    public void closeTabsLeft(int index)
    {
        if (index <= 0 || index >= documents.size())
        {
            return;
        }

        for (int i = index - 1; i >= 0; i--)
        {
            this.removeTab(i);
        }

        this.finishTabMutation();
    }

    public void closeTabsRight(int index)
    {
        if (index < 0 || index >= documents.size() - 1)
        {
            return;
        }

        for (int i = documents.size() - 1; i > index; i--)
        {
            this.removeTab(i);
        }

        this.finishTabMutation();
    }

    public void openNewTab()
    {
        UITextureEditor current = this.getCurrentEditor();
        Link currentLink = current != null ? current.getTexture() : null;

        UITexturePicker.findAllTextures(this.getContext(), currentLink, this::addTabFromPath);
    }

    private void removeTab(int index)
    {
        if (index < 0 || index >= documents.size())
        {
            return;
        }

        Document doc = documents.remove(index);

        doc.editor.removeFromParent();
        doc.pixels.delete();
        doc.editor.deleteTexture();

        if (documents.isEmpty())
        {
            currentIndex = -1;
        }
        else if (index < currentIndex)
        {
            currentIndex -= 1;
        }
        else if (currentIndex >= documents.size())
        {
            currentIndex = documents.size() - 1;
        }
    }

    private void finishTabMutation()
    {
        if (currentIndex >= 0 && currentIndex < documents.size())
        {
            this.showCurrentEditor();
        }

        this.syncTabs();
        
        if (this.layersPanel != null)
        {
            this.layersPanel.setEditor(this.getCurrentEditor());
        }
    }

    private void syncTabs()
    {
        if (this.tabs != null)
        {
            this.tabs.sync();
        }
    }

    private void swapColors()
    {
        int swap = this.primary.picker.color.getARGBColor();

        this.primary.setColor(this.secondary.picker.color.getARGBColor());
        this.secondary.setColor(swap);
    }

    private void setBrushSize(int size)
    {
        UITextureEditor editor = this.getCurrentEditor();
        if (editor != null)
        {
            editor.setBrushSize(size);
        }
    }

    public void fillTexture(Link current)
    {
        if (current != null)
        {
            this.openOrAddDocument(current, true);
        }
    }

    /**
     * Switches to an existing tab for {@code link} if one is open, otherwise loads the texture and
     * adds a new tab. When {@code setEditing} is {@code true} the resulting editor is marked as editing.
     */
    private void openOrAddDocument(Link link, boolean setEditing)
    {
        String path = link.toString();

        for (int i = 0; i < documents.size(); i++)
        {
            if (documents.get(i).link.toString().equals(path))
            {
                this.switchTab(i);

                if (setEditing)
                {
                    this.getCurrentEditor().setEditing(true);
                }

                return;
            }
        }

        Texture t = BBSModClient.getTextures().getTexture(link);
        Pixels pixels = Texture.pixelsFromTexture(t);

        if (pixels == null)
        {
            return;
        }

        UITextureEditor editor = this.createEditor(link, pixels);
        documents.add(new Document(link, pixels, editor));
        this.switchTab(documents.size() - 1);

        if (setEditing)
        {
            editor.setEditing(true);
        }
    }

    @Override
    public void render(UIContext context)
    {
        this.updateAltPipetteHold();

        super.render(context);

        UITextureEditor editor = this.getCurrentEditor();

        if (editor != null && editor.area.isInside(context) && editor.getPixels() != null)
        {
            this.renderHoverInfo(context, editor);
        }
    }

    private void renderHoverInfo(UIContext context, UITextureEditor editor)
    {
        Pixels pixels = editor.getPixels();
        Vector2i hover = editor.getHoverPixel(context.mouseX, context.mouseY);
        int tw = pixels.width;
        int th = pixels.height;
        int px = tw <= 0 ? 0 : MathUtils.clamp(hover.x, 0, tw - 1);
        int py = th <= 0 ? 0 : MathUtils.clamp(hover.y, 0, th - 1);
        Color color = pixels.getColor(px, py);

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

        String[] lines = {
            tw + "x" + th + " (" + px + ", " + py + ")",
            "\u00A7cR\u00A7aG\u00A79B\u00A7rA (" + r + ", " + g + ", " + b + ", " + a + ")",
        };

        FontRenderer font = context.batcher.getFont();
        int margin = 10;
        int ty = this.editorHost.area.y + margin;

        for (String line : lines)
        {
            context.batcher.textShadow(line, this.editorHost.area.ex() - margin - font.getWidth(line), ty);

            ty += font.getHeight() + 2;
        }
    }
}
