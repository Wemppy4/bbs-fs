package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.FormUtils;

public class PerLimbService
{
    public static final String POSE_BONES = "pose.bones.";
    public static final String IK_TARGETS = "ik_targets";
    public static final String IK_CONTROLS = "ik_controls";
    public static final String POLE_TARGETS = "pole_targets";
    public static final String PHYSICS_TARGETS = "physics_targets";

    public static record PoseBonePath(String formPath, String bone)
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

    public static boolean isIKControlChannel(String id)
    {
        return id != null && id.contains(IK_CONTROLS);
    }

    /** The IK-controls channel is one per form (not per controller); this returns its owning form path. */
    public static String parseIKControlFormPath(String id)
    {
        if (id == null)
        {
            return null;
        }

        int index = id.indexOf(IK_CONTROLS);

        if (index < 0)
        {
            return null;
        }

        String formPath = id.substring(0, index);

        if (formPath.endsWith(FormUtils.PATH_SEPARATOR))
        {
            formPath = formPath.substring(0, formPath.length() - 1);
        }

        return formPath;
    }

    public static String toIKControlKey(String formPath)
    {
        if (formPath == null || formPath.isEmpty())
        {
            return IK_CONTROLS;
        }

        return formPath + FormUtils.PATH_SEPARATOR + IK_CONTROLS;
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
