package mchorse.bbs_mod.graphics.render;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import org.lwjgl.opengl.GL11;

/** Explicit BBS vertex layouts; integer overlay/light attributes are not discarded. */
public final class VertexFormat
{
    public enum DrawMode
    {
        TRIANGLES(GL11.GL_TRIANGLES), QUADS(GL11.GL_QUADS), LINES(GL11.GL_LINES),
        LINE_STRIP(GL11.GL_LINE_STRIP), TRIANGLE_STRIP(GL11.GL_TRIANGLE_STRIP);
        public final int glMode;
        DrawMode(int glMode) { this.glMode = glMode; }
    }

    public enum Element
    {
        POSITION("Position", 3, GL11.GL_FLOAT, false, false),
        COLOR("Color", 4, GL11.GL_FLOAT, false, false),
        TEXTURE("UV0", 2, GL11.GL_FLOAT, false, false),
        OVERLAY("UV1", 2, GL11.GL_INT, false, true),
        LIGHT("UV2", 2, GL11.GL_INT, false, true),
        NORMAL("Normal", 3, GL11.GL_FLOAT, false, false);
        public final String name;
        public final int count, type;
        public final boolean normalized, integer;
        Element(String name, int count, int type, boolean normalized, boolean integer)
        { this.name = name; this.count = count; this.type = type; this.normalized = normalized; this.integer = integer; }
        public int bytes() { return this.count * 4; }
    }

    public final List<Element> elements;
    private final int stride;
    public VertexFormat(Element... elements)
    {
        this.elements = Collections.unmodifiableList(Arrays.asList(elements.clone()));
        int bytes = 0;
        for (Element element : elements) bytes += element.bytes();
        this.stride = bytes;
    }
    public int getVertexSizeByte() { return this.stride; }
    public int attribute(String name)
    {
        for (int i = 0; i < this.elements.size(); i++) if (this.elements.get(i).name.equals(name)) return i;
        return -1;
    }
}
