package net.minecraft.data;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public interface DataProvider {
    ToIntFunction<String> FIXED_ORDER_FIELDS = Util.make(new Object2IntOpenHashMap<>(), map -> {
        map.put("type", 0);
        map.put("parent", 1);
        map.defaultReturnValue(2);
    });
    Comparator<String> KEY_COMPARATOR = Comparator.comparingInt(FIXED_ORDER_FIELDS).thenComparing(key -> (String)key);
    Logger LOGGER = LogUtils.getLogger();

    CompletableFuture<?> run(CachedOutput writer);

    String getName();

    static <T> CompletableFuture<?> saveStable(CachedOutput writer, HolderLookup.Provider registryLookup, Codec<T> codec, T value, Path path) {
        RegistryOps<JsonElement> registryOps = registryLookup.createSerializationContext(JsonOps.INSTANCE);
        JsonElement jsonElement = codec.encodeStart(registryOps, value).getOrThrow();
        return saveStable(writer, jsonElement, path);
    }

    static CompletableFuture<?> saveStable(CachedOutput writer, JsonElement json, Path path) {
        return CompletableFuture.runAsync(() -> {
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);

                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(hashingOutputStream, StandardCharsets.UTF_8))) {
                    jsonWriter.setSerializeNulls(false);
                    jsonWriter.setIndent("  ");
                    GsonHelper.writeValue(jsonWriter, json, KEY_COMPARATOR);
                }

                writer.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
            } catch (IOException var10) {
                LOGGER.error("Failed to save file to {}", path, var10);
            }
        }, Util.backgroundExecutor());
    }

    @FunctionalInterface
    public interface Factory<T extends DataProvider> {
        T create(PackOutput output);
    }
}
