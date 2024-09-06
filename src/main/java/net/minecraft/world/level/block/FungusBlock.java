package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FungusBlock extends BushBlock implements BonemealableBlock {

    public static final MapCodec<FungusBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(ResourceKey.codec(Registries.CONFIGURED_FEATURE).fieldOf("feature").forGetter((blockfungi) -> {
            return blockfungi.feature;
        }), BuiltInRegistries.BLOCK.byNameCodec().fieldOf("grows_on").forGetter((blockfungi) -> {
            return blockfungi.requiredBlock;
        }), propertiesCodec()).apply(instance, FungusBlock::new);
    });
    protected static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 9.0D, 12.0D);
    private static final double BONEMEAL_SUCCESS_PROBABILITY = 0.4D;
    private final Block requiredBlock;
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    @Override
    public MapCodec<FungusBlock> codec() {
        return FungusBlock.CODEC;
    }

    protected FungusBlock(ResourceKey<ConfiguredFeature<?, ?>> featureKey, Block nylium, BlockBehaviour.Properties settings) {
        super(settings);
        this.feature = featureKey;
        this.requiredBlock = nylium;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return FungusBlock.SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return floor.is(BlockTags.NYLIUM) || floor.is(Blocks.MYCELIUM) || floor.is(Blocks.SOUL_SOIL) || super.mayPlaceOn(floor, world, pos);
    }

    private Optional<? extends Holder<ConfiguredFeature<?, ?>>> getFeature(LevelReader world) {
        return world.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(this.feature);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader world, BlockPos pos, BlockState state) {
        BlockState iblockdata1 = world.getBlockState(pos.below());

        return iblockdata1.is(this.requiredBlock);
    }

    @Override
    public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos pos, BlockState state) {
        return (double) random.nextFloat() < 0.4D;
    }

    @Override
    public void performBonemeal(ServerLevel world, RandomSource random, BlockPos pos, BlockState state) {
        this.getFeature(world).ifPresent((holder) -> {
            // CraftBukkit start
            if (this == Blocks.WARPED_FUNGUS) {
                SaplingBlock.treeType = org.bukkit.TreeType.WARPED_FUNGUS;
            } else if (this == Blocks.CRIMSON_FUNGUS) {
                SaplingBlock.treeType = org.bukkit.TreeType.CRIMSON_FUNGUS;
            }
            // CraftBukkit end
            ((ConfiguredFeature) holder.value()).place(world, world.getChunkSource().getGenerator(), random, pos);
        });
    }
}
