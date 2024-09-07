package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {
    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType(
        count -> Component.translatableEscape("command.forkLimit", count)
    );
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String command, ContextChain<T> contextChain) {
        this.commandInput = command;
        this.command = contextChain;
    }

    protected void execute(T baseSource, List<T> sources, ExecutionContext<T> context, Frame frame, ChainModifiers flags) {
        ContextChain<T> contextChain = this.command;
        ChainModifiers chainModifiers = flags;
        List<T> list = sources;
        if (contextChain.getStage() != Stage.EXECUTE) {
            context.profiler().push(() -> "prepare " + this.commandInput);

            try {
                for (int i = context.forkLimit(); contextChain.getStage() != Stage.EXECUTE; contextChain = contextChain.nextStage()) {
                    CommandContext<T> commandContext = contextChain.getTopContext();
                    if (commandContext.isForked()) {
                        chainModifiers = chainModifiers.setForked();
                    }

                    RedirectModifier<T> redirectModifier = commandContext.getRedirectModifier();
                    if (redirectModifier instanceof CustomModifierExecutor<T> customModifierExecutor) {
                        customModifierExecutor.apply(baseSource, list, contextChain, chainModifiers, ExecutionControl.create(context, frame));
                        return;
                    }

                    if (redirectModifier != null) {
                        context.incrementCost();
                        boolean bl = chainModifiers.isForked();
                        List<T> list2 = new ObjectArrayList<>();

                        for (T executionCommandSource : list) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(
                                    commandContext, executionCommandSource, (contextx, successful, returnValue) -> {
                                    }, bl
                                );
                                if (list2.size() + collection.size() >= i) {
                                    baseSource.handleError(ERROR_FORK_LIMIT_REACHED.create(i), bl, context.tracer());
                                    return;
                                }

                                list2.addAll(collection);
                            } catch (CommandSyntaxException var20) {
                                executionCommandSource.handleError(var20, bl, context.tracer());
                                if (!bl) {
                                    return;
                                }
                            }
                        }

                        list = list2;
                    }
                }
            } finally {
                context.profiler().pop();
            }
        }

        if (list.isEmpty()) {
            if (chainModifiers.isReturn()) {
                context.queueNext(new CommandQueueEntry<>(frame, FallthroughTask.instance()));
            }
        } else {
            CommandContext<T> commandContext2 = contextChain.getTopContext();
            if (commandContext2.getCommand() instanceof CustomCommandExecutor<T> customCommandExecutor) {
                ExecutionControl<T> executionControl = ExecutionControl.create(context, frame);

                for (T executionCommandSource2 : list) {
                    customCommandExecutor.run(executionCommandSource2, contextChain, chainModifiers, executionControl);
                }
            } else {
                if (chainModifiers.isReturn()) {
                    T executionCommandSource3 = list.get(0);
                    executionCommandSource3 = executionCommandSource3.withCallback(
                        CommandResultCallback.chain(executionCommandSource3.callback(), frame.returnValueConsumer())
                    );
                    list = List.of(executionCommandSource3);
                }

                ExecuteCommand<T> executeCommand = new ExecuteCommand<>(this.commandInput, chainModifiers, commandContext2);
                ContinuationTask.schedule(context, frame, list, (framex, source) -> new CommandQueueEntry<>(framex, executeCommand.bind(source)));
            }
        }
    }

    protected void traceCommandStart(ExecutionContext<T> context, Frame frame) {
        TraceCallbacks traceCallbacks = context.tracer();
        if (traceCallbacks != null) {
            traceCallbacks.onCommand(frame.depth(), this.commandInput);
        }
    }

    @Override
    public String toString() {
        return this.commandInput;
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String command, ContextChain<T> contextChain, ChainModifiers flags, T baseSource, List<T> sources) {
            super(command, contextChain);
            this.originalSource = baseSource;
            this.sources = sources;
            this.modifiers = flags;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.execute(this.originalSource, this.sources, context, frame, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final T source;

        public TopLevel(String command, ContextChain<T> contextChain, T source) {
            super(command, contextChain);
            this.source = source;
        }

        @Override
        public void execute(ExecutionContext<T> context, Frame frame) {
            this.traceCommandStart(context, frame);
            this.execute(this.source, List.of(this.source), context, frame, ChainModifiers.DEFAULT);
        }
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {
        public Unbound(String command, ContextChain<T> contextChain) {
            super(command, contextChain);
        }

        @Override
        public void execute(T executionCommandSource, ExecutionContext<T> executionContext, Frame frame) {
            this.traceCommandStart(executionContext, frame);
            this.execute(executionCommandSource, List.of(executionCommandSource), executionContext, frame, ChainModifiers.DEFAULT);
        }
    }
}
