package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;

/**
 * BOBJ keyframe. This class is responsible for holding data about a 
 * keyframe. 
 */
public class BOBJKeyframe
{
    public float frame;
    public float value;
    public Interpolation interpolation = Interpolation.LINEAR;

    /* For bezier interpolation */
    public float leftX;
    public float leftY;

    public float rightX;
    public float rightY;

    /**
     * Parse a keyframe from BOBJ line of tokens
     */
    public static BOBJKeyframe parse(String[] tokens)
    {
        if (tokens.length == 8)
        {
            float leftX = Float.parseFloat(tokens[4]);
            float leftY = Float.parseFloat(tokens[5]);

            float rightX = Float.parseFloat(tokens[6]);
            float rightY = Float.parseFloat(tokens[7]);

            return new BOBJKeyframe(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), tokens[3], leftX, leftY, rightX, rightY);
        }
        else if (tokens.length == 4)
        {
            return new BOBJKeyframe(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]), tokens[3]);
        }
        else if (tokens.length == 3)
        {
            return new BOBJKeyframe(Float.parseFloat(tokens[1]), Float.parseFloat(tokens[2]));
        }

        return null;
    }

    /**
     * Get interpolation from string 
     */
    public static Interpolation interpolationFromString(String interp)
    {
        if (interp.equals("CONSTANT"))
        {
            return Interpolation.CONSTANT;
        }
        else if (interp.equals("BEZIER"))
        {
            return Interpolation.BEZIER;
        }

        return Interpolation.LINEAR;
    }

    public BOBJKeyframe(float frame, float value)
    {
        this.frame = frame;
        this.value = value;
    }

    public BOBJKeyframe(float frame, float value, String interp)
    {
        this(frame, value);

        this.interpolation = interpolationFromString(interp);
    }

    public BOBJKeyframe(float frame, float value, String interp, float leftX, float leftY, float rightX, float rightY)
    {
        this(frame, value, interp);

        this.leftX = leftX;
        this.leftY = leftY;
        this.rightX = rightX;
        this.rightY = rightY;
    }

    /**
     * Interpolations. These enums provide different interpolation types.
     */
    public static enum Interpolation
    {
        CONSTANT(Interpolations.CONST),
        LINEAR(Interpolations.LINEAR),
        BEZIER(Interpolations.BEZIER);

        public final IInterp interp;

        Interpolation(IInterp interp)
        {
            this.interp = interp;
        }
    }
}