package net.minecraft.commands.execution;

import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;

public interface ExecutionControl<T> {
    void queueNext(EntryAction<T> action);

    void tracer(@Nullable TraceCallbacks tracer);

    @Nullable
    TraceCallbacks tracer();

    Frame currentFrame();

    static <T extends ExecutionCommandSource<T>> ExecutionControl<T> create(ExecutionContext<T> context, Frame frame) {
        return new ExecutionControl<T>() {
            @Override
            public void queueNext(EntryAction<T> action) {
                context.queueNext(new CommandQueueEntry<>(frame, action));
            }

            @Override
            public void tracer(@Nullable TraceCallbacks tracer) {
                context.tracer(tracer);
            }

            @Nullable
            @Override
            public TraceCallbacks tracer() {
                return context.tracer();
            }

            @Override
            public Frame currentFrame() {
                return frame;
            }
        };
    }
}
