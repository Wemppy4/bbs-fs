package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public final class BBSActionTransformer implements IClassTransformer
{
    private static final String HOOK="mchorse/bbs_mod/forge/FilmActionHooks";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes)
    {
        boolean item="net.minecraft.item.ItemStack".equals(transformedName),world="net.minecraft.world.World".equals(transformedName);
        if(bytes==null||!item&&!world)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int count=0;
        for(MethodNode method:node.methods)
        {
            if(item&&(method.name.equals("onItemUse")||method.name.equals("func_179546_a"))
                && method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/EnumActionResult;"))
            {
                InsnList hook=new InsnList();for(int i=0;i<6;i++)hook.add(new VarInsnNode(Opcodes.ALOAD,i));
                for(int i=6;i<9;i++)hook.add(new VarInsnNode(Opcodes.FLOAD,i));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"useBlock","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)V",false));
                method.instructions.insert(hook);count++;
            }
            if(world&&(method.name.equals("sendBlockBreakProgress")||method.name.equals("func_175715_c"))&&method.desc.equals("(ILnet/minecraft/util/math/BlockPos;I)V"))
            {
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ILOAD,1));hook.add(new VarInsnNode(Opcodes.ALOAD,2));hook.add(new VarInsnNode(Opcodes.ILOAD,3));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"breaking","(Lnet/minecraft/world/World;ILnet/minecraft/util/math/BlockPos;I)V",false));method.instructions.insert(hook);count++;
            }
        }
        if(count!=1)throw new IllegalStateException("BBS action capture boundary missing in "+transformedName+": "+count);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
