package mchorse.bbs_mod.graphics.render;

import static mchorse.bbs_mod.graphics.render.VertexFormat.Element.*;

public final class VertexFormats
{
    public static final VertexFormat POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL = new VertexFormat(POSITION, COLOR, TEXTURE, OVERLAY, LIGHT, NORMAL);
    public static final VertexFormat POSITION_TEXTURE_LIGHT_COLOR = new VertexFormat(POSITION, TEXTURE, LIGHT, COLOR);
    public static final VertexFormat POSITION_TEXTURE_COLOR = new VertexFormat(POSITION, TEXTURE, COLOR);
    public static final VertexFormat POSITION_COLOR_TEXTURE_LIGHT = new VertexFormat(POSITION, COLOR, TEXTURE, LIGHT);
    private VertexFormats() {}
}
