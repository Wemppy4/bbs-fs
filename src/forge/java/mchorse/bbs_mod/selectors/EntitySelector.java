package mchorse.bbs_mod.selectors;



import mchorse.bbs_mod.data.IMapSerializable;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

public class EntitySelector implements IMapSerializable
{
    public boolean enabled = true;
    public Form form;
    public ResourceLocation entity;
    public String name = "";
    public NBTTagCompound nbt;

    public boolean matches(EntityLivingBase mcEntity)
    {
        if (!this.enabled)
        {
            return false;
        }

        ResourceLocation id = mcEntity instanceof EntityPlayer ? new ResourceLocation("minecraft:player") : EntityList.getKey(mcEntity);

        if (id == null || !id.equals(this.entity))
        {
            return false;
        }

        if (this.nbt != null)
        {
            NBTTagCompound entityCompound = mcEntity.writeToNBT(new NBTTagCompound());

            if (!this.compare(this.nbt, entityCompound))
            {
                return false;
            }
        }

        if (!this.name.isEmpty())
        {
            String a = TextFormatting.getTextWithoutFormattingCodes(mcEntity.getDisplayName().getUnformattedText());

            return Objects.equals(a, this.name);
        }

        return true;
    }

    private boolean compare(NBTTagCompound source, NBTTagCompound base)
    {
        for (String key : source.getKeySet())
        {
            NBTBase a = source.getTag(key);
            NBTBase b = base.getTag(key);

            if (a instanceof NBTTagCompound && b instanceof NBTTagCompound)
            {
                if (!this.compare((NBTTagCompound) a, (NBTTagCompound) b)) return false;
            }
            else if (!Objects.equals(a, b))
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public void fromData(MapType data)
    {
        this.nbt = null;

        if (data.has("enabled")) this.enabled = data.getBool("enabled");
        if (data.has("form")) this.form = FormUtils.fromData(data.getMap("form"));
        if (data.has("entity")) this.entity = new ResourceLocation(data.getString("entity"));
        if (data.has("name")) this.name = data.getString("name");
        if (data.has("nbt"))
        {
            try
            {
                this.nbt = JsonToNBT.getTagFromJson(data.getString("nbt"));
            }
            catch (NBTException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void toData(MapType data)
    {
        data.putBool("enabled", this.enabled);
        if (this.form != null) data.put("form", FormUtils.toData(this.form));
        if (this.entity != null) data.putString("entity", this.entity.toString());
        if (!this.name.isEmpty()) data.putString("name", this.name);
        if (this.nbt != null) data.putString("nbt", this.nbt.toString());
    }
}
