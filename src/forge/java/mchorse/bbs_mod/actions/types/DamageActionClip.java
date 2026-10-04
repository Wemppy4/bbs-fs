package mchorse.bbs_mod.actions.types;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;

public class DamageActionClip extends ActionClip
{
    public final ValueFloat damage = new ValueFloat("damage", 0F);

    public DamageActionClip()
    {
        super();

        this.add(this.damage);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        float damage = this.damage.get();

        if (damage <= 0F)
        {
            return;
        }

        this.applyPositionRotation(player, replay, tick);

        if (actor != null)
        {
            actor.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player), damage);
        }
    }

    @Override
    protected Clip create()
    {
        return new DamageActionClip();
    }
}