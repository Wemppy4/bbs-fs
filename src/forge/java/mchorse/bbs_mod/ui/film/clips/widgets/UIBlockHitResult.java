package mchorse.bbs_mod.ui.film.clips.widgets;

import mchorse.bbs_mod.actions.values.ValueBlockHitResult;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

public class UIBlockHitResult
{
    public UITrackpad x;
    public UITrackpad y;
    public UITrackpad z;
    public UITrackpad hitX;
    public UITrackpad hitY;
    public UITrackpad hitZ;
    public UICirculate direction;
    public UIToggle inside;

    private IUIClipsDelegate editor;
    private ValueBlockHitResult result;

    public UIBlockHitResult(IUIClipsDelegate editor)
    {
        this.editor = editor;

        this.x = new UITrackpad((v) -> this.editor.editMultiple(this.result.x, (x) -> x.set(v.intValue())));
        this.x.integer();
        this.y = new UITrackpad((v) -> this.editor.editMultiple(this.result.y, (y) -> y.set(v.intValue())));
        this.y.integer();
        this.z = new UITrackpad((v) -> this.editor.editMultiple(this.result.z, (z) -> z.set(v.intValue())));
        this.z.integer();
        this.hitX = new UITrackpad((v) -> this.editor.editMultiple(this.result.hitX, (hitX) -> hitX.set(v)));
        this.hitY = new UITrackpad((v) -> this.editor.editMultiple(this.result.hitY, (hitY) -> hitY.set(v)));
        this.hitZ = new UITrackpad((v) -> this.editor.editMultiple(this.result.hitZ, (hitZ) -> hitZ.set(v)));
        this.direction = new UICirculate((b) -> this.result.direction.set(b.getValue()));
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_DOWN);
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_UP);
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_NORTH);
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_SOUTH);
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_WEST);
        this.direction.addLabel(UIKeys.ACTIONS_BLOCK_DIRECTION_EAST);
        this.inside = new UIToggle(UIKeys.ACTIONS_BLOCK_INSIDE, (b) -> this.result.inside.set(b.getValue()));

        this.addBlockPositionContext(this.x);
        this.addBlockPositionContext(this.y);
        this.addBlockPositionContext(this.z);
    }

    public void fill(ValueBlockHitResult result)
    {
        this.result = result;

        this.x.setValue(this.result.x.get());
        this.y.setValue(this.result.y.get());
        this.z.setValue(this.result.z.get());
        this.hitX.setValue(this.result.hitX.get());
        this.hitY.setValue(this.result.hitY.get());
        this.hitZ.setValue(this.result.hitZ.get());
        this.direction.setValue(this.result.direction.get());
        this.inside.setValue(this.result.inside.get());
    }

    private void addBlockPositionContext(UITrackpad trackpad)
    {
        trackpad.context((menu) ->
        {
            menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POINT, Colors.POSITIVE, () ->
            {
                if (this.result == null) return;

                Map<String, Double> map = new LinkedHashMap<>();

                map.put("X", (double) this.result.x.get());
                map.put("Y", (double) this.result.y.get());
                map.put("Z", (double) this.result.z.get());

                Window.setClipboard(UICameraUtils.mapToString(map));
            });

            menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POINT, () ->
            {
                if (this.result == null) return;

                Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

                if (map.containsKey("x") && map.containsKey("y") && map.containsKey("z"))
                {
                    this.editor.editMultiple(this.result.x, (x) -> x.set(map.get("x").intValue()));
                    this.editor.editMultiple(this.result.y, (y) -> y.set(map.get("y").intValue()));
                    this.editor.editMultiple(this.result.z, (z) -> z.set(map.get("z").intValue()));
                    this.editor.fillData();
                }
            });

            menu.action(Icons.BLOCK, UIKeys.ACTIONS_BLOCK_POSITION_FROM_LOOK, () ->
            {
                if (this.result == null) return;

                Minecraft mc = Minecraft.getMinecraft();
                RayTraceResult result = mc == null ? null : mc.objectMouseOver;

                if (result != null && result.typeOfHit == RayTraceResult.Type.BLOCK)
                {
                    BlockPos pos = result.getBlockPos();

                    this.editor.editMultiple(this.result.x, (x) -> x.set(pos.getX()));
                    this.editor.editMultiple(this.result.y, (y) -> y.set(pos.getY()));
                    this.editor.editMultiple(this.result.z, (z) -> z.set(pos.getZ()));
                    this.editor.fillData();
                }
            });
        });
    }
}