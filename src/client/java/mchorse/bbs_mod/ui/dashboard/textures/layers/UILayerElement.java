package mchorse.bbs_mod.ui.dashboard.textures.layers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureLayer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;

public class UILayerElement extends UIElement
{
    private UILayersPanel panel;
    public TextureLayer layer;
    public int index;

    private UILabel name;
    private UIIcon visible;

    public UILayerElement(UILayersPanel panel, TextureLayer layer, int index)
    {
        this.panel = panel;
        this.layer = layer;
        this.index = index;

        this.h(20);
        
        this.visible = new UIIcon(layer.visible ? Icons.VISIBLE : Icons.INVISIBLE, (b) ->
        {
            this.layer.visible = !this.layer.visible;
            this.visible.both(this.layer.visible ? Icons.VISIBLE : Icons.INVISIBLE);
            this.panel.currentEditor.dirty();
        });
        this.visible.w(20);

        this.name = UI.label(IKey.raw(layer.name));
        this.name.h(20);
        this.name.labelAnchor(0, 0.5F);

        UIElement row = UI.row(0, 0, 20, this.visible, this.name);
        row.relative(this).w(1F).h(1F);
        this.add(row);
        
        this.context(this::createContextMenu);
    }

    private void createContextMenu(ContextMenuManager menu)
    {
        if (this.panel.currentEditor == null) return;

        boolean canMoveUp = this.index < this.panel.currentEditor.getDocument().layers.size() - 1;
        boolean canMoveDown = this.index > 0;
        boolean canDelete = this.panel.currentEditor.getDocument().layers.size() > 1;

        if (canMoveUp)
        {
            menu.action(Icons.MOVE_UP, UIKeys.TEXTURES_LAYERS_CONTEXT_MOVE_UP, () ->
            {
                TextureLayer current = this.panel.currentEditor.getDocument().layers.remove(this.index);
                this.panel.currentEditor.getDocument().layers.add(this.index + 1, current);

                if (this.panel.currentEditor.getDocument().activeLayerIndex == this.index)
                {
                    this.panel.currentEditor.getDocument().activeLayerIndex++;
                }
                else if (this.panel.currentEditor.getDocument().activeLayerIndex == this.index + 1)
                {
                    this.panel.currentEditor.getDocument().activeLayerIndex--;
                }

                this.panel.currentEditor.setActiveLayer(this.panel.currentEditor.getDocument().activeLayerIndex);
                this.panel.updateLayers();
                this.panel.currentEditor.dirty();
            });
        }

        if (canMoveDown)
        {
            menu.action(Icons.MOVE_DOWN, UIKeys.TEXTURES_LAYERS_CONTEXT_MOVE_DOWN, () ->
            {
                TextureLayer current = this.panel.currentEditor.getDocument().layers.remove(this.index);

                this.panel.currentEditor.getDocument().layers.add(this.index - 1, current);

                if (this.panel.currentEditor.getDocument().activeLayerIndex == this.index)
                {
                    this.panel.currentEditor.getDocument().activeLayerIndex--;
                }
                else if (this.panel.currentEditor.getDocument().activeLayerIndex == this.index - 1)
                {
                    this.panel.currentEditor.getDocument().activeLayerIndex++;
                }

                this.panel.currentEditor.setActiveLayer(this.panel.currentEditor.getDocument().activeLayerIndex);
                this.panel.updateLayers();
                this.panel.currentEditor.dirty();
            });
        }

        menu.action(Icons.OUTLINE, UIKeys.TEXTURES_LAYERS_CONTEXT_SELECT, () ->
        {
            this.panel.currentEditor.setActiveLayer(this.index);
            this.panel.currentEditor.selectLayerBounds();
            this.panel.updateLayers();
            this.panel.currentEditor.dirty();
        });

        menu.action(Icons.EDIT, UIKeys.TEXTURES_LAYERS_CONTEXT_RENAME, () ->
        {
            UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
                UIKeys.TEXTURES_LAYERS_RENAME_TITLE,
                UIKeys.TEXTURES_LAYERS_RENAME_MESSAGE,
                (str) ->
                {
                    if (!str.trim().isEmpty())
                    {
                        this.layer.name = str.trim();
                        this.name.label = IKey.raw(this.layer.name);
                        this.panel.currentEditor.dirty();
                    }
                }
            );
            prompt.text.setText(this.layer.name);
            prompt.text.textbox.moveCursorToEnd();
            UIOverlay.addOverlay(this.getContext(), prompt);
        });

        menu.action(Icons.DUPE, UIKeys.TEXTURES_LAYERS_CONTEXT_DUPE, () -> {
            Pixels newPixels = Pixels.fromSize(this.layer.pixels.width, this.layer.pixels.height);

            newPixels.draw(this.layer.pixels, 0, 0);

            TextureLayer duplicatedLayer = new TextureLayer(UIKeys.TEXTURES_LAYERS_DUPE_SUFFIX.format(this.layer.name).get(), newPixels);
            
            this.panel.currentEditor.getDocument().layers.add(this.index + 1, duplicatedLayer);
            this.panel.currentEditor.setActiveLayer(this.index + 1);
            this.panel.updateLayers();
            this.panel.currentEditor.dirty();
        });

        if (canDelete)
        {
            menu.action(Icons.REMOVE, UIKeys.TEXTURES_LAYERS_CONTEXT_REMOVE, Colors.NEGATIVE, () ->
            {
                this.panel.currentEditor.getDocument().layers.remove(this.index);
                this.layer.delete();

                if (this.panel.currentEditor.getDocument().activeLayerIndex >= this.panel.currentEditor.getDocument().layers.size())
                {
                    this.panel.currentEditor.getDocument().activeLayerIndex = this.panel.currentEditor.getDocument().layers.size() - 1;
                }

                this.panel.currentEditor.setActiveLayer(this.panel.currentEditor.getDocument().activeLayerIndex);
                this.panel.updateLayers();
                this.panel.currentEditor.dirty();
            });
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            this.panel.currentEditor.setActiveLayer(this.index);
            this.panel.updateLayers();

            return true;
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        boolean active = this.panel.currentEditor.getDocument().activeLayerIndex == this.index;
        int color = active ? BBSSettings.primaryColor(Colors.A50) : Colors.A25;
        
        if (this.area.isInside(context))
        {
            color = active ? BBSSettings.primaryColor(Colors.A75): Colors.A50;
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), color);

        super.render(context);
    }
}