package net.minecraft.server.commands;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.datafixers.util.Pair;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.FunctionInstantiationException;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.commands.arguments.item.FunctionArgument;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.execution.tasks.FallthroughTask;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerFunctionManager;
import net.minecraft.server.commands.data.DataAccessor;
import net.minecraft.server.commands.data.DataCommands;

public class FunctionCommand {
    private static final DynamicCommandExceptionType ERROR_ARGUMENT_NOT_COMPOUND = new DynamicCommandExceptionType(
        argument -> Component.translatableEscape("commands.function.error.argument_not_compound", argument)
    );
    static final DynamicCommandExceptionType ERROR_NO_FUNCTIONS = new DynamicCommandExceptionType(
        argument -> Component.translatableEscape("commands.function.scheduled.no_functions", argument)
    );
    @VisibleForTesting
    public static final Dynamic2CommandExceptionType ERROR_FUNCTION_INSTANTATION_FAILURE = new Dynamic2CommandExceptionType(
        (argument, argument2) -> Component.translatableEscape("commands.function.instantiationFailure", argument, argument2)
    );
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_FUNCTION = (context, builder) -> {
        ServerFunctionManager serverFunctionManager = context.getSource().getServer().getFunctions();
        SharedSuggestionProvider.suggestResource(serverFunctionManager.getTagNames(), builder, "#");
        return SharedSuggestionProvider.suggestResource(serverFunctionManager.getFunctionNames(), builder);
    };
    static final FunctionCommand.Callbacks<CommandSourceStack> FULL_CONTEXT_CALLBACKS = new FunctionCommand.Callbacks<CommandSourceStack>() {
        @Override
        public void signalResult(CommandSourceStack source, ResourceLocation id, int result) {
            source.sendSuccess(() -> Component.translatable("commands.function.result", Component.translationArg(id), result), true);
        }
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("with");

        for (DataCommands.DataProvider dataProvider : DataCommands.SOURCE_PROVIDERS) {
            dataProvider.wrap(literalArgumentBuilder, builder -> builder.executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return dataProvider.access(context).getData();
                    }
                }).then(Commands.argument("path", NbtPathArgument.nbtPath()).executes(new FunctionCommand.FunctionCustomExecutor() {
                    @Override
                    protected CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
                        return FunctionCommand.getArgumentTag(NbtPathArgument.getPath(context, "path"), dataProvider.access(context));
                    }
                })));
        }

        dispatcher.register(
            Commands.literal("function")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("name", FunctionArgument.functions()).suggests(SUGGEST_FUNCTION).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Nullable
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                            return null;
                        }
                    }).then(Commands.argument("arguments", CompoundTagArgument.compoundTag()).executes(new FunctionCommand.FunctionCustomExecutor() {
                        @Override
                        protected CompoundTag arguments(CommandContext<CommandSourceStack> context) {
                            return CompoundTagArgument.getCompoundTag(context, "arguments");
                        }
                    })).then(literalArgumentBuilder)
                )
        );
    }

    static CompoundTag getArgumentTag(NbtPathArgument.NbtPath path, DataAccessor object) throws CommandSyntaxException {
        Tag tag = DataCommands.getSingleTag(path, object);
        if (tag instanceof CompoundTag) {
            return (CompoundTag)tag;
        } else {
            throw ERROR_ARGUMENT_NOT_COMPOUND.create(tag.getType().getName());
        }
    }

    public static CommandSourceStack modifySenderForExecution(CommandSourceStack source) {
        return source.withSuppressedOutput().withMaximumPermission(2);
    }

    public static <T extends ExecutionCommandSource<T>> void queueFunctions(
        Collection<CommandFunction<T>> commandFunctions,
        @Nullable CompoundTag args,
        T parentSource,
        T functionSource,
        ExecutionControl<T> control,
        FunctionCommand.Callbacks<T> resultConsumer,
        ChainModifiers flags
    ) throws CommandSyntaxException {
        if (flags.isReturn()) {
            queueFunctionsAsReturn(commandFunctions, args, parentSource, functionSource, control, resultConsumer);
        } else {
            queueFunctionsNoReturn(commandFunctions, args, parentSource, functionSource, control, resultConsumer);
        }
    }

    private static <T extends ExecutionCommandSource<T>> void instantiateAndQueueFunctions(
        @Nullable CompoundTag args,
        ExecutionControl<T> control,
        CommandDispatcher<T> dispatcher,
        T source,
        CommandFunction<T> function,
        ResourceLocation id,
        CommandResultCallback returnValueConsumer,
        boolean propagateReturn
    ) throws CommandSyntaxException {
        try {
            InstantiatedFunction<T> instantiatedFunction = function.instantiate(args, dispatcher);
            control.queueNext(new CallFunction<>(instantiatedFunction, returnValueConsumer, propagateReturn).bind(source));
        } catch (FunctionInstantiationException var9) {
            throw ERROR_FUNCTION_INSTANTATION_FAILURE.create(id, var9.messageComponent());
        }
    }

    private static <T extends ExecutionCommandSource<T>> CommandResultCallback decorateOutputIfNeeded(
        T flags, FunctionCommand.Callbacks<T> resultConsumer, ResourceLocation id, CommandResultCallback wrapped
    ) {
        return flags.isSilent() ? wrapped : (successful, returnValue) -> {
            resultConsumer.signalResult(flags, id, returnValue);
            wrapped.onResult(successful, returnValue);
        };
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsAsReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag args,
        T parentSource,
        T functionSource,
        ExecutionControl<T> control,
        FunctionCommand.Callbacks<T> resultConsumer
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commandDispatcher = parentSource.dispatcher();
        T executionCommandSource = functionSource.clearCallbacks();
        CommandResultCallback commandResultCallback = CommandResultCallback.chain(parentSource.callback(), control.currentFrame().returnValueConsumer());

        for (CommandFunction<T> commandFunction : functions) {
            ResourceLocation resourceLocation = commandFunction.id();
            CommandResultCallback commandResultCallback2 = decorateOutputIfNeeded(parentSource, resultConsumer, resourceLocation, commandResultCallback);
            instantiateAndQueueFunctions(
                args, control, commandDispatcher, executionCommandSource, commandFunction, resourceLocation, commandResultCallback2, true
            );
        }

        control.queueNext(FallthroughTask.instance());
    }

    private static <T extends ExecutionCommandSource<T>> void queueFunctionsNoReturn(
        Collection<CommandFunction<T>> functions,
        @Nullable CompoundTag args,
        T parentSource,
        T functionSource,
        ExecutionControl<T> control,
        FunctionCommand.Callbacks<T> resultConsumer
    ) throws CommandSyntaxException {
        CommandDispatcher<T> commandDispatcher = parentSource.dispatcher();
        T executionCommandSource = functionSource.clearCallbacks();
        CommandResultCallback commandResultCallback = parentSource.callback();
        if (!functions.isEmpty()) {
            if (functions.size() == 1) {
                CommandFunction<T> commandFunction = functions.iterator().next();
                ResourceLocation resourceLocation = commandFunction.id();
                CommandResultCallback commandResultCallback2 = decorateOutputIfNeeded(parentSource, resultConsumer, resourceLocation, commandResultCallback);
                instantiateAndQueueFunctions(
                    args, control, commandDispatcher, executionCommandSource, commandFunction, resourceLocation, commandResultCallback2, false
                );
            } else if (commandResultCallback == CommandResultCallback.EMPTY) {
                for (CommandFunction<T> commandFunction2 : functions) {
                    ResourceLocation resourceLocation2 = commandFunction2.id();
                    CommandResultCallback commandResultCallback3 = decorateOutputIfNeeded(
                        parentSource, resultConsumer, resourceLocation2, commandResultCallback
                    );
                    instantiateAndQueueFunctions(
                        args, control, commandDispatcher, executionCommandSource, commandFunction2, resourceLocation2, commandResultCallback3, false
                    );
                }
            } else {
                class Accumulator {
                    boolean anyResult;
                    int sum;

                    public void add(int returnValue) {
                        this.anyResult = true;
                        this.sum += returnValue;
                    }
                }

                Accumulator lv = new Accumulator();
                CommandResultCallback commandResultCallback4 = (successful, returnValue) -> lv.add(returnValue);

                for (CommandFunction<T> commandFunction3 : functions) {
                    ResourceLocation resourceLocation3 = commandFunction3.id();
                    CommandResultCallback commandResultCallback5 = decorateOutputIfNeeded(
                        parentSource, resultConsumer, resourceLocation3, commandResultCallback4
                    );
                    instantiateAndQueueFunctions(
                        args, control, commandDispatcher, executionCommandSource, commandFunction3, resourceLocation3, commandResultCallback5, false
                    );
                }

                control.queueNext((context, frame) -> {
                    if (lv.anyResult) {
                        commandResultCallback.onSuccess(lv.sum);
                    }
                });
            }
        }
    }

    public interface Callbacks<T> {
        void signalResult(T source, ResourceLocation id, int result);
    }

    abstract static class FunctionCustomExecutor
        extends CustomCommandExecutor.WithErrorHandling<CommandSourceStack>
        implements CustomCommandExecutor.CommandAdapter<CommandSourceStack> {
        @Nullable
        protected abstract CompoundTag arguments(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;

        @Override
        public void runGuarded(
            CommandSourceStack commandSourceStack,
            ContextChain<CommandSourceStack> contextChain,
            ChainModifiers chainModifiers,
            ExecutionControl<CommandSourceStack> executionControl
        ) throws CommandSyntaxException {
            CommandContext<CommandSourceStack> commandContext = contextChain.getTopContext().copyFor(commandSourceStack);
            Pair<ResourceLocation, Collection<CommandFunction<CommandSourceStack>>> pair = FunctionArgument.getFunctionCollection(commandContext, "name");
            Collection<CommandFunction<CommandSourceStack>> collection = pair.getSecond();
            if (collection.isEmpty()) {
                throw FunctionCommand.ERROR_NO_FUNCTIONS.create(Component.translationArg(pair.getFirst()));
            } else {
                CompoundTag compoundTag = this.arguments(commandContext);
                CommandSourceStack commandSourceStack2 = FunctionCommand.modifySenderForExecution(commandSourceStack);
                if (collection.size() == 1) {
                    commandSourceStack.sendSuccess(
                        () -> Component.translatable("commands.function.scheduled.single", Component.translationArg(collection.iterator().next().id())), true
                    );
                } else {
                    commandSourceStack.sendSuccess(
                        () -> Component.translatable(
                                "commands.function.scheduled.multiple",
                                ComponentUtils.formatList(collection.stream().map(CommandFunction::id).toList(), Component::translationArg)
                            ),
                        true
                    );
                }

                FunctionCommand.queueFunctions(
                    collection, compoundTag, commandSourceStack, commandSourceStack2, executionControl, FunctionCommand.FULL_CONTEXT_CALLBACKS, chainModifiers
                );
            }
        }
    }
}
