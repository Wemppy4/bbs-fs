package mchorse.bbs_mod.cubic.render.vanilla;

import com.google.common.collect.Maps;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.DyeableArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.trim.ArmorTrim;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Map;

public class ArmorRenderer
{
    private static final Map<String, Identifier> ARMOR_TEXTURE_CACHE = Maps.newHashMap();
    private static final Identifier ELYTRA_TEXTURE = new Identifier("textures/entity/elytra.png");

    private final BipedEntityModel innerModel;
    private final BipedEntityModel outerModel;
    private final ModelPart elytra;
    private final ModelPart elytraLeftWing;
    private final ModelPart elytraRightWing;
    private final SpriteAtlasTexture armorTrimsAtlas;

    public ArmorRenderer(BipedEntityModel innerModel, BipedEntityModel outerModel, ModelPart elytra, BakedModelManager bakery)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.elytra = elytra;
        this.elytraLeftWing = elytra.getChild("left_wing");
        this.elytraRightWing = elytra.getChild("right_wing");
        this.armorTrimsAtlas = bakery.getAtlas(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
    }

    public void renderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);
        Item item = itemStack.getItem();

        if (type == ArmorType.CHEST && itemStack.isOf(Items.ELYTRA))
        {
            this.renderElytra(matrices, vertexConsumers, entity, itemStack, light);

            return;
        }

        if (item instanceof ArmorItem armorItem)
        {
            if (armorItem.getSlotType() == armorSlot)
            {
                boolean innerModel = this.usesInnerModel(armorSlot);
                BipedEntityModel bipedModel = this.getModel(armorSlot);
                ModelPart part = this.getPart(bipedModel, type);

                bipedModel.setVisible(true);

                part.pivotX = part.pivotY = part.pivotZ = 0F;
                part.pitch = part.yaw = part.roll = 0F;
                part.xScale = part.yScale = part.zScale = 1F;

                if (armorItem instanceof DyeableArmorItem dyeableArmorItem)
                {
                    int color = dyeableArmorItem.getColor(itemStack);
                    float r = (float)(color >> 16 & 255) / 255.0F;
                    float g = (float)(color >> 8 & 255) / 255.0F;
                    float b = (float)(color & 255) / 255.0F;

                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, r, g, b, null);
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, "overlay");
                }
                else
                {
                    this.renderArmorParts(part, matrices, vertexConsumers, light, armorItem, innerModel, 1F, 1F, 1F, null);
                }

                /* A stub in a preview may have no world of its own; the trims live in the world's
                 * registries, so the client's stands in, and without any world there are no trims. */
                World world = entity.getWorld() != null ? entity.getWorld() : MinecraftClient.getInstance().world;

                if (world != null)
                {
                    ArmorTrim.getTrim(world.getRegistryManager(), itemStack, true).ifPresent((trim) ->
                    {
                        this.renderTrim(part, armorItem.getMaterial(), matrices, vertexConsumers, light, trim, innerModel);
                    });
                }

                if (itemStack.hasGlint())
                {
                    this.renderGlint(part, matrices, vertexConsumers, light);
                }
            }
        }
    }

    /* Vanilla elytra, verified against 1.20.4 bytecode: ElytraFeatureRenderer.render
     * (translate 0,0,0.125; armor cutout layer; glint) + ElytraEntityModel.setAngles
     * (non-player branch: standing / sneaking / fall flying wing angles) */
    private void renderElytra(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, ItemStack itemStack, int light)
    {
        float pitch = 0.2617994F;
        float roll = -0.2617994F;
        float pivotY = 0F;
        float yaw = 0F;

        if (entity.isFallFlying())
        {
            float spread = 1F;
            Vec3d velocity = entity.getVelocity();

            if (velocity.y < 0D)
            {
                spread = 1F - (float) Math.pow(-velocity.normalize().y, 1.5D);
            }

            pitch = spread * 0.34906584F + (1F - spread) * pitch;
            roll = spread * -1.5707964F + (1F - spread) * roll;
        }
        else if (entity.isSneaking())
        {
            pitch = 0.6981317F;
            roll = -0.7853982F;
            pivotY = 3F;
            yaw = 0.08726646F;
        }

        this.elytraLeftWing.pivotY = pivotY;
        this.elytraLeftWing.pitch = pitch;
        this.elytraLeftWing.yaw = yaw;
        this.elytraLeftWing.roll = roll;
        this.elytraRightWing.pivotY = pivotY;
        this.elytraRightWing.pitch = pitch;
        this.elytraRightWing.yaw = -yaw;
        this.elytraRightWing.roll = -roll;

        matrices.push();
        matrices.translate(0F, 0F, 0.125F);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(ELYTRA_TEXTURE));

        this.elytra.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);

        if (itemStack.hasGlint())
        {
            this.renderGlint(this.elytra, matrices, vertexConsumers, light);
        }

        matrices.pop();
    }

    private ModelPart getPart(BipedEntityModel bipedModel, ArmorType type)
    {
        switch (type)
        {
            case HELMET -> {
                return bipedModel.head;
            }
            case CHEST, LEGGINGS -> {
                return bipedModel.body;
            }
            case LEFT_ARM -> {
                return bipedModel.leftArm;
            }
            case RIGHT_ARM -> {
                return bipedModel.rightArm;
            }
            case LEFT_LEG, LEFT_BOOT -> {
                return bipedModel.leftLeg;
            }
            case RIGHT_LEG, RIGHT_BOOT -> {
                return bipedModel.rightLeg;
            }
        }

        return bipedModel.head;
    }

    private void renderArmorParts(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorItem item, boolean secondTextureLayer, float red, float green, float blue, String overlay)
    {
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getArmorCutoutNoCull(this.getArmorTexture(item, secondTextureLayer, overlay)));

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, red, green, blue, 1F);
    }

    private void renderTrim(ModelPart part, ArmorMaterial material, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorTrim trim, boolean leggings)
    {
        Sprite sprite = this.armorTrimsAtlas.getSprite(leggings ? trim.getLeggingsModelId(material) : trim.getGenericModelId(material));
        VertexConsumer vertexConsumer = sprite.getTextureSpecificVertexConsumer(vertexConsumers.getBuffer(TexturedRenderLayers.getArmorTrims(trim.getPattern().value().decal())));

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
    }

    private void renderGlint(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        part.render(matrices, vertexConsumers.getBuffer(RenderLayer.getArmorEntityGlint()), light, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F);
    }

    private BipedEntityModel getModel(EquipmentSlot slot)
    {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }

    private Identifier getArmorTexture(ArmorItem item, boolean secondLayer, String overlay)
    {
        String materialName = item.getMaterial().getName();
        String id = "textures/models/armor/" + materialName + "_layer_" + (secondLayer ? 2 : 1) + (overlay == null ? "" : "_" + overlay) + ".png";

        return ARMOR_TEXTURE_CACHE.computeIfAbsent(id, Identifier::new);
    }
}