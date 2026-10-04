package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.CurveClientClip;
import mchorse.bbs_mod.camera.clips.misc.CurveClip;
import mchorse.bbs_mod.camera.controller.CameraWorkCameraController;
import mchorse.bbs_mod.ui.film.clips.UICurveClip;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.iris.*;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import java.nio.*;
import java.util.*;

/** Real clip interpolation -> native OptiFine program uniforms. Test-only camera
 * controller never writes to a user's Film or pack option settings. */
public final class OriginalShaderCurvesProbe
{
    private static Fixture fixture;
    private static final List<String> picked = new ArrayList<>();

    public static JsonObject handle(JsonObject request)
    {
        String action = request.has("action") ? request.get("action").getAsString() : "state";
        if (action.equals("stop")) stop();
        if (action.equals("start"))
        {
            stop();
            String option = request.get("option").getAsString();
            if (!ShaderCurves.variableMap.containsKey(option)&&!option.equals(ShaderCurves.SUN_HORIZONTAL_ROTATION)) throw new IllegalArgumentException("No shader curve " + option);
            fixture = new Fixture(option, request.get("from").getAsDouble(), request.get("to").getAsDouble());
            BBSModClient.getCameraController().add(fixture);
            fixture.evaluate();
        }
        if (action.equals("seek"))
        {
            if (fixture == null) throw new IllegalStateException("Start the shader curve fixture first");
            fixture.tick = request.get("tick").getAsInt();
            fixture.evaluate();
        }
        if (action.equals("picker"))
        {
            if (UIScreen.getCurrentMenu() == null) throw new IllegalStateException("Open Dashboard first");
            picked.clear();
            UICurveClip.offerCurveKeys(UIScreen.getCurrentMenu().context, picked, picked::add);
        }
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("loaded", OptiFineShaderOptions.available());
        out.addProperty("sources", ShaderCurves.getProcessedSources());
        out.addProperty("uploads", ShaderCurves.getUploads());
        out.addProperty("curves", ShaderCurves.variableMap.size());
        out.addProperty("fixture", fixture != null);
        if (fixture != null) out.addProperty("tick", fixture.tick);
        ShaderMenu menu = OptiFineShaderOptions.menu();
        if (menu != null)
        {
            out.addProperty("screens", menu.subScreens.size());
            out.addProperty("menuCurves", menu.curvableInMenu.size());
            out.addProperty("menuColumns", menu.mainScreen.columnCount);
        }
        JsonArray options = new JsonArray();
        for (ShaderCurves.ShaderVariable variable : ShaderCurves.variableMap.values())
        {
            JsonObject option = new JsonObject();
            option.addProperty("id", variable.name);
            option.addProperty("integer", variable.integer);
            option.addProperty("default", variable.defaultValue);
            option.addProperty("value", variable.getValue());
            JsonArray gpu = new JsonArray();
            for (int program : ShaderCurves.getPrograms())
            {
                if (!GL20.glIsProgram(program)) continue;
                int location = GL20.glGetUniformLocation(program, variable.uniformName);
                if (location < 0) continue;
                JsonObject sample = new JsonObject();
                sample.addProperty("program", program);
                if (variable.integer)
                {
                    IntBuffer value = BufferUtils.createIntBuffer(16);
                    GL20.glGetUniform(program, location, value);
                    sample.addProperty("value", value.get(0));
                }
                else
                {
                    FloatBuffer value = BufferUtils.createFloatBuffer(16);
                    GL20.glGetUniform(program, location, value);
                    sample.addProperty("value", value.get(0));
                }
                gpu.add(sample);
            }
            option.add("gpu", gpu);
            options.add(option);
        }
        out.add("options", options);
        JsonArray selected = new JsonArray();
        for (String channel : picked) selected.add(channel);
        out.add("picked", selected);
        out.addProperty("glError", GL11.glGetError());
        out.addProperty("sunYaw",mchorse.bbs_mod.client.BBSRendering.getSunHorizontalRotation());
        out.addProperty("shadowCameraUpdates",OptiFineSunRotation.getCameraUpdates());
        out.addProperty("shadowLightUpdates",OptiFineSunRotation.getLightUpdates());
        try
        {
            Class<?> shaders=Class.forName("net.optifine.shaders.Shaders");
            for(String name:new String[]{"shadowModelView","shadowModelViewInverse","shadowLightPositionVector"})
            {
                java.lang.reflect.Field field=shaders.getDeclaredField(name);field.setAccessible(true);Object value=field.get(null);
                JsonArray array=new JsonArray();
                if(value instanceof FloatBuffer){FloatBuffer b=((FloatBuffer)value).duplicate();for(int i=0;i<16;i++)array.add(b.get(i));}
                else for(float number:(float[])value)array.add(number);
                out.add(name,array);
            }
        }
        catch(ReflectiveOperationException absent){}
        return out;
    }

    private static void stop()
    {
        if (fixture != null) BBSModClient.getCameraController().remove(fixture);
        fixture = null;
    }

    private static final class Fixture extends CameraWorkCameraController
    {
        int tick;
        final Camera anchor=new Camera();
        Fixture(String option, double from, double to)
        {
            Camera camera=BBSModClient.getCameraController().camera;
            anchor.position.set(camera.position).add(3,1,0);anchor.rotation.set(camera.rotation);anchor.fov=camera.fov;
            CurveClientClip clip = new CurveClientClip();
            clip.tick.set(0); clip.duration.set(20);
            boolean sun=option.equals(ShaderCurves.SUN_HORIZONTAL_ROTATION);
            KeyframeChannel<Double> channel = clip.channels.addChannel(sun?option:CurveClip.SHADER_CURVES_PREFIX + option);
            channel.insert(0, from); channel.insert(10, to);
            if(sun)clip.channels.addChannel(ShaderCurves.SUN_ROTATION).insert(0,6D);
            Clips clips = new Clips("shader_probe", BBSMod.getFactoryCameraClips());
            clips.addClip(clip);
            setWork(clips);
        }
        void evaluate() { apply(null, tick, 0F); }
        @Override public void setup(Camera camera, float transition)
        {camera.position.set(anchor.position);camera.rotation.set(anchor.rotation);camera.fov=anchor.fov;apply(camera,tick,0F);}
        @Override public int getPriority() { return 100000; }
    }
}
