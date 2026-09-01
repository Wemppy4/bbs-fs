package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.utils.IOUtils;

import java.util.List;

/**
 * The film editor layouts that ship with the mod, for the welcome screen to offer. Each is a
 * preset file exactly as the layout preset menu saves one, kept in the assets rather than in
 * the user's preset folder — the folder is theirs, and a mod update must not write into it.
 */
public class LayoutPresets
{
    public static final List<Preset> ALL = List.of(
        new Preset("default", UIKeys.ONBOARDING_LAYOUT_DEFAULT, UIKeys.ONBOARDING_LAYOUT_DEFAULT_DESCRIPTION),
        new Preset("bbs_old", UIKeys.ONBOARDING_LAYOUT_BBS_OLD, UIKeys.ONBOARDING_LAYOUT_BBS_OLD_DESCRIPTION),
        new Preset("mini", UIKeys.ONBOARDING_LAYOUT_MINI, UIKeys.ONBOARDING_LAYOUT_MINI_DESCRIPTION),
        new Preset("short", UIKeys.ONBOARDING_LAYOUT_SHORT, UIKeys.ONBOARDING_LAYOUT_SHORT_DESCRIPTION)
    );

    /** One shipped layout: read once, on first use, and kept. */
    public static class Preset
    {
        public final String id;
        public final IKey name;
        public final IKey description;

        private MapType data;
        private EditorLayoutNode tree;

        public Preset(String id, IKey name, IKey description)
        {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        /** The preset as the layout menu would paste it, or null when the asset is missing. */
        public MapType getData()
        {
            if (this.data == null)
            {
                try
                {
                    String string = IOUtils.readText(BBSMod.getProvider().getAsset(Link.assets("presets/layouts/" + this.id + ".json")));

                    this.data = DataToString.mapFromString(string);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            return this.data;
        }

        /** The layout tree, for drawing the schematic; null when the asset is missing. */
        public EditorLayoutNode getTree()
        {
            if (this.tree == null)
            {
                MapType data = this.getData();

                if (data != null && data.has("film_layout"))
                {
                    this.tree = EditorLayoutNode.fromData(data.get("film_layout"));
                }
            }

            return this.tree;
        }
    }
}
