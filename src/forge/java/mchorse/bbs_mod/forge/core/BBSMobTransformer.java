package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Apply BBS poses after vanilla animation, at the same boundary as modern ModelPart hooks. */
public final class BBSMobTransformer implements IClassTransformer
{
    public byte[] transform(String name,String transformedName,byte[] bytes)
    {
        if(bytes==null||!"net.minecraft.client.model.ModelRenderer".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int changed=0;
        for(MethodNode method:node.methods)
        {
            if(!method.desc.equals("(F)V"))continue;
            int mode=method.name.equals("render")||method.name.equals("func_78785_a")?0:
                method.name.equals("renderWithRotation")||method.name.equals("func_78791_b")?1:
                method.name.equals("postRender")||method.name.equals("func_78794_c")?2:-1;
            if(mode<0)continue;
            InsnList hook=new InsnList();LabelNode vanilla=new LabelNode();
            hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.FLOAD,1));hook.add(new InsnNode(Opcodes.ICONST_0+mode));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"mchorse/bbs_mod/forms/renderers/mob/NativeMobRenderContext","renderPart","(Lnet/minecraft/client/model/ModelRenderer;FI)Z",false));
            hook.add(new JumpInsnNode(Opcodes.IFEQ,vanilla));hook.add(new InsnNode(Opcodes.RETURN));hook.add(vanilla);hook.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
            method.instructions.insert(hook);changed++;
        }
        if(changed!=3)throw new IllegalStateException("BBS mob hooks did not match ModelRenderer: "+changed);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
