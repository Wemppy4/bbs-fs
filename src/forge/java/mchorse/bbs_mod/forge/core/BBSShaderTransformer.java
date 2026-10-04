package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** OptiFine's source/program boundaries, following Aperture 1.12's ShadersTransformer.
 * No OptiFine class is linked when the optional shader mod is absent. */
public final class BBSShaderTransformer implements IClassTransformer
{
    private static final String HOOK = "mchorse/bbs_mod/utils/iris/ShaderCurves";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes)
    {
        if (bytes == null || !"net.optifine.shaders.Shaders".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int sources = 0, programs = 0, reloads = 0, shadowRotations=0, shadowVectors=0;
        for (MethodNode method : node.methods)
        {
            if (method.name.matches("create(?:Vert|Geom|Frag|Comp)Shader"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode invoke = (MethodInsnNode) instruction;
                    if (invoke.name.equals("resolveIncludes") && invoke.desc.endsWith(")Ljava/io/BufferedReader;"))
                    {
                        method.instructions.insert(instruction, call("processReader", "(Ljava/io/BufferedReader;)Ljava/io/BufferedReader;"));
                        sources++;
                    }
                }
            }
            else if (method.name.equals("useProgram"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray())
                {
                    if (instruction.getOpcode() == Opcodes.RETURN)
                    {
                        method.instructions.insertBefore(instruction, call("applyUniforms", "()V"));
                        programs++;
                    }
                }
            }
            else if (method.name.equals("loadShaderPack") || method.name.equals("init"))
            {
                method.instructions.insert(call("reset", "()V"));
                reloads++;
            }
            else if (method.name.equals("uninit"))
            {
                method.instructions.insert(call("clearPrograms", "()V"));
            }
            else if(method.name.equals("setCameraShadow")&&method.desc.equals("(F)V"))
            {
                boolean sunRotation=false;
                for(AbstractInsnNode instruction:method.instructions.toArray())
                {
                    if(instruction instanceof FieldInsnNode)
                    {
                        FieldInsnNode field=(FieldInsnNode)instruction;
                        if(field.getOpcode()==Opcodes.GETSTATIC&&field.name.equals("sunPathRotation")&&field.desc.equals("F"))sunRotation=true;
                    }
                    if(sunRotation&&instruction instanceof MethodInsnNode)
                    {
                        MethodInsnNode invoke=(MethodInsnNode)instruction;
                        if(invoke.owner.equals("org/lwjgl/opengl/GL11")&&invoke.name.equals("glRotatef")&&invoke.desc.equals("(FFFF)V"))
                        {
                            method.instructions.insert(instruction,sunCall("rotateShadowCamera","()V"));
                            shadowRotations++;sunRotation=false;
                        }
                    }
                    if(instruction.getOpcode()==Opcodes.RETURN)
                    {
                        InsnList hook=new InsnList();hook.add(new FieldInsnNode(Opcodes.GETSTATIC,node.name,"shadowLightPositionVector","[F"));
                        hook.add(sunCall("rotateShadowLight","([F)V"));method.instructions.insertBefore(instruction,hook);shadowVectors++;
                    }
                }
            }
        }
        if (sources < 3 || programs == 0 || reloads != 2)
            throw new IllegalStateException("BBS shader curves: unsupported OptiFine shader boundaries (" + sources + "/" + programs + "/" + reloads + ")");
        if(shadowRotations!=2||shadowVectors!=1)throw new IllegalStateException("BBS shader sun yaw: unsupported shadow camera boundaries ("+shadowRotations+"/"+shadowVectors+")");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static MethodInsnNode call(String name, String descriptor)
    {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, name, descriptor, false);
    }
    private static MethodInsnNode sunCall(String name,String descriptor)
    {return new MethodInsnNode(Opcodes.INVOKESTATIC,"mchorse/bbs_mod/utils/iris/OptiFineSunRotation",name,descriptor,false);}
}
