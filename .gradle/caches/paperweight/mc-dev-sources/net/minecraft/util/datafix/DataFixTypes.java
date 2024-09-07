package net.minecraft.util.datafix;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.datafix.fixes.References;

public enum DataFixTypes {
    LEVEL(References.LEVEL),
    PLAYER(References.PLAYER),
    CHUNK(References.CHUNK),
    HOTBAR(References.HOTBAR),
    OPTIONS(References.OPTIONS),
    STRUCTURE(References.STRUCTURE),
    STATS(References.STATS),
    SAVED_DATA_COMMAND_STORAGE(References.SAVED_DATA_COMMAND_STORAGE),
    SAVED_DATA_FORCED_CHUNKS(References.SAVED_DATA_FORCED_CHUNKS),
    SAVED_DATA_MAP_DATA(References.SAVED_DATA_MAP_DATA),
    SAVED_DATA_MAP_INDEX(References.SAVED_DATA_MAP_INDEX),
    SAVED_DATA_RAIDS(References.SAVED_DATA_RAIDS),
    SAVED_DATA_RANDOM_SEQUENCES(References.SAVED_DATA_RANDOM_SEQUENCES),
    SAVED_DATA_SCOREBOARD(References.SAVED_DATA_SCOREBOARD),
    SAVED_DATA_STRUCTURE_FEATURE_INDICES(References.SAVED_DATA_STRUCTURE_FEATURE_INDICES),
    ADVANCEMENTS(References.ADVANCEMENTS),
    POI_CHUNK(References.POI_CHUNK),
    WORLD_GEN_SETTINGS(References.WORLD_GEN_SETTINGS),
    ENTITY_CHUNK(References.ENTITY_CHUNK);

    public static final Set<TypeReference> TYPES_FOR_LEVEL_LIST;
    private final TypeReference type;

    private DataFixTypes(final TypeReference typeReference) {
        this.type = typeReference;
    }

    static int currentVersion() {
        return SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    }

    public <A> Codec<A> wrapCodec(Codec<A> baseCodec, DataFixer dataFixer, int currentDataVersion) {
        return new Codec<A>() {
            public <T> DataResult<T> encode(A object, DynamicOps<T> dynamicOps, T object2) {
                return baseCodec.encode(object, dynamicOps, object2)
                    .flatMap(
                        encoded -> dynamicOps.mergeToMap(
                                (T)encoded, dynamicOps.createString("DataVersion"), dynamicOps.createInt(DataFixTypes.currentVersion())
                            )
                    );
            }

            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> dynamicOps, T object) {
                int i = dynamicOps.get(object, "DataVersion").flatMap(dynamicOps::getNumberValue).map(Number::intValue).result().orElse(currentDataVersion);
                Dynamic<T> dynamic = new Dynamic<>(dynamicOps, dynamicOps.remove(object, "DataVersion"));
                Dynamic<T> dynamic2 = DataFixTypes.this.updateToCurrentVersion(dataFixer, dynamic, i);
                return baseCodec.decode(dynamic2);
            }
        };
    }

    public <T> Dynamic<T> update(DataFixer dataFixer, Dynamic<T> dynamic, int oldVersion, int newVersion) {
        return dataFixer.update(this.type, dynamic, oldVersion, newVersion);
    }

    public <T> Dynamic<T> updateToCurrentVersion(DataFixer dataFixer, Dynamic<T> dynamic, int oldVersion) {
        return this.update(dataFixer, dynamic, oldVersion, currentVersion());
    }

    public CompoundTag update(DataFixer dataFixer, CompoundTag nbt, int oldVersion, int newVersion) {
        return (CompoundTag)this.update(dataFixer, new Dynamic<>(NbtOps.INSTANCE, nbt), oldVersion, newVersion).getValue();
    }

    public CompoundTag updateToCurrentVersion(DataFixer dataFixer, CompoundTag nbt, int oldVersion) {
        return this.update(dataFixer, nbt, oldVersion, currentVersion());
    }

    static {
        TYPES_FOR_LEVEL_LIST = Set.of(LEVEL.type);
    }
}
