package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;

public class ChunkHeightAndBiomeFix extends DataFix {
    public static final String DATAFIXER_CONTEXT_TAG = "__context";
    private static final String NAME = "ChunkHeightAndBiomeFix";
    private static final int OLD_SECTION_COUNT = 16;
    private static final int NEW_SECTION_COUNT = 24;
    private static final int NEW_MIN_SECTION_Y = -4;
    public static final int BLOCKS_PER_SECTION = 4096;
    private static final int LONGS_PER_SECTION = 64;
    private static final int HEIGHTMAP_BITS = 9;
    private static final long HEIGHTMAP_MASK = 511L;
    private static final int HEIGHTMAP_OFFSET = 64;
    private static final String[] HEIGHTMAP_TYPES = new String[]{
        "WORLD_SURFACE_WG", "WORLD_SURFACE", "WORLD_SURFACE_IGNORE_SNOW", "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"
    };
    private static final Set<String> STATUS_IS_OR_AFTER_SURFACE = Set.of(
        "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> STATUS_IS_OR_AFTER_NOISE = Set.of(
        "noise", "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> BLOCKS_BEFORE_FEATURE_STATUS = Set.of(
        "minecraft:air",
        "minecraft:basalt",
        "minecraft:bedrock",
        "minecraft:blackstone",
        "minecraft:calcite",
        "minecraft:cave_air",
        "minecraft:coarse_dirt",
        "minecraft:crimson_nylium",
        "minecraft:dirt",
        "minecraft:end_stone",
        "minecraft:grass_block",
        "minecraft:gravel",
        "minecraft:ice",
        "minecraft:lava",
        "minecraft:mycelium",
        "minecraft:nether_wart_block",
        "minecraft:netherrack",
        "minecraft:orange_terracotta",
        "minecraft:packed_ice",
        "minecraft:podzol",
        "minecraft:powder_snow",
        "minecraft:red_sand",
        "minecraft:red_sandstone",
        "minecraft:sand",
        "minecraft:sandstone",
        "minecraft:snow_block",
        "minecraft:soul_sand",
        "minecraft:soul_soil",
        "minecraft:stone",
        "minecraft:terracotta",
        "minecraft:warped_nylium",
        "minecraft:warped_wart_block",
        "minecraft:water",
        "minecraft:white_terracotta"
    );
    private static final int BIOME_CONTAINER_LAYER_SIZE = 16;
    private static final int BIOME_CONTAINER_SIZE = 64;
    private static final int BIOME_CONTAINER_TOP_LAYER_OFFSET = 1008;
    public static final String DEFAULT_BIOME = "minecraft:plains";
    private static final Int2ObjectMap<String> BIOMES_BY_ID = new Int2ObjectOpenHashMap<>();

    public ChunkHeightAndBiomeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        Schema schema = this.getOutputSchema();
        Type<?> type2 = schema.getType(References.CHUNK);
        Type<?> type3 = type2.findField("Level").type();
        Type<?> type4 = type3.findField("Sections").type();
        return this.fixTypeEverywhereTyped(
            "ChunkHeightAndBiomeFix",
            type,
            type2,
            chunk -> chunk.updateTyped(
                    opticFinder,
                    type3,
                    level -> {
                        Dynamic<?> dynamic = level.get(DSL.remainderFinder());
                        OptionalDynamic<?> optionalDynamic = chunk.get(DSL.remainderFinder()).get("__context");
                        String string = optionalDynamic.get("dimension").asString().result().orElse("");
                        String string2 = optionalDynamic.get("generator").asString().result().orElse("");
                        boolean bl = "minecraft:overworld".equals(string);
                        MutableBoolean mutableBoolean = new MutableBoolean();
                        int i = bl ? -4 : 0;
                        Dynamic<?>[] dynamics = getBiomeContainers(dynamic, bl, i, mutableBoolean);
                        Dynamic<?> dynamic2 = makePalettedContainer(
                            dynamic.createList(
                                Stream.of(dynamic.createMap(ImmutableMap.of(dynamic.createString("Name"), dynamic.createString("minecraft:air"))))
                            )
                        );
                        Set<String> set = Sets.newHashSet();
                        MutableObject<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> mutableObject = new MutableObject<>(() -> null);
                        level = level.updateTyped(
                            opticFinder2,
                            type4,
                            sections -> {
                                IntSet intSet = new IntOpenHashSet();
                                Dynamic<?> dynamic3 = (Dynamic<?>)sections.write()
                                    .result()
                                    .orElseThrow(() -> new IllegalStateException("Malformed Chunk.Level.Sections"));
                                List<Dynamic<?>> list = dynamic3.asStream().map(dynamic2xx -> {
                                    int jx = dynamic2xx.get("Y").asInt(0);
                                    Dynamic<?> dynamic3x = DataFixUtils.orElse(dynamic2xx.get("Palette").result().flatMap(dynamic2xxx -> {
                                        dynamic2xxx.asStream().map(dynamicxxxx -> dynamicxxxx.get("Name").asString("minecraft:air")).forEach(set::add);
                                        return dynamic2xx.get("BlockStates")
                                            .result()
                                            .map(dynamic2xxxx -> makeOptimizedPalettedContainer(dynamic2xxx, dynamic2xxxx));
                                    }), dynamic2);
                                    Dynamic<?> dynamic4x = dynamic2xx;
                                    int kx = jx - i;
                                    if (kx >= 0 && kx < dynamics.length) {
                                        dynamic4x = dynamic2xx.set("biomes", dynamics[kx]);
                                    }

                                    intSet.add(jx);
                                    if (dynamic2xx.get("Y").asInt(Integer.MAX_VALUE) == 0) {
                                        mutableObject.setValue(() -> {
                                            List<? extends Dynamic<?>> listx = dynamic3x.get("palette").asList(Function.identity());
                                            long[] ls = dynamic3x.get("data").asLongStream().toArray();
                                            return new ChunkProtoTickListFix.PoorMansPalettedContainer(listx, ls);
                                        });
                                    }

                                    return dynamic4x.set("block_states", dynamic3x).remove("Palette").remove("BlockStates");
                                }).collect(Collectors.toCollection(ArrayList::new));

                                for (int j = 0; j < dynamics.length; j++) {
                                    int k = j + i;
                                    if (intSet.add(k)) {
                                        Dynamic<?> dynamic4 = dynamic.createMap(Map.of(dynamic.createString("Y"), dynamic.createInt(k)));
                                        dynamic4 = dynamic4.set("block_states", dynamic2);
                                        dynamic4 = dynamic4.set("biomes", dynamics[j]);
                                        list.add(dynamic4);
                                    }
                                }

                                return Util.readTypedOrThrow(type4, dynamic.createList(list.stream()));
                            }
                        );
                        return level.update(DSL.remainderFinder(), level2 -> {
                            if (bl) {
                                level2 = this.predictChunkStatusBeforeSurface(level2, set);
                            }

                            return updateChunkTag(level2, bl, mutableBoolean.booleanValue(), "minecraft:noise".equals(string2), mutableObject.getValue());
                        });
                    }
                )
        );
    }

    private Dynamic<?> predictChunkStatusBeforeSurface(Dynamic<?> level, Set<String> blocks) {
        return level.update("Status", status -> {
            String string = status.asString("empty");
            if (STATUS_IS_OR_AFTER_SURFACE.contains(string)) {
                return status;
            } else {
                blocks.remove("minecraft:air");
                boolean bl = !blocks.isEmpty();
                blocks.removeAll(BLOCKS_BEFORE_FEATURE_STATUS);
                boolean bl2 = !blocks.isEmpty();
                if (bl2) {
                    return status.createString("liquid_carvers");
                } else if ("noise".equals(string) || bl) {
                    return status.createString("noise");
                } else {
                    return "biomes".equals(string) ? status.createString("structure_references") : status;
                }
            }
        });
    }

    private static Dynamic<?>[] getBiomeContainers(Dynamic<?> level, boolean overworld, int i, MutableBoolean heightAlreadyUpdated) {
        Dynamic<?>[] dynamics = new Dynamic[overworld ? 24 : 16];
        int[] is = level.get("Biomes").asIntStreamOpt().result().map(IntStream::toArray).orElse(null);
        if (is != null && is.length == 1536) {
            heightAlreadyUpdated.setValue(true);

            for (int j = 0; j < 24; j++) {
                int k = j;
                dynamics[j] = makeBiomeContainer(level, sectionY -> getOldBiome(is, k * 64 + sectionY));
            }
        } else if (is != null && is.length == 1024) {
            for (int l = 0; l < 16; l++) {
                int m = l - i;
                dynamics[m] = makeBiomeContainer(level, sectionY -> getOldBiome(is, l * 64 + sectionY));
            }

            if (overworld) {
                Dynamic<?> dynamic = makeBiomeContainer(level, sectionY -> getOldBiome(is, sectionY % 16));
                Dynamic<?> dynamic2 = makeBiomeContainer(level, sectionY -> getOldBiome(is, sectionY % 16 + 1008));

                for (int o = 0; o < 4; o++) {
                    dynamics[o] = dynamic;
                }

                for (int p = 20; p < 24; p++) {
                    dynamics[p] = dynamic2;
                }
            }
        } else {
            Arrays.fill(dynamics, makePalettedContainer(level.createList(Stream.of(level.createString("minecraft:plains")))));
        }

        return dynamics;
    }

    private static int getOldBiome(int[] is, int index) {
        return is[index] & 0xFF;
    }

    private static Dynamic<?> updateChunkTag(
        Dynamic<?> level,
        boolean overworld,
        boolean heightAlreadyUpdated,
        boolean atNoiseStatus,
        Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> supplier
    ) {
        level = level.remove("Biomes");
        if (!overworld) {
            return updateCarvingMasks(level, 16, 0);
        } else if (heightAlreadyUpdated) {
            return updateCarvingMasks(level, 24, 0);
        } else {
            level = updateHeightmaps(level);
            level = addPaddingEntries(level, "LiquidsToBeTicked");
            level = addPaddingEntries(level, "PostProcessing");
            level = addPaddingEntries(level, "ToBeTicked");
            level = updateCarvingMasks(level, 24, 4);
            level = level.update("UpgradeData", ChunkHeightAndBiomeFix::shiftUpgradeData);
            if (!atNoiseStatus) {
                return level;
            } else {
                Optional<? extends Dynamic<?>> optional = level.get("Status").result();
                if (optional.isPresent()) {
                    Dynamic<?> dynamic = (Dynamic<?>)optional.get();
                    String string = dynamic.asString("");
                    if (!"empty".equals(string)) {
                        level = level.set(
                            "blending_data",
                            level.createMap(ImmutableMap.of(level.createString("old_noise"), level.createBoolean(STATUS_IS_OR_AFTER_NOISE.contains(string))))
                        );
                        ChunkProtoTickListFix.PoorMansPalettedContainer poorMansPalettedContainer = supplier.get();
                        if (poorMansPalettedContainer != null) {
                            BitSet bitSet = new BitSet(256);
                            boolean bl = string.equals("noise");

                            for (int i = 0; i < 16; i++) {
                                for (int j = 0; j < 16; j++) {
                                    Dynamic<?> dynamic2 = poorMansPalettedContainer.get(j, 0, i);
                                    boolean bl2 = dynamic2 != null && "minecraft:bedrock".equals(dynamic2.get("Name").asString(""));
                                    boolean bl3 = dynamic2 != null && "minecraft:air".equals(dynamic2.get("Name").asString(""));
                                    if (bl3) {
                                        bitSet.set(i * 16 + j);
                                    }

                                    bl |= bl2;
                                }
                            }

                            if (bl && bitSet.cardinality() != bitSet.size()) {
                                Dynamic<?> dynamic3 = "full".equals(string) ? level.createString("heightmaps") : dynamic;
                                level = level.set(
                                    "below_zero_retrogen",
                                    level.createMap(
                                        ImmutableMap.of(
                                            level.createString("target_status"),
                                            dynamic3,
                                            level.createString("missing_bedrock"),
                                            level.createLongList(LongStream.of(bitSet.toLongArray()))
                                        )
                                    )
                                );
                                level = level.set("Status", level.createString("empty"));
                            }

                            level = level.set("isLightOn", level.createBoolean(false));
                        }
                    }
                }

                return level;
            }
        }
    }

    private static <T> Dynamic<T> shiftUpgradeData(Dynamic<T> upgradeData) {
        return upgradeData.update("Indices", indices -> {
            Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
            indices.getMapValues().ifSuccess(indicesMap -> indicesMap.forEach((key, value) -> {
                    try {
                        key.asString().result().map(Integer::parseInt).ifPresent(index -> {
                            int i = index - -4;
                            map.put(key.createString(Integer.toString(i)), (Dynamic<?>)value);
                        });
                    } catch (NumberFormatException var4) {
                    }
                }));
            return indices.createMap(map);
        });
    }

    private static Dynamic<?> updateCarvingMasks(Dynamic<?> level, int sectionsPerChunk, int oldBottomSectionY) {
        Dynamic<?> dynamic = level.get("CarvingMasks").orElseEmptyMap();
        dynamic = dynamic.updateMapValues(mask -> {
            long[] ls = BitSet.valueOf(mask.getSecond().asByteBuffer().array()).toLongArray();
            long[] ms = new long[64 * sectionsPerChunk];
            System.arraycopy(ls, 0, ms, 64 * oldBottomSectionY, ls.length);
            return Pair.of(mask.getFirst(), level.createLongList(LongStream.of(ms)));
        });
        return level.set("CarvingMasks", dynamic);
    }

    private static Dynamic<?> addPaddingEntries(Dynamic<?> level, String key) {
        List<Dynamic<?>> list = level.get(key).orElseEmptyList().asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 24) {
            return level;
        } else {
            Dynamic<?> dynamic = level.emptyList();

            for (int i = 0; i < 4; i++) {
                list.add(0, dynamic);
                list.add(dynamic);
            }

            return level.set(key, level.createList(list.stream()));
        }
    }

    private static Dynamic<?> updateHeightmaps(Dynamic<?> level) {
        return level.update("Heightmaps", heightmaps -> {
            for (String string : HEIGHTMAP_TYPES) {
                heightmaps = heightmaps.update(string, ChunkHeightAndBiomeFix::getFixedHeightmap);
            }

            return heightmaps;
        });
    }

    private static Dynamic<?> getFixedHeightmap(Dynamic<?> heightmap) {
        return heightmap.createLongList(heightmap.asLongStream().map(entry -> {
            long l = 0L;

            for (int i = 0; i + 9 <= 64; i += 9) {
                long m = entry >> i & 511L;
                long n;
                if (m == 0L) {
                    n = 0L;
                } else {
                    n = Math.min(m + 64L, 511L);
                }

                l |= n << i;
            }

            return l;
        }));
    }

    private static Dynamic<?> makeBiomeContainer(Dynamic<?> level, Int2IntFunction biomeGetter) {
        Int2IntMap int2IntMap = new Int2IntLinkedOpenHashMap();

        for (int i = 0; i < 64; i++) {
            int j = biomeGetter.applyAsInt(i);
            if (!int2IntMap.containsKey(j)) {
                int2IntMap.put(j, int2IntMap.size());
            }
        }

        Dynamic<?> dynamic = level.createList(
            int2IntMap.keySet().stream().map(rawBiomeId -> level.createString(BIOMES_BY_ID.getOrDefault(rawBiomeId.intValue(), "minecraft:plains")))
        );
        int k = ceillog2(int2IntMap.size());
        if (k == 0) {
            return makePalettedContainer(dynamic);
        } else {
            int l = 64 / k;
            int m = (64 + l - 1) / l;
            long[] ls = new long[m];
            int n = 0;
            int o = 0;

            for (int p = 0; p < 64; p++) {
                int q = biomeGetter.applyAsInt(p);
                ls[n] |= (long)int2IntMap.get(q) << o;
                o += k;
                if (o + k > 64) {
                    n++;
                    o = 0;
                }
            }

            Dynamic<?> dynamic2 = level.createLongList(Arrays.stream(ls));
            return makePalettedContainer(dynamic, dynamic2);
        }
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette));
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette, Dynamic<?> data) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette, palette.createString("data"), data));
    }

    private static Dynamic<?> makeOptimizedPalettedContainer(Dynamic<?> dynamic, Dynamic<?> dynamic2) {
        List<Dynamic<?>> list = dynamic.asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 1) {
            return makePalettedContainer(dynamic);
        } else {
            dynamic = padPaletteEntries(dynamic, dynamic2, list);
            return makePalettedContainer(dynamic, dynamic2);
        }
    }

    private static Dynamic<?> padPaletteEntries(Dynamic<?> dynamic, Dynamic<?> dynamic2, List<Dynamic<?>> list) {
        long l = dynamic2.asLongStream().count() * 64L;
        long m = l / 4096L;
        int i = list.size();
        int j = ceillog2(i);
        if (m <= (long)j) {
            return dynamic;
        } else {
            Dynamic<?> dynamic3 = dynamic.createMap(ImmutableMap.of(dynamic.createString("Name"), dynamic.createString("minecraft:air")));
            int k = (1 << (int)(m - 1L)) + 1;
            int n = k - i;

            for (int o = 0; o < n; o++) {
                list.add(dynamic3);
            }

            return dynamic.createList(list.stream());
        }
    }

    public static int ceillog2(int value) {
        return value == 0 ? 0 : (int)Math.ceil(Math.log((double)value) / Math.log(2.0));
    }

    static {
        BIOMES_BY_ID.put(0, "minecraft:ocean");
        BIOMES_BY_ID.put(1, "minecraft:plains");
        BIOMES_BY_ID.put(2, "minecraft:desert");
        BIOMES_BY_ID.put(3, "minecraft:mountains");
        BIOMES_BY_ID.put(4, "minecraft:forest");
        BIOMES_BY_ID.put(5, "minecraft:taiga");
        BIOMES_BY_ID.put(6, "minecraft:swamp");
        BIOMES_BY_ID.put(7, "minecraft:river");
        BIOMES_BY_ID.put(8, "minecraft:nether_wastes");
        BIOMES_BY_ID.put(9, "minecraft:the_end");
        BIOMES_BY_ID.put(10, "minecraft:frozen_ocean");
        BIOMES_BY_ID.put(11, "minecraft:frozen_river");
        BIOMES_BY_ID.put(12, "minecraft:snowy_tundra");
        BIOMES_BY_ID.put(13, "minecraft:snowy_mountains");
        BIOMES_BY_ID.put(14, "minecraft:mushroom_fields");
        BIOMES_BY_ID.put(15, "minecraft:mushroom_field_shore");
        BIOMES_BY_ID.put(16, "minecraft:beach");
        BIOMES_BY_ID.put(17, "minecraft:desert_hills");
        BIOMES_BY_ID.put(18, "minecraft:wooded_hills");
        BIOMES_BY_ID.put(19, "minecraft:taiga_hills");
        BIOMES_BY_ID.put(20, "minecraft:mountain_edge");
        BIOMES_BY_ID.put(21, "minecraft:jungle");
        BIOMES_BY_ID.put(22, "minecraft:jungle_hills");
        BIOMES_BY_ID.put(23, "minecraft:jungle_edge");
        BIOMES_BY_ID.put(24, "minecraft:deep_ocean");
        BIOMES_BY_ID.put(25, "minecraft:stone_shore");
        BIOMES_BY_ID.put(26, "minecraft:snowy_beach");
        BIOMES_BY_ID.put(27, "minecraft:birch_forest");
        BIOMES_BY_ID.put(28, "minecraft:birch_forest_hills");
        BIOMES_BY_ID.put(29, "minecraft:dark_forest");
        BIOMES_BY_ID.put(30, "minecraft:snowy_taiga");
        BIOMES_BY_ID.put(31, "minecraft:snowy_taiga_hills");
        BIOMES_BY_ID.put(32, "minecraft:giant_tree_taiga");
        BIOMES_BY_ID.put(33, "minecraft:giant_tree_taiga_hills");
        BIOMES_BY_ID.put(34, "minecraft:wooded_mountains");
        BIOMES_BY_ID.put(35, "minecraft:savanna");
        BIOMES_BY_ID.put(36, "minecraft:savanna_plateau");
        BIOMES_BY_ID.put(37, "minecraft:badlands");
        BIOMES_BY_ID.put(38, "minecraft:wooded_badlands_plateau");
        BIOMES_BY_ID.put(39, "minecraft:badlands_plateau");
        BIOMES_BY_ID.put(40, "minecraft:small_end_islands");
        BIOMES_BY_ID.put(41, "minecraft:end_midlands");
        BIOMES_BY_ID.put(42, "minecraft:end_highlands");
        BIOMES_BY_ID.put(43, "minecraft:end_barrens");
        BIOMES_BY_ID.put(44, "minecraft:warm_ocean");
        BIOMES_BY_ID.put(45, "minecraft:lukewarm_ocean");
        BIOMES_BY_ID.put(46, "minecraft:cold_ocean");
        BIOMES_BY_ID.put(47, "minecraft:deep_warm_ocean");
        BIOMES_BY_ID.put(48, "minecraft:deep_lukewarm_ocean");
        BIOMES_BY_ID.put(49, "minecraft:deep_cold_ocean");
        BIOMES_BY_ID.put(50, "minecraft:deep_frozen_ocean");
        BIOMES_BY_ID.put(127, "minecraft:the_void");
        BIOMES_BY_ID.put(129, "minecraft:sunflower_plains");
        BIOMES_BY_ID.put(130, "minecraft:desert_lakes");
        BIOMES_BY_ID.put(131, "minecraft:gravelly_mountains");
        BIOMES_BY_ID.put(132, "minecraft:flower_forest");
        BIOMES_BY_ID.put(133, "minecraft:taiga_mountains");
        BIOMES_BY_ID.put(134, "minecraft:swamp_hills");
        BIOMES_BY_ID.put(140, "minecraft:ice_spikes");
        BIOMES_BY_ID.put(149, "minecraft:modified_jungle");
        BIOMES_BY_ID.put(151, "minecraft:modified_jungle_edge");
        BIOMES_BY_ID.put(155, "minecraft:tall_birch_forest");
        BIOMES_BY_ID.put(156, "minecraft:tall_birch_hills");
        BIOMES_BY_ID.put(157, "minecraft:dark_forest_hills");
        BIOMES_BY_ID.put(158, "minecraft:snowy_taiga_mountains");
        BIOMES_BY_ID.put(160, "minecraft:giant_spruce_taiga");
        BIOMES_BY_ID.put(161, "minecraft:giant_spruce_taiga_hills");
        BIOMES_BY_ID.put(162, "minecraft:modified_gravelly_mountains");
        BIOMES_BY_ID.put(163, "minecraft:shattered_savanna");
        BIOMES_BY_ID.put(164, "minecraft:shattered_savanna_plateau");
        BIOMES_BY_ID.put(165, "minecraft:eroded_badlands");
        BIOMES_BY_ID.put(166, "minecraft:modified_wooded_badlands_plateau");
        BIOMES_BY_ID.put(167, "minecraft:modified_badlands_plateau");
        BIOMES_BY_ID.put(168, "minecraft:bamboo_jungle");
        BIOMES_BY_ID.put(169, "minecraft:bamboo_jungle_hills");
        BIOMES_BY_ID.put(170, "minecraft:soul_sand_valley");
        BIOMES_BY_ID.put(171, "minecraft:crimson_forest");
        BIOMES_BY_ID.put(172, "minecraft:warped_forest");
        BIOMES_BY_ID.put(173, "minecraft:basalt_deltas");
        BIOMES_BY_ID.put(174, "minecraft:dripstone_caves");
        BIOMES_BY_ID.put(175, "minecraft:lush_caves");
        BIOMES_BY_ID.put(177, "minecraft:meadow");
        BIOMES_BY_ID.put(178, "minecraft:grove");
        BIOMES_BY_ID.put(179, "minecraft:snowy_slopes");
        BIOMES_BY_ID.put(180, "minecraft:snowcapped_peaks");
        BIOMES_BY_ID.put(181, "minecraft:lofty_peaks");
        BIOMES_BY_ID.put(182, "minecraft:stony_peaks");
    }
}
