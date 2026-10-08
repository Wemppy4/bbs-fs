package mchorse.bbs_mod.api.client.editor;

import mchorse.bbs_mod.film.replays.tracks.TrackId;
import mchorse.bbs_mod.film.replays.tracks.TrackKind;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.keys.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.*;

/** Ordered startup registry shared by film and animation-state timelines. */
public final class TrackCategories
{
    private record Entry(TrackCategory category, BiPredicate<TrackId, Boolean> matches) {}
    private static final List<TrackCategory> categories = new ArrayList<>(List.of(
        TrackCategory.REPLAY, TrackCategory.FORM));
    private static final List<Entry> addons = new ArrayList<>();
    private static final List<KeyCombo> shortcuts = new ArrayList<>();
    private static boolean frozen;

    private TrackCategories() {}

    /** Addon rules run before the built-in fallback; first matching registration wins. */
    public static void register(TrackCategory category, BiPredicate<TrackId, Boolean> matches)
    {
        if (frozen) throw new IllegalStateException("Register categories during RegisterTrackCategoriesEvent");
        Objects.requireNonNull(category);
        Objects.requireNonNull(matches);
        if (categories.stream().anyMatch(value -> value.id.equals(category.id)))
            throw new IllegalArgumentException("Duplicate track category: " + category.id);
        categories.add(category);
        addons.add(new Entry(category, matches));
    }

    public static List<TrackCategory> values()
    {
        return List.copyOf(categories);
    }

    /** Owned is false for the recording's own channels, true for form tracks. */
    public static TrackCategory categoryOf(TrackId track, boolean owned)
    {
        if (track != null)
            for (Entry entry : addons)
                if (entry.matches.test(track, owned)) return entry.category;
        TrackKind kind = track == null ? null : track.kind();

        if (kind != null)
        {
            if (kind == TrackKind.PROPERTY && (track.subject().startsWith("splines/")
                || Set.of("ik", "physics", "spline_ik", "wind", "shake").contains(track.subject())))
            {
                return TrackCategory.FORM;
            }

            switch (kind)
            {
                case IK_CONTROLS, IK_TARGET, POLE_TARGET, PHYSICS_CONTROLS, PHYSICS_TARGET,
                    WIND_CONTROLS, BONE, BONE_CONSTRAINT:
                    return TrackCategory.FORM;
                case MATERIAL_TEXTURE, MATERIAL_PROP:
                    return TrackCategory.FORM;
                default:
                    break;
            }
        }

        /* What is left is a plain property track — a curated replay channel (which belongs to no form)
         * or one of the form's own properties. */
        if (!owned)
        {
            return TrackCategory.REPLAY;
        }


        return TrackCategory.FORM;
    }

    /** Called once after category registration, before loading keybind settings. */
    public static void finishRegistration()
    {
        if (frozen) return;
        frozen = true;
        shortcuts.addAll(List.of(Keys.REPLAYS_TAB_1, Keys.REPLAYS_TAB_2, Keys.REPLAYS_TAB_3,
            Keys.REPLAYS_TAB_4, Keys.REPLAYS_TAB_5));
        for (int i = shortcuts.size(); i < categories.size(); i++)
        {
            int[] keys = i < 9 ? new int[] {GLFW.GLFW_KEY_1 + i}
                : i == 9 ? new int[] {GLFW.GLFW_KEY_0} : new int[0];
            KeyCombo combo = new KeyCombo("tab_" + (i + 1),
                L10n.lang("bbs.ui.film.replays.tab_number").format(i + 1), keys).categoryKey("replays_editor");
            shortcuts.add(combo);
            KeybindSettings.register(combo);
        }
    }

    /** Slots follow visible button order. Hidden categories consume no numeric slot. */
    public static void registerShortcuts(KeybindManager manager, Supplier<List<TrackCategory>> visible,
        Consumer<TrackCategory> select)
    {
        for (int i = 0; i < shortcuts.size(); i++)
        {
            final int index = i;
            manager.register(shortcuts.get(i), () -> {
                List<TrackCategory> current = visible.get();
                if (index < current.size()) select.accept(current.get(index));
            }).category(UIKeys.FILM_REPLAY_TITLE).active(() -> index < visible.get().size());
        }
    }
}
