package mchorse.bbs_mod.cubic.render.vanilla;

import com.google.common.collect.Maps;
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
import net.minecraft.client.render.model.BakedModelManager;
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

import java.util.Map;

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
 *   <li>{@code BakedModelManager.getAtlas(Identifier)} was removed (atlases live in
 *       {@code AtlasManager}), so the armor-trims sprite atlas can no longer be fetched here.</li>
 * </ul>
 *
 * <p>Faithfully reproducing per-bone armor + trim + glint on top of cubic bones now requires the new
 * {@code EquipmentRenderer}/{@code OrderedRenderCommandQueue} pipeline, which is a real redesign (it
 * renders a whole {@code Model<S>} through a command queue rather than individual {@code ModelPart}s).
 * That is out of scope for a build-only port, so the draw bodies below are neutralized with
 * {@code TODO(1.21.11 render)} while the public API ({@link #ArmorRenderer} constructor and
 * {@link #renderArmorSlot}) is kept stable so callers compile unchanged. Mechanical migrations that
 * still have clean equivalents (equippable-based detection, {@code ModelPart.pivotX -> originX},
 * texture-id derivation, bone selection) are done so the structure is ready to wire up at runtime.
 */
public class ArmorRenderer
{
    private static final Map<String, Identifier> ARMOR_TEXTURE_CACHE = Maps.newHashMap();
    private final BipedEntityModel innerModel;
    private final BipedEntityModel outerModel;
    private final BakedModelManager bakery;

    public ArmorRenderer(BipedEntityModel innerModel, BipedEntityModel outerModel, BakedModelManager bakery)
    {
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        /* TODO(1.21.11 render): BakedModelManager.getAtlas(ARMOR_TRIMS_ATLAS_TEXTURE) was removed (the
         * trims atlas now lives in AtlasManager). The bakery is retained for when the trim sprite
         * lookup is rewired through the new equipment-asset pipeline. */
        this.bakery = bakery;
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

        boolean innerModel = this.usesInnerModel(armorSlot);
        BipedEntityModel bipedModel = this.getModel(armorSlot);
        ModelPart part = this.getPart(bipedModel, type);

        bipedModel.setVisible(true);

        part.originX = part.originY = part.originZ = 0F;
        part.pitch = part.yaw = part.roll = 0F;
        part.xScale = part.yScale = part.zScale = 1F;

        DyedColorComponent dyed = itemStack.get(DataComponentTypes.DYED_COLOR);

        if (dyed != null)
        {
            int color = dyed.rgb();
            float r = (float) (color >> 16 & 255) / 255.0F;
            float g = (float) (color >> 8 & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;

            this.renderArmorParts(part, matrices, vertexConsumers, light, assetId, innerModel, r, g, b, null);
            this.renderArmorParts(part, matrices, vertexConsumers, light, assetId, innerModel, 1F, 1F, 1F, "overlay");
        }
        else
        {
            this.renderArmorParts(part, matrices, vertexConsumers, light, assetId, innerModel, 1F, 1F, 1F, null);
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

    private void renderArmorParts(ModelPart part, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, RegistryKey<EquipmentAsset> assetId, boolean secondTextureLayer, float red, float green, float blue, String overlay)
    {
        /* The armor layer factories came back as static RenderLayers.armorCutoutNoCull (the 1.21.4
         * rewrite moved them off RenderLayer, it didn't remove them). Same draw as 1.21.1: the layer
         * carries the texture, the recolor consumer carries the dye. */
        VertexConsumer base = vertexConsumers.getBuffer(RenderLayers.armorCutoutNoCull(this.getArmorTexture(assetId, secondTextureLayer, overlay)));
        VertexConsumer vertexConsumer = new RecolorVertexConsumer(base, new Color(red, green, blue, 1F));

        part.render(matrices, vertexConsumer, light, OverlayTexture.DEFAULT_UV);
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
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.LEGS;
    }

    private Identifier getArmorTexture(RegistryKey<EquipmentAsset> assetId, boolean secondLayer, String overlay)
    {
        /* 1.21.4+: armor textures live under textures/entity/equipment/<layer>/<asset>(.png), keyed by
         * the equipment asset id rather than the old textures/models/armor/<material>_layer_N path. We
         * build a best-effort id from the asset path so the draw can be wired up later; the exact layer
         * folder is resolved by the equipment-model system at runtime. */
        String assetName = assetId.getValue().getPath();
        String layer = secondLayer ? "humanoid_leggings" : "humanoid";
        String id = "textures/entity/equipment/" + layer + "/" + assetName + (overlay == null ? "" : "_" + overlay) + ".png";

        Identifier found = ARMOR_TEXTURE_CACHE.get(id);

        if (found == null)
        {
            found = Identifier.of("minecraft", id);
            ARMOR_TEXTURE_CACHE.put(id, found);
        }

        return found;
    }
}
