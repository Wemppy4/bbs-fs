package dev.qualet.aihelper.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.AddonLifecycle;
import mchorse.bbs_mod.api.BBSAddon;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.BBSApi;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.events.*;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** An actual test addon discovered by Forge's ASM metadata before BBS registration events. */
@BBSAddon
public class OriginalAddonProbe implements BBSAddonMod {
    private static final List<String> events=new ArrayList<>();
    public OriginalAddonProbe() { mark("common_discovered"); }
    private static synchronized void mark(String event) { events.add(event); }
    @Subscribe public void sources(RegisterSourcePacksEvent event) { mark("source_packs"); }
    @Subscribe public void keyframes(RegisterKeyframeFactoriesEvent event) { mark("keyframe_factories"); }
    @Subscribe public void forms(RegisterFormsEvent event) { mark("forms"); }
    @Subscribe public void modifiers(RegisterFormModifiersEvent event) { mark("form_modifiers"); }
    @Subscribe public void camera(RegisterCameraClipsEvent event) { mark("camera_clips"); }
    @Subscribe public void actions(RegisterActionClipsEvent event) { mark("action_clips"); }
    @Subscribe public void settings(RegisterSettingsEvent event) { mark("settings"); }
    @Subscribe public void ready(BBSReadyEvent event) { mark("common_ready"); }

    @BBSAddon(clientOnly=true)
    public static class ClientAddon implements BBSAddonMod {
        public ClientAddon() { mark("client_discovered"); }
        @Subscribe public void l10n(mchorse.bbs_mod.api.client.events.RegisterL10nEvent event) { mark("l10n"); }
        @Subscribe public void models(mchorse.bbs_mod.api.client.events.RegisterModelLoadersEvent event) { mark("model_loaders"); }
        @Subscribe public void sections(mchorse.bbs_mod.api.client.events.RegisterFormSectionsEvent event) { mark("form_sections"); }
        @Subscribe public void tracks(mchorse.bbs_mod.api.client.events.RegisterTrackCategoriesEvent event) { mark("track_categories"); }
        @Subscribe public void keys(mchorse.bbs_mod.api.client.events.RegisterKeybindsEvent event) { mark("keybinds"); }
        @Subscribe public void renderers(mchorse.bbs_mod.api.client.events.RegisterFormRenderersEvent event) { mark("form_renderers"); }
        @Subscribe public void forms(mchorse.bbs_mod.api.client.events.RegisterFormEditorsEvent event) { mark("form_editors"); }
        @Subscribe public void imports(mchorse.bbs_mod.api.client.events.RegisterImportersEvent event) { mark("importers"); }
        @Subscribe public void ready(mchorse.bbs_mod.api.client.events.BBSClientReadyEvent event) { mark("client_ready"); }
    }
    public static synchronized JsonObject run() throws Exception {
        JsonObject out=new JsonObject();out.addProperty("ok",true);out.addProperty("apiVersion",BBSApi.VERSION);
        out.addProperty("modVersion",BBSApi.getModVersion());out.addProperty("registeredAddons",AddonLifecycle.getRegisteredCount());
        JsonArray order=new JsonArray();for(String event:events)order.add(event);out.add("events",order);
        InternalAssetsSourcePack source=new InternalAssetsSourcePack("api_fixture","assets/bbs/assets",BBSMod.class);
        Set<Link> links=new LinkedHashSet<>();source.getLinksFromPath(links,new Link("api_fixture","textures/"),true);
        out.addProperty("addonAssetListing",links.size());
        try(java.io.InputStream stream=source.getAsset(new Link("api_fixture","textures/gun.png"))) { out.addProperty("addonAssetRead",stream.read()==137); }
        boolean rejected=false;try{BBSApi.requireVersion("probe",BBSApi.VERSION+1);}catch(IllegalStateException expected){rejected=true;}
        out.addProperty("versionGuard",rejected);
        return out;
    }
}