package net.minecraft.world.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;

public record EitherHolder<T>(Optional<Holder<T>> holder, ResourceKey<T> key) {
    public EitherHolder(Holder<T> entry) {
        this(Optional.of(entry), entry.unwrapKey().orElseThrow());
    }

    public EitherHolder(ResourceKey<T> key) {
        this(Optional.empty(), key);
    }

    public static <T> Codec<EitherHolder<T>> codec(ResourceKey<Registry<T>> registryRef, Codec<Holder<T>> entryCodec) {
        return Codec.either(
                entryCodec,
                ResourceKey.codec(registryRef).comapFlatMap(resourceKey -> DataResult.error(() -> "Cannot parse as key without registry"), Function.identity())
            )
            .xmap(EitherHolder::fromEither, EitherHolder::asEither);
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, EitherHolder<T>> streamCodec(
        ResourceKey<Registry<T>> registryRef, StreamCodec<RegistryFriendlyByteBuf, Holder<T>> entryPacketCodec
    ) {
        return StreamCodec.composite(
            ByteBufCodecs.either(entryPacketCodec, ResourceKey.streamCodec(registryRef)), EitherHolder::asEither, EitherHolder::fromEither
        );
    }

    public Either<Holder<T>, ResourceKey<T>> asEither() {
        return this.holder.map(Either::left).orElseGet(() -> Either.right(this.key));
    }

    public static <T> EitherHolder<T> fromEither(Either<Holder<T>, ResourceKey<T>> entryOrKey) {
        return entryOrKey.map(EitherHolder::new, EitherHolder::new);
    }

    public Optional<T> unwrap(Registry<T> registry) {
        return this.holder.map(Holder::value).or(() -> registry.getOptional(this.key));
    }

    public Optional<Holder<T>> unwrap(HolderLookup.Provider registryLookup) {
        return this.holder.or(() -> registryLookup.lookupOrThrow(this.key.registryKey()).get(this.key));
    }
}
