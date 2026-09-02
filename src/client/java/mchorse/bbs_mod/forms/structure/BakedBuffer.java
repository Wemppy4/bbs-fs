package mchorse.bbs_mod.forms.structure;

import java.nio.ByteBuffer;

/**
 * A baked layer's vertex data, copied out of the scratch builder into a tightly packed
 * {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL} template ({@link #STRIDE}-byte stride).
 *
 * <p>{@code data == null} means the builder produced vertices but in a format that misses the
 * standard block attributes (the caller skips the layer); a {@code null} {@link BakedBuffer}
 * means the builder was empty.</p>
 */
public record BakedBuffer(ByteBuffer data, int vertexCount)
{
    /** Vertex stride of {@code POSITION_COLOR_TEXTURE_LIGHT_NORMAL} (the block/fluid layer format). */
    public static final int STRIDE = 32;
}
