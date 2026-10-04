package mchorse.bbs_mod.forge.studio;

import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.utils.pose.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** A renderer belongs to a form, while immutable assets are shared by ModelManager. */
public final class NativeFormRenderer implements ITickable
{
    private static final java.lang.reflect.Field BUFFER_BUILDING = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
        net.minecraft.client.renderer.BufferBuilder.class, "isDrawing", "field_179010_r");
    private final Form form;
    private Entity mob;
    private String mobKey;
    private final mchorse.bbs_mod.forms.entities.StubEntity previewEntity = new mchorse.bbs_mod.forms.entities.StubEntity();

    public Form getForm() { return form; }
    public mchorse.bbs_mod.utils.AABB getPreviewBounds()
    {
        Texture texture = getTexture();
        if (texture != null && form instanceof BillboardForm)
            return NativeBillboardGeometry.bounds((BillboardForm) form, texture.width, texture.height);
        if (texture != null && form instanceof ExtrudedForm)
            return NativeTextureExtruder.getBounds(texture.width, texture.height);
        if (!(form instanceof MobForm) || Minecraft.getMinecraft().world == null) return null;
        resolveMob((MobForm) form, Minecraft.getMinecraft().world);
        return mob == null ? null : new mchorse.bbs_mod.utils.AABB(-mob.width / 2F, 0, -mob.width / 2F, mob.width, mob.height, mob.width);
    }

    public boolean isPreviewCameraFacing()
    {
        return form instanceof BillboardForm && ((BillboardForm) form).billboard.get()
            || form instanceof ExtrudedForm && ((ExtrudedForm) form).billboard.get();
    }

    private Texture getTexture()
    {
        mchorse.bbs_mod.resources.Link link = form instanceof BillboardForm ? ((BillboardForm) form).texture.get()
            : form instanceof ExtrudedForm ? ((ExtrudedForm) form).texture.get() : null;
        return link == null ? null : mchorse.bbs_mod.BBSModClient.getTextures().getTexture(link);
    }

    /** Original form tile framing and labels, rendered through the Forge model backend. */
    public void renderUI(mchorse.bbs_mod.ui.framework.UIContext context, int x1, int y1, int x2, int y2)
    {
        renderPreview(context, x1, y1, x2, y2);
        mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer font = context.batcher.getFont();
        String name = form.name.get();
        if (!name.isEmpty())
        {
            name = font.limitToWidth(name, x2 - x1 - 3);
            context.batcher.textCard(name, (x2 + x1 - font.getWidth(name)) / 2, y1 + 6,
                mchorse.bbs_mod.utils.colors.Colors.WHITE, mchorse.bbs_mod.utils.colors.Colors.ACTIVE | mchorse.bbs_mod.utils.colors.Colors.A50);
        }
        if (form.hotkey.get() > 0)
        {
            name = font.limitToWidth(mchorse.bbs_mod.ui.utils.keys.KeyCodes.getName(form.hotkey.get()), x2 - x1 - 3);
            context.batcher.textCard(name, (x2 + x1 - font.getWidth(name)) / 2, y2 - 6 - font.getHeight(),
                mchorse.bbs_mod.utils.colors.Colors.WHITE, mchorse.bbs_mod.utils.colors.Colors.A50);
        }
    }

    public void renderPreview(mchorse.bbs_mod.ui.framework.UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();
        Matrix4f matrix = getUIMatrix(context, x1, y1, x2, y2);
        Matrix4f fitted = mchorse.bbs_mod.forms.renderers.utils.FormPreviewFit.frame(mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form), matrix, x1, y1, x2, y2, context.getTransition());
        if (fitted != null) matrix = fitted;
        previewEntity.setWorld(Minecraft.getMinecraft().world);
        previewEntity.setForm(form);
        int depth = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_DEPTH_FUNC);
        boolean depthEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        GlStateManager.pushMatrix();
        try
        {
            NativeTextureRenderer.loadMatrix(NativeTextureRenderer.currentMatrix().mul(context.batcher.getContext().getMatrices().peek().getPositionMatrix()).mul(matrix));
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
            render(previewEntity, context.getTransition(), true);
        }
        finally
        {
            GlStateManager.depthFunc(depth);
            if (!depthEnabled) GlStateManager.disableDepth();
            GlStateManager.popMatrix();
        }
    }

    public static Matrix4f getUIMatrix(mchorse.bbs_mod.ui.framework.UIContext context, int x1, int y1, int x2, int y2)
    {
        float scale = (y2 - y1) / 2.5F;
        float angle = mchorse.bbs_mod.utils.MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + (float) Math.PI;
        if (mchorse.bbs_mod.BBSSettings.freezeModels.get()) angle = -(float) Math.PI + (float) Math.PI / 8F;
        return new Matrix4f().translation(x1 + (x2 - x1) / 2, y1 + (y2 - y1) * 0.85F, 40)
            .scale(scale, -scale, scale).rotateX((float) Math.PI / 8F).rotateY(angle);
    }

    private NativeFormRenderer(Form form) { this.form = form; }
    public static NativeFormRenderer of(Form form)
    {
        if (form.getRenderer() instanceof mchorse.bbs_mod.forms.renderers.NativeFormRendererAdapter)
            return ((mchorse.bbs_mod.forms.renderers.NativeFormRendererAdapter<?>) form.getRenderer()).getBackend();
        return new NativeFormRenderer(form);
    }
    public static void prepare(Form form)
    {
        if (form == null) return;
        mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form);
        for (BodyPart part : form.parts.getAllTyped()) prepare(part.getForm());
    }
    @Override public void tick(IEntity entity)
    {
        Object renderer = mchorse.bbs_mod.forms.FormUtilsClient.getRenderer(form);
        if (renderer instanceof mchorse.bbs_mod.forms.renderers.ModelFormRenderer)
            ((ITickable) renderer).tick(entity);
    }
    public Transform transform()
    {
        form.syncOverlayTracks();
        Transform result = form.transform.get().copy();
        overlay(result, form.transformOverlay.get());
        for (ValueTransform value : form.additionalTransforms) overlay(result, value.get());
        return result;
    }
    private static void overlay(Transform target, Transform value)
    {
        target.translate.add(value.translate); target.scale.add(value.scale).sub(1, 1, 1); target.addRotation(value);
    }
    public static void overlay(Pose target, Pose overlay)
    {
        for (Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform p = target.getOrCreate(entry.getKey()), v = entry.getValue();
            p.visible &= v.visible;
            if (Math.abs(v.fix) > 0.00001F)
            {
                p.translate.lerp(v.translate, v.fix); p.scale.lerp(v.scale, v.fix); p.lerpRotation(v, v.fix);
            }
            else overlay(p, v);
        }
    }
    public static Matrix4f matrix(Transform transform)
    {
        return new Matrix4f().translate(transform.translate).rotate(transform.createRotation()).scale(transform.scale);
    }
    public void render(IEntity entity, float partial)
    {
        render(entity, partial, false);
    }

    private void render(IEntity entity, float partial, boolean ui)
    {
        mchorse.bbs_mod.graphics.MatrixStack stack = new mchorse.bbs_mod.graphics.MatrixStack();
        stack.peek().getPositionMatrix().set(NativeTextureRenderer.currentMatrix());
        stack.peek().getPositionMatrix().normal(stack.peek().getNormalMatrix());
        int light = ui ? 0x00f000f0 : (int) net.minecraft.client.renderer.OpenGlHelper.lastBrightnessX
            | (int) net.minecraft.client.renderer.OpenGlHelper.lastBrightnessY << 16;
        mchorse.bbs_mod.forms.renderers.FormRenderingContext context = new mchorse.bbs_mod.forms.renderers.FormRenderingContext()
            .set(mchorse.bbs_mod.forms.renderers.FormRenderType.ENTITY, entity, stack, light, 10 << 16, partial);
        context.ui = ui;
        GlStateManager.pushMatrix();
        try { GlStateManager.loadIdentity(); mchorse.bbs_mod.forms.FormUtilsClient.render(form, context); }
        finally { GlStateManager.popMatrix(); }
    }

    /** Draw only this form. FormRenderer owns states, transforms and the parts traversal. */
    public void renderGeometry(IEntity entity, float partial, boolean ui, int tint)
    {
        if (form instanceof MobForm) renderMob((MobForm) form, entity, new Transform(), partial);
        else if (form instanceof BillboardForm)
        {
            BillboardForm billboard = (BillboardForm) form;
            Texture texture = getTexture();
            if (texture != null) NativeTextureRenderer.render(
                NativeBillboardGeometry.create(billboard, texture.width, texture.height), texture, form,
                tinted(billboard.color.get(), tint), ui || billboard.shading.get(), billboard.linear.get(), billboard.mipmap.get(), ui);
        }
        else if (form instanceof ExtrudedForm)
        {
            ExtrudedForm extruded = (ExtrudedForm) form;
            Texture texture = getTexture();
            NativeTextureRenderer.render(mchorse.bbs_mod.BBSModClient.getTextures().getExtruder().get(texture), texture, form, tinted(extruded.color.get(), tint),
                ui || extruded.shading.get(), false, false, ui);
        }
    }

    private static mchorse.bbs_mod.utils.colors.Color tinted(mchorse.bbs_mod.utils.colors.Color color, int tint)
    {
        mchorse.bbs_mod.utils.colors.Color result = color.copy();
        result.mul(tint);
        return result;
    }

    private void renderMob(MobForm f, IEntity target, Transform transform, float partial)
    {
        resolveMob(f, target.getWorld());
        if (mob == null) return;
        mob.ticksExisted = target.getAge();
        mob.rotationYaw = target.getYaw(); mob.prevRotationYaw = target.getPrevYaw();
        mob.rotationPitch = target.getPitch(); mob.prevRotationPitch = target.getPrevPitch();
        if (mob instanceof EntityLivingBase)
        {
            EntityLivingBase living = (EntityLivingBase) mob;
            living.renderYawOffset = target.getBodyYaw(); living.prevRenderYawOffset = target.getPrevBodyYaw();
            living.rotationYawHead = target.getHeadYaw(); living.prevRotationYawHead = target.getPrevHeadYaw();
            living.limbSwing = target.getLimbPos(partial); living.limbSwingAmount = living.prevLimbSwingAmount = target.getLimbSpeed(partial);
            for (net.minecraft.inventory.EntityEquipmentSlot slot : net.minecraft.inventory.EntityEquipmentSlot.values())
                living.setItemStackToSlot(slot, target.getEquipmentStack(slot));
        }
        int previousMode = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MATRIX_MODE);
        GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
        int previousDepth = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH);
        int previousList = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_LIST_INDEX);
        net.minecraft.client.renderer.BufferBuilder vertices = net.minecraft.client.renderer.Tessellator.getInstance().getBuffer();
        boolean wasBuilding = isBuilding(vertices);
        GlStateManager.pushMatrix();
        try
        {
            NativeTextureRenderer.loadMatrix(NativeTextureRenderer.currentMatrix().mul(matrix(transform)));
            GlStateManager.rotate(target.getBodyYaw(), 0, 1, 0);
            Minecraft.getMinecraft().getRenderManager().renderEntity(mob, 0, 0, 0, target.getYaw(), partial, false);
        }
        finally
        {
            /* RenderLivingBase catches layer/model failures itself. An interrupted
             * ModelRenderer compilation otherwise keeps recording future frames into
             * its GL display list, growing driver memory without a Java heap limit. */
            if (previousList == 0 && org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_LIST_INDEX) != 0)
            {
                GlStateManager.glEndList();
            }
            if (!wasBuilding && isBuilding(vertices))
            {
                vertices.finishDrawing();
                vertices.reset();
            }
            /* RenderLivingBase pushes before querying isChild(), outside its own try block.
             * A failing external entity renderer must not leave that stack entry in the UI. */
            GlStateManager.matrixMode(org.lwjgl.opengl.GL11.GL_MODELVIEW);
            while (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_MODELVIEW_STACK_DEPTH) > previousDepth)
                GlStateManager.popMatrix();
            GlStateManager.matrixMode(previousMode);
        }
    }
    private static boolean isBuilding(net.minecraft.client.renderer.BufferBuilder vertices)
    {
        try { return BUFFER_BUILDING.getBoolean(vertices); }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot inspect native vertex buffer", error); }
    }
    private void resolveMob(MobForm f, net.minecraft.world.World world)
    {
        /* A viewport StubEntity has no world; vanilla 1.12 entities still consult it
         * while rendering (EntityAgeable.isChild(), lighting and layer rendering). */
        if (world == null) world = Minecraft.getMinecraft().world;
        if (world == null)
        {
            mob = null;
            mobKey = null;
            return;
        }
        String key = f.mobID.get() + "\n" + f.mobNBT.get();
        if (!key.equals(mobKey) || (mob != null && mob.world != world))
        {
            mobKey = key;
            mob = EntityList.createEntityByIDFromName(new ResourceLocation(f.mobID.get()), world);
            if (mob != null && !f.mobNBT.get().isEmpty())
            {
                try { mob.readFromNBT(JsonToNBT.getTagFromJson(f.mobNBT.get())); }
                catch (net.minecraft.nbt.NBTException e) { mchorse.bbs_mod.BBSMod.LOGGER.warn("Invalid mob form NBT", e); }
            }
        }
    }
}
