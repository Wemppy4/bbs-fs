package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.mob.MobRenderContext;
import mchorse.bbs_mod.forms.renderers.mob.MobPickerVertexConsumer;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.forms.renderers.mob.MobRigMatrices;
import mchorse.bbs_mod.forms.renderers.mob.MobRigs;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.EntityInvoker;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private final MatrixCache bones = new MatrixCache();

    private Entity entity;

    private String lastId = "";
    private String lastNBT = "";
    private boolean lastSlim;

    public float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    public static MobRig getRig(MobForm form)
    {
        return FormUtilsClient.getRenderer(form) instanceof MobFormRenderer renderer ? renderer.getRig() : null;
    }

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    @Override
    public List<String> getBones()
    {
        MobRig rig = this.getRig();

        return rig == null ? super.getBones() : rig.getGroupKeysInHierarchyOrder();
    }

    @Override
    public IBoneHierarchy getBoneHierarchy()
    {
        return this.getRig();
    }

    /**
     * The skeleton of the vanilla model this form renders through, or null while there is no
     * entity yet or the entity does not render through a living entity renderer.
     */
    public MobRig getRig()
    {
        this.ensureEntity();

        if (this.entity != null && MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity) instanceof LivingEntityRenderer renderer)
        {
            return MobRigs.of(renderer.getModel());
        }

        return null;
    }

    /**
     * Claims one pick id for the form and one per bone, in the order the parts drew with (see
     * {@code MobRenderContext.partLight}). Same contract as the model form's
     * {@code ModelInstance.fillStencilMap}: the shader adds the part's offset to the form's base
     * id, so the registration order here IS the decoding table.
     */
    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        MobRig rig = this.getRig();

        context.stencilMap.addPicking(this.form, "");

        if (rig != null)
        {
            for (ModelPart part : rig.ordered())
            {
                context.stencilMap.addPicking(this.form, rig.name(part));
            }
        }
    }

    private boolean hasBoundBodyParts()
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            if (!part.bone.get().isEmpty())
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Body parts bound to a bone ride that bone's frame; the rest stay exactly where they were,
     * in the form's own space. Only parts that name a bone the model actually has move, so nothing
     * that was authored before mob bones existed shifts underfoot.
     */
    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Matrix4f matrix = part.filterBoneMatrix(this.bones.get(part.bone.get()).matrix());

            if (matrix == null)
            {
                this.renderBodyPart(part, context);

                continue;
            }

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            MatrixStackUtils.multiply(context.stack, matrix);
            if (context.world != null)
            {
                MatrixStackUtils.multiply(context.world, matrix);
            }

            this.renderBodyPart(part, context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }

        this.bones.clear();
    }

    /**
     * The same bones, asked for outside a render - what the gizmo, the anchor system, trackers and
     * the motion path read. Body parts recurse through their bone's frame, so a form anchored to a
     * mob's head resolves under {@code <path>/head} the way a model form's bones do.
     */
    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        this.ensureEntity();

        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.peek().getPositionMatrix());

        matrices.put(prefix, mm, oo);

        MatrixCache collected = new MatrixCache();

        MobRigMatrices.evaluate(this.entity, this.getRig(), this.form.pose.get(), this.form.poseOverlay.get(), transition, collected);

        for (Map.Entry<String, MatrixCacheEntry> entry : collected.entrySet())
        {
            Matrix4f matrix = new Matrix4f();
            Matrix4f o = new Matrix4f();

            stack.push();
            MatrixStackUtils.multiply(stack, entry.getValue().matrix());
            matrix.set(stack.peek().getPositionMatrix());
            stack.pop();

            stack.push();
            MatrixStackUtils.multiply(stack, entry.getValue().origin());
            o.set(stack.peek().getPositionMatrix());
            stack.pop();

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), matrix, o);
        }

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form == null)
            {
                continue;
            }

            Matrix4f matrix = part.filterBoneMatrix(collected.get(part.bone.get()).matrix());

            stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, matrix);
            }

            MatrixStackUtils.applyTransform(stack, part.transform.get());
            FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, part.getId()), transition);

            stack.pop();
        }

        stack.pop();
    }

    private void bindTexture()
    {
        Link link = this.form.texture.get();

        if (link != null)
        {
            BBSModClient.getTextures().bindTexture(link);
        }
    }

    private void ensureEntity()
    {
        String id = this.form.mobID.get();
        String nbt = this.form.mobNBT.get();
        boolean slim = this.form.slim.get();

        if (!this.lastId.equals(id) || !this.lastNBT.equals(nbt) || slim != this.lastSlim)
        {
            this.lastId = id;
            this.lastNBT = nbt;
            this.lastSlim = slim;
            this.entity = null;
        }

        if (this.entity != null)
        {
            return;
        }

        NbtCompound compound = new NbtCompound();

        try
        {
            compound = (new StringNbtReader(new StringReader(nbt))).parseCompound();
        }
        catch (Exception e)
        {}

        this.entity = Registries.ENTITY_TYPE.get(new Identifier(id)).create(MinecraftClient.getInstance().world);

        if (this.entity == null && this.form.isPlayer())
        {
            this.entity = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, slim ? SLIM : WIDE);
            this.entity.getDataTracker().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }

        if (this.entity != null)
        {
            compound.putString("id", id);
            this.entity.readNbt(compound);
            this.entity.noClip = true;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            float scale = this.form.uiScale.get();
            float width = this.entity.getWidth();
            float height = this.entity.getHeight();

            scale = scale * Math.min(1.8F / Math.max(width, height), 1F);

            this.applyTransforms(uiMatrix, context.getTransition());
            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            if (!this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            BooleanHolder first = new BooleanHolder();

            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                if (!first.bool)
                {
                    this.bindTexture();

                    first.bool = true;
                }
            });

            MobRenderContext mob = MobRenderContext.push(this.getRig(), this.form.pose.get(), this.form.poseOverlay.get());

            consumers.setUI(true);

            try
            {
                MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, context.getTransition(), stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE);
                consumers.draw();
            }
            finally
            {
                mob.pop();
            }

            consumers.setUI(false);

            CustomVertexConsumerProvider.clearRunnables();

            stack.pop();

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            /* The vanilla entity pipeline below leaves its own value in the
             * global model-view matrix, so the non-UI cleanup used to wipe it
             * to identity. That identity leaked out of any render that is NOT
             * the film viewport: a placed model block with a mob form nuked
             * the matrix every world frame and the whole UI over it fell
             * apart. Snapshot and restore instead - the film viewport enters
             * here with identity anyway, so its old contract holds. */
            Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());

            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
            int light = context.light;
            BooleanHolder first = new BooleanHolder();

            if (context.isPicking())
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    if (!first.bool)
                    {
                        this.bindTexture();

                        first.bool = true;
                    }

                    /* The picker shader must be (re)applied for every layer, not just the
                     * first one. Entities like the piglin render held items (e.g. the golden
                     * sword) through Minecraft's own item rendering, which adds extra render
                     * layers. If those layers aren't forced onto the picker shader, they get
                     * drawn with vanilla item shaders, leaking GL/shader state that breaks the
                     * picking of any subsequent entity rendered into the stencil framebuffer. */
                    this.setupTarget(context, BBSShaders.getPickerModelsProgram());
                    RenderSystem.setShader(BBSShaders::getPickerModelsProgram);
                });

                /* Sodium's entity fast path cancels ModelPart.render and draws the whole subtree
                 * itself with ONE light value, which is precisely the channel the per-bone ids
                 * ride. It steps aside for a consumer it cannot convert, so the pick pass - one
                 * entity, off the hot path - hands it a plain wrapper and gets vanilla's
                 * part-by-part recursion back. */
                consumers.setSubstitute(MobPickerVertexConsumer::new);

                light = 0;
            }
            else
            {
                CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
                {
                    if (!first.bool)
                    {
                        this.bindTexture();

                        first.bool = true;
                    }
                });
            }

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            if (this.form.mobID.get().equals("minecraft:ender_dragon"))
            {
                context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }
            }

            if (this.entity instanceof LivingEntity entity)
            {
                int u = context.overlay & '\uffff';
                int v = context.overlay >> 16 & '\uffff';

                entity.hurtTime = v != 10 ? 100 : 0;
            }

            /* Publishing the form's camera-space origin opts its translucent layers (slime
             * bodies, ghost textures) into the deferred sorted pass. */
            if (!context.isPicking())
            {
                Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
            }

            this.bones.clear();

            /* Where the bones are, for the body parts riding them. The same evaluation the gizmo
             * and the anchors read, rather than a capture taken during the render: with Sodium
             * installed vanilla's part recursion does not run at all in a normal frame, and
             * sharing the one evaluation also means the gizmo and the part it moves cannot
             * disagree. */
            if (this.hasBoundBodyParts())
            {
                MobRigMatrices.evaluate(this.entity, this.getRig(), this.form.pose.get(), this.form.poseOverlay.get(), context.getTransition(), this.bones);
            }

            MobRenderContext mob = MobRenderContext.push(this.getRig(), this.form.pose.get(), this.form.poseOverlay.get()).picking(context.stencilMap);

            try
            {
                MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, context.getTransition(), context.stack, consumers, light);
            }
            finally
            {
                mob.pop();
                consumers.setSubstitute(null);
            }

            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);
            CustomVertexConsumerProvider.clearRunnables();

            context.stack.pop();

            if (context.world != null)
            {
                context.world.pop();
            }

            /* When this MobForm is a body part rendered inside a 2D list/preview (context.ui),
             * it reaches here through the 3D path. The viewport cleanup below would leak into
             * the ongoing 2D batch: resetting the shared model-view matrix to identity drops
             * the GUI transform, so every UI element drawn afterwards lands off-screen — the
             * "half the UI disappears" bug when a MobForm is nested under a ModelForm. In the
             * UI, match the known-good top-level renderInUI path (just fix the depth func,
             * leave the model-view matrix untouched). The 3D viewport keeps its cleanup. */
            if (context.ui)
            {
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
            }
            else
            {
                RenderSystem.enableDepthTest();
                RenderSystem.getModelViewMatrix().set(modelView);
            }
        }
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            this.entity.tick();

            this.entity.prevPitch = this.prevPitch;
            this.entity.prevYaw = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.prevHeadYaw = this.prevYawHead;
                livingEntity.prevBodyYaw = 0F;

                /* Limb swing is so ugly */
                if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor a && entity.getLimbAnimator() instanceof LimbAnimatorAccessor b)
                {
                    a.setPrevSpeed(b.getPrevSpeed());
                    a.setSpeed(b.getSpeed());
                    a.setPos(b.getPos());
                }

                /* Arm swing */
                float handSwingProgress = entity.getHandSwingProgress(0F);

                if (handSwingProgress < this.prevHandSwing)
                {
                    this.prevHandSwing = 0;
                }

                if (handSwingProgress > 0 && this.prevHandSwing == 0)
                {
                    livingEntity.swingHand(Hand.MAIN_HAND);
                }

                this.prevHandSwing = handSwingProgress;
            }

            this.entity.setYaw(0F);
            this.entity.setHeadYaw(entity.getHeadYaw() - entity.getBodyYaw());
            this.entity.setPitch(entity.getPitch());
            this.entity.setBodyYaw(0F);

            this.entity.setPos(entity.getX(), entity.getY(), entity.getZ());
            this.entity.setOnGround(entity.isOnGround());
            this.entity.setSneaking(entity.isSneaking());
            this.entity.setSprinting(entity.isSprinting());
            this.entity.setSwimming(entity.isSwimming());
            ((EntityInvoker) this.entity).bbs$setFlag(EntityState.FALL_FLYING_FLAG, entity.isFallFlying());
            this.entity.setPose(EntityState.pose(entity));
            this.entity.equipStack(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
            this.entity.equipStack(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
            this.entity.equipStack(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
            this.entity.equipStack(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
            this.entity.equipStack(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
            this.entity.equipStack(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            this.entity.age = entity.getAge();
            this.entity.noClip = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }

    private static class BooleanHolder
    {
        public boolean bool;
    }
}
