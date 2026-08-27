package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.dashboard.textures.undo.PixelsUndo;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PNGEncoder;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.joml.Vector2i;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class UITextureEditor extends UIPixelsEditor
{
    private boolean dirty;

    private Consumer<Link> saveCallback;
    private Consumer<Link> renameCallback;

    public UITextureEditor()
    {
        super();
    }

    /**
     * Saves the document straight to its current path with a success notification and no
     * dialog. Bound to the save icon's left click in {@link UITexturePainter}.
     */
    public void saveCurrentTexture()
    {
        Link link = this.getTexture();

        if (link == null)
        {
            return;
        }

        File file = this.writeTexture(link);

        if (file != null)
        {
            this.getContext().notifySuccess(UIKeys.TEXTURES_SAVE_NOTIFICATION.format(file.getName()));
        }
    }

    /**
     * Opens the "save as" path prompt, letting the user write the document under a different
     * path. Bound to the save icon's context menu in {@link UITexturePainter}.
     */
    public void openSaveOverlay()
    {
        if (this.getTexture() == null)
        {
            return;
        }

        UISaveTextureOverlayPanel panel = new UISaveTextureOverlayPanel(this, (link) ->
        {
            File file = this.saveTo(link);

            if (file != null)
            {
                this.getContext().notifySuccess(UIKeys.TEXTURES_SAVE_NOTIFICATION.format(file.getName()));
            }

            return file != null;
        });

        UIOverlay.addOverlay(this.getContext(), panel, 480, 320);
    }

    /** Called from UITexturePainter resize icon. Opens the resize overlay. */
    public void openResizeOverlay()
    {
        if (this.document == null || this.document.layers.isEmpty())
        {
            return;
        }
        /* The whole document is resized — the strip, not the frame on show */
        UIResizeTextureOverlayPanel overlayPanel = new UIResizeTextureOverlayPanel(this.document.width, this.document.height, (size) ->
        {
            boolean editing = this.isEditing();
            int newW = MathUtils.clamp(size.x, 1, 4096);
            int newH = MathUtils.clamp(size.y, 1, 4096);

            this.setSize(newW, newH);
            this.setDirty(true);
            this.setEditing(editing);
        });

        UIOverlay.addOverlay(this.getContext(), overlayPanel);
    }

    /** Called from UITexturePainter extract icon. Opens the extract frames overlay. */
    public void openExtractOverlay()
    {
        if (this.getTexture() == null || this.getPixels() == null)
        {
            return;
        }
        
        Pixels flattened = this.flattenLayers();
        if (flattened == null)
        {
            return;
        }
        
        UITextureExtractOverlayPanel panel = new UITextureExtractOverlayPanel(this.getTexture(), flattened);
        panel.onClose((e) -> flattened.delete());
        UIOverlay.addOverlay(this.getContext(), panel, 200, 231);
    }

    public UITextureEditor saveCallback(Consumer<Link> saveCallback)
    {
        this.saveCallback = saveCallback;

        return this;
    }

    /**
     * Invoked when a successful save changes the active document's path (Save As),
     * so the owning tab container can update its link and drop any duplicate tab.
     */
    public UITextureEditor renameCallback(Consumer<Link> renameCallback)
    {
        this.renameCallback = renameCallback;

        return this;
    }

    public Link getTexture()
    {
        return this.document == null ? null : this.document.link;
    }

    public boolean isDirty()
    {
        return this.dirty;
    }

    public void dirty()
    {
        this.setDirty(true);
    }

    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;

        /* Whatever dirties the document is a change for the caches to notice (the preview's frames) */
        if (dirty && this.document != null)
        {
            this.document.revision++;
        }
    }

    @Override
    protected void wasChanged()
    {
        super.wasChanged();
        this.dirty();
    }

    @Override
    protected void onFillAt(Vector2i pixel)
    {
        if (!this.isEditing() || this.getPixels() == null)
        {
            return;
        }

        this.fillColor(pixel, this.getActiveDrawColor(), Window.isShiftPressed());
    }

    public void fillColor(Vector2i pixel, Color color, boolean colorReplace)
    {
        /* pixel is in document space; the active layer's buffer is shifted by its move-tool offset,
         * so subtract the offset to index the layer and add it back for document-space checks. */
        int ox = this.getActiveOffsetX();
        int oy = this.getActiveOffsetY();

        PixelsUndo pixelsUndo = new PixelsUndo();
        pixelsUndo.layerIndex = this.document == null ? -1 : this.document.activeLayerIndex;
        Pixels pixels = this.getPixels();
        Color target = pixels.getColor(pixel.x - ox, pixel.y - oy);

        if (target == null)
        {
            return;
        }

        target = target.copy();

        if (colorReplace)
        {
            for (int x = 0; x < pixels.width; x++)
            {
                for (int y = 0; y < pixels.height; y++)
                {
                    if (!this.isInsideSelection(x + ox, y + oy))
                    {
                        continue;
                    }

                    Color current = pixels.getColor(x, y);

                    if (current.getARGBColor() == target.getARGBColor())
                    {
                        if (this.isAlphaLockEnabled() && current.a <= 0F)
                        {
                            continue;
                        }

                        Color c = color;
                        if (this.isAlphaLockEnabled())
                        {
                            c = color.copy();
                            c.a = current.a;
                        }

                        pixelsUndo.setColor(pixels, x, y, c);
                    }
                }
            }
        }
        else
        {
            this.floodFill(pixelsUndo, pixels, pixel.x - ox, pixel.y - oy, target.getARGBColor(), color.getARGBColor(), ox, oy);
        }

        this.undoManager.pushUndo(pixelsUndo);
        this.updateTexture();
    }

    private void floodFill(PixelsUndo undo, Pixels pixels, int x, int y, int targetColor, int replacementColor, int ox, int oy)
    {
        if (targetColor == replacementColor)
        {
            return;
        }

        Deque<Vector2i> queue = new ArrayDeque<>();
        queue.add(new Vector2i(x, y));

        while (!queue.isEmpty())
        {
            Vector2i point = queue.removeFirst();
            int px = point.x;
            int py = point.y;

            if (px < 0 || py < 0 || px >= pixels.width || py >= pixels.height)
            {
                continue;
            }

            if (!this.isInsideSelection(px + ox, py + oy))
            {
                continue;
            }

            Color current = pixels.getColor(px, py);
            if (current == null || current.getARGBColor() != targetColor)
            {
                continue;
            }

            if (this.isAlphaLockEnabled() && current.a <= 0F)
            {
                continue;
            }

            Color c = new Color().set(replacementColor, true);
            if (this.isAlphaLockEnabled())
            {
                c.a = current.a;
            }

            undo.setColor(pixels, px, py, c);

            queue.add(new Vector2i(px + 1, py));
            queue.add(new Vector2i(px - 1, py));
            queue.add(new Vector2i(px, py + 1));
            queue.add(new Vector2i(px, py - 1));
        }
    }

    /*
     * The animation: the frames are the order of showing, the images are the strip's rows. Every
     * operation here is one undo step, and the frame on show follows what was done.
     */

    public boolean isAnimated()
    {
        return this.document != null && this.document.animation != null;
    }

    /**
     * Turn the animation on — the whole texture becomes the one image, shown as the one frame —
     * or off: the order is forgotten (and the {@code .mcmeta} goes on save); the pixels stay.
     */
    public void setAnimated(boolean animated)
    {
        if (this.document == null || animated == this.isAnimated())
        {
            return;
        }

        this.recordLayerChange(null, () ->
        {
            this.document.animation = animated ? TextureAnimation.create(this.document.width, this.document.height) : null;
            this.document.removeAnimationOnSave = !animated;
            this.wasChanged();
        });

        this.setFrame(0);
    }

    /** A new blank frame at {@code position} of the order — a fresh image at the end of the strip — shown at once. */
    public void insertFrame(int position)
    {
        if (!this.isAnimated())
        {
            return;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        int at = MathUtils.clamp(position, 0, frames.size());

        this.recordLayerChange(null, () ->
        {
            this.document.bakeOffsets();
            frames.add(at, new TextureAnimation.Frame(this.document.appendImage(), 0));
            this.afterStripChanged();
        });

        this.setFrame(at);
    }

    /** Copies of the given frames, each right after its original and with an image of its own; the copies, the first of them shown. */
    public List<TextureAnimation.Frame> duplicateFrames(List<TextureAnimation.Frame> selected)
    {
        List<TextureAnimation.Frame> copies = new ArrayList<>();

        if (!this.isAnimated() || selected.isEmpty())
        {
            return copies;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;

        this.recordLayerChange(null, () ->
        {
            this.document.bakeOffsets();

            /* From the end, so the insertions don't shift the positions still to come */
            for (int i = frames.size() - 1; i >= 0; i--)
            {
                TextureAnimation.Frame frame = frames.get(i);

                if (selected.contains(frame))
                {
                    TextureAnimation.Frame copy = new TextureAnimation.Frame(this.document.duplicateImage(frame.index), frame.time);

                    frames.add(i + 1, copy);
                    copies.add(0, copy);
                }
            }

            this.afterStripChanged();
        });

        if (!copies.isEmpty())
        {
            this.setFrame(frames.indexOf(copies.get(0)));
        }

        return copies;
    }

    /**
     * Copies of the given frames (in their order, each with an image of its own) put before the
     * frame now at {@code insertion}, or last — a Ctrl-drag's drop. The copies, the first shown.
     */
    public List<TextureAnimation.Frame> duplicateFramesAt(List<TextureAnimation.Frame> selected, int insertion)
    {
        List<TextureAnimation.Frame> copies = new ArrayList<>();

        if (!this.isAnimated() || selected.isEmpty())
        {
            return copies;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        List<TextureAnimation.Frame> ordered = new ArrayList<>();

        for (TextureAnimation.Frame frame : frames)
        {
            if (selected.contains(frame))
            {
                ordered.add(frame);
            }
        }

        if (ordered.isEmpty())
        {
            return copies;
        }

        int at = MathUtils.clamp(insertion, 0, frames.size());

        this.recordLayerChange(null, () ->
        {
            this.document.bakeOffsets();

            for (TextureAnimation.Frame frame : ordered)
            {
                copies.add(new TextureAnimation.Frame(this.document.duplicateImage(frame.index), frame.time));
            }

            frames.addAll(at, copies);
            this.afterStripChanged();
        });

        this.setFrame(at);

        return copies;
    }

    /**
     * Take frames out of the order; an image nobody shows any more is cut from the strip. The
     * last frame stays put — whether anything went.
     */
    public boolean removeFrames(List<TextureAnimation.Frame> selected)
    {
        if (!this.isAnimated() || selected.isEmpty())
        {
            return false;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        List<TextureAnimation.Frame> going = new ArrayList<>();

        for (TextureAnimation.Frame frame : frames)
        {
            if (selected.contains(frame))
            {
                going.add(frame);
            }
        }

        if (going.isEmpty() || going.size() >= frames.size())
        {
            return false;
        }

        int first = frames.indexOf(going.get(0));

        this.recordLayerChange(null, () ->
        {
            this.document.bakeOffsets();
            frames.removeAll(going);

            /* Images nobody shows any more go too — highest first, so the numbers still to cut don't shift */
            Set<Integer> shown = new HashSet<>();
            List<Integer> orphans = new ArrayList<>();

            for (TextureAnimation.Frame frame : frames)
            {
                shown.add(frame.index);
            }

            for (TextureAnimation.Frame frame : going)
            {
                if (!shown.contains(frame.index) && !orphans.contains(frame.index))
                {
                    orphans.add(frame.index);
                }
            }

            orphans.sort((a, b) -> b - a);

            for (int image : orphans)
            {
                this.document.removeImage(image);
            }

            this.afterStripChanged();
        });

        this.setFrame(Math.min(first, frames.size() - 1));

        return true;
    }

    /** Put frames elsewhere in the order: before the frame now at {@code insertion}, or last. */
    public void moveFrames(List<TextureAnimation.Frame> selected, int insertion)
    {
        if (!this.isAnimated() || selected.isEmpty())
        {
            return;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        List<TextureAnimation.Frame> moving = new ArrayList<>();
        int target = MathUtils.clamp(insertion, 0, frames.size());

        for (int i = 0; i < frames.size(); i++)
        {
            TextureAnimation.Frame frame = frames.get(i);

            if (selected.contains(frame))
            {
                moving.add(frame);

                /* The slot is counted among the frames that stay */
                if (i < target)
                {
                    target--;
                }
            }
        }

        if (moving.isEmpty())
        {
            return;
        }

        int at = target;

        this.recordLayerChange(null, () ->
        {
            frames.removeAll(moving);
            frames.addAll(at, moving);
            this.wasChanged();
        });

        this.setFrame(at);
    }

    /**
     * How long frames stay on show, in ticks; the animation's own frametime (or 0) puts them back
     * on the default. Consecutive changes merge into one undo step, so a drag is one.
     */
    public void setFrameTime(List<TextureAnimation.Frame> selected, int time)
    {
        if (!this.isAnimated() || selected.isEmpty())
        {
            return;
        }

        TextureAnimation animation = this.document.animation;
        int value = time <= 0 || time == animation.frametime ? 0 : time;

        this.recordLayerChange("frame_time", () ->
        {
            for (TextureAnimation.Frame frame : animation.frames)
            {
                if (selected.contains(frame))
                {
                    frame.time = value;
                }
            }

            this.wasChanged();
        });
    }

    /** Ticks a frame lasts unless it says otherwise. Consecutive changes merge into one undo step. */
    public void setFrametime(int ticks)
    {
        if (!this.isAnimated())
        {
            return;
        }

        int value = Math.max(1, ticks);

        if (value == this.document.animation.frametime)
        {
            return;
        }

        this.recordLayerChange("frametime", () ->
        {
            this.document.animation.frametime = value;
            this.wasChanged();
        });
    }

    /**
     * The size of one image of the strip. The strip is re-cut by it: an order that was the plain
     * one (every image once) is made again for the new count; a custom order is kept, and frames
     * of it pointing past the shorter strip show for what they are.
     */
    public void setFrameSize(int width, int height)
    {
        if (!this.isAnimated())
        {
            return;
        }

        TextureAnimation animation = this.document.animation;
        int w = MathUtils.clamp(width, 1, this.document.width);
        int h = MathUtils.clamp(height, 1, this.document.height);

        if (w == this.document.frameWidth() && h == this.document.frameHeight())
        {
            return;
        }

        boolean plain = animation.isDefaultSequence(this.document.width, this.document.height);

        this.recordLayerChange("frame_size", () ->
        {
            animation.width = w;
            animation.height = h;

            if (plain)
            {
                animation.fillDefaultFrames(this.document.width, this.document.height);
            }

            this.wasChanged();
        });

        this.setFrame(Math.min(this.getFrame(), animation.frames.size() - 1));
        this.updateWindow(true);
    }

    /** The given frames' places in the order, filled the other way round. */
    public void reverseFrames(List<TextureAnimation.Frame> selected)
    {
        if (!this.isAnimated() || selected.isEmpty())
        {
            return;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        List<Integer> positions = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++)
        {
            if (selected.contains(frames.get(i)))
            {
                positions.add(i);
            }
        }

        if (positions.size() < 2)
        {
            return;
        }

        this.recordLayerChange(null, () ->
        {
            List<TextureAnimation.Frame> picked = new ArrayList<>();

            for (int position : positions)
            {
                picked.add(frames.get(position));
            }

            for (int i = 0; i < positions.size(); i++)
            {
                frames.set(positions.get(i), picked.get(positions.size() - 1 - i));
            }

            this.wasChanged();
        });

        /* The place on show now holds another frame */
        this.setFrame(this.getFrame());
    }

    /**
     * The way back after a run of frames — the given ones, or the whole order when it's one frame
     * or none: the run's inner frames again, last to first, as frames pointing at the same images
     * (the format's way; no pixels are copied). The frames added, the first of them shown.
     */
    public List<TextureAnimation.Frame> pingPong(List<TextureAnimation.Frame> selected)
    {
        List<TextureAnimation.Frame> added = new ArrayList<>();

        if (!this.isAnimated())
        {
            return added;
        }

        List<TextureAnimation.Frame> frames = this.document.animation.frames;
        List<TextureAnimation.Frame> run = new ArrayList<>();

        for (TextureAnimation.Frame frame : frames)
        {
            if (selected.size() <= 1 || selected.contains(frame))
            {
                run.add(frame);
            }
        }

        if (run.size() < 3)
        {
            return added;
        }

        int after = frames.indexOf(run.get(run.size() - 1));

        for (int i = run.size() - 2; i >= 1; i--)
        {
            TextureAnimation.Frame frame = run.get(i);

            added.add(new TextureAnimation.Frame(frame.index, frame.time));
        }

        this.recordLayerChange(null, () ->
        {
            frames.addAll(after + 1, added);
            this.wasChanged();
        });

        this.setFrame(after + 1);

        return added;
    }

    /** A macro over the whole images the given frames show (each image once), on the active layer. */
    public void applyMacroToFrames(PixelMacro macro, List<TextureAnimation.Frame> selected)
    {
        if (!this.isAnimated())
        {
            return;
        }

        List<int[]> rects = new ArrayList<>();
        Set<Integer> images = new HashSet<>();
        int fw = this.document.frameWidth();
        int fh = this.document.frameHeight();
        int count = this.document.imageCount();

        for (TextureAnimation.Frame frame : selected)
        {
            if (frame.index >= 0 && frame.index < count && images.add(frame.index))
            {
                rects.add(new int[] {0, frame.index * fh, fw, fh});
            }
        }

        this.applyMacro(macro, rects, false);
    }

    /** The layers' buffers were replaced: re-cache the active one's, and count the change. */
    private void afterStripChanged()
    {
        this.setActiveLayer(this.document.activeLayerIndex);
        this.wasChanged();
    }

    /**
     * Validates {@code link}, flattens the layers and writes them to a PNG on disk, clearing the
     * dirty flag and firing the rename/save callbacks. Returns the written file on success, or
     * {@code null} after notifying the user about a wrong path or an I/O failure.
     */
    public File saveTo(Link link)
    {
        return this.writeTexture(link);
    }

    private File writeTexture(Link link)
    {
        if (!Link.isAssets(link) || !link.path.endsWith(".png"))
        {
            this.getContext().notifyError(UIKeys.TEXTURES_SAVE_WRONG_PATH);

            return null;
        }

        File file = BBSMod.getAssetsPath(link.path);

        if (link.path.contains("/"))
        {
            file.getParentFile().mkdirs();
        }

        Pixels pixels = this.flattenLayers();

        try
        {
            /* The animation goes first: the watchdog re-reads the texture per file, and by the time
             * it reads the PNG the .mcmeta has to be beside it already */
            this.writeAnimation(file);
            PNGEncoder.writeToFile(pixels, file);

            this.setDirty(false);

            if (!link.equals(this.document.link))
            {
                this.document.link = link;

                if (this.renameCallback != null)
                {
                    this.renameCallback.accept(link);
                }
            }

            /* Persist the editable document (layers, opacity, etc.) next to the texture as
             * NAME_INCLUDING_EXTENSION.dat so re-opening restores the full layer stack. */
            this.document.write(Document.datFile(file));

            if (this.saveCallback != null)
            {
                this.saveCallback.accept(link);
            }

            return file;
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.getContext().notifyError(UIKeys.TEXTURES_EXPORT_OVERLAY_ERROR.format(file.getName()));

            return null;
        }
        finally
        {
            if (pixels != null)
            {
                pixels.delete();
            }
        }
    }

    /**
     * Write the {@code .mcmeta} beside the texture, or remove it when the user turned the
     * animation off. A texture that was never animated leaves whatever is on disk alone.
     */
    private void writeAnimation(File file)
    {
        if (this.document.animation != null)
        {
            this.document.animation.write(file, this.document.width, this.document.height);
        }
        else if (this.document.removeAnimationOnSave)
        {
            File mcmeta = TextureAnimation.file(file);

            if (mcmeta.isFile())
            {
                mcmeta.delete();
            }
        }

        this.document.removeAnimationOnSave = false;
    }

    /**
     * Adopt the document to edit. The editor takes ownership of the document and its layer
     * resources (freed on {@link #deleteTexture()}); the document already carries its link.
     */
    @Override
    public void setDocument(Document document)
    {
        super.setDocument(document);

        this.setDirty(false);
        this.setEditing(true);
    }

    @Override
    protected Texture getRenderTexture(UIContext context)
    {
        if (this.isEditing())
        {
            return super.getRenderTexture(context);
        }

        Texture original = context.render.getTextures().getTexture(this.getTexture());
        
        if (!this.isDirty())
        {
            return original;
        }
        
        return super.getRenderTexture(context);
    }
}