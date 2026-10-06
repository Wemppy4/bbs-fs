package mchorse.bbs_mod.client.renderer.item;

import mchorse.bbs_mod.forge.CommonProxy;
import mchorse.bbs_mod.forge.GunClientHandler;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.MatrixStack;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockEditorMenu;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.pose.Transform;
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

/** Stateful original gun form rendering, routed through Forge's perspective-aware TEISR. */
public final class GunItemRenderer extends TileEntityItemStackRenderer {
    public static final GunItemRenderer INSTANCE=new GunItemRenderer();
    private final Map<NBTTagCompound,Entry> entries=new HashMap<>();
    private ItemCameraTransforms.TransformType mode=ItemCameraTransforms.TransformType.NONE;
    public static final class Entry {
        public final GunProperties properties;
        public final StubEntity entity=new StubEntity();
        private final NBTTagCompound source;
        private int expiry=20;
        Entry(ItemStack stack) { this.properties=GunProperties.get(stack);this.source=stack.hasTagCompound()?stack.getTagCompound().copy():null; }
    }
    public Entry get(ItemStack stack) {
        if(stack==null||stack.isEmpty()||stack.getItem()!=CommonProxy.GUN_ITEM)return null;
        Entry entry=this.entries.get(stack.getTagCompound());
        if(entry==null) { entry=new Entry(stack);this.entries.put(entry.source,entry); }
        entry.entity.setWorld(Minecraft.getMinecraft().world);entry.properties.getEquipment().apply(entry.entity);entry.expiry=20;
        return entry;
    }
    public void tick() {
        Iterator<Entry> iterator=this.entries.values().iterator();
        while(iterator.hasNext()) {
            Entry entry=iterator.next();
            if(--entry.expiry<=0&&!UIModelBlockEditorMenu.isEditing(entry.properties)){iterator.remove();continue;}
            entry.entity.update();entry.properties.update(entry.entity);
        }
    }
    public void clear() { this.entries.clear(); }
    @Override public void renderByItem(ItemStack stack,float partialTicks) {
        Entry entry=this.get(stack);if(entry==null)return;
        GunProperties properties=entry.properties;
        ItemCameraTransforms.TransformType transformMode=this.mode;
        Form form=properties.getForm(transformMode);Transform transform=properties.getTransform(transformMode);
        boolean firstPerson=transformMode==ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND||transformMode==ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND;
        if(firstPerson&&GunClientHandler.getZoom()!=null&&properties.getZoomForm()!=null) { form=properties.getZoomForm();transform=properties.zoomTransform; }
        if(UIScreen.getCurrentMenu() instanceof UIModelBlockEditorMenu) {
            UIModelBlockEditorMenu editor=(UIModelBlockEditorMenu)UIScreen.getCurrentMenu();
            if(editor.getGunProperties()!=null&&editor.currentSection==editor.sectionZoom) { form=editor.getGunProperties().getZoomForm();transform=editor.getGunProperties().zoomTransform; }
        }
        if(form==null)return;
        Minecraft mc=Minecraft.getMinecraft();MatrixStack matrices=new MatrixStack();
        matrices.peek().getPositionMatrix().set(mchorse.bbs_mod.forge.studio.NativeTextureRenderer.currentMatrix()).translate(.5F,0F,.5F);
        matrices.peek().getNormalMatrix().set(matrices.peek().getPositionMatrix()).invert().transpose();
        if(transformMode==ItemCameraTransforms.TransformType.GUI)
        {
            org.joml.Vector3f scale=matrices.peek().getNormalMatrix().getScale(new org.joml.Vector3f());
            matrices.peek().getNormalMatrix().scale(1F/scale.x,
                (matrices.peek().getPositionMatrix().determinant3x3()<0F?-1F:1F)/scale.y,1F/scale.z);
        }
        MatrixStackUtils.applyTransform(matrices,transform);
        int matrixMode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();
        try(mchorse.bbs_mod.graphics.render.RenderSystem.LightScope lighting=transformMode==ItemCameraTransforms.TransformType.GUI
            ?mchorse.bbs_mod.graphics.render.RenderSystem.guiFlatLighting():null) {
            GlStateManager.enableDepth();
            FormUtilsClient.render(form,new FormRenderingContext().set(FormRenderType.fromModelMode(transformMode),entry.entity,matrices,
                transformMode==ItemCameraTransforms.TransformType.GUI||mc.player==null?0xF000F0:mc.player.getBrightnessForRender(),10<<16,partialTicks).camera(mc.getRenderViewEntity()));
        } finally {
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(matrixMode);
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
        }
    }
    public static final class Baked implements IBakedModel {
        private final IBakedModel fallback;
        public Baked(IBakedModel fallback) { this.fallback=fallback; }
        @Override public List<BakedQuad> getQuads(IBlockState state,EnumFacing face,long seed) { return Collections.emptyList(); }
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean isBuiltInRenderer() { return true; }
        @Override public TextureAtlasSprite getParticleTexture() { return this.fallback.getParticleTexture(); }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
        @Override public Pair<? extends IBakedModel,javax.vecmath.Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) { INSTANCE.mode=type;return Pair.of(this,null); }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
    }
}
