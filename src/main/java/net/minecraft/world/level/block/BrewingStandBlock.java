package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BrewingStandBlock extends BaseEntityBlock {
    public static final MapCodec<BrewingStandBlock> CODEC = simpleCodec(BrewingStandBlock::new);
    public static final BooleanProperty[] HAS_BOTTLE = new BooleanProperty[]{
        BlockStateProperties.HAS_BOTTLE_0, BlockStateProperties.HAS_BOTTLE_1, BlockStateProperties.HAS_BOTTLE_2
    };
    protected static final VoxelShape SHAPE = Shapes.or(Block.box(1.0, 0.0, 1.0, 15.0, 2.0, 15.0), Block.box(7.0, 0.0, 7.0, 9.0, 14.0, 9.0));

    @Override
    public MapCodec<BrewingStandBlock> codec() {
        return CODEC;
    }

    public BrewingStandBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(HAS_BOTTLE[0], Boolean.valueOf(false))
                .setValue(HAS_BOTTLE[1], Boolean.valueOf(false))
                .setValue(HAS_BOTTLE[2], Boolean.valueOf(false))
        );
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrewingStandBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return world.isClientSide ? null : createTickerHelper(type, BlockEntityType.BREWING_STAND, BrewingStandBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BrewingStandBlockEntity) {
                player.openMenu((BrewingStandBlockEntity)blockEntity);
                player.awardStat(Stats.INTERACT_WITH_BREWINGSTAND);
            }

            return InteractionResult.CONSUME;
        }
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        double d = (double)pos.getX() + 0.4 + (double)random.nextFloat() * 0.2;
        double e = (double)pos.getY() + 0.7 + (double)random.nextFloat() * 0.3;
        double f = (double)pos.getZ() + 0.4 + (double)random.nextFloat() * 0.2;
        world.addParticle(ParticleTypes.SMOKE, d, e, f, 0.0, 0.0, 0.0);
    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        Containers.dropContentsOnDestroy(state, newState, world, pos);
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(pos));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HAS_BOTTLE[0], HAS_BOTTLE[1], HAS_BOTTLE[2]);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}