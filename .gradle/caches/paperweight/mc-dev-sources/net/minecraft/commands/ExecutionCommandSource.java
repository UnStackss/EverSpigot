package net.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.execution.TraceCallbacks;

public interface ExecutionCommandSource<T extends ExecutionCommandSource<T>> {
    boolean hasPermission(int level);

    T withCallback(CommandResultCallback returnValueConsumer);

    CommandResultCallback callback();

    default T clearCallbacks() {
        return this.withCallback(CommandResultCallback.EMPTY);
    }

    CommandDispatcher<T> dispatcher();

    void handleError(CommandExceptionType type, Message message, boolean silent, @Nullable TraceCallbacks tracer);

    boolean isSilent();

    default void handleError(CommandSyntaxException exception, boolean silent, @Nullable TraceCallbacks tracer) {
        this.handleError(exception.getType(), exception.getRawMessage(), silent, tracer);
    }

    static <T extends ExecutionCommandSource<T>> ResultConsumer<T> resultConsumer() {
        return (context, success, result) -> context.getSource().callback().onResult(success, result);
    }
}
