package mchorse.bbs_mod.ui.framework.elements.utils;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fc;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * 2D immediate-mode UI drawing.
 *
 * Ported to 1.21.11. Three big changes from 1.21.1:
 *
 * <ul>
 *   <li>The GUI is now 2D-transform based: {@link DrawContext#getMatrices()} returns an
 *       {@link org.joml.Matrix3x2fStack} (not a 4x4 {@code MatrixStack}). The position-only vertex
 *       calls take a {@link Matrix3x2fc}; z is always 0 in the GUI.</li>
 *   <li>{@code RenderSystem.setShader(...)} / {@code BufferRenderer.drawWithGlobalProgram(...)} are
 *       gone. The GUI is two-phase/deferred: {@code DrawContext} accumulates a
 *       {@link net.minecraft.client.gui.render.state.GuiRenderState} that vanilla composites AFTER
 *       {@code Screen.render} returns. A self-issued {@code RenderLayer.draw} mid-frame would target
 *       a different (un-composited) state, so solid rectangles and gradients now route through the
 *       managed {@code context.fill}/{@code context.fillGradient} path, and text through
 *       {@code context.drawText}. The batcher's {@code DrawContext} is swapped to vanilla's live
 *       per-frame context by {@code UIScreen.render} (see {@link #setContext}).</li>
 *   <li>{@code DrawContext.draw()} no longer exists (the two-phase GUI flushes deferred draws by
 *       itself), and {@code RenderSystem.depthFunc(...)} is gone — both calls were removed.</li>
 * </ul>
 *
 * TEXTURED drawing for BBS {@link Texture} objects (texturedBox / texturedArea / icons) is bridged
 * through {@link AdoptedTexture}: the raw GL id is adopted (zero-copy) into a vanilla
 * {@code AbstractTexture}/{@code GpuTextureView}, registered under an {@code Identifier}, and drawn via
 * {@code context.drawTexture(RenderPipelines.GUI_TEXTURED, ...)} so it composites in the two-phase GUI.
 *
 * The raw-GL-id overloads {@code texturedBox(int,...)} / {@code texturedBox(Supplier,...)} (in-panel
 * framebuffer previews: film / picker / model-block) are bridged the same way, via
 * {@link AdoptedTexture#identifier(int, int, int, boolean)} which adopts the bare FBO color-attachment
 * GL id zero-copy. The {@code Supplier<RenderPipeline>} shader selector is ignored (always GUI_TEXTURED);
 * callers that depended on a CUSTOM pipeline's per-draw uniforms (multilink, subtitle blur) still need
 * those uniforms re-wired - see the per-call-site TODOs.
 *
 * TODO(1.21.11 render): fine gradients (horizontal/4-corner), drop shadows and circular shadows are
 * approximated (solid fill) or skipped — the two-phase GUI has no native primitive for them. Restore
 * via custom GuiRenderState elements / shader pipelines later.
 */
public class Batcher2D
{
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    /* BBS-owned 2D POSITION_COLOR pipelines (no depth test - GUI overlay), one per draw mode used
     * by the batcher. Seeded from the vanilla GUI position-color snippet so the GUI projection /
     * transform UBO is supplied. */
    private static final RenderPipeline GUI_QUADS = RenderPipelines.register(
        guiColorBuilder("gui_color_quads", VertexFormat.DrawMode.QUADS).build()
    );

    private static final RenderPipeline GUI_TRIANGLES = RenderPipelines.register(
        guiColorBuilder("gui_color_triangles", VertexFormat.DrawMode.TRIANGLES).build()
    );

    private static final RenderPipeline GUI_TRIANGLE_FAN = RenderPipelines.register(
        guiColorBuilder("gui_color_triangle_fan", VertexFormat.DrawMode.TRIANGLE_FAN).build()
    );

    private static RenderLayer guiQuadsLayer;
    private static RenderLayer guiTrianglesLayer;
    private static RenderLayer guiTriangleFanLayer;

    private static FontRenderer fontRenderer = new FontRenderer();

    private DrawContext context;
    private FontRenderer font;

    /* Mirror of the live GUI scissor stack (vanilla's DrawContext.scissorStack is private). Kept in lock-step
     * with enableScissor/disableScissor by clip()/unclip() so custom deferred elements (drawQuadMesh) can be
     * scissored identically to context.fill. Cleared each frame in setContext (vanilla rebuilds its stack). */
    private final Deque<ScreenRect> scissorStack = new ArrayDeque<>();

    private static RenderPipeline.Builder guiColorBuilder(String name, VertexFormat.DrawMode mode)
    {
        return RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(net.minecraft.util.Identifier.of(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexFormat(VertexFormats.POSITION_COLOR, mode)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false);
    }

    private static RenderLayer layer(RenderPipeline pipeline, String name, RenderLayer cached)
    {
        if (cached != null)
        {
            return cached;
        }

        return RenderLayer.of(BBSMod.MOD_ID + "_" + name, RenderSetup.builder(pipeline).translucent().build());
    }

    private static RenderLayer getQuadsLayer()
    {
        return guiQuadsLayer = layer(GUI_QUADS, "gui_color_quads", guiQuadsLayer);
    }

    private static RenderLayer getTrianglesLayer()
    {
        return guiTrianglesLayer = layer(GUI_TRIANGLES, "gui_color_triangles", guiTrianglesLayer);
    }

    private static RenderLayer getTriangleFanLayer()
    {
        return guiTriangleFanLayer = layer(GUI_TRIANGLE_FAN, "gui_color_triangle_fan", guiTriangleFanLayer);
    }

    /** Finish a buffer and submit it through the given layer (no-op on an empty buffer). */
    private static void flush(BufferBuilder builder, RenderLayer renderLayer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            renderLayer.draw(built);
        }
    }

    public static FontRenderer getDefaultTextRenderer()
    {
        fontRenderer.setRenderer(MinecraftClient.getInstance().textRenderer);

        return fontRenderer;
    }

    public Batcher2D(DrawContext context)
    {
        this.context = context;
        this.font = getDefaultTextRenderer();
    }

    public DrawContext getContext()
    {
        return this.context;
    }

    /**
     * Swap in the live per-frame vanilla {@link DrawContext}. The 1.21.6+ GUI is two-phase: vanilla
     * only composites the {@link net.minecraft.client.gui.render.state.GuiRenderState} that belongs
     * to the {@code DrawContext} it passes into {@code Screen.render}. The batcher must therefore draw
     * into that exact context each frame (set by {@code UIScreen.render}) or nothing is composited.
     */
    public void setContext(DrawContext context)
    {
        this.context = context;
        this.scissorStack.clear();
    }

    /** The active GUI scissor (top of the mirrored stack), or {@code null} when nothing is clipped. */
    public ScreenRect getCurrentScissor()
    {
        return this.scissorStack.peek();
    }

    /**
     * Submit a recorded POSITION_COLOR quad mesh into the deferred GUI as one simple element (mirrors how
     * {@code context.fill} records a {@code ColoredQuadGuiElementRenderState}). The mesh is clipped by the
     * current scissor and composites in the correct GUI layer order — unlike an immediate {@code RenderLayer
     * .draw}, which is overpainted by the two-phase GUI. No-op for an empty / fully-clipped mesh.
     */
    public void drawQuadMesh(GuiQuadMesh mesh)
    {
        if (mesh == null || mesh.isEmpty())
        {
            return;
        }

        ScreenRect scissor = this.getCurrentScissor();
        ScreenRect bounds = mesh.computeBounds(scissor);

        if (bounds == null)
        {
            return;
        }

        this.context.state.addSimpleElement(new GuiQuadMesh.State(
            RenderPipelines.GUI, TextureSetup.empty(),
            mesh.xs(), mesh.ys(), mesh.colors(), mesh.count(),
            scissor, bounds));
    }

    /**
     * Open a fresh root layer in the deferred {@link net.minecraft.client.gui.render.state.GuiRenderState}.
     *
     * <p>The two-phase GUI (1.21.6+) records draws into a layer tree and composites it later. Within a
     * single root layer the z-order of overlapping elements is decided by bounds-intersection
     * ({@code GuiRenderState.findAndGoToLayerIntersecting}), NOT recording order, so an element can be
     * overpainted by sibling chrome that was recorded earlier but climbed onto a higher sub-layer. Root
     * layers, by contrast, composite in strict insertion (painter's) order. Bracketing a draw with this
     * call therefore forces it onto its own root layer with a deterministic z relative to everything
     * recorded before/after it. Vanilla brackets {@code renderBackground}/{@code render} exactly this way
     * (see {@code Screen.renderWithTooltip}).</p>
     */
    public void newRootLayer()
    {
        this.context.createNewRootLayer();
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    private Matrix3x2fc matrix()
    {
        return this.context.getMatrices();
    }

    /* Screen space clipping */

    public void clip(Area area, UIContext context)
    {
        this.clip(area.x, area.y, area.w, area.h, context);
    }

    public void clip(int x, int y, int w, int h, UIContext context)
    {
        this.clip(context.globalX(x), context.globalY(y), w, h, context.menu.width, context.menu.height);
    }

    /**
     * Scissor (clip) the screen
     */
    public void clip(int x, int y, int w, int h, int sw, int sh)
    {
        this.context.enableScissor(x, y, x + w, y + h);

        /* Mirror vanilla's ScissorStack: push (x,y,w,h) intersected with the current top. */
        ScreenRect rect = new ScreenRect(x, y, Math.max(0, w), Math.max(0, h));
        ScreenRect top = this.scissorStack.peek();

        if (top != null)
        {
            ScreenRect intersection = top.intersection(rect);

            rect = intersection != null ? intersection : ScreenRect.empty();
        }

        this.scissorStack.push(rect);
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.context.disableScissor();

        if (!this.scissorStack.isEmpty())
        {
            this.scissorStack.pop();
        }
    }

    /* Solid rectangles */

    public void normalizedBox(float x1, float y1, float x2, float y2, int color)
    {
        float temp = x1;

        x1 = Math.min(x1, x2);
        x2 = Math.max(temp, x2);

        temp = y1;

        y1 = Math.min(y1, y2);
        y2 = Math.max(temp, y2);

        this.box(x1, y1, x2, y2, color);
    }

    public void box(float x1, float y1, float x2, float y2, int color)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        int x1 = (int) x;
        int y1 = (int) y;
        int x2 = (int) (x + w);
        int y2 = (int) (y + h);

        if (color1 == color2 && color1 == color3 && color1 == color4)
        {
            /* Solid fill - composites correctly through the two-phase GUI (GuiRenderState). */
            this.context.fill(x1, y1, x2, y2, color1);
        }
        else if (color1 == color2 && color3 == color4)
        {
            /* Vertical gradient (color1 = top, color3 = bottom). */
            this.context.fillGradient(x1, y1, x2, y2, color1, color3);
        }
        else
        {
            /* TODO(1.21.11 render): horizontal / true 4-corner gradients have no native two-phase-GUI
             * primitive. Approximate with a solid midpoint for the prototype (gradientHBox callers). */
            this.context.fill(x1, y1, x2, y2, Colors.lerp(color1, color4, 0.5F));
        }
    }

    public void fillRect(VertexConsumer builder, Matrix3x2fc matrix, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        /* c1 ---- c2
         * |        |
         * c3 ---- c4 */
        builder.vertex(matrix, x, y).color(color1);
        builder.vertex(matrix, x, y + h).color(color3);
        builder.vertex(matrix, x + w, y + h).color(color4);
        builder.vertex(matrix, x + w, y).color(color2);
    }

    public void bevelBox(int x1, int y1, int x2, int y2, int fill, boolean shadow, boolean border)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        this.box(x1, y1, x2, y2, fill);

        if (!BBSSettings.interfaceShadows.get())
        {
            return;
        }

        int light = Colors.lerp(fill, Colors.WHITE, 0.35F);

        this.box(x1, y1, x2, y1 + 1, light);
        this.box(x1, y1, x1 + 1, y2, light);

        if (shadow)
        {
            this.box(x1, y2 - 2, x2, y2, Colors.lerp(fill, Colors.A100, 0.4F));
        }
    }

    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        /* The original feathered drop shadow was a custom POSITION_COLOR gradient mesh (opaque centre
         * covered by the surface drawn on top, soft halo bleeding outward). The two-phase GUI has no
         * native primitive for that mesh, so reproduce the visible part — the outset halo — as a stack
         * of concentric 1px rings whose colour fades opaque -> transparent outward.
         *
         * Crucially the rings stay strictly OUTSIDE [left, top, right, bottom]: never place translucent
         * geometry under the opaque panel surface. The two-phase GUI defers translucent draws into a
         * separate depth-tested pass that runs AFTER opaque geometry, so an interior translucent fill
         * (the old stub) re-emerged wherever opaque content didn't cover it — leaking primary colour
         * around the panel borders. Drawing only the exterior halo avoids that entirely. */
        if (offset <= 0)
        {
            return;
        }

        for (int i = 1; i <= offset; i++)
        {
            int color = Colors.lerp(opaque, shadow, (float) i / offset);

            this.outline(left - i, top - i, right + i, bottom + i, color, 1);
        }
    }

    /* Gradients */

    public void gradientHBox(float x1, float y1, float x2, float y2, int leftColor, int rightColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void gradientVBox(float x1, float y1, float x2, float y2, int topColor, int bottomColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void dropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
        /* TODO(1.21.11 render): circular feathered shadow was a custom TRIANGLE_FAN mesh; no native
         * two-phase-GUI primitive. Skipped for the prototype (purely decorative). */
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
        /* TODO(1.21.11 render): circular feathered shadow was a custom TRIANGLE_FAN/TRIANGLES mesh; no
         * native two-phase-GUI primitive. Skipped for the prototype (purely decorative). */
    }

    /* Outline methods */

    public void outlineCenter(float x, float y, float offset, int color)
    {
        this.outlineCenter(x, y, offset, color, 1);
    }

    public void outlineCenter(float x, float y, float offset, int color, int border)
    {
        this.outline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public void outline(float x1, float y1, float x2, float y2, int color)
    {
        this.outline(x1, y1, x2, y2, color, 1);
    }

    /**
     * Draw rectangle outline with given border.
     */
    public void outline(float x1, float y1, float x2, float y2, int color, int border)
    {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    /* Icon */

    /** In the light theme white foreground (text/icons) becomes black; other colours pass through. */
    private static int darkenWhite(int color)
    {
        return (color & 0xFFFFFF) == 0xFFFFFF ? (color & 0xFF000000) : color;
    }

    public void icon(Icon icon, float x, float y)
    {
        this.icon(icon, Colors.WHITE, x, y);
    }

    public void icon(Icon icon, int color, float x, float y)
    {
        this.icon(icon, color, x, y, 0F, 0F);
    }

    public void icon(Icon icon, float x, float y, float ax, float ay)
    {
        this.icon(icon, Colors.WHITE, x, y, ax, ay);
    }

    public void icon(Icon icon, int color, float x, float y, float ax, float ay)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        this.texturedArea(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, w, h, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
    }

    public void outlinedIcon(Icon icon, float x, float y, float ax, float ay)
    {
        this.outlinedIcon(icon, x, y, Colors.WHITE, ax, ay);
    }

    /**
     * Draw an icon with a black outline.
     */
    public void outlinedIcon(Icon icon, float x, float y, int color, float ax, float ay)
    {
        this.icon(icon, Colors.A100, x - 1, y, ax, ay);
        this.icon(icon, Colors.A100, x + 1, y, ax, ay);
        this.icon(icon, Colors.A100, x, y - 1, ax, ay);
        this.icon(icon, Colors.A100, x, y + 1, ax, ay);
        this.icon(icon, color, x, y, ax, ay);
    }

    /* Textured box */

    public void fullTexturedBox(Texture texture, float x, float y, float w, float h)
    {
        this.fullTexturedBox(texture, Colors.WHITE, x, y, w, h);
    }

    public void fullTexturedBox(Texture texture, int color, float x, float y, float w, float h)
    {
        this.texturedBox(texture, color, x, y, w, h, 0, 0, w, h, (int) w, (int) h);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2)
    {
        this.texturedBox(texture, color, x, y, w, h, u1, v1, u2, v2, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u, float v)
    {
        this.texturedBox(texture, color, x, y, w, h, u, v, u + w, v + h, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        Identifier id = AdoptedTexture.identifier(texture);

        if (id == null)
        {
            return;
        }

        /* GUI_TEXTURED multiplies the sampled texel by the vertex color; alpha 0 would make the icon
         * invisible, so promote to opaque (mirrors the text path). */
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        /* drawTexture(pipeline, id, x, y, u, v, width, height, regionW, regionH, texW, texH, color):
         * vanilla computes u1=u/texW, u2=(u+regionW)/texW, v1=v/texH, v2=(v+regionH)/texH, so the
         * region sizes are the sampled span in texels (signed - negative flips the axis). */
        this.context.drawTexture(RenderPipelines.GUI_TEXTURED, id,
            (int) x, (int) y, u1, v1, (int) w, (int) h,
            (int) (u2 - u1), (int) (v2 - v1), textureW, textureH, color);
    }

    /**
     * Raw-GL-id overload: the callers (film / picker / model-block in-panel previews) hand us a
     * framebuffer color-attachment GL id directly. Bridged through {@link AdoptedTexture} (zero-copy)
     * and drawn via the same two-phase {@code context.drawTexture} path as the {@link Texture} overload.
     *
     * <p>FBO color textures are bottom-up (V-flipped); callers already pass {@code v1=height, v2=0} to
     * flip, which yields a negative {@code (v2 - v1)} region span (vanilla flips the V axis). We do NOT
     * add a flip here - the u/v are plumbed through faithfully. Previews use LINEAR sampling.</p>
     */
    public void texturedBox(int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        /* TODO(1.21.11 render): verify at runtime. */
        this.drawAdoptedGlTexture(texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    /**
     * @deprecated 1.21.5 removed the per-draw shader-program supplier; the {@code shader} argument is
     * ignored (the bridge always uses {@code GUI_TEXTURED}). Kept for source compatibility with the
     * 1.21.1 callers. The real texture is the {@code int} id, so it is routed through the same path as
     * {@link #texturedBox(int, int, float, float, float, float, float, float, float, float, int, int)}.
     *
     * TODO(1.21.11 render): callers that supplied a CUSTOM pipeline (multilink pixelate/erase atlas,
     * subtitle blur) lose their per-draw uniforms/samplers here - that is the separate already-documented
     * "re-wire custom GUI shaders" TODO at those call sites, not a regression of this bridge.
     */
    @Deprecated
    public void texturedBox(Supplier<RenderPipeline> shader, int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        /* TODO(1.21.11 render): verify at runtime. The Supplier (pipeline selector) is ignored. */
        this.drawAdoptedGlTexture(texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    /**
     * Shared draw path for the raw-GL-id overloads: adopt {@code glId} via {@link AdoptedTexture} and
     * composite it through {@code context.drawTexture} exactly like the {@link Texture} overload. The
     * region span is signed ({@code u2 - u1} / {@code v2 - v1}); a negative span flips that axis.
     */
    private void drawAdoptedGlTexture(int glId, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        Identifier id = AdoptedTexture.identifier(glId, textureW, textureH, true);

        if (id == null)
        {
            return;
        }

        /* GUI_TEXTURED multiplies the sampled texel by the vertex color; alpha 0 would make it
         * invisible, so promote to opaque (mirrors the Texture overload / text path). */
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawTexture(RenderPipelines.GUI_TEXTURED, id,
            (int) x, (int) y, u1, v1, (int) w, (int) h,
            (int) (u2 - u1), (int) (v2 - v1), textureW, textureH, color);
    }

    private void fillTexturedBox(BufferBuilder builder, Matrix3x2fc matrix, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        builder.vertex(matrix, x, y + h).texture(u1 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y + h).texture(u2 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y).texture(u2 / (float) textureW, v1 / (float) textureH).color(color);
        builder.vertex(matrix, x, y + h).texture(u1 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y).texture(u2 / (float) textureW, v1 / (float) textureH).color(color);
        builder.vertex(matrix, x, y).texture(u1 / (float) textureW, v1 / (float) textureH).color(color);
    }

    /* Repeatable textured box */

    public void texturedArea(Texture texture, int color, float x, float y, float w, float h, float u, float v, float tileW, float tileH, int tw, int th)
    {
        if (tileW <= 0 || tileH <= 0)
        {
            return;
        }

        /* Tile the (tileW x tileH) region at [u,v] across the (w x h) area, clipping the trailing
         * partial tiles. Each tile is one composited drawTexture (see texturedBox). */
        for (float dy = 0; dy < h; dy += tileH)
        {
            float ph = Math.min(tileH, h - dy);

            for (float dx = 0; dx < w; dx += tileW)
            {
                float pw = Math.min(tileW, w - dx);

                this.texturedBox(texture, color, x + dx, y + dy, pw, ph, u, v, u + pw, v + ph, tw, th);
            }
        }
    }

    /* Text with default font */

    public void text(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, false);
    }

    public void text(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, false);
    }

    public void textShadow(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, true);
    }

    public void textShadow(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, true);
    }

    public void text(String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.isLightTheme())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    /** Actual text draw (theming is applied by the public text() before calling this). */
    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawText(this.font.getRenderer(), label, (int) x, (int) y, color, shadow);
    }

    /* Text helpers */

    public int wallText(String text, int x, int y, int color, int width)
    {
        return this.wallText(text, x, y, color, width, 12);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight)
    {
        return this.wallText(text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        return wallText(text, x, y, color, width, lineHeight, ax, ay, true);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay, boolean shadow)
    {
        List<String> list = this.font.wrap(text, width);
        int h = (lineHeight * (list.size() - 1)) + this.font.getHeight();

        y -= h * ay;

        for (String string : list)
        {
            this.text(string.toString(), (int) (x + (width - this.font.getWidth(string)) * ax), y, color, shadow);

            y += lineHeight;
        }

        return h;
    }

    public void textCard(String text, float x, float y)
    {
        this.textCard(text, x, y, Colors.WHITE, Colors.A50);
    }

    /**
     * In this context, text card is a text with some background behind it
     */
    public void textCard(String text, float x, float y, int color, int background)
    {
        this.textCard(text, x, y, color, background, 3);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset)
    {
        this.textCard(text, x, y, color, background, offset, true);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            if (BBSSettings.isLightTheme() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            this.box(x - offset, y - offset, x + this.font.getWidth(text) + offset - 1, y + this.font.getHeight() + offset, background);
        }

        this.text(text, x, y, color, shadow);
    }

    public void flush()
    {
        /* TODO(1.21.11 render): DrawContext.draw() was removed in the 1.21.6 two-phase GUI; the
         * engine flushes deferred GUI draws itself. Kept as a no-op for source compatibility. */
    }
}
