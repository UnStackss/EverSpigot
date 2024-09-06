package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.craftbukkit.event.CraftEventFactory; // CraftBukkit

public class RedstoneLampBlock extends Block {

    public static final MapCodec<RedstoneLampBlock> CODEC = simpleCodec(RedstoneLampBlock::new);
    public static final BooleanProperty LIT = RedstoneTorchBlock.LIT;

    @Override
    public MapCodec<RedstoneLampBlock> codec() {
        return RedstoneLampBlock.CODEC;
    }

    public RedstoneLampBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) this.defaultBlockState().setValue(RedstoneLampBlock.LIT, false));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return (BlockState) this.defaultBlockState().setValue(RedstoneLampBlock.LIT, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (!world.isClientSide) {
            boolean flag1 = (Boolean) state.getValue(RedstoneLampBlock.LIT);

            if (flag1 != world.hasNeighborSignal(pos)) {
                if (flag1) {
                    world.scheduleTick(pos, (Block) this, 4);
                } else {
                    // CraftBukkit start
                    if (CraftEventFactory.callRedstoneChange(world, pos, 0, 15).getNewCurrent() != 15) {
                        return;
                    }
                    // CraftBukkit end
                    world.setBlock(pos, (BlockState) state.cycle(RedstoneLampBlock.LIT), 2);
                }
            }

        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedstoneLampBlock.LIT) && !world.hasNeighborSignal(pos)) {
            // CraftBukkit start
            if (CraftEventFactory.callRedstoneChange(world, pos, 15, 0).getNewCurrent() != 0) {
                return;
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.cycle(RedstoneLampBlock.LIT), 2);
        }

    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedstoneLampBlock.LIT);
    }
}
