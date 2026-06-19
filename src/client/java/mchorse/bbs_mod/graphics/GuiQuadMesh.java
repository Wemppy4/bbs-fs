package mchorse.bbs_mod.graphics;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jspecify.annotations.Nullable;

/**
 * A small {@link VertexConsumer} that RECORDS POSITION_COLOR quad vertices in screen space so they can be
 * replayed into the deferred GUI as a {@link SimpleGuiElementRenderState}.
 *
 * <p><b>Why this exists (1.21.11):</b> keyframe shapes / track bars used to be built into a {@code BufferBuilder}
 * and submitted with an immediate {@code RenderLayer.draw(builtBuffer)}. The 1.21.6+ GUI is two-phase: vanilla
 * RECORDS draws into a {@code GuiRenderState} during {@code Screen.render} and composites them afterwards, so an
 * immediate mid-frame {@code RenderLayer.draw} is overpainted by the deferred composite (and runs with the wrong
 * projection) — the keyframes were invisible. Vanilla's {@code DrawContext.fill} avoids that by recording a
 * {@link net.minecraft.client.gui.render.state.ColoredQuadGuiElementRenderState}; we mirror exactly that for
 * arbitrary keyframe quad geometry. The geometry the shape renderers emit is all 4-vertex groups (QUADS), which
 * is mandatory: {@code GuiRenderer} composites every simple element through the hard-wired QUADS sequential
 * index buffer, so TRIANGLE_STRIP/TRIANGLES cannot go through this path.</p>
 *
 * <p>Callers feed it through {@code vertex(Matrix3x2fc, x, y).color(argb)} (the same call sites as before); the 2D
 * pose is folded in by the {@code VertexConsumer} default, so the positions this records are already in screen
 * space. {@link mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D#drawQuadMesh} then submits the recorded
 * mesh as one {@link State} with the current GUI scissor.</p>
 */
public class GuiQuadMesh implements VertexConsumer
{
    private float[] xs = new float[64];
    private float[] ys = new float[64];
    private int[] colors = new int[64];
    private int count;

    private float minX = Float.POSITIVE_INFINITY;
    private float minY = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY;
    private float maxY = Float.NEGATIVE_INFINITY;

    public boolean isEmpty()
    {
        return this.count == 0;
    }

    public int count()
    {
        return this.count;
    }

    public float[] xs()
    {
        return this.xs;
    }

    public float[] ys()
    {
        return this.ys;
    }

    public int[] colors()
    {
        return this.colors;
    }

    /**
     * Axis-aligned bounds of the recorded geometry (used by the GUI layer system for z-ordering and culling),
     * intersected with the active scissor. Returns {@code null} when the geometry is fully clipped away.
     */
    @Nullable
    public ScreenRect computeBounds(@Nullable ScreenRect scissorArea)
    {
        if (this.count == 0)
        {
            return null;
        }

        int x = (int) Math.floor(this.minX);
        int y = (int) Math.floor(this.minY);
        int w = (int) Math.ceil(this.maxX) - x;
        int h = (int) Math.ceil(this.maxY) - y;

        ScreenRect bounds = new ScreenRect(x, y, Math.max(0, w), Math.max(0, h));

        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }

    private void ensureCapacity()
    {
        if (this.count < this.xs.length)
        {
            return;
        }

        int next = this.xs.length * 2;
        float[] nx = new float[next];
        float[] ny = new float[next];
        int[] nc = new int[next];

        System.arraycopy(this.xs, 0, nx, 0, this.count);
        System.arraycopy(this.ys, 0, ny, 0, this.count);
        System.arraycopy(this.colors, 0, nc, 0, this.count);

        this.xs = nx;
        this.ys = ny;
        this.colors = nc;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        this.ensureCapacity();

        this.xs[this.count] = x;
        this.ys[this.count] = y;
        this.colors[this.count] = -1;
        this.count++;

        if (x < this.minX) this.minX = x;
        if (y < this.minY) this.minY = y;
        if (x > this.maxX) this.maxX = x;
        if (y > this.maxY) this.maxY = y;

        return this;
    }

    @Override
    public VertexConsumer color(int argb)
    {
        if (this.count > 0)
        {
            this.colors[this.count - 1] = argb;
        }

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this.color((alpha << 24) | (red << 16) | (green << 8) | blue);
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this;
    }

    @Override
    public VertexConsumer lineWidth(float width)
    {
        return this;
    }

    /**
     * The recorded mesh as a deferred GUI element, mirroring vanilla {@code ColoredQuadGuiElementRenderState}
     * (POSITION_COLOR, QUADS). The vertex arrays are stored in screen space (the pose is already folded in),
     * so {@link #setupVertices} emits them directly at composite time.
     */
    public record State(
        RenderPipeline pipeline,
        TextureSetup textureSetup,
        float[] xs,
        float[] ys,
        int[] colors,
        int count,
        @Nullable ScreenRect scissorArea,
        @Nullable ScreenRect bounds
    ) implements SimpleGuiElementRenderState
    {
        @Override
        public void setupVertices(VertexConsumer vertices)
        {
            for (int i = 0; i < this.count; i++)
            {
                vertices.vertex(this.xs[i], this.ys[i], 0.0F).color(this.colors[i]);
            }
        }
    }
}
