package mchorse.bbs_mod.actions.types.chat;

import mchorse.bbs_mod.actions.SuperFakePlayer;
import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;

public class ChatActionClip extends ActionClip
{
    public final ValueString message = new ValueString("message", "");

    public ChatActionClip()
    {
        this.add(this.message);
    }

    @Override
    public void applyAction(EntityLivingBase actor, SuperFakePlayer player, Film film, Replay replay, int tick)
    {
        for (EntityPlayer entity : player.world.playerEntities)
        {
            entity.sendMessage(new TextComponentString(StringUtils.processColoredText(this.message.get())));
        }
    }

    @Override
    protected Clip create()
    {
        return new ChatActionClip();
    }
}