package net.minecraft.server.packs;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

public class CompositePackResources implements PackResources {
    private final PackResources primaryPackResources;
    private final List<PackResources> packResourcesStack;

    public CompositePackResources(PackResources base, List<PackResources> overlays) {
        this.primaryPackResources = base;
        List<PackResources> list = new ArrayList<>(overlays.size() + 1);
        list.addAll(Lists.reverse(overlays));
        list.add(base);
        this.packResourcesStack = List.copyOf(list);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... segments) {
        return this.primaryPackResources.getRootResource(segments);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        for (PackResources packResources : this.packResourcesStack) {
            IoSupplier<InputStream> ioSupplier = packResources.getResource(type, id);
            if (ioSupplier != null) {
                return ioSupplier;
            }
        }

        return null;
    }

    @Override
    public void listResources(PackType type, String namespace, String prefix, PackResources.ResourceOutput consumer) {
        Map<ResourceLocation, IoSupplier<InputStream>> map = new HashMap<>();

        for (PackResources packResources : this.packResourcesStack) {
            packResources.listResources(type, namespace, prefix, map::putIfAbsent);
        }

        map.forEach(consumer);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> set = new HashSet<>();

        for (PackResources packResources : this.packResourcesStack) {
            set.addAll(packResources.getNamespaces(type));
        }

        return set;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) throws IOException {
        return this.primaryPackResources.getMetadataSection(metaReader);
    }

    @Override
    public PackLocationInfo location() {
        return this.primaryPackResources.location();
    }

    @Override
    public void close() {
        this.packResourcesStack.forEach(PackResources::close);
    }
}
