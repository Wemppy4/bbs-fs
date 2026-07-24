package mchorse.bbs_mod.forms.renderers;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.StringReader;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.mixin.LimbAnimatorAccessor;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
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
    private static final VertexConsumer EMPTY_VERTEX_CONSUMER = new EmptyVertexConsumer();
    private static final VertexConsumerProvider EMPTY_VERTEX_CONSUMERS = (layer) -> EMPTY_VERTEX_CONSUMER;
    private static final int PAUSE_SAMPLE_TICK = 0;
    private static final int PAUSE_SAMPLE_UI = 1;
    private static final int PAUSE_SAMPLE_PREVIEW = 2;
    private static final int PAUSE_SAMPLE_WORLD = 3;

    private Entity entity;
    private String lastId = "";
    private String lastNBT = "";
    private MatrixCache bones = new MatrixCache();
    private List<String> pickedBoneIds = List.of();

    public float prevHandSwing;
    private boolean lastSlim;
    private boolean animationInitialized;
    private boolean animationSourceInitialized;
    private boolean animationPaused;
    private boolean animationResuming;
    private boolean pauseRequestPending;
    private boolean requestedPaused;
    private boolean runtimePauseActive;
    private boolean runtimePauseFromTick;
    private boolean pausedLookCaptured;
    private boolean pauseCaptureOpen;
    private float pausedTransition;
    private float requestTransition;
    private float requestLookTransition;
    private float limbPositionOffset;
    private float pausedHeadYaw;
    private float pausedPitch;
    private float resumeTransition;
    private float resumeStartHeadYaw;
    private float resumeStartPitch;
    private float resumeHeadYaw;
    private float resumePitch;
    private float lastRenderTransition;
    private int animationAgeOffset;
    private int pauseCapturePriority;
    private int lastRenderPriority;
    private int lastRenderAge = Integer.MIN_VALUE;

    public MobFormRenderer(MobForm form)
    {
        super(form);

        this.animationPaused = this.form.paused.getOriginalValue();
    }

    @Override
    public BoneHierarchy getBoneHierarchy()
    {
        this.ensureEntity();

        if (this.entity != null)
        {
            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);

            return VanillaRendererBones.discover(renderer).getBoneHierarchy();
        }

        return super.getBoneHierarchy();
    }

    private Pose mergeOverlays()
    {
        Pose overlay = this.form.poseOverlay.get().copy();

        for (ValuePose additional : this.form.additionalOverlays)
        {
            Pose additionalPose = additional.get();

            for (Map.Entry<String, PoseTransform> entry : additionalPose.transforms.entrySet())
            {
                PoseTransform target = overlay.get(entry.getKey());
                PoseTransform value = entry.getValue();

                if (value.fix != 0F)
                {
                    target.translate.lerp(value.translate, value.fix);
                    target.scale.lerp(value.scale, value.fix);
                    target.rotate.lerp(value.rotate, value.fix);
                    target.rotate2.lerp(value.rotate2, value.fix);
                }
                else
                {
                    target.translate.add(value.translate);
                    target.scale.add(value.scale).sub(1F, 1F, 1F);
                    target.rotate.add(value.rotate);
                    target.rotate2.add(value.rotate2);
                }
            }
        }

        return overlay;
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
            this.bones.clear();
            this.pickedBoneIds = List.of();
            this.animationInitialized = false;
            this.animationSourceInitialized = false;
            this.animationPaused = this.form.paused.getOriginalValue();
            this.animationResuming = false;
            this.pauseRequestPending = false;
            this.runtimePauseActive = false;
            this.runtimePauseFromTick = false;
            this.pausedLookCaptured = false;
            this.pauseCaptureOpen = false;
            this.pausedTransition = 0F;
            this.limbPositionOffset = 0F;
            this.animationAgeOffset = 0;
            this.pauseCapturePriority = 0;
            this.lastRenderTransition = 0F;
            this.lastRenderPriority = PAUSE_SAMPLE_TICK;
            this.lastRenderAge = Integer.MIN_VALUE;
            this.prevHandSwing = 0F;
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

        if (this.form.isPlayer())
        {
            this.entity = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, slim ? SLIM : WIDE);
            this.entity.getDataTracker().set(PlayerUtils.ProtectedAccess.getModelParts(), (byte) 0b1111111);
        }
        else
        {
            this.entity = Registries.ENTITY_TYPE.getOrEmpty(new Identifier(id))
                .map((type) -> type.create(MinecraftClient.getInstance().world))
                .orElse(null);
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
            this.ensureAnimationInitialized(null);

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

                RenderSystem.enableBlend();
            });

            consumers.setUI(true);
            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);

            float transition = this.getUIAnimationTransition(context.getTransition());

            this.prepareRenderLook(null, context.getTransition());
            this.recordRenderSample(transition, PAUSE_SAMPLE_UI);

            try (MobRenderContext ignored = MobRenderContext.push(renderer, this.form.pose.get(), this.mergeOverlays(), this.getColor(0xffffffff)))
            {
                MinecraftClient.getInstance().getEntityRenderDispatcher().render(this.entity, 0D, 0D, 0D, 0F, transition, stack, consumers, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE);
            }

            consumers.draw();
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
        this.bones.clear();
        this.pickedBoneIds = List.of();

        if (this.entity != null)
        {
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

                    RenderSystem.enableBlend();
                });
            }

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            Matrix4f captureBase = new Matrix4f(context.stack.peek().getPositionMatrix());

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

            Object renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(this.entity);
            boolean incrementPicking = context.stencilMap != null && context.stencilMap.increment;
            MobRenderContext mobContext = MobRenderContext.push(
                renderer,
                this.form.pose.get(),
                this.mergeOverlays(),
                this.getColor(context.color),
                captureBase,
                context.isPicking(),
                incrementPicking
            );

            try (mobContext)
            {
                float transition = this.prepareAnimationRender(context);

                /* Publishing the form's camera-space origin opts its translucent layers (slime
                 * bodies, ghost textures) into the deferred sorted pass. */
                if (!context.isPicking())
                {
                    Vector3f origin = context.stack.peek().getPositionMatrix().getTranslation(new Vector3f());

                    FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(origin));
                }

                MinecraftClient.getInstance().getEntityRenderDispatcher().render(
                    this.entity,
                    0D,
                    0D,
                    0D,
                    0F,
                    transition,
                    context.stack,
                    consumers,
                    light
                );

                mobContext.completeMatrices();
            }

            this.bones = mobContext.getMatrices();
            this.pickedBoneIds = mobContext.getPickedBoneIds();

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
                RenderSystem.getModelViewMatrix().identity();
            }
        }
    }

    private float prepareAnimationRender(FormRenderingContext context)
    {
        this.ensureAnimationInitialized(context.entity);

        if (!context.isPicking())
        {
            this.sampleRenderPause(context.entity, context.getTransition(), this.getPauseSamplePriority(context));
        }

        float transition = this.getAnimationTransition(context.getTransition());

        this.prepareRenderLook(context.entity, context.getTransition());

        if (!context.isPicking())
        {
            int priority = this.getPauseSamplePriority(context);
            boolean recorded = this.recordRenderSample(transition, priority);

            if (recorded && (this.animationPaused || this.pauseRequestPending))
            {
                this.captureCurrentLook();
            }
        }

        return transition;
    }

    private Color getColor(int contextColor)
    {
        Color color = new Color().set(contextColor, true);

        FormColorBlend.blend(color, this.form.color.get(), this.form.additiveColor.get());

        return color;
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        StencilMap stencilMap = context.stencilMap;

        if (stencilMap == null)
        {
            return;
        }

        stencilMap.addPicking(this.form);

        if (stencilMap.increment)
        {
            for (String bone : this.pickedBoneIds)
            {
                stencilMap.addPicking(this.form, bone);
            }
        }
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            String boneId = this.getBoneHierarchy().resolveId(part.bone.get());
            Matrix4f matrix = this.bones.get(boneId == null ? part.bone.get() : boneId).matrix();

            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }

            if (matrix != null)
            {
                MatrixStackUtils.multiply(context.stack, matrix);
                if (context.world != null)
                {
                    MatrixStackUtils.multiply(context.world, matrix);
                }
            }

            this.renderBodyPart(part, context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        MatrixCache bones = this.collectBoneMatrices(entity, transition);
        Matrix4f matrix = new Matrix4f();
        Matrix4f origin = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        origin.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        matrix.set(stack.peek().getPositionMatrix());
        matrices.put(prefix, matrix, origin);

        for (Map.Entry<String, MatrixCacheEntry> entry : bones.entrySet())
        {
            Matrix4f boneMatrix = new Matrix4f(stack.peek().getPositionMatrix()).mul(entry.getValue().matrix());
            Matrix4f boneOrigin = new Matrix4f(stack.peek().getPositionMatrix()).mul(entry.getValue().origin());

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), boneMatrix, boneOrigin, entry.getValue().rotationOffset());
        }

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                String boneId = this.getBoneHierarchy().resolveId(part.bone.get());
                Matrix4f boneMatrix = bones.get(boneId == null ? part.bone.get() : boneId).matrix();

                stack.push();

                if (boneMatrix != null)
                {
                    MatrixStackUtils.multiply(stack, boneMatrix);
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());
                FormUtilsClient.getRenderer(form).collectMatrices(
                    part.getRenderEntity(entity),
                    stack,
                    matrices,
                    StringUtils.combinePaths(prefix, String.valueOf(i)),
                    transition
                );

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }

    private MatrixCache collectBoneMatrices(IEntity source, float transition)
    {
        this.ensureEntity();
        this.ensureAnimationInitialized(source);

        if (this.entity == null)
        {
            return new MatrixCache();
        }

        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack stack = new MatrixStack();
        Matrix4f captureBase = new Matrix4f(stack.peek().getPositionMatrix());

        if (this.form.mobID.get().equals("minecraft:ender_dragon"))
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        Object renderer = client.getEntityRenderDispatcher().getRenderer(this.entity);
        MobRenderContext context = MobRenderContext.push(
            renderer,
            this.form.pose.get(),
            this.mergeOverlays(),
            Color.white(),
            captureBase,
            false,
            false
        );

        try (context)
        {
            float animationTransition = this.getAnimationTransition(transition);

            this.prepareRenderLook(source, transition);

            client.getEntityRenderDispatcher().render(
                this.entity,
                0D,
                0D,
                0D,
                0F,
                animationTransition,
                stack,
                EMPTY_VERTEX_CONSUMERS,
                LightmapTextureManager.MAX_LIGHT_COORDINATE
            );
            context.completeMatrices();
        }

        return context.getMatrices();
    }

    @Override
    public void tick(IEntity source)
    {
        this.ensureEntity();

        if (this.entity == null)
        {
            return;
        }

        boolean initialized = this.animationInitialized;

        this.ensureAnimationInitialized(source);

        boolean finishingResume = this.animationResuming;
        boolean resuming = this.updatePauseState(source, initialized, finishingResume);
        boolean paused = this.animationPaused;
        int ageBeforeTick = this.entity.age;
        float limbSpeedBeforeTick = this.getLimbSpeed();

        if (!paused && !resuming)
        {
            this.entity.tick();
        }

        this.entity.prevPitch = source.getPrevPitch();

        this.updateLivingAnimation(source, paused, resuming, finishingResume, limbSpeedBeforeTick);
        this.synchronizeEntity(source, paused, resuming, ageBeforeTick);

        if (resuming)
        {
            this.animationSourceInitialized = true;
        }

        this.lastRenderPriority = PAUSE_SAMPLE_TICK;
    }

    private boolean updatePauseState(IEntity source, boolean initialized, boolean finishingResume)
    {
        float tickTransition = initialized ? 1F : 0F;
        IEntity pauseSource = source;

        if (initialized && !finishingResume && this.lastRenderAge == this.entity.age)
        {
            tickTransition = this.lastRenderTransition;
            pauseSource = null;
        }

        this.sampleTickPause(pauseSource, tickTransition);
        this.animationResuming = false;

        if (!this.pauseRequestPending)
        {
            this.pauseCaptureOpen = false;

            return false;
        }

        boolean resuming = this.animationPaused && !this.requestedPaused;

        if (this.requestedPaused && !this.pausedLookCaptured)
        {
            this.capturePausedAnimation(this.requestTransition, this.requestLookTransition, source);
        }

        this.animationPaused = this.requestedPaused;
        this.pauseRequestPending = false;
        this.pauseCaptureOpen = false;

        if (resuming)
        {
            this.animationResuming = true;
            this.resumeTransition = this.pausedTransition;
            this.resumeStartHeadYaw = this.pausedHeadYaw;
            this.resumeStartPitch = this.pausedPitch;
            this.resumeHeadYaw = source.getHeadYaw() - source.getBodyYaw();
            this.resumePitch = source.getPitch();
        }

        return resuming;
    }

    private float getLimbSpeed()
    {
        if (this.entity instanceof LivingEntity livingEntity && livingEntity.limbAnimator instanceof LimbAnimatorAccessor animator)
        {
            return animator.getSpeed();
        }

        return 0F;
    }

    private void updateLivingAnimation(IEntity source, boolean paused, boolean resuming, boolean finishingResume, float limbSpeedBeforeTick)
    {
        this.entity.prevYaw = 0F;

        if (!(this.entity instanceof LivingEntity livingEntity))
        {
            return;
        }

        livingEntity.prevBodyYaw = 0F;
        livingEntity.prevHeadYaw = source.getPrevHeadYaw() - source.getPrevBodyYaw();

        if (paused)
        {
            return;
        }

        /* Limb swing is so ugly */
        if (livingEntity.limbAnimator instanceof LimbAnimatorAccessor target && source.getLimbAnimator() instanceof LimbAnimatorAccessor sourceAnimator)
        {
            if (resuming)
            {
                this.limbPositionOffset = target.getPos() - sourceAnimator.getPos();
            }
            else
            {
                target.setPrevSpeed(finishingResume ? limbSpeedBeforeTick : sourceAnimator.getPrevSpeed());
                target.setSpeed(sourceAnimator.getSpeed());
                target.setPos(sourceAnimator.getPos() + this.limbPositionOffset);
            }
        }

        this.updateHandSwing(source, livingEntity, resuming);
    }

    private void updateHandSwing(IEntity source, LivingEntity livingEntity, boolean resuming)
    {
        float handSwingProgress = source.getHandSwingProgress(0F);

        if (resuming)
        {
            this.prevHandSwing = handSwingProgress;

            return;
        }

        if (handSwingProgress < this.prevHandSwing)
        {
            this.prevHandSwing = 0F;
        }

        if (handSwingProgress > 0F && this.prevHandSwing == 0F)
        {
            livingEntity.swingHand(Hand.MAIN_HAND);
        }

        this.prevHandSwing = handSwingProgress;
    }

    private void synchronizeEntity(IEntity source, boolean paused, boolean resuming, int ageBeforeTick)
    {
        this.entity.setYaw(0F);
        this.entity.setHeadYaw(source.getHeadYaw() - source.getBodyYaw());
        this.entity.setPitch(source.getPitch());
        this.entity.setBodyYaw(0F);
        this.entity.setPos(source.getX(), source.getY(), source.getZ());
        this.entity.setOnGround(source.isOnGround());
        this.entity.setSneaking(source.isSneaking());
        this.entity.setSprinting(source.isSprinting());
        this.entity.setPose(source.isSneaking() ? EntityPose.CROUCHING : EntityPose.STANDING);
        this.entity.equipStack(EquipmentSlot.MAINHAND, source.getEquipmentStack(EquipmentSlot.MAINHAND));
        this.entity.equipStack(EquipmentSlot.OFFHAND, source.getEquipmentStack(EquipmentSlot.OFFHAND));
        this.entity.equipStack(EquipmentSlot.HEAD, source.getEquipmentStack(EquipmentSlot.HEAD));
        this.entity.equipStack(EquipmentSlot.CHEST, source.getEquipmentStack(EquipmentSlot.CHEST));
        this.entity.equipStack(EquipmentSlot.LEGS, source.getEquipmentStack(EquipmentSlot.LEGS));
        this.entity.equipStack(EquipmentSlot.FEET, source.getEquipmentStack(EquipmentSlot.FEET));

        if (!paused)
        {
            if (resuming)
            {
                this.animationAgeOffset = ageBeforeTick - source.getAge();
            }
            else
            {
                this.entity.age = source.getAge() + this.animationAgeOffset;
            }
        }
        else
        {
            this.captureCurrentLook();
        }

        this.entity.noClip = true;
    }

    private void ensureAnimationInitialized(IEntity source)
    {
        if (this.entity == null)
        {
            return;
        }

        boolean visualInitialized = this.animationInitialized;

        if (!this.animationInitialized)
        {
            this.animationInitialized = true;
            this.animationAgeOffset = 0;
        }

        if (source == null || this.animationSourceInitialized)
        {
            return;
        }

        if (visualInitialized && (this.animationPaused || this.form.paused.get()))
        {
            return;
        }

        if (this.entity instanceof LivingEntity livingEntity && livingEntity.limbAnimator instanceof LimbAnimatorAccessor target && source.getLimbAnimator() instanceof LimbAnimatorAccessor sourceAnimator)
        {
            target.setPrevSpeed(sourceAnimator.getPrevSpeed());
            target.setSpeed(sourceAnimator.getSpeed());
            target.setPos(sourceAnimator.getPos());
        }

        this.entity.age = source.getAge();
        this.prevHandSwing = source.getHandSwingProgress(0F);
        this.animationAgeOffset = 0;
        this.animationSourceInitialized = true;
    }

    private void sampleTickPause(IEntity source, float transition)
    {
        Boolean runtimePaused = this.form.paused.getRuntimeValue();

        if (runtimePaused != null)
        {
            this.runtimePauseActive = true;
            this.runtimePauseFromTick = true;
            this.requestAnimationPause(runtimePaused, transition, transition, source, PAUSE_SAMPLE_TICK);

            return;
        }

        if (this.runtimePauseFromTick)
        {
            this.runtimePauseActive = false;
            this.runtimePauseFromTick = false;
        }

        if (!this.runtimePauseActive)
        {
            boolean paused = this.form.paused.getOriginalValue();

            this.requestAnimationPause(paused, transition, transition, source, PAUSE_SAMPLE_TICK);
        }
    }

    private void sampleRenderPause(IEntity source, float transition, int priority)
    {
        boolean runtime = this.form.paused.getRuntimeValue() != null;
        float animationTransition = this.animationResuming
            ? MathHelper.lerp(transition, this.resumeTransition, 1F)
            : transition;

        if (!runtime)
        {
            this.runtimePauseFromTick = false;
        }

        this.runtimePauseActive = runtime;
        this.requestAnimationPause(this.form.paused.get(), animationTransition, transition, source, priority);
    }

    private int getPauseSamplePriority(FormRenderingContext context)
    {
        if (context.ui || (context.type != FormRenderType.ENTITY && context.type != FormRenderType.MODEL_BLOCK))
        {
            return context.type == FormRenderType.PREVIEW ? PAUSE_SAMPLE_PREVIEW : PAUSE_SAMPLE_UI;
        }

        return PAUSE_SAMPLE_WORLD;
    }

    private boolean recordRenderSample(float transition, int priority)
    {
        if (priority < this.lastRenderPriority)
        {
            return false;
        }

        this.lastRenderTransition = transition;
        this.lastRenderPriority = priority;
        this.lastRenderAge = this.entity.age;

        return true;
    }

    private void requestAnimationPause(boolean paused, float animationTransition, float lookTransition, IEntity source, int priority)
    {
        if (this.pauseRequestPending)
        {
            if (paused == this.requestedPaused)
            {
                if (paused && this.pauseCaptureOpen && priority > this.pauseCapturePriority)
                {
                    this.requestTransition = animationTransition;
                    this.requestLookTransition = lookTransition;
                    this.pauseCapturePriority = priority;
                    this.capturePausedAnimation(animationTransition, lookTransition, source);
                }

                return;
            }

            this.pauseRequestPending = false;

            if (!this.animationPaused)
            {
                this.pausedLookCaptured = false;
                this.pauseCaptureOpen = false;
                this.pauseCapturePriority = 0;
            }

            return;
        }

        if (paused == this.animationPaused)
        {
            if (paused && !this.pausedLookCaptured && this.animationInitialized)
            {
                this.pauseCaptureOpen = true;
                this.pauseCapturePriority = priority;
                this.capturePausedAnimation(animationTransition, lookTransition, source);
            }
            else if (paused && this.pauseCaptureOpen && priority > this.pauseCapturePriority)
            {
                this.pauseCapturePriority = priority;
                this.capturePausedAnimation(animationTransition, lookTransition, source);
            }

            return;
        }

        this.pauseRequestPending = true;
        this.requestedPaused = paused;
        this.requestTransition = animationTransition;
        this.requestLookTransition = lookTransition;
        this.pauseCaptureOpen = paused;
        this.pauseCapturePriority = paused ? priority : 0;

        if (paused && this.animationInitialized)
        {
            this.capturePausedAnimation(animationTransition, lookTransition, source);
        }
    }

    private float getAnimationTransition(float transition)
    {
        if (this.animationPaused || this.pauseRequestPending)
        {
            return this.pausedTransition;
        }

        return this.animationResuming ? MathHelper.lerp(transition, this.resumeTransition, 1F) : transition;
    }

    private float getUIAnimationTransition(float transition)
    {
        boolean paused = this.form.paused.get();
        boolean pendingPause = this.pauseRequestPending && this.requestedPaused;

        if (this.runtimePauseActive && !this.runtimePauseFromTick && this.lastRenderPriority <= PAUSE_SAMPLE_UI && this.form.paused.getRuntimeValue() == null)
        {
            this.runtimePauseActive = false;
        }

        if (paused && (!this.pausedLookCaptured || (!this.animationPaused && !pendingPause)))
        {
            float pauseTransition = this.lastRenderAge == this.entity.age ? this.lastRenderTransition : transition;

            this.pauseCaptureOpen = true;
            this.pauseCapturePriority = PAUSE_SAMPLE_UI;
            this.capturePausedAnimation(pauseTransition, pauseTransition, null);
        }

        if (paused)
        {
            return this.pausedTransition;
        }

        if (!this.runtimePauseActive && !this.pauseRequestPending && paused != this.animationPaused)
        {
            return transition;
        }

        return this.getAnimationTransition(transition);
    }

    private void capturePausedAnimation(float animationTransition, float lookTransition, IEntity source)
    {
        if (!this.animationInitialized)
        {
            return;
        }

        if (source != null)
        {
            this.applyRenderLook(source, lookTransition);
        }

        this.pausedTransition = animationTransition;

        this.pausedPitch = source == null
            ? MathHelper.lerp(lookTransition, this.entity.prevPitch, this.entity.getPitch())
            : this.entity.getPitch();
        this.pausedHeadYaw = this.entity.getHeadYaw();

        if (source == null && this.entity instanceof LivingEntity livingEntity)
        {
            this.pausedHeadYaw = MathHelper.lerpAngleDegrees(lookTransition, livingEntity.prevHeadYaw, livingEntity.getHeadYaw());
        }

        this.pausedLookCaptured = true;
        this.applyPausedLook();
    }

    private void captureCurrentLook()
    {
        this.pausedPitch = this.entity.getPitch();
        this.pausedHeadYaw = this.entity.getHeadYaw();
        this.pausedLookCaptured = true;
    }

    private void applyPausedLook()
    {
        this.entity.prevPitch = this.pausedPitch;
        this.entity.setPitch(this.pausedPitch);
        this.entity.setHeadYaw(this.pausedHeadYaw);

        if (this.entity instanceof LivingEntity livingEntity)
        {
            livingEntity.prevBodyYaw = 0F;
            livingEntity.setBodyYaw(0F);
            livingEntity.prevHeadYaw = this.pausedHeadYaw;
        }
    }

    private void applyResumeLook(float transition)
    {
        float headYaw = MathHelper.lerpAngleDegrees(transition, this.resumeStartHeadYaw, this.resumeHeadYaw);
        float pitch = MathHelper.lerp(transition, this.resumeStartPitch, this.resumePitch);
        this.entity.prevPitch = pitch;
        this.entity.setPitch(pitch);
        this.entity.setHeadYaw(headYaw);

        if (this.entity instanceof LivingEntity livingEntity)
        {
            livingEntity.prevBodyYaw = 0F;
            livingEntity.setBodyYaw(0F);
            livingEntity.prevHeadYaw = headYaw;
        }
    }

    /**
     * Resolves look angles once per render from the source entity. Vanilla normally performs this
     * interpolation inside LivingEntityRenderer, but MobForm keeps body yaw outside that renderer;
     * synchronizing only tick endpoints would therefore interpolate the already-subtracted angle.
     * Look remains source-driven while the animation clock is paused.
     */
    private void prepareRenderLook(IEntity source, float transition)
    {
        if (source != null)
        {
            this.applyRenderLook(source, transition);

            return;
        }

        if (this.animationPaused || this.pauseRequestPending)
        {
            if (this.pausedLookCaptured)
            {
                this.applyPausedLook();
            }

            return;
        }

        if (this.animationResuming)
        {
            this.applyResumeLook(transition);

            return;
        }
    }

    private void applyRenderLook(IEntity source, float transition)
    {
        float interpolatedHeadYaw = MathHelper.lerpAngleDegrees(transition, source.getPrevHeadYaw(), source.getHeadYaw());
        float interpolatedBodyYaw = MathHelper.lerpAngleDegrees(transition, source.getPrevBodyYaw(), source.getBodyYaw());
        float relativeHeadYaw = interpolatedHeadYaw - interpolatedBodyYaw;
        float interpolatedPitch = MathHelper.lerp(transition, source.getPrevPitch(), source.getPitch());

        this.entity.prevPitch = interpolatedPitch;
        this.entity.setPitch(interpolatedPitch);

        if (this.entity instanceof LivingEntity livingEntity)
        {
            livingEntity.prevBodyYaw = 0F;
            livingEntity.setBodyYaw(0F);
            livingEntity.prevHeadYaw = relativeHeadYaw;
            livingEntity.setHeadYaw(relativeHeadYaw);
        }
    }

    private static class BooleanHolder
    {
        public boolean bool;
    }

    private static class EmptyVertexConsumer implements VertexConsumer
    {
        @Override
        public VertexConsumer vertex(double x, double y, double z)
        {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v)
        {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v)
        {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z)
        {
            return this;
        }

        @Override
        public void next()
        {}

        @Override
        public void fixedColor(int red, int green, int blue, int alpha)
        {}

        @Override
        public void unfixColor()
        {}
    }
}