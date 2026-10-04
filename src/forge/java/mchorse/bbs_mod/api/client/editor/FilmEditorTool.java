package mchorse.bbs_mod.api.client.editor;

import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.graphics.WorldRenderContext;

/** Per-controller tool. BBS attaches it as a child, so hidden transform editors have a UI
 * context and get normal removal cleanup. Hooks run in registration order; the first consumed
 * input wins. Return null from target/startGizmo to leave the built-in tool in control.
 * target is called only for an editable actor, never behind a covering editor or while recording. */
public abstract class FilmEditorTool extends UIElement
{
    public FilmTarget target(FilmTarget original) { return null; }
    public Boolean startGizmo(UIContext context, int stencilIndex) { return null; }
    public boolean click(UIContext context) { return false; }
    public boolean release(UIContext context) { return false; }
    public boolean key(UIContext context) { return false; }
    public void updateTool(UIContext context) {}
    public void renderTool(WorldRenderContext context) {}
    public void stopTool() {}
}
