package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
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
    private Link texture;
    private boolean dirty;

    private Consumer<Link> saveCallback;
    private Consumer<Link> renameCallback;

    public UITextureEditor()
    {
        super();
    }

    /** Called from UITexturePainter save icon. Opens the save path prompt. */
    public void openSaveOverlay()
    {
        this.saveTexture();
    }

    /** Called from UITexturePainter resize icon. Opens the resize overlay. */
    public void openResizeOverlay()
    {
        Pixels pixels = this.getPixels();
        if (pixels == null)
        {
            return;
        }
        UIResizeTextureOverlayPanel overlayPanel = new UIResizeTextureOverlayPanel(pixels.width, pixels.height, (size) ->
        {
            boolean editing = this.isEditing();
            Pixels newPixels = Pixels.fromSize(
                MathUtils.clamp(size.x, 1, 4096),
                MathUtils.clamp(size.y, 1, 4096)
            );

            newPixels.draw(pixels, 0, 0, newPixels.width, newPixels.height);
            pixels.delete();

            this.fillPixels(newPixels);
            this.setDirty(false);
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
        UIOverlay.addOverlay(this.getContext(), new UITextureExtractOverlayPanel(this.getTexture(), this.getPixels()), 200, 231);
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
        return this.texture;
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
        PixelsUndo pixelsUndo = new PixelsUndo();
        Pixels pixels = this.getPixels();
        Color target = pixels.getColor(pixel.x, pixel.y);

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
                    if (!this.isInsideSelection(x, y))
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
            this.floodFill(pixelsUndo, pixels, pixel.x, pixel.y, target.getARGBColor(), color.getARGBColor());
        }

        this.undoManager.pushUndo(pixelsUndo);
        this.updateTexture();
    }

    private void floodFill(PixelsUndo undo, Pixels pixels, int x, int y, int targetColor, int replacementColor)
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

            if (!this.isInsideSelection(px, py))
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

    private void saveTexture()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_EXPORT,
            UIKeys.TEXTURES_SAVE,
            this::saveTexture
        );

        UIOverlay.addOverlay(this.getContext(), panel);

        String text = this.texture.toString();

        panel.text.setText(text);
        panel.text.textbox.selectFilename();
    }

    private void saveTexture(String path)
    {
        Link link = Link.create(path);

        if (!Link.isAssets(link) || !link.path.endsWith(".png"))
        {
            this.getContext().notifyError(UIKeys.TEXTURES_SAVE_WRONG_PATH);

            return;
        }

        File file = BBSMod.getAssetsPath(link.path);

        if (path.contains("/"))
        {
            file.getParentFile().mkdirs();
        }

        Pixels pixels = this.getPixels();

        try
        {
            PNGEncoder.writeToFile(pixels, file);
            UIMessageFolderOverlayPanel panel = new UIMessageFolderOverlayPanel(
                UIKeys.TEXTURES_EXPORT_OVERLAY_TITLE,
                UIKeys.TEXTURES_EXPORT_OVERLAY_SUCCESS.format(file.getName()),
                file.getParentFile()
            );

            panel.folder.tooltip(UIKeys.TEXTURES_EXPORT_OVERLAY_OPEN_FOLDER, Direction.LEFT);

            UIOverlay.addOverlay(this.getContext(), panel);

            this.setDirty(false);

            if (!link.equals(this.texture))
            {
                this.texture = link;

                if (this.renameCallback != null)
                {
                    this.renameCallback.accept(link);
                }
            }

            if (this.saveCallback != null)
            {
                this.saveCallback.accept(link);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();

            this.getContext().notifyError(UIKeys.TEXTURES_EXPORT_OVERLAY_ERROR.format(file.getName()));
        }
    }

    /**
     * Set the document from existing link and pixels. Caller keeps ownership of pixels (no delete).
     */
    public void setDocument(Link link, Pixels pixels)
    {
        this.texture = link;

        this.fillPixels(pixels);
        this.setDirty(false);
        this.setEditing(true);
    }

    @Override
    protected Texture getRenderTexture(UIContext context)
    {
        return this.isEditing() ? super.getRenderTexture(context) : context.render.getTextures().getTexture(this.texture);
    }
}