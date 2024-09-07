package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;

public interface ResourceMetadata {
    ResourceMetadata EMPTY = new ResourceMetadata() {
        @Override
        public <T> Optional<T> getSection(MetadataSectionSerializer<T> reader) {
            return Optional.empty();
        }
    };
    IoSupplier<ResourceMetadata> EMPTY_SUPPLIER = () -> EMPTY;

    static ResourceMetadata fromJsonStream(InputStream stream) throws IOException {
        ResourceMetadata var3;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            final JsonObject jsonObject = GsonHelper.parse(bufferedReader);
            var3 = new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> reader) {
                    String string = reader.getMetadataSectionName();
                    return jsonObject.has(string) ? Optional.of(reader.fromJson(GsonHelper.getAsJsonObject(jsonObject, string))) : Optional.empty();
                }
            };
        }

        return var3;
    }

    <T> Optional<T> getSection(MetadataSectionSerializer<T> reader);

    default ResourceMetadata copySections(Collection<MetadataSectionSerializer<?>> readers) {
        ResourceMetadata.Builder builder = new ResourceMetadata.Builder();

        for (MetadataSectionSerializer<?> metadataSectionSerializer : readers) {
            this.copySection(builder, metadataSectionSerializer);
        }

        return builder.build();
    }

    private <T> void copySection(ResourceMetadata.Builder builder, MetadataSectionSerializer<T> reader) {
        this.getSection(reader).ifPresent(value -> builder.put(reader, (T)value));
    }

    public static class Builder {
        private final ImmutableMap.Builder<MetadataSectionSerializer<?>, Object> map = ImmutableMap.builder();

        public <T> ResourceMetadata.Builder put(MetadataSectionSerializer<T> reader, T value) {
            this.map.put(reader, value);
            return this;
        }

        public ResourceMetadata build() {
            final ImmutableMap<MetadataSectionSerializer<?>, Object> immutableMap = this.map.build();
            return immutableMap.isEmpty() ? ResourceMetadata.EMPTY : new ResourceMetadata() {
                @Override
                public <T> Optional<T> getSection(MetadataSectionSerializer<T> reader) {
                    return Optional.ofNullable((T)immutableMap.get(reader));
                }
            };
        }
    }
}
