package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CoralPlantBlock extends BaseCoralPlantTypeBlock {

    public static final MapCodec<CoralPlantBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(CoralBlock.DEAD_CORAL_FIELD.forGetter((blockcoralplant) -> {
            return blockcoralplant.deadBlock;
        }), propertiesCodec()).apply(instance, CoralPlantBlock::new);
    });
    private final Block deadBlock;
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 15.0D, 14.0D);

    @Override
    public MapCodec<CoralPlantBlock> codec() {
        return CoralPlantBlock.CODEC;
    }

    protected CoralPlantBlock(Block deadCoralBlock, BlockBehaviour.Properties settings) {
        super(settings);
        this.deadBlock = deadCoralBlock;
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        this.tryScheduleDieTick(state, world, pos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!scanForWater(state, world, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, this.deadBlock.defaultBlockState().setValue(CoralPlantBlock.WATERLOGGED, false)).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) this.deadBlock.defaultBlockState().setValue(CoralPlantBlock.WATERLOGGED, false), 2);
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !state.canSurvive(world, pos)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            this.tryScheduleDieTick(state, world, pos);
            if ((Boolean) state.getValue(CoralPlantBlock.WATERLOGGED)) {
                world.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(world));
            }

            return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return CoralPlantBlock.SHAPE;
    }
}