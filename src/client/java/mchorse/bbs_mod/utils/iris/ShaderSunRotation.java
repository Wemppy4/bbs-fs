package mchorse.bbs_mod.utils.iris;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderSunRotation
{
    public static final String UNIFORM = "bbs_sunHorizontalRotation";

    private static final Pattern SUN_VECTOR = Pattern.compile("\\bvec3\\s*\\(\\s*-\\s*sin\\s*\\(\\s*(\\w+)\\s*\\)\\s*,\\s*cos\\s*\\(\\s*\\1\\s*\\)\\s*\\*\\s*sunRotationData\\d*\\s*\\)");
    private static final Pattern END_SUN_VECTOR = Pattern.compile("\\bvec3\\s*\\(\\s*0\\.0\\s*,\\s*sunRotationData\\d*\\s*\\*\\s*2000\\.0\\s*\\)");
    private static final String ROTATION_FUNCTION = """
        uniform float bbs_sunHorizontalRotation;

        vec3 bbs_rotateSunVector(vec3 direction)
        {
            float angle = radians(bbs_sunHorizontalRotation);
            float c = cos(angle);
            float s = sin(angle);

            return vec3(c * direction.x + s * direction.z, direction.y, c * direction.z - s * direction.x);
        }

        """;

    public static String processSource(String source)
    {
        if (source.contains("bbs_rotateSunVector"))
        {
            return source;
        }

        Matcher sunVector = SUN_VECTOR.matcher(source);
        Matcher endSunVector = END_SUN_VECTOR.matcher(source);

        if (!sunVector.find() && !endSunVector.find())
        {
            return source;
        }

        /* Complementary reconstructs these world-space directions instead of using Iris's sunPosition. */
        source = sunVector.replaceAll("bbs_rotateSunVector($0)");
        source = END_SUN_VECTOR.matcher(source).replaceAll("bbs_rotateSunVector($0)");

        int version = source.indexOf("#version");
        int newLine = source.indexOf('\n', version);

        return source.substring(0, newLine + 1) + ROTATION_FUNCTION + source.substring(newLine + 1);
    }
}
