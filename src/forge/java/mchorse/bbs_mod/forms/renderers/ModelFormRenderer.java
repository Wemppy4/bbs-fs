package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;

import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.renderer.ItemPredicateDonor;
import mchorse.bbs_mod.client.renderer.NativeEquipmentRenderer;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.spline.ModelSplineRuntime;
import mchorse.bbs_mod.cubic.jem.CemAnimator;
import mchorse.bbs_mod.cubic.jem.CemVanillaStage;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsDebug;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
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
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.utils.pose.PoseBones;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
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
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import net.minecraft.client.Minecraft;
import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import net.minecraft.client.entity.AbstractClientPlayer;
import mchorse.bbs_mod.graphics.texture.Texture;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import mchorse.bbs_mod.graphics.MatrixStack;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import org.joml.Vector3f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
    private ModelSplineRuntime.Motion splineMotion;

    /** Result of this form's latest evaluation, never stored on the shared model asset. */
    public ModelSplineRuntime.Motion getSplineMotion()
    {
        return this.splineMotion;
    }

    private ActionsConfig lastConfigs;
    private IAnimator animator;
    private ModelInstance lastModel;
    private boolean renderingArm;

    private IEntity entity = new StubEntity();

    /**
     * Render the bind pose alone — no actions, no default pose, no form pose: what the model editor
     * edits the model's geometry against.
     */
    private boolean rest;

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

    @Override
    public IBoneHierarchy getBoneHierarchy()
    {
        ModelInstance model = this.getModel();

        return model == null ? null : model.model;
    }

    public Pose getPose()
    {
        this.form.syncOverlayTracks();
        BBSProfiler.count(BBSProfiler.Section.POSE_COPY);

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
            poseTransform.visible &= value.visible;

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

    public void setRest(boolean rest)
    {
        this.rest = rest;
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
        this.splineMotion = null;
        /* The asset already holds this exact evaluation (same form, entity, transition, frame
         * and pose version) — every render pass of a frame used to redo it: the main render,
         * the shadow displacement's two samples, the stencil pass, the Iris shadow pass.
         * Skipping rewinds the constraint stack's orient/offset writes to the channels-phase
         * snapshot, because IK/physics blend FROM the evaluated state and must not stack on
         * their own previous output. Both skeleton flavours keep such a snapshot. */
        if (this.rest)
        {
            /* Nothing stamped: the cached evaluation is of the posed model, and leaving rest must not restore it. */
            model.model.resetPose();
            model.clearChannels();

            return;
        }

        boolean cacheable = this.form != null && model.model != null && RenderFrame.isEnabled();

        if (cacheable && model.matchesChannels(this.form, entity, transition, RenderFrame.getEpoch(), this.form.getPoseVersion()))
        {
            BBSProfiler.count(BBSProfiler.Section.CHANNELS_SKIPPED);

            model.model.restoreChannels();

            return;
        }

        BBSProfiler.count(BBSProfiler.Section.EVALUATE_CHANNELS);

        model.model.resetPose();

        /* The states a CEM pack asks about that only the form can answer — sitting, tamed, angry. Read
         * here rather than kept in sync, so a keyframe on one of them lands the frame it changes. */
        if (this.animator instanceof CemAnimator cem)
        {
            cem.status.read(this.form);
        }

        this.animator.applyActions(entity, model, transition);

        /* The config's default pose sits under the form's, the same additive layer: the posture the
         * model has before anything of the form is applied, whichever animator drove it. */
        model.model.applyPose(model.getDefaultPose());
        model.model.applyPose(this.getPose());

        if (cacheable)
        {
            model.model.snapshotChannels();
            model.stampChannels(this.form, entity, transition, RenderFrame.getEpoch(), this.form.getPoseVersion());
        }
        else
        {
            model.clearChannels();
        }
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

        this.animator = createAnimator(model);
        this.animator.setup(model, actionsConfig, false);

        this.lastConfigs = new ActionsConfig();
        this.lastConfigs.copy(actionsConfig);
        this.lastModel = model;
    }

    /**
     * The animator stage for a model: a .jem's live CEM program drives it, otherwise the config's
     * choice between vanilla-like procedural and keyframe actions.
     */
    private static IAnimator createAnimator(ModelInstance model)
    {
        if (model.cemAnimation != null)
        {
            if (model.config.cemAnimation.get())
            {
                return new CemAnimator(model.cemAnimation, new CemVanillaStage(model.cemAnimation.jem));
            }

            /* CEM drove the bones' visibility and nothing else resets it: switched off, every bone shows again. */
            for (ModelGroup group : model.model.getAllGroups())
            {
                group.visible = true;
            }
        }

        return model.isProcedural() ? new ProceduralAnimator() : new Animator();
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
        this.splineMotion = null;
        context.batcher.flush();

        this.ensureAnimator(context.getTransition());

        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            MatrixStack stack = context.batcher.getContext().getMatrices();
            boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);

            stack.push();

            try
            {
                Matrix4f uiMatrix = this.getPreviewMatrix(context, x1, y1, x2, y2);

                this.applyTransforms(uiMatrix, context.getTransition());

                Link link = this.form.texture.get();
                Link texture = link == null ? model.getTexture() : link;
                Color contextColor = Color.white();
                Color formColor = this.form.color.get();

                this.evaluateChannels(null, model, context.getTransition());

                MatrixStackUtils.multiply(stack, uiMatrix);

                BBSModClient.getTextures().bindTexture(texture);

                /* Native GUI callers can leave depth disabled. Changing its comparison alone
                 * cannot make the thumbnail's front faces occlude its back faces. */
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);

                Supplier<ShaderProgram> mainShader = BBSShaders::getModel;

                this.renderModel(this.entity, mainShader, stack, model, 0x00f000f0, (10 << 16), contextColor, formColor, true, null, context.getTransition(), null);

                /* Render body parts */
                stack.push();

                try
                {
                    stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
                    stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

                    this.renderBodyParts(new FormRenderingContext()
                        .set(FormRenderType.ENTITY, this.entity, stack, 0x00f000f0, (10 << 16), context.getTransition())
                        .inUI());
                }
                finally
                {
                    stack.pop();
                }
            }
            finally
            {
                stack.pop();
                RenderSystem.depthMask(depthMask);
                RenderSystem.depthFunc(depthFunc);
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            }
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

        if (stencilMap != null || this.form.renderLayer.get() == Form.LAYER_SOLID || this.form.renderLayer.get() == Form.LAYER_CUTOUT)
            RenderSystem.disableBlend();
        else RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

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

        RepeatedFormRender repeated = RepeatedFormRender.current();
        if (repeated == null || repeated.pose(this) == null)
        {
            this.applyIK(model, baseTransform);
            this.applyPhysics(target, model, transition, baseTransform);
            this.applySpline(model);
            this.applyConstraints(model);
            if (repeated != null) repeated.capture(this, model, this.splineMotion);
        }
        this.applySplineMotion(newStack);

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

        RenderSystem.disableBlend();

        if (!model.isCulling())
        {
            RenderSystem.enableCull();
        }

        /* Render items. The capture allocates ~4 matrices per bone, and its only readers here
         * are the item/armor block right below (skipped in the picking pass entirely) and
         * renderBodyParts afterwards - so a model with neither pays for neither. */
        boolean hasEquipment = !model.getItemsMain().isEmpty() || !model.getItemsOff().isEmpty() || !model.getArmorSlots().isEmpty();
        boolean hasBodyParts = this.form != null && !this.form.parts.getAllTyped().isEmpty();

        if (hasBodyParts || (stencilMap == null && hasEquipment))
        {
            this.captureMatrices(model);
        }

        if (stencilMap == null && hasEquipment)
        {
            this.renderItems(target, model, stack, EntityEquipmentSlot.MAINHAND, TransformType.THIRD_PERSON_RIGHT_HAND, model.getItemsMain(), finalColor, overlay, light);
            this.renderItems(target, model, stack, EntityEquipmentSlot.OFFHAND, TransformType.THIRD_PERSON_LEFT_HAND, model.getItemsOff(), finalColor, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.getArmorSlots().entrySet())
            {
                this.renderArmor(target, stack, entry.getKey(), entry.getValue(), finalColor, overlay, light);
            }
        }
    }

    /**
     * The channels phase for a reader outside the render: poses the model for the entity the
     * way the render does (rest &rarr; actions &rarr; pose) and leaves it there — the FK truth
     * the constraint stack starts from. {@code null} when the form has no model. A reader's
     * sample is never a repeat of the frame's evaluation, so the frame stamp is dropped first
     * and the evaluation always runs.
     */
    public ModelInstance evaluateChannels(IEntity entity, float transition)
    {
        this.ensureAnimator(transition);

        ModelInstance model = this.getModel();

        if (this.animator == null || model == null || model.model == null)
        {
            return null;
        }

        model.clearChannels();
        this.evaluateChannels(entity, model, transition);

        return model;
    }

    /**
     * The IK stage on the model as it stands (see {@link #evaluateChannels(IEntity, float)}):
     * the form's chains solved onto the bones' orientations, exactly as the render does before
     * drawing. {@code entityWorld} is the frame the film stands the entity in — what
     * {@code FilmEntityRenderer} renders it under — so the film's world-space targets are brought
     * into the model the way the render brings them; {@code null} solves against the model alone.
     */
    public void solveIK(ModelInstance model, Matrix4f entityWorld, float transition)
    {
        Matrix4f base = null;

        if (entityWorld != null)
        {
            /* The model's frame as the render establishes it: the entity's, then the form's own
             * transform and the model's scale, then the half turn every model renders under. */
            base = new Matrix4f(entityWorld);

            this.applyTransforms(base, transition);
            base.rotateY(MathUtils.PI);
        }

        this.applyIK(model, base);
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

    private void applySpline(ModelInstance model)
    {
        this.splineMotion = this.rest ? null : ModelSplineRuntime.apply(model, this.form);
    }

    private void applySplineMotion(MatrixStack stack)
    {
        if (this.splineMotion != null)
        {
            Matrix4f motion = this.splineMotion.matrix();
            stack.peek().getPositionMatrix().mul(motion);
            /* A scaled parent can make the conjugated motion affine, so preserve its shear
             * and use the inverse transpose for normals instead of decomposing it to TRS. */
            stack.peek().getNormalMatrix().mul(motion.normal(new Matrix3f()));
        }
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
        FormPoseEvents.MODEL_POSE.invoker().apply(this.form, target, model, transition, baseTransform, FormPoseEvents.Pass.RENDER);
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
            stack.push();
            try
            {
            MatrixStackUtils.multiply(stack, matrix);
            MatrixStackUtils.applyTransform(stack, armorSlot.transform);
            stack.multiply(new org.joml.Quaternionf().rotationX(MathUtils.toRad(180F)));

            ItemStack equipment = target.getEquipmentStack(type.slot).copy();
            NativeEquipmentRenderer.draw(stack, color, light, () -> ActorEntityRenderer.armorRenderer.renderArmorSlot(target, equipment, type));
            }
            finally { stack.pop(); }
        }
    }

    private void renderItems(IEntity target, ModelInstance model, MatrixStack stack, EntityEquipmentSlot slot, TransformType mode, List<ArmorSlot> items, Color color, int overlay, int light)
    {
        ItemStack itemStack = target.getEquipmentStack(slot).copy();

        if (itemStack.isEmpty())
        {
            return;
        }

        /* The film's use state makes the vanilla item model predicates fire in
         * the third person too: a drawn bow bends and shows its arrow, a shield
         * blocks, a trident lifts. The donor must hold the very stack instance
         * being rendered - the predicates compare by identity. */
        ItemUsePose.Use use = ThirdPersonItemUse.get(target, slot == EntityEquipmentSlot.MAINHAND);

        for (ArmorSlot armorSlot : items)
        {
            Matrix4f matrix = this.bones.get(armorSlot.group).matrix();

            if (matrix != null)
            {
                stack.push();
                try
                {
                MatrixStackUtils.multiply(stack, matrix);
                stack.multiply(new org.joml.Quaternionf().rotationX(MathUtils.toRad(90F)));
                stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.toRad(180F)));
                stack.translate(0F, 0.125F, 0F);
                MatrixStackUtils.applyTransform(stack, armorSlot.transform);

                NativeEquipmentRenderer.draw(stack, color, light, () ->
                {
                    EntityLivingBase holder = use == null ? liveHolder(target) : ItemPredicateDonor.get(itemStack, use);
                    Minecraft.getMinecraft().getRenderItem().renderItem(itemStack, holder, mode, mode == TransformType.THIRD_PERSON_LEFT_HAND);
                });
                }
                finally { stack.pop(); }
            }
        }
    }

    /**
     * Outside of a film - a player morphed into a form, or the one being
     * recorded - the entity holding the item is real and knows what it is doing,
     * so vanilla's model predicates get it as is. The film's own actors never
     * reach here: their state comes from the clips through the donor above.
     */
    private static EntityLivingBase liveHolder(IEntity target)
    {
        return target instanceof MCEntity mc && mc.getMcEntity() instanceof EntityLivingBase living ? living : null;
    }

    @Override
    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayer player, EnumHand hand)
    {
        if (this.renderFirstPersonHand(matrices, light, hand))
        {
            return true;
        }

        return super.renderArm(matrices, light, player, hand);
    }

    /**
     * Vanilla's frame for an empty first-person hand — {@code HeldItemRenderer#renderArmHoldingItem}
     * with no swing and no equip progress, up to where {@code PlayerEntityRenderer#renderArm} (and so
     * {@link #renderArm} above) is entered. This is what the model editor's first-person preview
     * multiplies before {@link #renderFirstPersonHand}, so the preview matches the game. The main hand
     * is the right arm; a left-handed player is not modelled here.
     */
    public static void applyFirstPersonArm(MatrixStack stack, boolean mainHand)
    {
        float f = mainHand ? 1F : -1F;

        stack.translate(f * 0.64F, -0.6F, -0.72F);
        stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.toRad(f * 45F)));
        stack.translate(f * -1F, 3.6F, 3.5F);
        stack.multiply(new org.joml.Quaternionf().rotationZ(MathUtils.toRad(f * 120F)));
        stack.multiply(new org.joml.Quaternionf().rotationX(MathUtils.toRad(200F)));
        stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.toRad(f * -135F)));
        stack.translate(f * 5.6F, 0F, 0F);
    }

    /**
     * The model's first-person hand: only the branch under the slot's bone, placed by the slot's
     * transform in the arm frame the caller has set up (the game's own, or
     * {@link #applyFirstPersonArm}). Shared by the in-game arm and the model editor's preview.
     * Returns false when the model has no slot for that hand.
     */
    public boolean renderFirstPersonHand(MatrixStack matrices, int light, EnumHand hand)
    {
        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            ArmorSlot slot = hand == EnumHand.MAIN_HAND ? model.getFpMain() : model.getFpOffhand();

            if (slot == null)
            {
                return false;
            }

            Link link = this.form.texture.get();
            Link texture = link == null ? model.getTexture() : link;
            Color contextColor = Color.white();
            Color formColor = this.form.color.get();
            Map<ModelGroup, Boolean> visibility = new IdentityHashMap<>();
            boolean previousRenderingArm = this.renderingArm;
            boolean previousSuppressed = ItemUsePose.isSuppressed();
            boolean matrixPushed = false;

            try
            {
                for (ModelGroup group : model.getModel().getAllGroups())
                {
                    visibility.put(group, group.visible);

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

                /* The cached channel evaluation is of the posed model, and this reset leaves the
                 * bind pose behind: a stamp left standing would have the next pass restore only
                 * the constraint writes on top of it - see evaluateChannels' own rest branch. */
                model.model.resetPose();
                model.clearChannels();

                matrices.push();
                matrixPushed = true;
                matrices.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
                MatrixStackUtils.applyTransform(matrices, slot.transform);

                BBSModClient.getTextures().bindTexture(texture);

                Supplier<ShaderProgram> mainShader = BBSShaders::getModel;

                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();

                this.renderingArm = true;

                /* Vanilla's renderArm zeroes the arm's pitch: the first person arm
                 * is never bent by the use poses, they belong to the third person. */
                ItemUsePose.setSuppressed(true);

                this.renderModel(this.entity, mainShader, matrices, model, light, (10 << 16), contextColor, formColor, false, null, 0F, null);
            }
            finally
            {
                this.renderingArm = previousRenderingArm;
                ItemUsePose.setSuppressed(previousSuppressed);

                for (Map.Entry<ModelGroup, Boolean> entry : visibility.entrySet())
                {
                    entry.getKey().visible = entry.getValue();
                }

                if (matrixPushed)
                {
                    matrices.pop();
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public void render3D(FormRenderingContext context)
    {
        this.splineMotion = null;
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
            RepeatedFormRender repeated = RepeatedFormRender.current();
            RepeatedFormRender.ModelPose pose = repeated == null ? null : repeated.pose(this);
            if (pose == null) this.evaluateChannels(context.entity, model, context.getTransition());
            else
            {
                pose.restore();
                this.splineMotion = pose.motion;
            }

            context.stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
            if (context.world != null)
            {
                context.world.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
            }

            Texture textureObject = BBSModClient.getTextures().getTexture(texture);

            BBSModClient.getTextures().bindTexture(textureObject);
            int renderLayer = this.form.renderLayer.get();
            boolean noBlend = renderLayer == Form.LAYER_SOLID || renderLayer == Form.LAYER_CUTOUT;
            boolean wasActive = false;
            if (noBlend) wasActive = FormTranslucentQueue.suspend();
            Supplier<ShaderProgram> shader = this.getShader(context, BBSShaders::getModel, BBSShaders::getPickerModelsProgram);

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

                if (noBlend)
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

        context.stack.push();
        this.applySplineMotion(context.stack);
        if (this.form != null)
        {
            ModelIKDebug.renderStencil(context.stack, model.model, this.form, context.stencilMap, this.form);
        }

        if (this.form != null)
        {
            ModelPhysicsDebug.renderStencil(context.stack, model.model, this.form, context.stencilMap, this.form);
        }
        context.stack.pop();
    }

    private void captureMatrices(ModelInstance model)
    {
        this.bones.clear();
        model.captureMatrices(this.bones);
        if (this.splineMotion != null)
        {
            for (Map.Entry<String, MatrixCacheEntry> entry : this.bones.entrySet())
            {
                this.splineMotion.matrix().mul(entry.getValue().matrix(), entry.getValue().matrix());
                this.splineMotion.matrix().mul(entry.getValue().origin(), entry.getValue().origin());
            }
        }
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
            Matrix4f matrix = part.filterBoneMatrix(this.bones.get(part.bone.get()).matrix());

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
                this.applySplineMotion(context.stack);
                context.stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
                if (context.world != null)
                {
                    this.applySplineMotion(context.world);
                    context.world.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
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
        this.splineMotion = null;
        FormPoseEvents.PARENT_FRAME.invoker().capture(this.form, entity, stack.peek().getPositionMatrix(), prefix, transition);

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
            FormPoseEvents.MODEL_POSE.invoker().apply(this.form, entity, model, transition, null, FormPoseEvents.Pass.MATRICES);
            this.applySpline(model);

            stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
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

        /* Recursively do the same thing with body parts */
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                Matrix4f matrix = part.filterBoneMatrix(this.bones.get(part.bone.get()).matrix());

                stack.push();

                if (matrix != null)
                {
                    MatrixStackUtils.multiply(stack, matrix);
                }
                else
                {
                    this.applySplineMotion(stack);
                    stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
                }

                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(part.getRenderEntity(entity), stack, matrices, StringUtils.combinePaths(prefix, part.getId()), transition);

                stack.pop();
            }
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
            if (this.form.splines.getAllTyped().stream().anyMatch(spline -> spline.moveModel.get() && this.form.splineIK.get().get(spline.getId()).influence > 0F))
            {
                this.ensureAnimator(transition);
                Vector3f movedOrigin = this.sampleBoneOrigin(entity, transition, null, false);
                if (movedOrigin != null) return movedOrigin;
            }
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
        this.splineMotion = null;
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
            /* Same as above: the rest sample wipes the live channels the posed evaluation left,
             * so the stamp must go with them - otherwise the render that follows this sampling
             * hits the cache and draws the bind pose (the form's pose silently gone). */
            model.model.resetPose();
            model.clearChannels();
        }
        else
        {
            this.evaluateChannels(entity, model, transition);
            if (!this.form.splines.getAllTyped().isEmpty())
            {
                /* Match the non-simulating pose used by matrix readers, including an
                 * IK-driven parent of the travelling chain and addon pose contributions. */
                model.form = this.form;
                ModelIKRuntime.apply(model, null, null);
                FormPoseEvents.MODEL_POSE.invoker().apply(this.form, entity, model, transition, null, FormPoseEvents.Pass.MATRICES);
            }
            this.applySpline(model);
        }

        stack.multiply(new org.joml.Quaternionf().rotationY(MathUtils.PI));
        if (bone == null)
        {
            this.applySplineMotion(stack);
            Vector3f origin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());
            stack.pop();
            return origin;
        }
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
