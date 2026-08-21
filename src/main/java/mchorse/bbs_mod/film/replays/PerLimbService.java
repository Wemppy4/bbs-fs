package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.FormUtils;

public class PerLimbService
{
    public static final String POSE_BONES = "pose.bones.";
    public static final String MATERIAL_TEXTURES = "texture.materials.";
    public static final String MATERIAL_PROPS = "materials.";
    public static final String IK_TARGETS = "ik_targets";
    public static final String POLE_TARGETS = "pole_targets";
    public static final String PHYSICS_TARGETS = "physics_targets";

    /**
     * Channel-id stand-in for the whole-form material level, whose material name is empty in the
     * data ({@code ModelForm.materials} keys it by ""). An empty segment would make the id
     * unparseable ({@code materials..color}), so the channel spells it {@code *} — the same
     * sentinel convention as {@code Form.DISABLED_ALL}.
     */
    public static final String MATERIAL_DEFAULT = "*";

    /* Animatable per-material properties (the suffix of a MATERIAL_PROPS channel id). The material
     * name itself may contain dots (OBJ's "Material.001"), so parsing strips a KNOWN suffix instead
     * of splitting on the last dot. */
    public static final String MATERIAL_PROP_COLOR = "color";
    public static final String MATERIAL_PROP_OVERLAY = "color_overlay";
    public static final String MATERIAL_PROP_LIGHTING = "lighting";
    public static final String MATERIAL_PROP_CULLING = "culling";
    public static final String MATERIAL_PROP_SMOOTHNESS = "smoothness";
    public static final String MATERIAL_PROP_METALLIC = "metallic";
    public static final String MATERIAL_PROP_SSS = "sss";
    public static final String MATERIAL_PROP_PIXEL_EMISSION = "pixel_emission";
    public static final String MATERIAL_PROP_RELIEF = "relief";

    /** Every animatable material property, longest first so suffix stripping never bites a shorter one. */
    public static final String[] MATERIAL_PROPS_ALL = {
        MATERIAL_PROP_PIXEL_EMISSION, MATERIAL_PROP_OVERLAY, MATERIAL_PROP_SMOOTHNESS,
        MATERIAL_PROP_LIGHTING, MATERIAL_PROP_METALLIC, MATERIAL_PROP_CULLING,
        MATERIAL_PROP_RELIEF, MATERIAL_PROP_COLOR, MATERIAL_PROP_SSS
    };

    public static record PoseBonePath(String formPath, String bone)
    {}

    public static record MaterialTexturePath(String formPath, String material)
    {}

    public static record MaterialPropPath(String formPath, String material, String property)
    {}

    public static record IKTargetPath(String formPath, String controller)
    {}

    public static record PoleTargetPath(String formPath, String controller)
    {}

    public static record PhysicsTargetPath(String formPath, String rootBone)
    {}

    public static boolean isPoseBoneChannel(String id)
    {
        return id != null && id.contains(POSE_BONES);
    }

    public static boolean isMaterialTextureChannel(String id)
    {
        return id != null && id.contains(MATERIAL_TEXTURES);
    }

    /**
     * A per-material appearance channel ({@code <formPath>/materials.<material>.<prop>}).
     * Guarded against the texture channels, whose {@code texture.materials.} prefix
     * contains this one's.
     */
    public static boolean isMaterialPropChannel(String id)
    {
        return parseMaterialPropPath(id) != null;
    }

    public static MaterialPropPath parseMaterialPropPath(String id)
    {
        if (id == null || id.contains(MATERIAL_TEXTURES))
        {
            return null;
        }

        int index = id.indexOf(MATERIAL_PROPS);

        if (index < 0)
        {
            return null;
        }

        String rest = id.substring(index + MATERIAL_PROPS.length());
        String property = null;

        for (String prop : MATERIAL_PROPS_ALL)
        {
            if (rest.endsWith("." + prop))
            {
                property = prop;
                rest = rest.substring(0, rest.length() - prop.length() - 1);

                break;
            }
        }

        if (property == null || rest.isEmpty())
        {
            return null;
        }

        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new MaterialPropPath(formPath, MATERIAL_DEFAULT.equals(rest) ? "" : rest, property);
    }

