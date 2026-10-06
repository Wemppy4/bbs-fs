package mchorse.bbs_mod.forge;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** BBS's four item forms over Forge TEISR and perspective-aware baked models.
 * The expiring state cache follows Blockbuster's TileEntityModelItemStackRenderer. */
public final class ModelItemRenderer extends TileEntityItemStackRenderer
{
    public static final ModelItemRenderer INSTANCE = new ModelItemRenderer();
    /* Vanilla can render a copy of the equipped stack. Share the editing state by
     * immutable NBT, as the original 1.12 model-item renderer does. */
    private final Map<NBTTagCompound, Entry> entries = new HashMap<>();
    private ItemCameraTransforms.TransformType mode = ItemCameraTransforms.TransformType.NONE;
    public static final class Entry
    {
        public final ModelTileEntity tile = new ModelTileEntity();
        public final StubEntity entity = new StubEntity();
        private NBTTagCompound source;
        private int expiry = 20;
    }
    public Entry get(ItemStack stack)
    {
        if (stack == null || stack.isEmpty() || stack.getItem() != net.minecraft.item.Item.getItemFromBlock(CommonProxy.MODEL_BLOCK)) return null;
        NBTTagCompound source = stack.getTagCompound();
        Entry entry = this.entries.get(source);
        if (entry == null)
        {
            entry = new Entry();
            entry.source = source == null ? null : source.copy();
            if (source != null) entry.tile.readFromNBT(source.getCompoundTag("BlockEntityTag"));
            this.entries.put(entry.source, entry);
        }
        entry.entity.setWorld(Minecraft.getMinecraft().world);
        entry.tile.getProperties().getEquipment().apply(entry.entity);
        entry.expiry = 20;
        return entry;
    }
    public void tick()
    {
        Iterator<Entry> iterator = this.entries.values().iterator();
        while (iterator.hasNext())
        {
            Entry entry = iterator.next();
            if (--entry.expiry <= 0 && !mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu.isEditing(entry.tile.getProperties())) { iterator.remove(); continue; }
            entry.entity.update();
            entry.tile.getProperties().update(entry.entity);
        }
    }
    public void clear() { this.entries.clear(); }
    @Override public void renderByItem(ItemStack stack, float partialTicks)
    {
        Entry entry = this.get(stack);
        if (entry == null) return;
        ModelProperties properties = entry.tile.getProperties();
        ItemCameraTransforms.TransformType transformMode = this.mode;
        Form form = properties.getForm(transformMode);
        if (form == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().set(mchorse.bbs_mod.forge.studio.NativeTextureRenderer.currentMatrix()).translate(.5F, 0F, .5F);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        if(transformMode==ItemCameraTransforms.TransformType.GUI)
        {
            /* Modern DrawContext reflects GUI Y in position only; native RenderItem
             * folds it into MODELVIEW. Keep that reflection out of BBS normals. */
            org.joml.Vector3f scale=matrices.peek().getNormalMatrix().getScale(new org.joml.Vector3f());
            matrices.peek().getNormalMatrix().scale(1F/scale.x,
                (matrices.peek().getPositionMatrix().determinant3x3()<0F?-1F:1F)/scale.y,1F/scale.z);
        }
        MatrixStackUtils.applyTransform(matrices, properties.getTransform(transformMode));
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        try(mchorse.bbs_mod.graphics.render.RenderSystem.LightScope lighting=transformMode==ItemCameraTransforms.TransformType.GUI
            ?mchorse.bbs_mod.graphics.render.RenderSystem.guiFlatLighting():null)
        {
            GlStateManager.enableDepth();
            FormUtilsClient.render(form, new FormRenderingContext().set(FormRenderType.fromModelMode(transformMode), entry.entity, matrices,
                transformMode == ItemCameraTransforms.TransformType.GUI || mc.player == null ? 0xF000F0 : mc.player.getBrightnessForRender(), 10 << 16, mc.getRenderPartialTicks()).camera(mc.getRenderViewEntity()));
        }
        finally
        {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(matrixMode);
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        }
    }
    public static final class Baked implements IBakedModel
    {
        private final IBakedModel fallback;
        private final boolean custom;
        public Baked(IBakedModel fallback) { this(fallback, false); }
        private Baked(IBakedModel fallback, boolean custom) { this.fallback = fallback; this.custom = custom; }
        @Override public List<BakedQuad> getQuads(IBlockState state, EnumFacing face, long seed) { return fallback.getQuads(state, face, seed); }
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return custom || fallback.isGui3d(); }
        @Override public boolean isBuiltInRenderer() { return custom; }
        @Override public TextureAtlasSprite getParticleTexture() { return fallback.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return custom ? ItemCameraTransforms.DEFAULT : fallback.getItemCameraTransforms(); }
        @Override public Pair<? extends IBakedModel, javax.vecmath.Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type)
        {
            INSTANCE.mode = type;
            if (!custom) return fallback.handlePerspective(type);
            return Pair.of(this, null);
        }
        @Override public ItemOverrideList getOverrides()
        {
            return new ItemOverrideList(Collections.emptyList())
            {
                @Override public IBakedModel handleItemState(IBakedModel original, ItemStack stack, World world, EntityLivingBase entity)
                {
                    Entry entry = INSTANCE.get(stack);
                    ModelProperties properties = entry == null ? null : entry.tile.getProperties();
                    boolean hasForm = properties != null && (properties.getForm() != null || properties.getFormInventory() != null || properties.getFormFirstPerson() != null || properties.getFormThirdPerson() != null);
                    return hasForm ? new Baked(fallback, true) : Baked.this;
                }
            };
        }
    }
}
