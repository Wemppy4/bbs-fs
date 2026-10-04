package dev.qualet.aihelper.forge;

import com.google.gson.*;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forge.studio.*;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.*;
import mchorse.bbs_mod.graphics.texture.*;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.CRC32;

/** Disposable visual/GL checks using the production forms and production renderer. */
public final class OriginalFormsProbe extends UIBaseMenu
{
    private final Link imageLink = new Link("probe", "forms.png");
    private final Link ringLink = new Link("probe", "ring.png");
    private final Link gifLink = new Link("probe", "forms.gif");
    private final BillboardForm image = new BillboardForm(), crop = new BillboardForm(), nested = new BillboardForm();
    private final ExtrudedForm extrusion = new ExtrudedForm(), animation = new ExtrudedForm();
    private final BillboardForm red = new BillboardForm(), blue = new BillboardForm();
    private final StubEntity entity = new StubEntity();
    private final NativeTextureExtruder cache = new NativeTextureExtruder();
    private final Set<Integer> frameTextures = new LinkedHashSet<>();
    private final Map<Integer, NativeTextureMesh> seenMeshes = new HashMap<>();
    private final long[] signatures = new long[6];
    private final boolean previousFreeze = BBSSettings.freezeModels.get();
    private final JsonArray errors = new JsonArray();
    private boolean cacheReused = true;
    private int frames, overlapPixel, extrusionVertices;
    private boolean stateRestored = true;

    public OriginalFormsProbe()
    {
        BBSSettings.freezeModels.set(true);
        createTexture(imageLink, false);
        createTexture(ringLink, true);
        try
        {
            BBSModClient.getTextures().animatedTextures.put(gifLink, AnimatedTexture.fromGif(new ByteArrayInputStream(gif())));
        }
        catch (Exception e) { throw new IllegalStateException("Cannot create forms GIF fixture", e); }
        image.texture.set(imageLink);
        crop.texture.set(imageLink);
        crop.crop.get().set(2, 1, 4, 2);
        nested.texture.set(imageLink);
        nested.transform.get().rotate.set(0, 20, 10);
        BillboardForm child = new BillboardForm();
        child.texture.set(ringLink);
        child.transform.get().scale.set(.4F);
        BodyPart part = new BodyPart("fixture-child");
        part.setForm(child);
        part.transform.get().translate.set(.6F, .2F, .15F);
        part.transform.get().rotate.z = 30;
        nested.parts.addBodyPart(part);
        extrusion.texture.set(ringLink);
        extrusion.transform.get().rotate.y = 50;
        animation.texture.set(gifLink);
        animation.transform.get().rotate.y = -30;
        red.texture.set(new Link(Link.COLOR, "ffffffff"));
        blue.texture.set(new Link(Link.COLOR, "ffffffff"));
        red.color.set(new Color(1, 0, 0, .5F));
        blue.color.set(new Color(0, 0, 1, .5F));
        red.shading.set(false); blue.shading.set(false);
        red.transform.get().translate.z = .1F;
        blue.transform.get().translate.z = -.1F;
        entity.setWorld(Minecraft.getMinecraft().world);
    }

