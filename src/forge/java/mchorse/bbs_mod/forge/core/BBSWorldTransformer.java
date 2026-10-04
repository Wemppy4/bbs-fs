package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Capture the actual old block and tile NBT before vanilla replaces either one.
 * Runs after Forge's remapper, covers commands and mod callers as well as clicks. */
public final class BBSWorldTransformer implements IClassTransformer
{
    @Override public byte[] transform(String name,String transformedName,byte[] bytes)
    {
        if(bytes==null||!"net.minecraft.world.World".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=false;
        for(MethodNode method:node.methods)
        {
            if(!(method.name.equals("setBlockState")||method.name.equals("func_180501_a")) ||
                !method.desc.equals("(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"))continue;
            InsnList entry=new InsnList();
            entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(new VarInsnNode(Opcodes.ALOAD,1));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mchorse/bbs_mod/forge/FilmWorldHooks","beforeSetBlock",
                "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",false));
            method.instructions.insert(entry);changed=true;
        }
        if(!changed)throw new IllegalStateException("BBS damage-control could not locate World.setBlockState");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
