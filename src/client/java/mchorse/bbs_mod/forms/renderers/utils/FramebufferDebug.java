package mchorse.bbs_mod.forms.renderers.utils;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.logging.LogUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.FormFramebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic log for the framebuffer form. Off until {@link #ENABLED} is set to true;
 * switched on, it writes out everything a framebuffer
 * form's render depends on, for every such render inside one frame per second: which pass it
 * is (world, Iris shadow, stencil pick, UI), its order in the frame, the framebuffer and program
 * GL really has bound against what Iris' redundant-bind cache believes, the write masks and
 * Iris' mask locks, the sampler units, the model-view matrix and GPU uniform slices, and what actually landed in the buffer
 * (coverage, bounding box, brightest texel) after each nested part. Lines carry "!!" where two
 * views of the same state disagree.
 *
 * <p>It stays off by default because a logged frame reads every form's buffer back from the
 * GPU - a megabyte per 512x512 form - which is a hitch once a second. This is what found the
 * shader-pack transparency bug (Iris leaving an indexed alpha blend function behind
 * GlStateManager's cache); the call sites live in FramebufferFormRenderer, ModelFormRenderer,
 * BillboardFormRenderer, IrisUtils and WorldRendererMixin, all gated on {@link #logging} or
 * {@link #inside()}.</p>
 */
public class FramebufferDebug
{
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final long PERIOD = 1000L;
    private static final Object MISSING = new Object();

    /** The switch. Flip to true to log; nothing else needs touching. */
    public static boolean ENABLED = false;

    /** True for the whole frame being logged: every framebuffer render in it gets written out. */
    public static boolean logging;

    private static long last;
    private static int frame;
    private static int sequence;
    private static int depth;
    private static final String[] labels = new String[16];
    private static final Map<String, Object> handles = new HashMap<>();

    /** Once per frame, at the start of the world render. Picks the frames that get logged. */
    public static void newFrame()
    {
        long now = System.currentTimeMillis();

        frame += 1;
        sequence = 0;
        logging = ENABLED && now - last >= PERIOD;

        if (logging)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            last = now;
            LOGGER.info("[BBS FB] ===== frame {} ===== pack={} graphics={} mainColor={} window={}x{}",
                frame, staticCall("net.irisshaders.iris.Iris", "getCurrentPackName"),
                mc.options.getPreset().getValue(), texture(mc.getFramebuffer().getColorAttachment()),
                mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        }
    }

    /** Start of one framebuffer form render. Returns whether this render is being logged. */
    public static boolean beginRender(FramebufferForm form, FormRenderingContext context, FormFramebuffer framebuffer)
    {
        depth += 1;

        if (!logging || depth >= labels.length)
        {
            return false;
        }

        sequence += 1;

        String pass = context.isPicking() ? "PICK"
            : BBSRendering.isIrisShadowPass() ? "SHADOW"
            : context.ui ? "UI"
            : BBSRendering.isRenderingWorld() ? "WORLD" : "OTHER";
        List<BodyPart> parts = form.parts.getAllTyped();
        StringBuilder inside = new StringBuilder();

        for (BodyPart part : parts)
        {
            Form nested = part.getForm();

            inside.append(inside.length() == 0 ? "" : ",").append(nested == null ? "null" : nested.getClass().getSimpleName());
        }

        labels[depth] = "f" + frame + " #" + sequence + " " + pass + " depth=" + depth + " form@" + Integer.toHexString(System.identityHashCode(form));

        log("begin", framebuffer.width + "x" + framebuffer.height + " scale=" + form.scale.get()
            + " parts=" + parts.size() + "[" + inside + "]"
            + " color=" + texture(framebuffer.getColor())
            + " light=" + context.light + " overlay=" + context.overlay + " color=" + Integer.toHexString(context.color)
            + " shaderShadow=" + form.shaderShadow.get());

        return true;
    }

    public static void endRender()
    {
        depth -= 1;
    }

    /** Whether a framebuffer form's render is being logged right now - nested renderers speak up then. */
    public static boolean inside()
    {
        return logging && depth > 0;
    }

    /** The bindings a draw is about to use, on one line - for the draw sites of nested forms. */
    public static String bindings()
    {
        return framebuffers() + " | " + program() + " | " + iris();
    }

    /** Read the named render target; the last GL read binding may belong to a different pass. */
    public static void readViewport(String tag)
    {
        if (!logging)
        {
            return;
        }

        GpuTextureView target = RenderSystem.outputColorTextureOverride;

        if (target != null)
        {
            readTexture(tag, target.texture(), target.baseMipLevel());
        }
    }

    public static void log(String tag, String line)
    {
        LOGGER.info("[BBS FB] {} | {}: {}", depth < labels.length && labels[depth] != null ? labels[depth] : "?", tag, line);
    }

    /** Everything the draw depends on, as GL and Iris really hold it right now. */
    public static void state(String tag, FormRenderingContext context)
    {
        if (!logging)
        {
            return;
        }

        log(tag, iris());
        log(tag, framebuffers());
        log(tag, program());
        log(tag, glState());
        log(tag, samplers());
        log(tag, matrices(context));
        log(tag, lights());
    }

    /** The masks a glClear obeys, right before the buffer is cleared. */
    public static void clearState(String tag)
    {
        if (!logging)
        {
            return;
        }

        float[] clearColor = new float[4];

        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);

        log(tag, "colorMask=" + colorMask() + " depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            + " scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            + " clearColor=" + clearColor[0] + "," + clearColor[1] + "," + clearColor[2] + "," + clearColor[3]
            + " depthColorLocked=" + staticCall("net.irisshaders.iris.gl.blending.DepthColorStorage", "isDepthColorLocked")
            + " " + framebuffers());
    }

    /** What the pack thinks, and every flag of its that our offscreen render toggles. */
    public static String iris()
    {
        Object pipeline = pipeline();

        return "irisPack=" + BBSRendering.isIrisShadersEnabled()
            + " shadingThisDraw=" + BBSRendering.isIrisWorldForms()
            + " renderingWorld=" + BBSRendering.isRenderingWorld()
            + " irisShadowPass=" + BBSRendering.isIrisShadowPass()
            + " pipeline=" + (pipeline == null ? "null" : pipeline.getClass().getSimpleName())
            + " isMainBound=" + instanceField(pipeline, "isMainBound")
            + " isRenderingWorld=" + instanceField(pipeline, "isRenderingWorld")
            + " shouldOverride=" + instanceCall(pipeline, "shouldOverrideShaders")
            + " shadowACTIVE=" + staticField("net.irisshaders.iris.shadows.ShadowRenderer", "ACTIVE")
            + " depthColorLocked=" + staticCall("net.irisshaders.iris.gl.blending.DepthColorStorage", "isDepthColorLocked")
            + " blendLocked=" + staticCall("net.irisshaders.iris.gl.blending.BlendModeStorage", "isBlendLocked")
            + " isRenderingLevel=" + staticField("net.irisshaders.iris.vertices.ImmediateState", "isRenderingLevel")
            + " extendedVertexFormat=" + staticField("net.irisshaders.iris.vertices.ImmediateState", "renderWithExtendedVertexFormat");
    }

    /** GL's framebuffer bindings against Iris' redundant-bind cache (a mismatch is marked). */
    public static String framebuffers()
    {
        int draw = GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        Object cachedDraw = staticField(GlStateManager.class, "iris$drawFramebuffer");
        Object cachedRead = staticField(GlStateManager.class, "iris$readFramebuffer");
        boolean mismatch = (cachedDraw instanceof Integer d && d != draw) || (cachedRead instanceof Integer r && r != read);

        return "fbo real draw=" + draw + " read=" + read + " irisCache draw=" + cachedDraw + " read=" + cachedRead + (mismatch ? " !!FBO-CACHE-MISMATCH" : "");
    }

    /** The last GL program against Iris' cache; the next render pass selects its own program. */
    public static String program()
    {
        int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        Object cached = staticField(GlStateManager.class, "iris$program");
        boolean mismatch = cached instanceof Integer c && c != current;

        return "program real=" + current + " irisCache=" + cached + (mismatch ? " !!PROGRAM-CACHE-MISMATCH" : "")
            + " (programs and samplers are selected per render pass)";
    }

    public static String glState()
    {
        int[] viewport = new int[4];
        int[] scissor = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);

        return "colorMask=" + colorMask()
            + " depthMask=" + GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            + " depth=" + GL11.glIsEnabled(GL11.GL_DEPTH_TEST) + "/" + name(GL11.glGetInteger(GL11.GL_DEPTH_FUNC))
            + " blend=" + GL11.glIsEnabled(GL11.GL_BLEND)
            + "/" + name(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)) + "," + name(GL11.glGetInteger(GL14.GL_BLEND_DST_RGB))
            + "/" + name(GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)) + "," + name(GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA))
            + " cull=" + GL11.glIsEnabled(GL11.GL_CULL_FACE) + "/" + name(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE))
            + " frontFace=" + name(GL11.glGetInteger(GL11.GL_FRONT_FACE))
            + " scissor=" + GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) + "[" + scissor[0] + "," + scissor[1] + "," + scissor[2] + "," + scissor[3] + "]"
            + " viewport=[" + viewport[0] + "," + viewport[1] + "," + viewport[2] + "," + viewport[3] + "]";
    }

    /** Actual GL sampler bindings; 1.21.11 has no global RenderSystem shader-texture slots. */
    public static String samplers()
    {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        StringBuilder builder = new StringBuilder("activeUnit=" + (active - GL13.GL_TEXTURE0));

        for (int i = 0; i < 4; i++)
        {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);

            int real = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            builder.append(" u").append(i).append("=").append(real);
        }

        GL13.glActiveTexture(active);

        return builder.toString();
    }

    /** Where the form sits and what the programs will multiply by. */
    public static String matrices(FormRenderingContext context)
    {
        Matrix4f stack = context.stack.peek().getPositionMatrix();
        Matrix4f modelView = RenderSystem.getModelViewStack();
        boolean identity = modelView.equals(new Matrix4f(), 1e-5F);

        /* Projection and lighting live in GPU uniform buffers in 1.21.11. Report their slices;
         * there is no separate applied model-view or global shader colour to compare anymore. */
        return "stack t=" + translation(stack) + " s=" + scale(stack)
            + " modelView t=" + translation(modelView) + " s=" + scale(modelView) + " identity=" + identity
            + " projection=" + RenderSystem.getProjectionType()
            + " projectionBuffer=" + RenderSystem.getProjectionMatrixBuffer();
    }

    public static String lights()
    {
        return "lightingBuffer=" + RenderSystem.getShaderLights();
    }

    public static void readBuffer(String tag, FormFramebuffer framebuffer)
    {
        if (logging)
        {
            readTexture(tag, framebuffer.getColor(), 0);
        }
    }

    /** Attach only for readback and restore the binding before any rendering can resume. */
    private static void readTexture(String tag, GpuTexture texture, int mipLevel)
    {
        if (!(texture instanceof GlTexture gl))
        {
            log(tag, "pixel readback unavailable for " + texture.getClass().getSimpleName());

            return;
        }

        int previous = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int readback = GL30.glGenFramebuffers();

        try
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readback);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, gl.getGlId(), mipLevel);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);

            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE)
            {
                readPixels(tag, readback, texture.getWidth(mipLevel), texture.getHeight(mipLevel));
            }
            else
            {
                log(tag, "pixel readback framebuffer incomplete");
            }
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous);
            GL30.glDeleteFramebuffers(readback);
        }
    }

    private static String texture(GpuTexture texture)
    {
        return texture instanceof GlTexture gl ? texture.getLabel() + "#" + gl.getGlId() : String.valueOf(texture);
    }

    private static void readPixels(String tag, int expected, int width, int height)
    {
        int read = GL30.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int x = 0;
        int y = 0;

        /* A huge buffer is sampled through a centred window rather than read whole. */
        if ((long) width * height > 2048L * 2048L)
        {
            x = width / 2 - 1024;
            y = height / 2 - 1024;
            width = 2048;
            height = 2048;
        }

        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);

        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glReadPixels(x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);

            int covered = 0;
            int minX = width, minY = height, maxX = -1, maxY = -1;
            int br = 0, bg = 0, bb = 0, ba = 0, best = -1;

            for (int py = 0; py < height; py++)
            {
                for (int px = 0; px < width; px++)
                {
                    int i = (py * width + px) * 4;
                    int a = pixels.get(i + 3) & 0xFF;

                    if (a == 0)
                    {
                        continue;
                    }

                    covered += 1;
                    minX = Math.min(minX, px);
                    minY = Math.min(minY, py);
                    maxX = Math.max(maxX, px);
                    maxY = Math.max(maxY, py);

                    int r = pixels.get(i) & 0xFF;
                    int g = pixels.get(i + 1) & 0xFF;
                    int b = pixels.get(i + 2) & 0xFF;
                    int sum = r + g + b;

                    if (sum > best)
                    {
                        best = sum;
                        br = r;
                        bg = g;
                        bb = b;
                        ba = a;
                    }
                }
            }

            float coverage = covered * 100F / (width * height);

            log(tag, "readFbo=" + read + " (expected " + expected + ")" + " size=" + width + "x" + height
                + " coverage=" + String.format("%.2f", coverage) + "%"
                + (covered == 0 ? " !!BUFFER-EMPTY" : " bbox=[" + (x + minX) + ".." + (x + maxX) + " x " + (y + minY) + ".." + (y + maxY) + "]")
                + (best < 0 ? "" : " brightest=rgba(" + br + ", " + bg + ", " + bb + ", " + ba + ")"));
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, skipRows);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            MemoryUtil.memFree(pixels);
        }
    }

    private static String colorMask()
    {
        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer mask = stack.malloc(4);

            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);

            boolean r = mask.get(0) != 0, g = mask.get(1) != 0, b = mask.get(2) != 0, a = mask.get(3) != 0;

            return r + "," + g + "," + b + "," + a + (r && g && b && a ? "" : "!!");
        }
    }

    private static Object pipeline()
    {
        Object manager = staticCall("net.irisshaders.iris.Iris", "getPipelineManager");

        return manager == null || "n/a".equals(manager) ? null : instanceCall(manager, "getPipelineNullable");
    }

    /* Reflection, because the class must stay loadable without Iris and the fields it reads are
     * private ones of another mod. Every lookup is cached, and a miss is reported as "n/a". */

    private static Object staticField(String className, String field)
    {
        try
        {
            return staticField(Class.forName(className), field);
        }
        catch (Throwable e)
        {
            return "n/a";
        }
    }

    private static Object staticField(Class<?> type, String field)
    {
        String key = type.getName() + "." + field;
        Object handle = handles.get(key);

        if (handle == null)
        {
            try
            {
                Field f = type.getDeclaredField(field);

                f.setAccessible(true);
                handle = f;
            }
            catch (Throwable e)
            {
                handle = MISSING;
            }

            handles.put(key, handle);
        }

        try
        {
            return handle == MISSING ? "n/a" : ((Field) handle).get(null);
        }
        catch (Throwable e)
        {
            return "n/a";
        }
    }

    private static Object instanceField(Object target, String field)
    {
        if (target == null)
        {
            return "n/a";
        }

        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass())
        {
            try
            {
                Field f = type.getDeclaredField(field);

                f.setAccessible(true);

                return f.get(target);
            }
            catch (NoSuchFieldException e)
            {}
            catch (Throwable e)
            {
                return "n/a";
            }
        }

        return "n/a";
    }

    private static Object staticCall(String className, String method)
    {
        try
        {
            String key = className + "#" + method;
            Object handle = handles.get(key);

            if (handle == null)
            {
                try
                {
                    handle = Class.forName(className).getMethod(method);
                }
                catch (Throwable e)
                {
                    handle = MISSING;
                }

                handles.put(key, handle);
            }

            return handle == MISSING ? "n/a" : ((Method) handle).invoke(null);
        }
        catch (Throwable e)
        {
            return "n/a";
        }
    }

    private static Object instanceCall(Object target, String method)
    {
        if (target == null)
        {
            return "n/a";
        }

        try
        {
            return target.getClass().getMethod(method).invoke(target);
        }
        catch (Throwable e)
        {
            return "n/a";
        }
    }

    private static String translation(Matrix4f m)
    {
        return "(" + fmt(m.m30()) + ", " + fmt(m.m31()) + ", " + fmt(m.m32()) + ")";
    }

    private static String scale(Matrix4f m)
    {
        float sx = (float) Math.sqrt(m.m00() * m.m00() + m.m01() * m.m01() + m.m02() * m.m02());
        float sy = (float) Math.sqrt(m.m10() * m.m10() + m.m11() * m.m11() + m.m12() * m.m12());
        float sz = (float) Math.sqrt(m.m20() * m.m20() + m.m21() * m.m21() + m.m22() * m.m22());

        return "(" + fmt(sx) + ", " + fmt(sy) + ", " + fmt(sz) + ")";
    }

    private static String fmt(float v)
    {
        return String.format("%.3f", v);
    }

    private static String name(int constant)
    {
        switch (constant)
        {
            case GL11.GL_NEAREST: return "NEAREST";
            case GL11.GL_LINEAR: return "LINEAR";
            case GL11.GL_FRONT: return "FRONT";
            case GL11.GL_BACK: return "BACK";
            case GL11.GL_FRONT_AND_BACK: return "FRONT_AND_BACK";
            case GL11.GL_CW: return "CW";
            case GL11.GL_CCW: return "CCW";
            case GL11.GL_ZERO: return "ZERO";
            case GL11.GL_ONE: return "ONE";
            case GL11.GL_SRC_ALPHA: return "SRC_ALPHA";
            case GL11.GL_ONE_MINUS_SRC_ALPHA: return "1-SRC_ALPHA";
            case GL11.GL_DST_ALPHA: return "DST_ALPHA";
            case GL11.GL_ONE_MINUS_DST_ALPHA: return "1-DST_ALPHA";
            case GL11.GL_SRC_COLOR: return "SRC_COLOR";
            case GL11.GL_ONE_MINUS_SRC_COLOR: return "1-SRC_COLOR";
            case GL11.GL_ALWAYS: return "ALWAYS";
            case GL11.GL_LEQUAL: return "LEQUAL";
            case GL11.GL_LESS: return "LESS";
            case GL11.GL_EQUAL: return "EQUAL";
            case GL11.GL_GEQUAL: return "GEQUAL";
            case GL11.GL_NEVER: return "NEVER";
            default: return String.valueOf(constant);
        }
    }
}
