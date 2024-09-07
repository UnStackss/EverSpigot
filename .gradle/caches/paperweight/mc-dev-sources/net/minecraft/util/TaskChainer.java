package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

@FunctionalInterface
public interface TaskChainer {
    Logger LOGGER = LogUtils.getLogger();

    static TaskChainer immediate(Executor executor) {
        return new TaskChainer() {
            @Override
            public <T> void append(CompletableFuture<T> future, Consumer<T> callback) {
                future.thenAcceptAsync(callback, executor).exceptionally(throwable -> {
                    LOGGER.error("Task failed", throwable);
                    return null;
                });
            }
        };
    }

    default void append(Runnable callback) {
        this.append(CompletableFuture.completedFuture(null), current -> callback.run());
    }

    <T> void append(CompletableFuture<T> future, Consumer<T> callback);
}
