package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.client.renderer.ItemPredicateDonor;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
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
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private boolean ikAppliedThisRender;
    private boolean physicsAppliedThisRender;
    private boolean constraintsAppliedThisRender;
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
            PoseTransform poseTransform = targetPose.get(entry.getKey());
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
     * of it separately (render: the apply*Once trio; matrix capture: its explicit IK solve) and
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

        /* List/icon form preview: submit a vanilla special GUI element so the form's model renders off-screen
         * and the deferred GUI composites it into this cell. The list draws each cell in the GUI record phase,
         * where a direct immediate 3D draw can't composite (two-phase GUI), so we reuse the mechanism vanilla
         * uses for entity/item thumbnails. BbsFormGuiElementRenderer.render then calls back into renderUIPreview
         * during the GUI prepare phase (with ModelPreviewRenderer.ACTIVE so the model draws into the FBO). The
         * cursor-driven yaw is computed here (same as the original getUIMatrix) since render() has no context. */
        float angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        if (BBSSettings.freezeModels.get())
        {
            angle = -MathUtils.PI + MathUtils.PI / 8;
        }

        net.minecraft.client.gui.DrawContext bbs$dc = context.batcher.getContext();

        /* Capture the live 2D GUI matrix (carries the list's scroll translate) so the thumbnail composites at
         * the scrolled cell position — faithful to the original, which rendered onto getMatrices() directly. */
        org.joml.Matrix3x2f bbs$pose = new org.joml.Matrix3x2f(bbs$dc.getMatrices());

        /* Read the live GUI scissor (set by the caller's batcher.clip, e.g. UIReplayList clips the form preview
         * to the row's square) and carry it as the composite quad's scissorArea — without it the model renders
         * full-size and overflows the cell instead of being cropped. Faithful to the original, where renderUI
         * was bracketed by batcher.clip/unclip and the immediate 3D draw respected the GL scissor.
         *
         * The scissor here is now correct under scroll: Batcher2D.clip neutralises the GUI matrix pose around
         * DrawContext.enableScissor (which on 1.21.11 transforms the rect by that pose, double-shifting it by the
         * scroll), so the stored scissor is shifted by the scroll exactly once (to y - S) — in lock-step with the
         * geometry placed by bbs$pose. */
        net.minecraft.client.gui.ScreenRect bbs$scissor = bbs$dc.scissorStack.peekLast();

        bbs$dc.state.addSpecialElement(new mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderState(
            this, angle, context.getTransition(), bbs$pose, x1, y1, x2, y2, 1.0F, bbs$scissor));
    }

    /**
     * Render this model form into the special-element off-screen FBO bound by {@code BbsFormGuiElementRenderer}
     * (vanilla's 3D-in-GUI mechanism), for a form-list thumbnail. The base renderer has set an ORTHOGRAPHIC
     * projection and pre-translated {@code stack} to the cell (origin at the horizontal centre, getYOffset =
     * 0.85*height down). Here we apply the rest of the original {@link #getUIMatrix} framing (cell scale, 22.5°
     * forward tilt, cursor-driven yaw), the form transform + uiScale, set the adopted model texture so
     * {@link ModelInstance#render} takes the entityCutoutNoCull immediate branch, then draw. The caller manages
     * {@code ModelPreviewRenderer.ACTIVE} + diffuse lighting + restore.
     */
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {
        this.ensureAnimator(transition);

        ModelInstance model = this.getModel();

        if (this.animator == null || model == null)
        {
            return;
        }

        Link link = this.form.texture.get();
        Link texture = link == null ? model.getTexture() : link;
        Color contextColor = Color.white();
        Color formColor = this.form.color.get();
        float scale = this.form.uiScale.get() * model.getUiScale();

        /* Route cubic geometry through the vanilla entity layer keyed on this model's (adopted) texture,
         * exactly like render3D — this is what makes ModelInstance.render take the entityCutoutNoCull branch. */
        ModelPreviewRenderer.TEXTURE = AdoptedTexture.identifier(BBSModClient.getTextures().getTexture(texture));

        model.model.resetPose();
        this.animator.applyActions(null, model, transition);
        model.model.applyPose(this.getPose());

        /* The base already did translate(width/2, 0.85*height, 0) + scale(wsf, wsf, -wsf); reproduce the rest
         * of getUIMatrix in logical-pixel space. The original used scale(s, -s, s); the base's extra -Z means
         * we flip Z here to preserve the original handedness (Y/Z signs are the empirical knob if flipped). */
        float cellScale = (y2 - y1) / 2.5F;

        Matrix4f uiMatrix = new Matrix4f();

        uiMatrix.scale(cellScale, -cellScale, -cellScale);
        uiMatrix.rotateX(MathUtils.PI / 8F);
        uiMatrix.rotateY(angle);

        this.applyTransforms(uiMatrix, transition);

        stack.push();

        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.scale(scale, scale, scale);

        boolean additive = this.form.additiveColor.get();

        this.renderModel(this.entity, stack, model,
            LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV,
            contextColor, formColor, additive, true, null, transition, null);

        /* Attached body parts. They hang off the bone matrices renderModel just captured and
         * renderBodyParts clears that cache when it is done, so they draw here, inside the same
         * push, right after the model — not from render(), which this preview path never enters.
         *
         * The normal matrix is flipped exactly as renderModel's `ui` branch flips its own: the UI
         * framing scales Y negative, and a nested form drawn without the flip shades inside-out.
         * The parts run through the ordinary FormRenderingContext, marked inUI, so every form type
         * that can hang on a bone reaches its normal render3D. */
        stack.push();
        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        this.renderBodyParts(new FormRenderingContext()
            .set(FormRenderType.ENTITY, this.entity, stack, LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, transition)
            .inUI());

        stack.pop();

        stack.pop();
    }

    private void renderModel(IEntity target, MatrixStack stack, ModelInstance model, int light, int overlay, Color contextColor, Color formColor, boolean additive, boolean ui, StencilMap stencilMap, float transition, MatrixStack world)
    {
        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;

        Color finalColor = contextColor.copy();
        FormColorBlend.BlendMode blendMode = additive ? FormColorBlend.BlendMode.BRIGHTEN : FormColorBlend.BlendMode.MULTIPLY;
        FormColorBlend.blend(finalColor, formColor, blendMode);

        /* The GL state this method used to set around the draw is encoded per-pipeline now: blend and
         * the lightmap/overlay samplers by the model RenderLayer, and model.culling by the choice
         * between the culled and non-culled layer variant, made where the draw happens
         * (ModelInstance.render / BOBJModelVAO.render). */

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

        this.applyIKOnce(model, baseTransform);
        this.applyPhysicsOnce(target, model, transition, baseTransform);
        this.applyConstraintsOnce(model);

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

        model.render(newStack, finalColor, light, overlay, stencilMap, this.form.shapeKeys.get(), (material) ->
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

        if (stencilMap == null && !this.renderingArm && this.form != null && this.form.ik.get() instanceof MapType ikMap)
        {
            ModelIKDebug.render(newStack, model.model, ikMap, "");
        }

        /* Same debug-layer path the IK overlay above already rides (the 1.21.1 lightmap/overlay/
         * blend/cull teardown is pipeline-encoded now, nothing to tear down). */
        if (stencilMap == null && !this.renderingArm && this.form != null && this.form.physics.get() instanceof MapType physicsMap)
        {
            ModelPhysicsDebug.render(newStack, model.model, physicsMap, target.getAge(), "");
        }

        /* Render items */
        this.captureMatrices(model);

        if (stencilMap == null)
        {
            this.renderItems(target, model, stack, EquipmentSlot.MAINHAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, model.getItemsMain(), finalColor, overlay, light);
            this.renderItems(target, model, stack, EquipmentSlot.OFFHAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, model.getItemsOff(), finalColor, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.getArmorSlots().entrySet())
            {
                this.renderArmor(target, stack, entry.getKey(), entry.getValue(), finalColor, overlay, light);
            }
        }
    }

    private void applyIKOnce(ModelInstance model, Matrix4f baseTransform)
    {
        if (this.ikAppliedThisRender)
        {
            return;
        }

        this.ikAppliedThisRender = true;
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

    private void applyPhysicsOnce(IEntity target, ModelInstance model, float transition, Matrix4f baseTransform)
    {
        if (this.physicsAppliedThisRender)
        {
            return;
        }

        this.physicsAppliedThisRender = true;
        model.form = this.form;
        ModelPhysicsRuntime.apply(target, model, transition, baseTransform);
    }

    private void applyConstraintsOnce(ModelInstance model)
    {
        if (this.constraintsAppliedThisRender)
        {
            return;
        }

        this.constraintsAppliedThisRender = true;
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

            /* TODO(1.21.11 render): blend/depth state is now pipeline-encoded; hijack hook left as a no-op. */
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});

            /* Translucent armor layers ride the deferred sorted pass (see
             * CustomVertexConsumerProvider#draw(RenderLayer)); only reached outside picking. */
            Vector3f armorOrigin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

            FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(armorOrigin));

            ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers, target, type.slot, type, light);
            consumers.draw();
            FormTranslucentQueue.setSortOrigin(null);

            CustomVertexConsumerProvider.clearRunnables();

            stack.pop();
        }
    }

    private void renderItems(IEntity target, ModelInstance model, MatrixStack stack, EquipmentSlot slot, ItemDisplayContext mode, List<ArmorSlot> items, Color color, int overlay, int light)
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

                /* TODO(1.21.11 render): blend state now pipeline-encoded; hijack hook left as a no-op. */
                CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});

                /* Translucent item layers (potions, glass blocks in hand) ride the deferred
                 * sorted pass; only reached outside picking. */
                Vector3f itemOrigin = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

                FormTranslucentQueue.setSortOrigin(new Matrix4f(RenderSystem.getModelViewMatrix()).transformPosition(itemOrigin));

                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                /* The 1.21.1 entity-flavored ItemRenderer.renderItem overload is gone; the shared replay in
                 * ItemFormRenderer resolves the stack through the new item-model system and replays BOTH
                 * vanilla item layers and BBS special-model custom commands into these consumers.
                 * (Left-handedness rides inside the display context now. The 1.21.1-era 0-size OAK_BUTTON
                 * Sodium workaround for BOBJ models is not restored — revisit if BOBJ held items misbehave.) */
                ItemFormRenderer.renderItem(itemStack, mode, stack, consumers, target.getWorld(), light, overlay, holder);
                consumers.draw();
                consumers.setSubstitute(null);
                FormTranslucentQueue.setSortOrigin(null);

                CustomVertexConsumerProvider.clearRunnables();

                stack.pop();
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

            BBSModClient.getTextures().bindTexture(texture);

            /* TODO(1.21.11 render): depth-test/blend now pipeline-encoded. */

            boolean additive = this.form.additiveColor.get();

            this.renderingArm = true;

            /* Vanilla's renderArm zeroes the arm's pitch: the first person arm
             * is never bent by the use poses, they belong to the third person. */
            ItemUsePose.setSuppressed(true);

            try
            {
                this.renderModel(this.entity, matrices, model, light, OverlayTexture.DEFAULT_UV, contextColor, formColor, additive, false, null, 0F, null);
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
            boolean additive = this.form.additiveColor.get();

            if (context.isPicking())
            {
                contextColor.mul(formColor);
                formColor = Color.white();
                additive = false;
            }
            this.evaluateChannels(context.entity, model, context.getTransition());

            context.stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            if (context.world != null)
            {
                context.world.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
            }

            BBSModClient.getTextures().bindTexture(texture);

            if (ModelPreviewRenderer.ACTIVE)
            {
                /* In-panel 3D preview: route cubic geometry through a vanilla entity layer keyed on this
                 * model's texture (adopted zero-copy into the vanilla TextureManager). */
                ModelPreviewRenderer.TEXTURE = AdoptedTexture.identifier(BBSModClient.getTextures().getTexture(texture));
            }

            if (context.isPicking())
            {
                /* Record the Target picking index into the BBSPicker UBO; ModelInstance.render then issues
                 * the picker_models draw itself. */
                this.setupTarget(context);

                /* picker_models samples Sampler0 for the alpha cutout. Bridge the bound (raw-GL) model texture
                 * into a vanilla GpuTextureView via AdoptedTexture so BBSPickerRenderer can bind it. */
                Texture tex = BBSModClient.getTextures().getTexture(texture);
                net.minecraft.util.Identifier adopted = AdoptedTexture.identifier(tex);

                if (adopted != null)
                {
                    net.minecraft.client.texture.AbstractTexture at = MinecraftClient.getInstance().getTextureManager().getTexture(adopted);

                    BBSPickerRenderer.setSampler0(at.getGlTextureView(), at.getSampler());
                }
            }

            /* TODO(1.21.11 render): 1.21.1 degraded a translucent-texture model to the vanilla cutout
             * program under Iris and suspended the deferred queue for that draw, so the model drew in the
             * phase its program was meant for rather than in an end-of-frame replay that a deferred pack
             * has already composited past. Both the program swap
             * (GameRenderer::getRenderTypeEntityCutoutProgram) and that queue interaction are gone on this
             * branch — the world span now mirrors the vanilla entity pipeline instead — so the dance is
             * dropped. Re-add via FormTranslucentQueue.suspend()/restore() if a deferred pack ever eats a
             * translucent model again. */
            this.renderModel(context.entity, context.stack, model, context.light, context.overlay, contextColor, formColor, additive, false, context.stencilMap, context.getTransition(), context.world);
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

        if (this.form != null && this.form.ik.get() instanceof MapType ikMap)
        {
            ModelIKDebug.renderStencil(context.stack, model.model, ikMap, context.stencilMap, this.form);
        }

        if (this.form != null && this.form.physics.get() instanceof MapType physicsMap)
        {
            ModelPhysicsDebug.renderStencil(context.stack, model.model, physicsMap, context.stencilMap, this.form);
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
