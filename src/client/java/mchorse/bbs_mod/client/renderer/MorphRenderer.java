package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.selectors.ISelectorOwnerProvider;
import mchorse.bbs_mod.selectors.SelectorOwner;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Morph rendering for the 1.21.11 (render-state) pipeline.
 *
 * <p>1.21.2+ made entity {@code render()} a build phase that only enqueues into the
 * OrderedRenderCommandQueue, so a morph form cannot be drawn there (the BBS immediate form pipeline
 * needs the camera transform supplied through the WorldRenderContext MatrixStack, which only holds
 * once the entity queue is flushed). So {@code LivingEntityRendererMorphMixin} COLLECTS each morph
 * and cancels the vanilla render, then {@link #renderQueued(WorldRenderContext)} draws the collected
 * forms from WorldRenderEvents.AFTER_ENTITIES (via {@code BBSRendering.renderCoolStuff}) — the same
 * proven path as replay/film forms ({@link BaseFilmController#renderEntity}).
 */
public class MorphRenderer
{
    public static boolean hidePlayer = false;

    private static final List<Queued> QUEUE = new ArrayList<>();

    /**
     * Set while vanilla draws an entity INTO THE GUI — the inventory's little player window and
     * everything else built on {@code EntityGuiElementRenderer}.
     *
     * <p>That draw reaches the same {@code LivingEntityRenderer.render} the world does, so a morph is
     * collected there too — but the queue is drained from {@code WorldRenderEvents.AFTER_ENTITIES},
     * which does not fire for the GUI. The entry therefore sat in the queue with the vanilla render
     * already cancelled: the inventory window came out empty, and the stale entry was then drawn by the
     * next world frame, which in first person is the one place the player is not submitted at all — so
     * the morph appeared in front of the camera as well.</p>
     */
    private static boolean guiPass;

    /** See {@link #guiPass}. Set by {@code EntityGuiElementRendererMixin} around that one draw. */
    public static void setGuiPass(boolean pass)
    {
        guiPass = pass;
    }

    /**
     * Collect a player morph for deferred rendering. Returns true to suppress the vanilla render.
     */
    public static boolean collectPlayer(AbstractClientPlayerEntity player, MatrixStack matrices, int light, int overlay, float tickDelta, LivingEntityRenderState state)
    {
        if (hidePlayer)
        {
            if (FormUtilsClient.getCurrentForm() instanceof MobForm form && !form.isPlayer())
            {
                return true;
            }
        }

        Morph morph = Morph.getMorph(player);

        if (morph != null && morph.getForm() != null)
        {
            if (canRender())
            {
                submit(morph.getForm(), morph.entity, matrices, light, overlay, tickDelta, player.deathTime, state);
            }

            return true;
        }

        return false;
    }

    /**
     * Collect a selector-owner (mob) morph for deferred rendering. Returns true to suppress the
     * vanilla render.
     */
    public static boolean collectLivingEntity(LivingEntity livingEntity, MatrixStack matrices, int light, int overlay, float tickDelta, LivingEntityRenderState state)
    {
        if (!(livingEntity instanceof ISelectorOwnerProvider))
        {
            return false;
        }

        SelectorOwner owner = ((ISelectorOwnerProvider) livingEntity).getOwner();

        owner.check();

        Form form = owner.getForm();

        if (form != null)
        {
            submit(form, owner.entity, matrices, light, overlay, tickDelta, livingEntity.deathTime, state);

            return true;
        }

        return false;
    }

    /**
     * Route one collected morph: straight to the shadow map during Iris's shadow pass, into the queue
     * otherwise.
     *
     * <p>The two passes cannot share a path. Iris renders shadows itself, without going through
     * {@code WorldRenderer.render}, so {@code WorldRenderEvents.AFTER_ENTITIES} — where {@link
     * #renderQueued} drains — never fires there. Queueing a shadow-pass morph therefore did two wrong
     * things at once, and they were the two reported bugs:
     *
     * <ul>
     *   <li><b>No shadow.</b> The vanilla render was cancelled and the form was only drawn later, in the
     *       main pass, so nothing at all reached the shadow map.</li>
     *   <li><b>Own body visible in first person.</b> Iris's shadow pass submits the player
     *       unconditionally ({@code ShadowRenderer.extractVisibleEntities}), while vanilla skips the
     *       camera entity in first person. So the shadow pass was the only thing putting the player's
     *       own morph into the queue — and the main pass then drew it in front of the camera. Not
     *       queueing here is what removes the body; nothing else has to change.</li>
     * </ul>
     */
    private static void submit(Form form, IEntity entity, MatrixStack matrices, int light, int overlay, float tickDelta, int deathTime, LivingEntityRenderState state)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            renderShadow(form, entity, matrices, light, overlay, tickDelta, deathTime);
        }
        else if (guiPass)
        {
            renderGui(form, entity, matrices, light, overlay, tickDelta, deathTime, state);
        }
        else
        {
            queue(form, entity, light, overlay, tickDelta, deathTime);
        }
    }

    /**
     * Draw a morph into the GUI, where the entity preview is being rendered.
     *
     * <p>Immediate, like the shadow pass and unlike the main pass: the special-element renderer has
     * already pointed the output at its own off-screen target and set the preview's projection, and it
     * flushes what we draw as soon as this call returns — there is no later point to defer to. The
     * translucent queue is suspended for the span for the same reason (its flush belongs to the world).</p>
     *
     * <p>The preview's rotations live in the render state, not on the entity: 1.21.5 stopped writing
     * them onto the entity for the duration of the draw (which is exactly what 1.21.1's
     * {@code drawEntity} did, and why the morph simply worked there). BBS's form rendering reads the
     * entity, so we do that write ourselves — and put the real values back — instead of letting a
     * preview show the body yaw and head tracking of the world.</p>
     */
    private static void renderGui(Form form, IEntity entity, MatrixStack matrices, int light, int overlay, float tickDelta, int deathTime, LivingEntityRenderState state)
    {
        float yaw = entity.getYaw();
        float prevYaw = entity.getPrevYaw();
        float headYaw = entity.getHeadYaw();
        float prevHeadYaw = entity.getPrevHeadYaw();
        float bodyYaw = entity.getBodyYaw();
        float prevBodyYaw = entity.getPrevBodyYaw();
        float pitch = entity.getPitch();
        float prevPitch = entity.getPrevPitch();

        float previewBodyYaw = state == null ? Lerps.lerp(prevBodyYaw, bodyYaw, tickDelta) : state.bodyYaw;

        if (state != null)
        {
            float previewHeadYaw = state.bodyYaw + state.relativeHeadYaw;

            /* No interpolation to do in a preview — prev and current are the same pose. */
            entity.setBodyYaw(state.bodyYaw);
            entity.setPrevBodyYaw(state.bodyYaw);
            entity.setYaw(previewHeadYaw);
            entity.setPrevYaw(previewHeadYaw);
            entity.setHeadYaw(previewHeadYaw);
            entity.setPrevHeadYaw(previewHeadYaw);
            entity.setPitch(state.pitch);
            entity.setPrevPitch(state.pitch);
        }

        boolean wasActive = FormTranslucentQueue.suspend();

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-previewBodyYaw));

        /* This render replaces LivingEntityRenderer's own transforms, so the fall of a dead body has to
         * be repeated here — the same reason as in renderShadow. */
        DeathPose.apply(matrices, deathTime, tickDelta);

        try
        {
            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, entity, matrices, light, overlay, tickDelta)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
        }
        finally
        {
            matrices.pop();

            FormTranslucentQueue.restore(wasActive);

            if (state != null)
            {
                entity.setBodyYaw(bodyYaw);
                entity.setPrevBodyYaw(prevBodyYaw);
                entity.setYaw(yaw);
                entity.setPrevYaw(prevYaw);
                entity.setHeadYaw(headYaw);
                entity.setPrevHeadYaw(prevHeadYaw);
                entity.setPitch(pitch);
                entity.setPrevPitch(prevPitch);
            }
        }
    }

    /**
     * Draw a morph form into the shadow map, from the entity-submission phase Iris's shadow pass runs.
     *
     * <p>Drawing immediately is correct here even though it is wrong in the main pass: Iris makes every
     * render pass keep the bound framebuffer and viewport while shadows render
     * ({@code MixinGlCommandEncoder}), so an immediate draw lands in the shadow map rather than in the
     * screen target, and {@code FormTranslucentQueue} already refuses to defer during the shadow pass
     * for the same reason.
     *
     * <p>The matrices arrive shadow-ready — the submission stack carries the shadow camera's model-view
     * with the entity's position already translated in — so only the body rotation is missing, the one
     * piece {@link BaseFilmController#getMatrixForRenderWithRotation} adds on top of position in the main
     * path. The world-forms span matters as much as the draw: it hands the form the world pipeline
     * variant, which is the one that carries a shadow-pass program assignment (see
     * {@code BBSRendering#mirrorIrisPipeline}). Without the span the form would draw with BBS's own
     * shader and never appear in the pack's shadow map.
     *
     * <p>A form that opts out of casting shadows is filtered further down, in {@code FormRenderer}.
     */
    private static void renderShadow(Form form, IEntity entity, MatrixStack matrices, int light, int overlay, float tickDelta, int deathTime)
    {
        float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), tickDelta);

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

        /* This render replaces LivingEntityRenderer's own transforms, so the fall of a dead body has to
         * be repeated here - without it a morphed player never went down when they died. */
        DeathPose.apply(matrices, deathTime, tickDelta);

        boolean prevWorldForms = BBSRendering.beginWorldForms();

        try
        {
            FormUtilsClient.render(form, new FormRenderingContext()
                .set(FormRenderType.ENTITY, entity, matrices, light, overlay, tickDelta)
                .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
        }
        finally
        {
            BBSRendering.endWorldForms(prevWorldForms);

            matrices.pop();
        }
    }

    private static void queue(Form form, IEntity entity, int light, int overlay, float tickDelta, int deathTime)
    {
        /* One entry per entity per drain: the collect hooks fire from the entity submission phase,
         * which can run more than once before AFTER_ENTITIES drains the queue (an extra render pass —
         * e.g. a shader mod's shadow pass — submits entities too). Without the dedup the same morph
         * draws twice in one frame, which doubles every translucent alpha. */
        for (Queued queued : QUEUE)
        {
            if (queued.entity == entity)
            {
                queued.form = form;
                queued.light = light;
                queued.overlay = overlay;
                queued.tickDelta = tickDelta;
                queued.deathTime = deathTime;

                return;
            }
        }

        Queued queued = new Queued();

        queued.form = form;
        queued.entity = entity;
        queued.light = light;
        queued.overlay = overlay;
        queued.tickDelta = tickDelta;
        queued.deathTime = deathTime;

        QUEUE.add(queued);
    }

    /**
     * Draw all collected morph forms. Called from WorldRenderEvents.AFTER_ENTITIES, where the entity
     * command queue has already flushed and the WorldRenderContext MatrixStack carries the camera
     * transform — the only world context where the BBS immediate form pipeline lands correctly. This
     * mirrors {@link BaseFilmController#renderEntity}: build the camera-relative matrix from the
     * entity's world position and multiply it onto the context MatrixStack.
     */
    public static void renderQueued(WorldRenderContext context)
    {
        if (QUEUE.isEmpty())
        {
            return;
        }

        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        double cx = camera.getCameraPos().x;
        double cy = camera.getCameraPos().y;
        double cz = camera.getCameraPos().z;
        MatrixStack stack = context.matrices();

        try
        {
            for (Queued queued : QUEUE)
            {
                Matrix4f target = BaseFilmController.getMatrixForRenderWithRotation(queued.entity, cx, cy, cz, queued.tickDelta);

                stack.push();
                MatrixStackUtils.multiply(stack, target);

                /* The target matrix carries position and body yaw, the way vanilla's setupTransforms
                 * does - the fall of a dead body goes on top of it, as it does there. */
                DeathPose.apply(stack, queued.deathTime, queued.tickDelta);

                FormUtilsClient.render(queued.form, new FormRenderingContext()
                    .set(FormRenderType.ENTITY, queued.entity, stack, queued.light, queued.overlay, queued.tickDelta)
                    .camera(camera));

                stack.pop();
            }
        }
        finally
        {
            /* Always drain: a throw mid-loop must not leave stale entries replaying next frame. */
            QUEUE.clear();
        }
    }

    private static boolean canRender()
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (menu instanceof UIDashboard dashboard)
        {
            UIDashboardPanel panel = dashboard.getPanels().panel;

            if (panel instanceof UIMorphingPanel morphingPanel)
            {
                return !morphingPanel.palette.editor.isEditing();
            }
        }

        return true;
    }

    private static class Queued
    {
        public Form form;
        public IEntity entity;
        public int light;
        public int overlay;
        public float tickDelta;
        public int deathTime;
    }
}
