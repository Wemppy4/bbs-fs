package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.l10n.keys.IKey;

import mchorse.bbs_mod.film.replays.tracks.TrackStyle;
import mchorse.bbs_mod.ui.utils.icons.Icon;

/**
 * Posted on the client once BBS has given its own track properties their colours and icons.
 *
 * <p>Purely how a track looks on a timeline, keyed by the property's own name. A property with no
 * style still animates — it just wears the default blue and no icon.</p>
 */
public class RegisterTrackStylesEvent
{
    public void registerLabel(String property, IKey label)
    {
        TrackStyle.registerLabel(property, label);
    }

    public void register(String property, Icon icon, int color, IKey label)
    {
        this.register(property, icon, color);
        this.registerLabel(property, label);
    }

    public void register(String property, Icon icon, int color)
    {
        TrackStyle.register(property, icon, color);
    }
}
