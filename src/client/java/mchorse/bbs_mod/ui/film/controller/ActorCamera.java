package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.joml.Matrices;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Riding the actor with the camera: over its eyes, or behind/in front of it at arm's length,
 * the way the game's own first and third person views work.
 *
 * <p>Everything is read at the render transition rather than at the tick, so the view is
 * smooth at any framerate — an actor that moved this tick is where it looks like it is, not
 * where the last tick left it.
 *
 * <p>Stateless: given an actor and a moment, it places the camera and nothing else.
 */
public class ActorCamera
{
    /** How far behind (or in front of) the actor the third-person camera sits, before collision. */
    private static final float DISTANCE = 5F;

    /** How much clearance is kept off a wall the camera would otherwise poke through. */
    private static final float WALL_MARGIN = 0.1F;

    /** Over the actor's eyes, looking where it looks. */
    public static void firstPerson(Camera camera, IEntity actor, float transition)
    {
        Vector3d position = eyePosition(actor, transition);
        Vector3f rotation = lookRotation(actor, transition);

        camera.position.set(position);
        camera.rotation.set(rotation.x, rotation.y + MathUtils.PI, 0F);
    }

    /**
     * Behind the actor ({@code back}) or in front of it, pulled in to the first wall in the way
     * so the camera never ends up inside geometry.
     */
    public static void thirdPerson(Camera camera, IEntity actor, float transition, boolean back)
    {
        Vector3d position = eyePosition(actor, transition);
        Vector3f rotation = lookRotation(actor, transition);

        Vector3f rotate = Matrices.rotation(rotation.x * (back ? 1 : -1), (back ? 0F : MathUtils.PI) - rotation.y);
        World world = MinecraftClient.getInstance().world;
        float distance = DISTANCE;

        HitResult result = RayTracing.rayTraceEntity(
            world,
            RayTracing.fromVector3d(position),
            RayTracing.fromVector3f(rotate),
            distance
        );

        if (result.getType() == HitResult.Type.BLOCK)
        {
            distance = (float) position.distance(result.getPos().x, result.getPos().y, result.getPos().z) - WALL_MARGIN;
        }

        rotate.mul(distance);
        position.add(rotate);

        camera.position.set(position);
        camera.rotation.set(rotation.x * (back ? -1 : 1), rotation.y + (back ? 0 : MathUtils.PI), 0);
    }

    /** The actor's eyes at this moment between ticks. */
    private static Vector3d eyePosition(IEntity actor, float transition)
    {
        Vector3d position = new Vector3d(actor.getPrevX(), actor.getPrevY(), actor.getPrevZ());

        position.lerp(new Vector3d(actor.getX(), actor.getY(), actor.getZ()), transition);
        position.y += actor.getEyeHeight();

        return position;
    }

    /** Where the actor is looking at this moment between ticks, in radians. */
    private static Vector3f lookRotation(IEntity actor, float transition)
    {
        Vector3f rotation = new Vector3f(actor.getPrevPitch(), actor.getPrevHeadYaw(), 0);

        rotation.lerp(new Vector3f(actor.getPitch(), actor.getHeadYaw(), 0), transition);

        rotation.x = MathUtils.toRad(rotation.x);
        rotation.y = MathUtils.toRad(rotation.y);

        return rotation;
    }
}
