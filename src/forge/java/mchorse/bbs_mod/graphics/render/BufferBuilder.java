package mchorse.bbs_mod.graphics.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.BufferUtils;

/** CPU triangle stream retaining the attributes used by bone picking and materials. */
public final class BufferBuilder
{
    private ByteBuffer bytes = BufferUtils.createByteBuffer(65536);
    private VertexFormat format;
    private VertexFormat.DrawMode mode;
    private boolean building;
    private int count, overlay, lightU, lightV;
    private float x, y, z, r = 1, g = 1, b = 1, a = 1, u, v, nx, ny = 1, nz;

    public void begin(VertexFormat.DrawMode mode, VertexFormat format)
    {
        if (this.building) throw new IllegalStateException("Already building BBS geometry");
        this.mode = mode; this.format = format; this.count = 0; this.bytes.clear(); this.building = true;
        this.x = this.y = this.z = this.u = this.v = this.nx = this.nz = 0;
        this.r = this.g = this.b = this.a = this.ny = 1;
        this.overlay = 10 << 16; this.lightU = this.lightV = 240;
    }
    public BufferBuilder vertex(double x, double y, double z) { this.x=(float)x; this.y=(float)y; this.z=(float)z; return this; }
    public BufferBuilder color(float r, float g, float b, float a) { this.r=r; this.g=g; this.b=b; this.a=a; return this; }
    public BufferBuilder texture(float u, float v) { this.u=u; this.v=v; return this; }
    public BufferBuilder overlay(int packed) { this.overlay=packed; return this; }
    public BufferBuilder light(int u, int v) { this.lightU=u; this.lightV=v; return this; }
    public BufferBuilder light(int packed) { return this.light(packed & 65535, packed >>> 16); }
    public BufferBuilder normal(float x, float y, float z) { this.nx=x; this.ny=y; this.nz=z; return this; }
    public void next()
    {
        if (!this.building) throw new IllegalStateException("Not building BBS geometry");
        if (this.bytes.remaining() < this.format.getVertexSizeByte())
        {
            ByteBuffer grown = BufferUtils.createByteBuffer(Math.max(this.bytes.capacity() * 2, this.bytes.position()+this.format.getVertexSizeByte()));
            this.bytes.flip(); grown.put(this.bytes); this.bytes=grown;
        }
        for (VertexFormat.Element element : this.format.elements) switch (element)
        {
            case POSITION: this.bytes.putFloat(this.x).putFloat(this.y).putFloat(this.z); break;
            case COLOR: this.bytes.putFloat(this.r).putFloat(this.g).putFloat(this.b).putFloat(this.a); break;
            case TEXTURE: this.bytes.putFloat(this.u).putFloat(this.v); break;
            case OVERLAY: this.bytes.putInt(this.overlay & 65535).putInt(this.overlay >>> 16); break;
            case LIGHT: this.bytes.putInt(this.lightU).putInt(this.lightV); break;
            case NORMAL: this.bytes.putFloat(this.nx).putFloat(this.ny).putFloat(this.nz); break;
        }
        this.count++;
    }
    public BuiltBuffer end()
    {
        if (!this.building) throw new IllegalStateException("Not building BBS geometry");
        this.building = false; this.bytes.flip();
        ByteBuffer owned = BufferUtils.createByteBuffer(this.bytes.remaining()); owned.put(this.bytes).flip();
        return new BuiltBuffer(owned, this.format, this.mode, this.count);
    }
    public boolean isBuilding() { return this.building; }
    public static final class BuiltBuffer
    {
        public final VertexFormat format;
        public final VertexFormat.DrawMode mode;
        public final int count;
        private final ByteBuffer data;
        private BuiltBuffer(ByteBuffer data, VertexFormat format, VertexFormat.DrawMode mode, int count)
        { this.data=data; this.format=format; this.mode=mode; this.count=count; }
        public ByteBuffer getBuffer() { return this.data.asReadOnlyBuffer().order(ByteOrder.nativeOrder()); }
    }
}
