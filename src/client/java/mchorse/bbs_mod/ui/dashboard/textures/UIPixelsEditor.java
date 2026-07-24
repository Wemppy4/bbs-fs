package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.ImageClipboard;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.undo.LayerStateUndo;
import mchorse.bbs_mod.ui.dashboard.textures.undo.PixelsUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UICanvasEditor;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.interps.rasterizers.LineRasterizer;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.utils.undo.IUndo;
import mchorse.bbs_mod.utils.undo.UndoManager;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureLayer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIPixelsEditor extends UICanvasEditor
{
    private static final double[] BRUSH_SAMPLE_OFFSETS = {0.25D, 0.75D};

    public UIElement toolbar;

    private int brushSize = 1;

    protected Document document;

    private Texture temporary;
    private Pixels pixels;

    private boolean editing;
    private Color drawColor;
    private boolean blendStroke;
    private final Color blendedStrokeColor = new Color();
    private final Color weightedStrokeColor = new Color();
    private final Map<Integer, Float> strokeStrengths = new HashMap<>();
    private Vector2i lastPixel;

    private boolean hasSelection;
    private List<SelectionRect> selections = new ArrayList<>();
    private SelectionRect currentSelection;
    private boolean currentSelectionSubtract;

    public static class SelectionRect
    {
        public int x1, y1, x2, y2;

        public SelectionRect(int x1, int y1, int x2, int y2)
        {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public boolean isInside(int x, int y)
        {
            int minX = Math.min(this.x1, this.x2);
            int maxX = Math.max(this.x1, this.x2);
            int minY = Math.min(this.y1, this.y2);
            int maxY = Math.max(this.y1, this.y2);

            int i = 0;

            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    protected UndoManager<Document> undoManager;
    private PixelsUndo pixelsUndo;

    /** Notified after an undo/redo rebuilds or reorders layers, so the owning UI can refresh. */
    private Runnable layersChangedCallback = () -> {};

    /** Document snapshot captured at the start of a move-tool drag (one undo per move gesture). */
    private MapType moveUndoBefore;

    private Supplier<Float> backgroundSupplier = () -> 0.7F;
    private Supplier<Color> colorSupplier = Color::white;
    private Consumer<Color> pickColorConsumer = (c) -> {};

    private Supplier<TexturePaintTool> toolSupplier = () -> TexturePaintTool.BRUSH;
    private Supplier<TextureStrokeShape> strokeShapeSupplier = () -> TextureStrokeShape.SQUARE;
    private Supplier<Boolean> strokeBuildUpSupplier = () -> false;
    private Supplier<Boolean> alphaLockSupplier = () -> false;
    private Supplier<Float> brushSoftnessSupplier = () -> 0.0F;
    private Supplier<Float> eraserOpacitySupplier = () -> 1.0F;

    private Consumer<Boolean> secondaryEraserToggle = (engage) -> {};

    /** True while the current stroke is the right-mouse-button temporary eraser. */
    private boolean secondaryEraser;

    /** Active layer's pixel offset captured at the start of a move-tool drag. */
    private int moveStartOffsetX;
    private int moveStartOffsetY;

    public UIPixelsEditor()
    {
        super();

        this.toolbar = new UIElement();
        this.toolbar.relative(this).w(1F).h(30).row(0).resize().padding(5);

        this.add(this.toolbar);

        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;
        Supplier<Boolean> texture = () -> this.pixels != null;
        Supplier<Boolean> editing = () -> this.editing;

        this.keys().register(Keys.COPY, this::copyImage).label(UIKeys.TEXTURES_COPY_IMAGE).inside().active(texture).category(category);
        this.keys().register(Keys.PIXEL_COPY_HEX, this::copyPixel).label(UIKeys.TEXTURES_VIEWER_CONTEXT_COPY_HEX).inside().active(texture).category(category);
        this.keys().register(Keys.CUT, this::cut).label(UIKeys.GENERAL_CUT).inside().active(editing).category(category);
        this.keys().register(Keys.PASTE, this::pasteImage).label(UIKeys.TEXTURES_PASTE_IMAGE).inside().active(editing).category(category);
        this.keys().register(Keys.UNDO, this::undo).inside().active(editing).category(category);
        this.keys().register(Keys.REDO, this::redo).inside().active(editing).category(category);
        this.keys().register(Keys.PIXEL_DESELECT, this::clearSelection).inside().active(editing).category(category);

        this.setEditing(false);
    }

    public void clearSelection()
    {
        if (this.hasSelection)
        {
            this.hasSelection = false;
            this.selections.clear();
            this.currentSelection = null;
            this.invalidateSelectionMask();
        }
    }

    /** Whether there is a non-empty pixel selection (a real region, not the implicit whole-canvas). */
    public boolean hasSelection()
    {
        return this.hasSelection && !this.selections.isEmpty();
    }

    /**
     * Cuts the current selection out of the active layer and moves it onto a new layer above it.
     * The new layer is document-sized with a zero offset, so the lifted pixels keep their on-canvas
     * position. Recorded as a single undo step (the cut and the new layer are captured together).
     */
    public void createLayerFromSelection()
    {
        TextureLayer source = this.document == null ? null : this.document.getActiveLayer();

        if (source == null || !this.hasSelection())
        {
            return;
        }

        this.recordLayerChange(null, () ->
        {
            int ox = source.offsetX;
            int oy = source.offsetY;
            int w = this.document.width;
            int h = this.document.height;
            Pixels lifted = Pixels.fromSize(w, h);
            Color transparent = new Color(0F, 0F, 0F, 0F);

            for (int dx = 0; dx < w; dx++)
            {
                for (int dy = 0; dy < h; dy++)
                {
                    if (!this.isInsideSelection(dx, dy))
                    {
                        continue;
                    }

                    int lx = dx - ox;
                    int ly = dy - oy;

                    if (lx < 0 || ly < 0 || lx >= source.pixels.width || ly >= source.pixels.height)
                    {
                        continue;
                    }

                    Color color = source.pixels.getColor(lx, ly);

                    if (color != null)
                    {
                        lifted.setColor(dx, dy, color);
                        source.pixels.setColor(lx, ly, transparent);
                    }
                }
            }

            source.updateTexture();

            int index = this.document.activeLayerIndex + 1;

            this.document.layers.add(index, new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(String.valueOf(this.document.layers.size() + 1)).get(), lifted));
            this.setActiveLayer(index);
            this.clearSelection();
        });
    }

    public boolean isInsideSelection(int x, int y)
    {
        if (!this.hasSelection || this.selections.isEmpty())
        {
            return true;
        }

        for (SelectionRect rect : this.selections)
        {
            if (rect.isInside(x, y))
            {
                return true;
            }
        }

        return false;
    }

    public UIPixelsEditor colorSupplier(Supplier<Color> supplier)
    {
        this.colorSupplier = supplier;

        return this;
    }

    protected Color getActiveDrawColor()
    {
        return this.colorSupplier.get();
    }

    public UIPixelsEditor backgroundSupplier(Supplier<Float> supplier)
    {
        this.backgroundSupplier = supplier;

        return this;
    }

    public void selectLayerBounds()
    {
        if (this.pixels == null)
        {
            return;
        }

        /* The selection mask lives in document space, but the layer's pixels are shifted by its
         * move-tool offset, so map each opaque pixel to document coordinates (and skip anything the
         * offset pushed off-canvas). */
        int ox = this.getActiveOffsetX();
        int oy = this.getActiveOffsetY();

        boolean[][] mask = this.createSelectionMask();
        boolean any = false;

        for (int x = 0; x < this.pixels.width; x++)
        {
            for (int y = 0; y < this.pixels.height; y++)
            {
                Color color = this.pixels.getColor(x, y);

                if (color != null && color.a > 0F)
                {
                    int dx = x + ox;
                    int dy = y + oy;

                    if (dx >= 0 && dy >= 0 && dx < this.w && dy < this.h)
                    {
                        mask[dx][dy] = true;
                        any = true;
                    }
                }
            }
        }

        if (any)
        {
            this.selections = this.buildSelectionsFromMask(mask);
            this.hasSelection = !this.selections.isEmpty();
            this.currentSelectionSubtract = false;
            this.invalidateSelectionMask();
        }
        else
        {
            this.clearSelection();
        }
    }

    public UIPixelsEditor pickColorConsumer(Consumer<Color> consumer)
    {
        this.pickColorConsumer = consumer != null ? consumer : (c) -> {};

        return this;
    }

    public Document getDocument()
    {
        return this.document;
    }

    /**
     * Colour at a document pixel composited across all layers (offsets and
     * opacity included) — the colour the user actually sees. {@code null} when
     * there's no document.
     */
    public Color getMergedColor(int x, int y)
    {
        return this.document == null ? null : this.document.getColorAt(x, y);
    }

    public void setActiveLayer(int index)
    {
        if (this.document != null && index >= 0 && index < this.document.layers.size())
        {
            this.document.activeLayerIndex = index;
            this.pixels = this.document.layers.get(index).pixels;
            this.temporary = this.document.layers.get(index).texture;
        }
    }

    public Texture getTemporaryTexture()
    {
        if (this.document == null || this.document.layers.isEmpty())
        {
            return this.temporary;
        }

        Pixels flat = this.flattenLayers();
        if (flat != null)
        {
            if (this.temporaryFlat == null)
            {
                this.temporaryFlat = new Texture();
                this.temporaryFlat.setFilter(GL11.GL_NEAREST);
            }
            this.temporaryFlat.bind();
            this.temporaryFlat.updateTexture(flat);
            flat.delete();
            return this.temporaryFlat;
        }

        return this.temporary;
    }

    public Pixels getPixels()
    {
        return this.pixels;
    }

    public int getBrushSize()
    {
        return this.brushSize;
    }

    public UIPixelsEditor setBrushSize(int size)
    {
        this.brushSize = MathUtils.clamp(size, 1, 1024);

        return this;
    }

    public UIPixelsEditor toolSupplier(Supplier<TexturePaintTool> supplier)
    {
        this.toolSupplier = supplier != null ? supplier : () -> TexturePaintTool.BRUSH;

        return this;
    }

    public UIPixelsEditor strokeShapeSupplier(Supplier<TextureStrokeShape> supplier)
    {
        this.strokeShapeSupplier = supplier != null ? supplier : () -> TextureStrokeShape.SQUARE;

        return this;
    }

    public UIPixelsEditor strokeBuildUpSupplier(Supplier<Boolean> supplier)
    {
        this.strokeBuildUpSupplier = supplier != null ? supplier : () -> false;

        return this;
    }

    public UIPixelsEditor alphaLockSupplier(Supplier<Boolean> supplier)
    {
        this.alphaLockSupplier = supplier != null ? supplier : () -> false;

        return this;
    }

    public UIPixelsEditor brushSoftnessSupplier(Supplier<Float> supplier)
    {
        this.brushSoftnessSupplier = supplier != null ? supplier : () -> 0.0F;

        return this;
    }

    public UIPixelsEditor eraserOpacitySupplier(Supplier<Float> supplier)
    {
        this.eraserOpacitySupplier = supplier != null ? supplier : () -> 1.0F;

        return this;
    }

    /**
     * Sets the hook used to engage ({@code true}) or disengage ({@code false}) the temporary
     * right-mouse-button eraser. The owner ({@link UITexturePainter}) flips the active tool in
     * response. See {@link #subMouseClicked(UIContext)}.
     */
    public UIPixelsEditor secondaryEraserToggle(Consumer<Boolean> consumer)
    {
        this.secondaryEraserToggle = consumer != null ? consumer : (engage) -> {};

        return this;
    }

    protected TexturePaintTool getActivePaintTool()
    {
        TexturePaintTool tool = this.toolSupplier.get();

        return tool == null ? TexturePaintTool.BRUSH : tool;
    }

    protected TextureStrokeShape getActiveStrokeShape()
    {
        TextureStrokeShape shape = this.strokeShapeSupplier.get();

        return shape == null ? TextureStrokeShape.SQUARE : shape;
    }

    protected boolean isStrokeBuildUpEnabled()
    {
        return Boolean.TRUE.equals(this.strokeBuildUpSupplier.get());
    }

    protected boolean isAlphaLockEnabled()
    {
        return Boolean.TRUE.equals(this.alphaLockSupplier.get());
    }

    protected float getBrushSoftness()
    {
        return MathUtils.clamp(this.brushSoftnessSupplier.get(), 0F, 1F);
    }

    /**
     * Invoked for the flood-fill tool on LMB down. Default does nothing;
     * {@link UITextureEditor} performs {@link UITextureEditor#fillColor}.
     */
    protected void onFillAt(Vector2i pixel)
    {}

    private boolean isStrokePaintTool()
    {
        TexturePaintTool t = this.getActivePaintTool();

        return t == TexturePaintTool.BRUSH || t == TexturePaintTool.ERASER;
    }

    /**
     * Move-tool offset of the active layer. Tools work in document/canvas coordinates, but the
     * layer's pixel buffer is indexed in layer-local space &mdash; subtract this offset to convert
     * a document coordinate into the active layer's buffer coordinate.
     */
    protected int getActiveOffsetX()
    {
        TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();

        return layer == null ? 0 : layer.offsetX;
    }

    protected int getActiveOffsetY()
    {
        TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();

        return layer == null ? 0 : layer.offsetY;
    }

    private void paint(int x, int y)
    {
        int left = (this.brushSize - 1) / 2;
        int right = this.brushSize / 2;
        TextureStrokeShape shape = this.getActiveStrokeShape();

        for (int dx = -left; dx <= right; dx++)
        {
            for (int dy = -left; dy <= right; dy++)
            {
                float strength = this.getBrushStrength(dx, dy, left, shape);

                if (strength > 0F)
                {
                    this.paintPixel(x + dx, y + dy, strength);
                }
            }
        }
    }

    private void paintPixel(int x, int y, float strength)
    {
        if (strength <= 0F)
        {
            return;
        }

        /* x,y are document coordinates: the selection is checked in document space, while the
         * active layer's buffer is indexed in layer-local space (shifted by the move-tool offset). */
        if (!this.isInsideSelection(x, y))
        {
            return;
        }

        int lx = x - this.getActiveOffsetX();
        int ly = y - this.getActiveOffsetY();

        if (lx < 0 || ly < 0 || lx >= this.pixels.width || ly >= this.pixels.height)
        {
            return;
        }

        if (!this.isStrokeBuildUpEnabled())
        {
            int index = this.pixels.toIndex(lx, ly);
            float previousStrength = this.strokeStrengths.getOrDefault(index, 0F);

            if (strength <= previousStrength)
            {
                return;
            }

            this.strokeStrengths.put(index, strength);
        }

        Color color = this.drawColor;

        if (this.isStrokePaintTool() && this.getActivePaintTool() == TexturePaintTool.ERASER)
        {
            float opacity = this.eraserOpacitySupplier.get() * strength;

            if (opacity < 1.0F)
            {
                Color destination;

                if (this.isStrokeBuildUpEnabled())
                {
                    destination = this.pixels.getColor(lx, ly);
                }
                else
                {
                    destination = this.pixelsUndo.getOriginalColor(this.pixels, lx, ly);

                    if (destination == null)
                    {
                        destination = this.pixels.getColor(lx, ly);
                    }
                }

                if (destination != null)
                {
                    color = destination.copy();
                    color.a = color.a * (1.0F - opacity);
                }
            }
        }
        else if (this.blendStroke)
        {
            Color destination;
            Color source = this.getWeightedStrokeColor(this.drawColor, strength);

            if (this.isStrokeBuildUpEnabled())
            {
                destination = this.pixels.getColor(lx, ly);
            }
            else
            {
                destination = this.pixelsUndo.getOriginalColor(this.pixels, lx, ly);

                if (destination == null)
                {
                    destination = this.pixels.getColor(lx, ly);
                }
            }

            color = this.blendColorOver(destination, source);
        }

        if (this.isAlphaLockEnabled())
        {
            Color destination;

            if (this.isStrokeBuildUpEnabled())
            {
                destination = this.pixels.getColor(lx, ly);
            }
            else
            {
                destination = this.pixelsUndo.getOriginalColor(this.pixels, lx, ly);

                if (destination == null)
                {
                    destination = this.pixels.getColor(lx, ly);
                }
            }

            if (destination == null || destination.a <= 0F)
            {
                return;
            }

            if (color == this.drawColor || color == this.blendedStrokeColor)
            {
                color = color.copy();
            }

            color.a = destination.a;
        }

        this.pixelsUndo.setColor(this.pixels, lx, ly, color);
    }

    private Color getWeightedStrokeColor(Color source, float strength)
    {
        this.weightedStrokeColor.copy(source);
        this.weightedStrokeColor.a *= MathUtils.clamp(strength, 0F, 1F);

        return this.weightedStrokeColor;
    }

    private Color blendColorOver(Color destination, Color source)
    {
        float outA = source.a + destination.a * (1F - source.a);

        if (outA <= 0F)
        {
            this.blendedStrokeColor.set(0F, 0F, 0F, 0F);

            return this.blendedStrokeColor;
        }

        float outR = (source.r * source.a + destination.r * destination.a * (1F - source.a)) / outA;
        float outG = (source.g * source.a + destination.g * destination.a * (1F - source.a)) / outA;
        float outB = (source.b * source.a + destination.b * destination.a * (1F - source.a)) / outA;

        this.blendedStrokeColor.set(outR, outG, outB, outA);

        return this.blendedStrokeColor;
    }

    private boolean isCircleMaskCell(int dx, int dy, int left)
    {
        int size = this.brushSize;
        double center = (size - 1) / 2D;
        double radius = size / 2D;
        double x = dx + left - center;
        double y = dy + left - center;

        return x * x + y * y <= radius * radius;
    }

    private float getBrushStrength(int dx, int dy, int left, TextureStrokeShape shape)
    {
        int size = this.brushSize;
        double center = (size - 1) / 2D;
        double radius = size / 2D;
        float softness = this.getBrushSoftness();

        if (softness <= 0F)
        {
            return shape == TextureStrokeShape.CIRCLE && !this.isCircleMaskCell(dx, dy, left) ? 0F : 1F;
        }

        if (shape == TextureStrokeShape.CIRCLE)
        {
            double strength = 0D;

            for (double sampleY : BRUSH_SAMPLE_OFFSETS)
            {
                for (double sampleX : BRUSH_SAMPLE_OFFSETS)
                {
                    double x = dx + left - center + sampleX - 0.5D;
                    double y = dy + left - center + sampleY - 0.5D;

                    strength += this.sampleBrushStrength(x, y, radius, softness, shape);
                }
            }

            return (float) (strength / (BRUSH_SAMPLE_OFFSETS.length * BRUSH_SAMPLE_OFFSETS.length));
        }

        double x = dx + left - center;
        double y = dy + left - center;

        return (float) this.sampleBrushStrength(x, y, radius, softness, shape);
    }

    private double sampleBrushStrength(double x, double y, double radius, float softness, TextureStrokeShape shape)
    {
        double distance = shape == TextureStrokeShape.CIRCLE
            ? Math.sqrt(x * x + y * y)
            : Math.max(Math.abs(x), Math.abs(y));

        if (distance >= radius)
        {
            return 0D;
        }

        double innerRadius = Math.max(0D, radius * (1D - softness));

        if (distance <= innerRadius)
        {
            return 1D;
        }

        double fade = Math.max(radius - innerRadius, 0.0001D);
        double t = MathUtils.clamp((distance - innerRadius) / fade, 0D, 1D);

        return this.normalizedGaussianFalloff(t);
    }

    private double normalizedGaussianFalloff(double value)
    {
        double t = MathUtils.clamp(value, 0D, 1D);
        double sigma = 0.42D;
        double edge = Math.exp(-1D / (2D * sigma * sigma));
        double current = Math.exp(-(t * t) / (2D * sigma * sigma));

        return MathUtils.clamp((current - edge) / (1D - edge), 0D, 1D);
    }

    private void renderStrokePreview(UIContext context, int pixelX, int pixelY)
    {
        int left = (this.brushSize - 1) / 2;
        int right = this.brushSize / 2;

        if (this.getActiveStrokeShape() == TextureStrokeShape.SQUARE)
        {
            context.batcher.outline(
                (int) Math.round(this.scaleX.to(pixelX - left)), (int) Math.round(this.scaleY.to(pixelY - left)),
                (int) Math.round(this.scaleX.to(pixelX + right + 1)), (int) Math.round(this.scaleY.to(pixelY + right + 1)),
                Colors.A50
            );

            return;
        }

        double minX = this.scaleX.to(pixelX - left);
        double minY = this.scaleY.to(pixelY - left);
        double maxX = this.scaleX.to(pixelX + right + 1);
        double maxY = this.scaleY.to(pixelY + right + 1);

        this.renderSmoothCirclePreview(context, minX, minY, maxX, maxY);
    }

    private void renderSmoothCirclePreview(UIContext context, double minX, double minY, double maxX, double maxY)
    {
        double cx = (minX + maxX) / 2D;
        double cy = (minY + maxY) / 2D;
        double rx = Math.abs(maxX - minX) / 2D;
        double ry = Math.abs(maxY - minY) / 2D;
        int segments = MathUtils.clamp((int) Math.ceil(Math.max(rx, ry) * 6D), 24, 256);

        int px = (int) Math.round(cx + rx);
        int py = (int) Math.round(cy);

        for (int i = 1; i <= segments; i++)
        {
            double angle = Math.PI * 2D * i / segments;
            int x = (int) Math.round(cx + Math.cos(angle) * rx);
            int y = (int) Math.round(cy + Math.sin(angle) * ry);

            this.renderPixelLine(context, px, py, x, y);

            px = x;
            py = y;
        }
    }

    private void renderPixelLine(UIContext context, int x0, int y0, int x1, int y1)
    {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));

        if (steps <= 0)
        {
            context.batcher.box(x0, y0, x0 + 1, y0 + 1, Colors.A50);
            return;
        }

        for (int i = 0; i <= steps; i++)
        {
            int x = (int) Math.round(x0 + dx * (i / (double) steps));
            int y = (int) Math.round(y0 + dy * (i / (double) steps));

            context.batcher.box(x, y, x + 1, y + 1, Colors.A50);
        }
    }

    private boolean[][] createSelectionMask()
    {
        return new boolean[this.w][this.h];
    }

    private void fillSelectionMask(boolean[][] mask, SelectionRect rect, boolean selected)
    {
        if (rect == null)
        {
            return;
        }

        int minX = Math.max(0, Math.min(rect.x1, rect.x2));
        int maxX = Math.min(this.w - 1, Math.max(rect.x1, rect.x2));
        int minY = Math.max(0, Math.min(rect.y1, rect.y2));
        int maxY = Math.min(this.h - 1, Math.max(rect.y1, rect.y2));

        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                mask[x][y] = selected;
            }
        }
    }

    private boolean[][] buildSelectionMask()
    {
        boolean[][] mask = this.createSelectionMask();

        for (SelectionRect rect : this.selections)
        {
            this.fillSelectionMask(mask, rect, true);
        }

        return mask;
    }

    private List<SelectionRect> buildSelectionsFromMask(boolean[][] mask)
    {
        List<SelectionRect> result = new ArrayList<>();
        List<SelectionRect> active = new ArrayList<>();

        for (int y = 0; y < this.h; y++)
        {
            List<SelectionRect> next = new ArrayList<>();
            List<int[]> spans = new ArrayList<>();
            boolean[] used;

            for (int x = 0; x < this.w; x++)
            {
                if (!mask[x][y])
                {
                    continue;
                }

                int start = x;

                while (x + 1 < this.w && mask[x + 1][y])
                {
                    x++;
                }

                spans.add(new int[] {start, x});
            }

            used = new boolean[spans.size()];

            for (SelectionRect rect : active)
            {
                int match = -1;

                for (int i = 0; i < spans.size(); i++)
                {
                    int[] span = spans.get(i);

                    if (!used[i] && rect.x1 == span[0] && rect.x2 == span[1])
                    {
                        match = i;
                        break;
                    }
                }

                if (match >= 0)
                {
                    rect.y2 = y;
                    next.add(rect);
                    used[match] = true;
                }
                else
                {
                    result.add(rect);
                }
            }

            for (int i = 0; i < spans.size(); i++)
            {
                if (!used[i])
                {
                    int[] span = spans.get(i);

                    next.add(new SelectionRect(span[0], y, span[1], y));
                }
            }

            active = next;
        }

        result.addAll(active);

        return result;
    }

    private void applyCurrentSelection()
    {
        if (this.currentSelection == null)
        {
            return;
        }

        boolean[][] mask = this.buildSelectionMask();

        this.fillSelectionMask(mask, this.currentSelection, !this.currentSelectionSubtract);
        this.selections = this.buildSelectionsFromMask(mask);
        this.hasSelection = !this.selections.isEmpty();
        this.currentSelection = null;
        this.currentSelectionSubtract = false;
        this.invalidateSelectionMask();
    }

    /** Mark the GPU selection mask stale so it's rebuilt before the next render. */
    private void invalidateSelectionMask()
    {
        this.selectionMaskDirty = true;
    }

    /**
     * Rebuild the GPU selection mask from the current selection (committed rects plus the in-progress
     * drag rectangle): white where selected, transparent elsewhere. Runs only on selection change (or
     * each frame during an active drag); the per-frame render just samples this texture in a shader.
     */
    private void updateSelectionMaskTexture()
    {
        boolean[][] mask = this.buildSelectionMask();

        this.fillSelectionMask(mask, this.currentSelection, !this.currentSelectionSubtract);

        if (this.selectionMaskPixels == null || this.selectionMaskPixels.width != this.w || this.selectionMaskPixels.height != this.h)
        {
            if (this.selectionMaskPixels != null)
            {
                this.selectionMaskPixels.delete();
            }

            this.selectionMaskPixels = Pixels.fromSize(this.w, this.h);
        }

        Color color = new Color();

        for (int x = 0; x < this.w; x++)
        {
            for (int y = 0; y < this.h; y++)
            {
                float value = mask[x][y] ? 1F : 0F;

                color.set(value, value, value, value);
                this.selectionMaskPixels.setColor(x, y, color);
            }
        }

        if (this.selectionMaskTexture == null)
        {
            this.selectionMaskTexture = new Texture();
            this.selectionMaskTexture.bind();
            this.selectionMaskTexture.setFilter(GL11.GL_NEAREST);
            this.selectionMaskTexture.setWrap(GL12.GL_CLAMP_TO_EDGE);
        }

        this.selectionMaskPixels.rewindBuffer();
        this.selectionMaskTexture.bind();
        this.selectionMaskTexture.updateTexture(this.selectionMaskPixels);
    }

    /**
     * Draw the selection outline by sampling the mask texture in the {@code selection} shader, which
     * marks border texels (selected texels adjacent to an unselected one) with an animated
     * marching-ants pattern, placed on the selected (inner) side. One textured quad over the document
     * area &mdash; no per-pixel CPU work.
     */
    private void renderSelection(UIContext context)
    {
        if (this.selectionMaskDirty || this.currentSelection != null)
        {
            this.updateSelectionMaskTexture();
            this.selectionMaskDirty = false;
        }

        ShaderProgram shader = BBSShaders.getSelectionProgram();

        if (shader == null || this.selectionMaskTexture == null)
        {
            return;
        }

        /* Re-derive the document area right here: calculate() returns a shared Area instance that the
         * layer loop in renderCanvasFrame overwrites with each layer's offset, so a document area
         * computed earlier is stale by now. Copy the values into locals immediately (the only call
         * after this is texturedBox, which doesn't touch the shared Area). */
        int x = -this.w / 2;
        int y = -this.h / 2;
        Area area = this.calculate(x, y, x + this.w, y + this.h);
        int ax = area.x;
        int ay = area.y;
        int aw = area.w;
        int ah = area.h;

        GlUniform phase = shader.getUniform("Phase");

        if (phase != null)
        {
            /* Only the parity matters for the marching ants, so toggle 0/1 (avoids float precision
             * loss from feeding a huge millisecond count into the shader). */
            phase.set((float) ((System.currentTimeMillis() / 150L) % 2L));
        }

        GlUniform scale = shader.getUniform("Scale");

        if (scale != null)
        {
            /* Screen pixels per document pixel, so the shader can keep the outline a constant
             * on-screen size regardless of canvas size / zoom. */
            scale.set(aw / (float) this.selectionMaskTexture.width);
        }

        context.batcher.texturedBox(
            () -> shader, this.selectionMaskTexture.id, Colors.WHITE,
            ax, ay, aw, ah,
            0, 0, this.selectionMaskTexture.width, this.selectionMaskTexture.height,
            this.selectionMaskTexture.width, this.selectionMaskTexture.height
        );
    }

    protected void wasChanged()
    {}

    public boolean isEditing()
    {
        return this.editing;
    }

    public void toggleEditor()
    {
        this.setEditing(!this.editing);
    }

    public void setEditing(boolean editing)
    {
        this.editing = editing;

        this.toolbar.setVisible(editing);

        if (editing)
        {
            this.undoManager = new UndoManager<>();
            this.undoManager.setCallback(this::handleUndo);
        }
        else
        {
            this.undoManager = null;
        }

        this.pixelsUndo = null;
    }

    private void handleUndo(IUndo<Document> undo, boolean redo)
    {
        if (this.document == null)
        {
            return;
        }

        /* A structural undo (LayerStateUndo) may have rebuilt the layer list, invalidating the
         * cached active layer; re-sync it, refresh every layer's GPU texture and notify the UI. */
        if (this.document.layers.isEmpty())
        {
            this.pixels = null;
            this.temporary = null;
        }
        else
        {
            this.setActiveLayer(MathUtils.clamp(this.document.activeLayerIndex, 0, this.document.layers.size() - 1));
        }

        for (TextureLayer layer : this.document.layers)
        {
            layer.updateTexture();
        }

        this.wasChanged();
        this.layersChangedCallback.run();
    }

    public UIPixelsEditor layersChangedCallback(Runnable callback)
    {
        this.layersChangedCallback = callback != null ? callback : () -> {};

        return this;
    }

    /**
     * Runs a layer-management mutation and records it as a single undoable step by snapshotting the
     * document before and after. {@code mergeTag} (nullable) lets consecutive changes of the same
     * kind &mdash; e.g. an opacity drag &mdash; collapse into one undo entry.
     */
    public void recordLayerChange(String mergeTag, Runnable mutation)
    {
        if (this.undoManager == null || this.document == null)
        {
            mutation.run();

            return;
        }

        MapType before = this.document.toData();

        mutation.run();

        this.undoManager.pushUndo(new LayerStateUndo(before, this.document.toData(), mergeTag));
    }

    /**
     * Ctrl+C: copy the active layer's pixels to the clipboard as an image. With a selection, only the
     * selected region is copied (cropped to its bounding box, unselected pixels left transparent).
     * Falls back to the displayed pixels in viewer mode (no document). Hex-pixel copy lives on
     * Ctrl+Shift+C ({@link #copyPixel()}). Returns {@code false} when there is nothing to copy.
     */
    private boolean copyImage()
    {
        TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();
        Pixels source = layer != null ? layer.pixels : this.pixels;

        if (source == null)
        {
            return false;
        }

        Pixels copy;
        int originX;
        int originY;

        if (this.hasSelection())
        {
            Vector2i origin = new Vector2i();

            copy = this.extractSelection(source, layer != null ? layer.offsetX : 0, layer != null ? layer.offsetY : 0, origin);

            if (copy == null)
            {
                return false;
            }

            originX = origin.x;
            originY = origin.y;
        }
        else
        {
            copy = source.createCopy(0, 0, source.width, source.height);
            /* The layer's pixel buffer top-left sits at its move offset in document space. */
            originX = layer != null ? layer.offsetX : 0;
            originY = layer != null ? layer.offsetY : 0;
        }

        ImageClipboard.copy(copy, originX, originY);
        copy.delete();
        UIUtils.playClick();

        return true;
    }

    /**
     * Ctrl+X / layers "cut": copy the selection (or the whole active layer when nothing is selected) to
     * the clipboard, then erase that region from the active layer (set transparent). The layer itself
     * stays. Recorded as one undo step.
     */
    public void cut()
    {
        TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();

        if (layer == null || layer.pixels == null || !this.copyImage())
        {
            return;
        }

        this.recordLayerChange(null, () ->
        {
            Pixels pixels = layer.pixels;
            Color transparent = new Color(0F, 0F, 0F, 0F);
            boolean selection = this.hasSelection();
            int ox = layer.offsetX;
            int oy = layer.offsetY;

            for (int x = 0; x < pixels.width; x++)
            {
                for (int y = 0; y < pixels.height; y++)
                {
                    if (!selection || this.isInsideSelection(x + ox, y + oy))
                    {
                        pixels.setColor(x, y, transparent);
                    }
                }
            }

            layer.updateTexture();
            this.wasChanged();
        });
    }

    /**
     * Copy the merged (flattened) visible layers to the clipboard as an image, respecting the current
     * selection (only the selected region, cropped to its bounding box). No-op when there's nothing to
     * flatten.
     */
    public void copyMerged()
    {
        Pixels flat = this.flattenLayers();

        if (flat == null)
        {
            return;
        }

        if (this.hasSelection())
        {
            Vector2i origin = new Vector2i();
            Pixels cropped = this.extractSelection(flat, 0, 0, origin);

            if (cropped != null)
            {
                ImageClipboard.copy(cropped, origin.x, origin.y);
                cropped.delete();
                UIUtils.playClick();
            }
        }
        else
        {
            ImageClipboard.copy(flat, 0, 0);
            UIUtils.playClick();
        }

        flat.delete();
    }

    /**
     * Extract the current selection from a document-space {@code source} into a new Pixels cropped to
     * the selection's bounding box (unselected pixels left transparent). {@code offsetX/Y} map document
     * coordinates into the source (0 for a document-sized image; the layer's offset for a layer). When
     * non-null, {@code outOrigin} receives the document-space top-left of the cropped region so the
     * caller can preserve its on-canvas position on paste. Returns {@code null} when the selection is
     * empty.
     */
    private Pixels extractSelection(Pixels source, int offsetX, int offsetY, Vector2i outOrigin)
    {
        int w = this.document != null ? this.document.width : source.width;
        int h = this.document != null ? this.document.height : source.height;
        int minX = w, minY = h, maxX = -1, maxY = -1;

        for (int dx = 0; dx < w; dx++)
        {
            for (int dy = 0; dy < h; dy++)
            {
                if (this.isInsideSelection(dx, dy))
                {
                    if (dx < minX) minX = dx;
                    if (dy < minY) minY = dy;
                    if (dx > maxX) maxX = dx;
                    if (dy > maxY) maxY = dy;
                }
            }
        }

        if (maxX < minX || maxY < minY)
        {
            return null;
        }

        if (outOrigin != null)
        {
            outOrigin.set(minX, minY);
        }

        Pixels copy = Pixels.fromSize(maxX - minX + 1, maxY - minY + 1);

        for (int dx = minX; dx <= maxX; dx++)
        {
            for (int dy = minY; dy <= maxY; dy++)
            {
                if (!this.isInsideSelection(dx, dy))
                {
                    continue;
                }

                int lx = dx - offsetX;
                int ly = dy - offsetY;

                if (lx < 0 || ly < 0 || lx >= source.width || ly >= source.height)
                {
                    continue;
                }

                Color color = source.getColor(lx, ly);

                if (color != null)
                {
                    copy.setColor(dx - minX, dy - minY, color);
                }
            }
        }

        return copy;
    }

    /**
     * Ctrl+V (and the layers panel's "paste as layer"): paste a clipboard image as a new layer above
     * the active one. When the clipboard remembers where the image was copied from, it is placed back
     * at that on-canvas position; otherwise it is centered. Clipped to the canvas. No-op when the
     * clipboard holds no image. Recorded as a single undo step; refreshes the layers list.
     */
    public void pasteImage()
    {
        if (this.document == null)
        {
            return;
        }

        Pixels pasted = ImageClipboard.paste();

        if (pasted == null)
        {
            return;
        }

        this.recordLayerChange(null, () ->
        {
            int w = this.document.width;
            int h = this.document.height;
            Pixels layerPixels = Pixels.fromSize(w, h);

            int px = ImageClipboard.hasOrigin() ? ImageClipboard.getOriginX() : (w - pasted.width) / 2;
            int py = ImageClipboard.hasOrigin() ? ImageClipboard.getOriginY() : (h - pasted.height) / 2;

            layerPixels.draw(pasted, px, py);
            layerPixels.rewindBuffer();

            int index = this.document.activeLayerIndex + 1;

            if (index < 0)
            {
                index = this.document.layers.size();
            }

            this.document.layers.add(index, new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(String.valueOf(this.document.layers.size() + 1)).get(), layerPixels));
            this.setActiveLayer(index);
            this.clearSelection();
            this.wasChanged();
        });

        this.layersChangedCallback.run();

        pasted.delete();
        UIUtils.playClick();
    }

    private void copyPixel()
    {
        UIContext context = this.getContext();
        int pixelX = (int) Math.floor(this.scaleX.from(context.mouseX)) + this.w / 2;
        int pixelY = (int) Math.floor(this.scaleY.from(context.mouseY)) + this.h / 2;
        Color color = this.getMergedColor(pixelX, pixelY);

        if (color != null)
        {
            Window.setClipboard(color.stringify());

            UIUtils.playClick();
        }
    }

    protected void updateTexture()
    {
        TextureLayer active = this.document == null ? null : this.document.getActiveLayer();

        if (active != null)
        {
            active.updateTexture();
        }
    }

    public void undo()
    {
        if (this.undoManager != null && this.undoManager.undo(this.document))
        {
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.undoManager != null && this.undoManager.redo(this.document))
        {
            UIUtils.playClick();
        }
    }

    public void deleteTexture()
    {
        if (this.document != null)
        {
            this.document.delete();
        }

        this.temporary = null;
        this.pixels = null;

        if (this.temporaryFlat != null)
        {
            this.temporaryFlat.delete();
            this.temporaryFlat = null;
        }

        if (this.selectionMaskTexture != null)
        {
            this.selectionMaskTexture.delete();
            this.selectionMaskTexture = null;
        }

        if (this.selectionMaskPixels != null)
        {
            this.selectionMaskPixels.delete();
            this.selectionMaskPixels = null;
        }

        this.selectionMaskDirty = true;
    }

    /**
     * Adopt {@code document} as the edited model, disposing whatever was open before. The active
     * layer's pixels/texture are cached for the painting hot-path and the canvas is sized to it.
     */
    public void setDocument(Document document)
    {
        this.lastPixel = null;

        this.deleteTexture();
        this.setEditing(false);

        this.document = document;

        if (document != null && !document.layers.isEmpty())
        {
            this.setActiveLayer(MathUtils.clamp(document.activeLayerIndex < 0 ? 0 : document.activeLayerIndex, 0, document.layers.size() - 1));
            this.setSize(document.width, document.height);
        }
    }

    @Override
    public void setSize(int w, int h)
    {
        super.setSize(w, h);

        if (this.document != null)
        {
            this.document.resize(w, h);

            TextureLayer active = this.document.getActiveLayer();

            if (active != null)
            {
                this.pixels = active.pixels;
                this.temporary = active.texture;
            }
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.editing && context.mouseButton == 1 && this.area.isInside(context)
            && this.getActivePaintTool() == TexturePaintTool.BRUSH)
        {
            /* While the brush is selected, the right mouse button temporarily acts as the
             * eraser: engage the secondary eraser (which switches the active tool) and run
             * the stroke as a regular left-button drag, reverting on release. */
            this.secondaryEraser = true;
            this.secondaryEraserToggle.accept(true);

            this.dragging = true;
            this.mouse = 0;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            this.startDragging(context);

            return true;
        }

        if (this.area.isInside(context) && this.isMouseButtonAllowed(context.mouseButton))
        {
            this.dragging = true;
            this.mouse = context.mouseButton;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            if (this.mouse == 0 && Window.isCtrlPressed() && this.getActivePaintTool() != TexturePaintTool.SELECTION)
            {
                this.mouse = 2;
            }

            this.startDragging(context);

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean isMouseButtonAllowed(int mouseButton)
    {
        return super.isMouseButtonAllowed(mouseButton);
    }

    @Override
    protected void startDragging(UIContext context)
    {
        super.startDragging(context);

        if (!this.editing || this.mouse != 0 || this.pixelsUndo != null)
        {
            return;
        }

        TexturePaintTool tool = this.getActivePaintTool();

        if (tool == TexturePaintTool.SELECTION)
        {
            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);
            boolean subtract = Window.isCtrlPressed();

            if (!Window.isShiftPressed() && !subtract)
            {
                this.selections.clear();
            }

            this.hasSelection = !this.selections.isEmpty() || !subtract;
            this.currentSelection = new SelectionRect(pixel.x, pixel.y, pixel.x, pixel.y);
            this.currentSelectionSubtract = subtract;

            return;
        }

        if (tool == TexturePaintTool.FILL)
        {
            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);

            this.onFillAt(pixel);

            return;
        }

        if (tool == TexturePaintTool.PIPETTE)
        {
            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);
            /* Pick the merged colour across all layers (the colour the user sees),
             * not just the active layer. */
            Color color = this.getMergedColor(pixel.x, pixel.y);

            if (color != null)
            {
                this.pickColorConsumer.accept(color);
            }

            return;
        }

        if (tool == TexturePaintTool.MOVE)
        {
            TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();

            if (layer != null)
            {
                /* Anchor the drag to the layer's current offset and the press position; the offset
                 * is recomputed as startOffset + (currentPixel - startPixel) each frame so slow
                 * sub-pixel drags accumulate correctly without drift. */
                this.moveStartOffsetX = layer.offsetX;
                this.moveStartOffsetY = layer.offsetY;

                /* Snapshot the document so the whole move gesture becomes one undo entry on release. */
                this.moveUndoBefore = this.document.toData();
            }

            return;
        }

        if (this.isStrokePaintTool())
        {
            this.pixelsUndo = new PixelsUndo();
            this.pixelsUndo.layerIndex = this.document == null ? -1 : this.document.activeLayerIndex;
            this.strokeStrengths.clear();
            this.blendStroke = tool == TexturePaintTool.BRUSH;
            this.drawColor = tool == TexturePaintTool.ERASER ? new Color(0, 0, 0, 0) : this.colorSupplier.get();

            Vector2i pixel = this.getHoverPixel(context.mouseX, context.mouseY);

            this.paint(pixel.x, pixel.y);
            this.updateTexture();

            this.wasChanged();
        }
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.dragging && this.getActivePaintTool() == TexturePaintTool.SELECTION && this.currentSelection != null)
        {
            Vector2i hoverPixel = this.getHoverPixel(context.mouseX, context.mouseY);

            this.currentSelection.x2 = hoverPixel.x;
            this.currentSelection.y2 = hoverPixel.y;
            this.applyCurrentSelection();
        }

        if (this.dragging && this.pixelsUndo != null)
        {
            Vector2i hoverPixel = this.getHoverPixel(context.mouseX, context.mouseY);

            if (Window.isShiftPressed() && this.lastPixel != null)
            {
                LineRasterizer rasterizer = new LineRasterizer(
                    new Vector2d(this.lastPixel.x, this.lastPixel.y),
                    new Vector2d(hoverPixel.x, hoverPixel.y)
                );
                Set<Vector2i> pixels = new HashSet<>();

                rasterizer.setupRange(0F, 1F, 1F / (float) this.lastPixel.distance(hoverPixel));
                rasterizer.solve(pixels);

                for (Vector2i pixel : pixels)
                {
                    this.paint(pixel.x, pixel.y);
                }

                this.updateTexture();
            }

            this.undoManager.pushUndo(this.pixelsUndo);

            this.pixelsUndo = null;
            this.strokeStrengths.clear();
            this.lastPixel = hoverPixel;
        }

        /* Restore the brush once the right-mouse-button eraser stroke is finalized (the undo
         * entry has already been pushed above, so reverting the tool does not affect it). */
        if (this.secondaryEraser)
        {
            this.secondaryEraser = false;
            this.secondaryEraserToggle.accept(false);
        }

        /* Commit the move gesture as a single undo entry if the layer offset actually changed. */
        if (this.moveUndoBefore != null)
        {
            TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();
            boolean moved = layer != null && (layer.offsetX != this.moveStartOffsetX || layer.offsetY != this.moveStartOffsetY);

            if (moved && this.undoManager != null)
            {
                this.undoManager.pushUndo(new LayerStateUndo(this.moveUndoBefore, this.document.toData()));
            }

            this.moveUndoBefore = null;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {}

    @Override
    protected void renderCanvasFrame(UIContext context)
    {
        int x = -this.w / 2;
        int y = -this.h / 2;
        Area area = this.calculate(x, y, x + this.w, y + this.h);

        if (!this.editing)
        {
            Texture texture = this.getRenderTexture(context);
            context.batcher.fullTexturedBox(texture, area.x, area.y, area.w, area.h);
        }
        else if (this.document != null)
        {
            for (TextureLayer layer : this.document.layers)
            {
                if (layer.visible && layer.texture != null)
                {
                    /* Shift the layer quad by its pixel offset (move tool); the canvas clip keeps it
                     * within the editor, and flattening crops it to the document bounds. */
                    Area layerArea = this.calculate(x + layer.offsetX, y + layer.offsetY, x + layer.offsetX + this.w, y + layer.offsetY + this.h);
                    int color = Colors.setA(Colors.WHITE, layer.opacity);
                    context.batcher.texturedBox(layer.texture, color, layerArea.x, layerArea.y, layerArea.w, layerArea.h, 0, 0, layer.texture.width, layer.texture.height, layer.texture.width, layer.texture.height);
                }
            }
        }

        /* Draw brush preview for stroke tools */
        if (this.isStrokePaintTool())
        {
            int pixelX = (int) Math.floor(this.scaleX.from(context.mouseX));
            int pixelY = (int) Math.floor(this.scaleY.from(context.mouseY));
            this.renderStrokePreview(context, pixelX, pixelY);
        }

        if (this.hasSelection || this.currentSelection != null)
        {
            context.batcher.clip(this.area, context);
            this.renderSelection(context);
            context.batcher.unclip(context);
        }

        if (this.editing && this.dragging && (this.lastX != context.mouseX || this.lastY != context.mouseY) && this.mouse == 0)
        {
            if (this.getActivePaintTool() == TexturePaintTool.SELECTION)
            {
                Vector2i current = this.getHoverPixel(context.mouseX, context.mouseY);

                if (this.currentSelection != null)
                {
                    this.currentSelection.x2 = current.x;
                    this.currentSelection.y2 = current.y;
                }

                this.lastX = context.mouseX;
                this.lastY = context.mouseY;
            }
            else if (this.getActivePaintTool() == TexturePaintTool.MOVE)
            {
                TextureLayer layer = this.document == null ? null : this.document.getActiveLayer();

                if (layer != null)
                {
                    /* lastX/lastY stay pinned to the drag origin so the offset tracks the total drag. */
                    Vector2i start = this.getHoverPixel(this.lastX, this.lastY);
                    Vector2i current = this.getHoverPixel(context.mouseX, context.mouseY);
                    int newX = this.moveStartOffsetX + (current.x - start.x);
                    int newY = this.moveStartOffsetY + (current.y - start.y);

                    if (newX != layer.offsetX || newY != layer.offsetY)
                    {
                        layer.offsetX = newX;
                        layer.offsetY = newY;
                        this.wasChanged();
                    }
                }
            }
            /* Only an actually started stroke can be continued: the undo record is created in
             * startDragging() and holds the pre-stroke pixels that blending, the eraser and alpha
             * lock read back. Switching to a stroke tool (or enabling the editor) while the button
             * is already held leaves the drag without one &mdash; such a drag paints nothing. */
            else if (this.isStrokePaintTool() && this.pixelsUndo != null)
            {
                Vector2i last = this.getHoverPixel(this.lastX, this.lastY);
                Vector2i current = this.getHoverPixel(context.mouseX, context.mouseY);

                double distance = Math.max(new Vector2d(current.x, current.y).distance(last.x, last.y), 1);

                for (int i = 0; i <= distance; i++)
                {
                    int xx = (int) Lerps.lerp(last.x, current.x, i / distance);
                    int yy = (int) Lerps.lerp(last.y, current.y, i / distance);

                    this.paint(xx, yy);
                }

                this.wasChanged();
                this.updateTexture();

                this.lastX = context.mouseX;
                this.lastY = context.mouseY;
            }
        }
    }

    public Pixels flattenLayers()
    {
        return this.document == null ? null : this.document.flatten();
    }

    private Texture temporaryFlat;

    /** GPU mask of the current selection (white = selected); the outline shader reads it. Rebuilt only
     * when the selection changes (or every frame during an active drag), not on every render. */
    private Texture selectionMaskTexture;
    private Pixels selectionMaskPixels;
    private boolean selectionMaskDirty = true;

    protected Texture getRenderTexture(UIContext context)
    {
        if (this.document == null || this.document.layers.isEmpty() || this.editing)
        {
            return this.temporary;
        }

        return this.getTemporaryTexture();
    }

    @Override
    protected void renderCheckboard(UIContext context, Area area)
    {
        int brightness = (int) (this.backgroundSupplier.get() * 255);
        int color = Colors.setA(brightness << 16 | brightness << 8 | brightness, 1F);

        context.batcher.iconArea(Icons.CHECKBOARD, color, area.x, area.y, area.w, area.h);
    }

    @Override
    protected void renderForeground(UIContext context)
    {
        super.renderForeground(context);
    }
}
