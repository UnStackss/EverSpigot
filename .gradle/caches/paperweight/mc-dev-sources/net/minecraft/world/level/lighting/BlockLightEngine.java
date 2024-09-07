package net.minecraft.world.level.lighting;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;

public final class BlockLightEngine extends LightEngine<BlockLightSectionStorage.BlockDataLayerStorageMap, BlockLightSectionStorage> {
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public BlockLightEngine(LightChunkGetter chunkProvider) {
        this(chunkProvider, new BlockLightSectionStorage(chunkProvider));
    }

    @VisibleForTesting
    public BlockLightEngine(LightChunkGetter chunkProvider, BlockLightSectionStorage blockLightStorage) {
        super(chunkProvider, blockLightStorage);
    }

    @Override
    protected void checkNode(long blockPos) {
        long l = SectionPos.blockToSection(blockPos);
        if (this.storage.storingLightForSection(l)) {
            BlockState blockState = this.getState(this.mutablePos.set(blockPos));
            int i = this.getEmission(blockPos, blockState);
            int j = this.storage.getStoredLevel(blockPos);
            if (i < j) {
                this.storage.setStoredLevel(blockPos, 0);
                this.enqueueDecrease(blockPos, LightEngine.QueueEntry.decreaseAllDirections(j));
            } else {
                this.enqueueDecrease(blockPos, PULL_LIGHT_IN_ENTRY);
            }

            if (i > 0) {
                this.enqueueIncrease(blockPos, LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
            }
        }
    }

    @Override
    protected void propagateIncrease(long blockPos, long l, int lightLevel) {
        BlockState blockState = null;

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(l, direction)) {
                long m = BlockPos.offset(blockPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(m))) {
                    int i = this.storage.getStoredLevel(m);
                    int j = lightLevel - 1;
                    if (j > i) {
                        this.mutablePos.set(m);
                        BlockState blockState2 = this.getState(this.mutablePos);
                        int k = lightLevel - this.getOpacity(blockState2, this.mutablePos);
                        if (k > i) {
                            if (blockState == null) {
                                blockState = LightEngine.QueueEntry.isFromEmptyShape(l)
                                    ? Blocks.AIR.defaultBlockState()
                                    : this.getState(this.mutablePos.set(blockPos));
                            }

                            if (!this.shapeOccludes(blockPos, blockState, m, blockState2, direction)) {
                                this.storage.setStoredLevel(m, k);
                                if (k > 1) {
                                    this.enqueueIncrease(
                                        m, LightEngine.QueueEntry.increaseSkipOneDirection(k, isEmptyShape(blockState2), direction.getOpposite())
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void propagateDecrease(long blockPos, long l) {
        int i = LightEngine.QueueEntry.getFromLevel(l);

        for (Direction direction : PROPAGATION_DIRECTIONS) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(l, direction)) {
                long m = BlockPos.offset(blockPos, direction);
                if (this.storage.storingLightForSection(SectionPos.blockToSection(m))) {
                    int j = this.storage.getStoredLevel(m);
                    if (j != 0) {
                        if (j <= i - 1) {
                            BlockState blockState = this.getState(this.mutablePos.set(m));
                            int k = this.getEmission(m, blockState);
                            this.storage.setStoredLevel(m, 0);
                            if (k < j) {
                                this.enqueueDecrease(m, LightEngine.QueueEntry.decreaseSkipOneDirection(j, direction.getOpposite()));
                            }

                            if (k > 0) {
                                this.enqueueIncrease(m, LightEngine.QueueEntry.increaseLightFromEmission(k, isEmptyShape(blockState)));
                            }
                        } else {
                            this.enqueueIncrease(m, LightEngine.QueueEntry.increaseOnlyOneDirection(j, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    private int getEmission(long blockPos, BlockState blockState) {
        int i = blockState.getLightEmission();
        return i > 0 && this.storage.lightOnInSection(SectionPos.blockToSection(blockPos)) ? i : 0;
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        this.setLightEnabled(chunkPos, true);
        LightChunk lightChunk = this.chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);
        if (lightChunk != null) {
            lightChunk.findBlockLightSources((blockPos, blockState) -> {
                int i = blockState.getLightEmission();
                this.enqueueIncrease(blockPos.asLong(), LightEngine.QueueEntry.increaseLightFromEmission(i, isEmptyShape(blockState)));
            });
        }
    }
}
