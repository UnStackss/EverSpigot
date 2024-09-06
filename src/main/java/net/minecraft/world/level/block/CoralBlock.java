package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class CoralBlock extends Block {

    public static final MapCodec<Block> DEAD_CORAL_FIELD = BuiltInRegistries.BLOCK.byNameCodec().fieldOf("dead");
    public static final MapCodec<CoralBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(CoralBlock.DEAD_CORAL_FIELD.forGetter((blockcoral) -> {
            return blockcoral.deadBlock;
        }), propertiesCodec()).apply(instance, CoralBlock::new);
    });
    private final Block deadBlock;

    public CoralBlock(Block deadCoralBlock, BlockBehaviour.Properties settings) {
        super(settings);
        this.deadBlock = deadCoralBlock;
    }

    @Override
    public MapCodec<CoralBlock> codec() {
        return CoralBlock.CODEC;
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (!this.scanForWater(world, pos)) {
            // CraftBukkit start
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callBlockFadeEvent(world, pos, this.deadBlock.defaultBlockState()).isCancelled()) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, this.deadBlock.defaultBlockState(), 2);
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        if (!this.scanForWater(world, pos)) {
            world.scheduleTick(pos, (Block) this, 60 + world.getRandom().nextInt(40));
        }

        return super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    protected boolean scanForWater(BlockGetter world, BlockPos pos) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];
            FluidState fluid = world.getFluidState(pos.relative(enumdirection));

            if (fluid.is(FluidTags.WATER)) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (!this.scanForWater(ctx.getLevel(), ctx.getClickedPos())) {
            ctx.getLevel().scheduleTick(ctx.getClickedPos(), (Block) this, 60 + ctx.getLevel().getRandom().nextInt(40));
        }

        return this.defaultBlockState();
    }
}
