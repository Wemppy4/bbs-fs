package mchorse.bbs_mod.ui.film.clips.renderer;

import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.clips.misc.VideoClientClip;
import mchorse.bbs_mod.utils.clips.Clip;

import java.util.HashMap;
import java.util.Map;

public class UIClipRenderers
{
    private UIClipRenderer defaultRenderer;

    private Map<Class, IUIClipRenderer> renderers = new HashMap<>();

    public UIClipRenderers()
    {
        this.defaultRenderer = new UIClipRenderer();

        this.register(AudioClientClip.class, new UIAudioClipRenderer());
        this.register(VideoClientClip.class, new UIAudioClipRenderer());
    }

    public void register(Class key, IUIClipRenderer renderer)
    {
        this.renderers.put(key, renderer);
    }

    public <T extends Clip> IUIClipRenderer<T> get(T clip)
    {
        IUIClipRenderer renderer = this.renderers.get(clip.getClass());

        return renderer == null ? this.defaultRenderer : renderer;
    }
}