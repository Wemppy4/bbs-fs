package mchorse.bbs_mod.graphics.render;

public final class Tessellator
{
    private static final Tessellator INSTANCE = new Tessellator();
    private final BufferBuilder buffer = new BufferBuilder();
    public static Tessellator getInstance() { return INSTANCE; }
    public BufferBuilder getBuffer() { return this.buffer; }
}
