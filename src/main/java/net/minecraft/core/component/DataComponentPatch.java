package net.minecraft.core.component;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;

public final class DataComponentPatch {

    public static final DataComponentPatch EMPTY = new DataComponentPatch(Reference2ObjectMaps.emptyMap());
    public static final Codec<DataComponentPatch> CODEC = Codec.dispatchedMap(DataComponentPatch.PatchKey.CODEC, DataComponentPatch.PatchKey::valueCodec).xmap((map) -> {
        if (map.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(map.size());
            Iterator iterator = map.entrySet().iterator();

            while (iterator.hasNext()) {
                Entry<DataComponentPatch.PatchKey, ?> entry = (Entry) iterator.next();
                DataComponentPatch.PatchKey datacomponentpatch_b = (DataComponentPatch.PatchKey) entry.getKey();

                if (datacomponentpatch_b.removed()) {
                    reference2objectmap.put(datacomponentpatch_b.type(), Optional.empty());
                } else {
                    reference2objectmap.put(datacomponentpatch_b.type(), Optional.of(entry.getValue()));
                }
            }

            return new DataComponentPatch(reference2objectmap);
        }
    }, (datacomponentpatch) -> {
        Reference2ObjectMap<DataComponentPatch.PatchKey, Object> reference2objectmap = new Reference2ObjectArrayMap(datacomponentpatch.map.size());
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

        while (objectiterator.hasNext()) {
            Entry<DataComponentType<?>, Optional<?>> entry = (Entry) objectiterator.next();
            DataComponentType<?> datacomponenttype = (DataComponentType) entry.getKey();

            if (!datacomponenttype.isTransient()) {
                Optional<?> optional = (Optional) entry.getValue();

                if (optional.isPresent()) {
                    reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, false), optional.get());
                } else {
                    reference2objectmap.put(new DataComponentPatch.PatchKey(datacomponenttype, true), Unit.INSTANCE);
                }
            }
        }

        return (Reference2ObjectMap) reference2objectmap; // CraftBukkit - decompile error
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, DataComponentPatch>() {
        public DataComponentPatch decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
            int i = registryfriendlybytebuf.readVarInt();
            int j = registryfriendlybytebuf.readVarInt();

            if (i == 0 && j == 0) {
                return DataComponentPatch.EMPTY;
            } else {
                int k = i + j;
                Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(Math.min(k, 65536));

                DataComponentType datacomponenttype;
                int l;

                for (l = 0; l < i; ++l) {
                    datacomponenttype = (DataComponentType) DataComponentType.STREAM_CODEC.decode(registryfriendlybytebuf);
                    Object object = datacomponenttype.streamCodec().decode(registryfriendlybytebuf);

                    reference2objectmap.put(datacomponenttype, Optional.of(object));
                }

                for (l = 0; l < j; ++l) {
                    datacomponenttype = (DataComponentType) DataComponentType.STREAM_CODEC.decode(registryfriendlybytebuf);
                    reference2objectmap.put(datacomponenttype, Optional.empty());
                }

                return new DataComponentPatch(reference2objectmap);
            }
        }

