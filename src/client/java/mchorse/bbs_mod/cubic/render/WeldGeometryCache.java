package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import net.minecraft.client.gl.VertexBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The CPU half of a welded model, kept on the GPU between draws.
 *
 * <p>A welded bone's geometry is a pure function of the pose (every bone's transform, colour and
 * lighting), the draw's light/overlay/colour and the shape keys — and it is baked in the model's
 * ROOT frame, so the same buffer serves every pass of a frame (world, stencil, Iris shadow) and
 * every frame in which nothing moved, drawn with the pass's own model-view. Before, that geometry
 * was re-tessellated in camera space on every pass and, for translucent textures, uploaded into a
 * fresh GL buffer that was freed at the end of the frame.</p>
 *
 * <p>Several entries, not one: the translucent queue borrows a buffer until its end-of-frame
 * flush, and a frame can bake more than one pose of the same model (onion skin). A buffer lent
 * out this frame is never rebuilt this frame; the ring grows to the number of concurrent poses,
 * capped — past the cap the caller falls back to the old owned, per-frame buffer.</p>
 */
public class WeldGeometryCache
{
    private static final int MAX_ENTRIES = 16;

    private final List<Entry> entries = new ArrayList<>();

    public static class Entry
    {
        public final VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);

        /** The bake this buffer holds; {@code valid} false means nothing usable. */
        public long key;
        public boolean valid;

        /** Which groups the bake tessellated — the VAO walk must skip exactly these. */
        public Set<ModelGroup> cpuGroups;

        /** Whether the bake emitted any geometry at all (an empty upload can't be drawn). */
        public boolean hasGeometry;

        /** The frame epoch the translucent queue last borrowed this buffer in. */
        public long lentEpoch = -1L;
    }

    public Entry find(long key)
    {
        for (Entry entry : this.entries)
        {
            if (entry.valid && entry.key == key)
            {
                return entry;
            }
        }

        return null;
    }

    /**
     * An entry to bake into: one not lent to the queue this frame, or a fresh one under the cap.
     * Null past the cap — the caller then bakes into a throwaway buffer the old way.
     */
    public Entry acquire(long epoch)
    {
        for (Entry entry : this.entries)
        {
            if (entry.lentEpoch != epoch)
            {
                return entry;
            }
        }

        if (this.entries.size() < MAX_ENTRIES)
        {
            Entry entry = new Entry();

            this.entries.add(entry);

            return entry;
        }

        return null;
    }

    /** Forget every bake (weld config changed) — buffers stay allocated for reuse, since the queue may still hold one. */
    public void invalidate()
    {
        for (Entry entry : this.entries)
        {
            entry.valid = false;
            entry.cpuGroups = null;
        }
    }

    public void delete()
    {
        for (Entry entry : this.entries)
        {
            entry.vbo.close();
        }

        this.entries.clear();
    }
}
