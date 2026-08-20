package mchorse.bbs_mod.cubic.render.vanilla;

import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentModelLoader;
import net.minecraft.client.render.entity.model.EquipmentModelData;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Vanilla armor rendering onto BBS cubic-model bones.
 *
 * <p>Ported to 1.21.11. The 1.21.4+ equipment rewrite removed the entire API this class was built
 * on:
 * <ul>
 *   <li>{@code net.minecraft.item.ArmorItem} no longer exists — armor identity is now carried by the
 *       {@link net.minecraft.component.DataComponentTypes#EQUIPPABLE} data component (an
 *       {@link EquippableComponent} whose {@link EquippableComponent#assetId()} points at an
 *       {@link EquipmentAsset}).</li>
 *   <li>{@code ArmorMaterial} moved to {@code net.minecraft.item.equipment} and no longer exposes a
 *       per-item material/key the way it used to; {@code ArmorTrim} moved to
 *       {@code net.minecraft.item.equipment.trim} and replaced
 *       {@code getGenericModelId}/{@code getLeggingsModelId} with
 *       {@link ArmorTrim#getTextureId(String, RegistryKey)}.</li>
 *   <li>{@code RenderLayer.getArmorCutoutNoCull(Identifier)}, {@code RenderLayer.getArmorEntityGlint()}
 *       and every other {@code RenderLayer.getXxx(...)} entity-layer factory were removed —
 *       {@code RenderLayer} now only exposes {@code of(String, RenderSetup)}. Entity/equipment drawing
 *       moved to {@code EquipmentRenderer} + the {@code OrderedRenderCommandQueue} command system, a
 *       different architecture from this per-{@link ArmorType}, per-{@link ModelPart} renderer.</li>
 *   <li>{@code BakedModelManager.getAtlas(Identifier)} was removed — the armor-trims sprite atlas
 *       comes from {@code AtlasManager} instead.</li>
 * </ul>
 *
 * <p>Vanilla's own {@code EquipmentRenderer} cannot be reused: it renders a whole {@code Model<S>}
 * through the command queue, while this one puts individual {@link ModelPart}s onto individual cubic
 * bones. So the draw is BBS's, but everything it draws is read out of vanilla's data - which shapes
 * an equipment asset has, and which texture each of them uses, come from the
 * {@link EquipmentModelLoader} index rather than from a path built out of the asset id.
 */
public class ArmorRenderer
{
    /** Per-slot armor models (1.21.4+: the inner/outer pair became head/chest/legs/feet layers). */
    private final EquipmentModelData<BipedEntityModel> models;
    private final ModelPart elytra;
    private final ModelPart elytraLeftWing;
    private final ModelPart elytraRightWing;

    /**
     * Vanilla's own {@code assets/<ns>/equipment/<asset>.json} index. It is what says WHICH shapes a
     * piece of equipment has (a humanoid body, wings, a horse's barding) and which texture each of
     * them draws with - guessing that from the asset id is what put a missing texture on the arms.
     */
    private final EquipmentModelLoader equipment;

    public ArmorRenderer(EquipmentModelData<BipedEntityModel> models, ModelPart elytra, EquipmentModelLoader equipment)
    {
        this.models = models;
        this.elytra = elytra;
        this.elytraLeftWing = elytra.getChild("left_wing");
        this.elytraRightWing = elytra.getChild("right_wing");
        this.equipment = equipment;
    }

    public void renderArmorSlot(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, EquipmentSlot armorSlot, ArmorType type, int light)
    {
        ItemStack itemStack = entity.getEquipmentStack(armorSlot);

        if (itemStack.isEmpty())
        {
            return;
        }

        /* 1.21.4+: armor is identified by the EQUIPPABLE component + its asset id, not by an
         * ArmorItem subclass. We still gate on the slot matching so this only fires for the armour
         * the model actually wears in that slot. */
        EquippableComponent equippable = itemStack.get(DataComponentTypes.EQUIPPABLE);

        if (equippable == null || equippable.slot() != armorSlot || equippable.assetId().isEmpty())
        {
            return;
        }

        RegistryKey<EquipmentAsset> assetId = equippable.assetId().get();
        EquipmentModel model = this.equipment.get(assetId);

        if (!model.getLayers(EquipmentModel.LayerType.WINGS).isEmpty())
        {
            /* Wings hang off the torso alone. Both arms share EquipmentSlot.CHEST with it (see
             * ArmorType), so without this an elytra was drawn three times - and the two arm draws
             * went down the humanoid path below, which has no texture for it: that missing texture
             * is what turned the body black. */
            if (type == ArmorType.CHEST)
            {
                this.renderElytra(matrices, vertexConsumers, entity, itemStack, model, light);
            }

            return;
        }

        boolean innerModel = this.usesInnerModel(armorSlot);
        EquipmentModel.LayerType layerType = innerModel ? EquipmentModel.LayerType.HUMANOID_LEGGINGS : EquipmentModel.LayerType.HUMANOID;
        List<EquipmentModel.Layer> layers = model.getLayers(layerType);

        if (layers.isEmpty())
        {
            /* Worn, but not shaped like a body: a carved pumpkin, a mob head, a horse's barding on
             * something that is not a horse. Vanilla draws nothing for those either. */
            return;
        }

        BipedEntityModel bipedModel = this.getModel(armorSlot);
        ModelPart part = this.getPart(bipedModel, type);

        bipedModel.setVisible(true);

        part.originX = part.originY = part.originZ = 0F;
        part.pitch = part.yaw = part.roll = 0F;
        part.xScale = part.yScale = part.zScale = 1F;

        /* One draw per declared layer, in order: leather is a grey mask plus an undyed overlay, and
         * the dye (or the material's own default tint) belongs to the first of the two. */
        for (EquipmentModel.Layer layer : layers)
        {
            this.renderArmorLayer(part, matrices, vertexConsumers, light, layer.getFullTextureId(layerType), this.tint(itemStack, layer));
        }

        ArmorTrim trim = itemStack.get(DataComponentTypes.TRIM);

        if (trim != null)
        {
            this.renderTrim(part, assetId, matrices, vertexConsumers, light, trim, innerModel);
        }

        if (itemStack.hasGlint())
        {
            this.renderGlint(part, matrices, vertexConsumers, light);
        }
    }

    /* Vanilla elytra, verified against 1.20.4 bytecode: ElytraFeatureRenderer.render
     * (translate 0,0,0.125; armor cutout layer; glint) + ElytraEntityModel.setAngles
     * (non-player branch: standing / sneaking / fall flying wing angles). 1.21.11 keeps the
     * same numbers, it only moved them onto the entity's own ElytraFlightController - which an
     * actor driven by keyframes never ticks, so they are computed here as before. */
    private void renderElytra(MatrixStack matrices, VertexConsumerProvider vertexConsumers, IEntity entity, ItemStack itemStack, EquipmentModel model, int light)
    {
        float pitch = 0.2617994F;
        float roll = -0.2617994F;
        float originY = 0F;
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
            originY = 3F;
            yaw = 0.08726646F;
        }

        this.elytraLeftWing.originY = originY;
        this.elytraLeftWing.pitch = pitch;
        this.elytraLeftWing.yaw = yaw;
        this.elytraLeftWing.roll = roll;
        this.elytraRightWing.originY = originY;
        this.elytraRightWing.pitch = pitch;
        this.elytraRightWing.yaw = -yaw;
        this.elytraRightWing.roll = -roll;

        matrices.push();
        matrices.translate(0F, 0F, 0.125F);

        /* The texture comes off the wings layer, not a hardcoded path. (Vanilla swaps in the wearer's
         * own cape when the layer asks for it - an actor has none, so the default stands.) */
        EquipmentModel.Layer wings = model.getLayers(EquipmentModel.LayerType.WINGS).get(0);
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayers.armorCutoutNoCull(wings.getFullTextureId(EquipmentModel.LayerType.WINGS)));

        this.elytra.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);

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

    private void renderArmorLayer(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, Identifier texture, Color color)
    {
        /* The armor layer factories came back as static RenderLayers.armorCutoutNoCull (the 1.21.4
         * rewrite moved them off RenderLayer, it didn't remove them). Same draw as 1.21.1: the layer
         * carries the texture, the recolor consumer carries the dye. */
        VertexConsumer base = vertexConsumers.getBuffer(RenderLayers.armorCutoutNoCull(texture));
        VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, color);

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
    }

    /**
     * The colour one layer draws with. Only a layer that declares itself dyeable takes one: the dye
     * on the stack when there is one, and the material's own default otherwise - a leather texture
     * is a grey mask since 1.21.4, so an undyed piece drawn white would have come out grey.
     */
    private Color tint(ItemStack itemStack, EquipmentModel.Layer layer)
    {
        if (layer.dyeable().isEmpty())
        {
            return Color.white();
        }

        DyedColorComponent dyed = itemStack.get(DataComponentTypes.DYED_COLOR);
        int rgb = dyed != null ? dyed.rgb() : layer.dyeable().get().colorWhenUndyed().orElse(0xFFFFFF);

        return new Color((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, 1F);
    }

    private void renderTrim(ModelPart part, RegistryKey<EquipmentAsset> assetId, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ArmorTrim trim, boolean leggings)
    {
        /* 1.21.4+: the trim texture id comes off the trim itself and the atlas lives in
         * AtlasManager (BakedModelManager.getAtlas is gone). */
        SpriteAtlasTexture atlas = MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE);
        Sprite sprite = atlas.getSprite(trim.getTextureId(leggings ? "leggings" : "armor", assetId));
        VertexConsumer vertexConsumer = sprite.getTextureSpecificVertexConsumer(vertexConsumers.getBuffer(TexturedRenderLayers.getArmorTrims(trim.pattern().value().decal())));

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
    }

    private void renderGlint(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        part.render(matrices, vertexConsumers.getBuffer(RenderLayers.armorEntityGlint()), light, OverlayTexture.DEFAULT_UV);
    }

    private BipedEntityModel getModel(EquipmentSlot slot)
    {
        return this.models.getModelData(slot);
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }
}
