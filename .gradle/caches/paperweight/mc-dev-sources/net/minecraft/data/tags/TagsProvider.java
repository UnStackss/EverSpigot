package net.minecraft.data.tags;

import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;

public abstract class TagsProvider<T> implements DataProvider {
    protected final PackOutput.PathProvider pathProvider;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;
    private final CompletableFuture<Void> contentsDone = new CompletableFuture<>();
    private final CompletableFuture<TagsProvider.TagLookup<T>> parentProvider;
    protected final ResourceKey<? extends Registry<T>> registryKey;
    private final Map<ResourceLocation, TagBuilder> builders = Maps.newLinkedHashMap();

    protected TagsProvider(PackOutput output, ResourceKey<? extends Registry<T>> registryRef, CompletableFuture<HolderLookup.Provider> registryLookupFuture) {
        this(output, registryRef, registryLookupFuture, CompletableFuture.completedFuture(TagsProvider.TagLookup.empty()));
    }

    protected TagsProvider(
        PackOutput output,
        ResourceKey<? extends Registry<T>> registryRef,
        CompletableFuture<HolderLookup.Provider> registryLookupFuture,
        CompletableFuture<TagsProvider.TagLookup<T>> parentTagLookupFuture
    ) {
        this.pathProvider = output.createRegistryTagsPathProvider(registryRef);
        this.registryKey = registryRef;
        this.parentProvider = parentTagLookupFuture;
        this.lookupProvider = registryLookupFuture;
    }

    @Override
    public final String getName() {
        return "Tags for " + this.registryKey.location();
    }

    protected abstract void addTags(HolderLookup.Provider lookup);

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        record CombinedData<T>(HolderLookup.Provider contents, TagsProvider.TagLookup<T> parent) {
        }

        return this.createContentsProvider()
            .thenApply(registryLookupFuture -> {
                this.contentsDone.complete(null);
                return (HolderLookup.Provider)registryLookupFuture;
            })
            .thenCombineAsync(this.parentProvider, (lookup, parent) -> new CombinedData<>(lookup, (TagsProvider.TagLookup<T>)parent), Util.backgroundExecutor())
            .thenCompose(
                info -> {
                    HolderLookup.RegistryLookup<T> registryLookup = info.contents.lookupOrThrow(this.registryKey);
                    Predicate<ResourceLocation> predicate = id -> registryLookup.get(ResourceKey.create(this.registryKey, id)).isPresent();
                    Predicate<ResourceLocation> predicate2 = id -> this.builders.containsKey(id) || info.parent.contains(TagKey.create(this.registryKey, id));
                    return CompletableFuture.allOf(
                        this.builders
                            .entrySet()
                            .stream()
                            .map(
                                entry -> {
                                    ResourceLocation resourceLocation = entry.getKey();
                                    TagBuilder tagBuilder = entry.getValue();
                                    List<TagEntry> list = tagBuilder.build();
                                    List<TagEntry> list2 = list.stream().filter(tagEntry -> !tagEntry.verifyIfPresent(predicate, predicate2)).toList();
                                    if (!list2.isEmpty()) {
                                        throw new IllegalArgumentException(
                                            String.format(
                                                Locale.ROOT,
                                                "Couldn't define tag %s as it is missing following references: %s",
                                                resourceLocation,
                                                list2.stream().map(Objects::toString).collect(Collectors.joining(","))
                                            )
                                        );
                                    } else {
                                        Path path = this.pathProvider.json(resourceLocation);
                                        return DataProvider.saveStable(writer, info.contents, TagFile.CODEC, new TagFile(list, false), path);
                                    }
                                }
                            )
                            .toArray(CompletableFuture[]::new)
                    );
                }
            );
    }

    protected TagsProvider.TagAppender<T> tag(TagKey<T> tag) {
        TagBuilder tagBuilder = this.getOrCreateRawBuilder(tag);
        return new TagsProvider.TagAppender<>(tagBuilder);
    }

    protected TagBuilder getOrCreateRawBuilder(TagKey<T> tag) {
        return this.builders.computeIfAbsent(tag.location(), id -> TagBuilder.create());
    }

    public CompletableFuture<TagsProvider.TagLookup<T>> contentsGetter() {
        return this.contentsDone.thenApply(void_ -> tag -> Optional.ofNullable(this.builders.get(tag.location())));
    }

    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return this.lookupProvider.thenApply(lookup -> {
            this.builders.clear();
            this.addTags(lookup);
            return (HolderLookup.Provider)lookup;
        });
    }

    protected static class TagAppender<T> {
        private final TagBuilder builder;

        protected TagAppender(TagBuilder builder) {
            this.builder = builder;
        }

        public final TagsProvider.TagAppender<T> add(ResourceKey<T> key) {
            this.builder.addElement(key.location());
            return this;
        }

        @SafeVarargs
        public final TagsProvider.TagAppender<T> add(ResourceKey<T>... keys) {
            for (ResourceKey<T> resourceKey : keys) {
                this.builder.addElement(resourceKey.location());
            }

            return this;
        }

        public final TagsProvider.TagAppender<T> addAll(List<ResourceKey<T>> keys) {
            for (ResourceKey<T> resourceKey : keys) {
                this.builder.addElement(resourceKey.location());
            }

            return this;
        }

        public TagsProvider.TagAppender<T> addOptional(ResourceLocation id) {
            this.builder.addOptionalElement(id);
            return this;
        }

        public TagsProvider.TagAppender<T> addTag(TagKey<T> identifiedTag) {
            this.builder.addTag(identifiedTag.location());
            return this;
        }

        public TagsProvider.TagAppender<T> addOptionalTag(ResourceLocation id) {
            this.builder.addOptionalTag(id);
            return this;
        }
    }

    @FunctionalInterface
    public interface TagLookup<T> extends Function<TagKey<T>, Optional<TagBuilder>> {
        static <T> TagsProvider.TagLookup<T> empty() {
            return tag -> Optional.empty();
        }

        default boolean contains(TagKey<T> tag) {
            return this.apply(tag).isPresent();
        }
    }
}
