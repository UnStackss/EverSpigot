package net.minecraft.data.tags;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;

public abstract class IntrinsicHolderTagsProvider<T> extends TagsProvider<T> {
    private final Function<T, ResourceKey<T>> keyExtractor;

    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryRef,
        CompletableFuture<HolderLookup.Provider> registryLookupFuture,
        Function<T, ResourceKey<T>> valueToKey
    ) {
        super(output, registryRef, registryLookupFuture);
        this.keyExtractor = valueToKey;
    }

    public IntrinsicHolderTagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryRef,
        CompletableFuture<HolderLookup.Provider> registryLookupFuture,
        CompletableFuture<TagsProvider.TagLookup<T>> parentTagLookupFuture,
        Function<T, ResourceKey<T>> valueToKey
    ) {
        super(output, registryRef, registryLookupFuture, parentTagLookupFuture);
        this.keyExtractor = valueToKey;
    }

    @Override
    protected IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> tag(TagKey<T> tagKey) {
        TagBuilder tagBuilder = this.getOrCreateRawBuilder(tagKey);
        return new IntrinsicHolderTagsProvider.IntrinsicTagAppender<>(tagBuilder, this.keyExtractor);
    }

    protected static class IntrinsicTagAppender<T> extends TagsProvider.TagAppender<T> {
        private final Function<T, ResourceKey<T>> keyExtractor;

        IntrinsicTagAppender(TagBuilder builder, Function<T, ResourceKey<T>> valueToKey) {
            super(builder);
            this.keyExtractor = valueToKey;
        }

        @Override
        public IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> addTag(TagKey<T> tagKey) {
            super.addTag(tagKey);
            return this;
        }

        public final IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> add(T value) {
            this.add(this.keyExtractor.apply(value));
            return this;
        }

        @SafeVarargs
        public final IntrinsicHolderTagsProvider.IntrinsicTagAppender<T> add(T... values) {
            Stream.<T>of(values).map(this.keyExtractor).forEach(this::add);
            return this;
        }
    }
}
