package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.api.client.render.RenderAttachment;
import java.util.IdentityHashMap;
import java.util.Map;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.cubic.IBoneHierarchy;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.joml.Vector3f;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FormRenderer <T extends Form>
{
    protected T form;
    private Map<RenderAttachment<?>, Object> attachments;

    @SuppressWarnings("unchecked")
    public <V> V getAttachment(RenderAttachment<V> key)
    {
        return this.attachments == null ? null : (V) this.attachments.get(key);
    }

    public <V> void setAttachment(RenderAttachment<V> key, V value)
    {
        if (value == null)
        {
            if (this.attachments != null) this.attachments.remove(key);
        }
        else
        {
            if (this.attachments == null) this.attachments = new IdentityHashMap<>();
            this.attachments.put(key, value);
        }
    }

    public FormRenderer(T form)
    {
        this.form = form;
    }

    public T getForm()
    {
        return this.form;
    }

    public List<String> getBones()
    {
        return Collections.emptyList();
    }

    /**
     * The shape of this form's skeleton, or null when it has none. The one question the bone
     * widgets ask a form - the tree list, the pose editor's bone column, the bone picker menus -
     * so they no longer have to know whether they are looking at a cubic model, a BOBJ armature or
     * a vanilla entity model.
     */
    public IBoneHierarchy getBoneHierarchy()
    {
        return null;
    }

    public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.renderInUI(context, x1, y1, x2, y2);

        FontRenderer font = context.batcher.getFont();
        String name = this.form.name.get();

        if (!name.isEmpty())
        {
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y1 + 6, Colors.WHITE, Colors.ACTIVE | Colors.A50);
        }

        int keybind = this.form.hotkey.get();

        if (keybind > 0)
        {
            name = KeyCodes.getName(keybind);
            name = font.limitToWidth(name, x2 - x1 - 3);

            int w = font.getWidth(name);

            context.batcher.textCard(name, (x2 + x1 - w) / 2, y2 - 6 - font.getHeight(), Colors.WHITE, Colors.A50);
        }
    }

    /**
     * The form alone, without the name and hotkey cards {@link #renderUI} lays over it — for a
     * host that draws its own captions around the picture.
     */
    public final void renderPreview(UIContext context, int x1, int y1, int x2, int y2)
    {
        this.renderInUI(context, x1, y1, x2, y2);
    }

    protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

    /** Geometry in this form's local render3D coordinates, before the form transform. */
    public mchorse.bbs_mod.utils.AABB getPreviewBounds()
    {
        return null;
    }

    public boolean isPreviewCameraFacing()
    {
        return false;
    }

    protected Matrix4f getPreviewMatrix(UIContext context, int x1, int y1, int x2, int y2)
    {
        Matrix4f stock = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
        Matrix4f fitted = mchorse.bbs_mod.forms.renderers.utils.FormPreviewFit.frame(
            this, stock, x1, y1, x2, y2, context.getTransition());
        return fitted == null ? stock : fitted;
    }

    private IEntity previewEntity;

    protected void renderPreviewBodyParts(UIContext context, MatrixStack stack)
    {
        if (this.form.parts.getAllTyped().isEmpty()) return;
        if (this.previewEntity == null) this.previewEntity = new mchorse.bbs_mod.forms.entities.StubEntity();
        this.renderBodyParts(new FormRenderingContext().set(FormRenderType.ENTITY, this.previewEntity, stack,
            LightmapTextureManager.MAX_LIGHT_COORDINATE, net.minecraft.client.render.OverlayTexture.DEFAULT_UV,
            context.getTransition()).inUI());
    }

    public boolean renderArm(MatrixStack matrices, int light, AbstractClientPlayerEntity player, Hand hand)
    {
        return false;
    }

    public final void render(FormRenderingContext context)
    {
        if (!this.form.shaderShadow.get() && BBSRendering.isIrisShadowPass())
        {
            return;
        }

        BBSProfiler.count(BBSProfiler.Section.FORM_RENDER);

        RepeatedFormRender repeated = RepeatedFormRender.current();
        if (repeated == null) this.form.applyStates(context.transition);
        else repeated.apply(this.form, context.transition);

        int light = context.light;
        boolean visible = this.form.visible.get();
        boolean isPicking = context.isPicking();

        if (!visible || (isPicking && !this.form.pickable.get()))
        {
            if (repeated == null) this.form.unapplyStates();

            return;
        }

        context.stack.push();
        if (context.world != null)
        {
            context.world.push();
        }
        this.applyTransforms(context.stack, false, context.getTransition());
        if (context.world != null)
        {
            this.applyTransforms(context.world, false, context.getTransition());
        }

        float lf = 1F - MathUtils.clamp(this.form.lighting.get(), 0F, 1F);
        int u = context.light & '\uffff';
        int v = context.light >> 16 & '\uffff';

        u = (int) Lerps.lerp(u, LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, lf);
        context.light = u | v << 16;

        this.render3D(context);

        if (isPicking)
        {
            this.updateStencilMap(context);
        }

        this.renderBodyParts(context);

        context.stack.pop();
        if (context.world != null)
        {
            context.world.pop();
        }

        context.light = light;

        if (repeated == null) this.form.unapplyStates();
    }

    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        Transform transform = this.createEvaluatedTransform(transition);

        if (origin)
        {
            stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        }
        else
        {
            /* About the pivot, as createMatrix: translations commute, so the pivot goes first. */
            Vector3f pivot = this.form.pivot.get();

            stack.translate(pivot.x, pivot.y, pivot.z);
            MatrixStackUtils.applyTransform(stack, transform);
            stack.translate(-pivot.x, -pivot.y, -pivot.z);
        }
    }

    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        matrix.mul(this.createMatrix(this.createEvaluatedTransform(transition)));
    }

    /**
     * The matrix of one of this form's transforms as the form is drawn: rotated and scaled about
     * the form's pivot, {@code translate · pivot · rotation · scale · -pivot}. At rest that is the
     * plain translation, so a pivot alone never moves the form.
     */
    public Matrix4f createMatrix(Transform transform)
    {
        Vector3f pivot = this.form.pivot.get();

        return transform.setupMatrix(new Matrix4f().translation(pivot)).translate(-pivot.x, -pivot.y, -pivot.z);
    }

    /**
     * The form's pivot in the units of the frames {@link #collectMatrices} records for it — the
     * form's own, unless a renderer scales its frame after the transform (a model's own scale).
     */
    protected Vector3f getFramePivot()
    {
        return new Vector3f(this.form.pivot.get());
    }

    /**
     * The form's own transform as it is actually rendered: its transform, its overlay and
     * whatever else was hung on it. Public because the film's orbit camera attaches to this
     * frame - what the camera follows has to be what the eye sees, not just where the replay
     * stands.
     */
    /** Saved animation plus overlays and external pose contributions. */
    public Transform createEvaluatedTransform(float transition)
    {
        Transform transform = this.createTransform();
        FormPoseEvents.TRANSFORM.invoker().apply(this.form, transform, transition);
        return transform;
    }

    public Transform createTransform()
    {
        this.form.syncOverlayTracks();
        Transform transform = new Transform();

        transform.copy(this.form.transform.get());
        this.applyTransform(transform, this.form.transformOverlay.get());

        for (ValueTransform t : this.form.additionalTransforms)
        {
            this.applyTransform(transform, t.get());
        }

        return transform;
    }

    private void applyTransform(Transform transform, Transform overlay)
    {
        transform.translate.add(overlay.translate);
        transform.scale.add(overlay.scale).sub(1, 1, 1);
        transform.addRotation(overlay);
    }

    /**
     * Form-local displacement of the form's origin from its rest pose this frame, used to drag the
     * entity's shadow under the form's perceived position. The base form simply reports its own
     * transform's translation (so transform keyframes shift the shadow); subclasses can override to
     * add their own motion (e.g. {@link ModelFormRenderer} folds in anchor-bone root motion). The
     * caller maps the result to world axes with the render target.
     */
    public Vector3f getShadowDisplacement(IEntity entity, float transition)
    {
        MatrixStack stack = new MatrixStack();

        stack.push();
        this.applyTransforms(stack, false, transition);

        Vector3f displacement = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        stack.pop();

        return displacement;
    }

    protected Supplier<ShaderProgram> getShader(FormRenderingContext context, Supplier<ShaderProgram> normal, Supplier<ShaderProgram> picking)
    {
        if (context.isPicking())
        {
            this.setupTarget(context, picking.get());

            return picking;
        }

        return normal;
    }

    protected void setupTarget(FormRenderingContext context, ShaderProgram program)
    {
        GlUniform target = program.getUniform("Target");

        if (target != null)
        {
            int pickingIndex = context.getPickingIndex();

            target.set(pickingIndex);
        }
    }

    protected void updateStencilMap(FormRenderingContext context)
    {
        context.stencilMap.addPicking(this.form);
    }

    protected void render3D(FormRenderingContext context)
    {}

    public void renderBodyParts(FormRenderingContext context)
    {
        for (BodyPart part : this.form.parts.getAllTyped())
        {
            this.renderBodyPart(part, context);
        }
    }

    protected void renderBodyPart(BodyPart part, FormRenderingContext context)
    {
        IEntity oldEntity = context.entity;

        context.entity = part.getRenderEntity(oldEntity);

        if (part.getForm() != null)
        {
            context.stack.push();
            if (context.world != null)
            {
                context.world.push();
            }
            MatrixStackUtils.applyTransform(context.stack, part.transform.get());
            if (context.world != null)
            {
                MatrixStackUtils.applyTransform(context.world, part.transform.get());
            }

            FormUtilsClient.render(part.getForm(), context);

            context.stack.pop();
            if (context.world != null)
            {
                context.world.pop();
            }
        }

        context.entity = oldEntity;
    }

    public MatrixCache collectMatrices(IEntity entity, float transition)
    {
        BBSProfiler.count(BBSProfiler.Section.COLLECT_MATRICES);

        MatrixCache map = new MatrixCache();
        MatrixStack stack = new MatrixStack();

        this.collectMatrices(entity, stack, map, "", transition);

        return map;
    }

    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
        FormPoseEvents.PARENT_FRAME.invoker().capture(this.form, entity, stack.peek().getPositionMatrix(), prefix, transition);

        Matrix4f mm = new Matrix4f();
        Matrix4f oo = new Matrix4f();

        stack.push();
        this.applyTransforms(stack, true, transition);
        oo.set(stack.peek().getPositionMatrix());
        stack.pop();

        stack.push();
        this.applyTransforms(stack, false, transition);
        mm.set(stack.peek().getPositionMatrix());

        matrices.putForm(prefix, mm, oo, this.getFramePivot());

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.push();
                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, part.getId()), transition);

                stack.pop();
            }
        }

        stack.pop();
    }
}
