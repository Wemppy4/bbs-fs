package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.graphics.OptiFineShaders;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import org.lwjgl.opengl.GL11;

/** Shader toggling and state checks for the isolated Forge test client only. */
public final class OptiFineProbe
{
    public static JsonObject handle(JsonObject request) throws Exception
    {
        JsonObject out = new JsonObject();
        Class<?> shaders;
        try { shaders = Class.forName("net.optifine.shaders.Shaders"); }
        catch (ClassNotFoundException absent) { out.addProperty("present", false); out.addProperty("ok", true); return out; }
        if (request.has("pack"))
        {
            shaders.getMethod("setShaderPack", String.class).invoke(null, request.get("pack").getAsString());
            shaders.getMethod("uninit").invoke(null);
        }
        out.addProperty("present", true);
        out.addProperty("pack", (String) shaders.getMethod("getShaderPackName").invoke(null));
        boolean loaded = OptiFineShaders.isLoaded();
        out.addProperty("loaded", loaded);
        for (String name : new String[]{"isRenderingWorld", "isShadowPass", "isRenderingDfb", "isShaderPackInitialized"})
            out.addProperty(name, shaders.getField(name).getBoolean(null));
        for (String name : new String[]{"renderWidth", "renderHeight", "activeProgramID"})
            out.addProperty(name, shaders.getField(name).getInt(null));
        boolean nested;
        try (OptiFineShaders.LocalPass outer = OptiFineShaders.localPass())
        {
            nested = loaded == OptiFineShaders.isLoaded() && !OptiFineShaders.isWorldPass();
            try (OptiFineShaders.LocalPass inner = OptiFineShaders.localPass())
            {
                nested &= loaded == OptiFineShaders.isLoaded() && !OptiFineShaders.isWorldPass();
            }
            nested &= loaded == OptiFineShaders.isLoaded() && !OptiFineShaders.isWorldPass();
        }
        out.addProperty("nestedScopeRestored", nested && loaded == OptiFineShaders.isLoaded());
        out.addProperty("lightmap", RenderSystem.nativeLightmap());
        out.addProperty("correctLightmap", RenderSystem.getShaderTexture(2) == RenderSystem.nativeLightmap());
        out.addProperty("glError", GL11.glGetError());
        out.addProperty("ok", true);
        return out;
    }
    private OptiFineProbe() {}
}
