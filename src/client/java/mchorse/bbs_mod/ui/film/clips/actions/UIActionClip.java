package mchorse.bbs_mod.ui.film.clips.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public abstract class UIActionClip <T extends ActionClip> extends UIClip<T>
{
    public UITrackpad frequency;

    public UIActionClip(T clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.frequency = new UITrackpad((v) -> this.editor.editMultiple(this.clip.frequency, (frequency) -> frequency.set(v.intValue())));
        this.frequency.limit(0).integer();
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.ACTIONS_FREQUENCY, this.frequency).marginTop(UIConstants.SECTION_GAP));
    }

    @Override
    protected void addEnvelopes()
    {}

    @Override
    public void fillData()
    {
        super.fillData();

        this.frequency.setValue(this.clip.frequency.get());
    }

    protected void addBlockPositionContext(UITrackpad x, UITrackpad y, UITrackpad z, IntSupplier getX, IntSupplier getY, IntSupplier getZ, IntConsumer setX, IntConsumer setY, IntConsumer setZ)
    {
        x.context((menu) -> this.populateBlockPositionContext(menu, getX, getY, getZ, setX, setY, setZ));
        y.context((menu) -> this.populateBlockPositionContext(menu, getX, getY, getZ, setX, setY, setZ));
        z.context((menu) -> this.populateBlockPositionContext(menu, getX, getY, getZ, setX, setY, setZ));
    }

    private void populateBlockPositionContext(ContextMenuManager menu, IntSupplier getX, IntSupplier getY, IntSupplier getZ, IntConsumer setX, IntConsumer setY, IntConsumer setZ)
    {
        menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POINT, Colors.POSITIVE, () ->
        {
            Map<String, Double> map = new LinkedHashMap<>();

            map.put("X", (double) getX.getAsInt());
            map.put("Y", (double) getY.getAsInt());
            map.put("Z", (double) getZ.getAsInt());

            Window.setClipboard(UICameraUtils.mapToString(map));
        });

        menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POINT, () ->
        {
            Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

            if (map.containsKey("x") && map.containsKey("y") && map.containsKey("z"))
            {
                setX.accept(map.get("x").intValue());
                setY.accept(map.get("y").intValue());
                setZ.accept(map.get("z").intValue());
                this.editor.fillData();
            }
        });

        menu.action(Icons.BLOCK, UIKeys.ACTIONS_BLOCK_POSITION_FROM_LOOK, () ->
        {
            MinecraftClient mc = MinecraftClient.getInstance();
            HitResult result = mc == null ? null : mc.crosshairTarget;

            if (result instanceof BlockHitResult bhr && result.getType() == HitResult.Type.BLOCK)
            {
                BlockPos pos = bhr.getBlockPos();

                setX.accept(pos.getX());
                setY.accept(pos.getY());
                setZ.accept(pos.getZ());
                this.editor.fillData();
            }
        });
    }
}