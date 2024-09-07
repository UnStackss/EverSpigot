package net.minecraft.resources;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;

public class HolderSetCodec<E> implements Codec<HolderSet<E>> {
    private final ResourceKey<? extends Registry<E>> registryKey;
    private final Codec<Holder<E>> elementCodec;
    private final Codec<List<Holder<E>>> homogenousListCodec;
    private final Codec<Either<TagKey<E>, List<Holder<E>>>> registryAwareCodec;

    private static <E> Codec<List<Holder<E>>> homogenousList(Codec<Holder<E>> entryCodec, boolean alwaysSerializeAsList) {
        Codec<List<Holder<E>>> codec = entryCodec.listOf().validate(ExtraCodecs.ensureHomogenous(Holder::kind));
        return alwaysSerializeAsList
            ? codec
            : Codec.either(codec, entryCodec)
                .xmap(
                    either -> either.map(entries -> entries, List::of),
                    entries -> entries.size() == 1 ? Either.right(entries.get(0)) : Either.left((List<Holder<E>>)entries)
                );
    }

    public static <E> Codec<HolderSet<E>> create(ResourceKey<? extends Registry<E>> registryRef, Codec<Holder<E>> entryCodec, boolean alwaysSerializeAsList) {
        return new HolderSetCodec<>(registryRef, entryCodec, alwaysSerializeAsList);
    }

    private HolderSetCodec(ResourceKey<? extends Registry<E>> registry, Codec<Holder<E>> entryCodec, boolean alwaysSerializeAsList) {
        this.registryKey = registry;
        this.elementCodec = entryCodec;
        this.homogenousListCodec = homogenousList(entryCodec, alwaysSerializeAsList);
        this.registryAwareCodec = Codec.either(TagKey.hashedCodec(registry), this.homogenousListCodec);
    }

    public <T> DataResult<Pair<HolderSet<E>, T>> decode(DynamicOps<T> dynamicOps, T object) {
        if (dynamicOps instanceof RegistryOps<T> registryOps) {
            Optional<HolderGetter<E>> optional = registryOps.getter(this.registryKey);
            if (optional.isPresent()) {
                HolderGetter<E> holderGetter = optional.get();
                return this.registryAwareCodec
                    .decode(dynamicOps, object)
                    .flatMap(
                        pair -> {
                            DataResult<HolderSet<E>> dataResult = pair.getFirst()
                                .map(
                                    tag -> lookupTag(holderGetter, (TagKey<E>)tag),
                                    entries -> DataResult.success(HolderSet.direct((List<? extends Holder<E>>)entries))
                                );
                            return dataResult.map(entries -> Pair.of((HolderSet<E>)entries, (T)pair.getSecond()));
                        }
                    );
            }
        }

        return this.decodeWithoutRegistry(dynamicOps, object);
    }

    private static <E> DataResult<HolderSet<E>> lookupTag(HolderGetter<E> registry, TagKey<E> tag) {
        return registry.get(tag)
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Missing tag: '" + tag.location() + "' in '" + tag.registry().location() + "'"));
    }

    public <T> DataResult<T> encode(HolderSet<E> holderSet, DynamicOps<T> dynamicOps, T object) {
        if (dynamicOps instanceof RegistryOps<T> registryOps) {
            Optional<HolderOwner<E>> optional = registryOps.owner(this.registryKey);
            if (optional.isPresent()) {
                if (!holderSet.canSerializeIn(optional.get())) {
                    return DataResult.error(() -> "HolderSet " + holderSet + " is not valid in current registry set");
                }

                return this.registryAwareCodec.encode(holderSet.unwrap().mapRight(List::copyOf), dynamicOps, object);
            }
        }

        return this.encodeWithoutRegistry(holderSet, dynamicOps, object);
    }

    private <T> DataResult<Pair<HolderSet<E>, T>> decodeWithoutRegistry(DynamicOps<T> ops, T input) {
        return this.elementCodec.listOf().decode(ops, input).flatMap(pair -> {
            List<Holder.Direct<E>> list = new ArrayList<>();

            for (Holder<E> holder : pair.getFirst()) {
                if (!(holder instanceof Holder.Direct<E> direct)) {
                    return DataResult.error(() -> "Can't decode element " + holder + " without registry");
                }

                list.add(direct);
            }

            return DataResult.success(new Pair<>(HolderSet.direct(list), pair.getSecond()));
        });
    }

    private <T> DataResult<T> encodeWithoutRegistry(HolderSet<E> entryList, DynamicOps<T> ops, T prefix) {
        return this.homogenousListCodec.encode(entryList.stream().toList(), ops, prefix);
    }
}
