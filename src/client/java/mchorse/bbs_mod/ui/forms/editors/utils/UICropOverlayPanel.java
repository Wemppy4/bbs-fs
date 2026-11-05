package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import org.joml.Vector4f;

public class UICropOverlayPanel extends UIOverlayPanel
{
    public UICropEditor cropEditor;

    public UICropOverlayPanel(Link texture, Vector4f crop)
    {
        super(UIKeys.FORMS_CROP_TITLE);
        this.wh(0.5F, 0.5F);

        Texture t = BBSModClient.getTextures().getTexture(texture);

        int w = t.width;
        int h = t.height;

        this.cropEditor = new UICropEditor();
        this.cropEditor.fill(texture, crop);
        this.cropEditor.setSize(w, h);
        this.cropEditor.full(this.content);
        this.cropEditor.scaleX.setZoom(1F / (Math.max(w, h) / 128F));
        this.cropEditor.scaleY.setZoom(1F / (Math.max(w, h) / 128F));
        this.content.add(this.cropEditor);
    }
}
