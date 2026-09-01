package mchorse.bbs_mod.ui.model_blocks;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.ModelBlockSound;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelBody;
import mchorse.bbs_mod.blocks.entities.ModelEquipment;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UINestedEdit;
import mchorse.bbs_mod.ui.forms.UIToggleEditorEvent;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIItemStack;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.drag.TransformSpace;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;
import mchorse.bbs_mod.ui.model_blocks.camera.ImmersiveModelBlockCameraController;
import mchorse.bbs_mod.ui.model_blocks.camera.OrbitModelBlockCameraController;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoDrag;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.pose.Transform;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UIModelBlockPanel extends UIDashboardPanel implements GizmoViewport
{
    public static boolean toggleRendering;

    /** Slots in the order they are listed in the equipment section. */
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** Fold state of the sections, kept across panel rebuilds like the form editor does. */
    private static final Map<String, Boolean> sectionFolds = new HashMap<>();

    public UIScrollView scrollView;
    public UISplitter draggable;
    public UIElement editor;
    public UIModelBlockEntityList modelBlocks;
    public UISearchList<ModelBlockEntity> modelBlocksSearch;
    public UINestedEdit pickEdit;
    public UIToggle enabled;
    public UIToggle shadow;
    public UIToggle global;
    public UIToggle lookAt;
    public UIPropTransform transform;

    public UISection bodySection;
    public UISection equipmentSection;
    public UICirculate hitboxMode;
    public UIElement hitboxManual;
    public UITrackpad hitboxMinX;
    public UITrackpad hitboxMinY;
    public UITrackpad hitboxMinZ;
    public UITrackpad hitboxMaxX;
    public UITrackpad hitboxMaxY;
    public UITrackpad hitboxMaxZ;
    public UIToggle solid;
    public UIToggle cameraCollision;
    public UITrackpad hardness;
    public UITrackpad lightLevel;
    public UICirculate sound;
    public Map<EquipmentSlot, UIItemStack> equipmentSlots = new EnumMap<>(EquipmentSlot.class);

    private final StencilFormFramebuffer gizmoStencil = new StencilFormFramebuffer();
    private final StencilMap gizmoStencilMap = new StencilMap();
    private final GizmoInteraction gizmo = new GizmoInteraction(this);
    private final mchorse.bbs_mod.camera.Camera gizmoCamera = new mchorse.bbs_mod.camera.Camera();
    private final Matrix4f gizmoProjection = new Matrix4f();

    private ModelBlockEntity modelBlock;
    private ModelBlockEntity hovered;
    private Vector3f mouseDirection = new Vector3f();

    private Set<ModelBlockEntity> toSave = new HashSet<>();

    private ImmersiveModelBlockCameraController cameraController;

    /**
     * How this panel is flown: turning around the selected block, the way the film's viewport
     * turns around a replay. It replaces the dashboard's flight here rather than sitting beside
     * it - a block is a thing one walks around, and two ways of moving would only split the
     * muscle memory in half.
     */
    public final OrbitModelBlockCameraController orbit = new OrbitModelBlockCameraController(this);
    private UIElement keyDude;

    public UIModelBlockPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.keyDude = new UIElement().noCulling();
        this.keyDude.keys().register(Keys.MODEL_BLOCKS_MOVE_TO, () ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            Camera camera = mc.gameRenderer.getCamera();
            BlockHitResult blockHitResult = RayTracing.rayTrace(mc.world, camera.getPos(), RayTracing.fromVector3f(this.mouseDirection), 512F);

            if (blockHitResult.getType() != HitResult.Type.MISS)
            {
                Vec3d hit = blockHitResult.getPos();
                BlockPos pos = this.modelBlock.getPos();

                this.modelBlock.getProperties().getTransform().translate.set(hit.x - pos.getX() - 0.5F, hit.y - pos.getY(), hit.z - pos.getZ() - 0.5F);
                this.fillData();
            }
        }).active(() -> this.modelBlock != null);

        this.modelBlocks = new UIModelBlockEntityList((l) -> this.fill(l.get(0), false));
        this.modelBlocks.context((menu) ->
        {
            if (this.modelBlock != null) menu.action(UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, this::teleport);
        });
        this.modelBlocks.background();

        this.modelBlocksSearch = new UISearchList<>(this.modelBlocks);
        this.modelBlocksSearch.label(UIKeys.GENERAL_SEARCH);
        this.modelBlocksSearch.h(20 + UIModelBlockEntityList.ROW * 9);

        /* What the tour of this panel points at; the fields below are built further down */
        TourAnchors.register("model_blocks.list", () -> this.modelBlocksSearch);
        TourAnchors.register("model_blocks.form", () -> this.pickEdit);
        TourAnchors.register("model_blocks.transform", () -> this.transform);

        this.pickEdit = new UINestedEdit((editing) ->
        {
            UIFormPalette palette = UIFormPalette.open(this, editing, this.modelBlock.getProperties().getForm(), (f) ->
            {
                this.pickEdit.setForm(f);

                if (this.modelBlock != null)
                {
                    this.modelBlock.getProperties().setForm(f);
                }
            });

            palette.immersive();
            palette.editor.keys().register(Keys.MODEL_BLOCKS_TOGGLE_RENDERING, () -> toggleRendering = !toggleRendering);
            palette.editor.renderer.full(dashboard.getRoot());
            palette.editor.renderer.setTarget(this.modelBlock.getEntity());
            palette.editor.renderer.setRenderForm(() -> !toggleRendering);
            palette.getEvents().register(UIToggleEditorEvent.class, (e) ->
            {
                if (e.editing)
                {
                    this.addCameraController(palette);
                }
                else
                {
                    this.removeCameraController();
                }
            });
            palette.getEvents().register(UIRemovedEvent.class, (e) ->
            {
                this.scrollView.setVisible(true);
                this.draggable.setVisible(true);
            });

            palette.resize();

            if (editing)
            {
                this.addCameraController(palette);
            }

            this.scrollView.setVisible(false);
            this.draggable.setVisible(false);
        });
        this.pickEdit.keybinds();

        this.enabled = new UIToggle(UIKeys.CAMERA_PANELS_ENABLED, (b) -> this.modelBlock.getProperties().setEnabled(b.getValue()));
        this.shadow = new UIToggle(UIKeys.MODEL_BLOCKS_SHADOW, (b) -> this.modelBlock.getProperties().setShadow(b.getValue()));
        this.global = new UIToggle(UIKeys.MODEL_BLOCKS_GLOBAL, (b) ->
        {
            this.modelBlock.getProperties().setGlobal(b.getValue());
            MinecraftClient.getInstance().worldRenderer.reload();
        });
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, (b) -> this.modelBlock.getProperties().setLookAt(b.getValue()));

        this.transform = new UIPropTransform();
        this.transform.enableHotkeys();
        this.transform.hotkeyDrag(this::buildGizmoDrag);

        /* Body: the block's physical side (hitbox, solidity, light, sound). */
        this.hitboxMode = new UICirculate((b) ->
        {
            this.getBody().setHitboxMode(ModelBody.HitboxMode.values()[b.getValue()]);
            this.updateHitboxManualVisibility();
        });
        this.hitboxMode.addLabel(UIKeys.MODEL_BLOCKS_BODY_HITBOX_CUBE);
        this.hitboxMode.addLabel(UIKeys.MODEL_BLOCKS_BODY_HITBOX_FORM);
        this.hitboxMode.addLabel(UIKeys.MODEL_BLOCKS_BODY_HITBOX_MANUAL);

        this.hitboxMinX = new UITrackpad((v) -> this.getBody().getHitboxMin().x = v.floatValue());
        this.hitboxMinY = new UITrackpad((v) -> this.getBody().getHitboxMin().y = v.floatValue());
        this.hitboxMinZ = new UITrackpad((v) -> this.getBody().getHitboxMin().z = v.floatValue());
        this.hitboxMaxX = new UITrackpad((v) -> this.getBody().getHitboxMax().x = v.floatValue());
        this.hitboxMaxY = new UITrackpad((v) -> this.getBody().getHitboxMax().y = v.floatValue());
        this.hitboxMaxZ = new UITrackpad((v) -> this.getBody().getHitboxMax().z = v.floatValue());

        this.hitboxManual = UI.column(
            UI.label(UIKeys.MODEL_BLOCKS_BODY_HITBOX_MIN),
            UI.row(this.hitboxMinX, this.hitboxMinY, this.hitboxMinZ),
            UI.label(UIKeys.MODEL_BLOCKS_BODY_HITBOX_MAX),
            UI.row(this.hitboxMaxX, this.hitboxMaxY, this.hitboxMaxZ)
        );
        this.hitboxManual.setVisible(false);

        this.solid = new UIToggle(UIKeys.MODEL_BLOCKS_BODY_SOLID, (b) -> this.getBody().setSolid(b.getValue()));
        this.cameraCollision = new UIToggle(UIKeys.MODEL_BLOCKS_BODY_CAMERA, (b) -> this.getBody().setCameraCollision(b.getValue()));

        /* The server steps break progress from its own copy of the body, so
         * hardness saves right away — otherwise it would still break instantly
         * until the panel saves. */
        this.hardness = new UITrackpad((v) ->
        {
            this.getBody().setHardness(v.floatValue());
            this.save(this.modelBlock);
        });
        this.hardness.limit(0).tooltip(UIKeys.MODEL_BLOCKS_BODY_HARDNESS_TOOLTIP);

        /* Light and sound live in the block state server side, so these two
         * save right away — otherwise nothing visible happens until the panel
         * saves on switching blocks or closing. */
        this.lightLevel = new UITrackpad((v) ->
        {
            this.getBody().setLightLevel(v.intValue());
            this.save(this.modelBlock);
        });
        this.lightLevel.limit(0, 15, true);

        this.sound = new UICirculate((b) ->
        {
            this.getBody().setSound(ModelBlockSound.values()[b.getValue()]);
            this.save(this.modelBlock);
        });
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_STONE);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_WOOD);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_METAL);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_GLASS);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_WOOL);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_GRASS);
        this.sound.addLabel(UIKeys.MODEL_BLOCKS_BODY_SOUND_NONE);

        this.bodySection = new UISection(UIKeys.MODEL_BLOCKS_BODY).remember(sectionFolds, "body", false);

        this.bodySection.fields.add(
            this.hitboxMode,
            this.hitboxManual,
            this.solid,
            this.cameraCollision,
            UI.labelRow(UIKeys.MODEL_BLOCKS_BODY_HARDNESS, this.hardness),
            UI.labelRow(UIKeys.MODEL_BLOCKS_BODY_LIGHT, this.lightLevel),
            this.sound
        );

        /* Equipment: six vanilla slots rendered by the existing armor and
         * held item renderers. A 2×3 grid of slots — the slot's name lives in
         * its tooltip, and the BBS armor icons tell the slots apart (the hand
         * icons follow the replay tracks: hotbar for the main hand, limb for
         * the off hand). */
        IKey[] slotTooltips = {
            UIKeys.MODEL_BLOCKS_EQUIPMENT_HEAD, UIKeys.MODEL_BLOCKS_EQUIPMENT_CHEST,
            UIKeys.MODEL_BLOCKS_EQUIPMENT_LEGS, UIKeys.MODEL_BLOCKS_EQUIPMENT_FEET,
            UIKeys.MODEL_BLOCKS_EQUIPMENT_MAINHAND, UIKeys.MODEL_BLOCKS_EQUIPMENT_OFFHAND
        };
        Icon[] slotIcons = {
            Icons.ARMOR_HELMET, Icons.ARMOR_CHESTPLATE,
            Icons.ARMOR_LEGGINGS, Icons.ARMOR_BOOTS,
            Icons.HOTBAR, Icons.LIMB
        };

        for (int i = 0; i < EQUIPMENT_SLOTS.length; i++)
        {
            EquipmentSlot slot = EQUIPMENT_SLOTS[i];
            UIItemStack stackUI = new UIItemStack((stack) -> this.getEquipment().set(slot, stack));

            stackUI.placeholder(slotIcons[i]).tooltip(slotTooltips[i]);
            this.equipmentSlots.put(slot, stackUI);
        }

        this.equipmentSection = new UISection(UIKeys.MODEL_BLOCKS_EQUIPMENT).remember(sectionFolds, "equipment", false);

        this.equipmentSection.fields.add(
            UI.row(this.equipmentSlots.get(EquipmentSlot.HEAD), this.equipmentSlots.get(EquipmentSlot.CHEST)),
            UI.row(this.equipmentSlots.get(EquipmentSlot.LEGS), this.equipmentSlots.get(EquipmentSlot.FEET)),
            UI.row(this.equipmentSlots.get(EquipmentSlot.MAINHAND), this.equipmentSlots.get(EquipmentSlot.OFFHAND))
        );

        this.editor = UI.column(this.pickEdit, this.enabled, this.shadow, this.global, this.lookAt, this.transform);

        this.scrollView = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.modelBlocksSearch, this.editor, this.bodySection, this.equipmentSection);
        this.scrollView.scroll.opposite().cancelScrolling();

        /* The sidebar resizes like the form editor's options column: a draggable
         * splitter whose share is remembered, double click resets it. */
        this.draggable = UISplitter.fraction("model_blocks.options", 0.2F, 0F, 0.5F);
        this.draggable.measure(this).onChange(() ->
        {
            this.scrollView.w(this.draggable.getValue()).resize();
            this.draggable.resize();
        });

        this.scrollView.relative(this).w(this.draggable.getValue()).minW(120).h(1F);
        this.draggable.relative(this.scrollView).x(1F).y(0.5F).w(6).h(40).anchor(0.5F, 0.5F);

        this.fill(null, false);

        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT, this::teleport);
        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT_ORBIT, () ->
        {
            this.orbit.teleportPivotToSubject();
            UIUtils.playClick();
        }).strict().active(() -> this.modelBlock != null);

        this.add(this.scrollView, this.draggable);

        this.onOpen(this::refreshBlocks);
        this.onAppear(this::enterEditing);
        this.onDisappear(this::leaveEditing);
        this.onClose(this::saveTouchedBlocks);
    }

    private void refreshBlocks()
    {
        this.updateList();

        if (this.modelBlock != null && this.modelBlock.isRemoved())
        {
            this.fill(null, true);
        }
    }

    private void enterEditing()
    {
        this.getContext().menu.main.add(this.keyDude);

        this.orbit.enabled = true;
        BBSModClient.getCameraController().add(this.orbit);

        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().add(this.cameraController);
        }
    }

    private void leaveEditing()
    {
        this.keyDude.removeFromParent();
        this.gizmo.stop();

        this.orbit.enabled = false;
        BBSModClient.getCameraController().remove(this.orbit);

        /* Detached from the global controller, but the field is kept: coming back to this panel
         * hands the same controller over again in enterEditing(). Dropping it (see
         * removeCameraController) is for leaving the screen for good. */
        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().remove(this.cameraController);
        }
    }

    private void saveTouchedBlocks()
    {
        this.removeCameraController();

        for (ModelBlockEntity entity : this.toSave)
        {
            this.save(entity);
        }

        this.toSave.clear();
    }

    private void teleport()
    {
        if (this.modelBlock != null)
        {
            BlockPos pos = this.modelBlock.getPos();

            PlayerUtils.teleport(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            UIUtils.playClick();
        }
    }

    public ModelBlockEntity getModelBlock()
    {
        return this.modelBlock;
    }

    /* Gizmo (editing the selected model block's transform in the world) */

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.gizmoStencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.gizmoProjection;
    }

    /**
     * The screen region the gizmo actually renders into: the full UI viewport, NOT this panel's own
     * area. The dashboard shrinks a panel by the 20px taskbar ({@code h(1F, -20)}), but the block
     * gizmo is drawn straight onto Minecraft's full-screen world — so its on-screen projection,
     * trackball sphere highlight and sphere pick must map against the whole screen. Using the shorter
     * panel area squishes/shifts them by that 20px (a constant, camera-independent offset).
     */
    @Override
    public Area getGizmoArea()
    {
        UIContext context = this.getContext();

        return context != null ? context.menu.viewport : this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        if (this.modelBlock == null)
        {
            return false;
        }

        return Gizmo.INSTANCE.start(stencilIndex, context.mouseX, context.mouseY, this.transform, this.buildGizmoDrag());
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        /* The model block gizmo only renders its own handles into the stencil,
         * so the deferred sphere-vs-form pick never resolves to a form here. */
    }

    /** Ray-drag context for the model block gizmo. Translation is one world unit per local
     *  unit, so the Jacobian is identity — but the rotation handles still need
     *  {@link GizmoDrag#computeRotateAxes}: the eulers compose, so {@code rotate.x/y/z} stop
     *  turning about the world axes once the block is rotated. */
    private GizmoDrag buildGizmoDrag()
    {
        if (this.modelBlock == null)
        {
            return null;
        }

        GizmoDrag drag = GizmoDrag.fromRenderedGizmo(this.gizmoCamera, this.getGizmoArea());
        Transform transform = this.modelBlock.getProperties().getTransform();

        if (drag != null && transform != null)
        {
            BlockPos pos = this.modelBlock.getPos();

            drag.setJacobian(GizmoDrag.computeTranslateJacobian(
                transform,
                () -> new Vector3f(
                    pos.getX() + 0.5F + transform.translate.x,
                    pos.getY() + transform.translate.y,
                    pos.getZ() + 0.5F + transform.translate.z
                )
            ));
            drag.setRotateAxes(GizmoDrag.computeRotateAxes(
                transform,
                () -> MatrixStackUtils.stripScale(transform.createMatrix())
            ));
            /* The block's two frames: its own rotation, and — since its transform
             * composes straight onto the world — the plain world axes above it (which
             * is also why PARENT is drawn as GLOBAL here, see renderGizmoVisual). The
             * pair lets the axis-key walk move a live gesture into LOCAL even when the
             * handles are drawn world-aligned. */
            drag.setFrameAxes(new Matrix4f().set(transform.createRotationMatrix()), new Matrix4f());
        }

        return drag;
    }

    /**
     * Whether the interactive gizmo is shown for {@code entity} — the selected
     * block, with gizmos enabled and the form palette closed. Lets the block
     * renderer drop its plain axes in favour of the gizmo.
     */
    public boolean isShowingGizmo(ModelBlockEntity entity)
    {
        return this.modelBlock == entity && this.canShowGizmo();
    }

    private boolean canShowGizmo()
    {
        return this.modelBlock != null
            && BBSSettings.gizmos.get()
            && !UIBaseMenu.isHideGizmoHeld()
            && this.getChildren(UIFormPalette.class).isEmpty();
    }

    private void renderGizmo(WorldRenderContext context, Vec3d cameraPos)
    {
        if (!this.canShowGizmo())
        {
            return;
        }

        MatrixStack stack = context.matrixStack();

        /* Capture the on-screen camera frame for the drag math and the deferred UI
         * passes: the gizmo's visual and its pick stencil are both drawn later in
         * the UI pass (GizmoInteraction.renderGizmo / renderGizmoStencilInterface)
         * from this frame, so rendering, picking and dragging share one coordinate
         * frame. */
        this.gizmoProjection.set(RenderSystem.getProjectionMatrix());
        this.gizmoCamera.projection.set(this.gizmoProjection);
        this.gizmoCamera.view.set(stack.peek().getPositionMatrix());
        this.gizmoCamera.position.set(cameraPos.x, cameraPos.y, cameraPos.z);

        /* Record the model-view at the block's gizmo origin so the UI-pass visual
         * and stencil (both read Gizmo#lastRenderMatrix) draw at the right place. */
        stack.push();
        this.applyGizmoOrigin(stack, cameraPos);
        /* Reorient into the active space (GLOBAL world axes / VIEW screen axes);
         * LOCAL keeps the block rotation applied above. The block's transform
         * composes straight onto the world, so its parent frame IS the world
         * frame — PARENT maps to GLOBAL here (bone editors instead keep their
         * placement frame, which carries the real parent frame). One capture
         * feeds both the visual and the pick stencil, so they stay in lockstep. */
        TransformSpace space = this.transform.getSpace();

        /* This gizmo edits the BLOCK's own transform, which composes straight
         * onto the world, so GLOBAL keeps meaning the plain world axes (null) —
         * turning the block must not turn the frame its own rotation is edited
         * in. The form INSIDE the block is a different story: it is drawn under
         * this transform, so its editor takes GLOBAL from the preview's scene
         * axes (UIModelRenderer#getSceneAxes) and follows the block. */
        Gizmo.INSTANCE.reorientForSpace(stack, space == TransformSpace.PARENT ? TransformSpace.GLOBAL : space, this.gizmoCamera.view, null);
        Gizmo.INSTANCE.captureVisual(stack);
        stack.pop();
    }

    /**
     * Move the stack to where the gizmo handles are drawn: the block's
     * transformed origin (camera-relative). In global mode the handles stay
     * world-aligned (position only); in local mode they follow the block's
     * rotation, matching how the form editor's {@code getOrigin} behaves.
     * Caller owns the surrounding {@code push}/{@code pop}.
     */
    private void applyGizmoOrigin(MatrixStack stack, Vec3d cameraPos)
    {
        BlockPos pos = this.modelBlock.getPos();
        Transform transform = this.modelBlock.getProperties().getTransform();

        stack.translate(
            pos.getX() + 0.5D + transform.translate.x - cameraPos.x,
            pos.getY() + transform.translate.y - cameraPos.y,
            pos.getZ() + 0.5D + transform.translate.z - cameraPos.z
        );

        if (this.transform.getSpace().placesOnOwnFrame())
        {
            MatrixStackUtils.multiply(stack, new Matrix4f().set(transform.createRotationMatrix()));
        }
    }

    /**
     * Render the gizmo handles into the picking framebuffer in the UI pass (the
     * new way) and read the handle under the cursor. Uses the same area /
     * projection / captured matrix as the visual ({@link Gizmo#renderInterface}),
     * so the picked pixel matches the drawn handle. The main framebuffer is handed
     * back afterwards so the rest of the UI keeps rendering normally.
     */
    private void renderGizmoStencilInterface(UIContext context)
    {
        if (!this.canShowGizmo())
        {
            this.gizmoStencil.clearPicking();

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        this.gizmoStencil.setup(Link.bbs("stencil_model_block"));

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        Texture texture = this.gizmoStencil.getFramebuffer().getMainTexture();

        if (texture.width != w || texture.height != h)
        {
            this.gizmoStencil.resize(w, h);
        }

        this.gizmoStencilMap.setup();

        /* Flush queued UI before binding the pick buffer, so pending batches go to
         * the screen and not into the stencil. */
        context.batcher.flush();
        this.gizmoStencil.apply();

        Gizmo.INSTANCE.renderStencilInterface(context, this.gizmoProjection, this.getGizmoArea());

        this.gizmoStencil.pick((int) mc.mouse.getX(), (int) (h - mc.mouse.getY()), Math.round(BBSSettings.gizmoHoverTolerance.get() * BBSModClient.getGUIScale()), Gizmo.STENCIL_MAX);
        this.gizmoStencil.unbind(this.gizmoStencilMap);

        mc.getFramebuffer().beginWrite(true);
    }

    private void addCameraController(UIFormPalette palette)
    {
        if (this.cameraController == null)
        {
            this.cameraController = new ImmersiveModelBlockCameraController(palette.editor.renderer, this.modelBlock);

            BBSModClient.getCameraController().add(this.cameraController);

            Transform transform = this.modelBlock.getProperties().getTransform().copy();

            transform.translate.set(0F, 0F, 0F);
            palette.editor.renderer.setTransform(transform.createMatrix());
        }
    }

    private void removeCameraController()
    {
        if (this.cameraController != null)
        {
            BBSModClient.getCameraController().remove(this.cameraController);

            this.cameraController = null;
        }
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    private void updateList()
    {
        this.modelBlocks.setBlocks(BBSRendering.capturedModelBlocks);

        /* Filling resets the list's filter, but the search box keeps its text - reapply
         * so what you see matches the query. */
        this.modelBlocks.filter(this.modelBlocksSearch.search.getText());

        this.fill(this.modelBlock, true);
    }

    public void fill(ModelBlockEntity modelBlock, boolean select)
    {
        if (modelBlock != null)
        {
            this.toSave.add(modelBlock);
        }

        boolean switched = modelBlock != null && modelBlock != this.modelBlock;

        this.modelBlock = modelBlock;

        /* Another block is another subject: the orbit goes to it instead of leaving the user
         * turning around where the last one stood. C brings it back over afterwards. */
        if (switched)
        {
            this.orbit.teleportPivotToSubject();
        }

        if (modelBlock != null)
        {
            this.fillData();
        }

        this.setEditorVisible(modelBlock != null);

        if (select)
        {
            this.modelBlocks.setCurrentScroll(modelBlock);
        }
    }

    private void setEditorVisible(boolean visible)
    {
        if (this.editor.isVisible() == visible)
        {
            return;
        }

        this.editor.setVisible(visible);
        this.bodySection.setVisible(visible);
        this.equipmentSection.setVisible(visible);
        this.scrollView.resize();
    }

    /** The selected block's body; the editor is only visible while a block is selected. */
    private ModelBody getBody()
    {
        return this.modelBlock.getProperties().getBody();
    }

    private ModelEquipment getEquipment()
    {
        return this.modelBlock.getProperties().getEquipment();
    }

    private void updateHitboxManualVisibility()
    {
        boolean manual = this.getBody().getHitboxMode() == ModelBody.HitboxMode.MANUAL;

        if (this.hitboxManual.isVisible() != manual)
        {
            this.hitboxManual.setVisible(manual);
            this.scrollView.resize();
        }
    }

    private void fillData()
    {
        ModelProperties properties = this.modelBlock.getProperties();

        this.pickEdit.setForm(properties.getForm());
        this.transform.setTransform(properties.getTransform());
        this.enabled.setValue(properties.isEnabled());
        this.shadow.setValue(properties.isShadow());
        this.global.setValue(properties.isGlobal());
        this.lookAt.setValue(properties.isLookAt());

        ModelBody body = properties.getBody();

        this.hitboxMode.setValue(body.getHitboxMode().ordinal());
        this.hitboxMinX.setValue(body.getHitboxMin().x);
        this.hitboxMinY.setValue(body.getHitboxMin().y);
        this.hitboxMinZ.setValue(body.getHitboxMin().z);
        this.hitboxMaxX.setValue(body.getHitboxMax().x);
        this.hitboxMaxY.setValue(body.getHitboxMax().y);
        this.hitboxMaxZ.setValue(body.getHitboxMax().z);
        this.solid.setValue(body.isSolid());
        this.cameraCollision.setValue(body.isCameraCollision());
        this.hardness.setValue(body.getHardness());
        this.lightLevel.setValue(body.getLightLevel());
        this.sound.setValue(body.getSound().ordinal());
        this.updateHitboxManualVisibility();

        ModelEquipment equipment = properties.getEquipment();

        for (EquipmentSlot slot : EQUIPMENT_SLOTS)
        {
            this.equipmentSlots.get(slot).setStack(equipment.get(slot));
        }
    }

    private void save(ModelBlockEntity modelBlock)
    {
        if (modelBlock != null)
        {
            ClientNetwork.sendModelBlockForm(modelBlock.getPos(), modelBlock);
        }
    }

    /** While a form is being edited in place, the palette owns the viewport and its own camera. */
    private boolean canOrbit()
    {
        return this.getChildren(UIFormPalette.class).isEmpty();
    }

    private void startOrbit(UIContext context)
    {
        if (this.canOrbit() && this.area.isInside(context))
        {
            this.orbit.start(context);
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context))
        {
            return true;
        }

        /* Gizmo handles first. The trackball sphere is otherwise deferred to the end so its screen
         * disc doesn't block a click on the model block under it - but while the sphere is hovered the
         * block fill is skipped, so the rotate disc can actually be grabbed (the gizmo sits on the
         * selected block, so this.hovered is almost always non-null and would steal the click). */
        if (this.canShowGizmo() && this.gizmo.mouseClickedHandle(context))
        {
            return true;
        }

        boolean sphereHovered = this.canShowGizmo() && this.gizmo.isSphereHovered();

        if (this.hovered != null && context.mouseButton == 0 && BBSSettings.clickModelBlocks.get() && !sphereHovered)
        {
            this.fill(this.hovered, true);

            /* Picking a block and turning around it are the same gesture: the press chooses,
             * and carrying on with the button down orbits */
            this.startOrbit(context);

            return false;
        }

        if (this.canShowGizmo() && this.gizmo.mouseClickedSphere(context))
        {
            return true;
        }

        this.startOrbit(context);

        return false;
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        if (this.canOrbit() && this.area.isInside(context) && this.orbit.zoom(context.mouseWheel))
        {
            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        boolean consumed = this.canShowGizmo() && this.gizmo.mouseReleased(context);

        this.gizmo.stop();
        this.orbit.stop();

        return super.subMouseReleased(context) || consumed;
    }

    @Override
    protected boolean subKeyPressed(UIContext context)
    {
        if (this.canOrbit() && this.orbit.keyPressed(context, this.area))
        {
            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.canOrbit())
        {
            this.orbit.handleOrbiting(context);
            this.orbit.update(context);
        }

        /* Pick first (UI pass): the stencil must be read before the visual's hover
         * (gizmo.update / renderGizmoHover both consume the picked index). */
        this.renderGizmoStencilInterface(context);

        if (this.canShowGizmo())
        {
            this.gizmo.renderGizmo(context);
            this.gizmo.update(context);
        }

        String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.dashboard.orbit.speed.getValue()).get();
        FontRenderer font = context.batcher.getFont();
        int w = font.getWidth(label);
        int x = this.area.w - w - 5;
        int y = this.area.ey() - font.getHeight() - 5;

        context.batcher.textCard(label, x, y, Colors.WHITE, Colors.A50);

        /* Solid backdrop under the sidebar, same surface as the form editor's
         * options column. Skipped while the sidebar is hidden (form palette
         * open) — the backdrop is painted here, not by the sidebar itself. */
        if (this.scrollView.isVisible())
        {
            this.scrollView.area.render(context.batcher, BBSSettings.deepSurface());
        }

        /* Light inputs on the deep backdrop, the film editor's scoping — the
         * sections drop them back to deep on their raised cards themselves. */
        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = false;
        }

        this.renderGizmoHover(context);

        if (this.canShowGizmo())
        {
            this.gizmo.renderSphereHighlight(context);
            this.gizmo.renderReadout(context);
        }
    }

    /**
     * Highlight the gizmo handle under the cursor by painting the picking
     * framebuffer back over the viewport through the picker-preview shader,
     * which recolours the pixels matching the hovered stencil index — the same
     * hover overlay the film and form editors draw.
     */
    private void renderGizmoHover(UIContext context)
    {
        if (!this.canShowGizmo() || !this.gizmoStencil.hasPicked())
        {
            return;
        }

        Texture texture = this.gizmoStencil.getFramebuffer().getMainTexture();
        int w = texture.width;
        int h = texture.height;

        ShaderProgram previewProgram = BBSShaders.getPickerPreviewProgram();
        GlUniform target = previewProgram.getUniform("Target");

        if (target != null)
        {
            target.set(this.gizmoStencil.getIndex());
        }

        GlUniform highlight = previewProgram.getUniform("HighlightColor");

        if (highlight != null)
        {
            int color = BBSSettings.stencilHighlightColor.get();

            highlight.set(Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        RenderSystem.enableBlend();
        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, 0, 0, context.menu.width, context.menu.height, 0, h, w, 0, w, h);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        Camera camera = context.camera();
        Vec3d pos = camera.getPos();

        MinecraftClient mc = MinecraftClient.getInstance();
        double x = mc.mouse.getX();
        double y = mc.mouse.getY();

        this.mouseDirection.set(CameraUtils.getMouseDirection(
            RenderSystem.getProjectionMatrix(),
            context.matrixStack().peek().getPositionMatrix(),
            (int) x, (int) y, 0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()
        ));
        this.hovered = this.getClosestObject(new Vector3d(pos.x, pos.y, pos.z), this.mouseDirection);

        RenderSystem.enableDepthTest();

        for (ModelBlockEntity entity : this.modelBlocks.getList())
        {
            BlockPos blockPos = entity.getPos();

            if (!this.isEditing(entity))
            {
                context.matrixStack().push();
                context.matrixStack().translate(blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z);

                /* The frame shows the block's actual hitbox (its body shape),
                 * so shaping the body gives immediate feedback in the world. */
                Box box = entity.getShape().getBoundingBox();

                if (this.hovered == entity || entity == this.modelBlock)
                {
                    Draw.renderBox(context.matrixStack(), box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, 0, 0.5F, 1F);
                }
                else
                {
                    Draw.renderBox(context.matrixStack(), box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ);
                }

                context.matrixStack().pop();
            }
        }

        RenderSystem.disableDepthTest();

        this.renderGizmo(context, pos);
    }

    private ModelBlockEntity getClosestObject(Vector3d finalPosition, Vector3f mouseDirection)
    {
        ModelBlockEntity closest = null;

        for (ModelBlockEntity object : this.modelBlocks.getList())
        {
            AABB aabb = this.getHitbox(object);

            if (aabb.intersectsRay(finalPosition, mouseDirection))
            {
                if (closest == null)
                {
                    closest = object;
                }
                else
                {
                    AABB aabb2 = this.getHitbox(closest);

                    if (finalPosition.distanceSquared(aabb.x, aabb.y, aabb.z) < finalPosition.distanceSquared(aabb2.x, aabb2.y, aabb2.z))
                    {
                        closest = object;
                    }
                }
            }
        }
        return closest;
    }

    private AABB getHitbox(ModelBlockEntity closest)
    {
        BlockPos pos = closest.getPos();
        Box box = closest.getShape().getBoundingBox();

        return new AABB(
            pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
            box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ
        );
    }

    public boolean isEditing(ModelBlockEntity entity)
    {
        if (this.modelBlock == entity)
        {
            List<UIFormPalette> children = this.getChildren(UIFormPalette.class);

            if (!children.isEmpty())
            {
                return children.get(0).editor.isEditing();
            }
        }

        return false;
    }
}