package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public abstract class TreeDecorator {
    public static final Codec<TreeDecorator> CODEC = BuiltInRegistries.TREE_DECORATOR_TYPE
        .byNameCodec()
        .dispatch(TreeDecorator::type, TreeDecoratorType::codec);

    protected abstract TreeDecoratorType<?> type();

    public abstract void place(TreeDecorator.Context generator);

    public static final class Context {
        private final LevelSimulatedReader level;
        private final BiConsumer<BlockPos, BlockState> decorationSetter;
        private final RandomSource random;
        private final ObjectArrayList<BlockPos> logs;
        private final ObjectArrayList<BlockPos> leaves;
        private final ObjectArrayList<BlockPos> roots;

        public Context(
            LevelSimulatedReader world,
            BiConsumer<BlockPos, BlockState> replacer,
            RandomSource random,
            Set<BlockPos> logPositions,
            Set<BlockPos> leavesPositions,
            Set<BlockPos> rootPositions
        ) {
            this.level = world;
            this.decorationSetter = replacer;
            this.random = random;
            this.roots = new ObjectArrayList<>(rootPositions);
            this.logs = new ObjectArrayList<>(logPositions);
            this.leaves = new ObjectArrayList<>(leavesPositions);
            this.logs.sort(Comparator.comparingInt(Vec3i::getY));
            this.leaves.sort(Comparator.comparingInt(Vec3i::getY));
            this.roots.sort(Comparator.comparingInt(Vec3i::getY));
        }

        public void placeVine(BlockPos pos, BooleanProperty faceProperty) {
            this.setBlock(pos, Blocks.VINE.defaultBlockState().setValue(faceProperty, Boolean.valueOf(true)));
        }

        public void setBlock(BlockPos pos, BlockState state) {
            this.decorationSetter.accept(pos, state);
        }

        public boolean isAir(BlockPos pos) {
            return this.level.isStateAtPosition(pos, BlockBehaviour.BlockStateBase::isAir);
        }

        public LevelSimulatedReader level() {
            return this.level;
        }

        public RandomSource random() {
            return this.random;
        }

        public ObjectArrayList<BlockPos> logs() {
            return this.logs;
        }

        public ObjectArrayList<BlockPos> leaves() {
            return this.leaves;
        }

        public ObjectArrayList<BlockPos> roots() {
            return this.roots;
        }
    }
}
