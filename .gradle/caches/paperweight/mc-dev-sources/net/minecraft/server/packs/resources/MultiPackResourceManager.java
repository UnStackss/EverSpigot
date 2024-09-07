package net.minecraft.server.packs.resources;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;

public class MultiPackResourceManager implements CloseableResourceManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, FallbackResourceManager> namespacedManagers;
    private final List<PackResources> packs;

    public MultiPackResourceManager(PackType type, List<PackResources> packs) {
        this.packs = List.copyOf(packs);
        Map<String, FallbackResourceManager> map = new HashMap<>();
        List<String> list = packs.stream().flatMap(pack -> pack.getNamespaces(type).stream()).distinct().toList();

        for (PackResources packResources : packs) {
            ResourceFilterSection resourceFilterSection = this.getPackFilterSection(packResources);
            Set<String> set = packResources.getNamespaces(type);
            Predicate<ResourceLocation> predicate = resourceFilterSection != null ? id -> resourceFilterSection.isPathFiltered(id.getPath()) : null;

            for (String string : list) {
                boolean bl = set.contains(string);
                boolean bl2 = resourceFilterSection != null && resourceFilterSection.isNamespaceFiltered(string);
                if (bl || bl2) {
                    FallbackResourceManager fallbackResourceManager = map.get(string);
                    if (fallbackResourceManager == null) {
                        fallbackResourceManager = new FallbackResourceManager(type, string);
                        map.put(string, fallbackResourceManager);
                    }

                    if (bl && bl2) {
                        fallbackResourceManager.push(packResources, predicate);
                    } else if (bl) {
                        fallbackResourceManager.push(packResources);
                    } else {
                        fallbackResourceManager.pushFilterOnly(packResources.packId(), predicate);
                    }
                }
            }
        }

        this.namespacedManagers = map;
    }

    @Nullable
    private ResourceFilterSection getPackFilterSection(PackResources pack) {
        try {
            return pack.getMetadataSection(ResourceFilterSection.TYPE);
        } catch (IOException var3) {
            LOGGER.error("Failed to get filter section from pack {}", pack.packId());
            return null;
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespacedManagers.keySet();
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation id) {
        ResourceManager resourceManager = this.namespacedManagers.get(id.getNamespace());
        return resourceManager != null ? resourceManager.getResource(id) : Optional.empty();
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation id) {
        ResourceManager resourceManager = this.namespacedManagers.get(id.getNamespace());
        return resourceManager != null ? resourceManager.getResourceStack(id) : List.of();
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        checkTrailingDirectoryPath(startingPath);
        Map<ResourceLocation, Resource> map = new TreeMap<>();

        for (FallbackResourceManager fallbackResourceManager : this.namespacedManagers.values()) {
            map.putAll(fallbackResourceManager.listResources(startingPath, allowedPathPredicate));
        }

        return map;
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String startingPath, Predicate<ResourceLocation> allowedPathPredicate) {
        checkTrailingDirectoryPath(startingPath);
        Map<ResourceLocation, List<Resource>> map = new TreeMap<>();

        for (FallbackResourceManager fallbackResourceManager : this.namespacedManagers.values()) {
            map.putAll(fallbackResourceManager.listResourceStacks(startingPath, allowedPathPredicate));
        }

        return map;
    }

    private static void checkTrailingDirectoryPath(String startingPath) {
        if (startingPath.endsWith("/")) {
            throw new IllegalArgumentException("Trailing slash in path " + startingPath);
        }
    }

    @Override
    public Stream<PackResources> listPacks() {
        return this.packs.stream();
    }

    @Override
    public void close() {
        this.packs.forEach(PackResources::close);
    }
}
