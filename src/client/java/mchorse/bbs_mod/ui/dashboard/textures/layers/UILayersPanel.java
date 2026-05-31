package mchorse.bbs_mod.ui.dashboard.textures.layers;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureEditor;
import mchorse.bbs_mod.ui.dashboard.textures.UITexturePainter;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.colors.Colors;

public class UILayersPanel extends UIElement
{
    private UITexturePainter painter;
    private UIScrollView list;
    private UIIcon addLayer;
    private UIIcon addImage;
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

        this.addLayer = new UIIcon(Icons.ADD, (b) -> this.addLayer());
        this.addLayer.tooltip(UIKeys.TEXTURES_LAYERS_ADD);
        this.addImage = new UIIcon(Icons.IMAGE, (b) -> this.addImageLayer());
        this.addImage.tooltip(UIKeys.TEXTURES_LAYERS_ADD_IMAGE);
        
        this.opacity = new UITrackpad((v) -> {
            if (this.currentEditor != null && this.currentEditor.activeLayerIndex >= 0) {
                this.currentEditor.layers.get(this.currentEditor.activeLayerIndex).opacity = v.floatValue() / 100F;
                this.currentEditor.dirty();
            }
        });
        this.opacity.limit(0, 100).integer().setValue(100);
        this.opacity.tooltip(UIKeys.TEXTURES_LAYERS_OPACITY);

        UIElement controls = UI.row(this.addLayer, this.addImage, this.opacity);
        controls.relative(this).x(UIConstants.MARGIN).y(1F, -UIConstants.CONTROL_HEIGHT - 5).w(1F, -UIConstants.MARGIN * 2).h(UIConstants.CONTROL_HEIGHT);

        this.add(label, this.list, controls);
    }

    public void setEditor(UITextureEditor editor)
    {
        this.currentEditor = editor;
        this.updateLayers();
    }

    private void addLayer()
    {
        if (this.currentEditor != null && this.currentEditor.getPixels() != null)
        {
            mchorse.bbs_mod.utils.resources.Pixels newPixels = mchorse.bbs_mod.utils.resources.Pixels.fromSize(this.currentEditor.getPixels().width, this.currentEditor.getPixels().height);
            TextureLayer layer = new TextureLayer(UIKeys.TEXTURES_LAYERS_DEFAULT_NAME.format(String.valueOf(this.currentEditor.layers.size() + 1)).get(), newPixels);
            this.currentEditor.layers.add(layer);
            this.currentEditor.activeLayerIndex = this.currentEditor.layers.size() - 1;
            this.currentEditor.setActiveLayer(this.currentEditor.activeLayerIndex);
            this.updateLayers();
            this.currentEditor.dirty();
        }
    }

    private void addImageLayer()
    {
        if (this.currentEditor == null) return;
        
        UITexturePicker.findAllTextures(this.getContext(), null, (path) -> {
            mchorse.bbs_mod.resources.Link link = mchorse.bbs_mod.resources.Link.create(path);
            try {
                mchorse.bbs_mod.utils.resources.Pixels loaded = mchorse.bbs_mod.BBSModClient.getTextures().getPixels(link);
                if (loaded != null) {
                    mchorse.bbs_mod.utils.resources.Pixels newPixels = mchorse.bbs_mod.utils.resources.Pixels.fromSize(
                        Math.max(this.currentEditor.getPixels().width, loaded.width),
                        Math.max(this.currentEditor.getPixels().height, loaded.height)
                    );
                    newPixels.draw(loaded, 0, 0);
                    loaded.delete();
                    
                    TextureLayer layer = new TextureLayer(mchorse.bbs_mod.utils.StringUtils.fileName(path), newPixels);
                    this.currentEditor.layers.add(layer);
                    this.currentEditor.activeLayerIndex = this.currentEditor.layers.size() - 1;
                    this.currentEditor.setActiveLayer(this.currentEditor.activeLayerIndex);
                    
                    
                    this.updateLayers();
                    this.currentEditor.dirty();
                    this.currentEditor.resize();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void updateLayers()
    {
        this.list.removeAll();

        if (this.currentEditor != null)
        {
            for (int i = this.currentEditor.layers.size() - 1; i >= 0; i--)
            {
                TextureLayer layer = this.currentEditor.layers.get(i);
                this.list.add(new UILayerElement(this, layer, i));
            }
            
            if (this.currentEditor.activeLayerIndex >= 0 && this.currentEditor.activeLayerIndex < this.currentEditor.layers.size()) {
                this.opacity.setValue(this.currentEditor.layers.get(this.currentEditor.activeLayerIndex).opacity * 100F);
            }
        }

        this.list.resize();
    }
}