package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.fonts.NativeTrueTypeFontRenderer;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.watchdog.WatchDogEvent;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

/** User-font service fixture. System font bytes stay in the isolated QA game folder. */
public final class OriginalFontProbe extends UIBaseMenu
{
    private static final String SAMPLE = "Вемпи: Жёлтый ёж, Дорожка 123 — BBS FS";
    private final Link link = Link.assets("fonts/__aihelper_native.ttf");
    private final FontManager manager = BBSModClient.getFonts();
    private final File fixture = BBSMod.getAssetsPath(this.link.path);
    private final byte[] fontBytes;
    private NativeTrueTypeFontRenderer previous;
    private FontRenderer low, high;
    private int frames;

    public OriginalFontProbe() throws Exception
    {
        File run = Minecraft.getMinecraft().gameDir.getCanonicalFile();
        if (!run.getName().equals("run-forge1122") || !this.fixture.getCanonicalPath().startsWith(run.getPath() + File.separator))
            throw new IllegalStateException("Font probe needs the isolated run-forge1122 assets");
        File source = new File(System.getenv("WINDIR") == null ? "C:/Windows" : System.getenv("WINDIR"), "Fonts/arial.ttf");
        if (!source.isFile()) source = new File(System.getProperty("java.home"), "lib/fonts/LucidaSansRegular.ttf");
        if (!source.isFile()) throw new IllegalStateException("No local Cyrillic TrueType fixture: " + source);
        this.fontBytes = Files.readAllBytes(source.toPath());
        this.fixture.getParentFile().mkdirs();
        Files.write(this.fixture.toPath(), this.fontBytes);
        this.manager.accept(this.fixture.toPath(), WatchDogEvent.MODIFIED);
        this.refresh();
    }
    private void refresh()
    {
        this.low = this.manager.get(this.link, 20, 1);
        this.high = this.manager.get(this.link, 20, 3);
    }
    @Override public boolean canPause() { return false; }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        this.refresh();
        this.frames++;
        render.batcher.box(0, 0, this.width, this.height, BBSSettings.baseSurface());
        render.batcher.text("Native user TTF / Cyrillic / formatting / wrap", 12, 14, -1);
        if (this.low == null || this.high == null)
        {
            render.batcher.text("Font fixture absent (watch invalidation)", 12, 42, -1);
            return;
        }
        FontRenderer before = render.batcher.setFont(this.low);
        try
        {
            render.batcher.text(SAMPLE, 12, 48, -1);
            render.batcher.textShadow("\u00a7cКрасный \u00a7lжирный \u00a7rобычный \u00a7oкурсив", 12, 85, -1);
            render.batcher.text("\u00a7nПодчёркнутый\u00a7r / \u00a7mзачёркнутый\u00a7r / \u00a7ksecret", 12, 122, -1);
            int y = 164;
            for (String line : this.low.wrap("\u00a7aПеренос слов сохраняет цвет, кириллицу и дробные интервалы между символами.", 330))
            {
                render.batcher.text(line, 12, y, -1);
                y += this.low.getLineHeight();
            }
            render.batcher.setFont(this.high);
            render.batcher.text(SAMPLE, 12, y + 30, -1);
        }
        finally { render.batcher.setFont(before); }
        render.batcher.text("Above: oversample1; below: oversample3. Layout widths must match.", 12, this.height - 22, -1);
    }
    public static JsonObject handle(JsonObject request) throws Exception
    {
        if (request.has("open") && request.get("open").getAsBoolean()) UIScreen.open(new OriginalFontProbe());
        if (!(UIScreen.getCurrentMenu() instanceof OriginalFontProbe)) throw new IllegalStateException("Open fonts probe first");
        OriginalFontProbe probe = (OriginalFontProbe) UIScreen.getCurrentMenu();
        if (request.has("action"))
        {
            String action = request.get("action").getAsString();
            probe.refresh();
            probe.previous = probe.low == null ? null : (NativeTrueTypeFontRenderer) probe.low.getRenderer();
            if (action.equals("invalidate")) probe.manager.accept(probe.fixture.toPath(), WatchDogEvent.MODIFIED);
            else if (action.equals("touch")) Files.write(probe.fixture.toPath(), probe.fontBytes);
            else if (action.equals("delete")) Files.deleteIfExists(probe.fixture.toPath());
            else if (action.equals("restore")) Files.write(probe.fixture.toPath(), probe.fontBytes);
            else throw new IllegalArgumentException("Unknown font action " + action);
        }
        return probe.snapshot();
    }
    public JsonObject snapshot()
    {
        this.refresh();
        JsonObject out = new JsonObject();
        out.addProperty("ok", true); out.addProperty("frames", this.frames);
        out.addProperty("glError", GL11.glGetError());
        out.addProperty("loaded", this.low != null && this.high != null);
        out.addProperty("previousClosed", this.previous != null && this.previous.isClosed());
        out.addProperty("fixture", this.fixture.getAbsolutePath());
        if (this.low != null && this.high != null)
        {
            NativeTrueTypeFontRenderer nativeLow = (NativeTrueTypeFontRenderer) this.low.getRenderer();
            out.addProperty("width1", this.low.getWidth(SAMPLE));
            out.addProperty("width3", this.high.getWidth(SAMPLE));
            out.addProperty("height", this.low.getHeight());
            out.addProperty("lineHeight", this.low.getLineHeight());
            out.addProperty("oversample1", nativeLow.getOversample());
            out.addProperty("oversample3", ((NativeTrueTypeFontRenderer) this.high.getRenderer()).getOversample());
            out.addProperty("atlasPages", nativeLow.getAtlasCount());
            out.addProperty("sameRoundedKey", this.low == this.manager.get(this.link, 20, 1.4F));
            out.addProperty("hasCyrillic", nativeLow.hasGlyph(0x416) && nativeLow.hasGlyph(0x451));
            out.addProperty("formatWidth", this.low.getWidth("\u00a7cПривет\u00a7r") == this.low.getWidth("Привет"));
            out.addProperty("boldWider", this.low.getWidth("\u00a7lПривет") > this.low.getWidth("Привет"));
            JsonArray wrapped = new JsonArray();
            List<String> lines = this.low.wrap("\u00a7aПеренос слов сохраняет цвет, кириллицу и дробные интервалы между символами.", 330);
            for (String line : lines) wrapped.add(line);
            out.add("wrapped", wrapped);
        }
        return out;
    }
}
