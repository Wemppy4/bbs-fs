package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.fonts.nativefonts.*;
import mchorse.bbs_mod.ui.framework.*;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

/** Exact default font fixture; uses the normal cache/provider/Batcher2D path. */
public final class OriginalDefaultFontProbe extends UIBaseMenu
{
    private static final String[] SAMPLES = {
        "Перемещение", "Вемпи: Жёлтый ёж, Дорожка 123 — BBS FS", "ЁёЙй ЖЩДцщ ABC abc 0123",
        "\u00a7cКрасный \u00a7lжирный \u00a7rобычный \u00a7oкурсив", "\u00a7nПодчёркнутый\u00a7r / \u00a7mзачёркнутый",
        "中日韓 あいうえお 한국어", "\u00a7l中中\u00a7r 中中 / \u00a7o漢字", "\u00a7kSecret\u00a7r / 𠀀 / 🙂"
    };
    private NativeBitmapFontRenderer previous;
    private int frames;
    @Override public boolean canPause() { return false; }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        this.frames++;
        render.batcher.box(0, 0, this.width, this.height, BBSSettings.baseSurface());
        render.batcher.text("Exact default 1.20.4 / PNG + Unihex", 12, 12, -1);
        if (NativeDefaultFont.state() != NativeDefaultFont.State.READY) return;
        int y = 42;
        for (String sample : SAMPLES)
        {
            render.batcher.text(sample, 12, y, -1);
            render.batcher.textShadow(sample, this.width / 2, y, -1);
            y += 25;
        }
        FontRenderer vanilla = new FontRenderer();
        vanilla.setRenderer(Minecraft.getMinecraft().fontRenderer);
        FontRenderer saved = render.batcher.setFont(vanilla);
        render.batcher.text("1.12 comparison: Перемещение / Жёлтый ёж", 12, y + 12, 0xffbbaaaa);
        render.batcher.setFont(saved);
        render.batcher.text("1.20.4 bitmap:   Перемещение / Жёлтый ёж", 12, y + 32, -1);
        y += 60;
        for (String line : saved.wrap("\u00a7aПеренос слов сохраняет цвет, кириллицу и точные интервалы между символами.", 260))
        {
            render.batcher.text(line, 12, y, -1);
            y += saved.getLineHeight();
        }
    }
    public static JsonObject handle(JsonObject request)
    {
        if (request.has("open") && request.get("open").getAsBoolean()) UIScreen.open(new OriginalDefaultFontProbe());
        if (request.has("retry") && request.get("retry").getAsBoolean()) NativeDefaultFont.prepare();
        OriginalDefaultFontProbe probe = UIScreen.getCurrentMenu() instanceof OriginalDefaultFontProbe ? (OriginalDefaultFontProbe) UIScreen.getCurrentMenu() : null;
        if (request.has("reload") && request.get("reload").getAsBoolean())
        {
            if (probe != null && NativeDefaultFont.renderer() instanceof NativeBitmapFontRenderer)
                probe.previous = (NativeBitmapFontRenderer) NativeDefaultFont.renderer();
            NativeDefaultFont.reloadAtlases();
        }
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("state", NativeDefaultFont.state().name());
        out.addProperty("detail", NativeDefaultFont.detail());
        out.addProperty("cache", NativeDefaultFont.cacheDirectory().toAbsolutePath().toString());
        out.addProperty("frames", probe == null ? 0 : probe.frames);
        out.addProperty("previousClosed", probe != null && probe.previous != null && probe.previous.isClosed());
        net.minecraft.client.gui.FontRenderer renderer = NativeDefaultFont.renderer();
        out.addProperty("glError", GL11.glGetError());
        if (renderer instanceof NativeBitmapFontRenderer)
        {
            NativeBitmapFontRenderer exact = (NativeBitmapFontRenderer) renderer;
            out.addProperty("glyphCount", exact.getData().size());
            out.addProperty("atlasPages", exact.getAtlasCount());
            out.addProperty("cyrillicWidth", exact.getStringWidth("Перемещение"));
            out.addProperty("boldCyrillicWidth", exact.getStringWidth("\u00a7lПеремещение"));
            out.addProperty("boldUnihexWidth", exact.getStringWidth("\u00a7l中中"));
            out.addProperty("vanillaCyrillicWidth", Minecraft.getMinecraft().fontRenderer.getStringWidth("Перемещение"));
            JsonArray samples = new JsonArray();
            for (String sample : SAMPLES) samples.add(exact.getStringWidth(sample));
            out.add("sampleWidths", samples);
            JsonArray glyphs = new JsonArray();
            for (int cp : new int[] {'A', 'Ж', 'Ё', 'ё', 'Й', 'й', '中', 0x20000, 0x10fffd})
            {
                BitmapFontData.Glyph g = exact.getData().glyph(cp);
                JsonObject glyph = new JsonObject();
                glyph.addProperty("codepoint", cp); glyph.addProperty("provider", g.provider);
                glyph.addProperty("advance", g.advance); glyph.addProperty("top", g.top);
                glyph.addProperty("width", g.width); glyph.addProperty("height", g.height);
                glyph.addProperty("boldOffset", g.boldOffset); glyph.addProperty("shadowOffset", g.shadowOffset);
                glyphs.add(glyph);
            }
            out.add("glyphs", glyphs);
        }
        return out;
    }
}
