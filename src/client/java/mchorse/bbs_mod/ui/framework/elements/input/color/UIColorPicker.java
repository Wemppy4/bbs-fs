package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Matrix3x2fc;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/**
 * Color picker element
 *
 * This is the one that is responsible for picking colors
 */
public class UIColorPicker extends UIElement
{
    private static final int DRAG_HSV_PICKER = 1;
    private static final int DRAG_HUE = 2;
    private static final int DRAG_HSV_ALPHA = 3;
    private static final int DRAG_RGB_RED = 1;
    private static final int DRAG_RGB_GREEN = 2;
    private static final int DRAG_RGB_BLUE = 3;
    private static final int DRAG_RGB_ALPHA = 4;

    private static final int POPUP_PADDING = 5;
    private static final int INPUT_HEIGHT = 20;
    private static final int PREVIEW_SIZE = 20;
    private static final int HEADER_HEIGHT = 30;
    private static final int DEFAULT_WIDTH = 200;
    private static final int RGB_SLIDER_HEIGHT = 50;
    private static final int RGB_SECTION_GAP = 15;
    private static final int HSV_PICKER_SIZE = 132;
    private static final int HSV_SLIDER_WIDTH = 12;
    private static final int HSV_SLIDER_GAP = 6;
    private static final int HSV_SECTION_GAP = 15;
    private static final int PALETTE_GAP = 15;
    private static final int WINDOW_BOTTOM_GAP = 4;

    private static final ValueColors RECENT_COLORS_FALLBACK = new ValueColors("recent");

    public Color color = new Color();
    public Consumer<Integer> callback;

    public UITextbox input;
    public UIColorPalette recent;
    public UIColorPalette favorite;

    public boolean editAlpha;

    public Area picker = new Area();
    public Area hue = new Area();
    public Area red = new Area();
    public Area green = new Area();
    public Area blue = new Area();
    public Area alpha = new Area();
    public Area preview = new Area();

    private int dragging = -1;
    private final Color hsv = new Color();
    private final Color tempColor = new Color();
    private final Color tempColor2 = new Color();

