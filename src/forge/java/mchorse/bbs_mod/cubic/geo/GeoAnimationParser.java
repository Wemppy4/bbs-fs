package mchorse.bbs_mod.cubic.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.AnimationInterpolation;
import mchorse.bbs_mod.cubic.data.animation.AnimationPart;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.Map;

public class GeoAnimationParser
{
    public static Animation parse(MolangParser parser, String key, JsonObject object) throws Exception
    {
        Animation animation = new Animation(key, parser);

        if (object.has("animation_length"))
        {
            animation.setLength(object.get("animation_length").getAsDouble());
        }

        if (object.has("bones"))
        {
            for (Map.Entry<String, JsonElement> entry : object.get("bones").getAsJsonObject().entrySet())
            {
                animation.parts.put(entry.getKey(), parsePart(parser, entry.getValue().getAsJsonObject()));
            }
        }

        return animation;
    }

    private static AnimationPart parsePart(MolangParser parser, JsonObject object) throws Exception
    {
        AnimationPart part = new AnimationPart(parser);

        if (object.has("position")) parseChannel(parser, part.x, part.y, part.z, object.get("position"));
        if (object.has("scale")) parseChannel(parser, part.sx, part.sy, part.sz, object.get("scale"));
        if (object.has("rotation")) parseChannel(parser, part.rx, part.ry, part.rz, object.get("rotation"));

        return part;
    }

    private static void parseChannel(MolangParser parser, KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, JsonElement element) throws Exception
    {
        if (element.isJsonArray())
        {
            Vector vector = parseAnimationVector(parser, element);

            if (vector != null)
            {
                insertKeyframe(0F, x, y, z, vector);
            }

            return;
        }

        if (!element.isJsonObject())
        {
            return;
        }

        JsonObject object = element.getAsJsonObject();

        if (object.has("vector"))
        {
            Vector vector = parseAnimationVector(parser, object);

            if (vector != null)
            {
                insertKeyframe(0F, x, y, z, vector);
            }
        }
        else
        {
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
            {
                double time;

                try
                {
                    time = Double.parseDouble(entry.getKey());
                }
                catch (Exception e)
                {
                    continue;
                }

                Vector vector = parseAnimationVector(parser, entry.getValue());

                if (vector != null)
                {
                    insertKeyframe((float) time, x, y, z, vector);
                }
            }
        }

        x.sort();
        y.sort();
        z.sort();
    }

    private static void insertKeyframe(float t, KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, Vector vector)
    {
        insertKeyframe(x, t * 20F, vector.x, vector.interp);
        insertKeyframe(y, t * 20F, vector.y, vector.interp);
        insertKeyframe(z, t * 20F, vector.z, vector.interp);
    }

    private static void insertKeyframe(KeyframeChannel<MolangExpression> channel, float t, MolangExpression e, IInterp interp)
    {
        int index = channel.insert(t, e);
        Keyframe<MolangExpression> kf = channel.get(index);

        kf.getInterpolation().setInterp(interp);
    }

    private static Vector parseAnimationVector(MolangParser parser, JsonElement element) throws Exception
    {
        JsonArray array = element.isJsonArray() ? element.getAsJsonArray() : null;

        if (array == null)
        {
            JsonObject object = element.getAsJsonObject();

            if (object.has("vector"))
            {
                array = element.getAsJsonObject().get("vector").getAsJsonArray();
            }
            else if (object.has("post"))
            {
                if (object.get("post").isJsonArray())
                {
                    array = object.get("post").getAsJsonArray();
                }
                else if (object.get("post").isJsonObject() && object.get("post").getAsJsonObject().has("vector"))
                {
                    array = object.get("post").getAsJsonObject().get("vector").getAsJsonArray();
                }
            }
        }

        if (array == null || array.size() < 3)
        {
            return null;
        }

        Vector vector = new Vector();

        vector.x = parseValue(parser, array.get(0));
        vector.y = parseValue(parser, array.get(1));
        vector.z = parseValue(parser, array.get(2));

        if (element.isJsonObject())
        {
            JsonObject object = element.getAsJsonObject();

            /* Hermite support */
            if (object.has("lerp_mode") && object.get("lerp_mode").isJsonPrimitive() && object.get("lerp_mode").getAsString().equals("catmullrom"))
            {
                vector.interp = Interpolations.HERMITE;
            }
            /* GeckoLib's partial easing support */
            else if (object.has("easing") && object.get("easing").isJsonPrimitive())
            {
                vector.interp = AnimationInterpolation.byName(object.get("easing").getAsString());
            }
        }

        return vector;
    }

    private static MolangExpression parseValue(MolangParser parser, JsonElement element) throws Exception
    {
        JsonPrimitive primitive = element.getAsJsonPrimitive();

        if (primitive.isNumber())
        {
            return new MolangValue(parser, new Constant(primitive.getAsDouble()));
        }

        return parser.parseExpression(primitive.getAsString());
    }

    private static class Vector
    {
        public MolangExpression x;
        public MolangExpression y;
        public MolangExpression z;
        public IInterp interp = Interpolations.LINEAR;
    }
}