package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
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
 * tool icons (brush/eraser/fill/pipette) separated by a small gap, styled via
 * {@link mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels#renderHighlightHorizontal}.
 * The left options column is a {@link UIScrollView} with a draggable splitter whose
 * width is persisted per panel class. Closing is handled by Escape in
 * {@link mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker}.
 */
public class UITexturePainter extends UIElement
{
    private static List<Document> documents = new ArrayList<>();
    private static int currentIndex = -1;

    /** Persisted fractional width of the tool options column, keyed by panel class. */
    private static final Map<Class, Float> widths = new HashMap<>();

    private static final int ICON_BAR_W = 20;
    private static final int TOOL_SEPARATOR_GAP = 9;
    private static final float DEFAULT_OPTIONS_WIDTH = 0.22F;
    private static final int MIN_OPTIONS_WIDTH = 140;
    private static final int MAX_BRUSH_SIZE = 64;

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

    public UIColor primary;
    public UIColor secondary;
    private UIElement colorPickersRow;

    public UIIcon saveIcon;
    public UIIcon resizeIcon;
    public UIIcon extractIcon;

    private TexturePaintTool activeTool = TexturePaintTool.BRUSH;

    /**
     * Non-null while Alt is held to temporarily use the pipette; stores the tool to restore on Alt release.
     * Null means Alt-pipette is not active.
     */
    private TexturePaintTool toolBeforeAltPipette;

    private UIScrollView iconBar;
    private UIElement editorHost;
    private UIScrollView options;
    private UIDraggable optionsDraggable;

    private UIIcon toolIconBrush;
    private UIIcon toolIconEraser;
    private UIIcon toolIconFill;
    private UIIcon toolIconPipette;

    private UILabel brushSizeLabel;
    private UILabel fillToolHint;

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
        this.buildEditorHost();

        this.content.add(new UIRenderable(this::renderPanelBackground),
            this.iconBar, this.options, this.editorHost, this.optionsDraggable);
        this.add(this.tabs, this.content, this.brightness);

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
        }, (b) -> this.withEditor(UITextureEditor::openSaveOverlay));
        this.saveIcon.tooltip(UIKeys.TEXTURES_SAVE, Direction.LEFT);

        this.resizeIcon = new UIIcon(Icons.FULLSCREEN, (b) -> this.withEditor(UITextureEditor::openResizeOverlay));
        this.resizeIcon.tooltip(UIKeys.TEXTURES_RESIZE, Direction.LEFT);
        this.extractIcon = new UIIcon(Icons.UPLOAD, (b) -> this.withEditor(UITextureEditor::openExtractOverlay));
        this.extractIcon.tooltip(UIKeys.TEXTURES_EXTRACT_FRAMES_TITLE, Direction.LEFT);

        this.toolIconBrush = this.createToolIcon(Icons.BRUSH, UIKeys.TEXTURES_TOOLS_BRUSH, TexturePaintTool.BRUSH);
        this.toolIconEraser = this.createToolIcon(Icons.ERASER, UIKeys.TEXTURES_TOOLS_ERASER, TexturePaintTool.ERASER);
        this.toolIconFill = this.createToolIcon(Icons.BUCKET, UIKeys.TEXTURES_TOOLS_FILL, TexturePaintTool.FILL);
        this.toolIconPipette = this.createToolIcon(Icons.PIPETTE,
            IKey.comp(List.of(UIKeys.TEXTURES_TOOLS_PIPETTE, IKey.constant("\n"), UIKeys.TEXTURES_TOOLS_PIPETTE_HINT)),
            TexturePaintTool.PIPETTE);

        this.iconBar.add(this.saveIcon, this.resizeIcon, this.extractIcon,
            this.toolIconBrush.marginTop(TOOL_SEPARATOR_GAP),
            this.toolIconEraser, this.toolIconFill, this.toolIconPipette);
    }

    private UIIcon createToolIcon(Icon icon, IKey tooltip, TexturePaintTool tool)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.userSelectTool(tool));

        button.tooltip(tooltip, Direction.LEFT);

        return button;
    }

    private void buildOptions()
    {
        this.options = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING);
        this.options.scroll.cancelScrolling();
        this.options.relative(this.content).x(0F)
            .w(widths.getOrDefault(this.getClass(), DEFAULT_OPTIONS_WIDTH))
            .minW(MIN_OPTIONS_WIDTH).h(1F);

        this.optionsDraggable = new UIDraggable((context) ->
        {
            float f = (context.mouseX - this.options.area.x) / (float) this.content.area.w;
            float w = MathUtils.clamp(f, 0F, 0.5F);

            this.options.w(w).resize();
            widths.put(this.getClass(), w);
            this.optionsDraggable.resize();
        });
        this.optionsDraggable.relative(this.options).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.primary = new UIColor((c) -> {}).noLabel();
        this.primary.direction(Direction.LEFT).h(UIConstants.CONTROL_HEIGHT);
        this.primary.setColor(0);
        this.secondary = new UIColor((c) -> {}).noLabel();
        this.secondary.direction(Direction.LEFT).h(UIConstants.CONTROL_HEIGHT);
        this.secondary.setColor(Colors.WHITE);
        this.colorPickersRow = UI.row(UIConstants.MARGIN, this.primary, this.secondary);
        this.colorPickersRow.row().preferred(0).height(UIConstants.CONTROL_HEIGHT);

        this.brushSize = new UITrackpad((v) -> this.setBrushSize(v.intValue()));
        this.brushSize.integer().limit(1, MAX_BRUSH_SIZE, true).setValue(1);

        this.brushSizeLabel = UI.label(UIKeys.TEXTURES_BRUSH_SIZE);
        this.fillToolHint = new UILabel(UIKeys.TEXTURES_KEYS_FILL, Colors.LIGHTEST_GRAY);

        this.options.add(
            UI.label(UIKeys.TEXTURES_COLOR_PRIMARY), this.colorPickersRow,
            this.brushSizeLabel, this.brushSize, this.fillToolHint);
    }

    private void buildEditorHost()
    {
        this.editorHost = new UIElement();
        this.editorHost.relative(this.options).x(1F, UIConstants.MARGIN).h(1F)
            .wTo(this.iconBar.area, 0F, -UIConstants.MARGIN);

        this.brightness = new UITrackpad();
        this.brightness.limit(0, 1).setValue(0.7);
        this.brightness.tooltip(UIKeys.TEXTURES_VIEWER_BRIGHTNESS, Direction.TOP);
        this.brightness.relative(this.editorHost).x(1F, -10).y(1F, -10).w(130).anchor(1F, 1F);
    }

    private void registerShortcuts()
    {
        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;

        this.keys().register(Keys.PIXEL_SWAP, this::swapColors).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_BRUSH, () -> this.userSelectTool(TexturePaintTool.BRUSH)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_ERASER, () -> this.userSelectTool(TexturePaintTool.ERASER)).inside().category(category);
        this.keys().register(Keys.PIXEL_TOOL_FILL, () -> this.userSelectTool(TexturePaintTool.FILL)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_DEC, () -> this.adjustBrushSize(-1)).inside().category(category);
        this.keys().register(Keys.PIXEL_BRUSH_INC, () -> this.adjustBrushSize(1)).inside().category(category);
        this.keys().register(Keys.CYCLE_PANELS, this::cycleTabs).inside().category(category);
    }

    private void renderPanelBackground(UIContext context)
    {
        /* Same look as UISidebarDashboardPanel.renderBackground: flat fill on the bar
         * with a soft fade spilling over the canvas, anchoring the sidebar visually. */
        this.iconBar.area.render(context.batcher, Colors.A50);
        context.batcher.gradientHBox(this.iconBar.area.x - 6, this.iconBar.area.y,
            this.iconBar.area.x, this.iconBar.area.ey(), 0, 0x29000000);
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
        };
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
     * Tool change from UI or keyboard shortcuts; also cancels any active Alt-pipette temporary mode so
     * releasing Alt afterwards does not unexpectedly restore the old tool.
     */
    private void userSelectTool(TexturePaintTool tool)
    {
        this.toolBeforeAltPipette = null;
        this.setActiveTool(tool);
    }

    private void updateAltPipetteHold()
    {
        boolean alt = Window.isAltPressed();

        if (alt && this.toolBeforeAltPipette == null && this.activeTool != TexturePaintTool.PIPETTE)
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

    private void refreshToolUi()
    {
        boolean strokeTool = this.activeTool == TexturePaintTool.BRUSH || this.activeTool == TexturePaintTool.ERASER;
        boolean fillTool = this.activeTool == TexturePaintTool.FILL;

        this.brushSizeLabel.setVisible(strokeTool);
        this.brushSize.setVisible(strokeTool);
        this.fillToolHint.setVisible(fillTool);

        this.options.resize();
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
    }

    private UITextureEditor createEditor(Link link, Pixels pixels)
    {
        UITextureEditor editor = new UITextureEditor().saveCallback(this.saveCallback);
        editor.renameCallback((newLink) -> this.renameDocument(editor, newLink));
        editor.colorSupplier(() -> this.primary.picker.color);
        editor.pickColorConsumer((color) -> this.primary.setColor(color.getRGBColor()));
        editor.backgroundSupplier(() -> (float) this.brightness.getValue());
        editor.toolSupplier(this::getActiveTexturePaintTool);
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

    private UITextureEditor getCurrentEditor()
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
        int swap = this.primary.picker.color.getRGBColor();

        this.primary.setColor(this.secondary.picker.color.getRGBColor());
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
