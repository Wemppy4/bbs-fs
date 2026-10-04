package mchorse.bbs_mod.cubic.render.vanilla;

import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

/** The original per-bone armor attachments, backed by actual 1.12 armor models and Forge hooks. */
public class ArmorRenderer
{
    private final ModelBiped inner = new ModelBiped(0.5F), outer = new ModelBiped(1F);
    private final ModelRenderer leftWing, rightWing;
    private static final ResourceLocation ELYTRA = new ResourceLocation("textures/entity/elytra.png");
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");

    public ArmorRenderer()
    {
        ModelBase wings = new ModelBase() {};
        wings.textureWidth = 64; wings.textureHeight = 32;
        this.leftWing = new ModelRenderer(wings, 22, 0);
        this.leftWing.addBox(-10, 0, 0, 10, 20, 2, 1);
        this.rightWing = new ModelRenderer(wings, 22, 0);
        this.rightWing.mirror = true;
        this.rightWing.addBox(0, 0, 0, 10, 20, 2, 1);
    }

    /** Called inside NativeEquipmentRenderer, which owns the GL matrix and state scope. */
    public void renderArmorSlot(IEntity target, ItemStack stack, ArmorType type)
    {
        if (stack == null || stack.isEmpty()) return;
        if (type == ArmorType.CHEST && stack.getItem() == Items.ELYTRA)
        {
            this.renderElytra(target, stack);
            return;
        }
        if (!(stack.getItem() instanceof ItemArmor)) return;
        ItemArmor armor = (ItemArmor) stack.getItem();
        if (armor.armorType != type.slot) return;
        EntityLivingBase entity = target instanceof MCEntity && ((MCEntity) target).getMcEntity() instanceof EntityLivingBase
            ? (EntityLivingBase) ((MCEntity) target).getMcEntity() : null;
        ModelBiped model = type.slot == EntityEquipmentSlot.LEGS ? this.inner : this.outer;
        if (entity != null) model = ForgeHooksClient.getArmorModel(entity, stack, type.slot, model);
        ModelRenderer part = this.part(model, type);
        float px = part.rotationPointX, py = part.rotationPointY, pz = part.rotationPointZ;
        float rx = part.rotateAngleX, ry = part.rotateAngleY, rz = part.rotateAngleZ;
        boolean hidden = part.isHidden, visible = part.showModel;
        try
        {
            part.rotationPointX = part.rotationPointY = part.rotationPointZ = 0;
            part.rotateAngleX = part.rotateAngleY = part.rotateAngleZ = 0;
            part.isHidden = false; part.showModel = true;
            int dye = armor.getColor(stack);
            boolean hasOverlay = armor.hasOverlay(stack);
            bind(armorTexture(entity, stack, type.slot, null));
            if (hasOverlay) GlStateManager.color((dye >> 16 & 255) / 255F, (dye >> 8 & 255) / 255F, (dye & 255) / 255F, 1);
            else GlStateManager.color(1, 1, 1, 1);
            part.render(1F / 16F);
            if (hasOverlay)
            {
                bind(armorTexture(entity, stack, type.slot, "overlay"));
                GlStateManager.color(1, 1, 1, 1); part.render(1F / 16F);
            }
            if (stack.hasEffect()) glint(target.getAge(), () -> part.render(1F / 16F));
        }
        finally
        {
            part.rotationPointX = px; part.rotationPointY = py; part.rotationPointZ = pz;
            part.rotateAngleX = rx; part.rotateAngleY = ry; part.rotateAngleZ = rz;
            part.isHidden = hidden; part.showModel = visible;
        }
    }

    private ModelRenderer part(ModelBiped model, ArmorType type)
    {
        switch (type)
        {
            case HELMET: return model.bipedHead;
            case LEFT_ARM: return model.bipedLeftArm;
            case RIGHT_ARM: return model.bipedRightArm;
            case LEFT_LEG: case LEFT_BOOT: return model.bipedLeftLeg;
            case RIGHT_LEG: case RIGHT_BOOT: return model.bipedRightLeg;
            default: return model.bipedBody;
        }
    }

    private static ResourceLocation armorTexture(EntityLivingBase entity, ItemStack stack, EntityEquipmentSlot slot, String type)
    {
        String material = ((ItemArmor) stack.getItem()).getArmorMaterial().getName();
        String domain = "minecraft";
        int colon = material.indexOf(':');
        if (colon >= 0) { domain = material.substring(0, colon); material = material.substring(colon + 1); }
        String texture = domain + ":textures/models/armor/" + material + "_layer_" + (slot == EntityEquipmentSlot.LEGS ? 2 : 1) + (type == null ? "" : "_" + type) + ".png";
        return new ResourceLocation(ForgeHooksClient.getArmorTexture(entity, stack, texture, slot, type));
    }

    private void renderElytra(IEntity target, ItemStack stack)
    {
        float pitch = 0.2617994F, roll = -0.2617994F, y = 0, yaw = 0;
        if (target.isFallFlying())
        {
            float spread = 1;
            Vec3d velocity = target.getVelocity();
            if (velocity.y < 0) spread = 1F - (float) Math.pow(-velocity.normalize().y, 1.5);
            pitch = spread * 0.34906584F + (1 - spread) * pitch;
            roll = spread * -1.5707964F + (1 - spread) * roll;
        }
        else if (target.isSneaking()) { pitch = 0.6981317F; roll = -0.7853982F; y = 3; yaw = 0.08726646F; }
        this.leftWing.setRotationPoint(5, y, 0); this.rightWing.setRotationPoint(-5, y, 0);
        this.leftWing.rotateAngleX = this.rightWing.rotateAngleX = pitch;
        this.leftWing.rotateAngleY = yaw; this.rightWing.rotateAngleY = -yaw;
        this.leftWing.rotateAngleZ = roll; this.rightWing.rotateAngleZ = -roll;
        bind(ELYTRA); GlStateManager.color(1, 1, 1, 1); GlStateManager.pushMatrix();
        try
        {
            GlStateManager.translate(0, 0, 0.125);
            Runnable draw = () -> { this.leftWing.render(1F / 16F); this.rightWing.render(1F / 16F); };
            draw.run(); if (stack.hasEffect()) glint(target.getAge(), draw);
        }
        finally { GlStateManager.popMatrix(); }
    }

    private static void bind(ResourceLocation texture) { Minecraft.getMinecraft().getTextureManager().bindTexture(texture); }

    private static void glint(float ticks, Runnable draw)
    {
        bind(GLINT); GlStateManager.enableBlend(); GlStateManager.depthFunc(GL11.GL_EQUAL); GlStateManager.depthMask(false);
        GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix();
        try
        {
            for (int i = 0; i < 2; i++)
            {
                GlStateManager.disableLighting(); GlStateManager.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                GlStateManager.color(0.38F, 0.19F, 0.608F, 1);
                GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.loadIdentity();
                GlStateManager.scale(1F / 3F, 1F / 3F, 1F / 3F); GlStateManager.rotate(30 - i * 60, 0, 0, 1);
                GlStateManager.translate(0, ticks * (0.001F + i * 0.003F) * 20, 0);
                GlStateManager.matrixMode(GL11.GL_MODELVIEW); draw.run();
            }
        }
        finally
        {
            GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.depthMask(true); GlStateManager.depthFunc(GL11.GL_LEQUAL);
        }
    }
}
