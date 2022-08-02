package io.papermc.paper.command.brigadier.argument;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.chat.SignedMessage;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public record SignedMessageResolverImpl(MessageArgument.Message message) implements SignedMessageResolver {

    @Override
    public String content() {
        return this.message.text();
    }

    @Override
    public @NotNull CompletableFuture<SignedMessage> resolveSignedMessage(final String argumentName, final CommandContext erased) throws CommandSyntaxException {
        final CommandContext<CommandSourceStack> type = erased;
        final CompletableFuture<SignedMessage> future = new CompletableFuture<>();

        final MessageArgument.Message response = type.getArgument(argumentName, SignedMessageResolverImpl.class).message;
        MessageArgument.resolveChatMessage(response, type, argumentName, (message) -> {
            future.complete(message.adventureView());
        });
        return future;
    }
}
