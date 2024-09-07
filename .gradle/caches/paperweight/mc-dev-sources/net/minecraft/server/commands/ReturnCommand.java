package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.ContextChain;
import java.util.List;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.FallthroughTask;

public class ReturnCommand {
    public static <T extends ExecutionCommandSource<T>> void register(CommandDispatcher<T> dispatcher) {
        dispatcher.register(
            (LiteralArgumentBuilder<T>)LiteralArgumentBuilder.<ExecutionCommandSource>literal("return")
                .requires(source -> source.hasPermission(2))
                .then(
                    RequiredArgumentBuilder.<T, Integer>argument("value", IntegerArgumentType.integer())
                        .executes(new ReturnCommand.ReturnValueCustomExecutor<>())
                )
                .then(LiteralArgumentBuilder.<T>literal("fail").executes(new ReturnCommand.ReturnFailCustomExecutor<>()))
                .then(LiteralArgumentBuilder.<T>literal("run").forward(dispatcher.getRoot(), new ReturnCommand.ReturnFromCommandCustomModifier<>(), false))
        );
    }

    static class ReturnFailCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        @Override
        public void run(T source, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control) {
            source.callback().onFailure();
            Frame frame = control.currentFrame();
            frame.returnFailure();
            frame.discard();
        }
    }

    static class ReturnFromCommandCustomModifier<T extends ExecutionCommandSource<T>> implements CustomModifierExecutor.ModifierAdapter<T> {
        @Override
        public void apply(T baseSource, List<T> sources, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control) {
            if (sources.isEmpty()) {
                if (flags.isReturn()) {
                    control.queueNext(FallthroughTask.instance());
                }
            } else {
                control.currentFrame().discard();
                ContextChain<T> contextChain2 = contextChain.nextStage();
                String string = contextChain2.getTopContext().getInput();
                control.queueNext(new BuildContexts.Continuation<>(string, contextChain2, flags.setReturn(), baseSource, sources));
            }
        }
    }

    static class ReturnValueCustomExecutor<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor.CommandAdapter<T> {
        @Override
        public void run(T source, ContextChain<T> contextChain, ChainModifiers flags, ExecutionControl<T> control) {
            int i = IntegerArgumentType.getInteger(contextChain.getTopContext(), "value");
            source.callback().onSuccess(i);
            Frame frame = control.currentFrame();
            frame.returnSuccess(i);
            frame.discard();
        }
    }
}
