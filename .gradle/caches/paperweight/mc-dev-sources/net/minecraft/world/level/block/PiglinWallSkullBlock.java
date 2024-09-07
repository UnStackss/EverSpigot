package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PiglinWallSkullBlock extends WallSkullBlock {
    public static final MapCodec<PiglinWallSkullBlock> CODEC = simpleCodec(PiglinWallSkullBlock::new);
    private static final Map<Direction, VoxelShape> AABBS = Maps.immutableEnumMap(
        Map.of(
            Direction.NORTH,
            Block.box(3.0, 4.0, 8.0, 13.0, 12.0, 16.0),
            Direction.SOUTH,
            Block.box(3.0, 4.0, 0.0, 13.0, 12.0, 8.0),
            Direction.EAST,
            Block.box(0.0, 4.0, 3.0, 8.0, 12.0, 13.0),
            Direction.WEST,
            Block.box(8.0, 4.0, 3.0, 16.0, 12.0, 13.0)
        )
    );

    @Override
    public MapCodec<PiglinWallSkullBlock> codec() {
        return CODEC;
    }

    public PiglinWallSkullBlock(BlockBehaviour.Properties settings) {
        super(SkullBlock.Types.PIGLIN, settings);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return AABBS.get(state.getValue(FACING));
    }
}
