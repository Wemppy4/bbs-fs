package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow replacements for the original player arm and eye-height mixins. */
public final class BBSMorphTransformer implements IClassTransformer
{
    public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        boolean player = "net.minecraft.entity.player.EntityPlayer".equals(transformedName);
        boolean renderer = "net.minecraft.client.renderer.entity.RenderPlayer".equals(transformedName);
        if (bytes == null || !player && !renderer) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods)
        {
            if (player && (method.name.equals("getEyeHeight") || method.name.equals("func_70047_e")) && method.desc.equals("()F"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (instruction.getOpcode() != Opcodes.FRETURN) continue;
                    InsnList hook = new InsnList();
                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new InsnNode(Opcodes.SWAP));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mchorse/bbs_mod/forge/MorphPlayerHooks", "eyeHeight", "(Lnet/minecraft/entity/player/EntityPlayer;F)F", false));
                    method.instructions.insertBefore(instruction, hook);
                }
                changed++;
            }
            boolean right = method.name.equals("renderRightArm") || method.name.equals("func_177138_b");
            boolean left = method.name.equals("renderLeftArm") || method.name.equals("func_177139_c");
            if (renderer && (right || left) && method.desc.equals("(Lnet/minecraft/client/entity/AbstractClientPlayer;)V"))
            {
                InsnList hook = new InsnList();
                LabelNode vanilla = new LabelNode();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new InsnNode(right ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mchorse/bbs_mod/client/renderer/MorphRenderer", "renderArm", "(Lnet/minecraft/client/entity/AbstractClientPlayer;Z)Z", false));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
                hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(vanilla);
                hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(hook);
                changed++;
            }
        }
        if (changed != (player ? 1 : 2)) throw new IllegalStateException("BBS morph hooks did not match " + transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
