package net.minecraft.server.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record Filterable<T>(T raw, Optional<T> filtered) {
    public static <T> Codec<Filterable<T>> codec(Codec<T> baseCodec) {
        Codec<Filterable<T>> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                        baseCodec.fieldOf("raw").forGetter(Filterable::raw), baseCodec.optionalFieldOf("filtered").forGetter(Filterable::filtered)
                    )
                    .apply(instance, Filterable::new)
        );
        Codec<Filterable<T>> codec2 = baseCodec.xmap(Filterable::passThrough, Filterable::raw);
        return Codec.withAlternative(codec, codec2);
    }

    public static <B extends ByteBuf, T> StreamCodec<B, Filterable<T>> streamCodec(StreamCodec<B, T> basePacketCodec) {
        return StreamCodec.composite(basePacketCodec, Filterable::raw, basePacketCodec.apply(ByteBufCodecs::optional), Filterable::filtered, Filterable::new);
    }

    public static <T> Filterable<T> passThrough(T raw) {
        return new Filterable<>(raw, Optional.empty());
    }

    public static Filterable<String> from(FilteredText message) {
        return new Filterable<>(message.raw(), message.isFiltered() ? Optional.of(message.filteredOrEmpty()) : Optional.empty());
    }

    public T get(boolean shouldFilter) {
        return shouldFilter ? this.filtered.orElse(this.raw) : this.raw;
    }

    public <U> Filterable<U> map(Function<T, U> mapper) {
        return new Filterable<>(mapper.apply(this.raw), this.filtered.map(mapper));
    }

    public <U> Optional<Filterable<U>> resolve(Function<T, Optional<U>> resolver) {
        Optional<U> optional = resolver.apply(this.raw);
        if (optional.isEmpty()) {
            return Optional.empty();
        } else if (this.filtered.isPresent()) {
            Optional<U> optional2 = resolver.apply(this.filtered.get());
            return optional2.isEmpty() ? Optional.empty() : Optional.of(new Filterable<>(optional.get(), optional2));
        } else {
            return Optional.of(new Filterable<>(optional.get(), Optional.empty()));
        }
    }
}
