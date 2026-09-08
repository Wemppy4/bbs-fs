package mchorse.bbs_mod.cubic.jem;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.renderers.mob.MobRig;
import mchorse.bbs_mod.forms.renderers.mob.MobRigs;
import mchorse.bbs_mod.forms.renderers.mob.MobStandIn;
import mchorse.bbs_mod.forms.renderers.mob.VanillaPose;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;

/**
 * The vanilla stage of a CEM model: the game's own model of the entity, posed for the frame by the
 * game's own code, read back as the program's starting values.
 *
 * <p>A pack keeps most vanilla parts empty and reads them — the fox's body is pitched flat by
 * {@code FoxEntityModel} and never by the pack, the hoglin measures its attack off a head vanilla holds
 * at fifty degrees, the blaze cancels the orbit vanilla gives its rods, the evoker tells a cast by the
 * flag vanilla sets on the separate arms. Nothing short of vanilla's {@code setAngles} answers those, so
 * this runs it: on a {@link MobStandIn} of the entity's kind, kept in step with the actor, through the
 * very model the world's entities of that kind draw with ({@link VanillaPose}). The parts are then read
 * under the names the pack uses ({@link CemPartNames}).</p>
 *
 * <p>There is no stage for a file that names no entity the game has — a skull, a pack's own invention —
 * or outside a world; the program then starts from the rest pose, as before.</p>
 */
public class CemVanillaStage implements ICemVanillaStage
{
    private static final String PLAYER = "player";
    private static final String PLAYER_SLIM = "player_slim";

    private final String entity;
    private final boolean baby;
    private final boolean player;
    private final boolean slim;
    private final CemPartNames names;

    private final MobStandIn standIn = new MobStandIn();
    private final CemVanillaSeed seed = new CemVanillaSeed();
    private Entity last;

    /** @param jem the model file's name, {@code cold_cow_baby}: the entity, and whether it is young or a slim player. */
    public CemVanillaStage(String jem)
    {
        this.entity = CemNames.entity(jem);
        this.baby = CemNames.baby(jem);
        this.player = this.entity.equals(PLAYER) || this.entity.equals(PLAYER_SLIM);
        this.slim = this.entity.equals(PLAYER_SLIM);
        this.names = CemPartNames.of(this.entity);
    }

    /** The stand-in, dressed as the file asks the first time it appears. */
    private Entity entity()
    {
        Entity entity = this.standIn.ensure(this.player ? PLAYER : this.entity, "", this.slim, this.player);

        if (entity != null && entity != this.last)
        {
            this.last = entity;

            if (this.baby && entity instanceof MobEntity mob)
            {
                mob.setBaby(true);
            }
        }

        return entity;
    }

    @Override
    public void tick(IEntity source)
    {
        if (this.entity() != null)
        {
            this.standIn.tick(source);
        }
    }

    @Override
    public CemVanillaSeed seed(IEntity source, float transition)
    {
        Entity entity = this.entity();
        LivingEntityRenderer renderer = entity == null ? null : VanillaPose.renderer(entity);

        if (renderer == null)
        {
            return null;
        }

        MobRig rig = MobRigs.of(renderer.getModel());

        VanillaPose.animate(renderer, (LivingEntity) entity, transition);

        for (String root : rig.getRootGroupKeys())
        {
            this.read(rig, root, 0F, 0F, 0F);
        }

        return this.seed;
    }

    /**
     * Note a part and everything under it, under the pack's name for it and — where that differs —
     * vanilla's, since a pack sometimes uses the vanilla one. {@code ox/oy/oz} is the parent's absolute
     * pivot, carried down the walk.
     */
    private void read(MobRig rig, String name, float ox, float oy, float oz)
    {
        ModelPart part = rig.part(name);

        if (part == null)
        {
            return;
        }

        float ax = ox + part.pivotX;
        float ay = oy + part.pivotY;
        float az = oz + part.pivotZ;
        String optifine = this.names.optifine(name);

        this.fill(this.seed.part(optifine), part, ax, ay, az);

        if (!optifine.equals(name))
        {
            this.fill(this.seed.part(name), part, ax, ay, az);
        }

        for (String child : rig.getDirectChildrenKeys(name))
        {
            this.read(rig, child, ax, ay, az);
        }
    }

    private void fill(CemVanillaSeed.Part slot, ModelPart part, float ax, float ay, float az)
    {
        slot.tx = part.pivotX;
        slot.ty = part.pivotY;
        slot.tz = part.pivotZ;
        slot.ax = ax;
        slot.ay = ay;
        slot.az = az;
        slot.rx = part.pitch;
        slot.ry = part.yaw;
        slot.rz = part.roll;
        slot.sx = part.xScale;
        slot.sy = part.yScale;
        slot.sz = part.zScale;
        slot.visible = part.visible;
    }
}
