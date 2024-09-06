package net.minecraft.world.level.redstone;

import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class CollectingNeighborUpdater implements NeighborUpdater {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Level level;
    private final int maxChainedNeighborUpdates;
    private final ArrayDeque<CollectingNeighborUpdater.NeighborUpdates> stack = new ArrayDeque<>();
    private final List<CollectingNeighborUpdater.NeighborUpdates> addedThisLayer = new ArrayList<>();
    private int count = 0;

    public CollectingNeighborUpdater(Level world, int maxChainDepth) {
        this.level = world;
        this.maxChainedNeighborUpdates = maxChainDepth;
    }

    @Override
    public void shapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        this.addAndRun(
            pos, new CollectingNeighborUpdater.ShapeUpdate(direction, neighborState, pos.immutable(), neighborPos.immutable(), flags, maxUpdateDepth)
        );
    }

    @Override
    public void neighborChanged(BlockPos pos, Block sourceBlock, BlockPos sourcePos) {
        this.addAndRun(pos, new CollectingNeighborUpdater.SimpleNeighborUpdate(pos, sourceBlock, sourcePos.immutable()));
    }

    @Override
    public void neighborChanged(BlockState state, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        this.addAndRun(pos, new CollectingNeighborUpdater.FullNeighborUpdate(state, pos.immutable(), sourceBlock, sourcePos.immutable(), notify));
    }

    @Override
    public void updateNeighborsAtExceptFromFacing(BlockPos pos, Block sourceBlock, @Nullable Direction except) {
        this.addAndRun(pos, new CollectingNeighborUpdater.MultiNeighborUpdate(pos.immutable(), sourceBlock, except));
    }

    private void addAndRun(BlockPos pos, CollectingNeighborUpdater.NeighborUpdates entry) {
        boolean bl = this.count > 0;
        boolean bl2 = this.maxChainedNeighborUpdates >= 0 && this.count >= this.maxChainedNeighborUpdates;
        this.count++;
        if (!bl2) {
            if (bl) {
                this.addedThisLayer.add(entry);
            } else {
                this.stack.push(entry);
            }
        } else if (this.count - 1 == this.maxChainedNeighborUpdates) {
            LOGGER.error("Too many chained neighbor updates. Skipping the rest. First skipped position: " + pos.toShortString());
        }

        if (!bl) {
            this.runUpdates();
        }
    }

    private void runUpdates() {
        try {
            while (!this.stack.isEmpty() || !this.addedThisLayer.isEmpty()) {
                for (int i = this.addedThisLayer.size() - 1; i >= 0; i--) {
                    this.stack.push(this.addedThisLayer.get(i));
                }

                this.addedThisLayer.clear();
                CollectingNeighborUpdater.NeighborUpdates neighborUpdates = this.stack.peek();

                while (this.addedThisLayer.isEmpty()) {
                    if (!neighborUpdates.runNext(this.level)) {
                        this.stack.pop();
                        break;
                    }
                }
            }
        } finally {
            this.stack.clear();
            this.addedThisLayer.clear();
            this.count = 0;
        }
    }

    static record FullNeighborUpdate(BlockState state, BlockPos pos, Block block, BlockPos neighborPos, boolean movedByPiston)
        implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level world) {
            NeighborUpdater.executeUpdate(world, this.state, this.pos, this.block, this.neighborPos, this.movedByPiston);
            return false;
        }
    }

    static final class MultiNeighborUpdate implements CollectingNeighborUpdater.NeighborUpdates {
        private final BlockPos sourcePos;
        private final Block sourceBlock;
        @Nullable
        private final Direction skipDirection;
        private int idx = 0;

        MultiNeighborUpdate(BlockPos pos, Block sourceBlock, @Nullable Direction except) {
            this.sourcePos = pos;
            this.sourceBlock = sourceBlock;
            this.skipDirection = except;
            if (NeighborUpdater.UPDATE_ORDER[this.idx] == except) {
                this.idx++;
            }
        }

        @Override
        public boolean runNext(Level world) {
            BlockPos blockPos = this.sourcePos.relative(NeighborUpdater.UPDATE_ORDER[this.idx++]);
            BlockState blockState = world.getBlockState(blockPos);
            NeighborUpdater.executeUpdate(world, blockState, blockPos, this.sourceBlock, this.sourcePos, false);
            if (this.idx < NeighborUpdater.UPDATE_ORDER.length && NeighborUpdater.UPDATE_ORDER[this.idx] == this.skipDirection) {
                this.idx++;
            }

            return this.idx < NeighborUpdater.UPDATE_ORDER.length;
        }
    }

    interface NeighborUpdates {
        boolean runNext(Level world);
    }

    static record ShapeUpdate(Direction direction, BlockState state, BlockPos pos, BlockPos neighborPos, int updateFlags, int updateLimit)
        implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level world) {
            NeighborUpdater.executeShapeUpdate(world, this.direction, this.state, this.pos, this.neighborPos, this.updateFlags, this.updateLimit);
            return false;
        }
    }

    static record SimpleNeighborUpdate(BlockPos pos, Block block, BlockPos neighborPos) implements CollectingNeighborUpdater.NeighborUpdates {
        @Override
        public boolean runNext(Level world) {
            BlockState blockState = world.getBlockState(this.pos);
            NeighborUpdater.executeUpdate(world, blockState, this.pos, this.block, this.neighborPos, false);
            return false;
        }
    }
}
