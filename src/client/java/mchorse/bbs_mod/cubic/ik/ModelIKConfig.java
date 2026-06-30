package mchorse.bbs_mod.cubic.ik;

import java.util.List;

public record ModelIKConfig(List<Chain> chains)
{
    public static final float DEFAULT_WEIGHT = 1F;
    public static final String DEFAULT_POLE_TARGET = "";
    public static final float DEFAULT_POLE_ANGLE = 0F;
    public static final float DEFAULT_SOFTNESS = 0.05F;
    public static final int DEFAULT_CHAIN_LENGTH = 0;
    public static final boolean DEFAULT_TIP_ROTATION = false;
    public static final boolean DEFAULT_STRETCH = false;

    /**
     * One IK constraint, modeled after Blender: it lives on the {@code tip}
     * bone, reaches {@code target}, spans {@code chainLength} bones up the
     * hierarchy ({@code 0} = up to the root). When {@code pole} is on, the bend is
     * aimed at {@code poleTarget} (a bone the limb keeps pointing its elbow
     * towards); with no pole target the bend side is oriented automatically; when
     * off, the bend is left to the raw position solve. {@code poleAngle} (degrees)
     * then rolls the aimed bend about the limb axis — Blender's pole angle. With
     * {@code tipRotation} on, the tip bone copies the {@code target} controller's
     * orientation (Blender's "use tip rotation") instead of keeping its FK pose.
     * With {@code stretch} on, a chain whose target is pulled past its reach
     * telescopes towards it — every bone slides out along the limb so the gaps
     * between them open up and the tip lands on the controller (no bone scaling).
     */
    public record Chain(String tip, String target, int chainLength, boolean pole, String poleTarget, float poleAngle, float softness, float weight, boolean enabled, boolean tipRotation, boolean stretch)
    {
        public Chain
        {
            tip = tip == null ? "" : tip;
            target = target == null ? "" : target;
            poleTarget = poleTarget == null ? "" : poleTarget;
            chainLength = Math.max(0, chainLength);
            softness = clamp01(softness);
            weight = clamp01(weight);
        }

        private static float clamp01(float value)
        {
            if (value < 0F)
            {
                return 0F;
            }

            return Math.min(value, 1F);
        }
    }
}
