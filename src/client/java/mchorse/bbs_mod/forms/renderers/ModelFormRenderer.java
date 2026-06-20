package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.client.renderer.entity.ActorEntityRenderer;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.Animator;
import mchorse.bbs_mod.cubic.animation.IAnimator;
import mchorse.bbs_mod.cubic.animation.ProceduralAnimator;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.ik.ModelIKDebug;
import mchorse.bbs_mod.cubic.ik.ModelIKRuntime;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.physics.ModelPhysicsRuntime;
import mchorse.bbs_mod.cubic.model.ArmorSlot;
import mchorse.bbs_mod.cubic.model.ArmorType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
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
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private boolean ikAppliedThisRender;
    private boolean physicsAppliedThisRender;
    private boolean constraintsAppliedThisRender;
    private final Map<String, Float> poseFixByBone = new HashMap<>();

    private IEntity entity = new StubEntity();

    @Override
    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        super.applyTransforms(stack, origin, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            stack.scale(model.scale.x, model.scale.y, model.scale.z);
        }
    }

    @Override
    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        super.applyTransforms(matrix, transition);

        ModelInstance model = this.getModel();

        if (model != null)
        {
            matrix.scale(model.scale.x, model.scale.y, model.scale.z);
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

    /**
     * The cell-relative part of {@link #getUIMatrix} for the special-element FBO preview path, shared by every
     * 3D form type's {@link #renderUIPreview}. The base {@code BbsFormGuiElementRenderer} already pre-translated
     * the stack to the cell (centre, {@code 0.85*height} down) and applied {@code scale(f, f, -f)}, so only the
     * cell scale + 22.5° forward tilt + cursor-driven yaw remain. The original {@link #getUIMatrix} used
     * {@code scale(s, -s, s)}; the base's extra {@code -Z} means we flip Z here to net the same handedness.
     */
    public static Matrix4f getUIPreviewMatrix(float angle, int y1, int y2)
    {
        float cellScale = (y2 - y1) / 2.5F;

        Matrix4f uiMatrix = new Matrix4f();

        uiMatrix.scale(cellScale, -cellScale, -cellScale);
        uiMatrix.rotateX(MathUtils.PI / 8F);
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

            if (value.fix != 0)
            {
                poseTransform.translate.lerp(value.translate, value.fix);
                poseTransform.scale.lerp(value.scale, value.fix);
                poseTransform.rotate.lerp(value.rotate, value.fix);
                poseTransform.rotate2.lerp(value.rotate2, value.fix);
            }
            else
            {
                poseTransform.translate.add(value.translate);
                poseTransform.scale.add(value.scale).sub(1, 1, 1);
                poseTransform.rotate.add(value.rotate);
                poseTransform.rotate2.add(value.rotate2);
            }
        }
    }

    public void resetAnimator()
    {
        this.animator = null;
        this.lastModel = null;
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

        this.animator = model.procedural ? new ProceduralAnimator() : new Animator();
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
        bones.removeIf(model.disabledBones::contains);

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
        Link texture = link == null ? model.texture : link;
        Color contextColor = Color.white();
        Color formColor = this.form.color.get();
        float scale = this.form.uiScale.get() * model.uiScale;

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

        this.renderModel(this.entity, this.getMainShader(model), stack, model,
            LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV,
            contextColor, formColor, additive, true, null, transition, null);

        stack.pop();
    }

    /**
     * TODO(1.21.11 render): the model shader path moved to RenderPipeline/RenderLayer.
     * {@link GameRenderer}'s getXxxProgram() accessors were removed and {@link BBSShaders#getModel()}
     * now returns a {@link com.mojang.blaze3d.pipeline.RenderPipeline}. {@link ModelInstance#render}
     * still consumes a {@code Supplier<ShaderProgram>}, so until the model render path is rebuilt on the
     * new pipeline this returns a null shader (rendering is a no-op). The old selection was:
     * {@code ((isIrisShadersEnabled() && isRenderingWorld()) || !model.isVAORendered())} ->
     * entity-translucent-cull program, else BBSShaders::getModel.
     */
    private Supplier<ShaderProgram> getMainShader(ModelInstance model)
    {
        return () -> null;
    }

    private void renderModel(IEntity target, Supplier<ShaderProgram> program, MatrixStack stack, ModelInstance model, int light, int overlay, Color contextColor, Color formColor, boolean additive, boolean ui, StencilMap stencilMap, float transition, MatrixStack world)
    {
        this.ikAppliedThisRender = false;
        this.physicsAppliedThisRender = false;
        this.constraintsAppliedThisRender = false;

        Color finalColor = contextColor.copy();
        FormColorBlend.BlendMode blendMode = additive ? FormColorBlend.BlendMode.BRIGHTEN : FormColorBlend.BlendMode.MULTIPLY;
        FormColorBlend.blend(finalColor, formColor, blendMode);

        /* TODO(1.21.11 render): cull/blend state and lightmap/overlay binding are now encoded in the
         * RenderPipeline (model.culling toggled cull; blend was default-func enabled; lightmap+overlay
         * were enabled here). Re-apply on the new pipeline foundation. */

        MatrixStack newStack = new MatrixStack();

        MatrixStackUtils.multiply(newStack, stack.peek().getPositionMatrix());
        newStack.peek().getNormalMatrix().set(stack.peek().getNormalMatrix());

        if (ui)
        {
            newStack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
            newStack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);
        }

        Matrix4f baseTransform = ui ? null : new Matrix4f((world != null ? world : stack).peek().getPositionMatrix());

        this.collectPoseFixByBone();
        this.applyIKOnce(model, baseTransform);
        this.applyPhysicsOnce(target, model, transition, baseTransform);
        this.applyConstraintsOnce(model);
        model.render(newStack, program, finalColor, light, overlay, stencilMap, this.form.shapeKeys.get());

        if (stencilMap == null && ModelIKDebug.enabled && this.form != null && this.form.ik.get() instanceof MapType ikMap)
        {
            ModelIKDebug.render(newStack, model.model, ikMap, "");
        }

        /* TODO(1.21.11 render): teardown of lightmap/overlay/blend/cull was here; now pipeline-encoded. */

        /* Render items */
        this.captureMatrices(model);

        if (stencilMap == null)
        {
            this.renderItems(target, model, stack, EquipmentSlot.MAINHAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, model.itemsMain, finalColor, overlay, light);
            this.renderItems(target, model, stack, EquipmentSlot.OFFHAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, model.itemsOff, finalColor, overlay, light);

            for (Map.Entry<ArmorType, ArmorSlot> entry : model.armorSlots.entrySet())
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
        if (baseTransform == null || this.form == null || this.form.ikTargetOverrides.isEmpty())
        {
            ModelIKRuntime.applyWithPoseFix(model, this.poseFixByBone);
            return;
        }

        Matrix4f inv = new Matrix4f(baseTransform).invert();
        Map<String, Vector3f> local = new HashMap<>(this.form.ikTargetOverrides.size() * 2);

        for (Map.Entry<String, Vector3f> entry : this.form.ikTargetOverrides.entrySet())
        {
            String controller = entry.getKey();
            Vector3f worldPos = entry.getValue();

            if (controller == null || controller.isEmpty() || worldPos == null)
            {
                continue;
            }

            Vector3f pos = new Vector3f(worldPos);
            inv.transformPosition(pos);
            local.put(controller, pos);
        }

        if (local.isEmpty())
        {
            ModelIKRuntime.applyWithPoseFix(model, this.poseFixByBone);
            return;
        }

        ModelIKRuntime.apply(model, local, this.poseFixByBone);
    }

    private void applyPhysicsOnce(IEntity target, ModelInstance model, float transition, Matrix4f baseTransform)
    {
        if (this.physicsAppliedThisRender)
        {
            return;
        }

        this.physicsAppliedThisRender = true;
        model.lastBaseTransform = baseTransform;
        model.form = this.form;
        ModelPhysicsRuntime.apply(target, model, transition, baseTransform, this.poseFixByBone);
    }

    private void collectPoseFixByBone()
    {
        this.poseFixByBone.clear();

        if (this.form == null)
        {
            return;
        }

        Pose pose = this.getPose();

        if (pose == null || pose.transforms.isEmpty())
        {
            return;
        }

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            String bone = entry.getKey();
            PoseTransform transform = entry.getValue();

            if (bone == null || bone.isEmpty() || transform == null)
            {
                continue;
            }

            float fix = MathUtils.clamp(transform.fix, 0F, 1F);

            if (fix > 0F)
            {
                this.poseFixByBone.put(bone, fix);
            }
        }
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

            ActorEntityRenderer.armorRenderer.renderArmorSlot(stack, consumers, target, type.slot, type, light);
            consumers.draw();

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

                consumers.setSubstitute(BBSRendering.getColorConsumer(color));

                /* TODO(1.21.11 render): ItemRenderer.renderItem(entity, stack, mode, leftHanded, matrices,
                 * vcp, world, light, overlay, seed) was removed by the 1.21.4 item-model rewrite. Item
                 * rendering (incl. the 0-size OAK_BUTTON Sodium workaround for the BOBJModel case) needs to
                 * be reimplemented against the new ItemRenderState / SpecialModelRenderer system.
                 * Neutralized for build-only; held items currently do not render. mode/leftHanded was:
                 * mode == ItemDisplayContext.THIRD_PERSON_LEFT_HAND. */
                consumers.draw();
                consumers.setSubstitute(null);

                CustomVertexConsumerProvider.clearRunnables();

                stack.pop();
            }
        }
    }

    @Override
    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        ModelInstance model = this.getModel();

        if (this.animator != null && model != null)
        {
            ArmorSlot slot = hand == Hand.MAIN_HAND ? model.fpMain : model.fpOffhand;

            if (slot == null)
            {
                return false;
            }

            Link link = this.form.texture.get();
            Link texture = link == null ? model.texture : link;
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

            Supplier<ShaderProgram> mainShader = this.getMainShader(model);

            /* TODO(1.21.11 render): depth-test/blend now pipeline-encoded. */

            boolean additive = this.form.additiveColor.get();
            this.renderModel(this.entity, mainShader, matrices, model, light, OverlayTexture.DEFAULT_UV, contextColor, formColor, additive, false, null, 0F, null);

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
            Link texture = link == null ? model.texture : link;
            Color contextColor = new Color().set(context.color, true);
            Color formColor = this.form.color.get();
            boolean additive = this.form.additiveColor.get();

            if (context.isPicking())
            {
                contextColor.mul(formColor);
                formColor = Color.white();
                additive = false;
            }
            model.model.resetPose();

            this.animator.applyActions(context.entity, model, context.getTransition());
            model.model.applyPose(this.getPose());

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

            Supplier<ShaderProgram> mainShader = this.getMainShader(model);
            /* getShader records the Target picking index (setupTarget -> BBSPickerRenderer.setTarget) when
             * picking; ModelInstance.render then issues the picker_models draw itself. The legacy
             * Supplier<ShaderProgram> is unused by that pipeline path (picker programs are RenderPipelines). */
            Supplier<ShaderProgram> shader = this.getShader(context, mainShader, () -> null);

            if (context.isPicking())
            {
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

            this.renderModel(context.entity, shader, context.stack, model, context.light, context.overlay, contextColor, formColor, additive, false, context.stencilMap, context.getTransition(), context.world);
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

        if (ModelIKDebug.enabled && this.form != null && this.form.ik.get() instanceof mchorse.bbs_mod.data.types.MapType ikMap)
        {
            ModelIKDebug.renderStencil(context.stack, model.model, ikMap, context.stencilMap, this.form);
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
            model.model.resetPose();

            this.animator.applyActions(entity, model, transition);
            model.model.applyPose(this.getPose());

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

            matrices.put(StringUtils.combinePaths(prefix, entry.getKey()), matrix, o);
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

                FormUtilsClient.getRenderer(form).collectMatrices(part.useTarget.get() ? entity : part.getEntity(), stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();

        this.bones.clear();
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
