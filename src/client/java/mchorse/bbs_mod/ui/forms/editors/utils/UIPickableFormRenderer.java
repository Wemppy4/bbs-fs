package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.render.picker.BBSPickerRenderer;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
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
import mchorse.bbs_mod.ui.utils.UIUtils;
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

        /* Picking pass: render the form a second time with the picker shaders into the off-screen
         * StencilFormFramebuffer — each bone of a model form gets a unique index colour (Target + per-vertex
         * sub-index) — then read back the pixel under the cursor to resolve the hovered form/bone. The picker
         * draw is driven by BBSPickerRenderer through an explicit render pass targeting the stencil colour/depth
         * (set in stencil.apply), so it lands in the readable stencil texture rather than the preview FBO.
         * Hover-gated so the second render only runs when the cursor is over the viewport. */
        if (this.area.isInside(context))
        {
            /* Alignment fix (1.21.11): size the picking texture to the SAME pixel dimensions as the visible
             * model's preview FBO (viewportW/viewportH, the rx/ry-scaled area) instead of getGUIScale(). The
             * model FBO is sized by setupViewport's rx = round(window.W / menu.W); the stencil's resizeGUI used
             * getGUIScale() instead, which can diverge from rx (custom BBSSettings.userIntefaceScale, or "auto"
             * MC scale where getGuiScale().getValue() != the effective factor). When the two scales differ the
             * picker draws into a differently-sized texture than the model FBO, and the recoloured highlight —
             * blitted over the SAME this.area — no longer lands on the bone. Binding the stencil to the FBO's
             * exact pixel size makes picking texture == model FBO, so the 1:1 recolour aligns. */
            int vpw = Math.max(1, this.viewportW);
            int vph = Math.max(1, this.viewportH);

            this.stencil.resize(vpw, vph);

            /* No viewport to remember here (unlike 1.21.1, which bound the pick framebuffer raw):
             * the picker draws through a 1.21.11 render pass onto BBSPickerRenderer's target, which
             * owns its own viewport and leaves the UI's alone. */
            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            /* Gizmo handle picking: re-draw the gizmo into the stencil AFTER the form so the handle ids overwrite
             * the form/bone ids where they overlap (the handles must win the pick — faithful to the original
             * depth-test-disabled gizmo stencil). This whole block runs inside ModelPreviewRenderer.begin/end, so
             * the perspective preview projection and identity global model-view are still active here — the same
             * frame the visible gizmo (renderAxes, below) draws in, so the pick aligns 1:1 with it. Matrix stack is
             * built exactly as renderAxes does (stripScale of the editor origin).
             * Skip the gizmo's pick stencil while the hide-gizmo key is held (merged 1.21.1 feature), so its
             * handles can't be clicked when hidden; form-part picking (rendered above) stays intact. */
            if (UIBaseMenu.renderAxes && !UIBaseMenu.isHideGizmoHeld())
            {
                Matrix4f gizmoMatrix = this.formEditor.getOrigin(context.getTransition());

                /* 1.21.11: the preview pass sets the perspective projection but leaves the GLOBAL model-view
         * IDENTITY, so every draw has to bake `camera.view * translate(-camera.pos) * transform` into its
         * own vertices — that is exactly what createCameraStack() returns, and what the ground grid and the
         * model geometry already use. Starting from a bare `new MatrixStack()` left this geometry in raw
         * model space: it landed at the camera origin, i.e. nowhere on screen. */
                MatrixStack gizmoStack = this.createCameraStack();

                gizmoStack.push();

                if (gizmoMatrix != null)
                {
                    MatrixStackUtils.multiply(gizmoStack, MatrixStackUtils.stripScale(gizmoMatrix));
                }

                /* Reorient the pick stencil into the active space to match the visual (renderAxes),
                 * so hovering a ring lands where it's drawn. */
                Gizmo.INSTANCE.reorientForSpace(gizmoStack, this.formEditor.getGizmoSpace(), this.camera.view, this.getSceneAxes());
                Gizmo.INSTANCE.renderStencil(gizmoStack, this.stencilMap);
                gizmoStack.pop();
            }

            /* Pick-read with the SAME scale the stencil was sized at: map the cursor (in GUI units, relative to
             * the area, V-flipped for the bottom-up texture) to picking-texture pixels via vpw/area.w & vph/area.h
             * — NOT getGUIScale() — so the sampled texel matches the resized texture. The radius carries the
             * merged 1.21.1 gizmo hover tolerance, converted into that same picking-texture scale. */
            float px = (context.mouseX - this.area.x) / (float) this.area.w * vpw;
            float py = (this.area.h - context.mouseY + this.area.y) / (float) this.area.h * vph;
            int radius = Math.round(BBSSettings.gizmoHoverTolerance.get() * (vpw / (float) Math.max(1, this.area.w)));

            this.stencil.pick((int) px, (int) py, radius, Gizmo.STENCIL_MAX);
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
        /* 1.21.11: the preview pass sets the perspective projection but leaves the GLOBAL model-view
         * IDENTITY, so every draw has to bake `camera.view * translate(-camera.pos) * transform` into its
         * own vertices — that is exactly what createCameraStack() returns, and what the ground grid and the
         * model geometry already use. Starting from a bare `new MatrixStack()` left this geometry in raw
         * model space: it landed at the camera origin, i.e. nowhere on screen. */
        MatrixStack stack = this.createCameraStack();

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

        /* Same camera-baking requirement as the gizmo above: the hitbox boxes are world-space geometry
         * drawn into the preview pass, so they start from the camera stack, not a bare one. */
        MatrixStack stack = this.createCameraStack();

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

        int index = this.stencil.getIndex();
        Pair<Form, String> pair = this.stencil.getPicked();

        /* Highlight the hovered form/bone: recolour the pixels of the picking texture whose encoded index equals
         * the picked index with BBSSettings.stencilHighlightColor (faithful to the 1.21.1 Target/HighlightColor
         * + texturedBox path). The recolour runs in an off-screen pass that carries the custom BBSPicker UBO (it
         * can't ride the immediate RenderLayer/texturedBox path); the result is then blitted back over the
         * viewport through the recorded two-phase-GUI texturedBox path (FBO-style V-flip), so it survives the
         * deferred GUI flush. */
        int color = BBSSettings.stencilHighlightColor.get();

        /* Alignment fix (1.21.11): build the highlight target at the SAME pixel size as the picking texture /
         * visible model FBO (viewportW/viewportH), so the recolour is a 1:1 copy of the picking texture and the
         * blit over this.area lands exactly where the model (also blitted over this.area at the same FBO size)
         * is. The previous getGUIScale()-derived size (area.w*scale) could differ from the FBO's rx/ry scale,
         * offsetting the highlight. */
        int hw = Math.max(1, this.viewportW);
        int hh = Math.max(1, this.viewportH);

        if (BBSPickerRenderer.drawHighlight(this.stencil.getPickColorView(),
            this.stencil.ensureHighlightTarget(this.stencil.getPickWidth(), this.stencil.getPickHeight()),
            this.stencil.getPickWidth(), this.stencil.getPickHeight(), index, color))
        {
            int vw = this.stencil.getHighlightWidth();
            int vh = this.stencil.getHighlightHeight();

            context.batcher.texturedBox(this.stencil.getHighlightGlId(), Colors.WHITE,
                this.area.x, this.area.y, this.area.w, this.area.h, 0, vh, vw, 0, vw, vh);
        }

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
