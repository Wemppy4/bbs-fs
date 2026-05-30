package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.UUID;

public class UIModelPreviewPanel extends UIElement
{
    public UITexturePainter painter;
    public UIFormRenderer renderer;
    public UIIcon close;
    
    private ModelForm form;
    private Link fakeLink;

    public UIModelPreviewPanel(UITexturePainter painter)
    {
        super();

        this.painter = painter;
        this.fakeLink = Link.create("bbs_mod:temp_model_preview_" + UUID.randomUUID().toString());
        
        this.form = new ModelForm();
        this.form.texture.set(this.fakeLink);

        this.renderer = new UIFormRenderer();
        this.renderer.form = this.form;
        this.renderer.relative(this).w(1F).h(1F);

        this.close = new UIIcon(Icons.CLOSE, (b) -> this.painter.closeModelPreview());
        this.close.relative(this).x(1F, -4).y(4).w(16).h(16).anchorX(1F);

        this.add(this.renderer, this.close);
    }

    public void setModel(String model)
    {
        this.form.model.set(model);
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.chromeSurface());
        this.area.render(context.batcher, BBSSettings.backgroundTint(Colors.A6));

        UITextureEditor editor = this.painter.getCurrentEditor();
        Texture temporary = editor != null ? editor.getTemporaryTexture() : null;

        if (temporary != null)
        {
            BBSModClient.getTextures().textures.put(this.fakeLink, temporary);
        }
        else
        {
            BBSModClient.getTextures().textures.remove(this.fakeLink);
        }

        super.render(context);
    }

    public void cleanUp()
    {
        BBSModClient.getTextures().textures.remove(this.fakeLink);
    }
}
