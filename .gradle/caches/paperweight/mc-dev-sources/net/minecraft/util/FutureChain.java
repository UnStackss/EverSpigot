package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class FutureChain implements TaskChainer, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CompletableFuture<?> head = CompletableFuture.completedFuture(null);
    private final Executor executor;
    private volatile boolean closed;

    public FutureChain(Executor executor) {
        this.executor = executor;
    }

    @Override
    public <T> void append(CompletableFuture<T> future, Consumer<T> callback) {
        this.head = this.head.<T, Object>thenCombine(future, (a, b) -> b).thenAcceptAsync(object -> {
            if (!this.closed) {
                callback.accept((T)object);
            }
        }, this.executor).exceptionally(throwable -> {
            if (throwable instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }

            if (throwable instanceof CancellationException cancellationException) {
                throw cancellationException;
            } else {
                LOGGER.error("Chain link failed, continuing to next one", throwable);
                return null;
            }
        });
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
