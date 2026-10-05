package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.api.client.events.FormRenderEvents;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in, read-only snapshots at actual form render boundaries, rather than between frames. */
public final class DepthStateProbe
{
    private static final Map<String, JsonObject> states = new LinkedHashMap<>();
    private static final FloatBuffer range = BufferUtils.createFloatBuffer(16);
    private static boolean registered, active;
    private static int samples, limit;

    public static JsonObject run(JsonObject request)
    {
        String op = request.has("op") ? request.get("op").getAsString() : "status";
        if (op.equals("start"))
        {
            if (!registered)
            {
                FormRenderEvents.BEFORE.register((form, context) -> sample("before", form, context));
                FormRenderEvents.AFTER.register((form, context) -> sample("after", form, context));
                registered = true;
            }
            states.clear(); samples = 0;
            limit = Math.max(1, Math.min(100000, request.has("limit") ? request.get("limit").getAsInt() : 2000));
            active = true;
        }
        else if (op.equals("stop")) active = false;
        JsonObject out = new JsonObject();
        out.addProperty("ok", true); out.addProperty("active", active);
        out.addProperty("samples", samples);
        JsonArray entries = new JsonArray();
        for (JsonObject value : states.values()) entries.add(new com.google.gson.JsonParser().parse(value.toString()));
        out.add("states", entries);
        return out;
    }

    private static void sample(String boundary, Form form, FormRenderingContext context)
    {
        if (!active) return;
        JsonObject state = new JsonObject();
        state.addProperty("boundary", boundary);
        state.addProperty("form", form.getClass().getSimpleName());
        state.addProperty("type", String.valueOf(context.type));
        state.addProperty("ui", context.ui); state.addProperty("viewport", context.modelRenderer);
        state.addProperty("picking", context.isPicking());
        state.addProperty("screen", Minecraft.getMinecraft().currentScreen == null ? "world"
            : Minecraft.getMinecraft().currentScreen.getClass().getSimpleName());
        state.addProperty("test", GL11.glIsEnabled(GL11.GL_DEPTH_TEST));
        state.addProperty("write", GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        state.addProperty("func", GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        state.addProperty("bits", GL11.glGetInteger(GL11.GL_DEPTH_BITS));
        state.addProperty("fbo", GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
        range.clear(); GL11.glGetFloat(GL11.GL_DEPTH_RANGE, range);
        state.addProperty("near", range.get(0)); state.addProperty("far", range.get(1));
        String key = state.toString();
        JsonObject existing = states.get(key);
        if (existing != null) existing.addProperty("count", existing.get("count").getAsInt() + 1);
        else if (states.size() < 128) { state.addProperty("count", 1); states.put(key, state); }
        if (++samples >= limit) active = false;
    }
}
