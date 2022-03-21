package net.minecraft.core;

import com.mojang.datafixers.util.Either;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public interface Holder<T> {
    T value();

    boolean isBound();

    boolean is(ResourceLocation id);

    boolean is(ResourceKey<T> key);

    boolean is(Predicate<ResourceKey<T>> predicate);

    boolean is(TagKey<T> tag);

    @Deprecated
    boolean is(Holder<T> entry);

    Stream<TagKey<T>> tags();

    Either<ResourceKey<T>, T> unwrap();

    Optional<ResourceKey<T>> unwrapKey();

    Holder.Kind kind();

    boolean canSerializeIn(HolderOwner<T> owner);

    default String getRegisteredName() {
        return this.unwrapKey().map(key -> key.location().toString()).orElse("[unregistered]");
    }

    static <T> Holder<T> direct(T value) {
        return new Holder.Direct<>(value);
    }

    public static record Direct<T>(@Override T value) implements Holder<T> {
        @Override
        public boolean isBound() {
            return true;
        }

        @Override
        public boolean is(ResourceLocation id) {
            return false;
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return false;
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return false;
        }

        @Override
        public boolean is(Holder<T> entry) {
            return this.value.equals(entry.value());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return false;
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.right(this.value);
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.empty();
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.DIRECT;
        }

        @Override
        public String toString() {
            return "Direct{" + this.value + "}";
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> owner) {
            return true;
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return Stream.of();
        }
    }

    public static enum Kind {
        REFERENCE,
        DIRECT;
    }

    public static class Reference<T> implements Holder<T> {
        private final HolderOwner<T> owner;
        private Set<TagKey<T>> tags = Set.of();
        private final Holder.Reference.Type type;
        @Nullable
        private ResourceKey<T> key;
        @Nullable
        private T value;

        protected Reference(Holder.Reference.Type referenceType, HolderOwner<T> owner, @Nullable ResourceKey<T> registryKey, @Nullable T value) {
            this.owner = owner;
            this.type = referenceType;
            this.key = registryKey;
            this.value = value;
        }

        public static <T> Holder.Reference<T> createStandAlone(HolderOwner<T> owner, ResourceKey<T> registryKey) {
            return new Holder.Reference<>(Holder.Reference.Type.STAND_ALONE, owner, registryKey, null);
        }

        @Deprecated
        public static <T> Holder.Reference<T> createIntrusive(HolderOwner<T> owner, @Nullable T value) {
            return new Holder.Reference<>(Holder.Reference.Type.INTRUSIVE, owner, null, value);
        }

        public ResourceKey<T> key() {
            if (this.key == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.value + "' from registry " + this.owner);
            } else {
                return this.key;
            }
        }

        @Override
        public T value() {
            if (this.value == null) {
                throw new IllegalStateException("Trying to access unbound value '" + this.key + "' from registry " + this.owner);
            } else {
                return this.value;
            }
        }

        @Override
        public boolean is(ResourceLocation id) {
            return this.key().location().equals(id);
        }

        @Override
        public boolean is(ResourceKey<T> key) {
            return this.key() == key;
        }

        @Override
        public boolean is(TagKey<T> tag) {
            return this.tags.contains(tag);
        }

        @Override
        public boolean is(Holder<T> entry) {
            return entry.is(this.key());
        }

        @Override
        public boolean is(Predicate<ResourceKey<T>> predicate) {
            return predicate.test(this.key());
        }

        @Override
        public boolean canSerializeIn(HolderOwner<T> owner) {
            return this.owner.canSerializeIn(owner);
        }

        @Override
        public Either<ResourceKey<T>, T> unwrap() {
            return Either.left(this.key());
        }

        @Override
        public Optional<ResourceKey<T>> unwrapKey() {
            return Optional.of(this.key());
        }

        @Override
        public Holder.Kind kind() {
            return Holder.Kind.REFERENCE;
        }

        @Override
        public boolean isBound() {
            return this.key != null && this.value != null;
        }

        void bindKey(ResourceKey<T> registryKey) {
            if (this.key != null && registryKey != this.key) {
                throw new IllegalStateException("Can't change holder key: existing=" + this.key + ", new=" + registryKey);
            } else {
                this.key = registryKey;
            }
        }

        protected void bindValue(T value) {
            if (this.type == Holder.Reference.Type.INTRUSIVE && this.value != value) {
                throw new IllegalStateException("Can't change holder " + this.key + " value: existing=" + this.value + ", new=" + value);
            } else {
                this.value = value;
            }
        }

        void bindTags(Collection<TagKey<T>> tags) {
            this.tags = java.util.Collections.unmodifiableSet(new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>(tags)); // Paper
        }

        @Override
        public Stream<TagKey<T>> tags() {
            return this.tags.stream();
        }

        @Override
        public String toString() {
            return "Reference{" + this.key + "=" + this.value + "}";
        }

        protected static enum Type {
            STAND_ALONE,
            INTRUSIVE;
        }
    }
}
