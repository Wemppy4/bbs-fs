package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** The 1.12 equivalent of the public Entity fire timer methods used by BBS. */
public final class BBSProjectileTransformer implements IClassTransformer
{
    @Override public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || !"net.minecraft.entity.Entity".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        String fire = null;
        for (FieldNode field : node.fields)
            if ((field.name.equals("fire") || field.name.equals("field_190534_ay")) && field.desc.equals("I")) fire = field.name;
        if (fire == null) throw new IllegalStateException("BBS projectile fire timer missing in Entity");
        node.interfaces.add("mchorse/bbs_mod/entity/EntityFireAccess");
        MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "bbs$getFireTicks", "()I", null, null);
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        get.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, fire, "I"));
        get.instructions.add(new InsnNode(Opcodes.IRETURN));
        node.methods.add(get);
        MethodNode set = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, "bbs$setFireTicks", "(I)V", null, null);
        set.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        set.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        set.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, node.name, fire, "I"));
        set.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(set);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
