package net.minecraft.commands.execution;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;

public interface CustomCommandExecutor<T> {
    void run(T source, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control);

    public interface CommandAdapter<T> extends Command<T>, CustomCommandExecutor<T> {
        default int run(CommandContext<T> commandContext) throws CommandSyntaxException {
            throw new UnsupportedOperationException("This function should not run");
        }
    }

    public abstract static class WithErrorHandling<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor<T> {
        @Override
        public final void run(T source, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control) {
            try {
                this.runGuarded(source, contextChain, flags, control);
            } catch (CommandSyntaxException var6) {
                this.onError(var6, source, flags, control.tracer());
                source.callback().onFailure();
            }
        }

        protected void onError(CommandSyntaxException exception, T source, ChainModifiers flags, @Nullable TraceCallbacks tracer) {
            source.handleError(exception, flags.isForked(), tracer);
        }

        protected abstract void runGuarded(T source, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control) throws CommandSyntaxException;
    }
}
