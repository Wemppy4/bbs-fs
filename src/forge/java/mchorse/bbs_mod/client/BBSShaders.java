package mchorse.bbs_mod.client;

import mchorse.bbs_mod.graphics.shader.ShaderProgram;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.render.VertexFormats;
import java.io.IOException;
import java.util.*;

/** The original BBS shader catalog, loaded into the native OpenGL context. */
public final class BBSShaders
{
    private static final Map<String,ShaderProgram> programs=new LinkedHashMap<>();
    private static boolean loaded;
    private BBSShaders() {}
    public static synchronized void setup()
    {
        Map<String,ShaderProgram> next=new LinkedHashMap<>();
        try
        {
            next.put("model",new ShaderProgram("model",VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL));
            for(String name:Arrays.asList("multilink","subtitles","selection","picker_preview"))
                next.put(name,new ShaderProgram(name,VertexFormats.POSITION_TEXTURE_COLOR));
            for(String name:Arrays.asList("picker_billboard","picker_models"))
                next.put(name,new ShaderProgram(name,VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL));
            next.put("picker_billboard_no_shading",new ShaderProgram("picker_billboard_no_shading",VertexFormats.POSITION_TEXTURE_LIGHT_COLOR));
            for(String name:Arrays.asList("picker_particles"))
                next.put(name,new ShaderProgram(name,VertexFormats.POSITION_COLOR_TEXTURE_LIGHT));
        }
        catch(IOException error)
        {
            for(ShaderProgram shader:next.values())shader.close();
            throw new IllegalStateException("Unable to initialize BBS shader programs",error);
        }
        /* Optional cosmetics fail together, without discarding model and bone-picking programs. */
        Map<String,ShaderProgram> cosmetic=new LinkedHashMap<>();
        try
        {
            cosmetic.put("pixelart",new ShaderProgram("pixelart",VertexFormats.POSITION_TEXTURE_COLOR));
            cosmetic.put("pixelart_text",new ShaderProgram("pixelart_text",VertexFormats.POSITION_COLOR_TEXTURE_LIGHT));
            cosmetic.put("pixelart_text_intensity",new ShaderProgram("pixelart_text_intensity",VertexFormats.POSITION_COLOR_TEXTURE_LIGHT));
            next.putAll(cosmetic);
        }
        catch(IOException error)
        {
            for(ShaderProgram shader:cosmetic.values())shader.close();
            BBSMod.LOGGER.warn("Optional pixel-art shaders are unavailable; using the native UI renderer",error);
        }
        for(ShaderProgram shader:programs.values())shader.close();
        programs.clear(); programs.putAll(next); loaded=true;
    }
    private static ShaderProgram get(String name){if(!loaded)setup();return programs.get(name);}
    public static ShaderProgram getModel(){return get("model");}
    public static ShaderProgram getMultilinkProgram(){return get("multilink");}
    public static ShaderProgram getSubtitlesProgram(){return get("subtitles");}
    public static ShaderProgram getSelectionProgram(){return get("selection");}
    public static ShaderProgram getPixelArtProgram(){return get("pixelart");}
    public static ShaderProgram getPixelArtTextProgram(){return get("pixelart_text");}
    public static ShaderProgram getPixelArtTextIntensityProgram(){return get("pixelart_text_intensity");}
    public static ShaderProgram getPickerPreviewProgram(){return get("picker_preview");}
    public static ShaderProgram getPickerBillboardProgram(){return get("picker_billboard");}
    public static ShaderProgram getPickerBillboardNoShadingProgram(){return get("picker_billboard_no_shading");}
    public static ShaderProgram getPickerParticlesProgram(){return get("picker_particles");}
    public static ShaderProgram getPickerModelsProgram(){return get("picker_models");}
}
