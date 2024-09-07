package net.minecraft.world.level.levelgen;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

public class Heightmap {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Predicate<BlockState> NOT_AIR = state -> !state.isAir();
    static final Predicate<BlockState> MATERIAL_MOTION_BLOCKING = BlockBehaviour.BlockStateBase::blocksMotion;
    private final BitStorage data;
    private final Predicate<BlockState> isOpaque;
    private final ChunkAccess chunk;

    public Heightmap(ChunkAccess chunk, Heightmap.Types type) {
        this.isOpaque = type.isOpaque();
        this.chunk = chunk;
        int i = Mth.ceillog2(chunk.getHeight() + 1);
        this.data = new SimpleBitStorage(i, 256);
    }

    public static void primeHeightmaps(ChunkAccess chunk, Set<Heightmap.Types> types) {
        int i = types.size();
        ObjectList<Heightmap> objectList = new ObjectArrayList<>(i);
        ObjectListIterator<Heightmap> objectListIterator = objectList.iterator();
        int j = chunk.getHighestSectionPosition() + 16;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; k++) {
            for (int l = 0; l < 16; l++) {
                for (Heightmap.Types types2 : types) {
                    objectList.add(chunk.getOrCreateHeightmapUnprimed(types2));
                }

                for (int m = j - 1; m >= chunk.getMinBuildHeight(); m--) {
                    mutableBlockPos.set(k, m, l);
                    BlockState blockState = chunk.getBlockState(mutableBlockPos);
                    if (!blockState.is(Blocks.AIR)) {
                        while (objectListIterator.hasNext()) {
                            Heightmap heightmap = objectListIterator.next();
                            if (heightmap.isOpaque.test(blockState)) {
                                heightmap.setHeight(k, l, m + 1);
                                objectListIterator.remove();
                            }
                        }

                        if (objectList.isEmpty()) {
                            break;
                        }

                        objectListIterator.back(i);
                    }
                }
            }
        }
    }

    public boolean update(int x, int y, int z, BlockState state) {
        int i = this.getFirstAvailable(x, z);
        if (y <= i - 2) {
            return false;
        } else {
            if (this.isOpaque.test(state)) {
                if (y >= i) {
                    this.setHeight(x, z, y + 1);
                    return true;
                }
            } else if (i - 1 == y) {
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int j = y - 1; j >= this.chunk.getMinBuildHeight(); j--) {
                    mutableBlockPos.set(x, j, z);
                    if (this.isOpaque.test(this.chunk.getBlockState(mutableBlockPos))) {
                        this.setHeight(x, z, j + 1);
                        return true;
                    }
                }

                this.setHeight(x, z, this.chunk.getMinBuildHeight());
                return true;
            }

            return false;
        }
    }

    public int getFirstAvailable(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z));
    }

    public int getHighestTaken(int x, int z) {
        return this.getFirstAvailable(getIndex(x, z)) - 1;
    }

    private int getFirstAvailable(int index) {
        return this.data.get(index) + this.chunk.getMinBuildHeight();
    }

    private void setHeight(int x, int z, int height) {
        this.data.set(getIndex(x, z), height - this.chunk.getMinBuildHeight());
    }

    public void setRawData(ChunkAccess chunk, Heightmap.Types type, long[] values) {
        long[] ls = this.data.getRaw();
        if (ls.length == values.length) {
            System.arraycopy(values, 0, ls, 0, values.length);
        } else {
            LOGGER.warn("Ignoring heightmap data for chunk " + chunk.getPos() + ", size does not match; expected: " + ls.length + ", got: " + values.length);
            primeHeightmaps(chunk, EnumSet.of(type));
        }
    }

    public long[] getRawData() {
        return this.data.getRaw();
    }

    private static int getIndex(int x, int z) {
        return x + z * 16;
    }

    public static enum Types implements StringRepresentable {
        WORLD_SURFACE_WG("WORLD_SURFACE_WG", Heightmap.Usage.WORLDGEN, Heightmap.NOT_AIR),
        WORLD_SURFACE("WORLD_SURFACE", Heightmap.Usage.CLIENT, Heightmap.NOT_AIR),
        OCEAN_FLOOR_WG("OCEAN_FLOOR_WG", Heightmap.Usage.WORLDGEN, Heightmap.MATERIAL_MOTION_BLOCKING),
        OCEAN_FLOOR("OCEAN_FLOOR", Heightmap.Usage.LIVE_WORLD, Heightmap.MATERIAL_MOTION_BLOCKING),
        MOTION_BLOCKING("MOTION_BLOCKING", Heightmap.Usage.CLIENT, state -> state.blocksMotion() || !state.getFluidState().isEmpty()),
        MOTION_BLOCKING_NO_LEAVES(
            "MOTION_BLOCKING_NO_LEAVES",
            Heightmap.Usage.LIVE_WORLD,
            state -> (state.blocksMotion() || !state.getFluidState().isEmpty()) && !(state.getBlock() instanceof LeavesBlock)
        );

        public static final Codec<Heightmap.Types> CODEC = StringRepresentable.fromEnum(Heightmap.Types::values);
        private final String serializationKey;
        private final Heightmap.Usage usage;
        private final Predicate<BlockState> isOpaque;

        private Types(final String name, final Heightmap.Usage purpose, final Predicate<BlockState> blockPredicate) {
            this.serializationKey = name;
            this.usage = purpose;
            this.isOpaque = blockPredicate;
        }

        public String getSerializationKey() {
            return this.serializationKey;
        }

        public boolean sendToClient() {
            return this.usage == Heightmap.Usage.CLIENT;
        }

        public boolean keepAfterWorldgen() {
            return this.usage != Heightmap.Usage.WORLDGEN;
        }

        public Predicate<BlockState> isOpaque() {
            return this.isOpaque;
        }

        @Override
        public String getSerializedName() {
            return this.serializationKey;
        }
    }

    public static enum Usage {
        WORLDGEN,
        LIVE_WORLD,
        CLIENT;
    }
}
