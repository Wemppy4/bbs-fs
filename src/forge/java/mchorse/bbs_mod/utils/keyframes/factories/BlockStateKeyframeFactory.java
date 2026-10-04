package mchorse.bbs_mod.utils.keyframes.factories;
import mchorse.bbs_mod.data.DataStorageUtils;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.interps.IInterp;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
public class BlockStateKeyframeFactory implements IKeyframeFactory<IBlockState>
{
    public IBlockState fromData(BaseType data){NBTBase tag=DataStorageUtils.toNbt(data);return tag instanceof NBTTagCompound?NBTUtil.readBlockState((NBTTagCompound)tag):createEmpty();}
    public BaseType toData(IBlockState value){return DataStorageUtils.fromNbt(NBTUtil.writeBlockState(new NBTTagCompound(),value));}
    public IBlockState createEmpty(){return Blocks.AIR.getDefaultState();}
    public boolean isStepped(){return true;}
    public IBlockState copy(IBlockState value){return value;}
    public IBlockState interpolate(IBlockState before,IBlockState a,IBlockState b,IBlockState after,IInterp interpolation,float x){return a;}
}
