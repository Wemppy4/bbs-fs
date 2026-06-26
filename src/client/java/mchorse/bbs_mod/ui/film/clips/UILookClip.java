package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.modifiers.LookClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class UILookClip extends UIClip<LookClip>
{
    public UIButton selector;
    public UIToggle relative;
    public UIPointModule offset;
    public UIToggle atBlock;
    public UIPointModule block;
    public UIToggle forward;

    public UIElement row;

    public UILookClip(LookClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);

        this.block = new UIPointModule(editor, UIKeys.CAMERA_PANELS_BLOCK).contextMenu();
        this.block.context((menu) ->
        {
            menu.action(Icons.VISIBLE, UIKeys.CAMERA_PANELS_CONTEXT_LOOK_COORDS, () -> this.rayTrace(false));
            menu.action(Icons.BLOCK, UIKeys.CAMERA_PANELS_CONTEXT_LOOK_BLOCK, () -> this.rayTrace(true));
        });
        this.offset = new UIPointModule(editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();

        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, false, (b) -> this.clip.relative.set(b.getValue()));
        this.relative.tooltip(UIKeys.CAMERA_PANELS_RELATIVE_TOOLTIP);

        this.atBlock = new UIToggle(UIKeys.CAMERA_PANELS_AT_BLOCK, false, (b) -> this.clip.atBlock.set(b.getValue()));

        this.forward = new UIToggle(UIKeys.CAMERA_PANELS_FORWARD, false, (b) -> this.clip.forward.set(b.getValue()));
        this.forward.tooltip(UIKeys.CAMERA_PANELS_FORWARD_TOOLTIP);
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.relative));
        this.panels.add(this.offset);
        this.panels.add(this.atBlock);
        this.panels.add(this.block);
        this.panels.add(this.forward);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.block.fill(this.clip.block);
        this.offset.fill(this.clip.offset);
        this.relative.setValue(this.clip.relative.get());
        this.atBlock.setValue(this.clip.atBlock.get());
        this.forward.setValue(this.clip.forward.get());
    }

    private void rayTrace(boolean center)
    {
        Camera camera = this.editor.getCamera();
        World world = MinecraftClient.getInstance().world;

        HitResult result = RayTracing.rayTraceEntity(world, camera, 128);

        if (center && result instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS)
        {
            BlockPos pos = bhr.getBlockPos();

            BaseValue.edit(this.clip.block, (block) -> block.get().set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            this.fillData();
        }
        else if (!center && result instanceof EntityHitResult ehr && ehr.getType() != HitResult.Type.MISS)
        {
            Vec3d vec = ehr.getPos();

            BaseValue.edit(this.clip.block, (block) -> block.get().set(vec.x, vec.y, vec.z));
            this.fillData();
        }
    }
}