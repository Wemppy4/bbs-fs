package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.QueueDispatch;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MobFormRenderer extends FormRenderer<MobForm> implements ITickable
{
    /** Per entity class, its model's ModelParts by field name — resolved reflectively once in {@link #getBones}. */
    private static final Map<Class, Map<String, ModelPart>> parts = new HashMap<>();

    /**
     * Original transforms of the ModelParts the current pose touched, keyed by part. Filled by
     * {@link #applyCurrentPose} (called from the ModelCommandRenderer mixin right after the
     * model's {@code setAngles}) and drained by {@link #restorePosedParts} at that command's end,
     * so the shared vanilla model instances never keep BBS pose residue.
     */
    private static final Map<ModelPart, Transform> cache = new HashMap<>();

    /** The merged pose (base + overlay) of the mob form being flushed right now, or null. */
    private static Pose currentPose;

    /** The posed entity's part map — resolved once per render, read by {@link #applyCurrentPose}. */
    private static Map<String, ModelPart> currentParts;

    public static final GameProfile WIDE = new GameProfile(UUID.fromString("b99a2400-28a8-4288-92dc-924beafbf756"), "McHorseYT");
    public static final GameProfile SLIM = new GameProfile(UUID.fromString("5477bd28-e672-4f87-a209-c03cf75f3606"), "osmiq");

    private Entity entity;

    private String lastId = "";
    private String lastNBT = "";
    private boolean lastSlim;

    public float prevHandSwing;
    private float prevYawHead;
    private float prevPitch;

    public MobFormRenderer(MobForm form)
    {
        super(form);
    }

    /**
     * Apply the current mob form's pose on top of the freshly set model angles. Runs from
     * {@code ModelCommandRendererMixin} at flush time — the 1.21.2+ queue calls
     * {@code Model.setAngles(state)} when the command RENDERS, not when it is submitted, so this
     * is the only moment the parts hold their final vanilla angles. The 1.21.1 equivalent hooked
     * {@code LivingEntityRenderer.render} after its (immediate) setAngles.
     *
     * <p>Only parts of the posed entity's own model match (identity through {@link #getParts}),
     * so armor/held-item model commands flushed in the same cycle pass through untouched. That
     * also means armor no longer inherits the pose the way 1.21.1's copy-angles chain did — the
     * queue re-derives armor angles from the render state, out of BBS's reach.
     */
    public static void applyCurrentPose()
    {
        Pose pose = currentPose;
        Map<String, ModelPart> partMap = currentParts;

        if (pose == null || partMap == null)
        {
            return;
        }

        for (Map.Entry<String, ModelPart> entry : partMap.entrySet())
        {
            ModelPart value = entry.getValue();
            PoseTransform poseTransform = pose.transforms.get(entry.getKey());

            if (poseTransform == null)
            {
                continue;
            }

            Transform transform = new Transform();

            transform.translate.x = value.originX;
            transform.translate.y = value.originY;
            transform.translate.z = value.originZ;
            transform.rotate.x = value.pitch;
            transform.rotate.y = value.yaw;
            transform.rotate.z = value.roll;
            transform.scale.x = value.xScale;
            transform.scale.y = value.yScale;
            transform.scale.z = value.zScale;

            /* Vanilla ModelPart holds euler pitch/yaw/roll only, so a quaternion pose bone is
             * decomposed to its euler equivalent instead of reading the stale rotate triple. */
            Vector3f rotation = poseTransform.getEulerRotation(new Vector3f());

            value.originX += poseTransform.translate.x;
            value.originY += poseTransform.translate.y;
            value.originZ += poseTransform.translate.z;
            value.pitch += rotation.x;
            value.yaw += rotation.y;
            value.roll += rotation.z;
            value.xScale += poseTransform.scale.x - 1F;
            value.yScale += poseTransform.scale.y - 1F;
            value.zScale += poseTransform.scale.z - 1F;

            cache.put(value, transform);
        }
    }

    /** Undo {@link #applyCurrentPose} — the model instances are shared with the whole game. */
    public static void restorePosedParts()
    {
        for (Map.Entry<ModelPart, Transform> entry : cache.entrySet())
        {
            Transform transform = entry.getValue();
            ModelPart value = entry.getKey();

            value.originX = transform.translate.x;
            value.originY = transform.translate.y;
            value.originZ = transform.translate.z;
            value.pitch = transform.rotate.x;
            value.yaw = transform.rotate.y;
            value.roll = transform.rotate.z;
            value.xScale = transform.scale.x;
            value.yScale = transform.scale.y;
            value.zScale = transform.scale.z;
        }

        cache.clear();
    }

    @Override
    public List<String> getBones()
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            Map<String, ModelPart> stringModelPartMap = parts.get(this.entity.getClass());

            if (stringModelPartMap == null)
            {
                stringModelPartMap = new HashMap<>();

                if (MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity) instanceof LivingEntityRenderer renderer)
                {
                    EntityModel model = renderer.getModel();
                    Set<Field> fields = new HashSet<>();
                    Class aClass = model.getClass();

                    while (aClass != Object.class)
                    {
                        for (Field field : aClass.getDeclaredFields())
                        {
                            fields.add(field);
                        }

                        aClass = aClass.getSuperclass();
                    }

                    for (Field declaredField : fields)
                    {
                        if (declaredField.getType().equals(ModelPart.class))
                        {
                            try
                            {
                                declaredField.setAccessible(true);

                                ModelPart part = (ModelPart) declaredField.get(model);

                                stringModelPartMap.put(declaredField.getName(), part);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                parts.put(this.entity.getClass(), stringModelPartMap);
            }

            return new ArrayList<>(stringModelPartMap.keySet());
        }

        return super.getBones();
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
            /* 1.21.5: new StringNbtReader(StringReader).parseCompound() -> StringNbtReader.readCompound(String). */
            compound = StringNbtReader.readCompound(nbt);
        }
        catch (Exception e)
        {}

        /* 1.21.2: EntityType.create(World) -> create(World, SpawnReason). */
        this.entity = Registries.ENTITY_TYPE.get(Identifier.of(id)).create(MinecraftClient.getInstance().world, SpawnReason.COMMAND);

        if (this.entity == null && this.form.isPlayer())
        {
            this.entity = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, slim ? SLIM : WIDE);
            /* 1.21.9: PlayerEntity.PLAYER_MODEL_PARTS moved to PlayerLikeEntity.PLAYER_MODE_CUSTOMIZATION_ID
             * (same tracked byte, renamed; opened via bbs.accesswidener). All cosmetic layers on, as before. */
            this.entity.getDataTracker().set(PlayerLikeEntity.PLAYER_MODE_CUSTOMIZATION_ID, (byte) 0b1111111);
        }

        if (this.entity != null)
        {
            compound.putString("id", id);

            World world = MinecraftClient.getInstance().world;

            if (world != null)
            {
                try
                {
                    /* 1.21.6 persistence rewrite: Entity.readNbt(NbtCompound) -> readData(ReadView).
                     * The user-typed NBT can be anything, and a mob that fails mid-read is still
                     * usable — it just ignores the broken tags, like the old readNbt did. */
                    this.entity.readData(NbtReadView.create(ErrorReporter.EMPTY, world.getRegistryManager(), compound));
                }
                catch (Exception e)
                {}
            }

            this.entity.noClip = true;
        }
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            /* List/icon preview goes through the special GUI element FBO pass, same as
             * item/block/model forms — a direct immediate draw can't composite in the
             * two-phase GUI (1.21.6+). */
            this.submitUIPreview(context, x1, y1, x2, y2);
        }
    }

    @Override
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        if (this.entity == null)
        {
            return;
        }

        Matrix4f uiMatrix = getUIPreviewMatrix(angle, y1, y2);
        float scale = this.form.uiScale.get();
        float width = this.entity.getWidth();
        float height = this.entity.getHeight();

        /* Big mobs are normalized into the cell, exactly like the 1.21.1 preview did. */
        scale = scale * Math.min(1.8F / Math.max(width, height), 1F);

        stack.push();
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.scale(scale, scale, scale);

        if (!this.form.mobID.get().equals("minecraft:ender_dragon"))
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        this.renderEntity(stack, transition, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        this.ensureEntity();

        if (this.entity == null)
        {
            return;
        }

        if (context.isPicking())
        {
            /* Vanilla-rendered forms (mob, label, block, item) aren't pickable on this branch:
             * 1.21.1 picked them by swapping the GLOBAL shader for the picker program, and the
             * 1.21.5+ pipeline system has no global program to swap. The mob simply draws nothing
             * into the picking stencil. */
            return;
        }

        Matrix4f cached = new Matrix4f(RenderSystem.getModelViewMatrix());

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
            int v = context.overlay >> 16 & 0xFFFF;

            /* The damage flash: the render state derives the red overlay from hurtTime, so the
             * BBS overlay coordinate is translated back into it before the state snapshot. */
            entity.hurtTime = v != 10 ? 100 : 0;
        }

        /* Publishing the form's camera-space origin opts its translucent layers (slime
         * bodies, ghost textures) into the deferred sorted pass. */
        Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));

        this.renderEntity(context.stack, context.getTransition(), context.light, context.overlay);

        FormTranslucentQueue.setSortOrigin(null);

        context.stack.pop();

        if (context.world != null)
        {
            context.world.pop();
        }

        /* Restore the shared model-view in case a command renderer touched it — the 2D UI batch
         * inherits it when a MobForm is nested as a body part inside a list preview. */
        RenderSystem.getModelViewMatrix().set(cached);
    }

    /**
     * The shared draw path: snapshot the entity into a render state, submit it into BBS's private
     * command queue and flush the queue synchronously through the BBS provider. See
     * {@link QueueDispatch} for why the private queue exists at all.
     */
    private void renderEntity(MatrixStack stack, float transition, int light, int overlay)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        EntityRenderManager manager = MinecraftClient.getInstance().getEntityRenderDispatcher();

        /* Resolves (and caches) the part map as a side effect, for the pose below. */
        this.getBones();

        EntityRenderState state = manager.getAndUpdateRenderState(this.entity, transition);

        /* The film/preview owns placement and lighting: the entity nominally stands wherever the
         * real world put it, but it draws at the stack's origin with the form's light. Labels,
         * vanilla blob shadows and leashes are world-decorations that never made sense on a form. */
        state.x = state.y = state.z = 0;
        state.squaredDistanceToCamera = 0;
        state.light = light;
        state.displayName = null;
        state.nameLabelPos = null;
        state.shadowPieces.clear();
        state.leashDatas = null;
        state.outlineColor = 0;

        currentPose = this.mergedPose();
        currentParts = parts.get(this.entity.getClass());

        /* The custom-texture feature: 1.21.1 GL-bound the texture over the first drawn layer
         * (the body). Textures are per-layer now, so the first requested layer of this flush is
         * remapped onto an entity layer carrying the form's texture instead. */
        Link textureLink = this.form.texture.get();

        if (textureLink != null)
        {
            Texture texture = BBSModClient.getTextures().getTexture(textureLink);
            Identifier adopted = AdoptedTexture.identifier(texture);
            boolean[] first = {true};

            consumers.setLayerMapper((layer) ->
            {
                if (first[0])
                {
                    first[0] = false;

                    return RenderLayers.entityTranslucent(adopted);
                }

                return null;
            });
        }

        try
        {
            manager.render(state, QueueDispatch.cameraState(), 0D, 0D, 0D, stack, QueueDispatch.queue());
            QueueDispatch.flush();
            consumers.draw();
        }
        finally
        {
            consumers.setLayerMapper(null);
            currentPose = null;
            currentParts = null;
        }
    }

    /** The form's pose with its overlay folded in — the merge the 1.21.1 mixin did per render. */
    private Pose mergedPose()
    {
        Pose pose = this.form.pose.get();
        Pose poseOverlay = this.form.poseOverlay.get();

        if (pose == null)
        {
            return null;
        }

        pose = pose.copy();

        if (poseOverlay != null)
        {
            for (Map.Entry<String, PoseTransform> transformEntry : poseOverlay.transforms.entrySet())
            {
                PoseTransform poseTransform = pose.get(transformEntry.getKey());
                PoseTransform value = transformEntry.getValue();

                if (value.fix != 0)
                {
                    poseTransform.translate.lerp(value.translate, value.fix);
                    poseTransform.scale.lerp(value.scale, value.fix);
                    poseTransform.lerpRotation(value, value.fix);
                }
                else
                {
                    poseTransform.translate.add(value.translate);
                    poseTransform.scale.add(value.scale).sub(1, 1, 1);
                    poseTransform.addRotation(value);
                }
            }
        }

        return pose;
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            this.entity.tick();

            /* 1.21.9: Entity prevPitch/prevYaw -> lastPitch/lastYaw; LivingEntity prevHeadYaw/
             * prevBodyYaw -> lastHeadYaw/lastBodyYaw. */
            this.entity.lastPitch = this.prevPitch;
            this.entity.lastYaw = 0F;

            if (this.entity instanceof LivingEntity livingEntity)
            {
                livingEntity.lastHeadYaw = this.prevYawHead;
                livingEntity.lastBodyYaw = 0F;

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
            this.entity.setPose(entity.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
            if (this.entity instanceof LivingEntity living)
            {
                living.equipStack(EquipmentSlot.MAINHAND, entity.getEquipmentStack(EquipmentSlot.MAINHAND));
                living.equipStack(EquipmentSlot.OFFHAND, entity.getEquipmentStack(EquipmentSlot.OFFHAND));
                living.equipStack(EquipmentSlot.HEAD, entity.getEquipmentStack(EquipmentSlot.HEAD));
                living.equipStack(EquipmentSlot.CHEST, entity.getEquipmentStack(EquipmentSlot.CHEST));
                living.equipStack(EquipmentSlot.LEGS, entity.getEquipmentStack(EquipmentSlot.LEGS));
                living.equipStack(EquipmentSlot.FEET, entity.getEquipmentStack(EquipmentSlot.FEET));
            }
            this.entity.age = entity.getAge();
            this.entity.noClip = true;

            this.prevYawHead = entity.getHeadYaw() - entity.getBodyYaw();
            this.prevPitch = entity.getPitch();
        }
    }

}
