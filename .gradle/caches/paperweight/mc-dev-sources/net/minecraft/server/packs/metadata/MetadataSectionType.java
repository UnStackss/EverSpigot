package net.minecraft.server.packs.metadata;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

public interface MetadataSectionType<T> extends MetadataSectionSerializer<T> {
    JsonObject toJson(T metadata);

    static <T> MetadataSectionType<T> fromCodec(String key, Codec<T> codec) {
        return new MetadataSectionType<T>() {
            @Override
            public String getMetadataSectionName() {
                return key;
            }

            @Override
            public T fromJson(JsonObject json) {
                return codec.parse(JsonOps.INSTANCE, json).getOrThrow(JsonParseException::new);
            }

            @Override
            public JsonObject toJson(T metadata) {
                return codec.encodeStart(JsonOps.INSTANCE, metadata).getOrThrow(IllegalArgumentException::new).getAsJsonObject();
            }
        };
    }
}
