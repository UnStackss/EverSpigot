package net.minecraft.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ChatType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorPreset;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

public class RegistryDataLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RegistrationInfo NETWORK_REGISTRATION_INFO = new RegistrationInfo(Optional.empty(), Lifecycle.experimental());
    private static final Function<Optional<KnownPack>, RegistrationInfo> REGISTRATION_INFO_CACHE = Util.memoize(knownPacks -> {
        Lifecycle lifecycle = knownPacks.map(KnownPack::isVanilla).map(vanilla -> Lifecycle.stable()).orElse(Lifecycle.experimental());
        return new RegistrationInfo(knownPacks, lifecycle);
    });
    public static final List<RegistryDataLoader.RegistryData<?>> WORLDGEN_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_CARVER, ConfiguredWorldCarver.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CONFIGURED_FEATURE, ConfiguredFeature.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.PLACED_FEATURE, PlacedFeature.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE, Structure.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.STRUCTURE_SET, StructureSet.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.PROCESSOR_LIST, StructureProcessorType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TEMPLATE_POOL, StructureTemplatePool.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.NOISE_SETTINGS, NoiseGeneratorSettings.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.NOISE, NormalNoise.NoiseParameters.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.DENSITY_FUNCTION, DensityFunction.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WORLD_PRESET, WorldPreset.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.FLAT_LEVEL_GENERATOR_PRESET, FlatLevelGeneratorPreset.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterList.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT_PROVIDER, EnchantmentProvider.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC)
    );
    public static final List<RegistryDataLoader.RegistryData<?>> DIMENSION_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.LEVEL_STEM, LevelStem.CODEC)
    );
    public static final List<RegistryDataLoader.RegistryData<?>> SYNCHRONIZED_REGISTRIES = List.of(
        new RegistryDataLoader.RegistryData<>(Registries.BIOME, Biome.NETWORK_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.CHAT_TYPE, ChatType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_PATTERN, TrimPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.TRIM_MATERIAL, TrimMaterial.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.WOLF_VARIANT, WolfVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.PAINTING_VARIANT, PaintingVariant.DIRECT_CODEC, true),
        new RegistryDataLoader.RegistryData<>(Registries.DIMENSION_TYPE, DimensionType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.BANNER_PATTERN, BannerPattern.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.ENCHANTMENT, Enchantment.DIRECT_CODEC),
        new RegistryDataLoader.RegistryData<>(Registries.JUKEBOX_SONG, JukeboxSong.DIRECT_CODEC)
    );

    public static RegistryAccess.Frozen load(ResourceManager resourceManager, RegistryAccess registryManager, List<RegistryDataLoader.RegistryData<?>> entries) {
        return load((loader, infoGetter, conversions) -> loader.loadFromResources(resourceManager, infoGetter, conversions), registryManager, entries); // Paper - pass conversions
    }

    public static RegistryAccess.Frozen load(
        Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> data,
        ResourceProvider factory,
        RegistryAccess registryManager,
        List<RegistryDataLoader.RegistryData<?>> entries
    ) {
        return load((loader, infoGetter, conversions) -> loader.loadFromNetwork(data, factory, infoGetter, conversions), registryManager, entries); // Paper - pass conversions
    }

    private static RegistryAccess.Frozen load(
        RegistryDataLoader.LoadingFunction loadable, RegistryAccess baseRegistryManager, List<RegistryDataLoader.RegistryData<?>> entries
    ) {
        Map<ResourceKey<?>, Exception> map = new HashMap<>();
        List<RegistryDataLoader.Loader<?>> list = entries.stream().map(entry -> entry.create(Lifecycle.stable(), map)).collect(Collectors.toUnmodifiableList());
        RegistryOps.RegistryInfoLookup registryInfoLookup = createContext(baseRegistryManager, list);
        final io.papermc.paper.registry.data.util.Conversions conversions = new io.papermc.paper.registry.data.util.Conversions(registryInfoLookup); // Paper - create conversions
        list.forEach(loader -> loadable.apply((RegistryDataLoader.Loader<?>)loader, registryInfoLookup, conversions));
        list.forEach(loader -> {
            Registry<?> registry = loader.registry();
            io.papermc.paper.registry.PaperRegistryListenerManager.INSTANCE.runFreezeListeners(loader.registry.key(), conversions); // Paper - run pre-freeze listeners

            try {
                registry.freeze();
            } catch (Exception var4x) {
                map.put(registry.key(), var4x);
            }

            if (loader.data.requiredNonEmpty && registry.size() == 0) {
                map.put(registry.key(), new IllegalStateException("Registry must be non-empty"));
            }
        });
        if (!map.isEmpty()) {
            logErrors(map);
            throw new IllegalStateException("Failed to load registries due to above errors");
        } else {
            return new RegistryAccess.ImmutableRegistryAccess(list.stream().map(RegistryDataLoader.Loader::registry).toList()).freeze();
        }
    }

    private static RegistryOps.RegistryInfoLookup createContext(RegistryAccess baseRegistryManager, List<RegistryDataLoader.Loader<?>> additionalRegistries) {
        final Map<ResourceKey<? extends Registry<?>>, RegistryOps.RegistryInfo<?>> map = new HashMap<>();
        baseRegistryManager.registries().forEach(entry -> map.put(entry.key(), createInfoForContextRegistry(entry.value())));
        additionalRegistries.forEach(loader -> map.put(loader.registry.key(), createInfoForNewRegistry(loader.registry)));
        return new RegistryOps.RegistryInfoLookup() {
            @Override
            public <T> Optional<RegistryOps.RegistryInfo<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return Optional.ofNullable((RegistryOps.RegistryInfo<T>)map.get(registryRef));
            }
        };
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForNewRegistry(WritableRegistry<T> registry) {
        return new RegistryOps.RegistryInfo<>(registry.asLookup(), registry.createRegistrationLookup(), registry.registryLifecycle());
    }

    private static <T> RegistryOps.RegistryInfo<T> createInfoForContextRegistry(Registry<T> registry) {
        return new RegistryOps.RegistryInfo<>(registry.asLookup(), registry.asTagAddingLookup(), registry.registryLifecycle());
    }

    private static void logErrors(Map<ResourceKey<?>, Exception> exceptions) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        Map<ResourceLocation, Map<ResourceLocation, Exception>> map = exceptions.entrySet()
            .stream()
            .collect(Collectors.groupingBy(entry -> entry.getKey().registry(), Collectors.toMap(entry -> entry.getKey().location(), Entry::getValue)));
        map.entrySet().stream().sorted(Entry.comparingByKey()).forEach(entry -> {
            printWriter.printf("> Errors in registry %s:%n", entry.getKey());
            entry.getValue().entrySet().stream().sorted(Entry.comparingByKey()).forEach(elementEntry -> {
                printWriter.printf(">> Errors in element %s:%n", elementEntry.getKey());
                elementEntry.getValue().printStackTrace(printWriter);
            });
        });
        printWriter.flush();
        LOGGER.error("Registry loading errors:\n{}", stringWriter);
    }

    private static <E> void loadElementFromResource(
        WritableRegistry<E> registry, Decoder<E> decoder, RegistryOps<JsonElement> ops, ResourceKey<E> key, Resource resource, RegistrationInfo entryInfo, io.papermc.paper.registry.data.util.Conversions conversions
    ) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            DataResult<E> dataResult = decoder.parse(ops, jsonElement);
            E object = dataResult.getOrThrow();
            io.papermc.paper.registry.PaperRegistryListenerManager.INSTANCE.registerWithListeners(registry, key, object, entryInfo, conversions); // Paper - register with listeners
        }
    }

    static <E> void loadContentsFromManager(
        ResourceManager resourceManager,
        RegistryOps.RegistryInfoLookup infoGetter,
        WritableRegistry<E> registry,
        Decoder<E> elementDecoder,
        Map<ResourceKey<?>, Exception> errors,
        io.papermc.paper.registry.data.util.Conversions conversions // Paper - pass conversions
    ) {
        String string = Registries.elementsDirPath(registry.key());
        FileToIdConverter fileToIdConverter = FileToIdConverter.json(string);
        RegistryOps<JsonElement> registryOps = RegistryOps.create(JsonOps.INSTANCE, infoGetter);

        for (Entry<ResourceLocation, Resource> entry : fileToIdConverter.listMatchingResources(resourceManager).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceKey<E> resourceKey = ResourceKey.create(registry.key(), fileToIdConverter.fileToId(resourceLocation));
            Resource resource = entry.getValue();
            RegistrationInfo registrationInfo = REGISTRATION_INFO_CACHE.apply(resource.knownPackInfo());

            try {
                loadElementFromResource(registry, elementDecoder, registryOps, resourceKey, resource, registrationInfo, conversions); // Paper - pass conversions
            } catch (Exception var15) {
                errors.put(
                    resourceKey,
                    new IllegalStateException(String.format(Locale.ROOT, "Failed to parse %s from pack %s", resourceLocation, resource.sourcePackId()), var15)
                );
            }
        }
    }

    static <E> void loadContentsFromNetwork(
        Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> data,
        ResourceProvider factory,
        RegistryOps.RegistryInfoLookup infoGetter,
        WritableRegistry<E> registry,
        Decoder<E> decoder,
        Map<ResourceKey<?>, Exception> loadingErrors,
        io.papermc.paper.registry.data.util.Conversions conversions // Paper - pass conversions
    ) {
        List<RegistrySynchronization.PackedRegistryEntry> list = data.get(registry.key());
        if (list != null) {
            RegistryOps<Tag> registryOps = RegistryOps.create(NbtOps.INSTANCE, infoGetter);
            RegistryOps<JsonElement> registryOps2 = RegistryOps.create(JsonOps.INSTANCE, infoGetter);
            String string = Registries.elementsDirPath(registry.key());
            FileToIdConverter fileToIdConverter = FileToIdConverter.json(string);

            for (RegistrySynchronization.PackedRegistryEntry packedRegistryEntry : list) {
                ResourceKey<E> resourceKey = ResourceKey.create(registry.key(), packedRegistryEntry.id());
                Optional<Tag> optional = packedRegistryEntry.data();
                if (optional.isPresent()) {
                    try {
                        DataResult<E> dataResult = decoder.parse(registryOps, optional.get());
                        E object = dataResult.getOrThrow();
                        registry.register(resourceKey, object, NETWORK_REGISTRATION_INFO);
                    } catch (Exception var17) {
                        loadingErrors.put(
                            resourceKey, new IllegalStateException(String.format(Locale.ROOT, "Failed to parse value %s from server", optional.get()), var17)
                        );
                    }
                } else {
                    ResourceLocation resourceLocation = fileToIdConverter.idToFile(packedRegistryEntry.id());

                    try {
                        Resource resource = factory.getResourceOrThrow(resourceLocation);
                        loadElementFromResource(registry, decoder, registryOps2, resourceKey, resource, NETWORK_REGISTRATION_INFO, conversions); // Paper - pass conversions
                    } catch (Exception var18) {
                        loadingErrors.put(resourceKey, new IllegalStateException("Failed to parse local data", var18));
                    }
                }
            }
        }
    }

    static record Loader<T>(RegistryDataLoader.RegistryData<T> data, WritableRegistry<T> registry, Map<ResourceKey<?>, Exception> loadingErrors) {
        public void loadFromResources(ResourceManager resourceManager, RegistryOps.RegistryInfoLookup infoGetter, io.papermc.paper.registry.data.util.Conversions conversions) { // Paper - pass conversions
            RegistryDataLoader.loadContentsFromManager(resourceManager, infoGetter, this.registry, this.data.elementCodec, this.loadingErrors, conversions); // Paper - pass conversions
        }

        public void loadFromNetwork(
            Map<ResourceKey<? extends Registry<?>>, List<RegistrySynchronization.PackedRegistryEntry>> data,
            ResourceProvider factory,
            RegistryOps.RegistryInfoLookup infoGetter,
            io.papermc.paper.registry.data.util.Conversions conversions // Paper
        ) {
            RegistryDataLoader.loadContentsFromNetwork(data, factory, infoGetter, this.registry, this.data.elementCodec, this.loadingErrors, conversions); // Paper - pass conversions
        }
    }

    @FunctionalInterface
    interface LoadingFunction {
        void apply(RegistryDataLoader.Loader<?> loader, RegistryOps.RegistryInfoLookup infoGetter, io.papermc.paper.registry.data.util.Conversions conversions); // Paper - pass conversions
    }

    public static record RegistryData<T>(ResourceKey<? extends Registry<T>> key, Codec<T> elementCodec, boolean requiredNonEmpty) {
        RegistryData(ResourceKey<? extends Registry<T>> resourceKey, Codec<T> codec) {
            this(resourceKey, codec, false);
        }

        RegistryDataLoader.Loader<T> create(Lifecycle lifecycle, Map<ResourceKey<?>, Exception> errors) {
            WritableRegistry<T> writableRegistry = new MappedRegistry<>(this.key, lifecycle);
            io.papermc.paper.registry.PaperRegistryAccess.instance().registerRegistry(this.key, writableRegistry); // Paper - initialize API registry
            return new RegistryDataLoader.Loader<>(this, writableRegistry, errors);
        }

        public void runWithArguments(BiConsumer<ResourceKey<? extends Registry<T>>, Codec<T>> callback) {
            callback.accept(this.key, this.elementCodec);
        }
    }
}
