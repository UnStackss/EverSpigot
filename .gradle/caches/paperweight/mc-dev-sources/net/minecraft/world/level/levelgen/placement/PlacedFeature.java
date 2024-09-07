package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.apache.commons.lang3.mutable.MutableBoolean;

public record PlacedFeature(Holder<ConfiguredFeature<?, ?>> feature, List<PlacementModifier> placement) {
    public static final Codec<PlacedFeature> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ConfiguredFeature.CODEC.fieldOf("feature").forGetter(placedFeature -> placedFeature.feature),
                    PlacementModifier.CODEC.listOf().fieldOf("placement").forGetter(placedFeature -> placedFeature.placement)
                )
                .apply(instance, PlacedFeature::new)
    );
    public static final Codec<Holder<PlacedFeature>> CODEC = RegistryFileCodec.create(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<HolderSet<PlacedFeature>> LIST_CODEC = RegistryCodecs.homogeneousList(Registries.PLACED_FEATURE, DIRECT_CODEC);
    public static final Codec<List<HolderSet<PlacedFeature>>> LIST_OF_LISTS_CODEC = RegistryCodecs.homogeneousList(
            Registries.PLACED_FEATURE, DIRECT_CODEC, true
        )
        .listOf();

    public boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(world, generator, Optional.empty()), random, pos);
    }

    public boolean placeWithBiomeCheck(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos pos) {
        return this.placeWithContext(new PlacementContext(world, generator, Optional.of(this)), random, pos);
    }

    private boolean placeWithContext(PlacementContext context, RandomSource random, BlockPos pos) {
        Stream<BlockPos> stream = Stream.of(pos);

        for (PlacementModifier placementModifier : this.placement) {
            stream = stream.flatMap(posx -> placementModifier.getPositions(context, random, posx));
        }

        ConfiguredFeature<?, ?> configuredFeature = this.feature.value();
        MutableBoolean mutableBoolean = new MutableBoolean();
        stream.forEach(placedPos -> {
            if (configuredFeature.place(context.getLevel(), context.generator(), random, placedPos)) {
                mutableBoolean.setTrue();
            }
        });
        return mutableBoolean.isTrue();
    }

    public Stream<ConfiguredFeature<?, ?>> getFeatures() {
        return this.feature.value().getFeatures();
    }

    @Override
    public String toString() {
        return "Placed " + this.feature;
    }
}
