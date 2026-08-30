package mchorse.bbs_mod.ui.forms.editors.utils;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer implements GizmoViewport
{
    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    private IEntity target;
    private Supplier<Boolean> renderForm;

    public UIPickableFormRenderer(UIFormEditor formEditor)
    {
        this.formEditor = formEditor;
    }

    public void updatable()
    {
        this.update = true;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void setRenderForm(Supplier<Boolean> renderForm)
    {
        this.renderForm = renderForm;
    }

    public IEntity getTargetEntity()
    {
        return this.target == null ? this.entity : this.target;
    }

    public void setTarget(IEntity target)
    {
        this.target = target;
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_form"));
        this.stencil.resizeGUI(this.area.w, this.area.h);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.formEditor.clickViewport(context, this.stencil))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.gizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    public GizmoInteraction getGizmoInteraction()
    {
        return this.gizmo;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.camera.projection;
    }

    @Override
    public Area getGizmoArea()
    {
        return this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        return this.formEditor.startGizmo(context, stencilIndex);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.formEditor.pickFormFromRenderer(new Pair<>(form, bone));
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        this.formEditor.preFormRender(context, this.form);

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, context.batcher.getContext().getMatrices(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        if (this.renderForm == null || this.renderForm.get())
        {
            /* The form editor viewport gets the same deferred translucency as the world: the
             * form's semi-transparent pixels draw after all its opaque ones, sorted, without
             * hiding bones behind them. */
            FormTranslucentQueue.begin();
            FormUtilsClient.render(this.form, formContext);
            FormTranslucentQueue.flush();

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        /* Keep the gizmo the same on-screen size as in the film preview (see
         * Gizmo#setViewportScale); set before both the visual (renderAxes) and the
         * stencil pass below so the drawn handles and their pick hitbox match. */
        Gizmo.INSTANCE.setViewportScale(context.menu.height / (float) this.area.h);

        this.renderAxes(context);

        if (this.area.isInside(context))
        {
            GlStateManager._disableScissorTest();

            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
            MatrixStack stack = context.render.batcher.getContext().getMatrices();

            stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
            }

            /* Reorient the pick stencil into the active space to match the visual
             * (below), so hovering a ring lands where it's drawn. */
            Gizmo.INSTANCE.reorientForSpace(stack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());

            /* Skip the gizmo's pick stencil while the hide-gizmo key is held, so its handles can't be
             * clicked when hidden. Form-part picking (the stencil rendered above) is left intact, and
             * the F8 axes toggle is untouched here on purpose. */
            if (!UIBaseMenu.isHideGizmoHeld())
            {
                Gizmo.INSTANCE.renderStencil(context.batcher.getContext().getMatrices());
            }

            stack.pop();

            this.stencil.pickGUI(context, this.area, BBSSettings.gizmoHoverTolerance.get(), Gizmo.STENCIL_MAX);
            this.stencil.unbind(this.stencilMap);

            MinecraftClient.getInstance().getFramebuffer().beginWrite(true);

            GlStateManager._enableScissorTest();
        }
        else
        {
            this.stencil.clearPicking();
        }

        this.gizmo.update(context);
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        MatrixStack stack = context.render.batcher.getContext().getMatrices();

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Reorient the drawn gizmo into the active space (the preview's own scene
         * axes for GLOBAL, screen axes for VIEW); LOCAL leaves it on the bone's
         * own axes. Kept in lockstep with the pick stencil above. The scene axes
         * are the renderer's transform ({@link UIModelRenderer#getSceneAxes}):
         * identity in a plain preview, the model block's own rotation when the
         * block is edited immersively — GLOBAL must follow the container the
         * form is drawn inside, or it points off the scene the user sees. */
        Gizmo.INSTANCE.reorientForSpace(stack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());

        /* Draw axes */
        if (UIBaseMenu.shouldRenderAxes())
        {
            RenderSystem.disableDepthTest();
            Gizmo.INSTANCE.render(stack);
            RenderSystem.enableDepthTest();
        }

        stack.pop();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(context.batcher.getContext().getMatrices(), -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(context.batcher.getContext().getMatrices(), -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.update && this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.gizmo.renderSphereHighlight(context);
        this.gizmo.renderReadout(context);

        if (!this.stencil.hasPicked())
        {
            /* An armed eyedropper over empty space explains itself at the cursor;
             * over a bone the regular pick card below already names the catch. */
            if (this.formEditor.isBonePicking() && this.area.isInside(context))
            {
                context.batcher.textCard(UIKeys.BONE_PICKER_CLICK_BONE.get(), context.mouseX + 12, context.mouseY + 8);
            }

            return;
        }

        Pair<Form, String> pair = this.stencil.getPicked();

        this.stencil.renderPreview(context, this.area);

        if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        if (this.renderForm == null || this.renderForm.get())
        {
            super.renderGrid(context);
        }
    }
}
