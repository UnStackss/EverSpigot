package net.minecraft.tags;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.DependencySorter;
import org.slf4j.Logger;

public class TagLoader<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    final Function<ResourceLocation, Optional<? extends T>> idToValue;
    private final String directory;

    public TagLoader(Function<ResourceLocation, Optional<? extends T>> registryGetter, String dataType) {
        this.idToValue = registryGetter;
        this.directory = dataType;
    }

    public Map<ResourceLocation, List<TagLoader.EntryWithSource>> load(ResourceManager resourceManager) {
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> map = Maps.newHashMap();
        FileToIdConverter fileToIdConverter = FileToIdConverter.json(this.directory);

        for (Entry<ResourceLocation, List<Resource>> entry : fileToIdConverter.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation resourceLocation = entry.getKey();
            ResourceLocation resourceLocation2 = fileToIdConverter.fileToId(resourceLocation);

            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    List<TagLoader.EntryWithSource> list = map.computeIfAbsent(resourceLocation2, id -> new ArrayList<>());
                    TagFile tagFile = TagFile.CODEC.parse(new Dynamic<>(JsonOps.INSTANCE, jsonElement)).getOrThrow();
                    if (tagFile.replace()) {
                        list.clear();
                    }

                    String string = resource.sourcePackId();
                    tagFile.entries().forEach(entryx -> list.add(new TagLoader.EntryWithSource(entryx, string)));
                } catch (Exception var17) {
                    LOGGER.error("Couldn't read tag list {} from {} in data pack {}", resourceLocation2, resourceLocation, resource.sourcePackId(), var17);
                }
            }
        }

        return map;
    }

    private Either<Collection<TagLoader.EntryWithSource>, Collection<T>> build(TagEntry.Lookup<T> valueGetter, List<TagLoader.EntryWithSource> entries) {
        Builder<T> builder = ImmutableSet.builder();
        List<TagLoader.EntryWithSource> list = new ArrayList<>();

        for (TagLoader.EntryWithSource entryWithSource : entries) {
            if (!entryWithSource.entry().build(valueGetter, builder::add)) {
                list.add(entryWithSource);
            }
        }

        return list.isEmpty() ? Either.right(builder.build()) : Either.left(list);
    }

    public Map<ResourceLocation, Collection<T>> build(Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        final Map<ResourceLocation, Collection<T>> map = Maps.newHashMap();
        TagEntry.Lookup<T> lookup = new TagEntry.Lookup<T>() {
            @Nullable
            @Override
            public T element(ResourceLocation id) {
                return (T)TagLoader.this.idToValue.apply(id).orElse(null);
            }

            @Nullable
            @Override
            public Collection<T> tag(ResourceLocation id) {
                return map.get(id);
            }
        };
        DependencySorter<ResourceLocation, TagLoader.SortingEntry> dependencySorter = new DependencySorter<>();
        tags.forEach((id, entries) -> dependencySorter.addEntry(id, new TagLoader.SortingEntry((List<TagLoader.EntryWithSource>)entries)));
        dependencySorter.orderByDependencies(
            (id, dependencies) -> this.build(lookup, dependencies.entries)
                    .ifLeft(
                        missingReferences -> LOGGER.error(
                                "Couldn't load tag {} as it is missing following references: {}",
                                id,
                                missingReferences.stream().map(Objects::toString).collect(Collectors.joining(", "))
                            )
                    )
                    .ifRight(resolvedEntries -> map.put(id, (Collection<T>)resolvedEntries))
        );
        return map;
    }

    public Map<ResourceLocation, Collection<T>> loadAndBuild(ResourceManager manager) {
        return this.build(this.load(manager));
    }

    public static record EntryWithSource(TagEntry entry, String source) {
        @Override
        public String toString() {
            return this.entry + " (from " + this.source + ")";
        }
    }

    static record SortingEntry(List<TagLoader.EntryWithSource> entries) implements DependencySorter.Entry<ResourceLocation> {
        @Override
        public void visitRequiredDependencies(Consumer<ResourceLocation> callback) {
            this.entries.forEach(entry -> entry.entry.visitRequiredDependencies(callback));
        }

        @Override
        public void visitOptionalDependencies(Consumer<ResourceLocation> callback) {
            this.entries.forEach(entry -> entry.entry.visitOptionalDependencies(callback));
        }
    }
}
