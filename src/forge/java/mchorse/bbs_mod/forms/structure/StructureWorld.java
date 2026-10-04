package mchorse.bbs_mod.forms.structure;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.init.Biomes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import java.util.*;

/** Native world facade for TESRs, based on Blockbuster's ClientHandlerStructure.FakeWorld. */
public final class StructureWorld extends World
{
    private final StructureRenderWorld view;
    private final Map<BlockPos, TileEntity> tiles = new LinkedHashMap<>();

    public StructureWorld(StructureRenderWorld view)
    {
        super(null, new WorldInfo(new WorldSettings(0, GameType.CREATIVE, false, false, WorldType.DEFAULT), "BBS structure"), new WorldProviderSurface(), new Profiler(), true);
        this.view = view;
        this.provider.setWorld(this);
        this.chunkProvider = this.createChunkProvider();
        view.getData().getBlockEntities().forEach((pos, source) ->
        {
            NBTTagCompound nbt = source.copy();
            nbt.setInteger("x",pos.getX());nbt.setInteger("y",pos.getY());nbt.setInteger("z",pos.getZ());
            TileEntity tile = TileEntity.create(this, nbt);
            if(tile != null){tile.setWorld(this);tile.setPos(pos);this.tiles.put(pos,tile);}
        });
        /* A captured native state can have a TESR without explicit NBT (e.g. a single block form). */
        view.getData().getBlocks().forEach((pos,state) ->
        {
            if(!tiles.containsKey(pos) && state.getBlock().hasTileEntity(state))
            {
                TileEntity tile=state.getBlock().createTileEntity(this,state);
                if(tile!=null){tile.setWorld(this);tile.setPos(pos);tiles.put(pos,tile);}
            }
        });
        this.loadedTileEntityList.addAll(tiles.values());
        view.setTiles(tiles);
    }
    public Collection<TileEntity> getTiles(){return Collections.unmodifiableCollection(tiles.values());}
    @Override protected IChunkProvider createChunkProvider(){return new ChunkProviderClient(this);}
    @Override protected boolean isChunkLoaded(int x,int z,boolean allowEmpty){return true;}
    @Override public boolean isAreaLoaded(BlockPos center,int radius,boolean allowEmpty){return true;}
    @Override public net.minecraft.block.state.IBlockState getBlockState(BlockPos pos){return view==null?net.minecraft.init.Blocks.AIR.getDefaultState():view.getBlockState(pos);}
    @Override public TileEntity getTileEntity(BlockPos pos){return tiles==null?null:tiles.get(pos);}
    @Override public Biome getBiome(BlockPos pos){return view==null?Biomes.PLAINS:view.getBiome(pos);}
    @Override public int getCombinedLight(BlockPos pos,int light){return view.getCombinedLight(pos,light);}
    @Override public int getLightFor(EnumSkyBlock type,BlockPos pos){return view.getLightLevel(type,pos);}
    @Override public float getLightBrightness(BlockPos pos){return provider.getLightBrightnessTable()[Math.max(getLightFor(EnumSkyBlock.SKY,pos),getLightFor(EnumSkyBlock.BLOCK,pos))];}
    @Override public boolean setBlockState(BlockPos pos,net.minecraft.block.state.IBlockState state,int flags){return false;}
}
