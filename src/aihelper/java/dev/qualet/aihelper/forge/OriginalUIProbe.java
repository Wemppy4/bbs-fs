package dev.qualet.aihelper.forge;

import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.opengl.GL11;
import java.util.LinkedHashMap;
import java.util.Map;

/** Test fixture for the original BBS widgets; excluded from the distributed mod. */
public class OriginalUIProbe extends UIBaseMenu
{
    private final Map<String, UIElement> elements = new LinkedHashMap<>();
    private final UITextbox input = new UITextbox();
    private final UITrackpad number = new UITrackpad();
    private final UIToggle toggle = new UIToggle(IKey.constant("Анимация"), null);
    private final UIScrollView list = new UIScrollView();
    private int clicks;
    private int contextActions;

    public OriginalUIProbe()
    {
        UIElement column = UI.column(8, 12);
        column.relative(this.main).xy(16, 16).w(300).h(1F, -32);
        this.main.add(column);
        UIButton button = new UIButton(UIKeys.GENERAL_SAVE, b -> clicks++);
        button.context(menu -> menu.action(Icons.COPY, UIKeys.GENERAL_COPY, () -> contextActions++));
        button.tooltip(IKey.constant("Исходная кнопка BBS и её контекстное меню"));
        UIButton overlay = new UIButton(IKey.constant("Открыть окно"), b -> UIOverlay.addOverlay(this.context,
            new UIMessageOverlayPanel(IKey.constant("BBS FS"), IKey.constant("Проверка исходного диалога: русский текст, перенос строк, размытие и закрытие через Escape."))));
        input.setText("Вемпи");
        number.setValue(12.5D);
        list.h(150);
        list.column(4).vertical().stretch().scroll();
        for (int i = 0; i < 30; i++) list.add(new UIButton(IKey.constant("Дорожка " + (i + 1)), null));
        column.add(UI.label(IKey.constant("BBS FS — проверка компонентов")), input, number, button, toggle, overlay, list);
        elements.put("input", input); elements.put("number", number); elements.put("save", button);
        elements.put("toggle", toggle); elements.put("overlay", overlay); elements.put("list", list);
    }

    @Override public boolean canPause() { return false; }
    @Override protected void preRenderMenu(UIRenderingContext render)
    {
        render.batcher.box(8, 8, 324, height - 8, BBSSettings.baseSurface());
        render.batcher.icon(Icons.MORPH, 354, 32);
        render.batcher.icon(Icons.CAMERA, 378, 32);
        render.batcher.icon(Icons.GEAR, 402, 32);
        render.batcher.text("1.20.4 UI / Forge 1.12.2", 354, 64, Colors.WHITE);
    }

    public JsonObject snapshot()
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true); out.addProperty("text", input.getText());
        out.addProperty("number", number.getValue()); out.addProperty("toggle", toggle.getValue());
        out.addProperty("clicks", clicks); out.addProperty("contextActions", contextActions);
        out.addProperty("contextMenu", context.contextMenu != null);
        out.addProperty("overlays", overlay.getChildren().size());
        out.addProperty("scroll", list.scroll.getScroll());
        out.addProperty("scale", BBSModClient.getGUIScale());
        out.addProperty("width", width); out.addProperty("height", height);
        out.addProperty("glError", GL11.glGetError());
        JsonObject areas = new JsonObject();
        for (Map.Entry<String, UIElement> entry : elements.entrySet())
        {
            mchorse.bbs_mod.ui.utils.Area area = entry.getValue().area;
            JsonObject box = new JsonObject();
            box.addProperty("x", area.x); box.addProperty("y", area.y);
            box.addProperty("w", area.w); box.addProperty("h", area.h);
            areas.add(entry.getKey(), box);
        }
        if (context.contextMenu != null)
        {
            JsonObject box = new JsonObject();
            box.addProperty("x", context.contextMenu.area.x); box.addProperty("y", context.contextMenu.area.y);
            box.addProperty("w", context.contextMenu.area.w); box.addProperty("h", context.contextMenu.area.h);
            areas.add("context", box);
        }
        out.add("areas", areas);
        return out;
    }
}
