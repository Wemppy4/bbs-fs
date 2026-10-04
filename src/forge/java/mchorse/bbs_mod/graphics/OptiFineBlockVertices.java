package mchorse.bbs_mod.graphics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import java.lang.reflect.Method;

/** The same material IDs/tangent calculation used by OptiFine's native chunk baker. */
public final class OptiFineBlockVertices implements AutoCloseable
{
    private static Method push, pop, normals;
    private final BufferBuilder buffer;
    public OptiFineBlockVertices(IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder buffer)
    {
        this.buffer = OptiFineShaders.isLoaded() ? buffer : null;
        if (this.buffer == null) return;
        ensure(); call(push,state,pos,world,buffer);
    }
    public static void finish(BufferBuilder buffer) { if (OptiFineShaders.isLoaded()) { ensure(); call(normals,buffer); } }
    private static void ensure()
    {
        if (push != null) return;
        try
        {
            Class<?> type=Class.forName("net.optifine.shaders.SVertexBuilder");
            push=type.getMethod("pushEntity",IBlockState.class,BlockPos.class,IBlockAccess.class,BufferBuilder.class);
            pop=type.getMethod("popEntity",BufferBuilder.class);
            normals=type.getMethod("calcNormalChunkLayer",BufferBuilder.class);
        }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("OptiFine native block vertex API changed",e); }
    }
    private static void call(Method method,Object... args)
    {
        try { method.invoke(null,args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("OptiFine native block vertices",e); }
    }
    @Override public void close() { if (buffer != null) call(pop,buffer); }
}
