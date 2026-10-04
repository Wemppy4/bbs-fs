package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.forms.renderers.*;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.framework.elements.utils.*;
import mchorse.bbs_mod.ui.utils.*;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** Production viewport, form lifecycle and RGBA picking in an isolated test menu. */
public final class OriginalViewportProbe extends UIBaseMenu
{
    private static OriginalViewportProbe last;
    private final StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private final StencilMap map = new StencilMap();
    private final Viewport viewport = new Viewport();
    private final ModelForm model = new ModelForm();
    private final BillboardForm billboard = new BillboardForm();
    private final ExtrudedForm extruded = new ExtrudedForm();
    private final MobForm mob = new MobForm();
    private Form form;
    private String kind = "model";
    private boolean highlight;
    private int frames;
    private boolean stateRestored = true;
    private int glError;
    private int framebufferStatus;
    private final float oldSmoothness = BBSSettings.editorCameraSmoothness.get();
    private final boolean oldFreeze = BBSSettings.freezeModels.get();

    private OriginalViewportProbe()
    {
        BBSSettings.editorCameraSmoothness.set(0F);
        BBSSettings.freezeModels.set(true);
        model.model.set("helper_drone");
        Link link = new Link("probe", "viewport-ring.png");
        Pixels pixels = Pixels.fromSize(16, 16);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
        {
            boolean hole = x < 2 || y < 2 || x >= 14 || y >= 14 || x >= 6 && x < 10 && y >= 6 && y < 10;
            pixels.setColor(x, y, new Color().set(hole ? 0 : x < 8 ? 0xff40ff90 : 0xff4090ff));
        }
        pixels.rewindBuffer();
        Texture texture = BBSModClient.getTextures().createTexture(link);
        texture.bind(); texture.setFilter(GL11.GL_NEAREST); texture.uploadTexture(pixels); texture.unbind();
        billboard.texture.set(link); extruded.texture.set(link);
        form = model;
        viewport.relative(this.main).xy(16, 48).w(1F, -32).h(1F, -64);
        viewport.setPosition(0F, .5F, 0F);
        viewport.setDistance(3F);
        viewport.setRotation(25F, 18F);
        this.main.add(viewport);
    }

