package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.renderer.ItemPredicateDonor;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.FormPbr;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.math.Operation;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.core.ValuePose;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public class ModelFormRenderer extends FormRenderer<ModelForm> implements ITickable
{
    private static Matrix4f uiMatrix = new Matrix4f();

    public ModelForm getForm()
    {
        return this.form;
    }

    private MatrixCache bones = new MatrixCache();

    private ActionsConfig lastConfigs;
    private IAnimator animator;
    private ModelInstance lastModel;
    private boolean renderingArm;

    private IEntity entity = new StubEntity();

    @Override
    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        super.applyTransforms(stack, origin, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            Vector3f scale = model.getScale();

            stack.scale(scale.x, scale.y, scale.z);
        }
    }

    @Override
    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        super.applyTransforms(matrix, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            Vector3f scale = model.getScale();

            matrix.scale(scale.x, scale.y, scale.z);
        }
    }

    public static Matrix4f getUIMatrix(UIContext context, int x1, int y1, int x2, int y2)
    {
        float scale = (y2 - y1) / 2.5F;
        int x = x1 + (x2 - x1) / 2;
        float y = y1 + (y2 - y1) * 0.85F;
        float angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        if (BBSSettings.freezeModels.get())
        {
            angle = -MathUtils.PI + MathUtils.PI / 8;
        }

        uiMatrix.identity();
        uiMatrix.translate(x, y, 40);
        uiMatrix.scale(scale, -scale, scale);
        uiMatrix.rotateX(MathUtils.PI / 8);
        uiMatrix.rotateY(angle);

        return uiMatrix;
    }

    public static ModelInstance getModel(ModelForm form)
    {
        return BBSModClient.getModels().getModel(form.model.get());
    }

    public ModelFormRenderer(ModelForm form)
    {
        super(form);
    }

    public IAnimator getAnimator()
    {
        return this.animator;
    }

    public ModelInstance getModel()
    {
        return getModel(this.form);
    }

    public Pose getPose()
    {
        Pose pose = this.form.pose.get().copy();
        Pose overlay = this.form.poseOverlay.get();

        this.applyPose(pose, overlay);

        for (ValuePose newPose : this.form.additionalOverlays)
        {
            this.applyPose(pose, newPose.get());
        }

        return pose;
    }

    private void applyPose(Pose targetPose, Pose pose)
    {
        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            PoseTransform poseTransform = targetPose.getOrCreate(entry.getKey());
            PoseTransform value = entry.getValue();

            if (!Operation.equals(value.fix, 0))
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

    public void resetAnimator()
    {
        this.animator = null;
        this.lastModel = null;
    }

    /**
     * The channels phase of the bone pipeline (rest &rarr; actions &rarr; pose): resets every bone
     * to its bind pose, applies the animator's actions, then the form's pose stack. After this the
     * channels are the FK truth; the constraint stages (IK &rarr; physics &rarr; limits) run on top
     * of it separately (render: the apply* trio; matrix capture: its explicit IK solve) and
     * write only evaluated orientations, never the channels.
     */
    private void evaluateChannels(IEntity entity, ModelInstance model, float transition)
    {
        model.model.resetPose();
        this.animator.applyActions(entity, model, transition);
        model.model.applyPose(this.getPose());
    }

    public void ensureAnimator(float transition)
    {
        ModelInstance model = this.getModel();
        ActionsConfig actionsConfig = this.form.actions.get();

        if (model == null || this.lastModel == model)
        {
            /* Update the config */
            if (this.animator != null && !Objects.equals(actionsConfig, this.lastConfigs))
            {
                this.animator.setup(model, actionsConfig, true);

                this.lastConfigs = new ActionsConfig();
                this.lastConfigs.copy(actionsConfig);
            }

            return;
        }

        this.animator = model.isProcedural() ? new ProceduralAnimator() : new Animator();
        this.animator.setup(model, actionsConfig, false);

        this.lastConfigs = new ActionsConfig();
        this.lastConfigs.copy(actionsConfig);
        this.lastModel = model;
    }

    @Override
    public List<String> getBones()
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return Collections.emptyList();
        }

        List<String> bones = new ArrayList<>(model.model.getGroupKeysInHierarchyOrder());
        bones.removeIf((bone) -> PoseBones.isHidden(model.getDisabledBones(), bone));

        return bones;
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();

            stack.push();

            Matrix4f uiMatrix = getUIMatrix(context, x1, y1, x2, y2);

            this.applyTransforms(uiMatrix, context.getTransition());

            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = Color.white();
            Color formColor = this.form.color.get();
            float scale = this.form.uiScale.get() * model.getUiScale();

            this.evaluateChannels(null, model, context.getTransition());

            MatrixStackUtils.multiply(stack, uiMatrix);
            stack.scale(scale, scale, scale);

            BBSModClient.getTextures().bindTexture(FormPbr.resolveAlbedo(this.form, "", texture, BBSModClient.getTextures().getTexture(texture)));
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            Supplier<ShaderProgram> mainShader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld()) || !model.isVAORendered()
                ? GameRenderer::getRenderTypeEntityTranslucentCullProgram
                : BBSShaders::getModel;

            this.renderModel(this.entity, mainShader, stack, model, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, contextColor, formColor, true, null, context.getTransition(), null);

            /* Render body parts */
            stack.push();
            stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

            this.renderBodyParts(new FormRenderingContext()
                .set(FormRenderType.ENTITY, this.entity, stack, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
                .inUI());

            stack.pop();
            stack.pop();

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
        }
    }

    private void renderModel(IEntity target, Supplier<ShaderProgram> program, MatrixStack stack, ModelInstance model, int light, int overlay, Color contextColor, Color formColor, boolean ui, StencilMap stencilMap, float transition, MatrixStack world)
    {
        Color finalColor = contextColor.copy();
        FormColorBlend.blend(finalColor, formColor);

        if (!model.isCulling())
        {
            RenderSystem.disableCull();
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GameRenderer gameRenderer = MinecraftClient.getInstance().gameRenderer;

        gameRenderer.getLightmapTextureManager().enable();
        gameRenderer.getOverlayTexture().setupOverlayColor();

        MatrixStack newStack = new MatrixStack();

        MatrixStackUtils.multiply(newStack, stack.peek().getPositionMatrix());
        newStack.peek().getNormalMatrix().set(stack.peek().getNormalMatrix());

        if (ui)
        {
            newStack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            newStack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);
        }

        /* Strictly the world frame: it's what places the model in the world for the simulating subsystems
         * (bone physics resolves gravity, wind and its collisions against it), so falling back to the render
         * stack when there is no world stack — the first person arm — resolved them against the camera
         * instead, and gravity pulled toward the bottom of the screen. Without a world frame there is no
         * honest answer, so they run model-local, as they do in the UI. */
        Matrix4f baseTransform = ui || world == null ? null : new Matrix4f(world.peek().getPositionMatrix());

        this.applyIK(model, baseTransform);
        this.applyPhysics(target, model, transition, baseTransform);
        this.applyConstraints(model);

        /* Default texture for materials without their own: the form's texture override, else the
         * model's default. Per-material textures (folder defaults now, animation tracks later)
         * layer on top via the resolver. */
        Link defaultTexture = this.form.texture.get();

        if (defaultTexture == null)
        {
            defaultTexture = model.getTexture();
        }

        final Link resolvedDefault = defaultTexture;

        /* A model with at most one material ignores the material system entirely: a single texture
         * (form.texture, else the model's base texture) covers the whole model, regardless of any
         * per-material folder/Kd default, editor pick, or animation track. Only with multiple materials
         * is the Default ambiguous - it's hidden in the editor then and must not affect them here either,
         * so they fall back to the model base texture. */
        final boolean ignoreMaterials = model.materials.size() <= 1;
        final Link materialFallback = ignoreMaterials ? resolvedDefault : model.getTexture();

        model.render(newStack, program, finalColor, light, overlay, stencilMap, this.form.shapeKeys.get(), (material) ->
        {
            if (ignoreMaterials)
            {
                return resolvedDefault;
            }

            /* Resolution order: animated per-material track > editor-picked static per-material
             * texture > the material's loaded default (folder/Kd) > the model base texture. */
            Link override = this.form.materialTextureOverrides.get(material);

            if (override != null)
            {
                return override;
            }

            Link picked = this.form.materialTextures.getLink(material);

            if (picked != null)
            {
                return picked;
            }

            return model.getMaterialTexture(material, materialFallback);
        });

        if (stencilMap == null && !this.renderingArm && this.form != null)
        {
            ModelIKDebug.render(newStack, model.model, this.form, "");
        }

        if (stencilMap == null && !this.renderingArm && this.form != null)
        {
            ModelPhysicsDebug.render(newStack, model.model, this.form, target.getAge(), "");
        }

        gameRenderer.getLightmapTextureManager().disable();
        gameRenderer.getOverlayTexture().teardownOverlayColor();
        RenderSystem.disableBlend();

        if (!model.isCulling())
        {
            RenderSystem.enableCull();
        }

        /* Render items */
        this.captureMatrices(model);

        if (stencilMap == null)
        {
            this.renderItems(target, model, stack, EquipmentSlot.MAINHAND, ModelTransformationMode.THIRD_PERSON_RIGHT_HAND, model.getItemsMain(), finalColor, overlay, light);
            this.renderItems(target, model, stack, EquipmentSlot.OFFHAND, ModelTransformationMode.THIRD_PERSON_LEFT_HAND, model.getItemsOff(), finalColor, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.getArmorSlots().entrySet())
            {
                this.renderArmor(target, stack, entry.getKey(), entry.getValue(), finalColor, overlay, light);
            }
        }
    }

    private void applyIK(ModelInstance model, Matrix4f baseTransform)
    {
        model.form = this.form;

        boolean hasOverrides = baseTransform != null && this.form != null
            && (!this.form.ikTargetOverrides.isEmpty() || !this.form.poleTargetOverrides.isEmpty());

        if (!hasOverrides)
        {
            ModelIKRuntime.apply(model, null, null);
            return;
        }

        Matrix4f inv = new Matrix4f(baseTransform).invert();
        Map<String, Vector3f> local = toModelSpace(this.form.ikTargetOverrides, inv);
        Map<String, Vector3f> poleLocal = toModelSpace(this.form.poleTargetOverrides, inv);

        if (local.isEmpty() && poleLocal.isEmpty())
        {
            ModelIKRuntime.apply(model, null, null);
            return;
        }

        ModelIKRuntime.apply(model, local.isEmpty() ? null : local, poleLocal.isEmpty() ? null : poleLocal);
    }

    /** World-space target overrides into the model's local space (the space the solver and pivot frames use). */
    private static Map<String, Vector3f> toModelSpace(Map<String, Vector3f> world, Matrix4f inv)
    {
        Map<String, Vector3f> local = new HashMap<>(world.size() * 2);

        for (Map.Entry<String, Vector3f> entry : world.entrySet())
        {
            String key = entry.getKey();
            Vector3f worldPos = entry.getValue();

            if (key == null || key.isEmpty() || worldPos == null)
            {
                continue;
            }

            Vector3f pos = new Vector3f(worldPos);
            inv.transformPosition(pos);
            local.put(key, pos);
        }

        return local;
    }

    private void applyPhysics(IEntity target, ModelInstance model, float transition, Matrix4f baseTransform)
    {
        model.lastBaseTransform = baseTransform;
        model.form = this.form;
        ModelPhysicsRuntime.apply(target, model, transition, baseTransform);
    }

    private void applyConstraints(ModelInstance model)
    {
        ModelConstraintsRuntime.apply(model);
    }

    private void renderArmor(IEntity target, MatrixStack stack, ArmorType type, ArmorSlot armorSlot, Color color, int overlay, int light)
    {
        Matrix4f matrix = this.bones.get(armorSlot.group).matrix();

        if (matrix != null)
        {
            CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

            stack.push();
            MatrixStackUtils.multiply(stack, matrix);
            MatrixStackUtils.applyTransform(stack, armorSlot.transform);
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180F));

            CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());

            /* Translucent armor layers ride the deferred sorted pass (see
             * CustomVertexConsumerProvider#draw(RenderLayer)); only reached outside picking. */
            Vector3f armorOrigin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(armorOrigin));

            ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers, target, type.slot, type, light);
            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);

            CustomVertexConsumerProvider.clearRunnables();

            stack.pop();

            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    private void renderItems(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ModelTransformationMode mode, List<ArmorSlot> items, Color color, int overlay, int light)
    {
        ItemStack itemStack = target.getEquipmentStack(slot);

        if (itemStack != null && itemStack.isEmpty())
        {
            return;
        }

        /* The film's use state makes the vanilla item model predicates fire in
         * the third person too: a drawn bow bends and shows its arrow, a shield
         * blocks, a trident lifts. The donor must hold the very stack instance
         * being rendered - the predicates compare by identity. */
        ItemUsePose.Use use = ThirdPersonItemUse.get(target, slot == EquipmentSlot.MAINHAND);
        LivingEntity holder = use == null ? liveHolder(target) : ItemPredicateDonor.get(itemStack, use);

        for (ArmorSlot armorSlot : items)
        {
            Matrix4f matrix = this.bones.get(armorSlot.group).matrix();

            if (matrix != null)
            {
                CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();

                stack.push();
                MatrixStackUtils.multiply(stack, matrix);
                stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90F));
                stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180F));
                stack.translate(0F, 0.125F, 0F);
                MatrixStackUtils.applyTransform(stack, armorSlot.transform);

                CustomVertexConsumerProvider.hijackVertexFormat((l) -> RenderSystem.enableBlend());

                /* Translucent item layers (potions, glass blocks in hand) ride the deferred
                 * sorted pass; only reached outside picking. */
                Vector3f itemOrigin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(itemOrigin));

                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                /* For some reason, due to Sodium and my color consumer, in some cases items like Trident,
                 * shield, etc. not get rendered, but if in another arm there is another item, it does render...
                 * So, I render a 0 size oak button to circumvent that bug! */
                if (model.model instanceof BOBJModel)
                {
                    stack.push();
                    stack.scale(0F, 0F, 0F);
                    MinecraftClient.getInstance().getItemRenderer().renderItem(null, new ItemStack(Items.OAK_BUTTON), mode, mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND, stack, consumers, target.getWorld(), light, overlay, 0);
                    consumers.draw();
                    stack.pop();
                }

                MinecraftClient.getInstance().getItemRenderer().renderItem(holder, itemStack, mode, mode == ModelTransformationMode.THIRD_PERSON_LEFT_HAND, stack, consumers, target.getWorld(), light, overlay, 0);
                consumers.draw();
                consumers.setSubstitute(null);
                FormTranslucentQueue.setSortOrigin(null);

                CustomVertexConsumerProvider.clearRunnables();

                stack.pop();

                RenderSystem.enableDepthTest();
            }
        }
    }

    /**
     * Outside of a film - a player morphed into a form, or the one being
     * recorded - the entity holding the item is real and knows what it is doing,
     * so vanilla's model predicates get it as is. The film's own actors never
     * reach here: their state comes from the clips through the donor above.
     */
    private static LivingEntity liveHolder(IEntity target)
    {
        return target instanceof MCEntity mc && mc.getMcEntity() instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            ArmorSlot slot = hand == Hand.MAIN_HAND ? model.getFpMain() : model.getFpOffhand();

            if (slot == null)
            {
                return false;
            }

            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = Color.white();
            Color formColor = this.form.color.get();

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                ModelGroup g = group;
                boolean visible = false;

                while (g != null)
                {
                    if (g.id.equals(slot.group))
                    {
                        visible = true;

                        break;
                    }

                    g = g.parent;
                }

                group.visible = visible;
            }

            model.model.resetPose();

            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            MatrixStackUtils.applyTransform(matrices, slot.transform);

            BBSModClient.getTextures().bindTexture(FormPbr.resolveAlbedo(this.form, "", texture, BBSModClient.getTextures().getTexture(texture)));

            Supplier<ShaderProgram> mainShader = (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld()) || !model.isVAORendered()
                ? GameRenderer::getRenderTypeEntityTranslucentCullProgram
                : BBSShaders::getModel;

            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();

            this.renderingArm = true;

            /* Vanilla's renderArm zeroes the arm's pitch: the first person arm
             * is never bent by the use poses, they belong to the third person. */
            ItemUsePose.setSuppressed(true);

            try
            {
                this.renderModel(this.entity, mainShader, matrices, model, light, OverlayTexture.DEFAULT_UV, contextColor, formColor, false, null, 0F, null);
            }
            finally
            {
                this.renderingArm = false;
                ItemUsePose.setSuppressed(false);
            }

            for (ModelGroup group : model.getModel().getAllGroups())
            {
                group.visible = true;
            }

            matrices.pop();

            return true;
        }

        return super.renderArm(matrices, light, player, hand);
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = new Color().set(context.color, true);
            Color formColor = this.form.color.get();

            if (context.isPicking())
            {
                contextColor.mul(formColor);
                formColor = Color.white();
            }
            this.evaluateChannels(context.entity, model, context.getTransition());

            context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            if (context.world != null)
            {
                context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(FormPbr.resolveAlbedo(this.form, "", texture, textureObject));

            boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

            /* Under shaders we can't split opaque/translucent per pixel (Iris strips our PassMode),
             * so a texture with semi-transparent texels would either hide what's behind it or drop
             * the whole model out of Photon's translucent handling. Degrade gracefully to alpha
             * cutout: the cutout program's baked alpha test turns fully-transparent texels into
             * proper holes and draws the rest as a solid, normally-shaded entity. Only for texture
             * translucency at full colour — a uniform colour fade must stay translucent, or the
             * cutout test would erase the whole faded model.
             *
             * The material tab's explicit render layer overrides the heuristic: SOLID and CUTOUT
             * draw immediately with blending off (CUTOUT rides the cutout program's alpha test
             * under Iris; without Iris the BBS shader's own discard covers it), TRANSLUCENT forbids
             * the cutout degrade so the model stays in the pack's translucent phase. */
            int renderLayer = this.form.renderLayer.get();
            boolean cutout = renderLayer == Form.LAYER_AUTO
                ? irisWorld && textureObject != null && textureObject.hasTranslucency()
                    && contextColor.a >= 1F && formColor.a >= 1F
                : renderLayer == Form.LAYER_CUTOUT && irisWorld;
            boolean noBlend = cutout || renderLayer == Form.LAYER_SOLID
                || (renderLayer == Form.LAYER_CUTOUT && !irisWorld);
            boolean suspendQueue = irisWorld || renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;

            Supplier<ShaderProgram> mainShader = cutout
                ? GameRenderer::getRenderTypeEntityCutoutProgram
                : (irisWorld || !model.isVAORendered())
                    ? GameRenderer::getRenderTypeEntityTranslucentCullProgram
                    : BBSShaders::getModel;
            Supplier<ShaderProgram> shader = this.getShader(context, mainShader, BBSShaders::getPickerModelsProgram);

            boolean wasActive = false;

            if (suspendQueue)
            {
                /* Under Iris the model always draws right now, in the phase its program is meant
                 * for. The end-of-frame replay runs after a deferred pack's shading composite —
                 * Photon never shades it and the model vanishes (a 1% colour fade used to fall
                 * into that path). Vanilla translucent entities don't sort either: vanilla-level
                 * blending is the ceiling under shaders, the sorted queue stays a no-shader
                 * feature. The explicit SOLID/CUTOUT layers opt out of the queue too. */
                wasActive = FormTranslucentQueue.suspend();
            }

            if (noBlend)
            {
                /* Blend off to match the vanilla cutout render type: semi-transparent texels
                 * draw solid instead of smearing over the gbuffer. */
                RenderSystem.disableBlend();
            }

            try
            {
                this.renderModel(context.entity, shader, context.stack, model, context.light, context.overlay, contextColor, formColor, false, context.stencilMap, context.getTransition(), context.world);
            }
            finally
            {
                if (noBlend)
                {
                    RenderSystem.enableBlend();
                }

                if (suspendQueue)
                {
                    FormTranslucentQueue.restore(wasActive);
                }
            }
        }
    }

    @Override
    protected void updateStencilMap(FormRenderingContext context)
    {
        ModelInstance model = this.getModel();

        if (model == null || model.model == null || context.stencilMap == null)
        {
            return;
        }

        model.fillStencilMap(context.stencilMap, this.form);

        if (this.form != null)
        {
            ModelIKDebug.renderStencil(context.stack, model.model, this.form, context.stencilMap, this.form);
        }

        if (this.form != null)
        {
            ModelPhysicsDebug.renderStencil(context.stack, model.model, this.form, context.stencilMap, this.form);
        }
    }

    private void captureMatrices(ModelInstance model)
    {
        /* this.bones.clear()? */
        model.captureMatrices(this.bones);
    }

    @Override
    public void renderBodyParts(FormRenderingContext context)
    {
        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

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
            else
            {
                context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                if (context.world != null)
                {
                    context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }
            }

            this.renderBodyPart(part, context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }

        this.bones.clear();
        context.stack.pop();
        if (context.world != null)
        {
            context.world.pop();
        }
    }

    @Override
    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        ModelInstance model = this.getModel();
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

        /* Collect bones and add them to matrix list */
        if (this.animator != null && model != null)
        {
            this.evaluateChannels(entity, model, transition);

            /* Solve IK here too, so a bone anchored to an IK-driven bone (a head pinned to
             * body_upper) rides the solved pose — these matrices feed the anchor system, the
             * gizmo and trackers, which otherwise see the FK-only pose the render path moved
             * past. The live-drag world-space target overrides need a base transform this
             * local pass doesn't carry, so the config/`ik`-track solve runs (controllers
             * keyed into the pose are already baked in and reached). */
            model.form = this.form;
            ModelIKRuntime.apply(model, null, null);

            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            this.captureMatrices(model);
        }

        for (Map.Entry<String, MatrixCacheEntry> entry : this.bones.entrySet())
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

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), matrix, o, entry.getValue().evaluatedRotation());
        }

        int i = 0;

        /* Recursively do the same thing with body parts */
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f matrix = this.bones.get(part.bone.get()).matrix();

                stack.push();

                if (matrix != null)
                {
                    MatrixStackUtils.multiply(stack, matrix);
                }
                else
                {
                    stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(part.getRenderEntity(entity), stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();

        this.bones.clear();
    }

    /**
     * Form-local displacement that drags the shadow under the model's perceived position: how far the
     * model has moved from its bind pose, counting BOTH the form's own transform (its keyframes) and
     * the anchor bone's root motion. Falls back to the base form-transform displacement when there's
     * no model or no anchor bone, so every form still shifts its shadow by its transform.
     */
    @Override
    public Vector3f getShadowDisplacement(IEntity entity, float transition)
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return super.getShadowDisplacement(entity, transition);
        }

        String anchor = model.getAnchor();

        if (anchor == null || anchor.isEmpty())
        {
            return super.getShadowDisplacement(entity, transition);
        }

        Vector3f current = this.sampleBoneOrigin(entity, transition, anchor, false);
        Vector3f rest = this.sampleBoneOrigin(entity, transition, anchor, true);

        if (current == null || rest == null)
        {
            return super.getShadowDisplacement(entity, transition);
        }

        return current.sub(rest);
    }

    /**
     * Capture a bone's origin translation in form-local space, either in the current animated pose
     * ({@code rest = false}) or the model's rest/bind pose ({@code rest = true}). Mirrors the root-form
     * portion of {@link #collectMatrices} so both samples share the same frame and the form's own
     * transform cancels out when they are subtracted.
     */
    private Vector3f sampleBoneOrigin(IEntity entity, float transition, String bone, boolean rest)
    {
        ModelInstance model = this.getModel();

        if (model == null)
        {
            return null;
        }

        MatrixStack stack = new MatrixStack();

        stack.push();

        /* The current sample includes the form's own transform (so its keyframes move the shadow); the
         * rest sample omits it and stays in the bind pose, so subtracting the two yields the full
         * displacement of the model from rest — form transform plus anchor-bone root motion. The
         * model's default scale is static, though, so it must be applied to BOTH samples or it won't
         * cancel and the bind pose ends up at a different height (a constant ~1/16 shadow sink). */
        if (rest)
        {
            Vector3f scale = model.getScale();

            stack.scale(scale.x, scale.y, scale.z);
        }
        else
        {
            this.applyTransforms(stack, false, transition);
        }

        if (rest || this.animator == null)
        {
            model.model.resetPose();
        }
        else
        {
            this.evaluateChannels(entity, model, transition);
        }

        stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        this.captureMatrices(model);

        Vector3f result = null;
        MatrixCacheEntry entry = this.bones.get(bone);

        if (entry != null)
        {
            stack.push();
            MatrixStackUtils.multiply(stack, entry.origin());
            result = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
            stack.pop();
        }

        this.bones.clear();
        stack.pop();

        return result;
    }

    @Override
    public void tick(IEntity entity)
    {
        this.ensureAnimator(0F);

        if (this.animator != null)
        {
            this.animator.update(entity);
        }
    }
}
