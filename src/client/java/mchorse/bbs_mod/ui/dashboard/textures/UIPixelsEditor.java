package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
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
import org.joml.Vector2d;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11;

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

            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }
    }

    protected UndoManager<Pixels> undoManager;
    private PixelsUndo pixelsUndo;

    private Supplier<Float> backgroundSupplier = () -> 0.7F;
    private Supplier<Color> colorSupplier = Color::white;
    private Consumer<Color> pickColorConsumer = (c) -> {};

    private Supplier<TexturePaintTool> toolSupplier = () -> TexturePaintTool.BRUSH;
    private Supplier<TextureStrokeShape> strokeShapeSupplier = () -> TextureStrokeShape.SQUARE;
    private Supplier<Boolean> strokeBuildUpSupplier = () -> false;
    private Supplier<Boolean> alphaLockSupplier = () -> false;
    private Supplier<Float> brushSoftnessSupplier = () -> 0.0F;
    private Supplier<Float> eraserOpacitySupplier = () -> 1.0F;

    public UIPixelsEditor()
    {
        super();

        this.toolbar = new UIElement();
        this.toolbar.relative(this).w(1F).h(30).row(0).resize().padding(5);

        this.add(this.toolbar);

        IKey category = UIKeys.TEXTURES_KEYS_CATEGORY;
        Supplier<Boolean> texture = () -> this.pixels != null;
        Supplier<Boolean> editing = () -> this.editing;

        this.keys().register(Keys.COPY, this::copyPixel).label(UIKeys.TEXTURES_VIEWER_CONTEXT_COPY_HEX).inside().active(texture).category(category);
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
        }
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

    public UIPixelsEditor pickColorConsumer(Consumer<Color> consumer)
    {
        this.pickColorConsumer = consumer != null ? consumer : (c) -> {};

        return this;
    }

    public Texture getTemporaryTexture()
    {
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
        if (x < 0 || y < 0 || x >= this.pixels.width || y >= this.pixels.height)
        {
            return;
        }

        if (!this.isInsideSelection(x, y))
        {
            return;
        }

        if (strength <= 0F)
        {
            return;
        }

        if (!this.isStrokeBuildUpEnabled())
        {
            int index = this.pixels.toIndex(x, y);
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
                    destination = this.pixels.getColor(x, y);
                }
                else
                {
                    destination = this.pixelsUndo.getOriginalColor(this.pixels, x, y);

                    if (destination == null)
                    {
                        destination = this.pixels.getColor(x, y);
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
                destination = this.pixels.getColor(x, y);
            }
            else
            {
                destination = this.pixelsUndo.getOriginalColor(this.pixels, x, y);

                if (destination == null)
                {
                    destination = this.pixels.getColor(x, y);
                }
            }

            color = this.blendColorOver(destination, source);
        }

        if (this.isAlphaLockEnabled())
        {
            Color destination;

            if (this.isStrokeBuildUpEnabled())
            {
                destination = this.pixels.getColor(x, y);
            }
            else
            {
                destination = this.pixelsUndo.getOriginalColor(this.pixels, x, y);

                if (destination == null)
                {
                    destination = this.pixels.getColor(x, y);
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

        this.pixelsUndo.setColor(this.pixels, x, y, color);
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
    }

    private int getSelectionScreenX(int x)
    {
        return (int) Math.round(this.scaleX.to(x - this.w / 2D));
    }

    private int getSelectionScreenY(int y)
    {
        return (int) Math.round(this.scaleY.to(y - this.h / 2D));
    }

    private int getSelectionPatternColor(int x, int y)
    {
        int phase = (int) (System.currentTimeMillis() / 150L);

        return ((x + y + phase) & 1) == 0 ? Colors.WHITE : 0xff000000;
    }

    private void renderSelectionHorizontalEdge(UIContext context, int x, int y)
    {
        int sx1 = this.getSelectionScreenX(x);
        int sx2 = this.getSelectionScreenX(x + 1);
        int sy = this.getSelectionScreenY(y);

        context.batcher.box(sx1, sy, sx2, sy + 1, this.getSelectionPatternColor(x, y));
    }

    private void renderSelectionVerticalEdge(UIContext context, int x, int y)
    {
        int sx = this.getSelectionScreenX(x);
        int sy1 = this.getSelectionScreenY(y);
        int sy2 = this.getSelectionScreenY(y + 1);

        context.batcher.box(sx, sy1, sx + 1, sy2, this.getSelectionPatternColor(x, y));
    }

    private void renderSelectionOutline(UIContext context, boolean[][] mask)
    {
        for (int y = 0; y <= this.h; y++)
        {
            for (int x = 0; x < this.w; x++)
            {
                boolean above = y > 0 && mask[x][y - 1];
                boolean below = y < this.h && mask[x][y];
                boolean boundary = above != below;

                if (boundary)
                {
                    this.renderSelectionHorizontalEdge(context, x, y);
                }
            }
        }

        for (int x = 0; x <= this.w; x++)
        {
            for (int y = 0; y < this.h; y++)
            {
                boolean left = x > 0 && mask[x - 1][y];
                boolean right = x < this.w && mask[x][y];
                boolean boundary = left != right;

                if (boundary)
                {
                    this.renderSelectionVerticalEdge(context, x, y);
                }
            }
        }
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

    private void handleUndo(IUndo<Pixels> pixelsIUndo, boolean redo)
    {
        this.updateTexture();
    }

    private void copyPixel()
    {
        UIContext context = this.getContext();
        int pixelX = (int) Math.floor(this.scaleX.from(context.mouseX)) + this.w / 2;
        int pixelY = (int) Math.floor(this.scaleY.from(context.mouseY)) + this.h / 2;
        Color color = this.pixels.getColor(pixelX, pixelY);

        if (color != null)
        {
            Window.setClipboard(color.stringify());

            UIUtils.playClick();
        }
    }

    protected void updateTexture()
    {
        this.pixels.rewindBuffer();
        this.temporary.bind();
        this.temporary.updateTexture(this.pixels);
    }

    public void undo()
    {
        if (this.undoManager != null && this.undoManager.undo(this.pixels))
        {
            UIUtils.playClick();
        }
    }

    public void redo()
    {
        if (this.undoManager != null && this.undoManager.redo(this.pixels))
        {
            UIUtils.playClick();
        }
    }

    public void deleteTexture()
    {
        if (this.temporary != null)
        {
            this.temporary.delete();
            this.temporary = null;
        }
    }

    public void fillPixels(Pixels pixels)
    {
        this.lastPixel = null;

        this.deleteTexture();
        this.setEditing(false);

        this.pixels = pixels;

        if (pixels != null)
        {
            this.temporary = new Texture();
            this.temporary.setFilter(GL11.GL_NEAREST);

            this.updateTexture();
            this.setSize(pixels.width, pixels.height);
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
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
            Color color = this.pixels.getColor(pixel.x, pixel.y);

            if (color != null)
            {
                this.pickColorConsumer.accept(color);
            }

            return;
        }

        if (this.isStrokePaintTool())
        {
            this.pixelsUndo = new PixelsUndo();
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
        Texture texture = this.getRenderTexture(context);

        context.batcher.fullTexturedBox(texture, area.x, area.y, area.w, area.h);

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
            boolean[][] mask = this.buildSelectionMask();

            this.fillSelectionMask(mask, this.currentSelection, !this.currentSelectionSubtract);
            this.renderSelectionOutline(context, mask);

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
            else if (this.isStrokePaintTool())
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

    protected Texture getRenderTexture(UIContext context)
    {
        return this.temporary;
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
