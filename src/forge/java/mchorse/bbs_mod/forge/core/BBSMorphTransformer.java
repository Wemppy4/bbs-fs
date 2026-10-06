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
        boolean manager = "net.minecraft.client.renderer.entity.RenderManager".equals(transformedName);
        boolean localPlayer = "net.minecraft.client.entity.EntityPlayerSP".equals(transformedName);
        if (bytes == null || !player && !renderer && !manager && !localPlayer) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int changed = 0;
        for (MethodNode method : node.methods)
        {
            if (renderer && (method.name.equals("doRender") || method.name.equals("func_76986_a"))
                && method.desc.equals("(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (instruction instanceof MethodInsnNode)
                    {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if ((call.name.equals("isUser") || call.name.equals("func_175144_cb")) && call.desc.equals("()Z"))
                        {
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                                "mchorse/bbs_mod/client/renderer/MorphRenderer", "isFirstPersonUser",
                                "(Lnet/minecraft/client/entity/AbstractClientPlayer;)Z", false));
                            changed++;
                        }
                    }
                }
            }
            if (manager && (method.name.equals("renderEntity") || method.name.equals("func_188391_a"))
                && method.desc.equals("(Lnet/minecraft/entity/Entity;DDDFFZ)V"))
            {
                InsnList hook = new InsnList(); LabelNode visible = new LabelNode();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mchorse/bbs_mod/client/renderer/MorphRenderer", "shouldHide", "(Lnet/minecraft/entity/Entity;)Z", false));
                hook.add(new JumpInsnNode(Opcodes.IFEQ, visible)); hook.add(new InsnNode(Opcodes.RETURN));
                hook.add(visible); hook.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(hook); changed++;
            }
            if (localPlayer && (method.name.equals("isCurrentViewEntity") || method.name.equals("func_175160_A")) && method.desc.equals("()Z"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode() == Opcodes.IRETURN)
                {
                    InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "mchorse/bbs_mod/forge/FilmControlInput", "isControlledPlayer", "(Lnet/minecraft/entity/Entity;)Z", false));
                    hook.add(new InsnNode(Opcodes.IOR)); method.instructions.insertBefore(instruction, hook);
                }
                changed++;
            }
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
        if (changed != (renderer ? 3 : 1)) throw new IllegalStateException("BBS morph hooks did not match " + transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
