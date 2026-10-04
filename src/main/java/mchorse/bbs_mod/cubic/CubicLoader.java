package mchorse.bbs_mod.cubic;

import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class CubicLoader
{
    public static MapType loadFile(InputStream stream)
    {
        try
        {
            return DataToString.mapFromString(loadStringFile(stream));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static String loadStringFile(InputStream stream) throws IOException
    {
        String content = IOUtils.readText(stream);

        stream.close();

        return content;
    }

    public LoadingInfo load(MolangParser parser, InputStream stream, String path)
    {
        LoadingInfo info = new LoadingInfo();

        try
        {
            MapType root = loadFile(stream);

            if (root.has("model"))
            {
                info.model = new Model(parser);
                info.model.fromData(root.getMap("model"));
                info.model.initialize();
            }

            if (root.has("animations"))
            {
                info.animations = new Animations(parser);
                info.animations.fromData(root.getMap("animations"));
            }
        }
        catch (Exception e)
        {
            System.err.println("An error happened when parsing BBS model file: " + path);
            e.printStackTrace();
        }

        return info;
    }

    public static MapType toData(IModelInstance model)
    {
        MapType data = new MapType();

        if (model.getModel() instanceof IMapSerializable)
        {
            IMapSerializable serializable = (IMapSerializable) model.getModel();
            data.put("model", serializable.toData());
        }

        if (model.getAnimations() != null)
        {
            data.put("animations", model.getAnimations().toData());
        }

        return data;
    }

    public static class LoadingInfo
    {
        public Animations animations;
        public Model model;
    }
}