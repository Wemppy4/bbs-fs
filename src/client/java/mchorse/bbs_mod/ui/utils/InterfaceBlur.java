package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.memory.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.joml.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Blurs what is on screen under an overlay panel, on top of the dimming — the way the game's
 * own menus do it from 1.21 on. Two passes of a box blur over the main framebuffer, with the
 * overlay landing sharp on top.
 *
 * <p>The GUI in 1.21.11 is deferred: {@code Batcher2D} only records into the
 * {@link net.minecraft.client.gui.render.state.GuiRenderState}, and nothing reaches the
 * framebuffer until {@code GuiRenderer.render()} composites it after {@code Screen.render}
 * returns. Blurring the framebuffer at the moment an overlay paints would therefore blur the
 * bare world and leave every panel drawn so far sharp on top of it. So this class works the
 * way vanilla's own {@code DrawContext.applyBlur()} does — as a marker: {@link #apply} opens a
 * fresh root layer and records the blur there, and {@code GuiRenderer.renderPreparedDraws}
 * composites the layers before the marker, runs the blur (its call to
 * {@code GameRenderer.renderBlur} is redirected into {@link #render}), then draws the rest.
 * Everything recorded before the call ends up under the glass, the caller's own dim and
 * chrome on top.</p>
 *
 * <p>The effect is built in code rather than read from a json, so the radius is a live value
 * from the settings. 1.21.1 could load a bare json and add the passes by hand afterwards; in
 * 1.21.11 a {@link PostEffectProcessor} is parsed whole from a {@link PostEffectPipeline} and
 * carries its uniform VALUES with it, so a json would have to name one fixed radius. Assembling
 * the pipeline here keeps the setting, at the cost of rebuilding when it changes — which is
 * exactly as often as the user drags the slider.</p>
 *
 * <p>Once per frame, and this time without exception: the render state holds a single blur
 * layer, and {@code GuiRenderState.applyBlur} throws on a second one. A panel that blurred the
 * world under itself and an overlay that comes up over that panel share one pass.</p>
 */
public class InterfaceBlur
{
    /** Screen-sized scratch target: the horizontal pass writes it, the vertical pass reads it back. */
    private static final Identifier SWAP = Identifier.of(BBSMod.MOD_ID, "swap");

    /** Vanilla's screen-quad vertex shader; there is nothing mod-specific about a full-screen pass. */
    private static final Identifier SCREEN_QUAD = Identifier.of("minecraft", "core/screenquad");

    /** Vanilla's box blur, minus the alpha averaging — see the shader for why. */
    private static final Identifier BOX_BLUR = Identifier.of(BBSMod.MOD_ID, "post/box_blur_opaque");

    private static PostEffectProcessor processor;

    /** Owned by us because {@link PostEffectProcessor#parseEffect} takes one and ShaderLoader's is private. */
    private static ProjectionMatrix2 projection;

    /** The radius baked into the built effect; a different one means a rebuild. */
    private static float builtRadius = Float.NaN;

    /** Whether this frame's render state carries our blur marker, waiting for {@link #render}. */
    private static boolean marked;

    /** Set when the effect failed to build, so a broken shader costs one stack trace, not one per frame. */
    private static boolean broken;

    /** Called where the frame's interface rendering starts. */
    public static void beginFrame()
    {
        marked = false;
    }

    /**
     * Mark the blur layer: everything recorded before lands under the blur, the caller's own
     * draws (its dim, its chrome) go into the fresh root layer on top. Does nothing when the
     * frame is marked already, the effect is broken or the setting is off.
     */
    public static void apply(Batcher2D batcher)
    {
        if (marked || broken || !BBSSettings.interfaceBlur.get())
        {
            return;
        }

        marked = true;

        batcher.newRootLayer();
        batcher.applyBlur();
    }

    /**
     * The world under a panel that paints its own background over it (morphing, the texture
     * manager). Same marker as {@link #apply}: vanilla allows one blur per frame, so an overlay
     * that comes up over the panel later rides this one rather than getting a pass of its own.
     */
    public static void applyUnder(Batcher2D batcher)
    {
        apply(batcher);
    }

    /**
     * {@code GuiRenderer}'s blur slot, reached from the redirect in {@code GuiRendererMixin} when
     * it composites up to the marked layer. The framebuffer holds the world and every layer
     * before the marker at this point, which is exactly the picture to blur.
     *
     * @return false when this frame has no BBS blur, so vanilla's own {@code renderBlur} runs instead.
     */
    public static boolean render()
    {
        if (!marked)
        {
            return false;
        }

        marked = false;

        MinecraftClient mc = MinecraftClient.getInstance();
        float radius = BBSSettings.interfaceBlurRadius.get();

        if (processor == null || radius != builtRadius)
        {
            if (!rebuild(mc, radius))
            {
                return true;
            }
        }

        /* The frame graph sizes the swap target off the framebuffer and puts the result back into
         * it, so the blend/framebuffer/texture-unit restoration 1.21.1 had to do by hand afterwards
         * has nothing left to undo — a render pass owns its own state now. */
        processor.render(mc.getFramebuffer(), ObjectAllocator.TRIVIAL);

        return true;
    }

    private static boolean rebuild(MinecraftClient mc, float radius)
    {
        close();

        try
        {
            /* The same near/far/invert ShaderLoader builds its own with, so our passes project
             * their screen quad exactly like every vanilla post effect does. */
            projection = new ProjectionMatrix2("bbs_interface_blur", 0.1F, 1000F, false);

            processor = PostEffectProcessor.parseEffect(pipeline(radius), mc.getTextureManager(),
                Set.of(PostEffectProcessor.MAIN), Identifier.of(BBSMod.MOD_ID, "interface_blur"), projection);

            builtRadius = radius;

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            close();
            broken = true;

            return false;
        }
    }

    /** Horizontal into the swap, vertical back into the main target — a separable box blur. */
    private static PostEffectPipeline pipeline(float radius)
    {
        return new PostEffectPipeline(
            Map.of(SWAP, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
            List.of(
                pass(PostEffectProcessor.MAIN, SWAP, 1F, 0F, radius),
                pass(SWAP, PostEffectProcessor.MAIN, 0F, 1F, radius)
            )
        );
    }

    /**
     * One blur pass. The uniform list is written into the {@code BlurConfig} std140 block in the
     * order given, so it has to match the shader's declaration — BlurDir then Radius. Bilinear
     * sampling is on because the shader halves its sample count by stepping between pixels.
     */
    private static PostEffectPipeline.Pass pass(Identifier in, Identifier out, float dirX, float dirY, float radius)
    {
        return new PostEffectPipeline.Pass(SCREEN_QUAD, BOX_BLUR,
            List.of(new PostEffectPipeline.TargetSampler("In", in, false, true)),
            out,
            Map.of("BlurConfig", List.of(
                new UniformValue.Vec2fValue(new Vector2f(dirX, dirY)),
                new UniformValue.FloatValue(radius)
            ))
        );
    }

    private static void close()
    {
        if (processor != null)
        {
            processor.close();
        }

        if (projection != null)
        {
            projection.close();
        }

        processor = null;
        projection = null;
        builtRadius = Float.NaN;
    }
}
