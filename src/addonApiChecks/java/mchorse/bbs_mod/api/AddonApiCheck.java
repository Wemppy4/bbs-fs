package mchorse.bbs_mod.api;

import mchorse.bbs_mod.cubic.spline.SplineFormCheck;
import mchorse.bbs_mod.cubic.spline.SplineIKDataTest;

import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.client.events.FilmEditEvents;
import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.api.client.events.RegisterTrackStylesEvent;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.forms.FormArchitect;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.forms.editors.UIFormUndoHandler;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks data round trips and real editor/evaluation paths; no native window or simulation. */
public final class AddonApiCheck implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
{
    private static int checks;
    private static final String OLD = "sample_physics_mass";
    private static final String KEY = "sample:mass";

    @Override
    public void onPreLaunch()
    {
        try { main(new String[0]); System.exit(0); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
    }

    public static void main(String[] args) throws Exception
    {
        net.minecraft.SharedConstants.createGameVersion();
        net.minecraft.Bootstrap.initialize();
        var field = BBSMod.class.getDeclaredField("forms");
        field.setAccessible(true);
        FormArchitect architect = new FormArchitect();
        architect.register(new Link("check", "form"), TestForm.class);
        architect.register(new Link("check", "model"), mchorse.bbs_mod.forms.forms.ModelForm.class);
        field.set(null, architect);
        KeyframeFactories.setup();
        FormPropertyAliases.register(OLD, KEY);
        values();
        tracks();
        edits();
        transforms();
        lifecycle();
        categories();
        SplineIKDataTest.main(new String[]{"client"});
        SplineFormCheck.run();
        mchorse.bbs_mod.film.ReplayAnchorCheck.run();
        mchorse.bbs_mod.cubic.ShapeControllerCheck.run();
        System.out.println("AddonApiCheck: " + checks + " checks passed");
    }

    private static void categories() throws Exception
    {
        for (String name : List.of("factoryCameraClips", "factoryActionClips"))
        {
            var factory = BBSMod.class.getDeclaredField(name);
            factory.setAccessible(true);
            factory.set(null, new mchorse.bbs_mod.utils.clips.ClipFactory());
        }
        var language = mchorse.bbs_mod.BBSModClient.class.getDeclaredField("l10n");
        language.setAccessible(true);
        language.set(null, new mchorse.bbs_mod.l10n.L10n());
        var event = new mchorse.bbs_mod.api.client.events.RegisterTrackCategoriesEvent();
        var custom = new mchorse.bbs_mod.api.client.editor.TrackCategory("check:custom",
            mchorse.bbs_mod.ui.utils.icons.Icons.PHYSICS, IKey.constant("Custom"), IKey.constant("Custom tracks"));
        event.register(custom, (track, owned) -> owned && track.subject().startsWith("sample:"));
        require(mchorse.bbs_mod.api.client.editor.TrackCategories.categoryOf(TrackId.property("2/1", KEY), true) == custom,
            "addon category includes nested form property");
        require(mchorse.bbs_mod.ui.film.replays.UIReplaysEditor.categoryOf(TrackId.property("2/1", KEY), true) == custom,
            "editor uses category registry");
        require(mchorse.bbs_mod.api.client.editor.TrackCategories.categoryOf(TrackId.property("", "x"), false)
            == mchorse.bbs_mod.api.client.editor.TrackCategory.REPLAY, "recording category fallback");
        require(mchorse.bbs_mod.api.client.editor.TrackCategories.categoryOf(TrackId.bone("", "arm"), true)
            == mchorse.bbs_mod.api.client.editor.TrackCategory.FORM, "pose shares form category");
        boolean duplicate = false;
        try { event.register(custom, (track, owned) -> true); }
        catch (IllegalArgumentException expected) { duplicate = true; }
        require(duplicate, "duplicate category rejected");
        int extraCategories = 11 - mchorse.bbs_mod.api.client.editor.TrackCategories.values().size();
        for (int i = 0; i < extraCategories; i++)
            event.register(new mchorse.bbs_mod.api.client.editor.TrackCategory("check:extra_" + i,
                custom.icon, custom.label, custom.tooltip), (track, owned) -> false);
        mchorse.bbs_mod.api.client.editor.TrackCategories.finishRegistration();
        var manager = new mchorse.bbs_mod.ui.utils.keys.KeybindManager();
        var visible = new ArrayList<>(List.of(mchorse.bbs_mod.api.client.editor.TrackCategory.FORM, custom));
        var selected = new ArrayList<mchorse.bbs_mod.api.client.editor.TrackCategory>();
        mchorse.bbs_mod.api.client.editor.TrackCategories.registerShortcuts(manager, () -> visible, selected::add);
        require(manager.keybinds.size() == 11, "shortcut slots grow with categories");
        manager.keybinds.get(1).callback.run();
        require(selected.get(selected.size() - 1) == custom, "second shortcut selects second visible category");
        visible.remove(0);
        manager.keybinds.get(0).callback.run();
        require(selected.get(selected.size() - 1) == custom && !manager.keybinds.get(1).isActive(), "hidden categories leave no shortcut gap");
        var comboField = mchorse.bbs_mod.ui.utils.keys.Keybind.class.getDeclaredField("combo");
        comboField.setAccessible(true);
        require(comboField.get(manager.keybinds.get(0)) == mchorse.bbs_mod.ui.Keys.REPLAYS_TAB_1,
            "existing configurable shortcut object preserved");
        require(((mchorse.bbs_mod.ui.utils.keys.KeyCombo) comboField.get(manager.keybinds.get(8))).getMainKey()
            == org.lwjgl.glfw.GLFW.GLFW_KEY_9, "ninth shortcut defaults to 9");
        require(((mchorse.bbs_mod.ui.utils.keys.KeyCombo) comboField.get(manager.keybinds.get(9))).getMainKey()
            == org.lwjgl.glfw.GLFW.GLFW_KEY_0, "tenth shortcut defaults to 0");
        require(((mchorse.bbs_mod.ui.utils.keys.KeyCombo) comboField.get(manager.keybinds.get(10))).keys.isEmpty(),
            "later shortcuts stay configurable without colliding defaults");
        boolean late = false;
        try { event.register(custom, (track, owned) -> true); }
        catch (IllegalStateException expected) { late = true; }
        require(late, "late category registration rejected");
        var bar = new mchorse.bbs_mod.ui.framework.elements.utils.UITimelineCategoryBar(40);
        for (int i = 0; i < 12; i++) bar.add(new mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon(custom.icon, button -> {}));
        require(bar.getWidthForHeight(100) == 80, "many category buttons fit above actions");
    }

    private static void values()
    {
        Form form = new TestForm();
        ValueFloat mass = new ValueFloat(KEY, 0F);
        form.add(mass);
        MapType old = new MapType();
        old.putFloat(OLD, 5F);
        ListType hidden = new ListType(); hidden.addString(OLD);
        old.put("disabled_tracks", hidden);
        form.fromData(old);
        require(mass.get() == 5F, "legacy form value");
        require(form.disabledTracks.get().contains(KEY), "hidden track migration");
        MapType saved = form.toData().asMap();
        require(saved.has(KEY) && !saved.has(OLD), "canonical write");
        Form absent = new TestForm();
        absent.fromData(saved);
        require(absent.toData().asMap().getFloat(KEY) == 5F, "save without addon retains data");
        form.fromData(absent.toData());
        require(mass.get() == 5F, "reinstall round trip");
        old.putFloat(KEY, 9F);
        form.fromData(old);
        require(mass.get() == 9F, "canonical value wins collision");
        ValueGroup unrelated = new ValueGroup("settings");
        ValueFloat setting = new ValueFloat(OLD, 0F); unrelated.add(setting);
        unrelated.fromData(old);
        require(setting.get() == 5F, "unrelated settings are not renamed");
        MapType removed = new MapType(); removed.putFloat("removed_bbs_field", 4F);
        absent.fromData(removed);
        require(!absent.toData().asMap().has("removed_bbs_field"), "removed core fields stay removed");
    }

    private static MapType channel(float value)
    {
        KeyframeChannel<Float> channel = new KeyframeChannel<>("test", KeyframeFactories.FLOAT);
        channel.insert(3.5F, value);
        return channel.toData().asMap();
    }

    private static void tracks()
    {
        FormProperties properties = new FormProperties("properties");
        MapType legacy = new MapType(); legacy.put("abcd1234/" + OLD, channel(7F));
        properties.fromData(legacy);
        TrackId track = TrackId.property("abcd1234", KEY);
        require(properties.has(track), "nested legacy track address");
        require(properties.get(track).getId().equals(track.toKey()), "channel id agrees with track address");
        require(properties.findRecursively(properties.get(track).getPath()) == properties.get(track), "channel path resolves for undo");
        FormProperties copy = new FormProperties("properties"); copy.fromData(properties.toData());
        require(copy.has(track) && ((Keyframe<?>) copy.get(track).getKeyframes().get(0)).getTick() == 3.5F, "track list round trip preserves sub-tick key");
        legacy.put(track.toKey(), channel(11F)); properties.fromData(legacy);
        require(((Number) ((Keyframe<?>) properties.get(track).getKeyframes().get(0)).getValue()).floatValue() == 11F, "canonical legacy channel wins");
        MapType listData = properties.toData().asMap();
        MapType oldTrack = listData.getList("tracks").get(0).asMap().copy().asMap();
        oldTrack.putString("subject", OLD); oldTrack.put("channel", channel(2F));
        listData.getList("tracks").add(oldTrack);
        properties.fromData(listData);
        require(((Number) ((Keyframe<?>) properties.get(track).getKeyframes().get(0)).getValue()).floatValue() == 11F, "canonical structured channel wins");
        oldTrack.getMap("channel").putString("type", "missing:factory");
        oldTrack.putString("future_metadata", "keep me");
        ListType foreign = new ListType(); foreign.add(oldTrack); MapType unknown = new MapType(); unknown.put("tracks", foreign);
        properties.fromData(unknown);
        require(properties.toData().asMap().getList("tracks").get(0).asMap().getString("subject").equals(KEY), "unavailable factory track is renamed and preserved");
        require(properties.toData().asMap().getList("tracks").get(0).asMap().getString("future_metadata").equals("keep me"), "unknown track metadata retained");
        require(FormPropertyAliases.resolve(TrackId.bone("", OLD)).subject().equals(OLD), "bone names are not property ids");
        new RegisterTrackStylesEvent().registerLabel(KEY, IKey.constant("Mass"));
        require(TrackStyle.label(track).get().equals("Mass") && track.toKey().equals("abcd1234/" + KEY), "display label does not rename data");
    }

    private static void edits()
    {
        Film film = new Film();
        List<FilmEditEvents.Cause> causes = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        FilmEditEvents.CHANGED.register((edited, values, cause) ->
        {
            require(edited == film, "edit owner");
            causes.add(cause); sizes.add(values.size());
        });
        UIFormUndoHandler handler = new UIFormUndoHandler(new UIBaseMenu.UIRootElement(null));
        handler.handlePreValues(film.hp, 0); film.hp.set(8F);
        handler.handlePreValues(film.hunger, 0); film.hunger.set(6F);
        require(causes.isEmpty(), "no intermediate drag callback");
        handler.submitUndo(true);
        require(causes.equals(List.of(FilmEditEvents.Cause.EDIT)) && sizes.get(0) == 2, "one batch for two fields");
        handler.getUndoManager().undo(film);
        require(film.hp.get() == 20F && film.hunger.get() == 20F && causes.get(1) == FilmEditEvents.Cause.UNDO && sizes.get(1) == 2, "compound undo batch after values restored");
        handler.getUndoManager().redo(film);
        require(film.hp.get() == 8F && causes.get(2) == FilmEditEvents.Cause.REDO, "redo notification");
        FilmEditEvents.notifyChanges(List.of(new ValueFloat("outside", 0F)), FilmEditEvents.Cause.EDIT);
        require(causes.size() == 3, "non-film edits ignored");
    }

    private static void transforms()
    {
        Form form = new TestForm();
        form.transform.get().translate.set(1, 2, 3);
        form.transform.get().scale.set(2, 3, 4);
        FormPoseEvents.TRANSFORM.register((target, transform, transition) ->
        { if (target == form) { transform.translate.x += 10 * transition; transform.rotate.y += 20; } });
        ProbeRenderer renderer = new ProbeRenderer(form);
        var attachment = new mchorse.bbs_mod.api.client.render.RenderAttachment<Form>();
        attachment.set(renderer, form);
        require(attachment.get(renderer) == form && attachment.get(new ProbeRenderer(form)) == null, "attachments belong to renderer instances");
        attachment.remove(renderer);
        require(attachment.get(renderer) == null, "attachment removal");
        MatrixStack stack = new MatrixStack(); renderer.stack(stack, false, .5F);
        Matrix4f matrix = new Matrix4f(); renderer.matrix(matrix, .5F);
        require(stack.peek().getPositionMatrix().equals(matrix, .00001F), "render and matrix transform agree");
        require(form.transform.get().translate.x == 1F && form.transform.get().rotate.y == 0F, "source animation unchanged");
        int[] parents = {0};
        FormPoseEvents.PARENT_FRAME.register((target, entity, parent, path, transition) ->
        { if (target == form) { parents[0]++; require(parent.equals(new Matrix4f()), "parent before own transform"); } });
        var matrices = renderer.collectMatrices(null, .5F);
        require(matrices.get("").matrix().equals(matrix, .00001F) && parents[0] == 1, "attachment walk sees external transform");
    }

    private static void lifecycle()
    {
        Films films = new Films();
        Controller first = new Controller(films); Controller second = new Controller(films);
        films.add(first); films.add(first); films.add(second);
        films.reset(); films.reset();
        require(first.closed == 1 && second.closed == 1 && films.getControllers().isEmpty(), "reset closes each controller once");
    }
    public static class TestForm extends Form {}

    private static class Controller extends BaseFilmController
    {
        int closed; final Films owner;
        Controller(Films owner) { super(null); this.owner = owner; }
        @Override public Map<String, Integer> getActors() { return Map.of(); }
        @Override public int getTick() { return 0; }
        @Override public void shutdown() { require(this.owner.getControllers().isEmpty(), "detached before shutdown callbacks"); this.closed++; }
    }
    private static class ProbeRenderer extends FormRenderer<Form>
    {
        ProbeRenderer(Form form) { super(form); }
        @Override protected void renderInUI(UIContext context, int a, int b, int c, int d) {}
        void stack(MatrixStack stack, boolean origin, float tick) { this.applyTransforms(stack, origin, tick); }
        void matrix(Matrix4f matrix, float tick) { this.applyTransforms(matrix, tick); }
    }
    private static void require(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
