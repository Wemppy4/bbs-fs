package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;

public class NativeFormVerticesTest
{
    private static BufferBuilder vertex(VertexFormat format)
    {
        BufferBuilder buffer = new BufferBuilder(128);buffer.begin(7,format);
        buffer.pos(1,2,3).color(200,100,50,240).tex(.25,.75).lightmap(160,192).endVertex();
        buffer.finishDrawing();return buffer;
    }
    @Test public void tintAndLightUseActualStrideAndPreserveOtherAttributes()
    {
        VertexFormat format=new VertexFormat(DefaultVertexFormats.BLOCK);
        format.addElement(new VertexFormatElement(0,VertexFormatElement.EnumType.FLOAT,VertexFormatElement.EnumUsage.PADDING,7));
        BufferBuilder buffer=vertex(format);int[] cached=buffer.getVertexState().getRawBuffer().clone();
        try(NativeFormVertices scope=new NativeFormVertices(new Color(.5F,1F,0F,.25F),0x00f00040,true,null))
        {NativeFormVertices.beforeUpload(buffer);}
        ByteBuffer bytes=buffer.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());int c=format.getColorOffset(),l=format.getUvOffsetById(1);
        assertEquals(100,bytes.get(c)&255);assertEquals(100,bytes.get(c+1)&255);assertEquals(0,bytes.get(c+2)&255);assertEquals(60,bytes.get(c+3)&255);
        /* The native SHORT lightmap writer stores its second argument first (block, sky). */
        assertEquals(192,bytes.getShort(l)&65535);assertEquals(240,bytes.getShort(l+2)&65535);
        assertEquals(1F,bytes.getFloat(0),0);assertEquals(.25F,bytes.getFloat(format.getUvOffsetById(0)),0);
        assertEquals(200,cached[c/4]&255);
        byte color=bytes.get(c);NativeFormVertices.beforeUpload(buffer);assertEquals(color,bytes.get(c));
    }
    @Test public void nestedScopeRestoresParentAndUsesFixedLightWhenRequested()
    {
        BufferBuilder first=vertex(DefaultVertexFormats.BLOCK),second=vertex(DefaultVertexFormats.BLOCK);
        try(NativeFormVertices parent=new NativeFormVertices(new Color(.5F,1,1,1),0x00500030,false,null))
        {
            try(NativeFormVertices child=new NativeFormVertices(new Color(1,.5F,1,1),0x00900020,false,null))
            {NativeFormVertices.beforeUpload(first);}
            NativeFormVertices.beforeUpload(second);
        }
        int c=DefaultVertexFormats.BLOCK.getColorOffset(),l=DefaultVertexFormats.BLOCK.getUvOffsetById(1);
        assertEquals(200,first.getByteBuffer().get(c)&255);assertEquals(50,first.getByteBuffer().get(c+1)&255);
        assertEquals(100,second.getByteBuffer().get(c)&255);assertEquals(48,second.getByteBuffer().getShort(l)&65535);
    }
}
