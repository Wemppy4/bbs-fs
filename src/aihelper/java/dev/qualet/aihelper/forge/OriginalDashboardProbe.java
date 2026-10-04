package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.*;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.*;
import mchorse.bbs_mod.ui.framework.elements.overlay.*;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.onboarding.UITour;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.utils.DataPath;
import org.lwjgl.opengl.GL11;

/** Read-only inspection of the actual F6 Dashboard. Does not create a test menu or mutate data. */
public final class OriginalDashboardProbe
{
    public static JsonObject snapshot()
    {
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        UIBaseMenu menu = UIScreen.getCurrentMenu();
        out.addProperty("menu", menu == null ? "" : menu.getClass().getSimpleName());
        out.addProperty("glError", GL11.glGetError());
        if (!(menu instanceof UIDashboard)) return out;
        UIDashboard dashboard = (UIDashboard) menu;
        out.addProperty("built", dashboard.isFullyBuilt());
        out.addProperty("width", dashboard.width); out.addProperty("height", dashboard.height);
        out.addProperty("mouseX", menu.context.mouseX); out.addProperty("mouseY", menu.context.mouseY);
        out.add("settings", area(dashboard.settings));
        UIDashboardPanels host = dashboard.getPanels();
        out.addProperty("selectedPanel", host.panel == null ? "" : host.panel.getClass().getSimpleName());
        if (host.panel instanceof UIDataDashboardPanel)
        {
            UIDataDashboardPanel<?> data = (UIDataDashboardPanel<?>) host.panel;
            out.addProperty("dataType", data.getType().getId());
            out.addProperty("dataId", data.getData() == null ? "" : data.getData().getId());
        }
        if (host.panel instanceof mchorse.bbs_mod.ui.film.UIFilmPanel)
        {
            mchorse.bbs_mod.ui.film.UIFilmPanel film = (mchorse.bbs_mod.ui.film.UIFilmPanel) host.panel;
            mchorse.bbs_mod.utils.VideoRecorder recorder = mchorse.bbs_mod.BBSModClient.getVideoRecorder();
            JsonObject state = new JsonObject();
            state.addProperty("cursor", film.getCursor());
            state.addProperty("duration", film.getData() == null ? 0 : film.getData().calculateDuration());
            state.addProperty("replays", film.getData() == null ? 0 : film.getData().replays.getList().size());
            state.addProperty("running", film.isRunning());
            state.addProperty("exporting", film.recorder.isExporting());
            state.addProperty("recording", recorder.isRecording());
            state.addProperty("frames", recorder.getCounter());
            state.addProperty("failure", recorder.getFailure() == null ? "" : recorder.getFailure().toString());
            state.add("exportButton", area(film.preview.recordVideo));
            state.add("playButton", area(film.preview.plause));
            state.addProperty("displayWidth", net.minecraft.client.Minecraft.getMinecraft().displayWidth);
            state.addProperty("displayHeight", net.minecraft.client.Minecraft.getMinecraft().displayHeight);
            state.addProperty("videoWidth", mchorse.bbs_mod.client.BBSRendering.getVideoWidth());
            state.addProperty("videoHeight", mchorse.bbs_mod.client.BBSRendering.getVideoHeight());
            state.addProperty("frameRate", mchorse.bbs_mod.BBSSettings.videoFrameRate.get());
            java.io.File folder = new java.io.File(mchorse.bbs_mod.BBSSettings.videoExportPath.get());
            if (!folder.isDirectory()) folder = new java.io.File(mchorse.bbs_mod.BBSMod.getSettingsFolder().getParentFile(), "movies");
            state.addProperty("videoFolder", folder.getAbsolutePath());
            out.add("film", state);
        }
        JsonArray panels = new JsonArray();
        java.util.List<UIIcon> icons = host.panelButtons.getChildren(UIIcon.class);
        for (int i = 0; i < host.panels.size(); i++)
        {
            UIDashboardPanel panel = host.panels.get(i);
            JsonObject entry = new JsonObject(); entry.addProperty("type", panel.getClass().getSimpleName());
            entry.addProperty("selected", host.panel == panel);
            if (i < icons.size()) entry.add("button", area(icons.get(i)));
            panels.add(entry);
        }
        out.add("panels", panels);
        JsonArray overlays = new JsonArray();
        for (UIOverlayPanel panel : menu.getRoot().getChildren(UIOverlayPanel.class))
        {
            if (!panel.canBeSeen()) continue;
            JsonObject entry = new JsonObject(); entry.addProperty("type", panel.getClass().getSimpleName());
            entry.add("area", area(panel)); entry.add("close", area(panel.close));
            if (panel instanceof UISettingsOverlayPanel)
            {
                UISettingsOverlayPanel settings = (UISettingsOverlayPanel) panel;
                entry.add("search", area(settings.search)); entry.addProperty("filter", settings.search.getText());
                entry.add("options", area(settings.options));
            }
            if (panel instanceof UICRUDOverlayPanel)
            {
                UICRUDOverlayPanel crud = (UICRUDOverlayPanel) panel;
                entry.add("add", area(crud.add)); entry.add("dupe", area(crud.dupe));
                entry.add("rename", area(crud.rename)); entry.add("remove", area(crud.remove));
                entry.add("search", area(crud.names.search)); entry.add("list", area(crud.namesList));
                entry.addProperty("filter", crud.names.search.getText());
                DataPath selected = crud.namesList.getCurrentFirst();
                entry.addProperty("selected", selected == null ? "" : selected.toString());
                JsonArray names = new JsonArray();
                for (DataPath name : crud.namesList.getList()) names.add(name.toString());
                entry.add("names", names);
            }
            JsonArray fields = new JsonArray();
            for (UITextbox field : panel.getChildren(UITextbox.class))
                if (field.canBeSeen()) { JsonObject f=area(field); f.addProperty("text", field.getText()); fields.add(f); }
            entry.add("fields", fields);
            JsonArray buttons = new JsonArray();
            for (UIButton button : panel.getChildren(UIButton.class))
                if (button.canBeSeen()) { JsonObject b=area(button); b.addProperty("label", button.label.get()); buttons.add(b); }
            entry.add("buttons", buttons);
            overlays.add(entry);
        }
        out.add("overlays", overlays);
        JsonArray tours = new JsonArray();
        for (UITour tour : menu.getRoot().getChildren(UITour.class))
            for (UIIcon close : tour.getChildren(UIIcon.class)) if (close.canBeSeen()) tours.add(area(close));
        out.add("tours", tours);
        return out;
    }
    private static JsonObject area(UIElement element)
    {
        JsonObject result = new JsonObject();
        result.addProperty("x", element.area.x); result.addProperty("y", element.area.y);
        result.addProperty("w", element.area.w); result.addProperty("h", element.area.h);
        result.addProperty("visible", element.canBeSeen()); result.addProperty("enabled", element.isEnabled());
        return result;
    }
}
