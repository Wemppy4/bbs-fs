package mchorse.bbs_mod.graphics.shader;

import com.google.gson.*;
import mchorse.bbs_mod.graphics.render.RenderSystem;
import mchorse.bbs_mod.graphics.render.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Loads BBS's original JSON/GLSL programs on the native LWJGL2 context. */
public final class ShaderProgram implements AutoCloseable
{
    private static final Pattern IMPORT = Pattern.compile("^\\s*#moj_import\\s+[<\"]([^>\"]+)[>\"]\\s*$", Pattern.MULTILINE);
    private final VertexFormat format;
    private final Map<String, GlUniform> uniforms = new LinkedHashMap<>();
    private final Map<String, Integer> samplerLocations = new LinkedHashMap<>();
    private final Map<String, Integer> samplers = new HashMap<>();
    private final Deque<State> states = new ArrayDeque<>();
    private int id;
    private int blendSrc = GL11.GL_SRC_ALPHA, blendDst = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private int blendSrcAlpha = GL11.GL_SRC_ALPHA, blendDstAlpha = GL11.GL_ONE_MINUS_SRC_ALPHA;
    private int blendEquation = GL14.GL_FUNC_ADD;
    private boolean blend;
    public final GlUniform projectionMat, modelViewMat, viewRotationMat, fogStart, fogEnd, fogColor, fogShape, colorModulator, gameTime, textureMat;

