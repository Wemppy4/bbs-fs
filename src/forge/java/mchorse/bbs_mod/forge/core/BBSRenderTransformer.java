package mchorse.bbs_mod.forge.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.ArrayList;

/** Exact native world/HUD boundaries: render at the chosen size, finish before the editor UI. */
public final class BBSRenderTransformer implements IClassTransformer
{
    private static final String HOOK="mchorse/bbs_mod/client/BBSRendering";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes)
    {
        if(bytes==null)return null;
        boolean renderer="net.minecraft.client.renderer.EntityRenderer".equals(transformedName);
        boolean minecraft="net.minecraft.client.Minecraft".equals(transformedName);
        boolean timer="net.minecraft.util.Timer".equals(transformedName);
        boolean server="net.minecraft.server.integrated.IntegratedServer".equals(transformedName);
        boolean world="net.minecraft.client.multiplayer.WorldClient".equals(transformedName);
        boolean sky="net.minecraft.client.renderer.RenderGlobal".equals(transformedName);
        if(!renderer&&!minecraft&&!timer&&!server&&!world&&!sky)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        if (world)
        {
            boolean srg=false;
            for (MethodNode method:node.methods) if(method.name.equals("func_72835_b")) srg=true;
            addWorldCurve(node,srg?"func_72820_D":"getWorldTime","()J","curveWorldTime");
            addWorldCurve(node,srg?"func_72867_j":"getRainStrength","(F)F","curveRain");
            ClassWriter output=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(output);return output.toByteArray();
        }
        int matched=0;
        for(MethodNode method:new ArrayList<MethodNode>(node.methods))
        {
            if (sky && named(method,"renderEntities","func_180446_a")
                && method.desc.equals("(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;F)V"))
            {
                for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction.getOpcode()==Opcodes.RETURN)
                {
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.FLOAD,3));hook.add(call("onShadowEntities","(F)V"));
                    method.instructions.insertBefore(instruction,hook);
                }
            }
            if(renderer)
            {
                for(AbstractInsnNode instruction:method.instructions.toArray()) if(instruction instanceof FieldInsnNode)
                {
                    FieldInsnNode field=(FieldInsnNode)instruction;
                    if(field.getOpcode()==Opcodes.GETFIELD && field.owner.equals("net/minecraft/client/settings/GameSettings")
                        && (field.name.equals("gammaSetting")||field.name.equals("field_74333_Y")) && field.desc.equals("F"))
                        method.instructions.insert(field,call("curveBrightness","(F)F"));
                }
            }
            if(sky && named(method,"renderSky","func_174976_a") && method.desc.equals("(FI)V"))
            {
                LabelNode nativeSky=new LabelNode();InsnList entry=new InsnList();
                entry.add(call("renderChromaSky","()Z"));entry.add(new JumpInsnNode(Opcodes.IFEQ,nativeSky));
                entry.add(new InsnNode(Opcodes.RETURN));entry.add(nativeSky);entry.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(entry);
                for(AbstractInsnNode instruction:method.instructions.toArray()) if(instruction instanceof LdcInsnNode && Float.valueOf(-90F).equals(((LdcInsnNode)instruction).cst))
                    method.instructions.insert(instruction,call("curveSunYaw","(F)F"));
                matched++;
            }
            if(timer && named(method,"updateTimer","func_74275_a") && method.desc.equals("()V"))
            {
                String clock=null;MethodInsnNode now=null;
                for(AbstractInsnNode instruction:method.instructions.toArray())
                {
                    if(instruction instanceof FieldInsnNode && instruction.getOpcode()==Opcodes.PUTFIELD && ((FieldInsnNode)instruction).desc.equals("J"))
                        clock=((FieldInsnNode)instruction).name;
                    if(instruction instanceof MethodInsnNode && instruction.getOpcode()==Opcodes.INVOKESTATIC && ((MethodInsnNode)instruction).desc.equals("()J"))
                        now=(MethodInsnNode)instruction;
                }
                if(clock==null||now==null)throw new IllegalStateException("BBS capture: Timer clock fields changed");
                LabelNode nativeTick=new LabelNode();InsnList entry=new InsnList();
                entry.add(new VarInsnNode(Opcodes.ALOAD,0));entry.add(call("updateTimer","(Lnet/minecraft/util/Timer;)Z"));
                entry.add(new JumpInsnNode(Opcodes.IFEQ,nativeTick));
                entry.add(new VarInsnNode(Opcodes.ALOAD,0));
                entry.add(new MethodInsnNode(now.getOpcode(),now.owner,now.name,now.desc,now.itf));
                entry.add(new FieldInsnNode(Opcodes.PUTFIELD,node.name,clock,"J"));entry.add(new InsnNode(Opcodes.RETURN));
                entry.add(nativeTick);entry.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(entry);matched++;
            }
            if(server && named(method,"tick","func_71217_p") && method.desc.equals("()V"))
            {
                MethodInsnNode original=null;
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode)
                {
                    MethodInsnNode invocation=(MethodInsnNode)instruction;
                    if(invocation.getOpcode()==Opcodes.INVOKESPECIAL && invocation.owner.equals(node.superName)
                        && (invocation.name.equals("tick")||invocation.name.equals("func_71217_p")) && invocation.desc.equals("()V"))
                    {
                        original=new MethodInsnNode(invocation.getOpcode(),invocation.owner,invocation.name,invocation.desc,false);
                        invocation.owner=node.name;invocation.name="bbs$capturedServerTick";
                    }
                }
                if(original==null)throw new IllegalStateException("BBS capture: IntegratedServer tick boundary changed");
                addServerTick(node,original);matched++;
            }
            if(minecraft && named(method,"getFramebuffer","func_147110_a") && method.desc.equals("()Lnet/minecraft/client/shader/Framebuffer;"))
            {
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN)
                    method.instructions.insertBefore(instruction,call("renderFramebuffer","(Lnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/client/shader/Framebuffer;"));
                matched++;
            }
            if(renderer && named(method,"renderWorld","func_78471_a") && method.desc.equals("(FJ)V"))
            {
                InsnList entry=new InsnList();entry.add(new VarInsnNode(Opcodes.FLOAD,1));entry.add(call("onWorldRenderBegin","(F)V"));
                method.instructions.insert(entry);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN)
                {
                    InsnList end=new InsnList();end.add(new VarInsnNode(Opcodes.FLOAD,1));end.add(call("onWorldRenderEnd","(F)V"));
                    method.instructions.insertBefore(instruction,end);
                }
                matched++;
            }
            if(renderer && named(method,"renderWorldPass","func_175068_a") && method.desc.equals("(IFJ)V"))
            {
                method.instructions.insert(call("onWorldRenderPassBegin","()V"));
                matched++;
            }
            if(renderer && named(method,"updateCameraAndRender","func_181560_a") && method.desc.equals("(FJ)V"))
            {
                int hud=0;
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode)
                {
                    MethodInsnNode invocation=(MethodInsnNode)instruction;
                    if(invocation.owner.equals("net/minecraft/client/gui/GuiIngame")
                        && (invocation.name.equals("renderGameOverlay")||invocation.name.equals("func_175180_a")) && invocation.desc.equals("(F)V"))
                    {
                        method.instructions.insert(instruction,call("onRenderBeforeScreen","()V"));hud++;
                    }
                    /* Vanilla stores the window's scaled width/height before renderWorld. The
                     * activation animation alone consumes those locals during the captured HUD. */
                    if(invocation.owner.equals(node.name) && (invocation.name.equals("renderItemActivation")||invocation.name.equals("func_190563_a"))
                        && invocation.desc.equals("(IIF)V"))
                    {
                        AbstractInsnNode partial=previousCode(invocation.getPrevious());
                        AbstractInsnNode height=previousCode(partial.getPrevious()),width=previousCode(height.getPrevious());
                        if(height.getOpcode()!=Opcodes.ILOAD || width.getOpcode()!=Opcodes.ILOAD)
                            throw new IllegalStateException("BBS capture: unexpected item activation arguments");
                        method.instructions.insert(width,call("capturedScaledWidth","(I)I"));
                        method.instructions.insert(height,call("capturedScaledHeight","(I)I"));
                    }
                }
                if(hud!=1)throw new IllegalStateException("BBS capture: expected one native HUD boundary, got "+hud);
                wrapUpdate(node,method);matched++;
            }
        }
        if(matched!=(renderer?3:1))throw new IllegalStateException("BBS capture: native render methods changed in "+transformedName+" ("+matched+")");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static AbstractInsnNode previousCode(AbstractInsnNode node)
    {
        while(node!=null && node.getOpcode()<0)node=node.getPrevious();
        return node;
    }
    private static boolean named(MethodNode method,String mcp,String srg){return method.name.equals(mcp)||method.name.equals(srg);}
    private static MethodInsnNode call(String name,String descriptor){return new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,name,descriptor,false);}
    private static void addWorldCurve(ClassNode owner,String name,String descriptor,String hook)
    {
        boolean time=descriptor.equals("()J");
        MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,name,descriptor,null,null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
        if(!time)method.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,owner.superName,name,descriptor,false));
        method.instructions.add(call(hook,time?"(J)J":"(F)F"));
        method.instructions.add(new InsnNode(time?Opcodes.LRETURN:Opcodes.FRETURN));owner.methods.add(method);
    }
    private static void addServerTick(ClassNode owner,MethodInsnNode nativeTick)
    {
        MethodNode method=new MethodNode(Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC,"bbs$capturedServerTick","()V",null,null);
        LabelNode loop=new LabelNode(),end=new LabelNode();
        method.instructions.add(call("serverTicksToRun","()I"));method.instructions.add(new VarInsnNode(Opcodes.ISTORE,1));
        method.instructions.add(loop);method.instructions.add(new FrameNode(Opcodes.F_APPEND,1,new Object[]{Opcodes.INTEGER},0,null));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));method.instructions.add(new JumpInsnNode(Opcodes.IFLE,end));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(nativeTick);
        method.instructions.add(call("serverTickComplete","()V"));method.instructions.add(new IincInsnNode(1,-1));
        method.instructions.add(new JumpInsnNode(Opcodes.GOTO,loop));
        method.instructions.add(end);method.instructions.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
        method.instructions.add(new InsnNode(Opcodes.RETURN));owner.methods.add(method);
    }
    private static void wrapUpdate(ClassNode owner,MethodNode body)
    {
        String name=body.name;int access=body.access;
        body.name="bbs$nativeCameraAndRender";
        body.access=(access&~(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED))|Opcodes.ACC_PRIVATE|Opcodes.ACC_SYNTHETIC;
        MethodNode wrapper=new MethodNode(access,name,body.desc,body.signature,body.exceptions.toArray(new String[0]));
        LabelNode begin=new LabelNode(),end=new LabelNode(),failure=new LabelNode();
        wrapper.instructions.add(begin);
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));wrapper.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));wrapper.instructions.add(new VarInsnNode(Opcodes.LLOAD,2));
        wrapper.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,owner.name,body.name,body.desc,false));
        wrapper.instructions.add(call("onRenderBeforeScreen","()V"));
        wrapper.instructions.add(end);wrapper.instructions.add(new InsnNode(Opcodes.RETURN));
        wrapper.instructions.add(failure);
        wrapper.instructions.add(new FrameNode(Opcodes.F_FULL,3,new Object[]{owner.name,Opcodes.FLOAT,Opcodes.LONG},1,new Object[]{"java/lang/Throwable"}));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ASTORE,4));wrapper.instructions.add(call("abortFrame","()V"));
        wrapper.instructions.add(new VarInsnNode(Opcodes.ALOAD,4));wrapper.instructions.add(new InsnNode(Opcodes.ATHROW));
        wrapper.tryCatchBlocks.add(new TryCatchBlockNode(begin,end,failure,"java/lang/Throwable"));
        owner.methods.add(wrapper);
    }
}
