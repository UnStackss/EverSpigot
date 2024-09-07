package net.minecraft.core.component;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

public final class PatchedDataComponentMap implements DataComponentMap {
    private final DataComponentMap prototype;
    private Reference2ObjectMap<DataComponentType<?>, Optional<?>> patch;
    private boolean copyOnWrite;

    public PatchedDataComponentMap(DataComponentMap baseComponents) {
        this(baseComponents, Reference2ObjectMaps.emptyMap(), true);
    }

    private PatchedDataComponentMap(
        DataComponentMap baseComponents, Reference2ObjectMap<DataComponentType<?>, Optional<?>> changedComponents, boolean copyOnWrite
    ) {
        this.prototype = baseComponents;
        this.patch = changedComponents;
        this.copyOnWrite = copyOnWrite;
    }

    public static PatchedDataComponentMap fromPatch(DataComponentMap baseComponents, DataComponentPatch changes) {
        if (isPatchSanitized(baseComponents, changes.map)) {
            return new PatchedDataComponentMap(baseComponents, changes.map, true);
        } else {
            PatchedDataComponentMap patchedDataComponentMap = new PatchedDataComponentMap(baseComponents);
            patchedDataComponentMap.applyPatch(changes);
            return patchedDataComponentMap;
        }
    }

    private static boolean isPatchSanitized(DataComponentMap baseComponents, Reference2ObjectMap<DataComponentType<?>, Optional<?>> changedComponents) {
        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(changedComponents)) {
            Object object = baseComponents.get(entry.getKey());
            Optional<?> optional = entry.getValue();
            if (optional.isPresent() && optional.get().equals(object)) {
                return false;
            }

            if (optional.isEmpty() && object == null) {
                return false;
            }
        }

        return true;
    }

    @Nullable
    @Override
    public <T> T get(DataComponentType<? extends T> type) {
        Optional<? extends T> optional = (Optional<? extends T>)this.patch.get(type);
        return (T)(optional != null ? optional.orElse(null) : this.prototype.get(type));
    }

    @Nullable
    public <T> T set(DataComponentType<? super T> type, @Nullable T value) {
        this.ensureMapOwnership();
        T object = this.prototype.get((DataComponentType<? extends T>)type);
        Optional<T> optional;
        if (Objects.equals(value, object)) {
            optional = (Optional<T>)this.patch.remove(type);
        } else {
            optional = (Optional<T>)this.patch.put(type, Optional.ofNullable(value));
        }

        return optional != null ? optional.orElse(object) : object;
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> type) {
        this.ensureMapOwnership();
        T object = this.prototype.get(type);
        Optional<? extends T> optional;
        if (object != null) {
            optional = (Optional<? extends T>)this.patch.put(type, Optional.empty());
        } else {
            optional = (Optional<? extends T>)this.patch.remove(type);
        }

        return (T)(optional != null ? optional.orElse(null) : object);
    }

    public void applyPatch(DataComponentPatch changes) {
        this.ensureMapOwnership();

        for (Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(changes.map)) {
            this.applyPatch(entry.getKey(), entry.getValue());
        }
    }

    private void applyPatch(DataComponentType<?> type, Optional<?> optional) {
        Object object = this.prototype.get(type);
        if (optional.isPresent()) {
            if (optional.get().equals(object)) {
                this.patch.remove(type);
            } else {
                this.patch.put(type, optional);
            }
        } else if (object != null) {
            this.patch.put(type, Optional.empty());
        } else {
            this.patch.remove(type);
        }
    }

    public void restorePatch(DataComponentPatch changes) {
        this.ensureMapOwnership();
        this.patch.clear();
        this.patch.putAll(changes.map);
    }

    public void setAll(DataComponentMap components) {
        for (TypedDataComponent<?> typedDataComponent : components) {
            typedDataComponent.applyTo(this);
        }
    }

    private void ensureMapOwnership() {
        if (this.copyOnWrite) {
            this.patch = new Reference2ObjectArrayMap<>(this.patch);
            this.copyOnWrite = false;
        }
    }

    @Override
    public Set<DataComponentType<?>> keySet() {
        if (this.patch.isEmpty()) {
            return this.prototype.keySet();
        } else {
            Set<DataComponentType<?>> set = new ReferenceArraySet<>(this.prototype.keySet());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                Optional<?> optional = entry.getValue();
                if (optional.isPresent()) {
                    set.add(entry.getKey());
                } else {
                    set.remove(entry.getKey());
                }
            }

            return set;
        }
    }

    @Override
    public Iterator<TypedDataComponent<?>> iterator() {
        if (this.patch.isEmpty()) {
            return this.prototype.iterator();
        } else {
            List<TypedDataComponent<?>> list = new ArrayList<>(this.patch.size() + this.prototype.size());

            for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(
                this.patch
            )) {
                if (entry.getValue().isPresent()) {
                    list.add(TypedDataComponent.createUnchecked(entry.getKey(), entry.getValue().get()));
                }
            }

            for (TypedDataComponent<?> typedDataComponent : this.prototype) {
                if (!this.patch.containsKey(typedDataComponent.type())) {
                    list.add(typedDataComponent);
                }
            }

            return list.iterator();
        }
    }

    @Override
    public int size() {
        int i = this.prototype.size();

        for (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry<DataComponentType<?>, Optional<?>> entry : Reference2ObjectMaps.fastIterable(this.patch)) {
            boolean bl = entry.getValue().isPresent();
            boolean bl2 = this.prototype.has(entry.getKey());
            if (bl != bl2) {
                i += bl ? 1 : -1;
            }
        }

        return i;
    }

    public DataComponentPatch asPatch() {
        if (this.patch.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            this.copyOnWrite = true;
            return new DataComponentPatch(this.patch);
        }
    }

    public PatchedDataComponentMap copy() {
        this.copyOnWrite = true;
        return new PatchedDataComponentMap(this.prototype, this.patch, true);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof PatchedDataComponentMap patchedDataComponentMap
                && this.prototype.equals(patchedDataComponentMap.prototype)
                && this.patch.equals(patchedDataComponentMap.patch)) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.prototype.hashCode() + this.patch.hashCode() * 31;
    }

    @Override
    public String toString() {
        return "{" + this.stream().map(TypedDataComponent::toString).collect(Collectors.joining(", ")) + "}";
    }
}
