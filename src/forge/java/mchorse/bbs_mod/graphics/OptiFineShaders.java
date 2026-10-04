package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.BBSMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    public static EntityPass entities() { return new EntityPass(); }
    public static final class EntityPass implements AutoCloseable
    {
        private final Object previous = get(API.active);
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private EntityPass()
        {
            Object selected = get(API.entities);
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
    private static final class Access
    {
        final Field loaded, world, deferred, shadow, glowing, active, activeId, none, entities, hand, handWater;
        final Method use, resize;
        Access(Class<?> shaders) throws ReflectiveOperationException
        {
            loaded = shaders.getField("shaderPackLoaded");
            world = shaders.getField("isRenderingWorld");
            deferred = shaders.getField("isRenderingDfb");
            shadow = shaders.getField("isShadowPass");
            glowing = shaders.getField("isEntitiesGlowing");
            active = shaders.getField("activeProgram");
            activeId = shaders.getField("activeProgramID");
            none = shaders.getField("ProgramNone");
            entities = shaders.getField("ProgramEntities");
            hand = shaders.getField("ProgramHand");
            handWater = shaders.getField("ProgramHandWater");
            use = shaders.getMethod("useProgram", active.getType());
            resize = shaders.getMethod("scheduleResize");
        }
    }
}
