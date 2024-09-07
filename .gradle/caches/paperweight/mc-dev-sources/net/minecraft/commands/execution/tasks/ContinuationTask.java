package net.minecraft.commands.execution.tasks;

import java.util.List;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;

public class ContinuationTask<T, P> implements EntryAction<T> {
    private final ContinuationTask.TaskProvider<T, P> taskFactory;
    private final List<P> arguments;
    private final CommandQueueEntry<T> selfEntry;
    private int index;

    private ContinuationTask(ContinuationTask.TaskProvider<T, P> wrapper, List<P> actions, Frame frame) {
        this.taskFactory = wrapper;
        this.arguments = actions;
        this.selfEntry = new CommandQueueEntry<>(frame, this);
    }

    @Override
    public void execute(ExecutionContext<T> context, Frame frame) {
        P object = this.arguments.get(this.index);
        context.queueNext(this.taskFactory.create(frame, object));
        if (++this.index < this.arguments.size()) {
            context.queueNext(this.selfEntry);
        }
    }

    public static <T, P> void schedule(ExecutionContext<T> context, Frame frame, List<P> actions, ContinuationTask.TaskProvider<T, P> wrapper) {
        int i = actions.size();
        switch (i) {
            case 0:
                break;
            case 1:
                context.queueNext(wrapper.create(frame, actions.get(0)));
                break;
            case 2:
                context.queueNext(wrapper.create(frame, actions.get(0)));
                context.queueNext(wrapper.create(frame, actions.get(1)));
                break;
            default:
                context.queueNext((new ContinuationTask<>(wrapper, actions, frame)).selfEntry);
        }
    }

    @FunctionalInterface
    public interface TaskProvider<T, P> {
        CommandQueueEntry<T> create(Frame frame, P action);
    }
}
