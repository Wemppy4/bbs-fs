package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.BBSMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Optional G5 shader API. No OptiFine classes are linked into the distributable. */
public final class OptiFineShaders
{
    private static final Access API = discover();
    private OptiFineShaders() {}

    private static Access discover()
    {
        try { return new Access(Class.forName("net.optifine.shaders.Shaders")); }
        catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException | LinkageError error)
        {
            BBSMod.LOGGER.warn("OptiFine shader integration is unavailable", error);
            return null;
        }
    }

    public static boolean isLoaded() { return API != null && flag(API.loaded); }
    public static float blockShade(int face)
    {
        return isLoaded() ? ((Number) get(API.blockLight[face])).floatValue() : new float[]{.6F, .8F, .5F}[face];
    }
    public static boolean isShadowPass() { return isLoaded() && flag(API.shadow); }
    public static boolean isWorldPass()
    {
        return isLoaded() && flag(API.world) && (flag(API.deferred) || flag(API.shadow));
    }
    public static void resize() { if (isLoaded()) invoke(API.resize); }

    /** Suspend pack programs, never shaderPackLoaded: BakedQuad and BufferBuilder use
     * that flag to agree on their vertex stride, including outside world rendering. */
    public static LocalPass localPass() { return new LocalPass(); }
    public static final class LocalPass implements AutoCloseable
    {
        private final boolean loaded = isLoaded();
        private final Object previous;
        private final int program;
        private final boolean world, deferred, shadow, glowing;
        private boolean closed;
        private LocalPass()
        {
            if (!loaded)
            {
                previous = null;
                program = 0;
                world = deferred = shadow = glowing = false;
                return;
            }
            previous = get(API.active);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            world = flag(API.world);
            deferred = flag(API.deferred);
            shadow = flag(API.shadow);
            glowing = flag(API.glowing);
            set(API.world, false);
            set(API.deferred, false);
            set(API.shadow, false);
            set(API.glowing, false);
            /* ProgramNone also detaches OptiFine's cached uniform locations. Merely
             * glUseProgram(0) leaves those hooks pointing into an unrelated BBS shader. */
            invoke(API.use, get(API.none));
            GL20.glUseProgram(0);
        }
        @Override public void close()
        {
            if (!closed && loaded)
            {
                set(API.world, world);
                set(API.deferred, deferred);
                set(API.shadow, shadow);
                set(API.glowing, glowing);
                invoke(API.use, previous);
                GL20.glUseProgram(program);
            }
            closed = true;
        }
    }

    public static EntityPass entities() { return new EntityPass(API.entities); }
    /** Vanilla particles use gbuffers_textured_lit; the shadow program remains
     * owned by OptiFine when a shader pack elects to include these draws. */
    public static EntityPass particles() { return new EntityPass(API.texturedLit); }
    public static EntityPass blocks(net.minecraft.util.BlockRenderLayer layer)
    {
        switch (layer)
        {
            case CUTOUT_MIPPED: return new EntityPass(API.terrainCutoutMip);
            case CUTOUT: return new EntityPass(API.terrainCutout);
            case TRANSLUCENT: return new EntityPass(API.water);
            default: return new EntityPass(API.terrainSolid);
        }
    }
    /** Standard shader-pack hurt/colour overlay, restored independently for every program used. */
    public static OverlayPass overlay(float r, float g, float b, float a) { return new OverlayPass(r,g,b,a); }
    public static final class OverlayPass implements AutoCloseable
    {
        private final float r,g,b,a;
        private final Map<Integer,float[]> previous = new LinkedHashMap<>();
        private final Object uniform = get(API.entityColor);
        private OverlayPass(float r,float g,float b,float a) { this.r=r;this.g=g;this.b=b;this.a=a;apply(); }
        public void apply()
        {
            if (isShadowPass()) return;
            int program = (Integer) invokeOn(API.uniformProgram,uniform);
            if (program <= 0 || program != GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)) return;
            if (!previous.containsKey(program))
            {
                /* OptiFine's cache begins with -Float.MAX_VALUE, not the actual GL
                 * default. Restoring that sentinel makes the following models black. */
                int location=GL20.glGetUniformLocation(program,"entityColor");
                java.nio.FloatBuffer value=org.lwjgl.BufferUtils.createFloatBuffer(4);
                if(location>=0)GL20.glGetUniform(program,location,value);
                previous.put(program,new float[]{value.get(0),value.get(1),value.get(2),value.get(3)});
            }
            invokeOn(API.colorSet,uniform,r,g,b,a);
        }
        @Override public void close()
        {
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int uniformProgram = (Integer) invokeOn(API.uniformProgram,uniform);
            for (Map.Entry<Integer,float[]> entry : previous.entrySet())
            {
                GL20.glUseProgram(entry.getKey()); invokeOn(API.uniformSetProgram,uniform,entry.getKey());
                float[] value=entry.getValue(); invokeOn(API.colorSet,uniform,value[0],value[1],value[2],value[3]);
            }
            invokeOn(API.uniformSetProgram,uniform,uniformProgram); GL20.glUseProgram(program);
        }
    }
    public static final class EntityPass implements AutoCloseable
    {
        private final Object previous = get(API.active);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private EntityPass(Field target)
        {
            Object selected = get(target);
            /* Keep the native hand and shadow program when those passes own the draw. */
            if (previous == get(API.hand) || previous == get(API.handWater)) selected = previous;
            invoke(API.use, selected);
            GL20.glUseProgram((Integer) get(API.activeId));
        }
        @Override public void close()
        {
            invoke(API.use, previous);
            GL20.glUseProgram(program);
        }
    }

    private static boolean flag(Field field) { return (Boolean) get(field); }
    private static Object get(Field field)
    {
        try { return field.get(null); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("OptiFine field " + field.getName(), error); }
    }
    private static void set(Field field, Object value)
    {
        try { field.set(null, value); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("OptiFine field " + field.getName(), error); }
    }
    private static Object invoke(Method method, Object... args)
    {
        try { return method.invoke(null, args); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("OptiFine method " + method.getName(), error); }
    }
    private static Object invokeOn(Method method, Object owner, Object... args)
    {
        try { return method.invoke(owner, args); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("OptiFine uniform " + method.getName(), error); }
    }
    private static final class Access
    {
        final Field loaded, world, deferred, shadow, glowing, active, activeId, none, entities, hand, handWater, texturedLit, entityColor;
        final Field terrainSolid, terrainCutoutMip, terrainCutout, water;
        final Field[] blockLight = new Field[3];
        final Method use, resize, colorSet, uniformProgram, uniformSetProgram;
        Access(Class<?> shaders) throws ReflectiveOperationException
        {
            loaded = shaders.getField("shaderPackLoaded");
            blockLight[0] = shaders.getField("blockLightLevel06");
            blockLight[1] = shaders.getField("blockLightLevel08");
            blockLight[2] = shaders.getField("blockLightLevel05");
            world = shaders.getField("isRenderingWorld");
            deferred = shaders.getField("isRenderingDfb");
            shadow = shaders.getField("isShadowPass");
            glowing = shaders.getField("isEntitiesGlowing");
            active = shaders.getField("activeProgram");
            activeId = shaders.getField("activeProgramID");
            none = shaders.getField("ProgramNone");
            entities = shaders.getField("ProgramEntities");
            texturedLit = shaders.getField("ProgramTexturedLit");
            terrainSolid = shaders.getField("ProgramTerrainSolid");
            terrainCutoutMip = shaders.getField("ProgramTerrainCutoutMip");
            terrainCutout = shaders.getField("ProgramTerrainCutout");
            water = shaders.getField("ProgramWater");
            entityColor = shaders.getField("uniform_entityColor");
            colorSet = entityColor.getType().getMethod("setValue",float.class,float.class,float.class,float.class);
            uniformProgram = entityColor.getType().getMethod("getProgram");
            uniformSetProgram = entityColor.getType().getMethod("setProgram",int.class);
            hand = shaders.getField("ProgramHand");
            handWater = shaders.getField("ProgramHandWater");
            use = shaders.getMethod("useProgram", active.getType());
            resize = shaders.getMethod("scheduleResize");
        }
    }
}