        public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, DataComponentPatch datacomponentpatch) {
            if (datacomponentpatch.isEmpty()) {
                registryfriendlybytebuf.writeVarInt(0);
                registryfriendlybytebuf.writeVarInt(0);
            } else {
                int i = 0;
                int j = 0;
                ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry it_unimi_dsi_fastutil_objects_reference2objectmap_entry;

                while (objectiterator.hasNext()) {
                    it_unimi_dsi_fastutil_objects_reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                    if (((Optional) it_unimi_dsi_fastutil_objects_reference2objectmap_entry.getValue()).isPresent()) {
                        ++i;
                    } else {
                        ++j;
                    }
                }

                registryfriendlybytebuf.writeVarInt(i);
                registryfriendlybytebuf.writeVarInt(j);
                objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                while (objectiterator.hasNext()) {
                    it_unimi_dsi_fastutil_objects_reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                    Optional<?> optional = (Optional) it_unimi_dsi_fastutil_objects_reference2objectmap_entry.getValue();

                    if (optional.isPresent()) {
                        DataComponentType<?> datacomponenttype = (DataComponentType) it_unimi_dsi_fastutil_objects_reference2objectmap_entry.getKey();

                        DataComponentType.STREAM_CODEC.encode(registryfriendlybytebuf, datacomponenttype);
                        encodeComponent(registryfriendlybytebuf, datacomponenttype, optional.get());
                    }
                }

                objectiterator = Reference2ObjectMaps.fastIterable(datacomponentpatch.map).iterator();

                while (objectiterator.hasNext()) {
                    it_unimi_dsi_fastutil_objects_reference2objectmap_entry = (it.unimi.dsi.fastutil.objects.Reference2ObjectMap.Entry) objectiterator.next();
                    if (((Optional) it_unimi_dsi_fastutil_objects_reference2objectmap_entry.getValue()).isEmpty()) {
                        DataComponentType<?> datacomponenttype1 = (DataComponentType) it_unimi_dsi_fastutil_objects_reference2objectmap_entry.getKey();

                        DataComponentType.STREAM_CODEC.encode(registryfriendlybytebuf, datacomponenttype1);
                    }
                }

            }
        }

        private static <T> void encodeComponent(RegistryFriendlyByteBuf buf, DataComponentType<T> type, Object value) {
            // Paper start - codec errors of random anonymous classes are useless
            try {
            type.streamCodec().encode(buf, (T) value); // CraftBukkit - decompile error
            } catch (final Exception e) {
                throw new RuntimeException("Error encoding component " + type, e);
            }
            // Paper end - codec errors of random anonymous classes are useless
        }
    };
    private static final String REMOVED_PREFIX = "!";
    final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map;

    DataComponentPatch(Reference2ObjectMap<DataComponentType<?>, Optional<?>> changedComponents) {
        this.map = changedComponents;
    }

    public static DataComponentPatch.Builder builder() {
        return new DataComponentPatch.Builder();
    }

    @Nullable
    public <T> Optional<? extends T> get(DataComponentType<? extends T> type) {
        return (Optional) this.map.get(type);
    }

    public Set<Entry<DataComponentType<?>, Optional<?>>> entrySet() {
        return this.map.entrySet();
    }

    public int size() {
        return this.map.size();
    }

    public DataComponentPatch forget(Predicate<DataComponentType<?>> removedTypePredicate) {
        if (this.isEmpty()) {
            return DataComponentPatch.EMPTY;
        } else {
            Reference2ObjectMap<DataComponentType<?>, Optional<?>> reference2objectmap = new Reference2ObjectArrayMap(this.map);

            reference2objectmap.keySet().removeIf(removedTypePredicate);
            return reference2objectmap.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(reference2objectmap);
        }
    }

    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    public DataComponentPatch.SplitResult split() {
        if (this.isEmpty()) {
            return DataComponentPatch.SplitResult.EMPTY;
        } else {
            DataComponentMap.Builder datacomponentmap_a = DataComponentMap.builder();
            Set<DataComponentType<?>> set = Sets.newIdentityHashSet();

            this.map.forEach((datacomponenttype, optional) -> {
                if (optional.isPresent()) {
                    datacomponentmap_a.setUnchecked(datacomponenttype, optional.get());
                } else {
                    set.add(datacomponenttype);
                }

            });
            return new DataComponentPatch.SplitResult(datacomponentmap_a.build(), set);
        }
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            boolean flag;

            if (object instanceof DataComponentPatch) {
                DataComponentPatch datacomponentpatch = (DataComponentPatch) object;

                if (this.map.equals(datacomponentpatch.map)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }
    }

    public int hashCode() {
        return this.map.hashCode();
    }

    public String toString() {
        return DataComponentPatch.toString(this.map);
    }

    static String toString(Reference2ObjectMap<DataComponentType<?>, Optional<?>> changes) {
        StringBuilder stringbuilder = new StringBuilder();

        stringbuilder.append('{');
        boolean flag = true;
        ObjectIterator objectiterator = Reference2ObjectMaps.fastIterable(changes).iterator();

        while (objectiterator.hasNext()) {
            Entry<DataComponentType<?>, Optional<?>> entry = (Entry) objectiterator.next();

            if (flag) {
                flag = false;
            } else {
                stringbuilder.append(", ");
            }

            Optional<?> optional = (Optional) entry.getValue();

            if (optional.isPresent()) {
                stringbuilder.append(entry.getKey());
                stringbuilder.append("=>");
                stringbuilder.append(optional.get());
            } else {
                stringbuilder.append("!");
                stringbuilder.append(entry.getKey());
            }
        }

        stringbuilder.append('}');
        return stringbuilder.toString();
    }

    public static class Builder {

        private final Reference2ObjectMap<DataComponentType<?>, Optional<?>> map = new Reference2ObjectArrayMap();

        Builder() {}

        // CraftBukkit start
        public void copy(DataComponentPatch orig) {
            this.map.putAll(orig.map);
        }

        public void clear(DataComponentType<?> type) {
            this.map.remove(type);
        }

        public boolean isSet(DataComponentType<?> type) {
            return this.map.containsKey(type);
        }

        public boolean isEmpty() {
            return this.map.isEmpty();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (object instanceof DataComponentPatch.Builder patch) {
                return this.map.equals(patch.map);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return this.map.hashCode();
        }
        // CraftBukkit end

        public <T> DataComponentPatch.Builder set(DataComponentType<T> type, T value) {
            this.map.put(type, Optional.of(value));
            return this;
        }

        public <T> DataComponentPatch.Builder remove(DataComponentType<T> type) {
            this.map.put(type, Optional.empty());
            return this;
        }

        public <T> DataComponentPatch.Builder set(TypedDataComponent<T> component) {
            return this.set(component.type(), component.value());
        }

        public DataComponentPatch build() {
            return this.map.isEmpty() ? DataComponentPatch.EMPTY : new DataComponentPatch(this.map);
        }
    }

    public static record SplitResult(DataComponentMap added, Set<DataComponentType<?>> removed) {

        public static final DataComponentPatch.SplitResult EMPTY = new DataComponentPatch.SplitResult(DataComponentMap.EMPTY, Set.of());
    }

    private static record PatchKey(DataComponentType<?> type, boolean removed) {

        public static final Codec<DataComponentPatch.PatchKey> CODEC = Codec.STRING.flatXmap((s) -> {
            boolean flag = s.startsWith("!");

            if (flag) {
                s = s.substring("!".length());
            }

            ResourceLocation minecraftkey = ResourceLocation.tryParse(s);
            DataComponentType<?> datacomponenttype = (DataComponentType) BuiltInRegistries.DATA_COMPONENT_TYPE.get(minecraftkey);

            return datacomponenttype == null ? DataResult.error(() -> {
                return "No component with type: '" + String.valueOf(minecraftkey) + "'";
            }) : (datacomponenttype.isTransient() ? DataResult.error(() -> {
                return "'" + String.valueOf(minecraftkey) + "' is not a persistent component";
            }) : DataResult.success(new DataComponentPatch.PatchKey(datacomponenttype, flag)));
        }, (datacomponentpatch_b) -> {
            DataComponentType<?> datacomponenttype = datacomponentpatch_b.type();
            ResourceLocation minecraftkey = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(datacomponenttype);

            return minecraftkey == null ? DataResult.error(() -> {
                return "Unregistered component: " + String.valueOf(datacomponenttype);
            }) : DataResult.success(datacomponentpatch_b.removed() ? "!" + String.valueOf(minecraftkey) : minecraftkey.toString());
        });

        public Codec<?> valueCodec() {
            return this.removed ? Codec.EMPTY.codec() : this.type.codecOrThrow();
        }
    }
}
