package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class IntRange {
    private static final Codec<IntRange> RECORD_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    NumberProviders.CODEC.optionalFieldOf("min").forGetter(operator -> Optional.ofNullable(operator.min)),
                    NumberProviders.CODEC.optionalFieldOf("max").forGetter(operator -> Optional.ofNullable(operator.max))
                )
                .apply(instance, IntRange::new)
    );
    public static final Codec<IntRange> CODEC = Codec.either(Codec.INT, RECORD_CODEC)
        .xmap(either -> either.map(IntRange::exact, Function.identity()), operator -> {
            OptionalInt optionalInt = operator.unpackExact();
            return optionalInt.isPresent() ? Either.left(optionalInt.getAsInt()) : Either.right(operator);
        });
    @Nullable
    private final NumberProvider min;
    @Nullable
    private final NumberProvider max;
    private final IntRange.IntLimiter limiter;
    private final IntRange.IntChecker predicate;

    public Set<LootContextParam<?>> getReferencedContextParams() {
        Builder<LootContextParam<?>> builder = ImmutableSet.builder();
        if (this.min != null) {
            builder.addAll(this.min.getReferencedContextParams());
        }

        if (this.max != null) {
            builder.addAll(this.max.getReferencedContextParams());
        }

        return builder.build();
    }

    private IntRange(Optional<NumberProvider> min, Optional<NumberProvider> max) {
        this(min.orElse(null), max.orElse(null));
    }

    private IntRange(@Nullable NumberProvider min, @Nullable NumberProvider max) {
        this.min = min;
        this.max = max;
        if (min == null) {
            if (max == null) {
                this.limiter = (context, value) -> value;
                this.predicate = (context, value) -> true;
            } else {
                this.limiter = (context, value) -> Math.min(max.getInt(context), value);
                this.predicate = (context, value) -> value <= max.getInt(context);
            }
        } else if (max == null) {
            this.limiter = (context, value) -> Math.max(min.getInt(context), value);
            this.predicate = (context, value) -> value >= min.getInt(context);
        } else {
            this.limiter = (context, value) -> Mth.clamp(value, min.getInt(context), max.getInt(context));
            this.predicate = (context, value) -> value >= min.getInt(context) && value <= max.getInt(context);
        }
    }

    public static IntRange exact(int value) {
        ConstantValue constantValue = ConstantValue.exactly((float)value);
        return new IntRange(Optional.of(constantValue), Optional.of(constantValue));
    }

    public static IntRange range(int min, int max) {
        return new IntRange(Optional.of(ConstantValue.exactly((float)min)), Optional.of(ConstantValue.exactly((float)max)));
    }

    public static IntRange lowerBound(int min) {
        return new IntRange(Optional.of(ConstantValue.exactly((float)min)), Optional.empty());
    }

    public static IntRange upperBound(int max) {
        return new IntRange(Optional.empty(), Optional.of(ConstantValue.exactly((float)max)));
    }

    public int clamp(LootContext context, int value) {
        return this.limiter.apply(context, value);
    }

    public boolean test(LootContext context, int value) {
        return this.predicate.test(context, value);
    }

    private OptionalInt unpackExact() {
        return Objects.equals(this.min, this.max)
                && this.min instanceof ConstantValue constantValue
                && Math.floor((double)constantValue.value()) == (double)constantValue.value()
            ? OptionalInt.of((int)constantValue.value())
            : OptionalInt.empty();
    }

    @FunctionalInterface
    interface IntChecker {
        boolean test(LootContext context, int value);
    }

    @FunctionalInterface
    interface IntLimiter {
        int apply(LootContext context, int value);
    }
}
