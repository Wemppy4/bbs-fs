package mchorse.bbs_mod.forms.structure;

import com.mojang.logging.LogUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.map.MapState;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkManager;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.tick.QueryableTickScheduler;
import net.minecraft.world.tick.TickManager;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Fake {@link World} backing the block entity renderers of a structure form.
 *
 * <p>Block entity renderers (chests, signs, beds, BBS model blocks) call {@link #setWorld} and then
 * query the world for neighbors and light. Feeding them the real {@code mc.world} answers those
 * queries at unrelated real-world coordinates (double chests fail to pair, etc.). This world instead
 * redirects {@link #getBlockState}/{@link #getFluidState}/{@link #getBlockEntity} to the structure
 * data, so neighbor lookups resolve within the structure.</p>
 *
 * <p>Everything else is borrowed from the real client world: the constructor copies its properties,
 * dimension, registries and profiler, and the remaining abstract methods delegate to it (or no-op
 * for mutators that must never touch the real world). Construction needs a live client world for
 * those registries — {@link #create} returns {@code null} otherwise and the caller falls back to
 * {@code mc.world}.</p>
 */
public class StructureWorld extends World
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The fallback below is a silent downgrade in quality, so it is worth saying once — but only once. */
    private static boolean reportedFailure;

    private final ClientWorld delegate;
    private final StructureRenderData data;
    private final Map<BlockPos, BlockEntity> blockEntities;

    private StructureWorld(ClientWorld delegate, StructureRenderData data, Map<BlockPos, BlockEntity> blockEntities)
    {
        super(
            (MutableWorldProperties) delegate.getLevelProperties(),
            delegate.getRegistryKey(),
            delegate.getRegistryManager(),
            delegate.getDimensionEntry(),
            delegate.getProfilerSupplier(),
            true,  /* client side */
            false, /* not a debug world */
            0L,    /* biome-zoomer seed; unused, biome comes from the structure view */
            0      /* no chained neighbor updates */
        );

        this.delegate = delegate;
        this.data = data;
        this.blockEntities = blockEntities;
    }

    /** Build a structure-backed world, or {@code null} if there is no client world to borrow from. */
    @Nullable
    public static World create(StructureRenderData data, Map<BlockPos, BlockEntity> blockEntities)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return null;
        }

        try
        {
            return new StructureWorld(world, data, blockEntities);
        }
        catch (Exception e)
        {
            /* Properties not castable / accessor missing on this build. The caller falls back to
             * mc.world, where block entities resolve their neighbours at unrelated coordinates —
             * double chests stop pairing and the like. Worth knowing about when that shows up. */
            if (!reportedFailure)
            {
                reportedFailure = true;

                LOGGER.warn("Couldn't build a structure-backed world, block entities will see the real world instead", e);
            }

            return null;
        }
    }

    /* --- Structure-backed reads --------------------------------------------------------------- */

    @Override
    public BlockState getBlockState(BlockPos pos)
    {
        return this.data.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos)
    {
        return this.data.getBlockState(pos).getFluidState();
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(BlockPos pos)
    {
        return this.blockEntities.get(pos);
    }

    /* --- Borrowed from the real client world -------------------------------------------------- */

    @Override
    public ChunkManager getChunkManager()
    {
        return this.delegate.getChunkManager();
    }

    @Override
    public QueryableTickScheduler<net.minecraft.block.Block> getBlockTickScheduler()
    {
        return this.delegate.getBlockTickScheduler();
    }

    @Override
    public QueryableTickScheduler<net.minecraft.fluid.Fluid> getFluidTickScheduler()
    {
        return this.delegate.getFluidTickScheduler();
    }

    @Override
    public TickManager getTickManager()
    {
        return this.delegate.getTickManager();
    }

    @Override
    public RecipeManager getRecipeManager()
    {
        return this.delegate.getRecipeManager();
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return this.delegate.getScoreboard();
    }

    @Override
    public FeatureSet getEnabledFeatures()
    {
        return this.delegate.getEnabledFeatures();
    }

    @Override
    public float getBrightness(Direction direction, boolean shaded)
    {
        return StructureLighting.getBrightness(direction, shaded);
    }

    @Override
    public int getLightLevel(LightType type, BlockPos pos)
    {
        return StructureLighting.getLightLevel(type);
    }

    @Override
    public RegistryEntry<Biome> getGeneratorStoredBiome(int biomeX, int biomeY, int biomeZ)
    {
        return this.delegate.getGeneratorStoredBiome(biomeX, biomeY, biomeZ);
    }

    @Override
    public List<? extends PlayerEntity> getPlayers()
    {
        return List.of();
    }

    /* --- Inert: a render-only world never mutates state or resolves entities/maps -------------- */

    @Override
    protected EntityLookup<Entity> getEntityLookup()
    {
        return null;
    }

    @Nullable
    @Override
    public Entity getEntityById(int id)
    {
        return null;
    }

    @Nullable
    @Override
    public MapState getMapState(String id)
    {
        return null;
    }

    @Override
    public void putMapState(String id, MapState state)
    {
    }

    @Override
    public int getNextMapId()
    {
        return 0;
    }

    @Override
    public void updateListeners(BlockPos pos, BlockState oldState, BlockState newState, int flags)
    {
    }

    @Override
    public void setBlockBreakingInfo(int entityId, BlockPos pos, int progress)
    {
    }

    @Override
    public void playSound(@Nullable PlayerEntity except, double x, double y, double z, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed)
    {
    }

    @Override
    public void playSoundFromEntity(@Nullable PlayerEntity except, Entity entity, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch, long seed)
    {
    }

    @Override
    public void syncWorldEvent(@Nullable PlayerEntity player, int eventId, BlockPos pos, int data)
    {
    }

    @Override
    public void emitGameEvent(GameEvent event, Vec3d emitterPos, GameEvent.Emitter emitter)
    {
    }

    @Override
    public String asString()
    {
        return "StructureWorld";
    }
}
