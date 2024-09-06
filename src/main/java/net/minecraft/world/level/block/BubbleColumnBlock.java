package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BubbleColumnBlock extends Block implements BucketPickup {
    public static final MapCodec<BubbleColumnBlock> CODEC = simpleCodec(BubbleColumnBlock::new);
    public static final BooleanProperty DRAG_DOWN = BlockStateProperties.DRAG;
    private static final int CHECK_PERIOD = 5;

    @Override
    public MapCodec<BubbleColumnBlock> codec() {
        return CODEC;
    }

    public BubbleColumnBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRAG_DOWN, Boolean.valueOf(true)));
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        BlockState blockState = world.getBlockState(pos.above());
        if (blockState.isAir()) {
            entity.onAboveBubbleCol(state.getValue(DRAG_DOWN));
            if (!world.isClientSide) {
                ServerLevel serverLevel = (ServerLevel)world;

                for (int i = 0; i < 2; i++) {
                    serverLevel.sendParticles(
                        ParticleTypes.SPLASH,
                        (double)pos.getX() + world.random.nextDouble(),
                        (double)(pos.getY() + 1),
                        (double)pos.getZ() + world.random.nextDouble(),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        1.0
                    );
                    serverLevel.sendParticles(
                        ParticleTypes.BUBBLE,
                        (double)pos.getX() + world.random.nextDouble(),
                        (double)(pos.getY() + 1),
                        (double)pos.getZ() + world.random.nextDouble(),
                        1,
                        0.0,
                        0.01,
                        0.0,
                        0.2
                    );
                }
            }
        } else {
            entity.onInsideBubbleColumn(state.getValue(DRAG_DOWN));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        updateColumn(world, pos, state, world.getBlockState(pos.below()));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    public static void updateColumn(LevelAccessor world, BlockPos pos, BlockState state) {
        updateColumn(world, pos, world.getBlockState(pos), state);
    }

    public static void updateColumn(LevelAccessor world, BlockPos pos, BlockState water, BlockState bubbleSource) {
        if (canExistIn(water)) {
            BlockState blockState = getColumnState(bubbleSource);
            world.setBlock(pos, blockState, 2);
            BlockPos.MutableBlockPos mutableBlockPos = pos.mutable().move(Direction.UP);

            while (canExistIn(world.getBlockState(mutableBlockPos))) {
                if (!world.setBlock(mutableBlockPos, blockState, 2)) {
                    return;
                }

                mutableBlockPos.move(Direction.UP);
            }
        }
    }

    private static boolean canExistIn(BlockState state) {
        return state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.WATER) && state.getFluidState().getAmount() >= 8 && state.getFluidState().isSource();
    }

    private static BlockState getColumnState(BlockState state) {
        if (state.is(Blocks.BUBBLE_COLUMN)) {
            return state;
        } else if (state.is(Blocks.SOUL_SAND)) {
            return Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, Boolean.valueOf(false));
        } else {
            return state.is(Blocks.MAGMA_BLOCK)
                ? Blocks.BUBBLE_COLUMN.defaultBlockState().setValue(DRAG_DOWN, Boolean.valueOf(true))
                : Blocks.WATER.defaultBlockState();
        }
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        double d = (double)pos.getX();
        double e = (double)pos.getY();
        double f = (double)pos.getZ();
        if (state.getValue(DRAG_DOWN)) {
            world.addAlwaysVisibleParticle(ParticleTypes.CURRENT_DOWN, d + 0.5, e + 0.8, f, 0.0, 0.0, 0.0);
            if (random.nextInt(200) == 0) {
                world.playLocalSound(
                    d,
                    e,
                    f,
                    SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        } else {
            world.addAlwaysVisibleParticle(ParticleTypes.BUBBLE_COLUMN_UP, d + 0.5, e, f + 0.5, 0.0, 0.04, 0.0);
            world.addAlwaysVisibleParticle(
                ParticleTypes.BUBBLE_COLUMN_UP, d + (double)random.nextFloat(), e + (double)random.nextFloat(), f + (double)random.nextFloat(), 0.0, 0.04, 0.0
            );
            if (random.nextInt(200) == 0) {
                world.playLocalSound(
                    d,
                    e,
                    f,
                    SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS,
                    0.2F + random.nextFloat() * 0.2F,
                    0.9F + random.nextFloat() * 0.15F,
                    false
                );
            }
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        if (!state.canSurvive(world, pos)
            || direction == Direction.DOWN
            || direction == Direction.UP && !neighborState.is(Blocks.BUBBLE_COLUMN) && canExistIn(neighborState)) {
            world.scheduleTick(pos, this, 5);
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos.below());
        return blockState.is(Blocks.BUBBLE_COLUMN) || blockState.is(Blocks.MAGMA_BLOCK) || blockState.is(Blocks.SOUL_SAND);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRAG_DOWN);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor world, BlockPos pos, BlockState state) {
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.WATER.getPickupSound();
    }
}
