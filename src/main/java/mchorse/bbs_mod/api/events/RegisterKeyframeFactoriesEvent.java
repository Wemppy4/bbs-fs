package mchorse.bbs_mod.api.events;

import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * Posted on both sides once BBS has registered its own keyframe value types.
 *
 * <p>A keyframe factory is what a track's value <em>is</em>: how it reads and writes, how two of
 * them interpolate. Its key is written next to the keyframes, so a track saved with a factory
 * that later goes missing can't be read — which is why BBS keeps such a track as data rather
 * than dropping it, and why the key an addon picks should carry its namespace.</p>
 */
public class RegisterKeyframeFactoriesEvent
{
    public void register(String key, IKeyframeFactory factory)
    {
        KeyframeFactories.FACTORIES.put(key, factory);
    }
}
