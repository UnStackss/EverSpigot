package net.minecraft.commands.execution;

import com.google.common.collect.Queues;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ExecutionContext<T> implements AutoCloseable {
    private static final int MAX_QUEUE_DEPTH = 10000000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int commandLimit;
    private final int forkLimit;
    private final ProfilerFiller profiler;
    @Nullable
    private TraceCallbacks tracer;
    private int commandQuota;
    private boolean queueOverflow;
    private final Deque<CommandQueueEntry<T>> commandQueue = Queues.newArrayDeque();
    private final List<CommandQueueEntry<T>> newTopCommands = new ObjectArrayList<>();
    private int currentFrameDepth;

    public ExecutionContext(int maxCommandChainLength, int maxCommandForkCount, ProfilerFiller profiler) {
        this.commandLimit = maxCommandChainLength;
        this.forkLimit = maxCommandForkCount;
        this.profiler = profiler;
        this.commandQuota = maxCommandChainLength;
    }

    private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(ExecutionContext<T> context, CommandResultCallback returnValueConsumer) {
        if (context.currentFrameDepth == 0) {
            return new Frame(0, returnValueConsumer, context.commandQueue::clear);
        } else {
            int i = context.currentFrameDepth + 1;
            return new Frame(i, returnValueConsumer, context.frameControlForDepth(i));
        }
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialFunctionCall(
        ExecutionContext<T> context, InstantiatedFunction<T> procedure, T source, CommandResultCallback returnValueConsumer
    ) {
        context.queueNext(
            new CommandQueueEntry<>(createTopFrame(context, returnValueConsumer), new CallFunction<>(procedure, source.callback(), false).bind(source))
        );
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialCommandExecution(
        ExecutionContext<T> context, String command, ContextChain<T> contextChain, T source, CommandResultCallback returnValueConsumer
    ) {
        context.queueNext(new CommandQueueEntry<>(createTopFrame(context, returnValueConsumer), new BuildContexts.TopLevel<>(command, contextChain, source)));
    }

    private void handleQueueOverflow() {
        this.queueOverflow = true;
        this.newTopCommands.clear();
        this.commandQueue.clear();
    }

    public void queueNext(CommandQueueEntry<T> entry) {
        if (this.newTopCommands.size() + this.commandQueue.size() > 10000000) {
            this.handleQueueOverflow();
        }

        if (!this.queueOverflow) {
            this.newTopCommands.add(entry);
        }
    }

    public void discardAtDepthOrHigher(int depth) {
        while (!this.commandQueue.isEmpty() && this.commandQueue.peek().frame().depth() >= depth) {
            this.commandQueue.removeFirst();
        }
    }

    public Frame.FrameControl frameControlForDepth(int depth) {
        return () -> this.discardAtDepthOrHigher(depth);
    }

    public void runCommandQueue() {
        this.pushNewCommands();

        while (true) {
            if (this.commandQuota <= 0) {
                LOGGER.info("Command execution stopped due to limit (executed {} commands)", this.commandLimit);
                break;
            }

            CommandQueueEntry<T> commandQueueEntry = this.commandQueue.pollFirst();
            if (commandQueueEntry == null) {
                return;
            }

            this.currentFrameDepth = commandQueueEntry.frame().depth();
            commandQueueEntry.execute(this);
            if (this.queueOverflow) {
                LOGGER.error("Command execution stopped due to command queue overflow (max {})", 10000000);
                break;
            }

            this.pushNewCommands();
        }

        this.currentFrameDepth = 0;
    }

    private void pushNewCommands() {
        for (int i = this.newTopCommands.size() - 1; i >= 0; i--) {
            this.commandQueue.addFirst(this.newTopCommands.get(i));
        }

        this.newTopCommands.clear();
    }

    public void tracer(@Nullable TraceCallbacks tracer) {
        this.tracer = tracer;
    }

    @Nullable
    public TraceCallbacks tracer() {
        return this.tracer;
    }

    public ProfilerFiller profiler() {
        return this.profiler;
    }

    public int forkLimit() {
        return this.forkLimit;
    }

    public void incrementCost() {
        this.commandQuota--;
    }

    @Override
    public void close() {
        if (this.tracer != null) {
            this.tracer.close();
        }
    }
}
