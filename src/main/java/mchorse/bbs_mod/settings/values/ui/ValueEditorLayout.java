package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.MathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dockable layout trees, keyed by layout id, plus a few editor sizes that predate them.
 *
 * <p>Ids are opaque to this class: an editor decides what it stores and under which name, so adding
 * one is a call site rather than another field with its own pair of accessors here. Whether two
 * editors share a tree or keep their own is likewise the editor's policy &mdash; this only records
 * which ids were marked as having their own, via {@link #setBound}.
 */
public class ValueEditorLayout extends BaseValue
{
    /** Bumped when the stored shape changes; older data is migrated on read. */
    private static final int VERSION = 1;

    /** Layout ids the film editor stores under. */
    public static final String FILM = "film";
    public static final String FILM_CAMERA = "film.camera";
    public static final String FILM_REPLAY = "film.replay";
    public static final String PARTICLE = "particle";

    private final Map<String, EditorLayoutNode> layouts = new LinkedHashMap<>();
    private final Set<String> bound = new HashSet<>();
    /** Panels the user hid, per layout id; hidden ids are not re-added by the dock's ensure pass. */
    private final Map<String, Set<String>> hiddenByLayout = new LinkedHashMap<>();
    /** Docks left in layout-editing mode, so unlock survives a restart. */
    private final Set<String> unlockedDocks = new HashSet<>();

    private float stateEditorSizeH = 0.7F;
    private float stateEditorSizeV = 0.25F;
    private int keyframeLabelWidth = 120;

    /**
     * Sidebar splitter positions keyed by splitter id (see {@code UISplitter}). The unit is the
     * splitter's own &mdash; a fraction of its parent or pixels &mdash; so this stores floats and
     * leaves the meaning, and the clamping, to the owner.
     */
    private final Map<String, Float> splitSizes = new LinkedHashMap<>();

    public ValueEditorLayout(String id)
    {
        super(id);
    }

    /* Layout trees */

    public EditorLayoutNode getLayout(String id, Supplier<EditorLayoutNode> defaultSupplier)
    {
        EditorLayoutNode root = this.layouts.get(id);

        return root == null ? defaultSupplier.get() : root;
    }

    public void setLayout(String id, EditorLayoutNode root)
    {
        BaseValue.edit(this, (v) ->
        {
            if (root == null)
            {
                this.layouts.remove(id);
            }
            else
            {
                this.layouts.put(id, root);
            }
        });
    }

    /** Whether this id keeps a layout of its own instead of sharing another one. */
    public boolean isBound(String id)
    {
        return this.bound.contains(id);
    }

    /**
     * Marks an id as keeping its own layout, seeding it from {@code seed} so binding starts from
     * what was on screen. Unbinding discards the copy.
     */
    public void setBound(String id, boolean isBound, EditorLayoutNode seed)
    {
        BaseValue.edit(this, (v) ->
        {
            if (isBound)
            {
                this.bound.add(id);

                if (!this.layouts.containsKey(id) && seed != null)
                {
                    this.layouts.put(id, seed);
                }
            }
            else
            {
                this.bound.remove(id);
                this.layouts.remove(id);
            }
        });
    }

    public Set<String> getHiddenPanels(String id)
    {
        Set<String> hidden = this.hiddenByLayout.get(id);

        return hidden == null ? new HashSet<>() : new HashSet<>(hidden);
    }

    public void setHiddenPanels(String id, Set<String> hidden)
    {
        BaseValue.edit(this, (v) ->
        {
            if (hidden == null || hidden.isEmpty())
            {
                this.hiddenByLayout.remove(id);
            }
            else
            {
                this.hiddenByLayout.put(id, new HashSet<>(hidden));
            }
        });
    }

    public boolean isDockUnlocked(String dockId)
    {
        return this.unlockedDocks.contains(dockId);
    }

    public void setDockUnlocked(String dockId, boolean unlocked)
    {
        BaseValue.edit(this, (v) ->
        {
            if (unlocked)
            {
                this.unlockedDocks.add(dockId);
            }
            else
            {
                this.unlockedDocks.remove(dockId);
            }
        });
    }

    /* Editor sizes that are not layout trees, kept here because they ship in the same settings key */

    public void setStateEditorSizeH(float stateEditorSizeH)
    {
        BaseValue.edit(this, (v) -> this.stateEditorSizeH = stateEditorSizeH);
    }

    public void setStateEditorSizeV(float stateEditorSizeV)
    {
        BaseValue.edit(this, (v) -> this.stateEditorSizeV = stateEditorSizeV);
    }

    public float getStateEditorSizeH()
    {
        return MathUtils.clamp(this.stateEditorSizeH, 0.1F, 0.9F);
    }

    public float getStateEditorSizeV()
    {
        return MathUtils.clamp(this.stateEditorSizeV, 0.1F, 0.9F);
    }

    public int getKeyframeLabelWidth()
    {
        return MathUtils.clamp(this.keyframeLabelWidth, 40, 400);
    }

    public void setKeyframeLabelWidth(int keyframeLabelWidth)
    {
        BaseValue.edit(this, (v) -> this.keyframeLabelWidth = MathUtils.clamp(keyframeLabelWidth, 40, 400));
    }

    /* Splitter sizes */

    public float getSplitSize(String key, float defaultValue)
    {
        Float size = this.splitSizes.get(key);

        return size == null ? defaultValue : size;
    }

    public void setSplitSize(String key, float value)
    {
        BaseValue.edit(this, (v) -> this.splitSizes.put(key, value));
    }

    /* Serialization */

    @Override
    public BaseType toData()
    {
        MapType data = new MapType();
        MapType layouts = new MapType();
        ListType bound = new ListType();

        for (Map.Entry<String, EditorLayoutNode> entry : this.layouts.entrySet())
        {
            layouts.put(entry.getKey(), entry.getValue().toData());
        }

        for (String id : this.bound)
        {
            bound.addString(id);
        }

        data.putInt("version", VERSION);
        data.put("layouts", layouts);

        if (!bound.isEmpty())
        {
            data.put("bound", bound);
        }

        MapType hidden = new MapType();

        for (Map.Entry<String, Set<String>> entry : this.hiddenByLayout.entrySet())
        {
            ListType ids = new ListType();

            for (String id : entry.getValue())
            {
                ids.addString(id);
            }

            hidden.put(entry.getKey(), ids);
        }

        if (!hidden.isEmpty())
        {
            data.put("hidden", hidden);
        }

        ListType unlocked = new ListType();

        for (String id : this.unlockedDocks)
        {
            unlocked.addString(id);
        }

        if (!unlocked.isEmpty())
        {
            data.put("unlocked_docks", unlocked);
        }

        data.putFloat("state_editor_size_h", this.stateEditorSizeH);
        data.putFloat("state_editor_size_v", this.stateEditorSizeV);
        data.putInt("keyframe_label_width", this.keyframeLabelWidth);

        MapType splits = new MapType();

        for (Map.Entry<String, Float> entry : this.splitSizes.entrySet())
        {
            splits.putFloat(entry.getKey(), entry.getValue());
        }

        if (!splits.isEmpty())
        {
            data.put("splits", splits);
        }

        return data;
    }

    @Override
    public void fromData(BaseType data)
    {
        this.layouts.clear();
        this.bound.clear();
        this.hiddenByLayout.clear();
        this.unlockedDocks.clear();
        this.splitSizes.clear();

        if (!data.isMap())
        {
            return;
        }

        MapType map = data.asMap();

        if (map.has("layouts"))
        {
            MapType layouts = map.getMap("layouts");

            for (String id : layouts.keys())
            {
                EditorLayoutNode root = EditorLayoutNode.fromData(layouts.get(id));

                if (root != null)
                {
                    this.layouts.put(id, root);
                }
            }

            for (BaseType id : map.getList("bound"))
            {
                if (id != null && id.isString())
                {
                    this.bound.add(id.asString());
                }
            }
        }
        else
        {
            this.readLegacyLayouts(map);
        }

        MapType hiddenMap = map.getMap("hidden");

        for (String id : hiddenMap.keys())
        {
            Set<String> ids = new HashSet<>();

            for (BaseType panelId : hiddenMap.getList(id))
            {
                if (panelId != null && panelId.isString())
                {
                    ids.add(panelId.asString());
                }
            }

            if (!ids.isEmpty())
            {
                this.hiddenByLayout.put(id, ids);
            }
        }

        for (BaseType id : map.getList("unlocked_docks"))
        {
            if (id != null && id.isString())
            {
                this.unlockedDocks.add(id.asString());
            }
        }

        this.stateEditorSizeH = map.getFloat("state_editor_size_h", 0.7F);
        this.stateEditorSizeV = map.getFloat("state_editor_size_v", 0.25F);
        this.keyframeLabelWidth = map.getInt("keyframe_label_width", 120);

        MapType splits = map.getMap("splits");

        for (String key : splits.keys())
        {
            BaseType size = splits.get(key);

            if (BaseType.isNumeric(size))
            {
                this.splitSizes.put(key, size.asNumeric().floatValue());
            }
        }
    }

    /** Reads the pre-{@link #VERSION} shape: one field per editor, plus the ratios that came before. */
    private void readLegacyLayouts(MapType map)
    {
        EditorLayoutNode particle = EditorLayoutNode.fromData(map.get("particle_layout"));

        if (particle != null)
        {
            this.layouts.put(PARTICLE, particle);
        }

        EditorLayoutNode film = EditorLayoutNode.fromData(map.get("film_layout"));

        if (film == null && (map.has("main_size_v") || map.has("editor_size_v")))
        {
            film = migrateLegacyFilmRatios(map.getFloat("main_size_v", 0.66F), map.getFloat("editor_size_v", 0.5F));
        }

        if (film != null)
        {
            this.layouts.put(FILM, film);
        }

        MapType editorLayouts = map.getMap("film_editor_layouts");
        MapType bindings = map.getMap("film_editor_layout_bindings");

        this.readLegacyFilmEditor(editorLayouts, bindings, "camera", FILM_CAMERA);
        this.readLegacyFilmEditor(editorLayouts, bindings, "replay", FILM_REPLAY);
    }

    private void readLegacyFilmEditor(MapType layouts, MapType bindings, String legacyId, String id)
    {
        EditorLayoutNode root = EditorLayoutNode.fromData(layouts.get(legacyId));

        if (root != null)
        {
            this.layouts.put(id, root);
        }

        if (bindings.getBool(legacyId))
        {
            this.bound.add(id);
        }
    }

    /**
     * Settings saved before the layout tree existed only knew two ratios: the main vertical split
     * and the one below it. Rebuild the default tree with those two applied.
     */
    private static EditorLayoutNode migrateLegacyFilmRatios(float mainRatio, float smallRatio)
    {
        EditorLayoutNode root = EditorLayoutNode.defaultFilmLayout();

        if (!(root instanceof EditorLayoutNode.SplitterNode))
        {
            return root;
        }

        EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) root;
        Map<EditorLayoutNode.SplitterNode, Float> ratios = new HashMap<>();

        ratios.put(splitter, mainRatio);

        if (splitter.getSecond() instanceof EditorLayoutNode.SplitterNode)
        {
            ratios.put((EditorLayoutNode.SplitterNode) splitter.getSecond(), smallRatio);
        }

        return EditorLayoutNode.copyWithSplitterRatios(root, ratios);
    }
}
