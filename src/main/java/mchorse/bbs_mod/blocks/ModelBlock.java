package mchorse.bbs_mod.blocks;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelBody;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class ModelBlock extends Block implements BlockEntityProvider, Waterloggable
{
    public static final IntProperty LIGHT_LEVEL = IntProperty.of("light_level", 0, 15);
    public static final EnumProperty<ModelBlockSound> SOUND = EnumProperty.of("sound", ModelBlockSound.class);

    /**
     * Client-side hook: whether the player is currently editing (dashboard
     * open, or a model block item in hand). While editing, the outline grows
     * to at least the full cube so a block with a tiny or offset hitbox stays
     * easy to target. On the dedicated server it stays false — the server
     * never targets blocks visually.
     */
    public static BooleanSupplier editingCheck = () -> false;

    public static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(BlockEntityType<A> givenType, BlockEntityType<E> expectedType, BlockEntityTicker<? super E> ticker)
    {
        return expectedType == givenType ? (BlockEntityTicker<A>) ticker : null;
    }

    public ModelBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getDefaultState()
            .with(Properties.WATERLOGGED, false)
            .with(LIGHT_LEVEL, 0)
            .with(SOUND, ModelBlockSound.STONE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.WATERLOGGED, LIGHT_LEVEL, SOUND);
    }

    /**
     * Pushes the body's light level and sound material from the block entity
     * into the block state, where the engine actually reads them. Server side;
     * the state change then syncs to clients on its own.
     */
    public static void mirrorBlockState(World world, BlockPos pos)
    {
        BlockEntity be = world.getBlockEntity(pos);
        BlockState state = world.getBlockState(pos);

        if (!(be instanceof ModelBlockEntity model) || !(state.getBlock() instanceof ModelBlock))
        {
            return;
        }

        ModelBody body = model.getProperties().getBody();
        BlockState updated = state
            .with(LIGHT_LEVEL, body.getLightLevel())
            .with(SOUND, body.getSound());

        if (updated != state)
        {
            world.setBlockState(pos, updated, Block.NOTIFY_ALL);
        }
    }

    @Nullable
    private static ModelBlockEntity getModelBlockEntity(BlockView world, BlockPos pos)
    {
        return world.getBlockEntity(pos) instanceof ModelBlockEntity model ? model : null;
    }

    /* Shape (the block's hitbox is authored per block in its body settings) */

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        ModelBlockEntity model = getModelBlockEntity(world, pos);
        VoxelShape shape = model == null ? VoxelShapes.fullCube() : model.getShape();

        /* While editing, the block stays targetable as at least a full cube
         * even when its actual hitbox is tiny or offset. CUBE mode returns the
         * fullCube() singleton, so the reference check skips a wasteful union. */
        if (shape != VoxelShapes.fullCube() && editingCheck.getAsBoolean())
        {
            return VoxelShapes.union(shape, VoxelShapes.fullCube());
        }

        return shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        ModelBlockEntity model = getModelBlockEntity(world, pos);

        if (model != null && model.getProperties().getBody().isSolid())
        {
            return model.getShape();
        }

        return VoxelShapes.empty();
    }

    @Override
    public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        ModelBlockEntity model = getModelBlockEntity(world, pos);

        if (model != null && model.getProperties().getBody().isCameraCollision())
        {
            return model.getShape();
        }

        return VoxelShapes.empty();
    }

    @Override
    public BlockSoundGroup getSoundGroup(BlockState state)
    {
        return state.get(SOUND).group;
    }

    /**
     * Vanilla's break-speed formula, but with the hardness taken from the
     * block's body instead of the registration-time settings — so every model
     * block can take its own time to mine. Runs identically on both sides
     * (the server steps its own break progress), fed by the synced entity.
     */
    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos)
    {
        ModelBlockEntity model = getModelBlockEntity(world, pos);
        float hardness = model == null ? 0F : model.getProperties().getBody().getHardness();

        if (hardness <= 0F)
        {
            /* A whole break per tick: the instant break this block always had. */
            return 1F;
        }

        int divisor = player.canHarvest(state) ? 30 : 100;

        return player.getBlockBreakingSpeed(state) / hardness / divisor;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack)
    {
        super.onPlaced(world, pos, state, placer, itemStack);

        /* A placed item may carry body data in its BlockEntityTag (already
         * poured into the block entity by now) — mirror it into the state. */
        if (!world.isClient())
        {
            mirrorBlockState(world, pos);
        }
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx)
    {
        return this.getDefaultState()
            .with(Properties.WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).isOf(Fluids.WATER));
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state)
    {
        BlockEntity entity = world.getBlockEntity(pos);

        if (entity instanceof ModelBlockEntity modelBlock)
        {
            ItemStack stack = new ItemStack(this);

            stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(modelBlock.createNbtWithId(world.getRegistryManager())));

            return stack;
        }

        return super.getPickStack(world, pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state)
    {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public boolean isTransparent(BlockState state, BlockView world, BlockPos pos)
    {
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        if (world.isClient())
        {
            return validateTicker(type, BBSMod.MODEL_BLOCK_ENTITY, (theWorld, blockPos, blockState, blockEntity) -> blockEntity.tick(theWorld, blockPos, blockState));
        }

        return null;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new ModelBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayerEntity serverPlayer)
        {
            ServerNetwork.sendClickedModelBlock(serverPlayer, pos);
        }

        return ActionResult.SUCCESS;
    }

    /* Waterloggable implementation */

    @Override
    public FluidState getFluidState(BlockState state)
    {
        return state.get(Properties.WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be, ItemStack tool)
    {
        if (!world.isClient && !player.getAbilities().creativeMode)
        {
            if (be instanceof ModelBlockEntity model)
            {
                ItemStack stack = new ItemStack(this);

                stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(model.createNbtWithId(world.getRegistryManager())));

                ItemScatterer.spawn(world, pos, DefaultedList.ofSize(1, stack));
            }
        }

        super.afterBreak(world, player, pos, state, be, tool);
    }
}