package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.client.render.special.BbsFormGuiElementRenderState;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public abstract class FormRenderer <T extends Form>
{
    protected T form;

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

    public final void renderUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* The diffuse-light directions the old setupLevelDiffuseLighting bound here (lightA=(0,1,-0.2),
         * lightB=(-0.85,0.85,1)) are now bound per model-form thumbnail in BbsFormGuiElementRenderer.lights():
         * the UI form preview renders off-screen during the GUI prepare phase (two-phase GUI), so the lights
         * must be set there at draw time, not here in the record phase. Other form types render flat 2D and
         * need no diffuse lighting. */

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

    protected abstract void renderInUI(UIContext context, int x1, int y1, int x2, int y2);

    /**
     * Submit this form as a vanilla special GUI element so it renders off-screen during the GUI prepare phase
     * and the deferred GUI composites it into the list/icon cell. The list draws each cell in the GUI record
     * phase, where a direct immediate 3D draw can't composite (two-phase GUI, 1.21.6+), so 3D form types route
     * their {@code renderInUI} through here — the same mechanism vanilla uses for entity/item thumbnails.
     * {@link BbsFormGuiElementRenderer} then calls back into {@link #renderUIPreview} inside the FBO render pass.
     * Form-type-agnostic: every renderer passes {@code this}; the cursor-driven yaw, the live 2D GUI matrix
     * (carries the list's scroll translate) and the active scissor are captured here, exactly like the original
     * which rendered onto {@code context.batcher.getContext().getMatrices()} bracketed by the GL scissor.
     */
    protected final void submitUIPreview(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        float angle = MathUtils.toRad(context.mouseX - (x1 + x2) / 2) + MathUtils.PI;

        if (BBSSettings.freezeModels.get())
        {
            angle = -MathUtils.PI + MathUtils.PI / 8;
        }

        DrawContext dc = context.batcher.getContext();
        Matrix3x2f pose = new Matrix3x2f(dc.getMatrices());
        ScreenRect scissor = dc.scissorStack.peekLast();

        dc.state.addSpecialElement(new BbsFormGuiElementRenderState(
            this, angle, context.getTransition(), pose, x1, y1, x2, y2, 1.0F, scissor));
    }

    /**
     * Render this form into the special-element off-screen FBO bound by {@link BbsFormGuiElementRenderer} for a
     * form-list thumbnail. The base renderer has set an ORTHOGRAPHIC projection and pre-translated {@code stack}
     * to the cell (origin at the horizontal centre, {@code 0.85*height} down) then {@code scale(f, f, -f)}; an
     * override applies the rest of the {@link #getUIPreviewMatrix} framing and draws. Default is
     * a no-op: form types that still render their UI preview the old (2D/immediate) way don't submit a special
     * element, so this is never called for them. The caller manages {@code ModelPreviewRenderer.ACTIVE} +
     * diffuse lighting + restore.
     */
    public void renderUIPreview(MatrixStack stack, float angle, float transition, int x1, int y1, int x2, int y2)
    {}

    /**
     * The cell-relative framing for the special-element FBO preview path, shared by every 3D form type's
     * {@link #renderUIPreview}. The base {@code BbsFormGuiElementRenderer} already pre-translated the stack to the
     * cell (centre, {@code 0.85*height} down) and applied {@code scale(f, f, -f)}, so only the cell scale + 22.5°
     * forward tilt + cursor-driven yaw remain. The original {@code ModelFormRenderer.getUIMatrix} used
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

        this.form.applyStates(context.transition);

        int light = context.light;
        boolean visible = this.form.visible.get();

        if (!visible)
        {
            return;
        }

        boolean isPicking = context.stencilMap != null;

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

        this.form.unapplyStates();
    }

    protected void applyTransforms(MatrixStack stack, boolean origin, float transition)
    {
        Transform transform = this.createTransform();

        if (origin)
        {
            stack.translate(transform.translate.x, transform.translate.y, transform.translate.z);
        }
        else
        {
            MatrixStackUtils.applyTransform(stack, transform);
        }
    }

    protected void applyTransforms(Matrix4f matrix, float transition)
    {
        matrix.mul(this.createTransform().createMatrix());
    }

    protected Transform createTransform()
    {
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
        transform.rotate.add(overlay.rotate);
        transform.rotate2.add(overlay.rotate2);
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
        /* 1.21.11 render: the loose `uniform int Target` the picker shaders used is now the BBSPicker
         * std140 UBO, uploaded per picker draw by BBSPickerRenderer. Record the active picking index
         * here — the faithful equivalent of the 1.21.1 program.getUniform("Target").set(getPickingIndex()).
         * The legacy ShaderProgram param is unused (picker programs are RenderPipelines now). */
        BBSPickerRenderer.setTarget(context.getPickingIndex());
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

        context.entity = part.useTarget.get() ? oldEntity : part.getEntity();

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
        MatrixCache map = new MatrixCache();
        MatrixStack stack = new MatrixStack();

        this.collectMatrices(entity, stack, map, "", transition);

        return map;
    }

    public void collectMatrices(IEntity entity, MatrixStack stack, MatrixCache matrices, String prefix, float transition)
    {
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

        int i = 0;

        for (BodyPart part : this.form.parts.getAllTyped())
        {
            Form form = part.getForm();

            if (form != null)
            {
                stack.push();
                MatrixStackUtils.applyTransform(stack, part.transform.get());

                FormUtilsClient.getRenderer(form).collectMatrices(entity, stack, matrices, StringUtils.combinePaths(prefix, String.valueOf(i)), transition);

                stack.pop();
            }

            i += 1;
        }

        stack.pop();
    }
}
