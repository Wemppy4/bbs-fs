package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.ui.dashboard.textures.frames.UIFramesPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UISplitter;

/**
 * One pane of the painter's canvas column: a texture's canvas, and under it the strip of its
 * frames while the texture is animated.
 *
 * <p>The painter has two of them, side by side while the screen is split. Each keeps a strip of
 * its own, so moving the work from one texture to the other never shifts either canvas.</p>
 */
public class UITexturePane extends UIElement
{
    private static final int DEFAULT_FRAMES_HEIGHT = 90;
    private static final int MIN_FRAMES_HEIGHT = 44;
    private static final int MAX_FRAMES_HEIGHT = 300;

    public final UIFramesPanel frames;

    private final UIElement canvas;
    private final UIElement framesHost;
    private final UISplitter framesDraggable;

    /** The tab's editor this pane shows, and the editor actually on show — its material map's, while it shows that. */
    private UITextureEditor texture;
    private UITextureEditor editor;

    public UITexturePane(UITexturePainter painter)
    {
        /* The strip sits along the bottom, collapsed to nothing while the texture isn't animated */
        this.framesHost = new UIElement();
        this.framesHost.relative(this).y(1F).w(1F).h(0).anchorY(1F);
        this.framesHost.setVisible(false);

        this.frames = new UIFramesPanel(painter);
        this.frames.relative(this.framesHost).w(1F).h(1F);
        this.framesHost.add(this.frames);

        this.framesDraggable = UISplitter.pixels("texture_painter.frames", DEFAULT_FRAMES_HEIGHT, MIN_FRAMES_HEIGHT, MAX_FRAMES_HEIGHT);
        this.framesDraggable.measure(this).vertical().fromEnd().onChange(() ->
        {
            this.framesHost.h(this.framesDraggable.getPixels());
            this.resize();
            this.framesDraggable.resize();
        });
        this.framesDraggable.relative(this.framesHost).x(0.5F).y(0F).w(40).h(6).anchor(0.5F, 0.5F);
        this.framesDraggable.setVisible(false);

        /* The canvas takes what the strip leaves: its height is measured to the strip's top, so
         * the strip has to be laid out first */
        this.canvas = new UIElement();
        this.canvas.relative(this).w(1F).hTo(this.framesHost.area, 0F);

        this.add(this.framesHost, this.canvas, this.framesDraggable);
    }

    public UITextureEditor getTexture()
    {
        return this.texture;
    }

    public UITextureEditor getEditor()
    {
        return this.editor;
    }

    /**
     * Shows {@code texture} here, whichever of its maps it has on show, or nothing. The editor
     * that was here before is detached, not freed: its owner keeps it for its tab.
     */
    public void setTexture(UITextureEditor texture)
    {
        UITextureEditor editor = texture == null ? null : texture.getActiveEditor();

        /* Another picker may have taken it away in the meantime; then it isn't this pane's to remove */
        if (this.editor != null && this.editor != editor && this.editor.getParent() == this.canvas)
        {
            this.canvas.remove(this.editor);
        }

        if (editor != null && editor.getParent() != this.canvas)
        {
            editor.removeFromParent();
            this.canvas.add(editor);
            editor.full(this.canvas);
        }

        this.texture = texture;

        /* Pointing the strip anew stops what it plays, so while the editor stays the strip only
         * catches up with it (another picker may have changed its frames) */
        if (editor != this.editor)
        {
            this.editor = editor;
            this.frames.setEditor(editor);
        }
        else
        {
            this.frames.sync();
        }

        this.updateFrames();
    }

    /** The strip is there for an animated texture and gone otherwise; the canvas takes what's left. */
    public void updateFrames()
    {
        boolean animated = this.editor != null && this.editor.isAnimated();

        this.framesHost.h(animated ? this.framesDraggable.getPixels() : 0);
        this.framesHost.setVisible(animated);
        this.framesDraggable.setVisible(animated);
        this.resize();
    }
}
