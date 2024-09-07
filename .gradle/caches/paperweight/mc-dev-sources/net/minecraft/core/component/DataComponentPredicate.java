package net.minecraft.core.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public final class DataComponentPredicate implements Predicate<DataComponentMap> {
    public static final Codec<DataComponentPredicate> CODEC = DataComponentType.VALUE_MAP_CODEC
        .xmap(
            map -> new DataComponentPredicate(map.entrySet().stream().map(TypedDataComponent::fromEntryUnchecked).collect(Collectors.toList())),
            predicate -> predicate.expectedComponents
                    .stream()
                    .filter(typedDataComponent -> !typedDataComponent.type().isTransient())
                    .collect(Collectors.toMap(TypedDataComponent::type, TypedDataComponent::value))
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPredicate> STREAM_CODEC = TypedDataComponent.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(DataComponentPredicate::new, predicate -> predicate.expectedComponents);
    public static final DataComponentPredicate EMPTY = new DataComponentPredicate(List.of());
    private final List<TypedDataComponent<?>> expectedComponents;

    DataComponentPredicate(List<TypedDataComponent<?>> components) {
        this.expectedComponents = components;
    }

    public static DataComponentPredicate.Builder builder() {
        return new DataComponentPredicate.Builder();
    }

    public static DataComponentPredicate allOf(DataComponentMap components) {
        return new DataComponentPredicate(ImmutableList.copyOf(components));
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof DataComponentPredicate dataComponentPredicate && this.expectedComponents.equals(dataComponentPredicate.expectedComponents)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return this.expectedComponents.hashCode();
    }

    @Override
    public String toString() {
        return this.expectedComponents.toString();
    }

    @Override
    public boolean test(DataComponentMap dataComponentMap) {
        for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
            Object object = dataComponentMap.get(typedDataComponent.type());
            if (!Objects.equals(typedDataComponent.value(), object)) {
                return false;
            }
        }

        return true;
    }

    public boolean test(DataComponentHolder holder) {
        return this.test(holder.getComponents());
    }

    public boolean alwaysMatches() {
        return this.expectedComponents.isEmpty();
    }

    public DataComponentPatch asPatch() {
        DataComponentPatch.Builder builder = DataComponentPatch.builder();

        for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
            builder.set(typedDataComponent);
        }

        return builder.build();
    }

    public static class Builder {
        private final List<TypedDataComponent<?>> expectedComponents = new ArrayList<>();

        Builder() {
        }

        public <T> DataComponentPredicate.Builder expect(DataComponentType<? super T> type, T value) {
            for (TypedDataComponent<?> typedDataComponent : this.expectedComponents) {
                if (typedDataComponent.type() == type) {
                    throw new IllegalArgumentException("Predicate already has component of type: '" + type + "'");
                }
            }

            this.expectedComponents.add(new TypedDataComponent<>(type, value));
            return this;
        }

        public DataComponentPredicate build() {
            return new DataComponentPredicate(List.copyOf(this.expectedComponents));
        }
    }
}
