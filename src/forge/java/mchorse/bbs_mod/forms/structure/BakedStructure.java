package mchorse.bbs_mod.forms.structure;

import mchorse.bbs_mod.BBSResources;
import mchorse.bbs_mod.graphics.OptiFineBlockVertices;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Baked native block/fluid vertices. Mirrors Blockbuster's buffer replay while retaining AO. */
public final class BakedStructure
{
    private final StructureRenderWorld world;
    private final VertexFormat format;
    private final int generation;
    private final EnumMap<BlockRenderLayer,int[]> layers = new EnumMap<>(BlockRenderLayer.class);
    private BakedStructure(StructureRenderWorld world)
    {
        this.world=world;this.format=new VertexFormat(DefaultVertexFormats.BLOCK);this.generation=BBSResources.getAssetsVersion();
    }
    public boolean isValidFor(StructureRenderWorld world)
    {
        return this.world==world && generation==BBSResources.getAssetsVersion() && format.equals(DefaultVertexFormats.BLOCK);
    }
    public static BakedStructure bake(StructureRenderData data,StructureRenderWorld world)
    {
        BakedStructure result=new BakedStructure(world);
        /* A private builder cannot interrupt vanilla's chunk/item tessellator. No GL display lists. */
        BufferBuilder buffer=new BufferBuilder(0x20000);
        BlockRenderLayer previous=net.minecraftforge.client.MinecraftForgeClient.getRenderLayer();
        try
        {
            for(BlockRenderLayer layer:BlockRenderLayer.values())
            {
                ForgeHooksClient.setRenderLayer(layer);
                buffer.begin(GL11.GL_QUADS,result.format);
                for(Map.Entry<BlockPos,IBlockState> entry:data.getBlocks().entrySet())
                {
                    IBlockState state=entry.getValue();
                    if(state.getBlock().canRenderInLayer(state,layer))
                        try(OptiFineBlockVertices material=new OptiFineBlockVertices(state,entry.getKey(),world,buffer))
                        {Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state,entry.getKey(),world,buffer);}
                }
                OptiFineBlockVertices.finish(buffer);
                buffer.finishDrawing();
                int[] vertices=new int[buffer.getVertexCount()*result.format.getIntegerSize()];
                buffer.getByteBuffer().asIntBuffer().get(vertices);
                result.layers.put(layer,vertices);buffer.reset();
            }
        }
        finally{ForgeHooksClient.setRenderLayer(previous);}
        return result;
    }
    public int getVertexCount(){int n=0;for(int[] data:layers.values())n+=data.length/format.getIntegerSize();return n;}
    public void render(BlockRenderLayer layer)
    {
        int[] vertices=layers.get(layer);if(vertices==null||vertices.length==0)return;
        BufferBuilder builder=Tessellator.getInstance().getBuffer();
        try(OptiFineShaders.EntityPass pass=OptiFineShaders.isWorldPass()?OptiFineShaders.blocks(layer):null)
        {
            builder.begin(GL11.GL_QUADS,format);
            /* addVertexData overwrites mc_Entity in the passed array in OptiFine. A state replay
             * retains each baked block's material and never changes our cached vertices. */
            builder.setVertexState(builder.new State(vertices,format));
            Tessellator.getInstance().draw();
        }
    }
}
