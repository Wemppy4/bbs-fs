package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.*;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.*;
import mchorse.bbs_mod.utils.keyframes.*;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Pose;
import org.lwjgl.opengl.GL11;

/** Runs the actual timeline widget on disposable channels; never writes a user's film. */
public final class OriginalTimelineProbe extends UIBaseMenu
{
    private final UIKeyframes timeline = new UIKeyframes(null);
    private final UIIcon graph = UIKeyframes.modeButton(() -> timeline);
    private int changes;

    public OriginalTimelineProbe()
    {
        KeyframeChannel<Double> x = new KeyframeChannel<>("x", KeyframeFactories.DOUBLE);
        x.insert(0, 0D); x.insert(20, 4D); x.insert(40, -2D); x.insert(60, 0D);
        KeyframeChannel<Double> y = new KeyframeChannel<>("y", KeyframeFactories.DOUBLE);
        y.insert(0, 1D); y.insert(30, 3D); y.insert(60, 1D);
        timeline.addSheet(new UIKeyframeSheet("x", IKey.constant("Положение X"), 0xff7777, x, null));
        timeline.addSheet(new UIKeyframeSheet("y", IKey.constant("Положение Y"), 0x77ff77, y, null));
        ModelForm form = new ModelForm();
        form.model.set("helper_drone");
        KeyframeChannel<Pose> poses = new KeyframeChannel<>("pose", KeyframeFactories.POSE);
        Pose pose = new Pose();
        poses.insert(0, pose.copy());
        pose.getOrCreate("body").rotate.y = 35;
        poses.insert(40, pose.copy());
        UIKeyframeSheet sheet = new UIKeyframeSheet("pose", IKey.constant("Поза"), 0x77bbff, poses, form.pose);
        sheet.form = form;
        timeline.addSheet(sheet);
        timeline.relative(main).xy(8, 34).w(1F, -16).h(1F, -42);
        timeline.duration(() -> 80).changed(() -> changes++);
        graph.relative(main).xy(8, 8).wh(20, 20);
        main.add(timeline, graph);
    }

    @Override public boolean canPause() { return false; }
    @Override public void resize(int width, int height)
    {
        super.resize(width, height);
        timeline.resetView();
    }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0, 0, width, height, BBSSettings.baseSurface());
        render.batcher.text("BBS FS — исходный таймлайн (проверка)", 36, 14, -1);
    }

    public JsonObject snapshot()
    {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("graph", timeline.isEditing());
        result.addProperty("changes", changes);
        result.addProperty("glError", GL11.glGetError());
        result.addProperty("contextMenu", context.contextMenu != null);
        result.addProperty("overlays", overlay.getChildren().size());
        result.addProperty("width", width); result.addProperty("height", height);
        result.addProperty("mouseX", context.mouseX); result.addProperty("mouseY", context.mouseY);
        result.addProperty("interacting", timeline.isInteracting());
        result.add("button", area(graph.area));
        result.add("area", area(timeline.graphArea));
        JsonArray tracks = new JsonArray();
        for (UIKeyframeSheet sheet : timeline.getSheets())
        {
            JsonObject track = new JsonObject();
            track.addProperty("id", sheet.id);
            JsonArray keys = new JsonArray();
            for (Object item : sheet.channel.getKeyframes())
            {
                Keyframe key = (Keyframe) item;
                JsonObject point = new JsonObject();
                point.addProperty("tick", key.getTick());
                if (key.getValue() instanceof Number) point.addProperty("value", (Number) key.getValue());
                point.addProperty("enabled", key.isEnabled());
                point.addProperty("selected", sheet.selection.has(sheet.channel.indexOf(key)));
                point.addProperty("x", timeline.toGraphX(key.getTick()));
                int y = timeline.getGraph() instanceof UIKeyframeDopeSheet
                    ? ((UIKeyframeDopeSheet) timeline.getGraph()).getDopeSheetY(sheet)
                    : key.getValue() instanceof Number ? ((UIKeyframeGraph) timeline.getGraph()).toGraphY(((Number) key.getValue()).doubleValue()) : -1;
                point.addProperty("y", y);
                keys.add(point);
            }
            track.add("keys", keys); tracks.add(track);
        }
        result.add("tracks", tracks);
        return result;
    }
    private static JsonObject area(mchorse.bbs_mod.ui.utils.Area area)
    {
        JsonObject result = new JsonObject();
        result.addProperty("x", area.x); result.addProperty("y", area.y);
        result.addProperty("w", area.w); result.addProperty("h", area.h);
        return result;
    }
}