    public ShaderProgram(String name, VertexFormat format) throws IOException
    {
        this.format = format;
        int vertex = 0, fragment = 0;
        try
        {
            JsonObject json = new JsonParser().parse(read("shaders/core/"+name+".json")).getAsJsonObject();
            vertex = compile(GL20.GL_VERTEX_SHADER, expand("shaders/core/"+json.get("vertex").getAsString()+".vsh", new HashSet<>()));
            fragment = compile(GL20.GL_FRAGMENT_SHADER, expand("shaders/core/"+json.get("fragment").getAsString()+".fsh", new HashSet<>()));
            this.id = GL20.glCreateProgram();
            GL20.glAttachShader(this.id, vertex); GL20.glAttachShader(this.id, fragment);
            // Layout, not declaration order, selects the integer/light/bone attributes.
            for (VertexFormat.Element element : format.elements)
                GL20.glBindAttribLocation(this.id, format.attribute(element.name), element.name);
            GL30.glBindFragDataLocation(this.id, 0, "fragColor");
            GL20.glLinkProgram(this.id);
            if (GL20.glGetProgrami(this.id, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
                throw new IOException(name+": "+GL20.glGetProgramInfoLog(this.id, 32768));
            if (json.has("uniforms")) for (JsonElement entry : json.getAsJsonArray("uniforms"))
            {
                JsonObject value = entry.getAsJsonObject(); String key = value.get("name").getAsString();
                int location = GL20.glGetUniformLocation(this.id, key);
                if (location < 0) continue;
                GlUniform uniform = new GlUniform(this, location, value.get("type").getAsString(), value.get("count").getAsInt());
                JsonArray array = value.getAsJsonArray("values");
                if (value.get("type").getAsString().equals("int"))
                {
                    int[] data = new int[array.size()];
                    for (int i=0;i<data.length;i++) data[i]=array.get(i).getAsInt();
                    uniform.set(data);
                }
                else
                {
                    float[] data = new float[array.size()];
                    for (int i=0;i<data.length;i++) data[i]=array.get(i).getAsFloat();
                    uniform.set(data);
                }
                this.uniforms.put(key, uniform);
            }
            if (json.has("samplers")) for (JsonElement entry : json.getAsJsonArray("samplers"))
            {
                String key = entry.getAsJsonObject().get("name").getAsString();
                int location = GL20.glGetUniformLocation(this.id, key);
                if (location >= 0) this.samplerLocations.put(key, location);
            }
            this.blend = json.has("blend");
            if (this.blend)
            {
                JsonObject value = json.getAsJsonObject("blend");
                this.blendSrc = factor(value, "srcrgb", GL11.GL_ONE);
                this.blendDst = factor(value, "dstrgb", GL11.GL_ZERO);
                this.blendSrcAlpha = factor(value, "srcalpha", this.blendSrc);
                this.blendDstAlpha = factor(value, "dstalpha", this.blendDst);
                String func = value.has("func") ? value.get("func").getAsString() : "add";
                this.blendEquation = func.equals("subtract") ? GL14.GL_FUNC_SUBTRACT : func.equals("reversesubtract") ? GL14.GL_FUNC_REVERSE_SUBTRACT : func.equals("min") ? GL14.GL_MIN : func.equals("max") ? GL14.GL_MAX : GL14.GL_FUNC_ADD;
            }
        }
        catch (RuntimeException | IOException error)
        {
            if (this.id != 0) GL20.glDeleteProgram(this.id);
            this.id=0;
            throw new IOException("Unable to load BBS shader "+name, error);
        }
        finally
        {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
        this.projectionMat=this.getUniform("ProjMat"); this.modelViewMat=this.getUniform("ModelViewMat");
        this.viewRotationMat=this.getUniform("IViewRotMat"); this.fogStart=this.getUniform("FogStart");
        this.fogEnd=this.getUniform("FogEnd"); this.fogColor=this.getUniform("FogColor"); this.fogShape=this.getUniform("FogShape");
        this.colorModulator=this.getUniform("ColorModulator"); this.gameTime=this.getUniform("GameTime"); this.textureMat=this.getUniform("TextureMat");
    }
    private static int factor(JsonObject json, String key, int fallback)
    {
        if (!json.has(key)) return fallback;
        switch (json.get(key).getAsString().toLowerCase(Locale.ROOT).replace("_", ""))
        {
            case "0": case "zero": return GL11.GL_ZERO;
            case "1": case "one": return GL11.GL_ONE;
            case "srccolor": return GL11.GL_SRC_COLOR;
            case "1-srccolor": return GL11.GL_ONE_MINUS_SRC_COLOR;
            case "dstcolor": return GL11.GL_DST_COLOR;
            case "1-dstcolor": return GL11.GL_ONE_MINUS_DST_COLOR;
            case "srcalpha": return GL11.GL_SRC_ALPHA;
            case "1-srcalpha": return GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "dstalpha": return GL11.GL_DST_ALPHA;
            case "1-dstalpha": return GL11.GL_ONE_MINUS_DST_ALPHA;
            default: throw new IllegalArgumentException("Unknown blend factor "+json.get(key));
        }
    }
    private static String read(String path) throws IOException
    {
        ResourceLocation resource = new ResourceLocation("bbs", path);
        try (IResource file = Minecraft.getMinecraft().getResourceManager().getResource(resource);
             Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
        {
            StringBuilder output=new StringBuilder(); char[] chunk=new char[4096]; int count;
            while ((count=reader.read(chunk))!=-1) output.append(chunk,0,count);
            return output.toString();
        }
    }
    private static String expand(String path, Set<String> included) throws IOException
    {
        if (!included.add(path)) return "\n";
        String source=read(path); Matcher matcher=IMPORT.matcher(source); StringBuffer result=new StringBuffer();
        while(matcher.find())
        {
            String child=expand("shaders/include/"+matcher.group(1), included).replaceAll("(?m)^\\s*#version[^\\r\\n]*", "");
            matcher.appendReplacement(result, Matcher.quoteReplacement("\n"+child+"\n"));
        }
        matcher.appendTail(result); return result.toString();
    }
    private static int compile(int type, String source) throws IOException
    {
        int shader=GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source); GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS)==GL11.GL_FALSE)
        {
            String log=GL20.glGetShaderInfoLog(shader,32768); GL20.glDeleteShader(shader); throw new IOException(log);
        }
        return shader;
    }
    public int getId() { return this.id; }
    public VertexFormat getFormat() { return this.format; }
    public GlUniform getUniform(String name) { return this.uniforms.get(name); }
    public void addSampler(String name,int texture) { this.samplers.put(name,texture); }
    boolean isBound() { return this.id != 0 && !this.states.isEmpty() && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)==this.id; }
    public void bind()
    {
        if (this.id == 0) throw new IllegalStateException("Shader is closed");
        State state = new State();
        this.states.push(state);
        try
        {
        // Resolve ALL logical textures before unit1 is temporarily used for overlay.
        Map<Integer,Integer> desired=new LinkedHashMap<>();
        for (String name : this.samplerLocations.keySet())
        {
            int unit=Integer.parseInt(name.substring("Sampler".length()));
            desired.put(unit,this.samplers.containsKey(name)?this.samplers.get(name):RenderSystem.getShaderTexture(unit));
        }
            GL20.glUseProgram(this.id);
            for (Map.Entry<Integer,Integer> entry:desired.entrySet())
            {
                int unit=entry.getKey(); GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);
                state.textures.put(unit,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
                GlStateManager.bindTexture(entry.getValue());
                GL20.glUniform1i(this.samplerLocations.get("Sampler"+unit),unit);
            }
            GlStateManager.setActiveTexture(state.activeTexture);
            if (this.blend)
            {
                GlStateManager.enableBlend(); GL14.glBlendEquation(this.blendEquation);
                GlStateManager.tryBlendFuncSeparate(this.blendSrc,this.blendDst,this.blendSrcAlpha,this.blendDstAlpha);
            }
            else GlStateManager.disableBlend();
            for (GlUniform uniform:this.uniforms.values()) uniform.upload();
        }
        catch (RuntimeException error) { this.unbind(); throw error; }
    }
    public void unbind()
    {
        if (this.states.isEmpty()) throw new IllegalStateException("Shader is not bound");
        State state=this.states.pop();
        for (Map.Entry<Integer,Integer> entry:state.textures.entrySet())
        { GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+entry.getKey()); GlStateManager.bindTexture(entry.getValue()); }
        GlStateManager.setActiveTexture(state.activeTexture); GL20.glUseProgram(state.program);
        GL20.glBlendEquationSeparate(state.equation,state.alphaEquation);
        GlStateManager.tryBlendFuncSeparate(state.src,state.dst,state.srcAlpha,state.dstAlpha);
        if (state.blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
    }
    @Override public void close()
    {
        while (!this.states.isEmpty()) this.unbind();
        if (this.id != 0) GL20.glDeleteProgram(this.id);
        this.id=0;
    }
    private static final class State
    {
        final int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), activeTexture=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final boolean blend=GL11.glIsEnabled(GL11.GL_BLEND);
        final int equation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), alphaEquation=GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        final int src=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final Map<Integer,Integer> textures=new LinkedHashMap<>();
    }
}
