package mchorse.bbs_mod.cubic.data.animation;

import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.math.Constant;
import mchorse.bbs_mod.math.molang.MolangParser;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.math.molang.expressions.MolangValue;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.List;

public class AnimationPart implements IMapSerializable
{
    public final KeyframeChannel<MolangExpression> x = new KeyframeChannel<>("x", null);
    public final KeyframeChannel<MolangExpression> y = new KeyframeChannel<>("y", null);
    public final KeyframeChannel<MolangExpression> z = new KeyframeChannel<>("z", null);

    public final KeyframeChannel<MolangExpression> sx = new KeyframeChannel<>("sx", null);
    public final KeyframeChannel<MolangExpression> sy = new KeyframeChannel<>("sy", null);
    public final KeyframeChannel<MolangExpression> sz = new KeyframeChannel<>("sz", null);

    public final KeyframeChannel<MolangExpression> rx = new KeyframeChannel<>("rx", null);
    public final KeyframeChannel<MolangExpression> ry = new KeyframeChannel<>("ry", null);
    public final KeyframeChannel<MolangExpression> rz = new KeyframeChannel<>("rz", null);

    public final List<KeyframeChannel<MolangExpression>> channels = java.util.Arrays.asList(this.x, this.y, this.z, this.sx, this.sy, this.sz, this.rx, this.ry, this.rz);

    private MolangParser parser;

    public AnimationPart(MolangParser parser)
    {
        this.parser = parser;
    }

    @Override
    public void fromData(MapType data)
    {
        if (data.has("translate")) parseChannel(this.x, this.y, this.z, data.get("translate"), MolangParser.ZERO);
        if (data.has("scale")) parseChannel(this.sx, this.sy, this.sz, data.get("scale"), MolangParser.ONE);
        if (data.has("rotate")) parseChannel(this.rx, this.ry, this.rz, data.get("rotate"), MolangParser.ZERO);
    }

    private void parseChannel(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, BaseType data, MolangExpression defaultValue)
    {
        if (BaseType.isList(data))
        {
            for (BaseType keyframe : (ListType) data)
            {
                this.parseAnimationVector(x, y, z, keyframe, defaultValue);
            }
        }

        x.sort();
        y.sort();
        z.sort();
    }

    private void parseAnimationVector(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z, BaseType data, MolangExpression defaultValue)
    {
        ListType values = (ListType) data;

        if (values.size() >= 5)
        {
            double time = values.getDouble(0) * 20F;
            IInterp interp = AnimationInterpolation.byName(values.getString(1));

            int xIndex = x.insert((float) time, this.parseValue(this.parser, values.get(2), defaultValue));
            int yIndex = y.insert((float) time, this.parseValue(this.parser, values.get(3), defaultValue));
            int zIndex = z.insert((float) time, this.parseValue(this.parser, values.get(4), defaultValue));

            x.get(xIndex).getInterpolation().setInterp(interp);
            y.get(yIndex).getInterpolation().setInterp(interp);
            z.get(zIndex).getInterpolation().setInterp(interp);
        }
    }

    private MolangExpression parseValue(MolangParser parser, BaseType element, MolangExpression defaultValue)
    {
        if (element.isNumeric())
        {
            return new MolangValue(parser, new Constant(element.asNumeric().doubleValue()));
        }

        try
        {
            return parser.parseExpression(((StringType) element).value);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return defaultValue;
    }

    @Override
    public void toData(MapType data)
    {
        data.put("translate", this.serializeChannel(this.x, this.y, this.z));
        data.put("scale", this.serializeChannel(this.sx, this.sy, this.sz));
        data.put("rotate", this.serializeChannel(this.rx, this.ry, this.rz));
    }

    private ListType serializeChannel(KeyframeChannel<MolangExpression> x, KeyframeChannel<MolangExpression> y, KeyframeChannel<MolangExpression> z)
    {
        ListType list = new ListType();

        /* TODO: for (AnimationVector keyframe : channel.keyframes)
        {
            ListType keyframeList = new ListType();

            keyframeList.addDouble(keyframe.time);
            keyframeList.addString(AnimationInterpolation.toName(keyframe.interp));
            keyframeList.add(keyframe.x.toData());
            keyframeList.add(keyframe.y.toData());
            keyframeList.add(keyframe.z.toData());

            list.add(keyframeList);
        } */

        return list;
    }
}
