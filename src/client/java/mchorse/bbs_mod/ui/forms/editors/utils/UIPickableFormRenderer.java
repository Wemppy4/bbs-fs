package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
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
import mchorse.bbs_mod.utils.colors.Colors;
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

        /* 1.21.11 render: seed the per-vertex MatrixStack with the camera model-view (createCameraStack)
         * so the cubic geometry lands in view space; the global model-view stays identity + perspective
         * projection is set in ModelPreviewRenderer.begin. */
        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, this.createCameraStack(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        if (this.renderForm == null || this.renderForm.get())
        {
            FormUtilsClient.render(this.form, formContext);

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        this.renderAxes(context);

        /* Picking pass: render the form a second time with the picker shaders into the off-screen
         * StencilFormFramebuffer — each bone of a model form gets a unique index colour (Target + per-vertex
         * sub-index) — then read back the pixel under the cursor to resolve the hovered form/bone. The picker
         * draw is driven by BBSPickerRenderer through an explicit render pass targeting the stencil colour/depth
         * (set in stencil.apply), so it lands in the readable stencil texture rather than the preview FBO.
         * Hover-gated so the second render only runs when the cursor is over the viewport. */
        if (this.area.isInside(context))
        {
            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            /* TODO(1.21.11 render): gizmo handle stencil (Gizmo.INSTANCE.renderStencil) is still stubbed —
             * handles own indices 0..STENCIL_MAX and simply won't be hit until ported; form/bone picking
             * (indices past STENCIL_MAX) is unaffected. */

            this.stencil.pickGUI(context, this.area);
            this.stencil.unbind(this.stencilMap);
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
        /* TODO(1.21.11 render): getMatrices() is now 2D; axes/gizmo render in 3D, so build
         * a MatrixStack from the origin instead (the depth state is encoded by the layer). */
        MatrixStack stack = new MatrixStack();

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Draw axes */
        if (UIBaseMenu.renderAxes)
        {
            /* TODO(1.21.11 render): RenderSystem.disable/enableDepthTest removed; depth state
             * is now part of the RenderPipeline used by the gizmo render layer. */
            Gizmo.INSTANCE.render(stack);
        }

        stack.pop();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* TODO(1.21.11 render): getMatrices() is now a 2D Matrix3x2fStack; hitbox boxes render
         * in 3D world space, so draw against a fresh MatrixStack at the model origin. */
        MatrixStack stack = new MatrixStack();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(stack, -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(stack, -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
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

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        /* TODO(1.21.11 render): the picker-preview highlight overlay depends on the new
         * uniform-upload path. RenderPipeline.getUniform("Target"/"HighlightColor")/GlUniform.set
         * are gone (uniforms are UBO/DynamicUniform entries now), and Batcher2D.texturedBox is a
         * no-op stub. Re-enable once the picker-preview pipeline + uniform upload are ported.
         * Original intent: set Target=index, HighlightColor=stencilHighlightColor, then draw the
         * stencil texture through the picker-preview program over the viewport. */
        int color = BBSSettings.stencilHighlightColor.get();

        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, this.area.x, this.area.y, this.area.w, this.area.h, 0, h, w, 0, w, h);

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
