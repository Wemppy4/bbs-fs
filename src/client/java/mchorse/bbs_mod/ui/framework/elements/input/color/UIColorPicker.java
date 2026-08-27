package mchorse.bbs_mod.ui.framework.elements.input.color;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UITabStrip;
import mchorse.bbs_mod.ui.framework.elements.utils.UITextTab;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
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

    /**
     * The preview shows both colors side by side: what the picker opened with on the left,
     * what it holds now on the right — so a nudge can be judged against where it started,
     * and pressing the left half goes back there.
     */
    private static final int PREVIEW_WIDTH = PREVIEW_SIZE * 2 + 4;

    private static final int HEADER_HEIGHT = 30;

    /**
     * The popup is the same width in both color models, so switching between them moves
     * nothing but the surface in the middle.
     */
    private static final int POPUP_WIDTH = 180;

    /** Space between the parts of the header row, which is tighter than the popup's own padding. */
    private static final int HEADER_GAP = 3;

    private static final int TABS_HEIGHT = 16;
    private static final int TABS_GAP = 6;
    private static final int FIELDS_HEIGHT = 16;
    private static final int FIELDS_GAP = 6;
    private static final int RGB_SLIDER_HEIGHT = 50;
    private static final int RGB_SECTION_GAP = 15;
    private static final int HSV_SLIDER_WIDTH = 12;
    private static final int HSV_SLIDER_GAP = 6;
    private static final int HSV_SECTION_GAP = 15;
    private static final int PALETTE_GAP = 15;
    private static final int WINDOW_BOTTOM_GAP = 4;

    /** How far the ends of an RGB slider are kept clear so the marker never hangs off it. */
    private static final int SLIDER_INSET = 7;

    private static final int EYEDROPPER_SIZE = 20;

    /** The patch of the sampled color shown beside the cursor while the eyedropper is armed. */
    private static final int SAMPLE_SIZE = 16;

    private static final ValueColors RECENT_COLORS_FALLBACK = new ValueColors("recent");

    public Color color = new Color();
    public Consumer<Integer> callback;

    public UITextbox input;
    public UIIcon eyedropper;
    public UITabStrip tabs;
    public UIColorFields fields;
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
     * The color the popup opened with. Escape puts it back, and a color that never moved
     * away from it isn't worth remembering among the recent ones.
     */
    private final Color initial = new Color();

    /**
     * The color model the current layout was built for. Everything that paints or hits
     * the surface reads this rather than the setting: the setting can change from another
     * picker or the settings screen, and a popup laid out for one model must never be
     * painted as the other. {@link #render} notices the drift and lays out again.
     */
    private boolean layoutHsv;

    /** Whether what's typed into the hex field can't be read as a color. */
    private boolean hexError;

    /** Whether the next click takes its color off the screen rather than doing what it usually does. */
    private boolean picking;

    /** What the eyedropper sees under the cursor, read once a frame while it's armed. */
    private int sampled;

    private final Color sampledColor = new Color();

    /** One color over the checkboard behind it: opaque above the diagonal, as it really is below. */
    public static void renderAlphaPreviewQuad(Batcher2D batcher, int x1, int y1, int x2, int y2, Color color)
    {
        int argb = color.getARGBColor();

        batcher.splitBox(x1, y1, x2, y2, Colors.opaque(argb), argb);
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

        this.eyedropper = new UIIcon(Icons.EYEDROPPER, (b) -> this.picking = !this.picking);
        this.eyedropper.highlight(() -> this.picking, Direction.BOTTOM);
        this.eyedropper.tooltip(UIKeys.COLOR_EYEDROPPER);

        this.tabs = new UITabStrip(ScrollDirection.HORIZONTAL)
        {
            @Override
            protected boolean pressTab(int index, UIContext context)
            {
                this.select(index);

                return true;
            }
        };

        this.tabs.fixed();
        this.tabs.active(() -> this.isHsvPicker() ? 0 : 1);
        this.tabs.onSelect((index) -> this.setHsvPicker(index == 0));
        this.tabs.addTab(new UITextTab(IKey.constant("HSV"))).w(48).h(TABS_HEIGHT);
        this.tabs.addTab(new UITextTab(IKey.constant("RGB"))).w(48).h(TABS_HEIGHT);

        this.fields = new UIColorFields(this::applyChannel);

        this.recent = new UIColorPalette(this.getRecentColors(), this::pickFromPalette).editable();
        this.recent.onChanged(this::resize);

        this.recent.context((menu) ->
        {
            Color color = this.recent.getColor(this.recent.getIndex(this.getContext()));

            if (color != null)
            {
                menu.action(Icons.FAVORITE, UIKeys.COLOR_CONTEXT_FAVORITES_ADD, () -> this.addToFavorites(color));
            }
        });

        /* Only the favorites are put into an order by hand; the recent ones are ordered by time */
        this.favorite = new UIColorPalette(BBSSettings.favoriteColors, this::pickFromPalette).editable().sortable();
        this.favorite.onChanged(this::resize);

        this.favorite.context((menu) ->
        {
            int index = this.favorite.getIndex(this.getContext());

            if (this.favorite.hasColor(index))
            {
                menu.action(Icons.REMOVE, UIKeys.COLOR_CONTEXT_FAVORITES_REMOVE, () -> this.removeFromFavorites(index));
            }
        });

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).add(this.input, this.eyedropper, this.tabs, this.fields, this.favorite, this.recent);
    }

    public UIColorPicker editAlpha()
    {
        this.editAlpha = true;
        this.input.textbox.setLength(9);

        return this;
    }

    public void updateField()
    {
        this.syncFields();

        if (this.input.isFocused())
        {
            return;
        }

        this.syncHexInputAfterEdit();
    }

    /** Show the current color in the numeric row, in the units of the model on show. */
    private void syncFields()
    {
        boolean hsv = this.layoutHsv;

        this.fields.mode(hsv, this.editAlpha);

        if (hsv)
        {
            this.fields.update(this.hsv.r, this.hsv.g, this.hsv.b, this.hsv.a);
        }
        else
        {
            this.fields.update(this.color.r, this.color.g, this.color.b, this.color.a);
        }
    }

    /**
     * One channel was typed or dragged in the numeric row. Channels are numbered the way
     * {@link Color#set(float, int)} numbers them once shifted by one, so the fourth is alpha
     * in both models.
     */
    private void applyChannel(int channel, float value)
    {
        if (this.layoutHsv)
        {
            this.hsv.set(value, channel + 1);
            this.syncColorFromHsv();
        }
        else
        {
            this.color.set(value, channel + 1);
            this.syncHsvFromColor();
        }

        this.notifyColorChanged();
    }

    private void syncHexInputAfterEdit()
    {
        this.input.setText(this.color.stringify(this.editAlpha));
        this.setHexError(false);
    }

    /**
     * A color typed by hand. Half-typed input is left alone — it's on its way somewhere —
     * but input of the right length that still isn't a color is marked instead of silently
     * turning the color into black, which is what a failed parse used to hand back.
     */
    private void applyColorFromHexInput(String string)
    {
        int digits = this.hexDigits(string);

        if (digits != 6 && digits != 8)
        {
            this.setHexError(false);

            return;
        }

        int parsed;

        try
        {
            parsed = Colors.parseWithException(string.trim());
        }
        catch (Exception e)
        {
            this.setHexError(true);

            return;
        }

        this.setHexError(false);
        this.setValue(parsed, digits == 8);
        this.notifyColorChanged();
    }

    /** How many hex digits were typed, without the leading hash. */
    private int hexDigits(String raw)
    {
        if (raw == null)
        {
            return -1;
        }

        String t = raw.trim();

        return t.startsWith("#") ? t.length() - 1 : t.length();
    }

    private void setHexError(boolean error)
    {
        if (this.hexError != error)
        {
            this.hexError = error;
            this.input.setColor(error ? Colors.A100 | Colors.RED : Colors.WHITE);
        }
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
        this.setValue(color, this.editAlpha);
    }

    /**
     * @param withAlpha whether the value carries an alpha channel of its own. A six digit
     *                  color handed to an alpha picker keeps the alpha the picker already
     *                  has, rather than reading the missing channel as fully transparent.
     */
    public void setValue(int color, boolean withAlpha)
    {
        float alpha = this.color.a;

        this.color.set(color, withAlpha && this.editAlpha);

        if (this.editAlpha && !withAlpha)
        {
            this.color.a = alpha;
        }

        this.syncHsvFromColor();
    }

    /** Place the popup and remember what it opened with, so Escape has somewhere to go back to. */
    public void setup(int x, int y)
    {
        this.xy(x, y);
        this.initial.copy(this.color);
        this.setHexError(false);
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

    /* Eyedropper */

    /**
     * What the pixel under the cursor is, out of what's already been painted this frame.
     *
     * <p>The read happens at the top of this element's own painting, which is the moment
     * everything under the popup is on screen and the popup itself is not — so the dropper
     * sees the viewport, the panels and other people's colors, right through its own window.</p>
     */
    private int readPixelUnderCursor(UIContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();

        if (width <= 0 || height <= 0 || context.menu.width <= 0)
        {
            return this.color.getARGBColor();
        }

        /* Nothing may still be sitting in a buffer: the read is of the framebuffer, not of the queue */
        context.batcher.flush();

        /* The interface is drawn at its own scale; the framebuffer is in real pixels and upside down */
        float scale = width / (float) context.menu.width;
        int x = MathUtils.clamp(Math.round(context.globalX(context.mouseX) * scale), 0, width - 1);
        int y = MathUtils.clamp(height - 1 - Math.round(context.globalY(context.mouseY) * scale), 0, height - 1);

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            FloatBuffer floats = stack.mallocFloat(4);

            GL11.glReadPixels(x, y, 1, 1, GL11.GL_RGBA, GL11.GL_FLOAT, floats);

            /* Whatever is on screen is opaque; the alpha being edited is the picker's own */
            return this.sampledColor.set(floats.get(0), floats.get(1), floats.get(2), this.color.a).getARGBColor();
        }
    }

    /** Take the sampled color and put the dropper away. */
    private void applySample()
    {
        this.picking = false;

        this.setValue(this.sampled, this.editAlpha);
        this.notifyColorChanged();
    }

    /** A color chosen out of one of the palettes. */
    private void pickFromPalette(Color color)
    {
        this.setColor(color.getARGBColor());
        this.notifyColorChanged();
    }

    /**
     * Take a color handed in from outside the popup — the presets panel, say. Those are
     * opaque, so a picker that edits alpha keeps the alpha it already has rather than
     * being turned solid behind the user's back.
     */
    public void applyOpaqueColor(Color color)
    {
        this.setValue(color.getRGBColor(), false);
        this.notifyColorChanged();
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

    /** Settle on the current color: only one the user actually moved to is worth remembering. */
    private void closePicker()
    {
        this.removeFromParent();

        if (!this.color.equals(this.initial))
        {
            this.addToRecent();
        }
    }

    /** Escape puts the color back the way it was when the popup opened, and closes it. */
    private void cancelPicker()
    {
        this.removeFromParent();
        this.revertToInitial();
    }

    /** Back to the color the picker opened with, leaving it open. */
    private void revertToInitial()
    {
        if (this.color.equals(this.initial))
        {
            return;
        }

        this.color.copy(this.initial);
        this.syncHsvFromColor();
        this.notifyColorChanged();
    }

    /* GuiElement overrides */

    @Override
    public void resize()
    {
        PickerLayout layout = this.createLayout();

        this.layoutHsv = layout.hsv;

        this.w(layout.width);
        this.h(layout.height);

        if (this.resizer != null)
        {
            this.resizer.apply(this.area);
        }

        this.afterResizeApplied();
        this.applyLayout(layout);

        this.input.resize();
        this.eyedropper.resize();
        this.tabs.resize();
        this.syncFields();
        this.fields.resize();
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
        layout.width = POPUP_WIDTH;
        layout.paletteWidth = layout.width - POPUP_PADDING * 2;
        layout.favoriteHeight = this.favorite.isEmpty() ? 0 : this.favorite.getHeight(layout.paletteWidth);
        layout.recentHeight = this.recent.isEmpty() ? 0 : this.recent.getHeight(layout.paletteWidth);
        layout.tabsY = HEADER_HEIGHT;
        layout.surfaceY = layout.tabsY + TABS_HEIGHT + TABS_GAP;
        layout.surfaceHeight = layout.hsv ? this.hsvSize(layout.paletteWidth) : RGB_SLIDER_HEIGHT;
        layout.fieldsY = layout.surfaceY + layout.surfaceHeight + FIELDS_GAP;
        layout.paletteY = layout.fieldsY + FIELDS_HEIGHT + (layout.hsv ? HSV_SECTION_GAP : RGB_SECTION_GAP);

        /* Both palettes are placed from the layout alone. Reading one's area to place the
         * other would read it a layout late — the element's area only catches up with its
         * flex in resize(), which runs after everything here. */
        layout.favoriteY = layout.paletteY;
        layout.recentY = layout.favoriteHeight > 0 ? layout.paletteY + layout.favoriteHeight + PALETTE_GAP : layout.paletteY;

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
        int surfaceY = this.area.y + layout.surfaceY;
        int headerY = this.area.y + POPUP_PADDING;
        int previewX = this.area.ex() - POPUP_PADDING - PREVIEW_WIDTH;
        int eyedropperX = previewX - HEADER_GAP - EYEDROPPER_SIZE;

        this.preview.set(previewX, headerY, PREVIEW_WIDTH, PREVIEW_SIZE);
        this.eyedropper.set(eyedropperX, headerY, EYEDROPPER_SIZE, INPUT_HEIGHT);
        this.input.set(contentX, headerY, eyedropperX - HEADER_GAP - contentX, INPUT_HEIGHT);
        this.tabs.set(contentX, this.area.y + layout.tabsY, layout.paletteWidth, TABS_HEIGHT);

        if (layout.hsv)
        {
            this.layoutHsv(contentX, surfaceY, layout.paletteWidth);
        }
        else
        {
            this.layoutRgb(contentX, surfaceY, layout.paletteWidth);
        }

        this.fields.set(contentX, this.area.y + layout.fieldsY, layout.paletteWidth, FIELDS_HEIGHT);
        this.favorite.set(contentX, this.area.y + layout.favoriteY, layout.paletteWidth, layout.favoriteHeight);
        this.recent.set(contentX, this.area.y + layout.recentY, layout.paletteWidth, layout.recentHeight);
    }

    /**
     * The saturation/value field is a square — saturation across, value down, at the same
     * rate — so its side is whatever the sliders down its right leave, and the popup grows
     * to that. The sliders stand as tall as it does.
     */
    private int hsvSize(int width)
    {
        int sliders = HSV_SLIDER_GAP + HSV_SLIDER_WIDTH + (this.editAlpha ? HSV_SLIDER_GAP + HSV_SLIDER_WIDTH : 0);

        return width - sliders;
    }

    private void layoutHsv(int x, int y, int width)
    {
        int size = this.hsvSize(width);

        this.picker.set(x, y, size, size);
        this.hue.set(this.picker.ex() + HSV_SLIDER_GAP, y, HSV_SLIDER_WIDTH, size);

        if (this.editAlpha)
        {
            this.alpha.set(this.hue.ex() + HSV_SLIDER_GAP, y, HSV_SLIDER_WIDTH, size);
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

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.picking)
        {
            /* Pressing the dropper again is how it's put away, so that press stays its own */
            if (context.mouseButton == 0 && !this.eyedropper.area.isInside(context))
            {
                this.applySample();

                return true;
            }

            if (context.mouseButton == 1)
            {
                this.picking = false;

                return true;
            }
        }

        if (context.mouseButton == 0 && this.isOverInitial(context))
        {
            this.revertToInitial();

            return true;
        }

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
            /* Escape puts the dropper away first; the picker itself stays open */
            if (this.picking)
            {
                this.picking = false;

                return true;
            }

            this.cancelPicker();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        /* The setting may have been changed elsewhere while this popup was open */
        if (this.layoutHsv != this.isHsvPicker())
        {
            this.resize();
        }

        this.handleDragging(context);

        /* Before anything of this popup is painted: what the dropper sees is what's under it */
        if (this.picking)
        {
            this.sampled = this.readPixelUnderCursor(context);
        }

        this.renderBackground(context);
        this.renderPreview(context);

        if (this.layoutHsv)
        {
            this.renderHsv(context);
        }
        else
        {
            this.renderRgb(context);
        }

        this.renderPaletteLabels(context);

        super.render(context);

        if (this.picking)
        {
            this.renderSample(context);
        }
    }

    /** What the dropper sees right now, beside the cursor, over everything else. */
    private void renderSample(UIContext context)
    {
        int x = context.mouseX + 10;
        int y = context.mouseY + 10;

        context.requestCursor(GLFW.GLFW_CROSSHAIR_CURSOR);

        context.batcher.box(x - 1, y - 1, x + SAMPLE_SIZE + 1, y + SAMPLE_SIZE + 1, Colors.A100);
        context.batcher.box(x, y, x + SAMPLE_SIZE, y + SAMPLE_SIZE, Colors.opaque(this.sampled));
        context.batcher.textCard(this.sampledColor.stringify(), x + SAMPLE_SIZE + 4, y + (SAMPLE_SIZE - context.batcher.getFont().getHeight()) / 2);
    }

    private boolean beginDragging(UIContext context)
    {
        if (this.layoutHsv)
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

        if (this.layoutHsv)
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
        Area slider = this.rgbSlider(this.dragging);
        float factor = (context.mouseX - (slider.x + SLIDER_INSET)) / (float) (slider.w - SLIDER_INSET * 2);

        this.color.set(MathUtils.clamp(factor, 0, 1), this.dragging);
        this.syncHsvFromColor();
        this.notifyColorChanged();
    }

    /** The strip a channel is dragged along; channels are numbered as {@link Color#set(float, int)} numbers them. */
    private Area rgbSlider(int channel)
    {
        switch (channel)
        {
            case DRAG_RGB_RED:
                return this.red;

            case DRAG_RGB_GREEN:
                return this.green;

            case DRAG_RGB_BLUE:
                return this.blue;

            default:
                return this.alpha;
        }
    }

    /** Where a channel's marker sits along its strip. */
    private int markerX(Area slider, float value)
    {
        return slider.x + SLIDER_INSET + (int) ((slider.w - SLIDER_INSET * 2) * value);
    }

    private boolean isHsvPicker()
    {
        return BBSSettings.colorPickerHsvTab.get();
    }

    /**
     * Switch color models. The choice is remembered globally, so every picker opens on
     * the tab the last one was left on; the settings screen has no row for it.
     */
    private void setHsvPicker(boolean hsv)
    {
        if (this.isHsvPicker() == hsv)
        {
            return;
        }

        BBSSettings.colorPickerHsvTab.set(hsv);

        this.dragging = -1;
        this.syncFields();
        this.resize();
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

        this.renderMarker(context.batcher, this.markerX(this.red, this.color.r), this.red.my());
        this.renderMarker(context.batcher, this.markerX(this.green, this.color.g), this.green.my());
        this.renderMarker(context.batcher, this.markerX(this.blue, this.color.b), this.blue.my());

        if (this.editAlpha)
        {
            this.renderMarker(context.batcher, this.markerX(this.alpha, this.color.a), this.alpha.my());
        }
    }

    private void renderPaletteLabels(UIContext context)
    {
        if (!this.favorite.isEmpty())
        {
            context.batcher.text(UIKeys.COLOR_FAVORITE.get(), this.favorite.area.x, this.favorite.area.y - 10, Colors.GRAY);
        }

        if (!this.recent.isEmpty())
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

    /** The well the square and its sliders sit in: a step below the popup, like every other field. */
    private void renderSliderBackdrop(Batcher2D batcher, Area picker, int right)
    {
        batcher.box(picker.x - 1, picker.y - 1, right + 1, picker.ey() + 1, BBSSettings.deepSurface());
    }

    public void renderRect(Batcher2D batcher, int x1, int y1, int x2, int y2)
    {
        this.renderSwatch(batcher, x1, y1, x2, y2, this.color);
    }

    /**
     * The picker floats over whatever it was opened from, so it takes the raised surface of
     * the tonal map and the shadow every other popup casts — the same background as a context
     * menu or an overlay panel, rather than a fixed grey of its own.
     */
    private void renderBackground(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, BBSSettings.panelShadowOpaqueColor(), BBSSettings.panelShadowTransparentColor());

        this.area.render(context.batcher, BBSSettings.raisedSurface());
    }

    /** One color as a patch: over a checkboard, split along the diagonal, when alpha is edited. */
    private void renderSwatch(Batcher2D batcher, int x1, int y1, int x2, int y2, Color color)
    {
        if (this.editAlpha)
        {
            batcher.iconArea(Icons.CHECKBOARD, x1, y1, x2 - x1, y2 - y1);
            renderAlphaPreviewQuad(batcher, x1, y1, x2, y2, color);
        }
        else
        {
            batcher.box(x1, y1, x2, y2, color.getARGBColor());
        }
    }

    /** The color the picker opened with beside the one it holds now. */
    private void renderPreview(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int half = this.preview.x + this.preview.w / 2;

        this.renderSwatch(batcher, this.preview.x, this.preview.y, half, this.preview.ey(), this.initial);
        this.renderSwatch(batcher, half, this.preview.y, this.preview.ex(), this.preview.ey(), this.color);

        /* A seam down the middle, so two nearly equal colors still read as two patches */
        batcher.box(half, this.preview.y, half + 1, this.preview.ey(), Colors.A25);
        batcher.outline(this.preview.x, this.preview.y, this.preview.ex(), this.preview.ey(), Colors.A25);

        if (this.isOverInitial(context) && !this.color.equals(this.initial))
        {
            context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            batcher.textCard(UIKeys.COLOR_REVERT.get(), context.mouseX + 6, context.mouseY + 10);
        }
    }

    /** Whether the cursor is over the half of the preview that puts the original color back. */
    private boolean isOverInitial(UIContext context)
    {
        return this.preview.isInside(context) && context.mouseX < this.preview.x + this.preview.w / 2;
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
        public int tabsY;
        public int surfaceY;
        public int surfaceHeight;
        public int fieldsY;
        public int paletteY;
        public int favoriteY;
        public int recentY;
        public int recentHeight;
        public int favoriteHeight;
    }
}