    /**
     * The swatch: the colour at full opacity in one diagonal half and at its real alpha in the other, over
     * the checkerboard, so the alpha is readable at a glance.
     *
     * <p>1.21.11: submitted as a recorded {@link GuiQuadMesh}, not an immediate {@code RenderLayer.draw}.
     * The 1.21.6+ GUI is two-phase — vanilla records draws into a {@code GuiRenderState} and composites them
     * afterwards — so an immediate mid-frame draw is overpainted by that composite and never appears. The
     * deferred path also fixes QUADS-only geometry, which is what {@code GuiRenderer} can composite.
     * Each of the two triangles rides as a quad with its last corner doubled, keeping the exact geometry.</p>
     */
    public static void renderAlphaPreviewQuad(Batcher2D batcher, int x1, int y1, int x2, int y2, Color color)
    {
        Matrix3x2fc matrix4f = batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        builder.vertex(matrix4f, x1, y1).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x1, y2).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x2, y1).color(color.r, color.g, color.b, 1);
        builder.vertex(matrix4f, x2, y1).color(color.r, color.g, color.b, 1);

        builder.vertex(matrix4f, x2, y1).color(color.r, color.g, color.b, color.a);
        builder.vertex(matrix4f, x1, y2).color(color.r, color.g, color.b, color.a);
        builder.vertex(matrix4f, x2, y2).color(color.r, color.g, color.b, color.a);
        builder.vertex(matrix4f, x2, y2).color(color.r, color.g, color.b, color.a);

        if (!builder.isEmpty())
        {
            batcher.drawQuadMesh(builder);
        }
    }

    public UIColorPicker(Consumer<Integer> callback)
    {
        super();

        this.callback = callback;

        this.input = new UITextbox(7, this::applyColorFromHexInput)
        {
            @Override
            public void unfocus(UIContext context)
            {
                super.unfocus(context);

                UIColorPicker.this.syncHexInputAfterEdit();
            }
        };
        this.input.context((menu) -> menu.action(Icons.FAVORITE, UIKeys.COLOR_CONTEXT_FAVORITES_ADD, () -> this.addToFavorites(this.color)));

        this.recent = new UIColorPalette((color) ->
        {
            this.setColor(color.getARGBColor());
            this.notifyColorChanged();
        }).colors(this.getRecentColors().getCurrentColors());

        this.recent.context((menu) ->
        {
            int index = this.recent.getIndex(this.getContext());

            if (this.recent.hasColor(index))
            {
                menu.action(Icons.FAVORITE, UIKeys.COLOR_CONTEXT_FAVORITES_ADD, () -> this.addToFavorites(this.recent.colors.get(index)));
            }
        });

        this.favorite = new UIColorPalette((color) ->
        {
            this.setColor(color.getARGBColor());
            this.notifyColorChanged();
        }).colors(BBSSettings.favoriteColors.getCurrentColors());

        this.favorite.context((menu) ->
        {
            int index = this.favorite.getIndex(this.getContext());

            if (this.favorite.hasColor(index))
            {
                menu.action(Icons.REMOVE, UIKeys.COLOR_CONTEXT_FAVORITES_REMOVE, () -> this.removeFromFavorites(index));
            }
        });

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).add(this.input, this.favorite, this.recent);
    }

    public UIColorPicker editAlpha()
    {
        this.editAlpha = true;
        this.input.textbox.setLength(9);

        return this;
    }

    public void updateField()
    {
        if (this.input.isFocused())
        {
            return;
        }

        this.syncHexInputAfterEdit();
    }

    private void syncHexInputAfterEdit()
    {
        this.input.setText(this.color.stringify(this.editAlpha));
    }

    private void applyColorFromHexInput(String string)
    {
        if (!this.isCompleteHexColorInput(string))
        {
            return;
        }

        this.setValue(Colors.parse(string));
        this.notifyColorChanged();
    }

    private boolean isCompleteHexColorInput(String raw)
    {
        if (raw == null)
        {
            return false;
        }

        String t = raw.trim();

        if (t.startsWith("#"))
        {
            t = t.substring(1);
        }

        return t.length() == 6 || t.length() == 8;
    }

    protected void callback()
    {
        if (this.callback != null)
        {
            this.callback.accept(this.editAlpha ? this.color.getARGBColor() : this.color.getRGBColor());
        }
    }

    public void setColor(int color)
    {
        this.setValue(color);
        this.updateField();
    }

    public void setValue(int color)
    {
        this.color.set(color, this.editAlpha);
        this.syncHsvFromColor();
    }

    public void setup(int x, int y)
    {
        this.xy(x, y);
    }

    private void notifyColorChanged()
    {
        this.updateField();
        this.callback();
    }

    private void syncHsvFromColor()
    {
        Colors.RGBtoHSV(this.hsv, this.color.r, this.color.g, this.color.b);
        this.hsv.a = this.color.a;
    }

    private void syncColorFromHsv()
    {
        Colors.HSVtoRGB(this.color, this.hsv.r, this.hsv.g, this.hsv.b);
        this.color.a = this.hsv.a;
    }

    private ValueColors getRecentColors()
    {
        return BBSSettings.recentColors == null ? RECENT_COLORS_FALLBACK : BBSSettings.recentColors;
    }

    /* Managing recent and favorite colors */

    private void addToRecent()
    {
        this.getRecentColors().addColor(this.color);
    }

    private void addToFavorites(Color color)
    {
        BBSSettings.favoriteColors.addColor(color);
        this.resize();
    }

    private void removeFromFavorites(int index)
    {
        BBSSettings.favoriteColors.remove(index);
        this.resize();
    }

    private void closePicker()
    {
        this.removeFromParent();
        this.addToRecent();
    }

    /* GuiElement overrides */

    @Override
    public void resize()
    {
        PickerLayout layout = this.createLayout();

        this.w(layout.width);
        this.h(layout.height);

        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        this.afterResizeApplied();
        this.applyLayout(layout);

        this.input.resize();
        this.favorite.resize();
        this.recent.resize();

        if (this.resizer != null)
        {
            this.resizer.postApply(this.area);
        }
    }

    private PickerLayout createLayout()
    {
        PickerLayout layout = new PickerLayout();

        layout.hsv = this.isHsvPicker();
        layout.width = layout.hsv ? this.getHsvWidth() : DEFAULT_WIDTH;
        layout.paletteWidth = layout.width - POPUP_PADDING * 2;
        layout.favoriteHeight = this.favorite.colors.isEmpty() ? 0 : this.favorite.getHeight(layout.paletteWidth);
        layout.recentHeight = this.recent.colors.isEmpty() ? 0 : this.recent.getHeight(layout.paletteWidth);
        layout.contentY = HEADER_HEIGHT;
        layout.paletteY = layout.contentY + (layout.hsv ? HSV_PICKER_SIZE + HSV_SECTION_GAP : RGB_SLIDER_HEIGHT + RGB_SECTION_GAP);
        layout.height = layout.paletteY;

        if (layout.favoriteHeight > 0)
        {
            layout.height += layout.favoriteHeight;
        }

        if (layout.favoriteHeight > 0 && layout.recentHeight > 0)
        {
            layout.height += PALETTE_GAP;
        }

        if (layout.recentHeight > 0)
        {
            layout.height += layout.recentHeight + WINDOW_BOTTOM_GAP;
        }
        else if (layout.favoriteHeight > 0)
        {
            layout.height += PALETTE_GAP;
        }

        return layout;
    }

    private void applyLayout(PickerLayout layout)
    {
        int contentX = this.area.x + POPUP_PADDING;
        int contentY = this.area.y + layout.contentY;
        int previewX = this.area.ex() - POPUP_PADDING - PREVIEW_SIZE;

        this.preview.set(previewX, this.area.y + POPUP_PADDING, PREVIEW_SIZE, PREVIEW_SIZE);
        this.input.set(contentX, this.area.y + POPUP_PADDING, layout.paletteWidth - PREVIEW_SIZE - POPUP_PADDING, INPUT_HEIGHT);

        if (layout.hsv)
        {
            this.layoutHsv(contentX, contentY);
        }
        else
        {
            this.layoutRgb(contentX, contentY, layout.paletteWidth);
        }

        this.favorite.set(contentX, this.area.y + layout.paletteY, layout.paletteWidth, layout.favoriteHeight);

        if (layout.favoriteHeight > 0 && layout.recentHeight > 0)
        {
            this.recent.set(contentX, this.favorite.area.ey() + PALETTE_GAP, layout.paletteWidth, layout.recentHeight);
        }
        else
        {
            this.recent.set(contentX, this.area.y + layout.paletteY, layout.paletteWidth, layout.recentHeight);
        }
    }

    private void layoutHsv(int x, int y)
    {
        this.picker.set(x, y, HSV_PICKER_SIZE, HSV_PICKER_SIZE);
        this.hue.set(this.picker.ex() + HSV_SLIDER_GAP, y, HSV_SLIDER_WIDTH, HSV_PICKER_SIZE);

        if (this.editAlpha)
        {
            this.alpha.set(this.hue.ex() + HSV_SLIDER_GAP, y, HSV_SLIDER_WIDTH, HSV_PICKER_SIZE);
        }
        else
        {
            this.alpha.set(0, 0, 0, 0);
        }

        this.red.set(0, 0, 0, 0);
        this.green.set(0, 0, 0, 0);
        this.blue.set(0, 0, 0, 0);
    }

    private void layoutRgb(int x, int y, int width)
    {
        int components = this.editAlpha ? 4 : 3;
        int sliderHeight = RGB_SLIDER_HEIGHT / components;
        int remainder = RGB_SLIDER_HEIGHT - sliderHeight * components;

        this.red.set(x, y, width, sliderHeight);

        if (this.editAlpha)
        {
            this.green.set(x, y + sliderHeight, width, sliderHeight);
            this.blue.set(x, y + sliderHeight * 2, width, sliderHeight + remainder);
            this.alpha.set(x, y + RGB_SLIDER_HEIGHT - sliderHeight, width, sliderHeight);
        }
        else
        {
            this.green.set(x, y + sliderHeight, width, sliderHeight + remainder);
            this.blue.set(x, y + RGB_SLIDER_HEIGHT - sliderHeight, width, sliderHeight);
            this.alpha.set(0, 0, 0, 0);
        }

        this.picker.set(0, 0, 0, 0);
        this.hue.set(0, 0, 0, 0);
    }

    private int getHsvWidth()
    {
        int width = POPUP_PADDING * 2 + HSV_PICKER_SIZE + HSV_SLIDER_GAP + HSV_SLIDER_WIDTH;

        if (this.editAlpha)
        {
            width += HSV_SLIDER_GAP + HSV_SLIDER_WIDTH;
        }

        return width;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.beginDragging(context))
        {
            return true;
        }

        if (!this.area.isInside(context))
        {
            this.closePicker();
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.dragging = -1;

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.closePicker();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.handleDragging(context);

        this.area.render(context.batcher, Colors.LIGHTEST_GRAY);
        this.renderRect(context.batcher, this.preview.x, this.preview.y, this.preview.ex(), this.preview.ey());
        context.batcher.outline(this.preview.x, this.preview.y, this.preview.ex(), this.preview.ey(), Colors.A25);

        if (this.isHsvPicker())
        {
            this.renderHsv(context);
        }
        else
        {
            this.renderRgb(context);
        }

        this.renderPaletteLabels(context);

        super.render(context);
    }

    private boolean beginDragging(UIContext context)
    {
        if (this.isHsvPicker())
        {
            if (this.picker.isInside(context))
            {
                this.dragging = DRAG_HSV_PICKER;

                return true;
            }

            if (this.hue.isInside(context))
            {
                this.dragging = DRAG_HUE;

                return true;
            }

            if (this.editAlpha && this.alpha.isInside(context))
            {
                this.dragging = DRAG_HSV_ALPHA;

                return true;
            }

            return false;
        }

        if (this.red.isInside(context))
        {
            this.dragging = DRAG_RGB_RED;

            return true;
        }

        if (this.green.isInside(context))
        {
            this.dragging = DRAG_RGB_GREEN;

            return true;
        }

        if (this.blue.isInside(context))
        {
            this.dragging = DRAG_RGB_BLUE;

            return true;
        }

        if (this.editAlpha && this.alpha.isInside(context))
        {
            this.dragging = DRAG_RGB_ALPHA;

            return true;
        }

        return false;
    }

    private void handleDragging(UIContext context)
    {
        if (this.dragging < 0)
        {
            return;
        }

        if (this.isHsvPicker())
        {
            this.handleHsvDragging(context);
        }
        else
        {
            this.handleRgbDragging(context);
        }
    }

    private void handleHsvDragging(UIContext context)
    {
        if (this.dragging == DRAG_HSV_PICKER)
        {
            this.hsv.g = MathUtils.clamp((context.mouseX - this.picker.x) / (float) this.picker.w, 0F, 1F);
            this.hsv.b = 1F - MathUtils.clamp((context.mouseY - this.picker.y) / (float) this.picker.h, 0F, 1F);
        }
        else if (this.dragging == DRAG_HUE)
        {
            this.hsv.r = MathUtils.clamp((context.mouseY - this.hue.y) / (float) this.hue.h, 0F, 1F);
        }
        else if (this.dragging == DRAG_HSV_ALPHA && this.editAlpha)
        {
            this.hsv.a = 1F - MathUtils.clamp((context.mouseY - this.alpha.y) / (float) this.alpha.h, 0F, 1F);
        }

        this.syncColorFromHsv();
        this.notifyColorChanged();
    }

    private void handleRgbDragging(UIContext context)
    {
        float factor = (context.mouseX - (this.red.x + 7)) / (float) (this.red.w - 14);

        this.color.set(MathUtils.clamp(factor, 0, 1), this.dragging);
        this.syncHsvFromColor();
        this.notifyColorChanged();
    }

    private boolean isHsvPicker()
    {
        return BBSSettings.hsvColorPicker.get();
    }

    private void renderHsv(UIContext context)
    {
        this.renderSliderBackdrop(context.batcher, this.picker, this.editAlpha ? this.alpha.ex() : this.hue.ex());
        this.renderHsvSquare(context.batcher);
        this.renderHueSlider(context.batcher);

        if (this.editAlpha)
        {
            this.renderAlphaSlider(context.batcher);
        }

        context.batcher.outline(this.picker.x, this.picker.y, this.picker.ex(), this.picker.ey(), Colors.A25);
        context.batcher.outline(this.hue.x, this.hue.y, this.hue.ex(), this.hue.ey(), Colors.A25);

        if (this.editAlpha)
        {
            context.batcher.outline(this.alpha.x, this.alpha.y, this.alpha.ex(), this.alpha.ey(), Colors.A25);
        }

        this.renderSquareMarker(context.batcher, this.picker.x + (int) ((this.picker.w - 1) * this.hsv.g), this.picker.y + (int) ((this.picker.h - 1) * (1F - this.hsv.b)));
        this.renderMarker(context.batcher, this.hue.mx(), this.hue.y + (int) ((this.hue.h - 1) * this.hsv.r));

        if (this.editAlpha)
        {
            this.renderMarker(context.batcher, this.alpha.mx(), this.alpha.y + (int) ((this.alpha.h - 1) * (1F - this.hsv.a)));
        }
    }

    private void renderRgb(UIContext context)
    {
        if (this.editAlpha)
        {
            context.batcher.iconArea(Icons.CHECKBOARD, this.alpha.x, this.red.y, this.alpha.w, this.alpha.ey() - this.red.y);
        }

        this.renderRgbSlider(context.batcher, this.red, this.tempColor.copy(this.color).set(0F, DRAG_RGB_RED).getARGBColor(), this.tempColor2.copy(this.color).set(1F, DRAG_RGB_RED).getARGBColor());
        this.renderRgbSlider(context.batcher, this.green, this.tempColor.copy(this.color).set(0F, DRAG_RGB_GREEN).getARGBColor(), this.tempColor2.copy(this.color).set(1F, DRAG_RGB_GREEN).getARGBColor());
        this.renderRgbSlider(context.batcher, this.blue, this.tempColor.copy(this.color).set(0F, DRAG_RGB_BLUE).getARGBColor(), this.tempColor2.copy(this.color).set(1F, DRAG_RGB_BLUE).getARGBColor());

        if (this.editAlpha)
        {
            this.renderRgbSlider(context.batcher, this.alpha, this.tempColor.copy(this.color).set(0F, DRAG_RGB_ALPHA).getARGBColor(), this.tempColor2.copy(this.color).set(1F, DRAG_RGB_ALPHA).getARGBColor());
        }

        context.batcher.outline(this.red.x, this.red.y, this.red.ex(), this.editAlpha ? this.alpha.ey() : this.blue.ey(), Colors.A25);

        this.renderMarker(context.batcher, this.red.x + 7 + (int) ((this.red.w - 14) * this.color.r), this.red.my());
        this.renderMarker(context.batcher, this.green.x + 7 + (int) ((this.green.w - 14) * this.color.g), this.green.my());
        this.renderMarker(context.batcher, this.blue.x + 7 + (int) ((this.blue.w - 14) * this.color.b), this.blue.my());

        if (this.editAlpha)
        {
            this.renderMarker(context.batcher, this.alpha.x + 7 + (int) ((this.alpha.w - 14) * this.color.a), this.alpha.my());
        }
    }

    private void renderPaletteLabels(UIContext context)
    {
        if (!this.favorite.colors.isEmpty())
        {
            context.batcher.text(UIKeys.COLOR_FAVORITE.get(), this.favorite.area.x, this.favorite.area.y - 10, Colors.GRAY);
        }

        if (!this.recent.colors.isEmpty())
        {
            context.batcher.text(UIKeys.COLOR_RECENT.get(), this.recent.area.x, this.recent.area.y - 10, Colors.GRAY);
        }
    }

    private void renderHsvSquare(Batcher2D batcher)
    {
        int hueColor = Colors.HSVtoRGB(this.tempColor, this.hsv.r, 1F, 1F).getARGBColor();

        batcher.gradientHBox(this.picker.x, this.picker.y, this.picker.ex(), this.picker.ey(), Colors.WHITE, hueColor);
        batcher.gradientVBox(this.picker.x, this.picker.y, this.picker.ex(), this.picker.ey(), 0x00000000, Colors.A100);
    }

    private void renderHueSlider(Batcher2D batcher)
    {
        for (int i = 0; i < 6; i++)
        {
            float a = i / 6F;
            float b = (i + 1) / 6F;
            int top = Colors.HSVtoRGB(this.tempColor, a, 1F, 1F).getARGBColor();
            int bottom = Colors.HSVtoRGB(this.tempColor2, b, 1F, 1F).getARGBColor();

            batcher.gradientVBox(this.hue.x, this.hue.y + this.hue.h * a, this.hue.ex(), this.hue.y + this.hue.h * b, top, bottom);
        }
    }

    private void renderAlphaSlider(Batcher2D batcher)
    {
        int opaque = Colors.HSVtoRGB(this.tempColor, this.hsv.r, this.hsv.g, this.hsv.b).getARGBColor();

        this.tempColor2.copy(this.tempColor).a = 0F;

        batcher.iconArea(Icons.CHECKBOARD, this.alpha.x, this.alpha.y, this.alpha.w, this.alpha.h);
        batcher.gradientVBox(this.alpha.x, this.alpha.y, this.alpha.ex(), this.alpha.ey(), opaque, this.tempColor2.getARGBColor());
    }

    private void renderRgbSlider(Batcher2D batcher, Area area, int left, int right)
    {
        batcher.gradientHBox(area.x, area.y, area.ex(), area.ey(), left, right);
    }

    private void renderSliderBackdrop(Batcher2D batcher, Area picker, int right)
    {
        batcher.box(picker.x - 1, picker.y - 1, right + 1, picker.ey() + 1, Colors.A6);
    }

    public void renderRect(Batcher2D batcher, int x1, int y1, int x2, int y2)
    {
        if (this.editAlpha)
        {
            batcher.iconArea(Icons.CHECKBOARD, x1, y1, x2 - x1, y2 - y1);
            renderAlphaPreviewQuad(batcher, x1, y1, x2, y2, this.color);
        }
        else
        {
            batcher.box(x1, y1, x2, y2, this.color.getARGBColor());
        }
    }

    private void renderMarker(Batcher2D batcher, int x, int y)
    {
        batcher.box(x - 4, y - 4, x + 4, y + 4, Colors.A100);
        batcher.box(x - 3, y - 3, x + 3, y + 3, Colors.WHITE);
        batcher.box(x - 2, y - 2, x + 2, y + 2, Colors.LIGHTEST_GRAY);
    }

    private void renderSquareMarker(Batcher2D batcher, int x, int y)
    {
        batcher.outlineCenter(x, y, 4, Colors.A100);
        batcher.outlineCenter(x, y, 3, Colors.WHITE);
    }

    private static class PickerLayout
    {
        public boolean hsv;
        public int width;
        public int height;
        public int paletteWidth;
        public int contentY;
        public int paletteY;
        public int recentHeight;
        public int favoriteHeight;
    }
}
