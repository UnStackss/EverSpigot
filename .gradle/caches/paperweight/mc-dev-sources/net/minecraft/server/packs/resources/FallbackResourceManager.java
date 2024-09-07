package net.minecraft.server.packs.resources;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

public class FallbackResourceManager implements ResourceManager {
    static final Logger LOGGER = LogUtils.getLogger();
    protected final List<FallbackResourceManager.PackEntry> fallbacks = Lists.newArrayList();
    private final PackType type;
    private final String namespace;

    public FallbackResourceManager(PackType type, String namespace) {
        this.type = type;
        this.namespace = namespace;
    }

    public void push(PackResources pack) {
        this.pushInternal(pack.packId(), pack, null);
    }

    public void push(PackResources pack, Predicate<ResourceLocation> filter) {
        this.pushInternal(pack.packId(), pack, filter);
    }

    public void pushFilterOnly(String id, Predicate<ResourceLocation> filter) {
        this.pushInternal(id, null, filter);
    }

    private void pushInternal(String id, @Nullable PackResources underlyingPack, @Nullable Predicate<ResourceLocation> filter) {
        this.fallbacks.add(new FallbackResourceManager.PackEntry(id, underlyingPack, filter));
    }

    @Override
    public Set<String> getNamespaces() {
        return ImmutableSet.of(this.namespace);
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation id) {
        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(i);
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, id);
                if (ioSupplier != null) {
                    IoSupplier<ResourceMetadata> ioSupplier2 = this.createStackMetadataFinder(id, i);
                    return Optional.of(createResource(packResources, id, ioSupplier, ioSupplier2));
                }
            }

            if (packEntry.isFiltered(id)) {
                LOGGER.warn("Resource {} not found, but was filtered by pack {}", id, packEntry.name);
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private static Resource createResource(
        PackResources pack, ResourceLocation id, IoSupplier<InputStream> supplier, IoSupplier<ResourceMetadata> metadataSupplier
    ) {
        return new Resource(pack, wrapForDebug(id, pack, supplier), metadataSupplier);
    }

    private static IoSupplier<InputStream> wrapForDebug(ResourceLocation id, PackResources pack, IoSupplier<InputStream> supplier) {
        return LOGGER.isDebugEnabled() ? () -> new FallbackResourceManager.LeakedResourceWarningInputStream(supplier.get(), id, pack.packId()) : supplier;
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation id) {
        ResourceLocation resourceLocation = getMetadataLocation(id);
        List<Resource> list = new ArrayList<>();
        boolean bl = false;
        String string = null;

        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(i);
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, id);
                if (ioSupplier != null) {
                    IoSupplier<ResourceMetadata> ioSupplier2;
                    if (bl) {
                        ioSupplier2 = ResourceMetadata.EMPTY_SUPPLIER;
                    } else {
                        ioSupplier2 = () -> {
                            IoSupplier<InputStream> ioSupplierx = packResources.getResource(this.type, resourceLocation);
                            return ioSupplierx != null ? parseMetadata(ioSupplierx) : ResourceMetadata.EMPTY;
                        };
                    }

                    list.add(new Resource(packResources, ioSupplier, ioSupplier2));
                }
            }

            if (packEntry.isFiltered(id)) {
                string = packEntry.name;
                break;
            }

            if (packEntry.isFiltered(resourceLocation)) {
                bl = true;
            }
        }

        if (list.isEmpty() && string != null) {
            LOGGER.warn("Resource {} not found, but was filtered by pack {}", id, string);
        }

        return Lists.reverse(list);
    }

    private static boolean isMetadata(ResourceLocation id) {
        return id.getPath().endsWith(".mcmeta");
    }

    private static ResourceLocation getResourceLocationFromMetadata(ResourceLocation id) {
        String string = id.getPath().substring(0, id.getPath().length() - ".mcmeta".length());
        return id.withPath(string);
    }

    static ResourceLocation getMetadataLocation(ResourceLocation id) {
        return id.withPath(id.getPath() + ".mcmeta");
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        record ResourceWithSourceAndIndex(PackResources packResources, IoSupplier<InputStream> resource, int packIndex) {
        }

        Map<ResourceLocation, ResourceWithSourceAndIndex> map = new HashMap<>();
        Map<ResourceLocation, ResourceWithSourceAndIndex> map2 = new HashMap<>();
        int i = this.fallbacks.size();

        for (int j = 0; j < i; j++) {
            FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(j);
            packEntry.filterAll(map.keySet());
            packEntry.filterAll(map2.keySet());
            PackResources packResources = packEntry.resources;
            if (packResources != null) {
                int k = j;
                packResources.listResources(this.type, this.namespace, startingPath, (id, supplier) -> {
                    if (isMetadata(id)) {
                        if (allowedPathPredicate.test(getResourceLocationFromMetadata(id))) {
                            map2.put(id, new ResourceWithSourceAndIndex(packResources, supplier, k));
                        }
                    } else if (allowedPathPredicate.test(id)) {
                        map.put(id, new ResourceWithSourceAndIndex(packResources, supplier, k));
                    }
                });
            }
        }

        Map<ResourceLocation, Resource> map3 = Maps.newTreeMap();
        map.forEach((id, result) -> {
            ResourceLocation resourceLocation = getMetadataLocation(id);
            ResourceWithSourceAndIndex lv = map2.get(resourceLocation);
            IoSupplier<ResourceMetadata> ioSupplier;
            if (lv != null && lv.packIndex >= result.packIndex) {
                ioSupplier = convertToMetadata(lv.resource);
            } else {
                ioSupplier = ResourceMetadata.EMPTY_SUPPLIER;
            }

            map3.put(id, createResource(result.packResources, id, result.resource, ioSupplier));
        });
        return map3;
    }

    private IoSupplier<ResourceMetadata> createStackMetadataFinder(ResourceLocation id, int index) {
        return () -> {
            ResourceLocation resourceLocation2 = getMetadataLocation(id);

            for (int j = this.fallbacks.size() - 1; j >= index; j--) {
                FallbackResourceManager.PackEntry packEntry = this.fallbacks.get(j);
                PackResources packResources = packEntry.resources;
                if (packResources != null) {
                    IoSupplier<InputStream> ioSupplier = packResources.getResource(this.type, resourceLocation2);
                    if (ioSupplier != null) {
                        return parseMetadata(ioSupplier);
                    }
                }

                if (packEntry.isFiltered(resourceLocation2)) {
                    break;
                }
            }

            return ResourceMetadata.EMPTY;
        };
    }

    private static IoSupplier<ResourceMetadata> convertToMetadata(IoSupplier<InputStream> supplier) {
        return () -> parseMetadata(supplier);
    }

    private static ResourceMetadata parseMetadata(IoSupplier<InputStream> supplier) throws IOException {
        ResourceMetadata var2;
        try (InputStream inputStream = supplier.get()) {
            var2 = ResourceMetadata.fromJsonStream(inputStream);
        }

        return var2;
    }

    private static void applyPackFiltersToExistingResources(
        FallbackResourceManager.PackEntry pack, Map<ResourceLocation, FallbackResourceManager.EntryStack> idToEntryList
    ) {
        for (FallbackResourceManager.EntryStack entryStack : idToEntryList.values()) {
            if (pack.isFiltered(entryStack.fileLocation)) {
                entryStack.fileSources.clear();
            } else if (pack.isFiltered(entryStack.metadataLocation())) {
                entryStack.metaSources.clear();
            }
        }
    }

    private void listPackResources(
        FallbackResourceManager.PackEntry pack,
        String startingPath,
        Predicate<ResourceLocation> allowedPathPredicate,
        Map<ResourceLocation, FallbackResourceManager.EntryStack> idToEntryList
    ) {
        PackResources packResources = pack.resources;
        if (packResources != null) {
            packResources.listResources(
                this.type,
                this.namespace,
                startingPath,
                (id, supplier) -> {
                    if (isMetadata(id)) {
                        ResourceLocation resourceLocation = getResourceLocationFromMetadata(id);
                        if (!allowedPathPredicate.test(resourceLocation)) {
                            return;
                        }

                        idToEntryList.computeIfAbsent(resourceLocation, FallbackResourceManager.EntryStack::new).metaSources.put(packResources, supplier);
                    } else {
                        if (!allowedPathPredicate.test(id)) {
                            return;
                        }

                        idToEntryList.computeIfAbsent(id, FallbackResourceManager.EntryStack::new)
                            .fileSources
                            .add(new FallbackResourceManager.ResourceWithSource(packResources, supplier));
                    }
                }
            );
        }
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        Map<ResourceLocation, FallbackResourceManager.EntryStack> map = Maps.newHashMap();

        for (FallbackResourceManager.PackEntry packEntry : this.fallbacks) {
            applyPackFiltersToExistingResources(packEntry, map);
            this.listPackResources(packEntry, startingPath, allowedPathPredicate, map);
        }

        TreeMap<ResourceLocation, List<Resource>> treeMap = Maps.newTreeMap();

        for (FallbackResourceManager.EntryStack entryStack : map.values()) {
            if (!entryStack.fileSources.isEmpty()) {
                List<Resource> list = new ArrayList<>();

                for (FallbackResourceManager.ResourceWithSource resourceWithSource : entryStack.fileSources) {
                    PackResources packResources = resourceWithSource.source;
                    IoSupplier<InputStream> ioSupplier = entryStack.metaSources.get(packResources);
                    IoSupplier<ResourceMetadata> ioSupplier2 = ioSupplier != null ? convertToMetadata(ioSupplier) : ResourceMetadata.EMPTY_SUPPLIER;
                    list.add(createResource(packResources, entryStack.fileLocation, resourceWithSource.resource, ioSupplier2));
                }

                treeMap.put(entryStack.fileLocation, list);
            }
        }

        return treeMap;
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.fallbacks.stream().map(pack -> pack.resources).filter(Objects::nonNull);
    }

    static record EntryStack(
        ResourceLocation fileLocation,
        ResourceLocation metadataLocation,
        List<FallbackResourceManager.ResourceWithSource> fileSources,
        Map<PackResources, IoSupplier<InputStream>> metaSources
    ) {
        EntryStack(ResourceLocation id) {
            this(id, FallbackResourceManager.getMetadataLocation(id), new ArrayList<>(), new Object2ObjectArrayMap<>());
        }
    }

    static class LeakedResourceWarningInputStream extends FilterInputStream {
        private final Supplier<String> message;
        private boolean closed;

        public LeakedResourceWarningInputStream(InputStream parent, ResourceLocation id, String packId) {
            super(parent);
            Exception exception = new Exception("Stacktrace");
            this.message = () -> {
                StringWriter stringWriter = new StringWriter();
                exception.printStackTrace(new PrintWriter(stringWriter));
                return "Leaked resource: '" + id + "' loaded from pack: '" + packId + "'\n" + stringWriter;
            };
        }

        @Override
        public void close() throws IOException {
            super.close();
            this.closed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            if (!this.closed) {
                FallbackResourceManager.LOGGER.warn("{}", this.message.get());
            }

            super.finalize();
        }
    }

    static record PackEntry(String name, @Nullable PackResources resources, @Nullable Predicate<ResourceLocation> filter) {
        public void filterAll(Collection<ResourceLocation> ids) {
            if (this.filter != null) {
                ids.removeIf(this.filter);
            }
        }

        public boolean isFiltered(ResourceLocation id) {
            return this.filter != null && this.filter.test(id);
        }
    }

    static record ResourceWithSource(PackResources source, IoSupplier<InputStream> resource) {
    }
}
