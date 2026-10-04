package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.forge.ModelTileEntity;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Ways of building a new replay inside a film from something that already exists: a point in the
 * world, the camera's flight, a model block, a cut-out structure. Pure data assembly — the list
 * that offers these keeps its dialogs and refreshes itself, this only knows films and replays.
 */
public class ReplayFactory
{
    /** A replay standing at the given spot, optionally facing the given way. */
    public static Replay atPosition(Film film, Vector3d position, float pitch, float yaw, boolean includeRotation)
    {
        Replay replay = film.replays.addReplay();

        replay.category.set("");

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        if (includeRotation)
        {
            replay.keyframes.pitch.insert(0, (double) pitch);
            replay.keyframes.yaw.insert(0, (double) yaw);
            replay.keyframes.headYaw.insert(0, (double) yaw);
            replay.keyframes.bodyYaw.insert(0, (double) yaw);
        }

        return replay;
    }

    /** The camera's flight baked into position and rotation keyframes, tick by tick. */
    public static Replay fromCamera(Film film, int duration)
    {
        Position position = new Position();
        Clips camera = film.camera;
        CameraClipContext context = new CameraClipContext();

        Replay replay = film.replays.addReplay();

        replay.category.set("");

        context.clips = camera;

        for (int i = 0; i < duration; i++)
        {
            context.clipData.clear();
            context.setup(i, 0F);

            for (Clip clip : context.clips.getClips(i))
            {
                context.apply(clip, position);
            }

            context.currentLayer = 0;

            float yaw = position.angle.yaw - 180;

            replay.keyframes.x.insert(i, position.point.x);
            replay.keyframes.y.insert(i, position.point.y);
            replay.keyframes.z.insert(i, position.point.z);
            replay.keyframes.yaw.insert(i, (double) yaw);
            replay.keyframes.headYaw.insert(i, (double) yaw);
            replay.keyframes.bodyYaw.insert(i, (double) yaw);
            replay.keyframes.pitch.insert(i, (double) position.angle.pitch);
        }

        return replay;
    }

    /** The model block's form as a replay, standing exactly where the block shows it. */
    public static Replay fromModelBlock(Film film, ModelTileEntity modelBlock)
    {
        Replay replay = film.replays.addReplay();

        replay.category.set("");

        BlockPos blockPos = modelBlock.getPos();
        /* The native model block renders its complete form transform at this origin. */
        replay.form.set(FormUtils.copy(modelBlock.form));
        replay.keyframes.x.insert(0, blockPos.getX() + 0.5D);
        replay.keyframes.y.insert(0, (double) blockPos.getY());
        replay.keyframes.z.insert(0, blockPos.getZ() + 0.5D);

        return replay;
    }

    /** The cut region's form, dropped in at the very spot it was cut from. */
    public static Replay fromStructure(Film film, String id, BlockPos min, Vec3i size)
    {
        StructureForm form = new StructureForm();

        form.structure.set(id);
        form.name.set(id.substring(id.lastIndexOf('/') + 1));

        Replay replay = film.replays.addReplay();

        replay.category.set("");
        replay.form.set(form);

        /* The form centres its footprint and stands on its lowest layer, so this is the one
         * position at which the structure covers the blocks it was made from */
        replay.keyframes.x.insert(0, min.getX() + size.getX() / 2D);
        replay.keyframes.y.insert(0, (double) min.getY());
        replay.keyframes.z.insert(0, min.getZ() + size.getZ() / 2D);

        return replay;
    }
}
