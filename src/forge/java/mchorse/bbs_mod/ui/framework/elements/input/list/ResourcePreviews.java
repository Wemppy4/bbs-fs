package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.renderer.RenderHelper;
import java.util.HashMap;
import java.util.Map;

/** Reuse the existing texture and form renderers; no thumbnail files or background jobs. */
public class ResourcePreviews
{
    public static UIList.Preview<String> particles()
    {
        Map<String, ParticleForm> forms = new HashMap<>();

        return (context, name, x, y, size) ->
        {
            ParticleForm form = forms.computeIfAbsent(name, key ->
            {
                ParticleForm particle = new ParticleForm();
                particle.effect.set(key);
                return particle;
            });
            FormUtilsClient.renderPreview(form, context, x, y, x + size, y + size);
        };
    }

    public static UIList.Preview<String> models()
    {
        Map<String, ModelForm> forms = new HashMap<>();

        return (context, name, x, y, size) ->
        {
            ModelForm form = forms.computeIfAbsent(name, key ->
            {
                ModelForm model = new ModelForm();
                model.model.set(key);
                return model;
            });
            context.batcher.flush();
            RenderHelper.enableGUIStandardItemLighting();
            try
            {
                FormUtilsClient.renderPreview(form, context, x, y, x + size, y + size);
            }
            finally
            {
                context.batcher.flush();
                RenderHelper.disableStandardItemLighting();
            }
        };
    }

    public static void texture(UIContext context, String path, int x, int y, int size)
    {
        Texture texture = BBSModClient.getTextures().getTexture(Link.create(path));

        if (texture == null || texture == BBSModClient.getTextures().getError())
        {
            context.batcher.icon(Icons.IMAGE, Colors.WHITE, x + size / 2F, y + size / 2F, 0.5F, 0.5F);
            return;
        }

        float scale = Math.min(size / (float) Math.max(1, texture.width), size / (float) Math.max(1, texture.height));
        int w = Math.max(1, Math.round(texture.width * scale));
        int h = Math.max(1, Math.round(texture.height * scale));
        x += (size - w) / 2;
        y += (size - h) / 2;
        context.batcher.iconArea(Icons.CHECKBOARD, x, y, w, h);
        context.batcher.fullTexturedBox(texture, x, y, w, h);
    }
}
