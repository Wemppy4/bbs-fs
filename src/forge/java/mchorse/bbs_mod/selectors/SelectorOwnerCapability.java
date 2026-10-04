package mchorse.bbs_mod.selectors;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/** Client-side form state attached to the entity, following Metamorph's native capability path. */
public final class SelectorOwnerCapability implements ICapabilityProvider, ISelectorOwnerProvider
{
    @CapabilityInject(ISelectorOwnerProvider.class)
    public static final Capability<ISelectorOwnerProvider> OWNER = null;

    private final SelectorOwner owner;

    public static void register()
    {
        CapabilityManager.INSTANCE.register(ISelectorOwnerProvider.class, new Capability.IStorage<ISelectorOwnerProvider>()
        {
            @Override public NBTBase writeNBT(Capability<ISelectorOwnerProvider> capability, ISelectorOwnerProvider instance, EnumFacing side) { return null; }
            @Override public void readNBT(Capability<ISelectorOwnerProvider> capability, ISelectorOwnerProvider instance, EnumFacing side, NBTBase tag) {}
        }, () -> () -> null);
    }

    public static SelectorOwner get(EntityLivingBase entity)
    {
        ISelectorOwnerProvider provider = OWNER == null ? null : entity.getCapability(OWNER, null);
        return provider == null ? null : provider.getOwner();
    }

    public SelectorOwnerCapability(EntityLivingBase entity) { this.owner = new SelectorOwner(entity); }
    @Override public SelectorOwner getOwner() { return this.owner; }
    @Override public boolean hasCapability(Capability<?> capability, EnumFacing facing) { return capability == OWNER; }
    @Override public <T> T getCapability(Capability<T> capability, EnumFacing facing)
    {
        return capability == OWNER ? OWNER.cast(this) : null;
    }
}
