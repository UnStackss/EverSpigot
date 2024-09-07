package net.minecraft.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DependencySorter<K, V extends DependencySorter.Entry<K>> {
    private final Map<K, V> contents = new HashMap<>();

    public DependencySorter<K, V> addEntry(K key, V value) {
        this.contents.put(key, value);
        return this;
    }

    private void visitDependenciesAndElement(Multimap<K, K> parentChild, Set<K> visited, K rootKey, BiConsumer<K, V> callback) {
        if (visited.add(rootKey)) {
            parentChild.get(rootKey).forEach(child -> this.visitDependenciesAndElement(parentChild, visited, (K)child, callback));
            V entry = this.contents.get(rootKey);
            if (entry != null) {
                callback.accept(rootKey, entry);
            }
        }
    }

    private static <K> boolean isCyclic(Multimap<K, K> dependencies, K key, K dependency) {
        Collection<K> collection = dependencies.get(dependency);
        return collection.contains(key) || collection.stream().anyMatch(subdependency -> isCyclic(dependencies, key, (K)subdependency));
    }

    private static <K> void addDependencyIfNotCyclic(Multimap<K, K> dependencies, K key, K dependency) {
        if (!isCyclic(dependencies, key, dependency)) {
            dependencies.put(key, dependency);
        }
    }

    public void orderByDependencies(BiConsumer<K, V> callback) {
        Multimap<K, K> multimap = HashMultimap.create();
        this.contents.forEach((key, value) -> value.visitRequiredDependencies(dependency -> addDependencyIfNotCyclic(multimap, (K)key, dependency)));
        this.contents.forEach((key, value) -> value.visitOptionalDependencies(dependency -> addDependencyIfNotCyclic(multimap, (K)key, dependency)));
        Set<K> set = new HashSet<>();
        this.contents.keySet().forEach(key -> this.visitDependenciesAndElement(multimap, set, (K)key, callback));
    }

    public interface Entry<K> {
        void visitRequiredDependencies(Consumer<K> callback);

        void visitOptionalDependencies(Consumer<K> callback);
    }
}
