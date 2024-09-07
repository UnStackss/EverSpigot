package net.minecraft.world.level.levelgen.flat;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.placement.MiscOverworldPlacements;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

public class FlatLevelGeneratorSettings {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<FlatLevelGeneratorSettings> CODEC = RecordCodecBuilder.<FlatLevelGeneratorSettings>create(
            instance -> instance.group(
                        RegistryCodecs.homogeneousList(Registries.STRUCTURE_SET)
                            .lenientOptionalFieldOf("structure_overrides")
                            .forGetter(config -> config.structureOverrides),
                        FlatLayerInfo.CODEC.listOf().fieldOf("layers").forGetter(FlatLevelGeneratorSettings::getLayersInfo),
                        Codec.BOOL.fieldOf("lakes").orElse(false).forGetter(config -> config.addLakes),
                        Codec.BOOL.fieldOf("features").orElse(false).forGetter(config -> config.decoration),
                        Biome.CODEC.lenientOptionalFieldOf("biome").orElseGet(Optional::empty).forGetter(config -> Optional.of(config.biome)),
                        RegistryOps.retrieveElement(Biomes.PLAINS),
                        RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND),
                        RegistryOps.retrieveElement(MiscOverworldPlacements.LAKE_LAVA_SURFACE)
                    )
                    .apply(instance, FlatLevelGeneratorSettings::new)
        )
        .comapFlatMap(FlatLevelGeneratorSettings::validateHeight, Function.identity())
        .stable();
    private final Optional<HolderSet<StructureSet>> structureOverrides;
    private final List<FlatLayerInfo> layersInfo = Lists.newArrayList();
    private final Holder<Biome> biome;
    private final List<BlockState> layers;
    private boolean voidGen;
    private boolean decoration;
    private boolean addLakes;
    private final List<Holder<PlacedFeature>> lakes;

    private static DataResult<FlatLevelGeneratorSettings> validateHeight(FlatLevelGeneratorSettings config) {
        int i = config.layersInfo.stream().mapToInt(FlatLayerInfo::getHeight).sum();
        return i > DimensionType.Y_SIZE ? DataResult.error(() -> "Sum of layer heights is > " + DimensionType.Y_SIZE, config) : DataResult.success(config);
    }

    private FlatLevelGeneratorSettings(
        Optional<HolderSet<StructureSet>> structureOverrides,
        List<FlatLayerInfo> layers,
        boolean lakes,
        boolean features,
        Optional<Holder<Biome>> biome,
        Holder.Reference<Biome> fallback,
        Holder<PlacedFeature> undergroundLavaLakeFeature,
        Holder<PlacedFeature> surfaceLavaLakeFeature
    ) {
        this(structureOverrides, getBiome(biome, fallback), List.of(undergroundLavaLakeFeature, surfaceLavaLakeFeature));
        if (lakes) {
            this.setAddLakes();
        }

        if (features) {
            this.setDecoration();
        }

        this.layersInfo.addAll(layers);
        this.updateLayers();
    }

    private static Holder<Biome> getBiome(Optional<? extends Holder<Biome>> biome, Holder<Biome> fallback) {
        if (biome.isEmpty()) {
            LOGGER.error("Unknown biome, defaulting to plains");
            return fallback;
        } else {
            return (Holder<Biome>)biome.get();
        }
    }

    public FlatLevelGeneratorSettings(Optional<HolderSet<StructureSet>> structureOverrides, Holder<Biome> biome, List<Holder<PlacedFeature>> features) {
        this.structureOverrides = structureOverrides;
        this.biome = biome;
        this.layers = Lists.newArrayList();
        this.lakes = features;
    }

    public FlatLevelGeneratorSettings withBiomeAndLayers(List<FlatLayerInfo> layers, Optional<HolderSet<StructureSet>> structureOverrides, Holder<Biome> biome) {
        FlatLevelGeneratorSettings flatLevelGeneratorSettings = new FlatLevelGeneratorSettings(structureOverrides, biome, this.lakes);

        for (FlatLayerInfo flatLayerInfo : layers) {
            flatLevelGeneratorSettings.layersInfo.add(new FlatLayerInfo(flatLayerInfo.getHeight(), flatLayerInfo.getBlockState().getBlock()));
            flatLevelGeneratorSettings.updateLayers();
        }

        if (this.decoration) {
            flatLevelGeneratorSettings.setDecoration();
        }

        if (this.addLakes) {
            flatLevelGeneratorSettings.setAddLakes();
        }

        return flatLevelGeneratorSettings;
    }

    public void setDecoration() {
        this.decoration = true;
    }

    public void setAddLakes() {
        this.addLakes = true;
    }

    public BiomeGenerationSettings adjustGenerationSettings(Holder<Biome> biomeEntry) {
        if (!biomeEntry.equals(this.biome)) {
            return biomeEntry.value().getGenerationSettings();
        } else {
            BiomeGenerationSettings biomeGenerationSettings = this.getBiome().value().getGenerationSettings();
            BiomeGenerationSettings.PlainBuilder plainBuilder = new BiomeGenerationSettings.PlainBuilder();
            if (this.addLakes) {
                for (Holder<PlacedFeature> holder : this.lakes) {
                    plainBuilder.addFeature(GenerationStep.Decoration.LAKES, holder);
                }
            }

            boolean bl = (!this.voidGen || biomeEntry.is(Biomes.THE_VOID)) && this.decoration;
            if (bl) {
                List<HolderSet<PlacedFeature>> list = biomeGenerationSettings.features();

                for (int i = 0; i < list.size(); i++) {
                    if (i != GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal()
                        && i != GenerationStep.Decoration.SURFACE_STRUCTURES.ordinal()
                        && (!this.addLakes || i != GenerationStep.Decoration.LAKES.ordinal())) {
                        for (Holder<PlacedFeature> holder2 : list.get(i)) {
                            plainBuilder.addFeature(i, holder2);
                        }
                    }
                }
            }

            List<BlockState> list2 = this.getLayers();

            for (int j = 0; j < list2.size(); j++) {
                BlockState blockState = list2.get(j);
                if (!Heightmap.Types.MOTION_BLOCKING.isOpaque().test(blockState)) {
                    list2.set(j, null);
                    plainBuilder.addFeature(
                        GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
                        PlacementUtils.inlinePlaced(Feature.FILL_LAYER, new LayerConfiguration(j, blockState))
                    );
                }
            }

            return plainBuilder.build();
        }
    }

    public Optional<HolderSet<StructureSet>> structureOverrides() {
        return this.structureOverrides;
    }

    public Holder<Biome> getBiome() {
        return this.biome;
    }

    public List<FlatLayerInfo> getLayersInfo() {
        return this.layersInfo;
    }

    public List<BlockState> getLayers() {
        return this.layers;
    }

    public void updateLayers() {
        this.layers.clear();

        for (FlatLayerInfo flatLayerInfo : this.layersInfo) {
            for (int i = 0; i < flatLayerInfo.getHeight(); i++) {
                this.layers.add(flatLayerInfo.getBlockState());
            }
        }

        this.voidGen = this.layers.stream().allMatch(state -> state.is(Blocks.AIR));
    }

    public static FlatLevelGeneratorSettings getDefault(
        HolderGetter<Biome> biomeLookup, HolderGetter<StructureSet> structureSetLookup, HolderGetter<PlacedFeature> featureLookup
    ) {
        HolderSet<StructureSet> holderSet = HolderSet.direct(
            structureSetLookup.getOrThrow(BuiltinStructureSets.STRONGHOLDS), structureSetLookup.getOrThrow(BuiltinStructureSets.VILLAGES)
        );
        FlatLevelGeneratorSettings flatLevelGeneratorSettings = new FlatLevelGeneratorSettings(
            Optional.of(holderSet), getDefaultBiome(biomeLookup), createLakesList(featureLookup)
        );
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.BEDROCK));
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(2, Blocks.DIRT));
        flatLevelGeneratorSettings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.GRASS_BLOCK));
        flatLevelGeneratorSettings.updateLayers();
        return flatLevelGeneratorSettings;
    }

    public static Holder<Biome> getDefaultBiome(HolderGetter<Biome> biomeLookup) {
        return biomeLookup.getOrThrow(Biomes.PLAINS);
    }

    public static List<Holder<PlacedFeature>> createLakesList(HolderGetter<PlacedFeature> featureLookup) {
        return List.of(
            featureLookup.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_UNDERGROUND), featureLookup.getOrThrow(MiscOverworldPlacements.LAKE_LAVA_SURFACE)
        );
    }
}
