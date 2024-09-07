package net.minecraft.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;

public class LayeredRegistryAccess<T> {
    private final List<T> keys;
    private final List<RegistryAccess.Frozen> values;
    private final RegistryAccess.Frozen composite;

    public LayeredRegistryAccess(List<T> types) {
        this(types, Util.make(() -> {
            RegistryAccess.Frozen[] frozens = new RegistryAccess.Frozen[types.size()];
            Arrays.fill(frozens, RegistryAccess.EMPTY);
            return Arrays.asList(frozens);
        }));
    }

    private LayeredRegistryAccess(List<T> types, List<RegistryAccess.Frozen> registryManagers) {
        this.keys = List.copyOf(types);
        this.values = List.copyOf(registryManagers);
        this.composite = new RegistryAccess.ImmutableRegistryAccess(collectRegistries(registryManagers.stream())).freeze();
    }

    private int getLayerIndexOrThrow(T type) {
        int i = this.keys.indexOf(type);
        if (i == -1) {
            throw new IllegalStateException("Can't find " + type + " inside " + this.keys);
        } else {
            return i;
        }
    }

    public RegistryAccess.Frozen getLayer(T index) {
        int i = this.getLayerIndexOrThrow(index);
        return this.values.get(i);
    }

    public RegistryAccess.Frozen getAccessForLoading(T type) {
        int i = this.getLayerIndexOrThrow(type);
        return this.getCompositeAccessForLayers(0, i);
    }

    public RegistryAccess.Frozen getAccessFrom(T type) {
        int i = this.getLayerIndexOrThrow(type);
        return this.getCompositeAccessForLayers(i, this.values.size());
    }

    private RegistryAccess.Frozen getCompositeAccessForLayers(int startIndex, int endIndex) {
        return new RegistryAccess.ImmutableRegistryAccess(collectRegistries(this.values.subList(startIndex, endIndex).stream())).freeze();
    }

    public LayeredRegistryAccess<T> replaceFrom(T type, RegistryAccess.Frozen... registryManagers) {
        return this.replaceFrom(type, Arrays.asList(registryManagers));
    }

    public LayeredRegistryAccess<T> replaceFrom(T type, List<RegistryAccess.Frozen> registryManagers) {
        int i = this.getLayerIndexOrThrow(type);
        if (registryManagers.size() > this.values.size() - i) {
            throw new IllegalStateException("Too many values to replace");
        } else {
            List<RegistryAccess.Frozen> list = new ArrayList<>();

            for (int j = 0; j < i; j++) {
                list.add(this.values.get(j));
            }

            list.addAll(registryManagers);

            while (list.size() < this.values.size()) {
                list.add(RegistryAccess.EMPTY);
            }

            return new LayeredRegistryAccess<>(this.keys, list);
        }
    }

    public RegistryAccess.Frozen compositeAccess() {
        return this.composite;
    }

    private static Map<ResourceKey<? extends Registry<?>>, Registry<?>> collectRegistries(Stream<? extends RegistryAccess> registryManagers) {
        Map<ResourceKey<? extends Registry<?>>, Registry<?>> map = new HashMap<>();
        registryManagers.forEach(registryManager -> registryManager.registries().forEach(entry -> {
                if (map.put(entry.key(), entry.value()) != null) {
                    throw new IllegalStateException("Duplicated registry " + entry.key());
                }
            }));
        return map;
    }
}
