package net.minecraft.advancements.critereon;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

public interface MinMaxBounds<T extends Number> {
    SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Component.translatable("argument.range.empty"));
    SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Component.translatable("argument.range.swapped"));

    Optional<T> min();

    Optional<T> max();

    default boolean isAny() {
        return this.min().isEmpty() && this.max().isEmpty();
    }

    default Optional<T> unwrapPoint() {
        Optional<T> optional = this.min();
        Optional<T> optional2 = this.max();
        return optional.equals(optional2) ? optional : Optional.empty();
    }

    static <T extends Number, R extends MinMaxBounds<T>> Codec<R> createCodec(Codec<T> valueCodec, MinMaxBounds.BoundsFactory<T, R> rangeFactory) {
        Codec<R> codec = RecordCodecBuilder.create(
            instance -> instance.group(
                        valueCodec.optionalFieldOf("min").forGetter(MinMaxBounds::min), valueCodec.optionalFieldOf("max").forGetter(MinMaxBounds::max)
                    )
                    .apply(instance, rangeFactory::create)
        );
        return Codec.either(codec, valueCodec)
            .xmap(either -> either.map(range -> (R)range, value -> rangeFactory.create(Optional.of((T)value), Optional.of((T)value))), range -> {
                Optional<T> optional = range.unwrapPoint();
                return optional.isPresent() ? Either.right(optional.get()) : Either.left((R)range);
            });
    }

    static <T extends Number, R extends MinMaxBounds<T>> R fromReader(
        StringReader commandReader,
        MinMaxBounds.BoundsFromReaderFactory<T, R> commandFactory,
        Function<String, T> converter,
        Supplier<DynamicCommandExceptionType> exceptionTypeSupplier,
        Function<T, T> mapper
    ) throws CommandSyntaxException {
        if (!commandReader.canRead()) {
            throw ERROR_EMPTY.createWithContext(commandReader);
        } else {
            int i = commandReader.getCursor();

            try {
                Optional<T> optional = readNumber(commandReader, converter, exceptionTypeSupplier).map(mapper);
                Optional<T> optional2;
                if (commandReader.canRead(2) && commandReader.peek() == '.' && commandReader.peek(1) == '.') {
                    commandReader.skip();
                    commandReader.skip();
                    optional2 = readNumber(commandReader, converter, exceptionTypeSupplier).map(mapper);
                    if (optional.isEmpty() && optional2.isEmpty()) {
                        throw ERROR_EMPTY.createWithContext(commandReader);
                    }
                } else {
                    optional2 = optional;
                }

                if (optional.isEmpty() && optional2.isEmpty()) {
                    throw ERROR_EMPTY.createWithContext(commandReader);
                } else {
                    return commandFactory.create(commandReader, optional, optional2);
                }
            } catch (CommandSyntaxException var8) {
                commandReader.setCursor(i);
                throw new CommandSyntaxException(var8.getType(), var8.getRawMessage(), var8.getInput(), i);
            }
        }
    }

    private static <T extends Number> Optional<T> readNumber(
        StringReader reader, Function<String, T> converter, Supplier<DynamicCommandExceptionType> exceptionTypeSupplier
    ) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInputChat(reader)) {
            reader.skip();
        }

        String string = reader.getString().substring(i, reader.getCursor());
        if (string.isEmpty()) {
            return Optional.empty();
        } else {
            try {
                return Optional.of(converter.apply(string));
            } catch (NumberFormatException var6) {
                throw exceptionTypeSupplier.get().createWithContext(reader, string);
            }
        }
    }

    private static boolean isAllowedInputChat(StringReader reader) {
        char c = reader.peek();
        return c >= '0' && c <= '9' || c == '-' || c == '.' && (!reader.canRead(2) || reader.peek(1) != '.');
    }

    @FunctionalInterface
    public interface BoundsFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(Optional<T> min, Optional<T> max);
    }

    @FunctionalInterface
    public interface BoundsFromReaderFactory<T extends Number, R extends MinMaxBounds<T>> {
        R create(StringReader reader, Optional<T> min, Optional<T> max) throws CommandSyntaxException;
    }

    public static record Doubles(@Override Optional<Double> min, @Override Optional<Double> max, Optional<Double> minSq, Optional<Double> maxSq)
        implements MinMaxBounds<Double> {
        public static final MinMaxBounds.Doubles ANY = new MinMaxBounds.Doubles(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Doubles> CODEC = MinMaxBounds.createCodec(Codec.DOUBLE, MinMaxBounds.Doubles::new);

        private Doubles(Optional<Double> min, Optional<Double> max) {
            this(min, max, squareOpt(min), squareOpt(max));
        }

        private static MinMaxBounds.Doubles create(StringReader reader, Optional<Double> min, Optional<Double> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Doubles(min, max);
            }
        }

        private static Optional<Double> squareOpt(Optional<Double> value) {
            return value.map(d -> d * d);
        }

        public static MinMaxBounds.Doubles exactly(double value) {
            return new MinMaxBounds.Doubles(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Doubles between(double min, double max) {
            return new MinMaxBounds.Doubles(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Doubles atLeast(double value) {
            return new MinMaxBounds.Doubles(Optional.of(value), Optional.empty());
        }

        public static MinMaxBounds.Doubles atMost(double value) {
            return new MinMaxBounds.Doubles(Optional.empty(), Optional.of(value));
        }

        public boolean matches(double value) {
            return (!this.min.isPresent() || !(this.min.get() > value)) && (this.max.isEmpty() || !(this.max.get() < value));
        }

        public boolean matchesSqr(double value) {
            return (!this.minSq.isPresent() || !(this.minSq.get() > value)) && (this.maxSq.isEmpty() || !(this.maxSq.get() < value));
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, value -> value);
        }

        public static MinMaxBounds.Doubles fromReader(StringReader reader, Function<Double, Double> mapper) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Doubles::create, Double::parseDouble, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidDouble, mapper
            );
        }
    }

    public static record Ints(@Override Optional<Integer> min, @Override Optional<Integer> max, Optional<Long> minSq, Optional<Long> maxSq)
        implements MinMaxBounds<Integer> {
        public static final MinMaxBounds.Ints ANY = new MinMaxBounds.Ints(Optional.empty(), Optional.empty());
        public static final Codec<MinMaxBounds.Ints> CODEC = MinMaxBounds.createCodec(Codec.INT, MinMaxBounds.Ints::new);

        private Ints(Optional<Integer> min, Optional<Integer> max) {
            this(min, max, min.map(i -> i.longValue() * i.longValue()), squareOpt(max));
        }

        private static MinMaxBounds.Ints create(StringReader reader, Optional<Integer> min, Optional<Integer> max) throws CommandSyntaxException {
            if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
                throw ERROR_SWAPPED.createWithContext(reader);
            } else {
                return new MinMaxBounds.Ints(min, max);
            }
        }

        private static Optional<Long> squareOpt(Optional<Integer> value) {
            return value.map(i -> i.longValue() * i.longValue());
        }

        public static MinMaxBounds.Ints exactly(int value) {
            return new MinMaxBounds.Ints(Optional.of(value), Optional.of(value));
        }

        public static MinMaxBounds.Ints between(int min, int max) {
            return new MinMaxBounds.Ints(Optional.of(min), Optional.of(max));
        }

        public static MinMaxBounds.Ints atLeast(int value) {
            return new MinMaxBounds.Ints(Optional.of(value), Optional.empty());
        }

        public static MinMaxBounds.Ints atMost(int value) {
            return new MinMaxBounds.Ints(Optional.empty(), Optional.of(value));
        }

        public boolean matches(int value) {
            return (!this.min.isPresent() || this.min.get() <= value) && (this.max.isEmpty() || this.max.get() >= value);
        }

        public boolean matchesSqr(long value) {
            return (!this.minSq.isPresent() || this.minSq.get() <= value) && (this.maxSq.isEmpty() || this.maxSq.get() >= value);
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader) throws CommandSyntaxException {
            return fromReader(reader, value -> value);
        }

        public static MinMaxBounds.Ints fromReader(StringReader reader, Function<Integer, Integer> converter) throws CommandSyntaxException {
            return MinMaxBounds.fromReader(
                reader, MinMaxBounds.Ints::create, Integer::parseInt, CommandSyntaxException.BUILT_IN_EXCEPTIONS::readerInvalidInt, converter
            );
        }
    }
}
