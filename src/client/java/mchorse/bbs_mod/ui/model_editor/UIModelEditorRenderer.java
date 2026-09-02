package mchorse.bbs_mod.ui.model_editor;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.forms.FormTranslucentQueue;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.bones.UIBonePicker;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The model editor's viewport: the orbit preview of {@link UIFormRenderer} with what the
 * config being edited needs to be seen.
 *
 * <ul>
 * <li>A pick stencil over the model — so a bone lights up under the cursor and under the
 * cursor of a row that names it ({@link #highlight}), the bone pickers' eyedropper works
 * ({@link UIBonePicker.Viewport}), and a click on a bone reports it to the panel.</li>
 * <li>The transform gizmo on the picked attachment slot or pose bone ({@link #target}): drawn
 * in the frame the renderer applies the transform in, so dragging a handle moves the item
 * (or the bone) exactly as the numbers would.</li>
 * <li>A first-person view ({@link #setFirstPerson}): the game's own hand frame and lens,
 * showing the first-person slots the way {@code ModelFormRenderer#renderArm} does in play —
 * the only way those slots were ever visible before.</li>
 * <li>The armor and the held items ({@link #setEquipment}), worn while the page that edits
 * them is open, so each is seen against the bare model.</li>
 * </ul>
 */
public class UIModelEditorRenderer extends UIFormRenderer implements GizmoViewport, UIBonePicker.Viewport
{
    /** Vanilla's near plane for the hand ({@code GameRenderer#getBasicProjectionMatrix}). */
    private static final float FIRST_PERSON_NEAR = 0.05F;

    private final StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private final StencilMap stencilMap = new StencilMap();
    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    /** The bone matrices of the frame being drawn, as the slots' frames are built on them. */
    private MatrixCache bones;

    /** The transition they were drawn at, for the fresh samples a pose drag takes. */
    private float transition;

    private Supplier<ModelSlotTarget> target = () -> null;
    private Predicate<String> onBoneClick;

    /** The armed eyedropper; null when idle. */
    private Consumer<String> bonePicking;

    /** A bone a row asked to light up this frame; consumed by {@link #render}. */
    private String highlighted;

    private boolean firstPerson;
    private boolean firstPersonShown;

    /* The orbit lens, put back when the first-person view is left. */
    private float orbitFov;
    private float orbitNear;

    /**
     * Something in every slot the config can place, so the slots are seen the moment they get a bone —
     * a varied set, so each region reads at a glance.
     */
    private static final Map<EquipmentSlot, Item> EQUIPMENT = Map.of(
        EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD,
        EquipmentSlot.OFFHAND, Items.NETHERITE_SWORD,
        EquipmentSlot.HEAD, Items.TURTLE_HELMET,
        EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE,
        EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS,
        EquipmentSlot.FEET, Items.NETHERITE_BOOTS
    );

    /** What the preview wears: the armor pieces, the items in the hands — each only while its page is open. */
    public void setEquipment(boolean armor, boolean items)
    {
        for (Map.Entry<EquipmentSlot, Item> entry : EQUIPMENT.entrySet())
        {
            boolean worn = entry.getKey().getType() == EquipmentSlot.Type.ARMOR ? armor : items;

            this.entity.setEquipmentStack(entry.getKey(), worn ? new ItemStack(entry.getValue()) : ItemStack.EMPTY);
        }
    }

    /** The slot the gizmo is on, asked every frame so the panel's selection stays the truth. */
    public UIModelEditorRenderer target(Supplier<ModelSlotTarget> target)
    {
        this.target = target;

        return this;
    }

    /** A bone clicked in the viewport (outside of an eyedropper pick); answers whether it took the click. */
    public UIModelEditorRenderer onBoneClick(Predicate<String> callback)
    {
        this.onBoneClick = callback;

        return this;
    }

    /** Light {@code bone} up this frame, the way it lights up under the cursor. */
    public void highlight(String bone)
    {
        if (bone != null && !bone.isEmpty())
        {
            this.highlighted = bone;
        }
    }

    public boolean isFirstPerson()
    {
        return this.firstPerson;
    }

    public void setFirstPerson(boolean firstPerson)
    {
        if (this.firstPerson == firstPerson)
        {
            return;
        }

        this.firstPerson = firstPerson;

        if (firstPerson)
        {
            this.orbitFov = this.camera.fov;
            this.orbitNear = this.camera.near;
        }
        else
        {
            this.camera.fov = this.orbitFov;
            this.camera.near = this.orbitNear;
        }

        this.gizmo.stop();
        this.stencil.clearPicking();
    }

    /* Eyedropper */

    @Override
    public void startPicking(Consumer<String> callback)
    {
        this.stopPicking();
        this.bonePicking = callback;
    }

    @Override
    public void stopPicking()
    {
        Consumer<String> callback = this.bonePicking;

        this.bonePicking = null;

        if (callback != null)
        {
            callback.accept(null);
        }
    }

    /* Gizmo */

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
        ModelSlotTarget target = this.shownTarget();

        if (target == null)
        {
            return false;
        }

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, target.editor(), this.buildGizmoDrag(target));
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        if (this.onBoneClick != null && form == this.form && bone != null && !bone.isEmpty())
        {
            this.onBoneClick.test(bone);
        }
    }

    /**
     * The slot whose gizmo is drawn: the picked one, when this view shows it — the held items
     * and armor in the orbit view, the first-person hands in the first-person view — and its
     * bone was actually rendered.
     */
    private ModelSlotTarget shownTarget()
    {
        ModelSlotTarget target = this.target.get();

        if (target == null || target.kind().firstPerson != this.firstPerson || target.editor().getTransform() == null)
        {
            return null;
        }

        return this.boneMatrix(target) == null ? null : target;
    }

    private Matrix4f boneMatrix(ModelSlotTarget target)
    {
        MatrixCacheEntry entry = this.boneEntry(target.bone());

        return entry == null ? null : entry.matrix();
    }

    /** The bone's frames of the frame being drawn. */
    private MatrixCacheEntry boneEntry(String bone)
    {
        return this.bones != null && this.bones.has(bone) ? this.bones.get(bone) : null;
    }

    /**
     * The bone's frames evaluated right now, off the pose as it currently is — what a pose drag's
     * samplers need: they nudge the pose and read back where the bone went. The channel cache is
     * dropped first, since the sneaking pose isn't part of its key (only the form's pose is).
     */
    private MatrixCacheEntry sampleBone(String bone)
    {
        FormRenderer renderer = FormUtilsClient.getRenderer(this.form);

        if (renderer instanceof ModelFormRenderer model && model.getModel() != null)
        {
            model.getModel().clearChannels();
        }

        MatrixCache bones = renderer == null ? null : renderer.collectMatrices(this.entity, this.transition);

        return bones != null && bones.has(bone) ? bones.get(bone) : null;
    }

    /**
     * The bone matrices of the pose just drawn, the way the form editor reads them for its gizmo —
     * once per frame, since the gizmo's placement, its pick stencil and a drag's samplers all ask.
     */
    private void captureBones(UIContext context)
    {
        FormRenderer renderer = FormUtilsClient.getRenderer(this.form);

        this.transition = context.getTransition();
        this.bones = renderer == null ? null : renderer.collectMatrices(this.entity, this.transition);
    }

    /**
     * The first-person hand isn't animated (it's drawn from the rest pose, as in play), so its bones
     * are read straight off the pose the hand was just drawn in, not the animated one the form's
     * capture evaluates. Matches how the hand is placed: the arm frame, then the bones.
     */
    private void captureFirstPersonBones(ModelFormRenderer renderer)
    {
        ModelInstance instance = renderer.getModel();

        if (!this.firstPersonShown || instance == null)
        {
            this.bones = null;

            return;
        }

        MatrixCache bones = new MatrixCache();

        instance.captureMatrices(bones);
        this.bones = bones;
    }

    /**
     * The frame the slot's transform is applied in — everything the renderer multiplies onto
     * the stack before the transform. Kept in lockstep with {@code ModelFormRenderer}: the
     * held item's bone plus vanilla's item-in-hand adjustment, the armor piece's bare bone, the
     * first-person hand's arm frame ({@link ModelFormRenderer#applyFirstPersonArm}) turned
     * around like {@code renderFirstPersonHand} turns it.
     */
    private Matrix4f parentFrame(ModelSlotTarget target)
    {
        Matrix4f frame = new Matrix4f();

        switch (target.kind())
        {
            case FIRST_PERSON_MAIN, FIRST_PERSON_OFF ->
            {
                MatrixStack stack = new MatrixStack();

                ModelFormRenderer.applyFirstPersonArm(stack, !target.kind().offHand);
                stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
                frame.set(stack.peek().getPositionMatrix());
            }
            case ITEM_MAIN, ITEM_OFF ->
            {
                Matrix4f bone = this.boneMatrix(target);

                if (bone != null)
                {
                    frame.set(bone).rotateX(MathUtils.PI / 2F).rotateY(MathUtils.PI).translate(0F, 0.125F, 0F);
                }
            }
            case ARMOR ->
            {
                Matrix4f bone = this.boneMatrix(target);

                if (bone != null)
                {
                    frame.set(bone);
                }
            }
            case POSE ->
            {
                MatrixCacheEntry entry = this.boneEntry(target.bone());

                if (entry != null)
                {
                    frame.set(entry.origin());
                }
            }
        }

        return frame;
    }

    /**
     * Where the gizmo sits for {@code space}: on the slot's own frame for LOCAL, at the slot's
     * position on the parent frame otherwise — the form editor's placement convention. A pose
     * bone is placed the way the form editor places a bone: its full matrix for LOCAL, the frame
     * before its own rotation for every other space.
     *
     * @param fresh re-evaluate the bones instead of reading the drawn frame's — for a drag's
     *              samplers, which need to see the pose they just nudged
     */
    private Matrix4f origin(ModelSlotTarget target, TransformSpace space, boolean fresh)
    {
        if (target.kind() == ModelSlotKind.POSE)
        {
            MatrixCacheEntry entry = fresh ? this.sampleBone(target.bone()) : this.boneEntry(target.bone());

            if (entry == null)
            {
                return new Matrix4f();
            }

            return new Matrix4f(space.placesOnOwnFrame() ? entry.matrix() : entry.origin());
        }

        Transform transform = target.editor().getTransform();
        Matrix4f origin = this.parentFrame(target);

        if (space.placesOnOwnFrame())
        {
            transform.setupMatrix(origin);
        }
        else
        {
            origin.translate(transform.translate);
        }

        /* A first-person slot places the whole model branch under its bone, so the transform's own
         * origin is the model's root — which can be far off the hand on screen. The gizmo goes to the
         * bone instead, keeping the frame it edits in; a rotation still turns about the root, as the
         * transform does. */
        if (target.kind().firstPerson)
        {
            Matrix4f bone = this.boneMatrix(target);

            if (bone != null)
            {
                Matrix4f full = this.parentFrame(target);

                transform.setupMatrix(full);
                full.mul(bone);
                origin.setTranslation(full.getTranslation(new Vector3f()));
            }
        }

        return origin;
    }

    /**
     * The drag context for a slot, mirroring the form editor's (see its {@code buildGizmoDrag}). Also what
     * the slot's transform editor asks for when a G/R/S hotkey starts a gesture without a handle; null
     * until the gizmo has been drawn once, which is where the drag's frame is read back from.
     */
    public GizmoDrag buildGizmoDrag(ModelSlotTarget target)
    {
        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.camera, this.area);

        if (drag == null)
        {
            return null;
        }

        Transform transform = target.editor().getTransform();

        drag.setGlobalAxes(this.getSceneAxes());
        drag.setJacobian(GizmoDrag.computeTranslateJacobian(transform, () -> this.toSceneMatrix(this.origin(target, TransformSpace.LOCAL, true)).getTranslation(new Vector3f())));
        drag.setRotateAxes(GizmoDrag.computeRotateAxes(transform, () -> MatrixStackUtils.stripScale(this.toSceneMatrix(this.origin(target, TransformSpace.LOCAL, true)))));
        drag.setAdditiveRotationBase(this.poseRotationBase(target));
        drag.setFrameAxes(
            this.toSceneMatrix(this.origin(target, TransformSpace.LOCAL, true)),
            this.toSceneMatrix(this.origin(target, TransformSpace.PARENT, true))
        );

        return drag;
    }

    /**
     * The additive euler base under a pose bone's channels — the bone's evaluated rotation (rest,
     * actions, the sneaking pose) minus the pose's own contribution, so gizmo deltas compose at the
     * effective angles; the form editor's rule ({@code FormUtils#additivePoseRotationBase}). Null
     * for the slots and for a pose that merges multiplicatively (a quaternion, a fix weight).
     */
    private Vector3f poseRotationBase(ModelSlotTarget target)
    {
        if (target.kind() != ModelSlotKind.POSE || !(target.editor().getTransform() instanceof PoseTransform pose))
        {
            return null;
        }

        if (pose.rotationMode == Transform.RotationMode.QUATERNION || pose.fix != 0F)
        {
            return null;
        }

        MatrixCacheEntry entry = this.sampleBone(target.bone());

        return entry == null || entry.evaluatedRotation() == null ? null : new Vector3f(entry.evaluatedRotation()).sub(pose.rotate);
    }

    /** Move the stack to the gizmo's placement, reoriented into the editor's active space. */
    private void placeGizmo(MatrixStack stack, ModelSlotTarget target)
    {
        TransformSpace space = target.editor().getSpace();

        MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(this.origin(target, space, false)));
        Gizmo.INSTANCE.reorientForSpace(stack, space, this.camera.view, this.getSceneAxes());
    }

    /* Rendering */

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_model_editor"));
        this.stencil.resizeGUI(this.area.w, this.area.h);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    /**
     * The first-person view borrows the game's lens for the hand: the player's field of view
     * and vanilla's near plane, from the eye, looking straight ahead. The orbit's position and
     * rotation are overwritten every frame while the view is on, and put back by
     * {@link #setFirstPerson} when it's left.
     */
    @Override
    protected void setupViewport(UIContext context)
    {
        if (this.firstPerson)
        {
            this.camera.setFov(MinecraftClient.getInstance().options.getFov().getValue());
            this.camera.near = FIRST_PERSON_NEAR;
            this.camera.position.set(0, 0, 0);
            this.camera.rotation.set(0, 0, 0);
        }

        super.setupViewport(context);
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        if (!this.firstPerson)
        {
            super.renderGrid(context);
        }
    }

    /**
     * Tick the form so its animator runs — the idle plays, and a played animation moves. The stub is
     * handed the client's world along the way: the armor in the slots reads its trims off the world's
     * registries.
     */
    @Override
    protected void update()
    {
        super.update();

        this.entity.setWorld(MinecraftClient.getInstance().world);

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.entity, context.batcher.getContext().getMatrices(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer(context.getTick());

        if (this.firstPerson)
        {
            this.renderFirstPerson(context);
        }
        else
        {
            /* The same deferred translucency the form editor's viewport gets: semi-transparent
             * pixels draw after the opaque ones, sorted, without hiding bones behind them. */
            FormTranslucentQueue.begin();
            FormUtilsClient.render(this.form, formContext);
            FormTranslucentQueue.flush();
            this.captureBones(context);
        }

        /* Keep the gizmo the same on-screen size as in the film preview; set before both the
         * visual and the stencil pass so the drawn handles and their pick hitbox match. */
        Gizmo.INSTANCE.setViewportScale(context.menu.height / (float) this.area.h);

        ModelSlotTarget shown = this.shownTarget();

        if (shown != null)
        {
            this.renderAxes(context, shown);
        }

        boolean inside = this.area.isInside(context);

        /* The stencil is drawn under the cursor, and also while a row elsewhere points at a bone —
         * that is how the bone gets lit up without the cursor being here. */
        if (inside || (this.highlighted != null && !this.firstPerson))
        {
            GlStateManager._disableScissorTest();

            this.stencilMap.setup();
            this.stencil.apply();

            /* The first-person hand has no stencil pass; only the gizmo's handles pick there. */
            if (!this.firstPerson)
            {
                FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));
            }

            if (shown != null && !UIBaseMenu.isHideGizmoHeld())
            {
                MatrixStack stack = context.render.batcher.getContext().getMatrices();

                stack.push();
                this.placeGizmo(stack, shown);
                Gizmo.INSTANCE.renderStencil(stack);
                stack.pop();
            }

            if (inside)
            {
                this.stencil.pickGUI(context, this.area, BBSSettings.gizmoHoverTolerance.get(), Gizmo.STENCIL_MAX);
            }
            else
            {
                this.stencil.clearIndex();
            }

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

    /**
     * The hand the picked first-person slot is for (the main hand when none is picked), placed
     * where the game places an empty first-person hand. Remembers whether anything was drawn,
     * so the view can say why it's empty.
     */
    private void renderFirstPerson(UIContext context)
    {
        this.firstPersonShown = false;
        this.bones = null;

        if (!(FormUtilsClient.getRenderer(this.form) instanceof ModelFormRenderer renderer))
        {
            return;
        }

        ModelSlotTarget target = this.target.get();
        boolean mainHand = target == null || !target.kind().firstPerson || !target.kind().offHand;
        MatrixStack stack = context.batcher.getContext().getMatrices();

        renderer.ensureAnimator(context.getTransition());

        stack.push();
        ModelFormRenderer.applyFirstPersonArm(stack, mainHand);
        this.firstPersonShown = renderer.renderFirstPersonHand(stack, LightmapTextureManager.pack(15, 15), mainHand ? Hand.MAIN_HAND : Hand.OFF_HAND);
        stack.pop();

        this.captureFirstPersonBones(renderer);
    }

    private void renderAxes(UIContext context, ModelSlotTarget target)
    {
        MatrixStack stack = context.render.batcher.getContext().getMatrices();

        stack.push();
        this.placeGizmo(stack, target);

        if (UIBaseMenu.shouldRenderAxes())
        {
            RenderSystem.disableDepthTest();
            Gizmo.INSTANCE.render(stack);
            RenderSystem.enableDepthTest();
        }

        stack.pop();
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        this.gizmo.renderSphereHighlight(context);
        this.gizmo.renderReadout(context);

        if (this.firstPerson)
        {
            this.renderFirstPersonOverlay(context);
        }

        /* What's lit up: the pick under the cursor, else the bone a row pointed at this frame. */
        int index = this.stencil.hasPicked() ? this.stencil.getIndex() : this.highlighted == null ? 0 : this.stencil.indexOf(this.form, this.highlighted);

        this.highlighted = null;

        if (index > 0)
        {
            this.stencil.renderPreview(context, this.area, index);
        }

        if (this.stencil.hasPicked())
        {
            Pair<Form, String> pair = this.stencil.getPicked();

            if (pair != null && pair.a != null && !pair.b.isEmpty())
            {
                context.batcher.textCard(pair.b, context.mouseX + 12, context.mouseY + 8);
            }
        }
        else if (this.bonePicking != null && this.area.isInside(context))
        {
            /* An armed eyedropper over empty space explains itself at the cursor. */
            context.batcher.textCard(UIKeys.BONE_PICKER_CLICK_BONE.get(), context.mouseX + 12, context.mouseY + 8);
        }
    }

    /** The game's frame: its edge, a crosshair where the game's would be, and why the view is empty when it is. */
    private void renderFirstPersonOverlay(UIContext context)
    {
        int cx = this.area.mx();
        int cy = this.area.my();
        int color = Colors.setA(Colors.WHITE, 0.6F);

        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.setA(Colors.WHITE, 0.2F));
        context.batcher.box(cx - 5, cy, cx + 5, cy + 1, color);
        context.batcher.box(cx, cy - 5, cx + 1, cy + 5, color);

        if (!this.firstPersonShown)
        {
            FontRenderer font = context.batcher.getFont();
            String label = UIKeys.MODEL_EDITOR_FIRST_PERSON_NO_SLOT.get();

            context.batcher.textCard(label, cx - font.getWidth(label) / 2, cy + 12, Colors.WHITE, Colors.A50);
        }
    }

    /* Input */

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            return super.subMouseClicked(context);
        }

        /* An armed eyedropper wins over everything else the click could do — that's the whole
         * point of the mode. A left click disarms it: a bone delivers, a miss cancels; a right
         * click just cancels. */
        if (this.bonePicking != null)
        {
            if (context.mouseButton == 1)
            {
                this.stopPicking();

                return true;
            }

            if (context.mouseButton == 0)
            {
                Consumer<String> callback = this.bonePicking;
                Pair<Form, String> pair = this.stencil.hasPicked() ? this.stencil.getPicked() : null;

                this.bonePicking = null;
                callback.accept(pair != null && pair.a == this.form ? pair.b : null);

                return true;
            }
        }

        if (this.gizmo.mouseClicked(context))
        {
            return true;
        }

        /* A left click on a bone picks it in the panel's tree, the way the form editor picks a bone;
         * when the panel takes it, the click is spent, so it doesn't start an orbit as well. */
        if (context.mouseButton == 0 && this.stencil.hasPicked() && this.onBoneClick != null)
        {
            Pair<Form, String> pair = this.stencil.getPicked();

            if (pair != null && pair.a == this.form && !pair.b.isEmpty() && this.onBoneClick.test(pair.b))
            {
                return true;
            }
        }

        /* No orbiting in the first-person view: the lens is the game's, not the user's. */
        if (this.firstPerson)
        {
            return context.mouseButton == 0 || context.mouseButton == 2;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.firstPerson && this.area.isInside(context))
        {
            return true;
        }

        return super.subMouseScrolled(context);
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
}
