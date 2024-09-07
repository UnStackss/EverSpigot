package net.minecraft.server.level;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public interface ChunkResult<T> {
    static <T> ChunkResult<T> of(T chunk) {
        return new ChunkResult.Success<>(chunk);
    }

    static <T> ChunkResult<T> error(String error) {
        return error(() -> error);
    }

    static <T> ChunkResult<T> error(Supplier<String> error) {
        return new ChunkResult.Fail<>(error);
    }

    boolean isSuccess();

    @Nullable
    T orElse(@Nullable T other);

    @Nullable
    static <R> R orElse(ChunkResult<? extends R> optionalChunk, @Nullable R other) {
        R object = (R)optionalChunk.orElse(null);
        return object != null ? object : other;
    }

    @Nullable
    String getError();

    ChunkResult<T> ifSuccess(Consumer<T> callback);

    <R> ChunkResult<R> map(Function<T, R> mapper);

    <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E;

    public static record Fail<T>(Supplier<String> error) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Nullable
        @Override
        public T orElse(@Nullable T other) {
            return other;
        }

        @Override
        public String getError() {
            return this.error.get();
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> callback) {
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> mapper) {
            return new ChunkResult.Fail(this.error);
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            throw exceptionSupplier.get();
        }
    }

    public static record Success<T>(T value) implements ChunkResult<T> {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public T orElse(@Nullable T other) {
            return this.value;
        }

        @Nullable
        @Override
        public String getError() {
            return null;
        }

        @Override
        public ChunkResult<T> ifSuccess(Consumer<T> callback) {
            callback.accept(this.value);
            return this;
        }

        @Override
        public <R> ChunkResult<R> map(Function<T, R> mapper) {
            return new ChunkResult.Success<>(mapper.apply(this.value));
        }

        @Override
        public <E extends Throwable> T orElseThrow(Supplier<E> exceptionSupplier) throws E {
            return this.value;
        }
    }
}
