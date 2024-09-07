package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicLike;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

public class WorldGenSettingsFix extends DataFix {
    private static final String VILLAGE = "minecraft:village";
    private static final String DESERT_PYRAMID = "minecraft:desert_pyramid";
    private static final String IGLOO = "minecraft:igloo";
    private static final String JUNGLE_TEMPLE = "minecraft:jungle_pyramid";
    private static final String SWAMP_HUT = "minecraft:swamp_hut";
    private static final String PILLAGER_OUTPOST = "minecraft:pillager_outpost";
    private static final String END_CITY = "minecraft:endcity";
    private static final String WOODLAND_MANSION = "minecraft:mansion";
    private static final String OCEAN_MONUMENT = "minecraft:monument";
    private static final ImmutableMap<String, WorldGenSettingsFix.StructureFeatureConfiguration> DEFAULTS = ImmutableMap.<String, WorldGenSettingsFix.StructureFeatureConfiguration>builder()
        .put("minecraft:village", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 10387312))
        .put("minecraft:desert_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357617))
        .put("minecraft:igloo", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357618))
        .put("minecraft:jungle_pyramid", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357619))
        .put("minecraft:swamp_hut", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 14357620))
        .put("minecraft:pillager_outpost", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 8, 165745296))
        .put("minecraft:monument", new WorldGenSettingsFix.StructureFeatureConfiguration(32, 5, 10387313))
        .put("minecraft:endcity", new WorldGenSettingsFix.StructureFeatureConfiguration(20, 11, 10387313))
        .put("minecraft:mansion", new WorldGenSettingsFix.StructureFeatureConfiguration(80, 20, 10387319))
        .build();

    public WorldGenSettingsFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            "WorldGenSettings building",
            this.getInputSchema().getType(References.WORLD_GEN_SETTINGS),
            worldGenSettingsTyped -> worldGenSettingsTyped.update(DSL.remainderFinder(), WorldGenSettingsFix::fix)
        );
    }

    private static <T> Dynamic<T> noise(long seed, DynamicLike<T> worldGenSettingsDynamic, Dynamic<T> settingsDynamic, Dynamic<T> biomeSourceDynamic) {
        return worldGenSettingsDynamic.createMap(
            ImmutableMap.of(
                worldGenSettingsDynamic.createString("type"),
                worldGenSettingsDynamic.createString("minecraft:noise"),
                worldGenSettingsDynamic.createString("biome_source"),
                biomeSourceDynamic,
                worldGenSettingsDynamic.createString("seed"),
                worldGenSettingsDynamic.createLong(seed),
                worldGenSettingsDynamic.createString("settings"),
                settingsDynamic
            )
        );
    }

    private static <T> Dynamic<T> vanillaBiomeSource(Dynamic<T> worldGenSettingsDynamic, long seed, boolean legacyBiomeInitLayer, boolean largeBiomes) {
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.<Dynamic<T>, Dynamic<T>>builder()
            .put(worldGenSettingsDynamic.createString("type"), worldGenSettingsDynamic.createString("minecraft:vanilla_layered"))
            .put(worldGenSettingsDynamic.createString("seed"), worldGenSettingsDynamic.createLong(seed))
            .put(worldGenSettingsDynamic.createString("large_biomes"), worldGenSettingsDynamic.createBoolean(largeBiomes));
        if (legacyBiomeInitLayer) {
            builder.put(worldGenSettingsDynamic.createString("legacy_biome_init_layer"), worldGenSettingsDynamic.createBoolean(legacyBiomeInitLayer));
        }

        return worldGenSettingsDynamic.createMap(builder.build());
    }

    private static <T> Dynamic<T> fix(Dynamic<T> worldGenSettingsDynamic) {
        DynamicOps<T> dynamicOps = worldGenSettingsDynamic.getOps();
        long l = worldGenSettingsDynamic.get("RandomSeed").asLong(0L);
        Optional<String> optional = worldGenSettingsDynamic.get("generatorName")
            .asString()
            .map(generatorName -> generatorName.toLowerCase(Locale.ROOT))
            .result();
        Optional<String> optional2 = worldGenSettingsDynamic.get("legacy_custom_options")
            .asString()
            .result()
            .map(Optional::of)
            .orElseGet(
                () -> optional.equals(Optional.of("customized")) ? worldGenSettingsDynamic.get("generatorOptions").asString().result() : Optional.empty()
            );
        boolean bl = false;
        Dynamic<T> dynamic;
        if (optional.equals(Optional.of("customized"))) {
            dynamic = defaultOverworld(worldGenSettingsDynamic, l);
        } else if (optional.isEmpty()) {
            dynamic = defaultOverworld(worldGenSettingsDynamic, l);
        } else {
            String bl6 = optional.get();
            switch (bl6) {
                case "flat":
                    OptionalDynamic<T> optionalDynamic = worldGenSettingsDynamic.get("generatorOptions");
                    Map<Dynamic<T>, Dynamic<T>> map = fixFlatStructures(dynamicOps, optionalDynamic);
                    dynamic = worldGenSettingsDynamic.createMap(
                        ImmutableMap.of(
                            worldGenSettingsDynamic.createString("type"),
                            worldGenSettingsDynamic.createString("minecraft:flat"),
                            worldGenSettingsDynamic.createString("settings"),
                            worldGenSettingsDynamic.createMap(
                                ImmutableMap.of(
                                    worldGenSettingsDynamic.createString("structures"),
                                    worldGenSettingsDynamic.createMap(map),
                                    worldGenSettingsDynamic.createString("layers"),
                                    optionalDynamic.get("layers")
                                        .result()
                                        .orElseGet(
                                            () -> worldGenSettingsDynamic.createList(
                                                    Stream.of(
                                                        worldGenSettingsDynamic.createMap(
                                                            ImmutableMap.of(
                                                                worldGenSettingsDynamic.createString("height"),
                                                                worldGenSettingsDynamic.createInt(1),
                                                                worldGenSettingsDynamic.createString("block"),
                                                                worldGenSettingsDynamic.createString("minecraft:bedrock")
                                                            )
                                                        ),
                                                        worldGenSettingsDynamic.createMap(
                                                            ImmutableMap.of(
                                                                worldGenSettingsDynamic.createString("height"),
                                                                worldGenSettingsDynamic.createInt(2),
                                                                worldGenSettingsDynamic.createString("block"),
                                                                worldGenSettingsDynamic.createString("minecraft:dirt")
                                                            )
                                                        ),
                                                        worldGenSettingsDynamic.createMap(
                                                            ImmutableMap.of(
                                                                worldGenSettingsDynamic.createString("height"),
                                                                worldGenSettingsDynamic.createInt(1),
                                                                worldGenSettingsDynamic.createString("block"),
                                                                worldGenSettingsDynamic.createString("minecraft:grass_block")
                                                            )
                                                        )
                                                    )
                                                )
                                        ),
                                    worldGenSettingsDynamic.createString("biome"),
                                    worldGenSettingsDynamic.createString(optionalDynamic.get("biome").asString("minecraft:plains"))
                                )
                            )
                        )
                    );
                    break;
                case "debug_all_block_states":
                    dynamic = worldGenSettingsDynamic.createMap(
                        ImmutableMap.of(worldGenSettingsDynamic.createString("type"), worldGenSettingsDynamic.createString("minecraft:debug"))
                    );
                    break;
                case "buffet":
                    OptionalDynamic<T> optionalDynamic2 = worldGenSettingsDynamic.get("generatorOptions");
                    OptionalDynamic<?> optionalDynamic3 = optionalDynamic2.get("chunk_generator");
                    Optional<String> optional3 = optionalDynamic3.get("type").asString().result();
                    Dynamic<T> dynamic5;
                    if (Objects.equals(optional3, Optional.of("minecraft:caves"))) {
                        dynamic5 = worldGenSettingsDynamic.createString("minecraft:caves");
                        bl = true;
                    } else if (Objects.equals(optional3, Optional.of("minecraft:floating_islands"))) {
                        dynamic5 = worldGenSettingsDynamic.createString("minecraft:floating_islands");
                    } else {
                        dynamic5 = worldGenSettingsDynamic.createString("minecraft:overworld");
                    }

                    Dynamic<T> dynamic8 = optionalDynamic2.get("biome_source")
                        .result()
                        .orElseGet(
                            () -> worldGenSettingsDynamic.createMap(
                                    ImmutableMap.of(worldGenSettingsDynamic.createString("type"), worldGenSettingsDynamic.createString("minecraft:fixed"))
                                )
                        );
                    Dynamic<T> dynamic9;
                    if (dynamic8.get("type").asString().result().equals(Optional.of("minecraft:fixed"))) {
                        String string = dynamic8.get("options")
                            .get("biomes")
                            .asStream()
                            .findFirst()
                            .flatMap(biomeDynamic -> biomeDynamic.asString().result())
                            .orElse("minecraft:ocean");
                        dynamic9 = dynamic8.remove("options").set("biome", worldGenSettingsDynamic.createString(string));
                    } else {
                        dynamic9 = dynamic8;
                    }

                    dynamic = noise(l, worldGenSettingsDynamic, dynamic5, dynamic9);
                    break;
                default:
                    boolean bl2 = optional.get().equals("default");
                    boolean bl3 = optional.get().equals("default_1_1") || bl2 && worldGenSettingsDynamic.get("generatorVersion").asInt(0) == 0;
                    boolean bl4 = optional.get().equals("amplified");
                    boolean bl5 = optional.get().equals("largebiomes");
                    dynamic = noise(
                        l,
                        worldGenSettingsDynamic,
                        worldGenSettingsDynamic.createString(bl4 ? "minecraft:amplified" : "minecraft:overworld"),
                        vanillaBiomeSource(worldGenSettingsDynamic, l, bl3, bl5)
                    );
            }
        }

        boolean bl6 = worldGenSettingsDynamic.get("MapFeatures").asBoolean(true);
        boolean bl7 = worldGenSettingsDynamic.get("BonusChest").asBoolean(false);
        Builder<T, T> builder = ImmutableMap.builder();
        builder.put(dynamicOps.createString("seed"), dynamicOps.createLong(l));
        builder.put(dynamicOps.createString("generate_features"), dynamicOps.createBoolean(bl6));
        builder.put(dynamicOps.createString("bonus_chest"), dynamicOps.createBoolean(bl7));
        builder.put(dynamicOps.createString("dimensions"), vanillaLevels(worldGenSettingsDynamic, l, dynamic, bl));
        optional2.ifPresent(legacyCustomOptions -> builder.put(dynamicOps.createString("legacy_custom_options"), dynamicOps.createString(legacyCustomOptions)));
        return new Dynamic<>(dynamicOps, dynamicOps.createMap(builder.build()));
    }

    protected static <T> Dynamic<T> defaultOverworld(Dynamic<T> worldGenSettingsDynamic, long seed) {
        return noise(
            seed,
            worldGenSettingsDynamic,
            worldGenSettingsDynamic.createString("minecraft:overworld"),
            vanillaBiomeSource(worldGenSettingsDynamic, seed, false, false)
        );
    }

    protected static <T> T vanillaLevels(Dynamic<T> worldGenSettingsDynamic, long seed, Dynamic<T> generatorSettingsDynamic, boolean caves) {
        DynamicOps<T> dynamicOps = worldGenSettingsDynamic.getOps();
        return dynamicOps.createMap(
            ImmutableMap.of(
                dynamicOps.createString("minecraft:overworld"),
                dynamicOps.createMap(
                    ImmutableMap.of(
                        dynamicOps.createString("type"),
                        dynamicOps.createString("minecraft:overworld" + (caves ? "_caves" : "")),
                        dynamicOps.createString("generator"),
                        generatorSettingsDynamic.getValue()
                    )
                ),
                dynamicOps.createString("minecraft:the_nether"),
                dynamicOps.createMap(
                    ImmutableMap.of(
                        dynamicOps.createString("type"),
                        dynamicOps.createString("minecraft:the_nether"),
                        dynamicOps.createString("generator"),
                        noise(
                                seed,
                                worldGenSettingsDynamic,
                                worldGenSettingsDynamic.createString("minecraft:nether"),
                                worldGenSettingsDynamic.createMap(
                                    ImmutableMap.of(
                                        worldGenSettingsDynamic.createString("type"),
                                        worldGenSettingsDynamic.createString("minecraft:multi_noise"),
                                        worldGenSettingsDynamic.createString("seed"),
                                        worldGenSettingsDynamic.createLong(seed),
                                        worldGenSettingsDynamic.createString("preset"),
                                        worldGenSettingsDynamic.createString("minecraft:nether")
                                    )
                                )
                            )
                            .getValue()
                    )
                ),
                dynamicOps.createString("minecraft:the_end"),
                dynamicOps.createMap(
                    ImmutableMap.of(
                        dynamicOps.createString("type"),
                        dynamicOps.createString("minecraft:the_end"),
                        dynamicOps.createString("generator"),
                        noise(
                                seed,
                                worldGenSettingsDynamic,
                                worldGenSettingsDynamic.createString("minecraft:end"),
                                worldGenSettingsDynamic.createMap(
                                    ImmutableMap.of(
                                        worldGenSettingsDynamic.createString("type"),
                                        worldGenSettingsDynamic.createString("minecraft:the_end"),
                                        worldGenSettingsDynamic.createString("seed"),
                                        worldGenSettingsDynamic.createLong(seed)
                                    )
                                )
                            )
                            .getValue()
                    )
                )
            )
        );
    }

    private static <T> Map<Dynamic<T>, Dynamic<T>> fixFlatStructures(DynamicOps<T> worldGenSettingsDynamicOps, OptionalDynamic<T> generatorOptionsDynamic) {
        MutableInt mutableInt = new MutableInt(32);
        MutableInt mutableInt2 = new MutableInt(3);
        MutableInt mutableInt3 = new MutableInt(128);
        MutableBoolean mutableBoolean = new MutableBoolean(false);
        Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map = Maps.newHashMap();
        if (generatorOptionsDynamic.result().isEmpty()) {
            mutableBoolean.setTrue();
            map.put("minecraft:village", DEFAULTS.get("minecraft:village"));
        }

        generatorOptionsDynamic.get("structures")
            .flatMap(Dynamic::getMapValues)
            .ifSuccess(
                map2 -> map2.forEach(
                        (oldStructureName, dynamic) -> dynamic.getMapValues()
                                .result()
                                .ifPresent(
                                    map2x -> map2x.forEach(
                                            (propertyName, spacing) -> {
                                                String string = oldStructureName.asString("");
                                                String string2 = propertyName.asString("");
                                                String string3 = spacing.asString("");
                                                if ("stronghold".equals(string)) {
                                                    mutableBoolean.setTrue();
                                                    switch (string2) {
                                                        case "distance":
                                                            mutableInt.setValue(getInt(string3, mutableInt.getValue(), 1));
                                                            return;
                                                        case "spread":
                                                            mutableInt2.setValue(getInt(string3, mutableInt2.getValue(), 1));
                                                            return;
                                                        case "count":
                                                            mutableInt3.setValue(getInt(string3, mutableInt3.getValue(), 1));
                                                            return;
                                                    }
                                                } else {
                                                    switch (string2) {
                                                        case "distance":
                                                            switch (string) {
                                                                case "village":
                                                                    setSpacing(map, "minecraft:village", string3, 9);
                                                                    return;
                                                                case "biome_1":
                                                                    setSpacing(map, "minecraft:desert_pyramid", string3, 9);
                                                                    setSpacing(map, "minecraft:igloo", string3, 9);
                                                                    setSpacing(map, "minecraft:jungle_pyramid", string3, 9);
                                                                    setSpacing(map, "minecraft:swamp_hut", string3, 9);
                                                                    setSpacing(map, "minecraft:pillager_outpost", string3, 9);
                                                                    return;
                                                                case "endcity":
                                                                    setSpacing(map, "minecraft:endcity", string3, 1);
                                                                    return;
                                                                case "mansion":
                                                                    setSpacing(map, "minecraft:mansion", string3, 1);
                                                                    return;
                                                                default:
                                                                    return;
                                                            }
                                                        case "separation":
                                                            if ("oceanmonument".equals(string)) {
                                                                WorldGenSettingsFix.StructureFeatureConfiguration structureFeatureConfiguration = map.getOrDefault(
                                                                    "minecraft:monument", DEFAULTS.get("minecraft:monument")
                                                                );
                                                                int i = getInt(string3, structureFeatureConfiguration.separation, 1);
                                                                map.put(
                                                                    "minecraft:monument",
                                                                    new WorldGenSettingsFix.StructureFeatureConfiguration(
                                                                        i, structureFeatureConfiguration.separation, structureFeatureConfiguration.salt
                                                                    )
                                                                );
                                                            }

                                                            return;
                                                        case "spacing":
                                                            if ("oceanmonument".equals(string)) {
                                                                setSpacing(map, "minecraft:monument", string3, 1);
                                                            }

                                                            return;
                                                    }
                                                }
                                            }
                                        )
                                )
                    )
            );
        Builder<Dynamic<T>, Dynamic<T>> builder = ImmutableMap.builder();
        builder.put(
            generatorOptionsDynamic.createString("structures"),
            generatorOptionsDynamic.createMap(
                map.entrySet()
                    .stream()
                    .collect(
                        Collectors.toMap(
                            entry -> generatorOptionsDynamic.createString(entry.getKey()), entry -> entry.getValue().serialize(worldGenSettingsDynamicOps)
                        )
                    )
            )
        );
        if (mutableBoolean.isTrue()) {
            builder.put(
                generatorOptionsDynamic.createString("stronghold"),
                generatorOptionsDynamic.createMap(
                    ImmutableMap.of(
                        generatorOptionsDynamic.createString("distance"),
                        generatorOptionsDynamic.createInt(mutableInt.getValue()),
                        generatorOptionsDynamic.createString("spread"),
                        generatorOptionsDynamic.createInt(mutableInt2.getValue()),
                        generatorOptionsDynamic.createString("count"),
                        generatorOptionsDynamic.createInt(mutableInt3.getValue())
                    )
                )
            );
        }

        return builder.build();
    }

    private static int getInt(String string, int defaultValue) {
        return NumberUtils.toInt(string, defaultValue);
    }

    private static int getInt(String string, int defaultValue, int minValue) {
        return Math.max(minValue, getInt(string, defaultValue));
    }

    private static void setSpacing(Map<String, WorldGenSettingsFix.StructureFeatureConfiguration> map, String structureId, String spacingStr, int minSpacing) {
        WorldGenSettingsFix.StructureFeatureConfiguration structureFeatureConfiguration = map.getOrDefault(structureId, DEFAULTS.get(structureId));
        int i = getInt(spacingStr, structureFeatureConfiguration.spacing, minSpacing);
        map.put(
            structureId, new WorldGenSettingsFix.StructureFeatureConfiguration(i, structureFeatureConfiguration.separation, structureFeatureConfiguration.salt)
        );
    }

    static final class StructureFeatureConfiguration {
        public static final Codec<WorldGenSettingsFix.StructureFeatureConfiguration> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Codec.INT.fieldOf("spacing").forGetter(structureFeatureConfiguration -> structureFeatureConfiguration.spacing),
                        Codec.INT.fieldOf("separation").forGetter(structureFeatureConfiguration -> structureFeatureConfiguration.separation),
                        Codec.INT.fieldOf("salt").forGetter(structureFeatureConfiguration -> structureFeatureConfiguration.salt)
                    )
                    .apply(instance, WorldGenSettingsFix.StructureFeatureConfiguration::new)
        );
        final int spacing;
        final int separation;
        final int salt;

        public StructureFeatureConfiguration(int spacing, int separation, int salt) {
            this.spacing = spacing;
            this.separation = separation;
            this.salt = salt;
        }

        public <T> Dynamic<T> serialize(DynamicOps<T> dynamicOps) {
            return new Dynamic<>(dynamicOps, CODEC.encodeStart(dynamicOps, this).result().orElse(dynamicOps.emptyMap()));
        }
    }
}
