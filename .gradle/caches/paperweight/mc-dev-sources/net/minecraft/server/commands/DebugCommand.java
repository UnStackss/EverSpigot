package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.profiling.ProfileResults;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class DebugCommand {
    static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType ERROR_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("commands.debug.notRunning"));
    private static final SimpleCommandExceptionType ERROR_ALREADY_RUNNING = new SimpleCommandExceptionType(
        Component.translatable("commands.debug.alreadyRunning")
    );
    static final SimpleCommandExceptionType NO_RECURSIVE_TRACES = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noRecursion"));
    static final SimpleCommandExceptionType NO_RETURN_RUN = new SimpleCommandExceptionType(Component.translatable("commands.debug.function.noReturnRun"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("debug")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("start").executes(context -> start(context.getSource())))
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(
                    Commands.literal("function")
                        .requires(source -> source.hasPermission(3))
                        .then(
                            Commands.argument("name", FunctionArgument.functions())
                                .suggests(FunctionCommand.SUGGEST_FUNCTION)
                                .executes(new DebugCommand.TraceCustomExecutor())
                        )
                )
        );
    }

    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftServer = source.getServer();
        if (minecraftServer.isTimeProfilerRunning()) {
            throw ERROR_ALREADY_RUNNING.create();
        } else {
            minecraftServer.startTimeProfiler();
            source.sendSuccess(() -> Component.translatable("commands.debug.started"), true);
            return 0;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        MinecraftServer minecraftServer = source.getServer();
        if (!minecraftServer.isTimeProfilerRunning()) {
            throw ERROR_NOT_RUNNING.create();
        } else {
            ProfileResults profileResults = minecraftServer.stopTimeProfiler();
            double d = (double)profileResults.getNanoDuration() / (double)TimeUtil.NANOSECONDS_PER_SECOND;
            double e = (double)profileResults.getTickDuration() / d;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.debug.stopped",
                        String.format(Locale.ROOT, "%.2f", d),
                        profileResults.getTickDuration(),
                        String.format(Locale.ROOT, "%.2f", e)
                    ),
                true
            );
            return (int)e;
        }
    }

    static class TraceCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Override
        public void runGuarded(
            CommandSourceStack commandSourceStack,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) throws CommandSyntaxException {
            if (chainModifiers.isReturn()) {
                throw DebugCommand.NO_RETURN_RUN.create();
            } else if (executionControl.tracer() != null) {
                throw DebugCommand.NO_RECURSIVE_TRACES.create();
            } else {
                CommandContext<CommandSourceStack> commandContext = contextChain.getTopContext();
                Collection<CommandFunction<CommandSourceStack>> collection = FunctionArgument.getFunctions(commandContext, "name");
                MinecraftServer minecraftServer = commandSourceStack.getServer();
                String string = "debug-trace-" + Util.getFilenameFormattedDateTime() + ".txt";
                CommandDispatcher<CommandSourceStack> commandDispatcher = commandSourceStack.getServer().getFunctions().getDispatcher();
                int i = 0;

                try {
                    Path path = minecraftServer.getFile("debug");
                    Files.createDirectories(path);
                    final PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(path.resolve(string), StandardCharsets.UTF_8));
                    DebugCommand.Tracer tracer = new DebugCommand.Tracer(printWriter);
                    executionControl.tracer(tracer);

                    for (final CommandFunction<CommandSourceStack> commandFunction : collection) {
                        try {
                            CommandSourceStack commandSourceStack2 = commandSourceStack.withSource(tracer).withMaximumPermission(2);
                            InstantiatedFunction<CommandSourceStack> instantiatedFunction = commandFunction.instantiate(null, commandDispatcher);
                            executionControl.queueNext((new CallFunction<CommandSourceStack>(instantiatedFunction, CommandResultCallback.EMPTY, false) {
                                @Override
                                public void execute(CommandSourceStack commandSourceStack, ExecutionContext<CommandSourceStack> executionContext, Frame frame) {
                                    printWriter.println(commandFunction.id());
                                    super.execute(commandSourceStack, executionContext, frame);
                                }
                            }).bind(commandSourceStack2));
                            i += instantiatedFunction.entries().size();
                        } catch (FunctionInstantiationException var18) {
                            commandSourceStack.sendFailure(var18.messageComponent());
                        }
                    }
                } catch (IOException | UncheckedIOException var19) {
                    DebugCommand.LOGGER.warn("Tracing failed", (Throwable)var19);
                    commandSourceStack.sendFailure(Component.translatable("commands.debug.function.traceFailed"));
                }

                int j = i;
                executionControl.queueNext(
                    (context, frame) -> {
                        if (collection.size() == 1) {
                            commandSourceStack.sendSuccess(
                                () -> Component.translatable(
                                        "commands.debug.function.success.single", j, Component.translationArg(collection.iterator().next().id()), string
                                    ),
                                true
                            );
                        } else {
                            commandSourceStack.sendSuccess(
                                () -> Component.translatable("commands.debug.function.success.multiple", j, collection.size(), string), true
                            );
                        }
                    }
                );
            }
        }
    }

    static class Tracer implements CommandSource, TraceCallbacks {
        public static final int INDENT_OFFSET = 1;
        private final PrintWriter output;
        private int lastIndent;
        private boolean waitingForResult;

        Tracer(PrintWriter writer) {
            this.output = writer;
        }

        private void indentAndSave(int width) {
            this.printIndent(width);
            this.lastIndent = width;
        }

        private void printIndent(int width) {
            for (int i = 0; i < width + 1; i++) {
                this.output.write("    ");
            }
        }

        private void newLine() {
            if (this.waitingForResult) {
                this.output.println();
                this.waitingForResult = false;
            }
        }

        @Override
        public void onCommand(int depth, String command) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[C] ");
            this.output.print(command);
            this.waitingForResult = true;
        }

        @Override
        public void onReturn(int depth, String command, int result) {
            if (this.waitingForResult) {
                this.output.print(" -> ");
                this.output.println(result);
                this.waitingForResult = false;
            } else {
                this.indentAndSave(depth);
                this.output.print("[R = ");
                this.output.print(result);
                this.output.print("] ");
                this.output.println(command);
            }
        }

        @Override
        public void onCall(int depth, ResourceLocation function, int size) {
            this.newLine();
            this.indentAndSave(depth);
            this.output.print("[F] ");
            this.output.print(function);
            this.output.print(" size=");
            this.output.println(size);
        }

        @Override
        public void onError(String message) {
            this.newLine();
            this.indentAndSave(this.lastIndent + 1);
            this.output.print("[E] ");
            this.output.print(message);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.newLine();
            this.printIndent(this.lastIndent + 1);
            this.output.print("[M] ");
            this.output.println(message.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        @Override
        public boolean alwaysAccepts() {
            return true;
        }

        @Override
        public void close() {
            IOUtils.closeQuietly((Writer)this.output);
        }
    }
}