    @Override public boolean canPause() { return false; }
    @Override public void onClose(UIBaseMenu next)
    {
        BBSSettings.editorCameraSmoothness.set(oldSmoothness);
        BBSSettings.freezeModels.set(oldFreeze);
    }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0, 0, width, height, 0xff14181e);
        render.batcher.text("BBS FS: original 3D viewport / " + kind, 16, 12, -1);
        render.batcher.text("Orbit / pan / zoom / native form picking", 16, 28, -1);
        render.batcher.box(viewport.area.x, viewport.area.y, viewport.area.ex(), viewport.area.ey(), 0xff222832);
    }

    private final class Viewport extends UIModelRenderer
    {
        @Override public void resize()
        {
            super.resize();
            stencil.setup(Link.bbs("viewport_probe"));
            stencil.resizeGUI(Math.max(1, area.w), Math.max(1, area.h));
        }
        @Override protected void renderUserModel(UIContext context)
        {
            FormRenderingContext rendering = new FormRenderingContext()
                .set(FormRenderType.PREVIEW, getEntity(), context.batcher.getContext().getMatrices(), 0x00f000f0, 10 << 16, context.getTransition())
                .camera(camera).modelRenderer(context.getTick());
            FormUtilsClient.render(form, rendering);
            int target = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            IntBuffer savedViewport = BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, savedViewport);
            FloatBuffer clearColor = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            try
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                map.setup();
                GlStateManager.clearColor(0F, 0F, 0F, 0F);
                stencil.apply();
                framebufferStatus = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                FormUtilsClient.render(form, rendering.stencilMap(map));
                stencil.pickGUI(context, area, 4, GizmoPickIds.STENCIL_MAX);
                stencil.unbind(map);
            }
            finally
            {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
                GlStateManager.viewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
                GlStateManager.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
        @Override public void render(UIContext context)
        {
            FloatBuffer projection = readMatrix(GL11.GL_PROJECTION_MATRIX);
            FloatBuffer view = readMatrix(GL11.GL_MODELVIEW_MATRIX);
            int framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            super.render(context);
            if (highlight) stencil.renderPreview(context, area, stencil.indexOf(form, ""));
            stateRestored &= sameMatrix(projection, readMatrix(GL11.GL_PROJECTION_MATRIX))
                && sameMatrix(view, readMatrix(GL11.GL_MODELVIEW_MATRIX))
                && framebuffer == GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
                && program == GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int error = GL11.glGetError();
            if (error != 0) glError = error;
            frames++;
        }
    }

    private static FloatBuffer readMatrix(int kind)
    {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(kind, buffer);
        return buffer;
    }
    private static boolean sameMatrix(FloatBuffer a, FloatBuffer b)
    {
        for (int i = 0; i < 16; i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    /** Read the actual rendered stencil, finding a visible form pixel nearest the centre. */
    private JsonObject scan()
    {
        Texture texture = stencil.getFramebuffer().getMainTexture();
        ByteBuffer data = BufferUtils.createByteBuffer(texture.width * texture.height * 4);
        int target = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] parameters = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS, GL11.GL_PACK_SKIP_PIXELS};
        int[] values = new int[parameters.length];
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        try
        {
            for (int i = 0; i < parameters.length; i++) { values[i] = GL11.glGetInteger(parameters[i]); GL11.glPixelStorei(parameters[i], i == 0 ? 1 : 0); }
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            stencil.getFramebuffer().bind();
            GL11.glReadPixels(0, 0, texture.width, texture.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        }
        finally
        {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            for (int i = 0; i < parameters.length; i++) GL11.glPixelStorei(parameters[i], values[i]);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pbo);
        }
        int count = 0, sx = -1, sy = -1, best = Integer.MAX_VALUE;
        java.util.Set<Integer> indices = new java.util.TreeSet<>();
        for (int y = 0; y < texture.height; y++) for (int x = 0; x < texture.width; x++)
        {
            int base = (y * texture.width + x) * 4;
            if ((data.get(base + 3) & 255) == 0) continue;
            int index = (data.get(base) & 255) | (data.get(base + 1) & 255) << 8 | (data.get(base + 2) & 255) << 16;
            if (index <= GizmoPickIds.STENCIL_MAX) continue;
            indices.add(index); count++;
            int dx = x - texture.width / 2, dy = y - texture.height / 2, d = dx * dx + dy * dy;
            if (d < best) { best = d; sx = x; sy = y; }
        }
        JsonObject out = new JsonObject();
        JsonArray ids = new JsonArray(); for (int id : indices) ids.add(id);
        out.add("ids", ids); out.addProperty("pixels", count);
        out.addProperty("sampleX", viewport.area.x + Math.round(sx / BBSModClient.getGUIScale()));
        out.addProperty("sampleY", viewport.area.y + viewport.area.h - Math.round(sy / BBSModClient.getGUIScale()));
        return out;
    }

    private JsonObject snapshot()
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true); out.addProperty("kind", kind); out.addProperty("frames", frames);
        out.addProperty("stateRestored", stateRestored); out.addProperty("glError", glError);
        out.addProperty("framebufferStatus", framebufferStatus); out.addProperty("pickedIndex", stencil.getIndex());
        out.addProperty("pickedForm", stencil.getPicked() != null && stencil.getPicked().a == form);
        out.addProperty("indexOf", stencil.indexOf(form, "")); out.addProperty("highlight", highlight);
        out.addProperty("cameraYaw", viewport.camera.rotation.y); out.addProperty("cameraPitch", viewport.camera.rotation.x);
        JsonArray position = new JsonArray(); position.add(viewport.camera.position.x); position.add(viewport.camera.position.y); position.add(viewport.camera.position.z); out.add("camera", position);
        JsonArray pivot = new JsonArray(); pivot.add(viewport.pos.x); pivot.add(viewport.pos.y); pivot.add(viewport.pos.z); out.add("pivot", pivot);
        JsonObject area = new JsonObject(); area.addProperty("x", viewport.area.x); area.addProperty("y", viewport.area.y); area.addProperty("w", viewport.area.w); area.addProperty("h", viewport.area.h); out.add("area", area);
        Texture texture = stencil.getFramebuffer().getMainTexture(); out.addProperty("fboWidth", texture.width); out.addProperty("fboHeight", texture.height);
        out.addProperty("scale", BBSModClient.getGUIScale()); out.addProperty("currentFormCleared", FormUtilsClient.getCurrentForm() == null);
        return out;
    }
    public static JsonObject handle(JsonObject request)
    {
        if (request.has("open") && request.get("open").getAsBoolean())
        {
            if (UIScreen.getCurrentMenu() instanceof OriginalViewportProbe) Minecraft.getMinecraft().displayGuiScreen(null);
            last = new OriginalViewportProbe(); UIScreen.open(last);
        }
        if (last == null) throw new IllegalStateException("Open the viewport probe first");
        if (request.has("close") && request.get("close").getAsBoolean()) { Minecraft.getMinecraft().displayGuiScreen(null); JsonObject out = new JsonObject(); out.addProperty("ok", true); return out; }
        if (request.has("kind"))
        {
            last.kind = request.get("kind").getAsString();
            switch (last.kind)
            {
                case "model": last.form = last.model; break;
                case "billboard": last.form = last.billboard; break;
                case "extruded": last.form = last.extruded; break;
                case "mob": last.form = last.mob; break;
                default: throw new IllegalArgumentException("Unknown form fixture");
            }
        }
        if (request.has("highlight")) last.highlight = request.get("highlight").getAsBoolean();
        if (request.has("reset") && request.get("reset").getAsBoolean()) { last.viewport.setPosition(0F,.5F,0F);last.viewport.setDistance(3F);last.viewport.setRotation(25F,18F); }
        JsonObject out = last.snapshot();
        if (request.has("scan") && request.get("scan").getAsBoolean()) out.add("scan", last.scan());
        return out;
    }
}
