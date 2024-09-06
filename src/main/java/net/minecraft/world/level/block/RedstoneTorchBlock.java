package net.minecraft.world.level.block;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.bukkit.event.block.BlockRedstoneEvent; // CraftBukkit

public class RedstoneTorchBlock extends BaseTorchBlock {

    public static final MapCodec<RedstoneTorchBlock> CODEC = simpleCodec(RedstoneTorchBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<BlockGetter, List<RedstoneTorchBlock.Toggle>> RECENT_TOGGLES = new WeakHashMap();
    public static final int RECENT_TOGGLE_TIMER = 60;
    public static final int MAX_RECENT_TOGGLES = 8;
    public static final int RESTART_DELAY = 160;
    private static final int TOGGLE_DELAY = 2;

    @Override
    public MapCodec<? extends RedstoneTorchBlock> codec() {
        return RedstoneTorchBlock.CODEC;
    }

    protected RedstoneTorchBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(RedstoneTorchBlock.LIT, true));
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        Direction[] aenumdirection = Direction.values();
        int i = aenumdirection.length;

        for (int j = 0; j < i; ++j) {
            Direction enumdirection = aenumdirection[j];

            world.updateNeighborsAt(pos.relative(enumdirection), this);
        }

    }

    @Override
    protected void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!moved) {
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];

                world.updateNeighborsAt(pos.relative(enumdirection), this);
            }

        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return (Boolean) state.getValue(RedstoneTorchBlock.LIT) && Direction.UP != direction ? 15 : 0;
    }

    protected boolean hasNeighborSignal(Level world, BlockPos pos, BlockState state) {
        return world.hasSignal(pos.below(), Direction.DOWN);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        boolean flag = this.hasNeighborSignal(world, pos, state);
        List<RedstoneTorchBlock.Toggle> list = (List) RedstoneTorchBlock.RECENT_TOGGLES.get(world);

        while (list != null && !list.isEmpty() && world.getGameTime() - ((RedstoneTorchBlock.Toggle) list.get(0)).when > 60L) {
            list.remove(0);
        }

        // CraftBukkit start
        org.bukkit.plugin.PluginManager manager = world.getCraftServer().getPluginManager();
        org.bukkit.block.Block block = world.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        int oldCurrent = ((Boolean) state.getValue(RedstoneTorchBlock.LIT)).booleanValue() ? 15 : 0;

        BlockRedstoneEvent event = new BlockRedstoneEvent(block, oldCurrent, oldCurrent);
        // CraftBukkit end
        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT)) {
            if (flag) {
                // CraftBukkit start
                if (oldCurrent != 0) {
                    event.setNewCurrent(0);
                    manager.callEvent(event);
                    if (event.getNewCurrent() != 0) {
                        return;
                    }
                }
                // CraftBukkit end
                world.setBlock(pos, (BlockState) state.setValue(RedstoneTorchBlock.LIT, false), 3);
                if (RedstoneTorchBlock.isToggledTooFrequently(world, pos, true)) {
                    world.levelEvent(1502, pos, 0);
                    world.scheduleTick(pos, world.getBlockState(pos).getBlock(), 160);
                }
            }
        } else if (!flag && !RedstoneTorchBlock.isToggledTooFrequently(world, pos, false)) {
            // CraftBukkit start
            if (oldCurrent != 15) {
                event.setNewCurrent(15);
                manager.callEvent(event);
                if (event.getNewCurrent() != 15) {
                    return;
                }
            }
            // CraftBukkit end
            world.setBlock(pos, (BlockState) state.setValue(RedstoneTorchBlock.LIT, true), 3);
        }

    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT) == this.hasNeighborSignal(world, pos, state) && !world.getBlockTicks().willTickThisTick(pos, this)) {
            world.scheduleTick(pos, (Block) this, 2);
        }

    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return direction == Direction.DOWN ? state.getSignal(world, pos, direction) : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if ((Boolean) state.getValue(RedstoneTorchBlock.LIT)) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;
            double d1 = (double) pos.getY() + 0.7D + (random.nextDouble() - 0.5D) * 0.2D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.2D;

            world.addParticle(DustParticleOptions.REDSTONE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RedstoneTorchBlock.LIT);
    }

    private static boolean isToggledTooFrequently(Level world, BlockPos pos, boolean addNew) {
        List<RedstoneTorchBlock.Toggle> list = (List) RedstoneTorchBlock.RECENT_TOGGLES.computeIfAbsent(world, (iblockaccess) -> {
            return Lists.newArrayList();
        });

        if (addNew) {
            list.add(new RedstoneTorchBlock.Toggle(pos.immutable(), world.getGameTime()));
        }

        int i = 0;
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            RedstoneTorchBlock.Toggle blockredstonetorch_redstoneupdateinfo = (RedstoneTorchBlock.Toggle) iterator.next();

            if (blockredstonetorch_redstoneupdateinfo.pos.equals(pos)) {
                ++i;
                if (i >= 8) {
                    return true;
                }
            }
        }

        return false;
    }

    public static class Toggle {

        final BlockPos pos;
        final long when;

        public Toggle(BlockPos pos, long time) {
            this.pos = pos;
            this.when = time;
        }
    }
}
