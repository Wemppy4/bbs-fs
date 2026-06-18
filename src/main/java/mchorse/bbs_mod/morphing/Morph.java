package mchorse.bbs_mod.morphing;

import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.utils.RayTracing;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Arrays;
import java.util.Optional;

public class Morph
{
    private Form form;
    public final MCEntity entity;

    public static Form getMobForm(PlayerEntity player)
    {
        HitResult hitResult = RayTracing.rayTraceEntity(player, player.getEntityWorld(), player.getEyePos(), player.getRotationVector(), 64);

        if (hitResult.getType() == HitResult.Type.ENTITY)
        {
            Entity target = ((EntityHitResult) hitResult).getEntity();

            return createMobForm(target);
        }

        return null;
    }

    /**
     * Build a {@link MobForm} snapshotting the given entity's type and NBT, stripping
     * transient world-state keys (position, motion, UUID, etc.) so the form is reusable.
     *
     * @return the form, or {@code null} if the entity's type isn't registered.
     */
    public static MobForm createMobForm(Entity target)
    {
        Optional<RegistryKey<EntityType<?>>> key = Registries.ENTITY_TYPE.getKey(target.getType());

        if (key.isEmpty())
        {
            return null;
        }

        MobForm form = new MobForm();
        NbtWriteView view = NbtWriteView.create(ErrorReporter.EMPTY);

        target.saveData(view);

        NbtCompound compound = view.getNbt();

        for (String s : Arrays.asList("Pos", "Motion", "Rotation", "FallDistance", "Fire", "Air", "OnGround", "Invulnerable", "PortalCooldown", "UUID"))
        {
            compound.remove(s);
        }

        form.mobID.set(key.get().getValue().toString());
        form.mobNBT.set(compound.toString());

        return form;
    }

    public static Morph getMorph(Entity entity)
    {
        if (entity instanceof IMorphProvider provider)
        {
            return provider.getMorph();
        }

        return null;
    }

    public Morph(Entity entity)
    {
        this.entity = new MCEntity(entity);
    }

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        if (form == null && this.form != null && this.entity.getMcEntity() instanceof PlayerEntity player)
        {
            this.form.onDemorph(player);
        }

        this.form = form;

        if (this.form != null && this.entity.getMcEntity() instanceof PlayerEntity player)
        {
            this.form.onMorph(player);
            this.form.playMain();
        }

        this.entity.getMcEntity().calculateDimensions();
    }

    public void update()
    {
        this.entity.update();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    public NbtElement toNbt()
    {
        NbtCompound compound = new NbtCompound();

        if (this.form != null)
        {
            compound.put("Form", DataStorageUtils.toNbt(FormUtils.toData(this.form)));
        }

        return compound;
    }

    public void fromNbt(NbtCompound compound)
    {
        if (compound.contains("Form"))
        {
            MapType map = (MapType) DataStorageUtils.fromNbt(compound.getCompoundOrEmpty("Form"));

            this.form = FormUtils.fromData(map);
        }
    }
}