package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.Optional;

public class BBSShaders
{
    private static ShaderProgram model;
    private static ShaderProgram multiLink;
    private static ShaderProgram subtitles;
    private static ShaderProgram selection;
    private static ShaderProgram pixelArt;
    private static ShaderProgram pixelArtText;
    private static ShaderProgram pixelArtTextIntensity;
    private static ShaderProgram markupOccluded;
    private static ShaderProgram selectionOutline;

    private static ShaderProgram pickerPreview;
    private static ShaderProgram pickerBillboard;
    private static ShaderProgram pickerBillboardNoShading;
    private static ShaderProgram pickerParticles;
    private static ShaderProgram pickerModels;

    static
    {
        setup();
    }

    public static void setup()
    {
        if (model != null) model.close();
        if (subtitles != null) subtitles.close();
        if (selection != null) selection.close();
        if (pixelArt != null) pixelArt.close();
        if (pixelArtText != null) pixelArtText.close();
        if (pixelArtTextIntensity != null) pixelArtTextIntensity.close();
        if (markupOccluded != null) markupOccluded.close();
        if (selectionOutline != null) selectionOutline.close();

        if (pickerPreview != null) pickerPreview.close();
        if (pickerBillboard != null) pickerBillboard.close();
        if (pickerBillboardNoShading != null) pickerBillboardNoShading.close();
        if (pickerParticles != null) pickerParticles.close();
        if (pickerModels != null) pickerModels.close();

        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            model = new ShaderProgram(factory, "model", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            multiLink = new ShaderProgram(factory, "multilink", VertexFormats.POSITION_TEXTURE_COLOR);
            subtitles = new ShaderProgram(factory, "subtitles", VertexFormats.POSITION_TEXTURE_COLOR);
            selection = new ShaderProgram(factory, "selection", VertexFormats.POSITION_TEXTURE_COLOR);

            pickerPreview = new ShaderProgram(factory, "picker_preview", VertexFormats.POSITION_TEXTURE_COLOR);
            pickerBillboard = new ShaderProgram(factory, "picker_billboard", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
            pickerBillboardNoShading = new ShaderProgram(factory, "picker_billboard_no_shading", VertexFormats.POSITION_TEXTURE_LIGHT_COLOR);
            pickerParticles = new ShaderProgram(factory, "picker_particles", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pickerModels = new ShaderProgram(factory, "picker_models", VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        /* Kept in a try of their own: these are a cosmetic nicety, and a driver
         * that refuses to compile them must not take the shaders the editor
         * can't work without (bone picking, models) down with them. Everything
         * asking for these falls back to vanilla's programs when they're null. */
        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            pixelArt = new ShaderProgram(factory, "pixelart", VertexFormats.POSITION_TEXTURE_COLOR);
            pixelArtText = new ShaderProgram(factory, "pixelart_text", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
            pixelArtTextIntensity = new ShaderProgram(factory, "pixelart_text_intensity", VertexFormats.POSITION_COLOR_TEXTURE_LIGHT);
        }
        catch (IOException e)
        {
            /* All or nothing: text must not end up half swapped */
            if (pixelArt != null) pixelArt.close();
            if (pixelArtText != null) pixelArtText.close();
            if (pixelArtTextIntensity != null) pixelArtTextIntensity.close();

            pixelArt = pixelArtText = pixelArtTextIntensity = null;

            e.printStackTrace();
        }

        /* Its own try too: without it the markup's world lines just stop hiding behind blocks */
        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            markupOccluded = new ShaderProgram(factory, "markup_occluded", VertexFormats.POSITION_TEXTURE_COLOR);
        }
        catch (IOException e)
        {
            markupOccluded = null;

            e.printStackTrace();
        }

        /* And this one: without it the selected actor just isn't outlined */
        try
        {
            ResourceFactory factory = new ProxyResourceFactory(MinecraftClient.getInstance().getResourceManager());

            selectionOutline = new ShaderProgram(factory, "selection_outline", VertexFormats.POSITION_TEXTURE);
        }
        catch (IOException e)
        {
            selectionOutline = null;

            e.printStackTrace();
        }
    }

    /** The outline around the film's selected actor, or null when it didn't compile. */
    public static ShaderProgram getSelectionOutlineProgram()
    {
        return selectionOutline;
    }

    /** The markup's lines that hide behind the world (see SceneDepth), or null when it didn't compile. */
    public static ShaderProgram getMarkupOccludedProgram()
    {
        return markupOccluded;
    }

    public static ShaderProgram getModel()
    {
        return model;
    }

    public static ShaderProgram getMultilinkProgram()
    {
        return multiLink;
    }

    public static ShaderProgram getSubtitlesProgram()
    {
        return subtitles;
    }

    public static ShaderProgram getSelectionProgram()
    {
        return selection;
    }

    /**
     * Textured UI quads with the seam between texels smoothed, for when the
     * interface is drawn at a fractional scale (see the shader's own comment).
     */
    public static ShaderProgram getPixelArtProgram()
    {
        return pixelArt;
    }

    public static ShaderProgram getPixelArtTextProgram()
    {
        return pixelArtText;
    }

    public static ShaderProgram getPixelArtTextIntensityProgram()
    {
        return pixelArtTextIntensity;
    }

    public static ShaderProgram getPickerPreviewProgram()
    {
        return pickerPreview;
    }

    public static ShaderProgram getPickerBillboardProgram()
    {
        return pickerBillboard;
    }

    public static ShaderProgram getPickerBillboardNoShadingProgram()
    {
        return pickerBillboardNoShading;
    }

    public static ShaderProgram getPickerParticlesProgram()
    {
        return pickerParticles;
    }

    public static ShaderProgram getPickerModelsProgram()
    {
        return pickerModels;
    }

    private static class ProxyResourceFactory implements ResourceFactory
    {
        private ResourceManager manager;

        public ProxyResourceFactory(ResourceManager manager)
        {
            this.manager = manager;
        }

        @Override
        public Optional<Resource> getResource(Identifier id)
        {
            if (id.getPath().contains("/core/"))
            {
                return this.manager.getResource(new Identifier(BBSMod.MOD_ID, id.getPath()));
            }

            /* #moj_import always resolves in the minecraft namespace, so our own
             * includes have to be looked up in BBS first, vanilla's second */
            if (id.getPath().contains("/include/"))
            {
                Optional<Resource> resource = this.manager.getResource(new Identifier(BBSMod.MOD_ID, id.getPath()));

                if (resource.isPresent())
                {
                    return resource;
                }
            }

            return this.manager.getResource(id);
        }
    }
}