    public static String toMaterialPropKey(String formPath, String material, String property)
    {
        String key = MATERIAL_PROPS + (material == null || material.isEmpty() ? MATERIAL_DEFAULT : material) + "." + property;

        if (formPath == null || formPath.isEmpty())
        {
            return key;
        }

        return formPath + FormUtils.PATH_SEPARATOR + key;
    }

    public static MaterialTexturePath parseMaterialTexturePath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(MATERIAL_TEXTURES);

        if (index < 0)
        {
            return null;
        }

        String material = id.substring(index + MATERIAL_TEXTURES.length());
        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new MaterialTexturePath(formPath, material);
    }

    public static String toMaterialTextureKey(String formPath, String material)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return MATERIAL_TEXTURES + material;
        }

        return formPath + FormUtils.PATH_SEPARATOR + MATERIAL_TEXTURES + material;
    }

    public static boolean isIKTargetChannel(String id)
    {
        return id != null && id.contains(IK_TARGETS);
    }

    public static boolean isPoleTargetChannel(String id)
    {
        return id != null && id.contains(POLE_TARGETS);
    }

    public static boolean isPhysicsTargetChannel(String id)
    {
        return id != null && id.contains(PHYSICS_TARGETS);
    }

    public static PoseBonePath parsePoseBonePath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(POSE_BONES);

        if (index < 0)
        {
            return null;
        }

        String bone = id.substring(index + POSE_BONES.length());
        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new PoseBonePath(formPath, bone);
    }

    public static String toPoseBoneKey(String formPath, String bone)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return POSE_BONES + bone;
        }

        return formPath + FormUtils.PATH_SEPARATOR + POSE_BONES + bone;
    }

    public static IKTargetPath parseIKTargetPath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(IK_TARGETS);

        if (index < 0)
        {
            return null;
        }

        String controller = id.substring(index + IK_TARGETS.length());
        if (controller.startsWith(FormUtils.PATH_SEPARATOR))
        {
            controller = controller.substring(FormUtils.PATH_SEPARATOR.length());
        }

        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new IKTargetPath(formPath, controller);
    }

    public static String toIKTargetKey(String formPath, String controller)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return IK_TARGETS + FormUtils.PATH_SEPARATOR + controller;
        }

        return formPath + FormUtils.PATH_SEPARATOR + IK_TARGETS + FormUtils.PATH_SEPARATOR + controller;
    }

    public static PoleTargetPath parsePoleTargetPath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(POLE_TARGETS);

        if (index < 0)
        {
            return null;
        }

        String controller = id.substring(index + POLE_TARGETS.length());
        if (controller.startsWith(FormUtils.PATH_SEPARATOR))
        {
            controller = controller.substring(FormUtils.PATH_SEPARATOR.length());
        }

        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new PoleTargetPath(formPath, controller);
    }

    public static String toPoleTargetKey(String formPath, String controller)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return POLE_TARGETS + FormUtils.PATH_SEPARATOR + controller;
        }

        return formPath + FormUtils.PATH_SEPARATOR + POLE_TARGETS + FormUtils.PATH_SEPARATOR + controller;
    }

    public static PhysicsTargetPath parsePhysicsTargetPath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(PHYSICS_TARGETS);

        if (index < 0)
        {
            return null;
        }

        String rootBone = id.substring(index + PHYSICS_TARGETS.length());
        if (rootBone.startsWith(FormUtils.PATH_SEPARATOR))
        {
            rootBone = rootBone.substring(FormUtils.PATH_SEPARATOR.length());
        }

        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return new PhysicsTargetPath(formPath, rootBone);
    }

    public static String toPhysicsTargetKey(String formPath, String rootBone)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return PHYSICS_TARGETS + FormUtils.PATH_SEPARATOR + rootBone;
        }

        return formPath + FormUtils.PATH_SEPARATOR + PHYSICS_TARGETS + FormUtils.PATH_SEPARATOR + rootBone;
    }
}
