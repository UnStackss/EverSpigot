package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.LongStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;

public class StructuresBecomeConfiguredFix extends DataFix {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, StructuresBecomeConfiguredFix.Conversion> CONVERSION_MAP = ImmutableMap.<String, StructuresBecomeConfiguredFix.Conversion>builder()
        .put(
            "mineshaft",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"), "minecraft:mineshaft_mesa"),
                "minecraft:mineshaft"
            )
        )
        .put(
            "shipwreck",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:beach", "minecraft:snowy_beach"), "minecraft:shipwreck_beached"), "minecraft:shipwreck"
            )
        )
        .put(
            "ocean_ruin",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(List.of("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"), "minecraft:ocean_ruin_warm"),
                "minecraft:ocean_ruin_cold"
            )
        )
        .put(
            "village",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(
                    List.of("minecraft:desert"),
                    "minecraft:village_desert",
                    List.of("minecraft:savanna"),
                    "minecraft:village_savanna",
                    List.of("minecraft:snowy_plains"),
                    "minecraft:village_snowy",
                    List.of("minecraft:taiga"),
                    "minecraft:village_taiga"
                ),
                "minecraft:village_plains"
            )
        )
        .put(
            "ruined_portal",
            StructuresBecomeConfiguredFix.Conversion.biomeMapped(
                Map.of(
                    List.of("minecraft:desert"),
                    "minecraft:ruined_portal_desert",
                    List.of(
                        "minecraft:badlands",
                        "minecraft:eroded_badlands",
                        "minecraft:wooded_badlands",
                        "minecraft:windswept_hills",
                        "minecraft:windswept_forest",
                        "minecraft:windswept_gravelly_hills",
                        "minecraft:savanna_plateau",
                        "minecraft:windswept_savanna",
                        "minecraft:stony_shore",
                        "minecraft:meadow",
                        "minecraft:frozen_peaks",
                        "minecraft:jagged_peaks",
                        "minecraft:stony_peaks",
                        "minecraft:snowy_slopes"
                    ),
                    "minecraft:ruined_portal_mountain",
                    List.of("minecraft:bamboo_jungle", "minecraft:jungle", "minecraft:sparse_jungle"),
                    "minecraft:ruined_portal_jungle",
                    List.of(
                        "minecraft:deep_frozen_ocean",
                        "minecraft:deep_cold_ocean",
                        "minecraft:deep_ocean",
                        "minecraft:deep_lukewarm_ocean",
                        "minecraft:frozen_ocean",
                        "minecraft:ocean",
                        "minecraft:cold_ocean",
                        "minecraft:lukewarm_ocean",
                        "minecraft:warm_ocean"
                    ),
                    "minecraft:ruined_portal_ocean"
                ),
                "minecraft:ruined_portal"
            )
        )
        .put("pillager_outpost", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:pillager_outpost"))
        .put("mansion", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:mansion"))
        .put("jungle_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:jungle_pyramid"))
        .put("desert_pyramid", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:desert_pyramid"))
        .put("igloo", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:igloo"))
        .put("swamp_hut", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:swamp_hut"))
        .put("stronghold", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:stronghold"))
        .put("monument", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:monument"))
        .put("fortress", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:fortress"))
        .put("endcity", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:end_city"))
        .put("buried_treasure", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:buried_treasure"))
        .put("nether_fossil", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:nether_fossil"))
        .put("bastion_remnant", StructuresBecomeConfiguredFix.Conversion.trivial("minecraft:bastion_remnant"))
        .build();

    public StructuresBecomeConfiguredFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type2 = this.getInputSchema().getType(References.CHUNK);
        return this.writeFixAndRead("StucturesToConfiguredStructures", type, type2, this::fix);
    }

    private Dynamic<?> fix(Dynamic<?> chunkDynamic) {
        return chunkDynamic.update(
            "structures",
            structuresDynamic -> structuresDynamic.update("starts", startsDynamic -> this.updateStarts(startsDynamic, chunkDynamic))
                    .update("References", referencesDynamic -> this.updateReferences(referencesDynamic, chunkDynamic))
        );
    }

    private Dynamic<?> updateStarts(Dynamic<?> startsDynamic, Dynamic<?> chunkDynamic) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = startsDynamic.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashMap = Maps.newHashMap();
        map.forEach((structureId, startDynamic) -> {
            if (!startDynamic.get("id").asString("INVALID").equals("INVALID")) {
                Dynamic<?> dynamic2 = this.findUpdatedStructureType((Dynamic<?>)structureId, chunkDynamic);
                if (dynamic2 == null) {
                    LOGGER.warn("Encountered unknown structure in datafixer: " + structureId.asString("<missing key>"));
                } else {
                    hashMap.computeIfAbsent(dynamic2, configuredStructureId -> startDynamic.set("id", dynamic2));
                }
            }
        });
        return chunkDynamic.createMap(hashMap);
    }

    private Dynamic<?> updateReferences(Dynamic<?> referencesDynamic, Dynamic<?> chunkDynamic) {
        Map<? extends Dynamic<?>, ? extends Dynamic<?>> map = referencesDynamic.getMapValues().result().orElse(Map.of());
        HashMap<Dynamic<?>, Dynamic<?>> hashMap = Maps.newHashMap();
        map.forEach(
            (structureId, referenceDynamic) -> {
                if (referenceDynamic.asLongStream().count() != 0L) {
                    Dynamic<?> dynamic2 = this.findUpdatedStructureType((Dynamic<?>)structureId, chunkDynamic);
                    if (dynamic2 == null) {
                        LOGGER.warn("Encountered unknown structure in datafixer: " + structureId.asString("<missing key>"));
                    } else {
                        hashMap.compute(
                            dynamic2,
                            (configuredStructureId, referenceDynamicx) -> referenceDynamicx == null
                                    ? referenceDynamic
                                    : referenceDynamic.createLongList(LongStream.concat(referenceDynamicx.asLongStream(), referenceDynamic.asLongStream()))
                        );
                    }
                }
            }
        );
        return chunkDynamic.createMap(hashMap);
    }

    @Nullable
    private Dynamic<?> findUpdatedStructureType(Dynamic<?> structureIdDynamic, Dynamic<?> chunkDynamic) {
        String string = structureIdDynamic.asString("UNKNOWN").toLowerCase(Locale.ROOT);
        StructuresBecomeConfiguredFix.Conversion conversion = CONVERSION_MAP.get(string);
        if (conversion == null) {
            return null;
        } else {
            String string2 = conversion.fallback;
            if (!conversion.biomeMapping().isEmpty()) {
                Optional<String> optional = this.guessConfiguration(chunkDynamic, conversion);
                if (optional.isPresent()) {
                    string2 = optional.get();
                }
            }

            return chunkDynamic.createString(string2);
        }
    }

    private Optional<String> guessConfiguration(Dynamic<?> chunkDynamic, StructuresBecomeConfiguredFix.Conversion mappingForStructure) {
        Object2IntArrayMap<String> object2IntArrayMap = new Object2IntArrayMap<>();
        chunkDynamic.get("sections")
            .asList(Function.identity())
            .forEach(sectionDynamic -> sectionDynamic.get("biomes").get("palette").asList(Function.identity()).forEach(biomePaletteDynamic -> {
                    String string = mappingForStructure.biomeMapping().get(biomePaletteDynamic.asString(""));
                    if (string != null) {
                        object2IntArrayMap.mergeInt(string, 1, Integer::sum);
                    }
                }));
        return object2IntArrayMap.object2IntEntrySet()
            .stream()
            .max(Comparator.comparingInt(it.unimi.dsi.fastutil.objects.Object2IntMap.Entry::getIntValue))
            .map(Entry::getKey);
    }

    static record Conversion(Map<String, String> biomeMapping, String fallback) {
        public static StructuresBecomeConfiguredFix.Conversion trivial(String mapping) {
            return new StructuresBecomeConfiguredFix.Conversion(Map.of(), mapping);
        }

        public static StructuresBecomeConfiguredFix.Conversion biomeMapped(Map<List<String>, String> biomeMapping, String fallback) {
            return new StructuresBecomeConfiguredFix.Conversion(unpack(biomeMapping), fallback);
        }

        private static Map<String, String> unpack(Map<List<String>, String> biomeMapping) {
            Builder<String, String> builder = ImmutableMap.builder();

            for (Entry<List<String>, String> entry : biomeMapping.entrySet()) {
                entry.getKey().forEach(key -> builder.put(key, entry.getValue()));
            }

            return builder.build();
        }
    }
}
