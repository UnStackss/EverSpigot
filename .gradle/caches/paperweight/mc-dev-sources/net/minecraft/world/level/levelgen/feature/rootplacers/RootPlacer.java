package net.minecraft.world.level.levelgen.feature.rootplacers;

import com.mojang.datafixers.Products.P3;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public abstract class RootPlacer {
    public static final Codec<RootPlacer> CODEC = BuiltInRegistries.ROOT_PLACER_TYPE.byNameCodec().dispatch(RootPlacer::type, RootPlacerType::codec);
    protected final IntProvider trunkOffsetY;
    protected final BlockStateProvider rootProvider;
    protected final Optional<AboveRootPlacement> aboveRootPlacement;

    protected static <P extends RootPlacer> P3<Mu<P>, IntProvider, BlockStateProvider, Optional<AboveRootPlacement>> rootPlacerParts(Instance<P> instance) {
        return instance.group(
            IntProvider.CODEC.fieldOf("trunk_offset_y").forGetter(rootPlacer -> rootPlacer.trunkOffsetY),
            BlockStateProvider.CODEC.fieldOf("root_provider").forGetter(rootPlacer -> rootPlacer.rootProvider),
            AboveRootPlacement.CODEC.optionalFieldOf("above_root_placement").forGetter(rootPlacer -> rootPlacer.aboveRootPlacement)
        );
    }

    public RootPlacer(IntProvider trunkOffsetY, BlockStateProvider rootProvider, Optional<AboveRootPlacement> aboveRootPlacement) {
        this.trunkOffsetY = trunkOffsetY;
        this.rootProvider = rootProvider;
        this.aboveRootPlacement = aboveRootPlacement;
    }

    protected abstract RootPlacerType<?> type();

    public abstract boolean placeRoots(
        LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, BlockPos trunkPos, TreeConfiguration config
    );

    protected boolean canPlaceRoot(LevelSimulatedReader world, BlockPos pos) {
        return TreeFeature.validTreePos(world, pos);
    }

    protected void placeRoot(LevelSimulatedReader world, BiConsumer<BlockPos, BlockState> replacer, RandomSource random, BlockPos pos, TreeConfiguration config) {
        if (this.canPlaceRoot(world, pos)) {
            replacer.accept(pos, this.getPotentiallyWaterloggedState(world, pos, this.rootProvider.getState(random, pos)));
            if (this.aboveRootPlacement.isPresent()) {
                AboveRootPlacement aboveRootPlacement = this.aboveRootPlacement.get();
                BlockPos blockPos = pos.above();
                if (random.nextFloat() < aboveRootPlacement.aboveRootPlacementChance()
                    && world.isStateAtPosition(blockPos, BlockBehaviour.BlockStateBase::isAir)) {
                    replacer.accept(
                        blockPos, this.getPotentiallyWaterloggedState(world, blockPos, aboveRootPlacement.aboveRootProvider().getState(random, blockPos))
                    );
                }
            }
        }
    }

    protected BlockState getPotentiallyWaterloggedState(LevelSimulatedReader world, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            boolean bl = world.isFluidAtPosition(pos, fluidState -> fluidState.is(FluidTags.WATER));
            return state.setValue(BlockStateProperties.WATERLOGGED, Boolean.valueOf(bl));
        } else {
            return state;
        }
    }

    public BlockPos getTrunkOrigin(BlockPos pos, RandomSource random) {
        return pos.above(this.trunkOffsetY.sample(random));
    }
}
