package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.texture.AnimatedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureManager;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.dashboard.textures.data.Document;
import mchorse.bbs_mod.ui.dashboard.textures.data.TextureAnimation;
import mchorse.bbs_mod.ui.forms.editors.utils.UIFormRenderer;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class UIModelPreviewPanel extends UIElement
{
    public UITexturePainter painter;
    public UIFormRenderer renderer;
    public UIIcon close;
    
    private ModelForm form;
    private Link fakeLink;

    /* For an animated texture: its images cut from the strip the way the game cuts them, rebuilt when the document changes */
    private AnimatedTexture animated;
    private int animatedRevision = -1;

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

    /** The model on show, so the picker can open at it. */
    public String getModel()
    {
        return this.form.model.get();
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, BBSSettings.chromeSurface());

        UITextureEditor editor = this.painter.getCurrentEditor();
        Document document = editor == null ? null : editor.getDocument();
        TextureManager textures = BBSModClient.getTextures();

        /* The model shows the frame the canvas shows — the editor's clock, not the game's: click
         * a frame and the model wears it; play, and the two run together */
        Texture frame = document != null && document.animation != null && this.updateAnimated(editor, document)
            ? this.frameOnShow(editor, document)
            : null;

        if (frame != null)
        {
            textures.textures.put(this.fakeLink, frame);
        }
        else
        {
            Texture temporary = editor != null ? editor.getTemporaryTexture() : null;

            this.dropAnimated();

            if (temporary != null)
            {
                textures.textures.put(this.fakeLink, temporary);
            }
            else
            {
                textures.textures.remove(this.fakeLink);
            }
        }

        super.render(context);
    }

    /** The image of the frame on show, out of those cut from the strip; null for a frame pointing past it. */
    private Texture frameOnShow(UITextureEditor editor, Document document)
    {
        List<TextureAnimation.Frame> frames = document.animation.frames;
        int position = editor.getFrame();

        if (position < 0 || position >= frames.size())
        {
            return null;
        }

        int image = frames.get(position).index;

        return image >= 0 && image < this.animated.textures.size() ? this.animated.textures.get(image) : null;
    }

    /**
     * Keep the images in step with the document: cut from the flattened strip the way the game
     * cuts them, by the same {@code .mcmeta}, and rebuilt only when the document changed (or the
     * textures were thrown away under us by a resource reload). False when they can't be made
     * (a strip shorter than a frame), so the still texture shows.
     */
    private boolean updateAnimated(UITextureEditor editor, Document document)
    {
        if (this.animated != null && this.animatedRevision == document.revision && !this.animated.textures.isEmpty() && this.animated.textures.get(0).isValid())
        {
            return true;
        }

        Pixels flat = editor.flattenLayers();

        if (flat == null)
        {
            return false;
        }

        this.dropAnimated();

        try
        {
            String mcmeta = DataToString.toString(document.animation.toMcmeta(document.width, document.height), true);

            this.animated = AnimatedTexture.load(new ByteArrayInputStream(mcmeta.getBytes(StandardCharsets.UTF_8)), flat);
            this.animatedRevision = document.revision;
        }
        catch (Exception e)
        {
            this.animated = null;
        }
        finally
        {
            flat.delete();
        }

        return this.animated != null;
    }

    private void dropAnimated()
    {
        if (this.animated != null)
        {
            /* The manager may hold one of the images as the fake link's texture: not after this */
            BBSModClient.getTextures().textures.remove(this.fakeLink);
            this.animated.delete();
            this.animated = null;
        }

        this.animatedRevision = -1;
    }

    public void cleanUp()
    {
        this.dropAnimated();
        BBSModClient.getTextures().textures.remove(this.fakeLink);
    }
}
