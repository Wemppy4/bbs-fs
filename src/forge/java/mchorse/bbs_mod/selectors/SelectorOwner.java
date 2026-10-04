package mchorse.bbs_mod.selectors;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SelectorOwner
{
    public IEntity entity;

    private Form form;
    private long check = -1;
    private int nextNbtCheck = Integer.MIN_VALUE;
    private NBTTagCompound lastNbt;

    private EntityLivingBase mcEntity;

    public SelectorOwner(EntityLivingBase mcEntity)
    {
        this.mcEntity = mcEntity;
        this.entity = new MCEntity(mcEntity);
    }

    public Form getForm()
    {
        return form;
    }

    public void update()
    {
        World world = this.entity.getWorld();

        if (world == null || !world.isRemote)
        {
            return;
        }

        this.check();
        this.entity.update();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    public void check()
    {
        EntitySelectors selectors = BBSModClient.getSelectors();

        if (selectors.selectors.isEmpty())
        {
            this.form = null;
            this.check = selectors.getLastUpdate();
            return;
        }

        if (this.mcEntity.ticksExisted >= this.nextNbtCheck)
        {
            this.nextNbtCheck = this.mcEntity.ticksExisted + 10;

            Set<String> keys = createWhitelist();
            NBTTagCompound compound = this.mcEntity.writeToNBT(new NBTTagCompound());
            NBTTagCompound newCompound = new NBTTagCompound();

            for (String key : keys)
            {
                NBTBase element = compound.getTag(key);

                if (element != null)
                {
                    newCompound.setTag(key, element);
                }
            }

            if (!Objects.equals(newCompound, this.lastNbt))
            {
                this.check = -1;
            }

            this.lastNbt = newCompound;
        }

        if (this.check < selectors.getLastUpdate())
        {
            this.check = selectors.getLastUpdate();

            EntitySelector selectorFor = selectors.getSelectorFor(this.mcEntity);

            if (selectorFor != null)
            {
                this.form = FormUtils.copy(selectorFor.form);

                if (this.form != null)
                {
                    this.form.playMain();
                }
            }
            else
            {
                this.form = null;
            }
        }

    }

    private Set<String> createWhitelist()
    {
        HashSet<String> strings = new HashSet<>();
        String s = BBSSettings.entitySelectorsPropertyWhitelist.get();
        String[] split = s.split(",");

        for (String string : split)
        {
            strings.add(string.trim());
        }

        return strings;
    }
}
