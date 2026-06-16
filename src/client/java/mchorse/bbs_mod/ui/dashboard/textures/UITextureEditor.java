package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
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
import java.util.Deque;
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
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_EXPORT,
            UIKeys.TEXTURES_SAVE,
            this::saveTextureAs
        );

        UIOverlay.addOverlay(this.getContext(), panel);

        panel.text.setText(this.getTexture().toString());
        panel.text.textbox.selectFilename();
    }

    /** Called from UITexturePainter resize icon. Opens the resize overlay. */
    public void openResizeOverlay()
    {
        if (this.document == null || this.document.layers.isEmpty())
        {
            return;
        }
        UIResizeTextureOverlayPanel overlayPanel = new UIResizeTextureOverlayPanel(this.w, this.h, (size) ->
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
    }

    @Override
    protected void wasChanged()
    {
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

    private void saveTextureAs(String path)
    {
        File file = this.writeTexture(Link.create(path));

        if (file == null)
        {
            return;
        }

        UIMessageFolderOverlayPanel panel = new UIMessageFolderOverlayPanel(
            UIKeys.TEXTURES_EXPORT_OVERLAY_TITLE,
            UIKeys.TEXTURES_EXPORT_OVERLAY_SUCCESS.format(file.getName()),
            file.getParentFile()
        );

        panel.folder.tooltip(UIKeys.TEXTURES_EXPORT_OVERLAY_OPEN_FOLDER, Direction.LEFT);

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    /**
     * Validates {@code link}, flattens the layers and writes them to a PNG on disk, clearing the
     * dirty flag and firing the rename/save callbacks. Returns the written file on success, or
     * {@code null} after notifying the user about a wrong path or an I/O failure.
     */
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