package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ChunkToProtochunkFix extends DataFix {
    private static final int NUM_SECTIONS = 16;

    public ChunkToProtochunkFix(Schema outputSchema, boolean changestype) {
        super(outputSchema, changestype);
    }

    public TypeRewriteRule makeRule() {
        return this.writeFixAndRead(
            "ChunkToProtoChunkFix",
            this.getInputSchema().getType(References.CHUNK),
            this.getOutputSchema().getType(References.CHUNK),
            chunkDynamic -> chunkDynamic.update("Level", ChunkToProtochunkFix::fixChunkData)
        );
    }

    private static <T> Dynamic<T> fixChunkData(Dynamic<T> levelDynamic) {
        boolean bl = levelDynamic.get("TerrainPopulated").asBoolean(false);
        boolean bl2 = levelDynamic.get("LightPopulated").asNumber().result().isEmpty() || levelDynamic.get("LightPopulated").asBoolean(false);
        String string;
        if (bl) {
            if (bl2) {
                string = "mobs_spawned";
            } else {
                string = "decorated";
            }
        } else {
            string = "carved";
        }

        return repackTicks(repackBiomes(levelDynamic))
            .set("Status", levelDynamic.createString(string))
            .set("hasLegacyStructureData", levelDynamic.createBoolean(true));
    }

    private static <T> Dynamic<T> repackBiomes(Dynamic<T> levelDynamic) {
        return levelDynamic.update("Biomes", biomesDynamic -> DataFixUtils.orElse(biomesDynamic.asByteBufferOpt().result().map(biomes -> {
                int[] is = new int[256];

                for (int i = 0; i < is.length; i++) {
                    if (i < biomes.capacity()) {
                        is[i] = biomes.get(i) & 255;
                    }
                }

                return levelDynamic.createIntList(Arrays.stream(is));
            }), biomesDynamic));
    }

    private static <T> Dynamic<T> repackTicks(Dynamic<T> levelDynamic) {
        return DataFixUtils.orElse(
            levelDynamic.get("TileTicks")
                .asStreamOpt()
                .result()
                .map(
                    tileTicksDynamic -> {
                        List<ShortList> list = IntStream.range(0, 16).mapToObj(sectionY -> new ShortArrayList()).collect(Collectors.toList());
                        tileTicksDynamic.forEach(tickTag -> {
                            int i = tickTag.get("x").asInt(0);
                            int j = tickTag.get("y").asInt(0);
                            int k = tickTag.get("z").asInt(0);
                            short s = packOffsetCoordinates(i, j, k);
                            list.get(j >> 4).add(s);
                        });
                        return levelDynamic.remove("TileTicks")
                            .set(
                                "ToBeTicked",
                                levelDynamic.createList(
                                    list.stream()
                                        .map(
                                            section -> levelDynamic.createList(
                                                    section.intStream().mapToObj(packedLocalPos -> levelDynamic.createShort((short)packedLocalPos))
                                                )
                                        )
                                )
                            );
                    }
                ),
            levelDynamic
        );
    }

    private static short packOffsetCoordinates(int x, int y, int z) {
        return (short)(x & 15 | (y & 15) << 4 | (z & 15) << 8);
    }
}
