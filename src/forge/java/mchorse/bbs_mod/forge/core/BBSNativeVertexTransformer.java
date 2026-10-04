package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Apply form colour/light before either vanilla or OptiFine uploads native vertices. */
public final class BBSNativeVertexTransformer implements IClassTransformer
{
    @Override public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || !"net.minecraft.client.renderer.WorldVertexBufferUploader".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        int hooks = 0;
        for (MethodNode method : node.methods)
        {
            if (!(method.name.equals("draw") || method.name.equals("func_181679_a"))
                || !method.desc.equals("(Lnet/minecraft/client/renderer/BufferBuilder;)V")) continue;
            InsnList entry = new InsnList(); entry.add(new VarInsnNode(Opcodes.ALOAD, 1));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "mchorse/bbs_mod/graphics/NativeFormVertices", "beforeUpload",
                "(Lnet/minecraft/client/renderer/BufferBuilder;)V", false));
            method.instructions.insert(entry); hooks++;
        }
        if (hooks != 1) throw new IllegalStateException("BBS native form uploader boundary changed: " + hooks);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
