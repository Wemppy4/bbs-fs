package mchorse.bbs_mod.api.client.events;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;

/**
 * Posted on the client once BBS has registered the keyframe editors of its own value types.
 *
 * <p>An editor is picked by the value's factory, and a property may override that with an editor
 * of its own — that is how two tracks holding the same kind of number get different controls.</p>
 */
public class RegisterKeyframeEditorsEvent
{
    public <T> void register(IKeyframeFactory<T> factory, UIKeyframeFactory.IUIKeyframeFactoryFactory<T> editor)
    {
        UIKeyframeFactory.register(factory, editor);
    }

    public <T> void registerProperty(String property, UIKeyframeFactory.IUIKeyframeFactoryFactory<T> editor)
    {
        UIKeyframeFactory.registerProperty(property, editor);
    }
}
