package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.VideoExportSettings;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.iris.IrisShaderPacks;
import mchorse.bbs_mod.utils.iris.ShaderOptions;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.fabricmc.loader.api.FabricLoader;
import org.joml.Vector2i;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The world as BBS renders it for a recording, shown in an editor: the render fitted to its own
 * proportions in the middle of the element (the viewport), the framing guides over it, a bar of
 * icons along its bottom, and the frame's corners to put text in ({@link PreviewHud}).
 *
 * <p>It also decides how big the world is rendered while it is on screen ({@link #applySize()}),
 * by the preview size settings: at the export's size, at a size of the user's, or at its own
 * size on screen.
 *
 * <p>The film editor's preview builds on it ({@link UIFilmPreview}) with the film's tools.
 */
public class UIWorldPreview extends UIElement
{
    private static final int SIZE_MODE_EXPORT = 0;
    private static final int SIZE_MODE_CUSTOM = 1;
    private static final int SIZE_MODE_AUTO = 2;

    public UIElement icons;

    private final Map<UIIcon, ValueBoolean> iconSettings = new LinkedHashMap<>();

    /** Whether the icon bar is currently shown - see {@link #updateIconsVisibility(UIContext)} */
    protected boolean iconsVisible = true;

    /** Everything drawn over the frame goes through here - see {@link PreviewHud}. */
    protected final PreviewHud hud = new PreviewHud();

    /** The export's frame inside the viewport, see {@link #getExportFrame(Area)}. */
    private final Area exportFrame = new Area();

    /** Whether the bands were on last frame, to give the render its size when they switch. */
    private boolean exportBands;

    /**
     * Sets the world render to the size of the given export settings, rounded up to even sides.
     * Used when a recording starts, and in the film editor's full screen preview (F1).
     */
    public static void applyExportSize(VideoExportSettings settings)
    {
        int w = Math.max(2, settings.width());
        int h = Math.max(2, settings.height());

        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;

        BBSRendering.setCustomSize(true, w, h);
    }

    public UIWorldPreview()
    {
        this.icons = UI.row(0, 0);
        this.icons.row().resize();
        this.icons.relative(this).x(0.5F).y(1F).anchor(0.5F, 1F);

        this.add(this.icons);
    }

    /**
     * Puts an icon on the bar, shown while the given setting is on.
     */
    public void addIcon(UIIcon icon, ValueBoolean setting)
    {
        this.iconSettings.put(icon, setting);
        icon.setVisible(setting.get());
        this.icons.add(icon);
    }

    public boolean isIconBarVisible()
    {
        return this.iconsVisible;
    }

    /**
     * The icon that switches the shader pack on and off (Iris), lit while shaders are on.
     */
    protected UIIcon createShadersIcon()
    {
        boolean iris = FabricLoader.getInstance().isModLoaded("iris");
        UIIcon shaders = new UIIcon(Icons.SUN, (b) -> this.toggleShaders());

        shaders.highlight(BBSRendering::isIrisShadersEnabled, Direction.BOTTOM);
        shaders.tooltip(iris ? UIKeys.FILM_TOGGLE_SHADERS : UIKeys.UTILITY_IRIS_REQUIRED);
        shaders.setEnabled(iris);

        return shaders;
    }

    protected void toggleShaders()
    {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return;

        UIContext context = this.getContext();

        ShaderOptions.reload(() ->
        {
            if (!IrisShaderPacks.toggle())
            {
                UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.FILM_TOGGLE_SHADERS, UIKeys.FILM_SELECT_SHADER));
            }
        }, () -> UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.UTILITY_SHADER_ERROR)));
    }

    /**
     * The export the preview frames: in the export size mode the world renders at its size, and
     * whenever the preview {@link #isFramingExport() frames the export} at its proportions.
     */
    protected VideoExportSettings getExportSettings()
    {
        return VideoExportSettings.global();
    }

    /**
     * Whether the preview shows the export's proportions even when it renders at its own size
     * on screen (the film editor while the camera is edited).
     */
    protected boolean isFramingExport()
    {
        return false;
    }

    /**
     * Whether the render fills the whole preview and the export's frame is marked inside it by
     * bands, the way it was done before the render took the export's proportions. Only in the
     * automatic size mode, while the preview {@link #isFramingExport() frames the export}.
     */
    protected boolean isShowingExportBands()
    {
        return BBSSettings.editorPreviewExportBands.get()
            && BBSSettings.editorPreviewSizeMode.get() == SIZE_MODE_AUTO
            && this.isFramingExport();
    }

    /**
     * Whether the render's size must stay as it is: a recording or a screenshot owns it.
     */
    protected boolean isSizeLocked()
    {
        return BBSRendering.isHoldingExportFrame();
    }

    /**
     * Whether the icon bar may show at all (nothing on it can be used during an export).
     */
    protected boolean canShowIcons()
    {
        return true;
    }

    /**
     * Sets how big the world is rendered, by the preview size settings. Called when the preview
     * is laid out or resized, when what it frames changes, and once a recording or a screenshot
     * gives the size back.
     */
    public void applySize()
    {
        if (this.isSizeLocked())
        {
            return;
        }

        int w;
        int h;
        int mode = BBSSettings.editorPreviewSizeMode.get();

        if (mode == SIZE_MODE_EXPORT)
        {
            VideoExportSettings export = this.getExportSettings();

            w = Math.max(2, export.width());
            h = Math.max(2, export.height());
        }
        else if (mode == SIZE_MODE_CUSTOM)
        {
            w = Math.max(2, BBSSettings.editorPreviewCustomWidth.get());
            h = Math.max(2, BBSSettings.editorPreviewCustomHeight.get());
        }
        else
        {
            float scale = BBSSettings.editorPreviewResolutionScale.get();

            if (this.isFramingExport() && !this.isShowingExportBands())
            {
                VideoExportSettings export = this.getExportSettings();
                int previewW = Math.max(2, this.area.w);
                int previewH = Math.max(2, this.area.h);
                int exportW = Math.max(2, export.width());
                int exportH = Math.max(2, export.height());
                Vector2i resized = Vectors.resize(exportW / (float) exportH, previewW, previewH);

                w = Math.max(2, (int) (resized.x * scale));
                h = Math.max(2, (int) (resized.y * scale));
            }
            else
            {
                w = Math.max(2, (int) (this.area.w * scale));
                h = Math.max(2, (int) (this.area.h * scale));
            }
        }

        if (w % 2 != 0) w++;
        if (h % 2 != 0) h++;

        if (w != BBSRendering.getVideoWidth() || h != BBSRendering.getVideoHeight())
        {
            BBSRendering.setCustomSize(true, w, h);
        }
    }

    /**
     * Where the render is drawn: as big as fits into the element at the render's proportions,
     * in its middle.
     */
    public Area getViewport()
    {
        int width = BBSRendering.getVideoWidth();
        int height = BBSRendering.getVideoHeight();
        Vector2i size = Vectors.resize(width / (float) height, this.area.w, this.area.h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.mx() - area.w / 2, this.area.my() - area.h / 2);

        return area;
    }

    /**
     * The export's frame inside the given viewport: as big as fits at the export's proportions,
     * in its middle, while the {@link #isShowingExportBands() bands} are up, the viewport itself
     * otherwise. The guides are drawn in it.
     */
    public Area getExportFrame(Area viewport)
    {
        this.exportFrame.copy(viewport);

        if (this.isShowingExportBands() && !this.isSizeLocked())
        {
            VideoExportSettings export = this.getExportSettings();
            float aspect = Math.max(2, export.width()) / (float) Math.max(2, export.height());
            Vector2i size = Vectors.resize(aspect, viewport.w, viewport.h);

            this.exportFrame.setSize(size.x, size.y);
            this.exportFrame.setPos(viewport.mx() - size.x / 2, viewport.my() - size.y / 2);
        }

        return this.exportFrame;
    }

    /**
     * Keeps the render's size and the camera in step with the bands: switching them on or off
     * (the setting, or the camera editor coming up) resizes the render, and while they are up
     * the world is told the frame's proportions so the camera widens to them
     * ({@link BBSRendering#getExportFrameFovScale()}). Called every frame the preview is drawn.
     */
    protected void updateExportBands()
    {
        boolean bands = this.isShowingExportBands();

        if (bands != this.exportBands)
        {
            this.exportBands = bands;
            this.applySize();
        }

        float aspect = 0F;

        if (bands && !this.isSizeLocked())
        {
            VideoExportSettings export = this.getExportSettings();

            aspect = Math.max(2, export.width()) / (float) Math.max(2, export.height());
        }

        BBSRendering.setExportFrameAspect(aspect);
    }

    /**
     * Whether the mouse is over the icon bar. The bar is drawn over the frame, so nothing
     * of the scene under it (gizmo, bones, controllers) may take the click or the hover.
     */
    public boolean isOverIcons(UIContext context)
    {
        return this.icons.isVisible() && this.icons.area.isInside(context);
    }

    /**
     * The icon bar can be set to stay out of the way until the mouse comes over the
     * preview. A context menu opened from an icon takes the mouse out of the preview,
     * so while any menu is up the bar keeps the visibility it had when it opened.
     */
    protected void updateIconsVisibility(UIContext context)
    {
        boolean changed = false;

        for (Map.Entry<UIIcon, ValueBoolean> entry : this.iconSettings.entrySet())
        {
            boolean visible = entry.getValue().get();

            if (entry.getKey().isVisible() != visible)
            {
                entry.getKey().setVisible(visible);
                changed = true;
            }
        }

        if (changed) this.icons.resize();

        if (!BBSSettings.editorPreviewIconsAutoHide.get())
        {
            this.iconsVisible = true;
        }
        else if (!context.hasContextMenu())
        {
            this.iconsVisible = this.area.isInside(context.mouseX, context.mouseY);
        }

        this.iconsVisible &= this.icons.getChildren().stream().anyMatch(element -> element.isVisible());
        this.iconsVisible &= this.canShowIcons();
        this.icons.setVisible(this.iconsVisible);
    }

    @Override
    public void render(UIContext context)
    {
        Area area = this.getViewport();

        this.updateExportBands();
        this.renderFrame(context, area);
        this.updateIconsVisibility(context);
        this.hud.begin(area);
        this.reserveIconBar(area);
        this.renderGuides(context, area);
        this.renderIconBar(context);
        this.hud.flush(context);
        this.renderElements(context);
    }

    /** The world's render, into the viewport. */
    protected void renderFrame(UIContext context, Area area)
    {
        Texture texture = BBSRendering.getTexture();

        context.batcher.flush();

        if (texture != null)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }
    }

    /** Keeps the frame's bottom middle free where the icon bar is up. */
    protected void reserveIconBar(Area area)
    {
        if (this.iconsVisible)
        {
            this.hud.reserve(PreviewHud.Anchor.BOTTOM_CENTER, area.ey() - this.icons.area.y);
        }
    }

    /**
     * The framing guides switched on in the settings: thirds, centre lines, crosshair, all in
     * the export's frame, with the bands around it while they are up.
     */
    protected void renderGuides(UIContext context, Area viewport)
    {
        Area area = this.getExportFrame(viewport);

        this.renderExportBands(context, viewport, area);

        if (BBSSettings.editorRuleOfThirds.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();

            context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
            context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

            context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
            context.batcher.box(area.x, area.y + area.h - area.h / 3, area.x + area.w, area.y + area.h - area.h / 3 + 1, guidesColor);
        }

        if (BBSSettings.editorCenterLines.get())
        {
            int guidesColor = BBSSettings.editorGuidesColor.get();
            int x = area.mx();
            int y = area.my();

            context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
            context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
        }

        if (BBSSettings.editorCrosshair.get())
        {
            int x = area.mx() + 1;
            int y = area.my() + 1;

            context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
            context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
        }
    }

    /** Shades the viewport outside the export's frame. */
    protected void renderExportBands(UIContext context, Area viewport, Area frame)
    {
        if (frame.w == viewport.w && frame.h == viewport.h)
        {
            return;
        }

        int color = Colors.setA(0, BBSSettings.editorPreviewExportBandsOpacity.get());

        if (viewport.y < frame.y) context.batcher.box(viewport.x, viewport.y, viewport.ex(), frame.y, color);
        if (frame.ey() < viewport.ey()) context.batcher.box(viewport.x, frame.ey(), viewport.ex(), viewport.ey(), color);
        if (viewport.x < frame.x) context.batcher.box(viewport.x, frame.y, frame.x, frame.ey(), color);
        if (frame.ex() < viewport.ex()) context.batcher.box(frame.ex(), frame.y, viewport.ex(), frame.ey(), color);
    }

    /** The shade behind the icon bar, while it is up. */
    protected void renderIconBar(UIContext context)
    {
        if (this.iconsVisible)
        {
            Area a = this.icons.area;
            int barShade = BBSSettings.lightSurfaces() ? (Colors.A50 | 0xFFFFFF) : Colors.A50;

            context.batcher.gradientVBox(a.x, a.y, a.ex(), a.ey(), 0, barShade);
        }
    }

    /** The preview's own elements (the icon bar, layers put over it), kept inside it. */
    protected void renderElements(UIContext context)
    {
        context.batcher.clip(this.area, context);
        super.render(context);
        context.batcher.unclip(context);
    }
}