    private void createTexture(Link link, boolean ring)
    {
        int w = ring ? 16 : 32, h = 16;
        Pixels pixels = Pixels.fromSize(w, h);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++)
        {
            int argb = x < w / 2 ? (y < h / 2 ? 0xffff4040 : 0xff4080ff) : (y < h / 2 ? 0xff40ff60 : 0xffffd040);
            if (ring && (x < 2 || y < 2 || x >= 14 || y >= 14 || x >= 6 && x < 10 && y >= 6 && y < 10)) argb = 0;
            pixels.setColor(x, y, new Color().set(argb));
        }
        pixels.rewindBuffer();
        Texture texture = BBSModClient.getTextures().createTexture(link);
        texture.bind();
        texture.setFilter(GL11.GL_NEAREST);
        texture.uploadTexture(pixels);
        texture.unbind();
    }

    private static byte[] gif() throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes))
        {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int frame = 0; frame < 2; frame++)
            {
                BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                {
                    boolean inside = frame == 0 ? x >= 3 && x <= 12 && y >= 3 && y <= 12 : Math.abs(x - 8) + Math.abs(y - 8) < 7;
                    if (inside) image.setRGB(x, y, frame == 0 ? 0xff40ff80 : 0xffff40b0);
                }
                IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                IIOMetadataNode control = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);
                control.setAttribute("disposalMethod", "restoreToBackgroundColor");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "TRUE");
                control.setAttribute("delayTime", "20");
                control.setAttribute("transparentColorIndex", "0");
                metadata.setFromTree("javax_imageio_gif_image_1.0", root);
                writer.writeToSequence(new IIOImage(image, null, metadata), writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        }
        finally { writer.dispose(); }
        return bytes.toByteArray();
    }

    @Override public boolean canPause() { return false; }

    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(0, 0, width, height, 0xff171b22);
        render.batcher.text("BBS FS - original texture forms", 8, 8, -1);
        String[] labels = {"Billboard / RGBA", "Crop / UV", "Nested transforms", "Extruded / hole", "GIF / extrusion", "Alpha overlap"};
        int cw = width / 3, ch = (height - 30) / 2;
        for (int i = 0; i < 6; i++)
        {
            int x = i % 3 * cw, y = 28 + i / 3 * ch;
            render.batcher.box(x + 3, y, x + cw - 3, y + ch - 3, 0xff07090c);
            render.batcher.text(labels[i], x + 8, y + 3, -1);
        }
        render.batcher.flush();
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        Form[] forms = {image, crop, nested, extrusion, animation};
        for (int i = 0; i < forms.length; i++)
        {
            int x = i % 3 * cw, y = 28 + i / 3 * ch;
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            NativeFormRenderer.of(forms[i]).renderPreview(context, x + 8, y + 17, x + cw - 8, y + ch - 8);
            stateRestored &= program == GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) && active == GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            signatures[i] = signature(x + 8, y + 17, cw - 16, ch - 25);
        }
        int x = 2 * cw, y = 28 + ch;
        int depth = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        float brightnessX = OpenGlHelper.lastBrightnessX, brightnessY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        try
        {
            GlStateManager.translate(x + cw / 2F, y + ch / 2F + 5, 40);
            float scale = Math.min(cw, ch) * .6F;
            GlStateManager.scale(scale, -scale, scale);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
            NativeTextureRenderer.beginPass();
            try
            {
                NativeFormRenderer.of(red).render(entity, 0);
                NativeFormRenderer.of(blue).render(entity, 0);
            }
            finally { NativeTextureRenderer.endPass(); }
        }
        finally
        {
            GlStateManager.popMatrix();
            GlStateManager.depthFunc(depth);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, brightnessX, brightnessY);
        }
        overlapPixel = sample(x + cw / 2, y + ch / 2 + 5);
        signatures[5] = signature(x + 8, y + 17, cw - 16, ch - 25);
        Texture texture = BBSModClient.getTextures().getTexture(gifLink);
        NativeTextureMesh mesh = cache.get(texture);
        NativeTextureMesh previous = seenMeshes.put(texture.id, mesh);
        cacheReused &= previous == null || previous == mesh;
        frameTextures.add(texture.id);
        extrusionVertices = cache.get(BBSModClient.getTextures().getTexture(ringLink)).positions.length / 3;
        int error = GL11.glGetError();
        if (error != 0) errors.add(error);
        frames++;
    }

    private static ByteBuffer readPixels(int x, int y, int w, int h)
    {
        float scale = BBSModClient.getGUIScale();
        int px = Math.round(x * scale), py = Minecraft.getMinecraft().displayHeight - Math.round((y + h) * scale);
        int pw = Math.max(1, Math.round(w * scale)), ph = Math.max(1, Math.round(h * scale));
        ByteBuffer data = BufferUtils.createByteBuffer(pw * ph * 4);
        int pack = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int align = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT), row = GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        try
        {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glReadPixels(px, py, pw, ph, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        }
        finally
        {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, align);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, row);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, pack);
        }
        return data;
    }

    private static long signature(int x, int y, int w, int h)
    {
        ByteBuffer data = readPixels(x, y, w, h);
        CRC32 crc = new CRC32();
        while (data.hasRemaining()) crc.update(data.get() & 255);
        return crc.getValue();
    }

    private static int sample(int x, int y)
    {
        ByteBuffer data = readPixels(x, y, 1, 1);
        return (data.get(0) & 255) << 16 | (data.get(1) & 255) << 8 | data.get(2) & 255;
    }

    public void action(JsonObject data)
    {
        String action = data.get("action").getAsString();
        if (action.equals("crop")) crop.resizeCrop.set(!crop.resizeCrop.get());
        else if (action.equals("uv")) { crop.rotation.set(crop.rotation.get() + 90); crop.offsetX.set(3F); }
        else if (action.equals("alpha")) image.color.set(new Color(.4F, .8F, 1F, .5F));
        else if (action.equals("overlay")) image.overlayColor.set(new Color(0, 1, 0, .75F));
        else if (action.equals("billboard")) nested.billboard.set(!nested.billboard.get());
        else if (action.equals("filter")) { crop.linear.set(!crop.linear.get()); crop.mipmap.set(!crop.mipmap.get()); }
        else if (action.equals("reload")) { BBSModClient.getTextures().delete(ringLink); createTexture(ringLink, true); }
        else throw new IllegalArgumentException("Unknown form probe action: " + action);
    }

    public JsonObject describe()
    {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("frames", frames);
        result.add("glErrors", errors);
        result.addProperty("stateRestored", stateRestored);
        result.addProperty("cacheReused", cacheReused);
        result.addProperty("extrusionVertices", extrusionVertices);
        result.addProperty("overlapPixel", overlapPixel);
        result.addProperty("textureId", BBSModClient.getTextures().getTexture(ringLink).id);
        result.addProperty("textureFilter", BBSModClient.getTextures().getTexture(imageLink).getFilter());
        JsonArray values = new JsonArray(), ids = new JsonArray();
        for (long signature : signatures) values.add(signature);
        for (int id : frameTextures) ids.add(id);
        result.add("signatures", values); result.add("animationFrames", ids);
        return result;
    }

    @Override public void onClose(UIBaseMenu next)
    {
        BBSSettings.freezeModels.set(previousFreeze);
        BBSModClient.getTextures().delete(imageLink);
        BBSModClient.getTextures().delete(ringLink);
        BBSModClient.getTextures().delete(gifLink);
    }
}
