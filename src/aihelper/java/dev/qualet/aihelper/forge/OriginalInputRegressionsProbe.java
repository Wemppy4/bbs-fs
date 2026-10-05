package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forge.GlobalKeybinds;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.UIOrder;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeParameters;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/** Original widgets only; key presses come through the helper's native GuiScreen route. */
public final class OriginalInputRegressionsProbe extends UIBaseMenu
{
    private static OriginalInputRegressionsProbe active;
    private final GuiScreen previous;
    private final Keyframe<Double> keyframe;
    private final UIOrder[] orders = new UIOrder[3];
    private final ValueOrder[] values = {BBSSettings.translateHotkeyOrder, BBSSettings.scaleHotkeyOrder, BBSSettings.rotateHotkeyOrder};

    private OriginalInputRegressionsProbe()
    {
        this.previous = Minecraft.getMinecraft().currentScreen;
        KeyframeChannel<Double> channel = new KeyframeChannel<>("input_regression", KeyframeFactories.DOUBLE);
        this.keyframe = channel.get(channel.insert(0F, 0D));
        channel.insert(20F, 1D);
        this.keyframe.getInterpolation().setInterp(Interpolations.CONST);
        UIKeyframes view = new UIKeyframes(null);
        UIKeyframeSheet sheet = new UIKeyframeSheet(Colors.RED, channel, null);
        view.addSheet(sheet);
        sheet.selection.add(this.keyframe);
        view.selectTrack(sheet);
        view.relative(this.main).xy(8, 152).w(1F, -158).h(1F, -160);
        UIKeyframeParameters parameters = new UIKeyframeParameters(this.keyframe, view);
        parameters.relative(this.main).x(1F, -144).y(152).w(136);
        this.main.add(view, parameters);
        for (int i = 0; i < this.orders.length; i++)
        {
            this.orders[i] = new UIOrder(this.values[i]);
            this.orders[i].relative(this.main).xy(16, 16 + i * 36).h(24);
            this.main.add(this.orders[i]);
        }
    }

    public static JsonObject run(JsonObject request) throws Exception
    {
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("prepare"))
        {
            if (active != null) throw new IllegalStateException("Input fixture already open");
            active = new OriginalInputRegressionsProbe();
            UIScreen.open(active);
        }
        else if (op.equals("camera"))
        {
            UICameraUtils.interps(active.context, Interpolations.MAP.values(), active.keyframe.getInterpolation().getInterp(),
                interpolation -> active.keyframe.getInterpolation().setInterp(interpolation));
        }
        else if (op.equals("cleanup") && active != null)
        {
            Minecraft.getMinecraft().displayGuiScreen(active.previous);
            active = null;
        }
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        if (active != null) active.snapshot(result);
        return result;
    }

    private void snapshot(JsonObject result) throws Exception
    {
        result.addProperty("interpolation", this.keyframe.getInterpolation().getKey());
        result.addProperty("contextMenu", this.context.contextMenu != null);
        result.addProperty("width", this.width);
        result.addProperty("height", this.height);
        result.addProperty("glError", GL11.glGetError());
        result.addProperty("dashboardDefault", GlobalKeybinds.DASHBOARD.getKeyCodeDefault());
        result.addProperty("dashboardCurrent", GlobalKeybinds.DASHBOARD.getKeyCode());
        int savedKey = GlobalKeybinds.DASHBOARD.getKeyCode();
        try
        {
            GlobalKeybinds.DASHBOARD.setKeyCode(Keyboard.KEY_R);
            Method migrate = GlobalKeybinds.class.getDeclaredMethod("migrateLegacyDashboard", Minecraft.class);
            migrate.setAccessible(true);
            migrate.invoke(new GlobalKeybinds(), Minecraft.getMinecraft());
            result.addProperty("customRPreserved", GlobalKeybinds.DASHBOARD.getKeyCode() == Keyboard.KEY_R);
        }
        finally { GlobalKeybinds.DASHBOARD.setKeyCode(savedKey); KeyBinding.resetKeyBindingArrayAndHash(); }
        Set<String> categories = new LinkedHashSet<>();
        for (KeyBinding binding : Minecraft.getMinecraft().gameSettings.keyBindings)
            if (binding.getKeyDescription().startsWith("key.bbs.")) categories.add(binding.getKeyCategory());
        JsonArray categoryList = new JsonArray();
        for (String category : categories) categoryList.add(category);
        result.add("categories", categoryList);
        JsonArray rows = new JsonArray();
        for (int i = 0; i < this.orders.length; i++)
        {
            JsonObject row = new JsonObject();
            row.addProperty("x", this.orders[i].area.x); row.addProperty("y", this.orders[i].area.y);
            row.addProperty("w", this.orders[i].area.w); row.addProperty("h", this.orders[i].area.h);
            row.addProperty("xColor", this.values[i].getColor("x"));
            row.addProperty("yColor", this.values[i].getColor("y"));
            row.addProperty("zColor", this.values[i].getColor("z"));
            rows.add(row);
        }
        result.add("orders", rows);
    }

    @Override public boolean canPause() { return false; }
    @Override protected void preRenderMenu(UIRenderingContext context)
    {
        context.batcher.box(0, 0, this.width, this.height, BBSSettings.baseSurface());
    }
}
