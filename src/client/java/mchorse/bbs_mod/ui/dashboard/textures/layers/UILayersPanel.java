package mchorse.bbs_mod.ui.dashboard.textures.layers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.window.ImageClipboard;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureEditor;
import mchorse.bbs_mod.ui.dashboard.textures.UITexturePainter;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureLayer;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.Pixels;

public class UILayersPanel extends UIElement
{
    private UITexturePainter painter;
    private UIScrollView list;
    private UIIcon addLayer;
    private UITrackpad opacity;
    
    public UITextureEditor currentEditor;

    public UILayersPanel(UITexturePainter painter)
    {
        this.painter = painter;

        this.list = UI.scrollView(5, 5);
        this.list.scroll.cancelScrolling();
        this.list.relative(this).y(20).w(1F).h(1F, -UIConstants.CONTROL_HEIGHT * 2 - 25);

        UILabel label = UI.label(UIKeys.TEXTURES_LAYERS).background();
        label.relative(this).w(1F).h(20);
        label.labelAnchor(0.5F, 0.5F);

        this.addLayer = new UIIcon(Icons.ADD, (b) -> this.openAddLayerMenu());
        this.addLayer.tooltip(UIKeys.TEXTURES_LAYERS_ADD);
        this.addLayer.h(UIConstants.CONTROL_HEIGHT);

        this.opacity = new UITrackpad((v) ->
        {
            Document document = this.document();
            TextureLayer active = document == null ? null : document.getActiveLayer();

            if (active != null)
            {
                /* Merge so a continuous opacity drag collapses into a single undo step. */
                this.currentEditor.recordLayerChange("opacity", () ->
                {
                    active.opacity = v.floatValue() / 100F;
                    this.currentEditor.dirty();
                });
            }
        });
        this.opacity.limit(0, 100).integer().setValue(100);
        this.opacity.tooltip(UIKeys.TEXTURES_LAYERS_OPACITY);

        UIElement controls = UI.row(this.addLayer, this.opacity);
        controls.relative(this).x(UIConstants.MARGIN).y(1F, -UIConstants.CONTROL_HEIGHT - 5).w(1F, -UIConstants.MARGIN * 2).h(UIConstants.CONTROL_HEIGHT);

        this.add(label, this.list, controls);
    }

    public void setEditor(UITextureEditor editor)
    {
        this.currentEditor = editor;
        this.updateLayers();
    }

    private Document document()
    {
        return this.currentEditor == null ? null : this.currentEditor.getDocument();
    }

    /** Opens the "Add layer..." menu: empty layer, layer from a texture, and (if selected) from selection. */
    private void openAddLayerMenu()
    {
        if (this.currentEditor == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.ADD, UIKeys.TEXTURES_LAYERS_ADD_EMPTY, this::addLayer);
            menu.action(Icons.IMAGE, UIKeys.TEXTURES_LAYERS_ADD_IMAGE, this::addImageLayer);

            if (ImageClipboard.hasImage())
            {
                menu.action(Icons.PASTE, UIKeys.TEXTURES_LAYERS_CONTEXT_PASTE, () -> this.currentEditor.pasteImage());
            }

            if (this.currentEditor.hasSelection())
            {
                menu.action(Icons.OUTLINE, UIKeys.TEXTURES_LAYERS_FROM_SELECTION, this::addLayerFromSelection);
            }
        });
    }

    private void addLayer()
    {
        Document document = this.document();

        if (document != null)
        {
            this.currentEditor.recordLayerChange(null, () ->
            {
                Pixels newPixels = Pixels.fromSize(document.width, document.height);
                TextureLayer layer = new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(String.valueOf(document.layers.size() + 1)).get(), newPixels);

                document.layers.add(layer);
                this.currentEditor.setActiveLayer(document.layers.size() - 1);
                this.updateLayers();
                this.currentEditor.dirty();
            });
        }
    }

    private void addLayerFromSelection()
    {
        if (this.currentEditor != null)
        {
            this.currentEditor.createLayerFromSelection();
            this.updateLayers();
        }
    }

    private void addImageLayer()
    {
        if (this.currentEditor == null) return;
        
        UITexturePicker.findAllTextures(this.getContext(), null, (path) ->
        {
            Link link = Link.create(path);

            try
            {
                Pixels loaded = BBSModClient.getTextures().getPixels(link);

                if (loaded != null)
                {
                    this.currentEditor.recordLayerChange(null, () ->
                    {
                        Document document = this.currentEditor.getDocument();

                        /* Document-sized layer with a zero offset; the image is cropped to the
                         * document if it is larger. */
                        Pixels newPixels = Pixels.fromSize(document.width, document.height);
                        newPixels.draw(loaded, 0, 0);
                        loaded.delete();

                        TextureLayer layer = new TextureLayer(StringUtils.fileName(path), newPixels);

                        document.layers.add(layer);
                        this.currentEditor.setActiveLayer(document.layers.size() - 1);

                        this.updateLayers();
                        this.currentEditor.dirty();
                    });
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    public void updateLayers()
    {
        this.list.removeAll();

        Document document = this.document();

        if (document != null)
        {
            for (int i = document.layers.size() - 1; i >= 0; i--)
            {
                this.list.add(new UILayerElement(this, document.layers.get(i), i));
            }

            TextureLayer active = document.getActiveLayer();

            if (active != null)
            {
                this.opacity.setValue(active.opacity * 100F);
            }
        }

        this.list.resize();
    }
}