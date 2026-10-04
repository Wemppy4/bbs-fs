package mchorse.bbs_mod.cubic.model.loaders;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.PNGEncoder;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.resources.Pixels;
import mchorse.bbs_mod.vox.VoxBuilder;
import mchorse.bbs_mod.vox.VoxDocument;
import mchorse.bbs_mod.vox.VoxReader;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;

public class VoxModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link modelVox = IModelLoader.getLink(model.combine("model.vox"), links, ".vox");
        Link palette = IModelLoader.getLink(model.combine("palette.png"), links, ".png");
        Model newModel = new Model(models.parser);

        try (InputStream asset = models.provider.getAsset(modelVox))
        {
            VoxReader reader = new VoxReader();
            VoxDocument document = reader.read(asset, modelVox);

            newModel.textureWidth = document.palette.length;
            newModel.textureHeight = 1;

            for (VoxDocument.LimbNode node : document.generate())
            {
                ModelGroup group = new ModelGroup(node.name);
                VoxBuilder builder = new VoxBuilder(node.translation, node.rotation);

                group.initial.translate.set(node.translation.x, node.translation.z, node.translation.y);
                group.meshes.add(builder.build(node.chunk));
                newModel.topGroups.add(group);
            }

            newModel.initialize();
            this.ensurePalette(models.provider, document, modelVox, palette);

            ModelInstance modelInstance = new ModelInstance(id, newModel, new Animations(models.parser), palette);
            modelInstance.setSourceFile(modelVox);

            modelInstance.applyConfig(config);

            return modelInstance;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private void ensurePalette(AssetProvider provider, VoxDocument document, Link vox, Link pallete)
    {
        File paletteFile = provider.getFile(pallete);
        File voxFile = provider.getFile(vox);

        if (paletteFile.exists())
        {
            try
            {
                BasicFileAttributes voxAttributes = Files.readAttributes(voxFile.toPath(), BasicFileAttributes.class);
                BasicFileAttributes paletteAttributes = Files.readAttributes(paletteFile.toPath(), BasicFileAttributes.class);
                int compare = paletteAttributes.lastModifiedTime().compareTo(voxAttributes.lastModifiedTime());

                /* If palette is older than vox, then it needs to be regenerated */
                if (compare >= 0)
                {
                    return;
                }
            }
            catch (Exception e)
            {
                return;
            }
        }

        Pixels pixels = Pixels.fromSize(document.palette.length, 1);

        for (int x = 0; x < document.palette.length; x++)
        {
            pixels.setColor(x, 0, new Color().set(document.palette[x], false));
        }

        try
        {
            PNGEncoder.writeToFile(pixels, paletteFile);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        pixels.delete();
    }
}
