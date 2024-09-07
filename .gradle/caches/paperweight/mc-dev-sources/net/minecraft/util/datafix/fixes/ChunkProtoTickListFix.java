package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.types.templates.List.ListType;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.apache.commons.lang3.mutable.MutableInt;

public class ChunkProtoTickListFix extends DataFix {
    private static final int SECTION_WIDTH = 16;
    private static final ImmutableSet<String> ALWAYS_WATERLOGGED = ImmutableSet.of(
        "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass"
    );

    public ChunkProtoTickListFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        OpticFinder<?> opticFinder3 = ((ListType)opticFinder2.type()).getElement().finder();
        OpticFinder<?> opticFinder4 = opticFinder3.type().findField("block_states");
        OpticFinder<?> opticFinder5 = opticFinder3.type().findField("biomes");
        OpticFinder<?> opticFinder6 = opticFinder4.type().findField("palette");
        OpticFinder<?> opticFinder7 = opticFinder.type().findField("TileTicks");
        return this.fixTypeEverywhereTyped(
            "ChunkProtoTickListFix",
            type,
            chunkTyped -> chunkTyped.updateTyped(
                    opticFinder,
                    levelTyped -> {
                        levelTyped = levelTyped.update(
                            DSL.remainderFinder(),
                            levelDynamic -> DataFixUtils.orElse(
                                    levelDynamic.get("LiquidTicks")
                                        .result()
                                        .map(liquidTicksDynamic -> levelDynamic.set("fluid_ticks", (Dynamic<?>)liquidTicksDynamic).remove("LiquidTicks")),
                                    levelDynamic
                                )
                        );
                        Dynamic<?> dynamic = levelTyped.get(DSL.remainderFinder());
                        MutableInt mutableInt = new MutableInt();
                        Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> int2ObjectMap = new Int2ObjectArrayMap<>();
                        levelTyped.getOptionalTyped(opticFinder2)
                            .ifPresent(
                                sectionsTyped -> sectionsTyped.getAllTyped(opticFinder3)
                                        .forEach(
                                            sectionTyped -> {
                                                Dynamic<?> dynamicx = sectionTyped.get(DSL.remainderFinder());
                                                int ix = dynamicx.get("Y").asInt(Integer.MAX_VALUE);
                                                if (ix != Integer.MAX_VALUE) {
                                                    if (sectionTyped.getOptionalTyped(opticFinder5).isPresent()) {
                                                        mutableInt.setValue(Math.min(ix, mutableInt.getValue()));
                                                    }

                                                    sectionTyped.getOptionalTyped(opticFinder4)
                                                        .ifPresent(
                                                            blockStatesTyped -> int2ObjectMap.put(
                                                                    ix,
                                                                    Suppliers.memoize(
                                                                        () -> {
                                                                            List<? extends Dynamic<?>> list = blockStatesTyped.getOptionalTyped(opticFinder6)
                                                                                .map(
                                                                                    paletteTyped -> paletteTyped.write()
                                                                                            .result()
                                                                                            .map(paletteDynamic -> paletteDynamic.asList(Function.identity()))
                                                                                            .orElse(Collections.emptyList())
                                                                                )
                                                                                .orElse(Collections.emptyList());
                                                                            long[] ls = blockStatesTyped.get(DSL.remainderFinder())
                                                                                .get("data")
                                                                                .asLongStream()
                                                                                .toArray();
                                                                            return new ChunkProtoTickListFix.PoorMansPalettedContainer(list, ls);
                                                                        }
                                                                    )
                                                                )
                                                        );
                                                }
                                            }
                                        )
                            );
                        byte b = mutableInt.getValue().byteValue();
                        levelTyped = levelTyped.update(DSL.remainderFinder(), levelDynamic -> levelDynamic.update("yPos", yDynamic -> yDynamic.createByte(b)));
                        if (!levelTyped.getOptionalTyped(opticFinder7).isPresent() && !dynamic.get("fluid_ticks").result().isPresent()) {
                            int i = dynamic.get("xPos").asInt(0);
                            int j = dynamic.get("zPos").asInt(0);
                            Dynamic<?> dynamic2 = this.makeTickList(dynamic, int2ObjectMap, b, i, j, "LiquidsToBeTicked", ChunkProtoTickListFix::getLiquid);
                            Dynamic<?> dynamic3 = this.makeTickList(dynamic, int2ObjectMap, b, i, j, "ToBeTicked", ChunkProtoTickListFix::getBlock);
                            Optional<? extends Pair<? extends Typed<?>, ?>> optional = opticFinder7.type().readTyped(dynamic3).result();
                            if (optional.isPresent()) {
                                levelTyped = levelTyped.set(opticFinder7, (Typed<?>)optional.get().getFirst());
                            }

                            return levelTyped.update(
                                DSL.remainderFinder(),
                                levelDynamic -> levelDynamic.remove("ToBeTicked").remove("LiquidsToBeTicked").set("fluid_ticks", dynamic2)
                            );
                        } else {
                            return levelTyped;
                        }
                    }
                )
        );
    }

    private Dynamic<?> makeTickList(
        Dynamic<?> levelDynamic,
        Int2ObjectMap<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> palettedSectionsByY,
        byte sectionY,
        int localX,
        int localZ,
        String key,
        Function<Dynamic<?>, String> blockIdGetter
    ) {
        Stream<Dynamic<?>> stream = Stream.empty();
        List<? extends Dynamic<?>> list = levelDynamic.get(key).asList(Function.identity());

        for (int i = 0; i < list.size(); i++) {
            int j = i + sectionY;
            Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> supplier = palettedSectionsByY.get(j);
            Stream<? extends Dynamic<?>> stream2 = list.get(i)
                .asStream()
                .mapToInt(posDynamic -> posDynamic.asShort((short)-1))
                .filter(packedLocalPos -> packedLocalPos > 0)
                .mapToObj(packedLocalPos -> this.createTick(levelDynamic, supplier, localX, j, localZ, packedLocalPos, blockIdGetter));
            stream = Stream.concat(stream, stream2);
        }

        return levelDynamic.createList(stream);
    }

    private static String getBlock(@Nullable Dynamic<?> blockStateDynamic) {
        return blockStateDynamic != null ? blockStateDynamic.get("Name").asString("minecraft:air") : "minecraft:air";
    }

    private static String getLiquid(@Nullable Dynamic<?> blockStateDynamic) {
        if (blockStateDynamic == null) {
            return "minecraft:empty";
        } else {
            String string = blockStateDynamic.get("Name").asString("");
            if ("minecraft:water".equals(string)) {
                return blockStateDynamic.get("Properties").get("level").asInt(0) == 0 ? "minecraft:water" : "minecraft:flowing_water";
            } else if ("minecraft:lava".equals(string)) {
                return blockStateDynamic.get("Properties").get("level").asInt(0) == 0 ? "minecraft:lava" : "minecraft:flowing_lava";
            } else {
                return !ALWAYS_WATERLOGGED.contains(string) && !blockStateDynamic.get("Properties").get("waterlogged").asBoolean(false)
                    ? "minecraft:empty"
                    : "minecraft:water";
            }
        }
    }

    private Dynamic<?> createTick(
        Dynamic<?> levelDynamic,
        @Nullable Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> sectionSupplier,
        int sectionX,
        int sectionY,
        int sectionZ,
        int packedLocalPos,
        Function<Dynamic<?>, String> blockIdGetter
    ) {
        int i = packedLocalPos & 15;
        int j = packedLocalPos >>> 4 & 15;
        int k = packedLocalPos >>> 8 & 15;
        String string = blockIdGetter.apply(sectionSupplier != null ? sectionSupplier.get().get(i, j, k) : null);
        return levelDynamic.createMap(
            ImmutableMap.builder()
                .put(levelDynamic.createString("i"), levelDynamic.createString(string))
                .put(levelDynamic.createString("x"), levelDynamic.createInt(sectionX * 16 + i))
                .put(levelDynamic.createString("y"), levelDynamic.createInt(sectionY * 16 + j))
                .put(levelDynamic.createString("z"), levelDynamic.createInt(sectionZ * 16 + k))
                .put(levelDynamic.createString("t"), levelDynamic.createInt(0))
                .put(levelDynamic.createString("p"), levelDynamic.createInt(0))
                .build()
        );
    }

    public static final class PoorMansPalettedContainer {
        private static final long SIZE_BITS = 4L;
        private final List<? extends Dynamic<?>> palette;
        private final long[] data;
        private final int bits;
        private final long mask;
        private final int valuesPerLong;

        public PoorMansPalettedContainer(List<? extends Dynamic<?>> palette, long[] data) {
            this.palette = palette;
            this.data = data;
            this.bits = Math.max(4, ChunkHeightAndBiomeFix.ceillog2(palette.size()));
            this.mask = (1L << this.bits) - 1L;
            this.valuesPerLong = (char)(64 / this.bits);
        }

        @Nullable
        public Dynamic<?> get(int localX, int localY, int localZ) {
            int i = this.palette.size();
            if (i < 1) {
                return null;
            } else if (i == 1) {
                return (Dynamic<?>)this.palette.get(0);
            } else {
                int j = this.getIndex(localX, localY, localZ);
                int k = j / this.valuesPerLong;
                if (k >= 0 && k < this.data.length) {
                    long l = this.data[k];
                    int m = (j - k * this.valuesPerLong) * this.bits;
                    int n = (int)(l >> m & this.mask);
                    return (Dynamic<?>)(n >= 0 && n < i ? this.palette.get(n) : null);
                } else {
                    return null;
                }
            }
        }

        private int getIndex(int localX, int localY, int localZ) {
            return (localY << 4 | localZ) << 4 | localX;
        }

        public List<? extends Dynamic<?>> palette() {
            return this.palette;
        }

        public long[] data() {
            return this.data;
        }
    }
}
