package mchorse.bbs_mod.graphics;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.slf4j.Logger;

/**
 * Save/restore of OpenGL's pixel <em>pack</em> state around a read-back
 * ({@code glReadPixels} / {@code glGetTexImage}).
 *
 * <p>The pack state is global and nothing in the pipeline puts it back. In 1.21.11 vanilla's
 * {@code GlCommandEncoder#copyTextureToBuffer} sets {@code GL_PACK_ROW_LENGTH} to the width of
 * whatever texture it downloads and leaves it there. Every read-back afterwards that sizes its
 * destination buffer as {@code width * height * pixel} then gets {@code rowLength} pixels per row
 * written into it instead — i.e. straight past the end of the buffer. That is what killed the
 * picker: its tolerance read is a WxH region (more than one row), so the driver overran the
 * buffer and the process died with EXCEPTION_ACCESS_VIOLATION inside {@code glReadPixels}. The
 * single-pixel pick never crashed for the same reason it is not a fix — one row is one row
 * whatever the stride says.</p>
 *
 * <p>A stray pixel-pack buffer binding is the same class of hazard (it silently reinterprets the
 * destination pointer as an offset into that buffer), so it is neutralised here too. Callers that
 * genuinely want a PBO bind theirs after {@link #push()}.</p>
 */
public class PixelPackState implements AutoCloseable
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** One-shot report of the first dirty pack state we walk into, to keep the diagnosis provable. */
    private static boolean reported;

    private final int rowLength;
    private final int skipPixels;
    private final int skipRows;
    private final int alignment;
    private final int packBuffer;

    /** Read-back with the default 4-byte row alignment (fits RGBA8 and RGBA float). */
    public static PixelPackState push()
    {
        return push(4);
    }

    /** Read-back with an explicit {@code GL_PACK_ALIGNMENT} (formats like BGR8 need 1). */
    public static PixelPackState push(int alignment)
    {
        PixelPackState state = new PixelPackState();

        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);

        return state;
    }

    private PixelPackState()
    {
        this.rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        this.skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        this.skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        this.alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        this.packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);

        if (!reported && (this.rowLength != 0 || this.skipPixels != 0 || this.skipRows != 0 || this.packBuffer != 0))
        {
            reported = true;

            LOGGER.info("[BBS gl] dirty pixel pack state on read-back: rowLength={} skipPixels={} skipRows={} alignment={} packBuffer={}",
                this.rowLength, this.skipPixels, this.skipRows, this.alignment, this.packBuffer);
        }
    }

    @Override
    public void close()
    {
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, this.rowLength);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, this.skipPixels);
        GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, this.skipRows);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, this.alignment);
        GL21.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, this.packBuffer);
    }
}
