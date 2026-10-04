package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.forms.entities.NativeLivingState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;

/** Borrow native render inputs without calling setActiveHand/resetActiveHand (which
 * fire gameplay hooks). Closing restores the exact previous stack instance and flags. */
public final class NativeItemUseScope implements AutoCloseable
{
    private static final Field STACK = ReflectionHelper.findField(EntityLivingBase.class, "activeItemStack", "field_184627_bm");
    private static final Field COUNT = ReflectionHelper.findField(EntityLivingBase.class, "activeItemStackUseCount", "field_184628_bn");
    private final EntityLivingBase entity;
    private final ItemStack stack;
    private final int count;
    private final byte flags;
    private boolean closed;
    private NativeItemUseScope(EntityLivingBase entity, EnumHand hand, ItemStack stack, int count)
    {
        this.entity = entity;
        this.stack = entity.getActiveItemStack();
        this.count = entity.getItemInUseCount();
        this.flags = entity.getDataManager().get(NativeLivingState.handStates());
        try
        {
            entity.getDataManager().set(NativeLivingState.handStates(), (byte) (1 | (hand == EnumHand.OFF_HAND ? 2 : 0)));
            STACK.set(entity, stack);
            COUNT.setInt(entity, count);
        }
        catch (IllegalAccessException error) { close(); throw new IllegalStateException("Cannot borrow native item-use state", error); }
    }
    public static NativeItemUseScope open(EntityLivingBase entity, EnumHand hand, ItemStack stack, int count)
    { return new NativeItemUseScope(entity, hand, stack, count); }
    @Override public void close()
    {
        if (closed) return;
        closed = true;
        try
        {
            entity.getDataManager().set(NativeLivingState.handStates(), flags);
            STACK.set(entity, stack);
            COUNT.setInt(entity, count);
        }
        catch (IllegalAccessException error) { throw new IllegalStateException("Cannot restore native item-use state", error); }
    }
}
