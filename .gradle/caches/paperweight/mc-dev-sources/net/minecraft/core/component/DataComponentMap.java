package net.minecraft.core.component;

import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;

public interface DataComponentMap extends Iterable<TypedDataComponent<?>> {
    DataComponentMap EMPTY = new DataComponentMap() {
        @Nullable
        @Override
        public <T> T get(DataComponentType<? extends T> type) {
            return null;
        }

        @Override
        public Set<DataComponentType<?>> keySet() {
            return Set.of();
        }

        @Override
        public Iterator<TypedDataComponent<?>> iterator() {
            return Collections.emptyIterator();
        }
    };
    Codec<DataComponentMap> CODEC = makeCodecFromMap(DataComponentType.VALUE_MAP_CODEC);

    static Codec<DataComponentMap> makeCodec(Codec<DataComponentType<?>> componentTypeCodec) {
        return makeCodecFromMap(Codec.dispatchedMap(componentTypeCodec, DataComponentType::codecOrThrow));
    }

    static Codec<DataComponentMap> makeCodecFromMap(Codec<Map<DataComponentType<?>, Object>> typeToValueMapCodec) {
        return typeToValueMapCodec.flatComapMap(DataComponentMap.Builder::buildFromMapTrusted, componentMap -> {
            int i = componentMap.size();
            if (i == 0) {
                return DataResult.success(Reference2ObjectMaps.emptyMap());
            } else {
                Reference2ObjectMap<DataComponentType<?>, Object> reference2ObjectMap = new Reference2ObjectArrayMap<>(i);

                for (TypedDataComponent<?> typedDataComponent : componentMap) {
                    if (!typedDataComponent.type().isTransient()) {
                        reference2ObjectMap.put(typedDataComponent.type(), typedDataComponent.value());
                    }
                }

                return DataResult.success(reference2ObjectMap);
            }
        });
    }

    static DataComponentMap composite(DataComponentMap base, DataComponentMap overrides) {
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                T object = overrides.get(type);
                return object != null ? object : base.get(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.union(base.keySet(), overrides.keySet());
            }
        };
    }

    static DataComponentMap.Builder builder() {
        return new DataComponentMap.Builder();
    }

    @Nullable
    <T> T get(DataComponentType<? extends T> type);

    Set<DataComponentType<?>> keySet();

    default boolean has(DataComponentType<?> type) {
        return this.get(type) != null;
    }

    default <T> T getOrDefault(DataComponentType<? extends T> type, T fallback) {
        T object = this.get(type);
        return object != null ? object : fallback;
    }

    @Nullable
    default <T> TypedDataComponent<T> getTyped(DataComponentType<T> type) {
        T object = this.get(type);
        return object != null ? new TypedDataComponent<>(type, object) : null;
    }

    @Override
    default Iterator<TypedDataComponent<?>> iterator() {
        return Iterators.transform(this.keySet().iterator(), type -> Objects.requireNonNull(this.getTyped((DataComponentType<?>)type)));
    }

    default Stream<TypedDataComponent<?>> stream() {
        return StreamSupport.stream(Spliterators.spliterator(this.iterator(), (long)this.size(), 1345), false);
    }

    default int size() {
        return this.keySet().size();
    }

    default boolean isEmpty() {
        return this.size() == 0;
    }

    default DataComponentMap filter(Predicate<DataComponentType<?>> predicate) {
        return new DataComponentMap() {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                return predicate.test(type) ? DataComponentMap.this.get(type) : null;
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return Sets.filter(DataComponentMap.this.keySet(), predicate::test);
            }
        };
    }

    public static class Builder {
        private final Reference2ObjectMap<DataComponentType<?>, Object> map = new Reference2ObjectArrayMap<>();

        Builder() {
        }

        public <T> DataComponentMap.Builder set(DataComponentType<T> type, @Nullable T value) {
            this.setUnchecked(type, value);
            return this;
        }

        <T> void setUnchecked(DataComponentType<T> type, @Nullable Object value) {
            if (value != null) {
                this.map.put(type, value);
            } else {
                this.map.remove(type);
            }
        }

        public DataComponentMap.Builder addAll(DataComponentMap componentSet) {
            for (TypedDataComponent<?> typedDataComponent : componentSet) {
                this.map.put(typedDataComponent.type(), typedDataComponent.value());
            }

            return this;
        }

        public DataComponentMap build() {
            return buildFromMapTrusted(this.map);
        }

        private static DataComponentMap buildFromMapTrusted(Map<DataComponentType<?>, Object> components) {
            if (components.isEmpty()) {
                return DataComponentMap.EMPTY;
            } else {
                return components.size() < 8
                    ? new DataComponentMap.Builder.SimpleMap(new Reference2ObjectArrayMap<>(components))
                    : new DataComponentMap.Builder.SimpleMap(new Reference2ObjectOpenHashMap<>(components));
            }
        }

        static record SimpleMap(Reference2ObjectMap<DataComponentType<?>, Object> map) implements DataComponentMap {
            @Nullable
            @Override
            public <T> T get(DataComponentType<? extends T> type) {
                return (T)this.map.get(type);
            }

            @Override
            public boolean has(DataComponentType<?> type) {
                return this.map.containsKey(type);
            }

            @Override
            public Set<DataComponentType<?>> keySet() {
                return this.map.keySet();
            }

            @Override
            public Iterator<TypedDataComponent<?>> iterator() {
                return Iterators.transform(Reference2ObjectMaps.fastIterator(this.map), TypedDataComponent::fromEntryUnchecked);
            }

            @Override
            public int size() {
                return this.map.size();
            }

            @Override
            public String toString() {
                return this.map.toString();
            }
        }
    }
}
