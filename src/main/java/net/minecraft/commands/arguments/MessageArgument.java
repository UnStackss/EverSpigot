package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSigningContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.FilteredText;

public class MessageArgument implements SignedArgument<MessageArgument.Message> {
    private static final Collection<String> EXAMPLES = Arrays.asList("Hello world!", "foo", "@e", "Hello @p :)");
    static final Dynamic2CommandExceptionType TOO_LONG = new Dynamic2CommandExceptionType(
        (length, maxLength) -> Component.translatableEscape("argument.message.too_long", length, maxLength)
    );

    public static MessageArgument message() {
        return new MessageArgument();
    }

    public static Component getMessage(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(name, MessageArgument.Message.class);
        return message.resolveComponent(context.getSource());
    }

    public static void resolveChatMessage(CommandContext<CommandSourceStack> context, String name, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
        MessageArgument.Message message = context.getArgument(name, MessageArgument.Message.class);
    // Paper start
        resolveChatMessage(message, context, name, callback);
    }
    public static void resolveChatMessage(MessageArgument.Message message, CommandContext<CommandSourceStack> context, String name, Consumer<PlayerChatMessage> callback) throws CommandSyntaxException {
    // Paper end
        CommandSourceStack commandSourceStack = context.getSource();
        Component component = message.resolveComponent(commandSourceStack);
        CommandSigningContext commandSigningContext = commandSourceStack.getSigningContext();
        PlayerChatMessage playerChatMessage = commandSigningContext.getArgument(name);
        if (playerChatMessage != null) {
            resolveSignedMessage(callback, commandSourceStack, playerChatMessage.withUnsignedContent(component));
        } else {
            resolveDisguisedMessage(callback, commandSourceStack, PlayerChatMessage.system(message.text).withUnsignedContent(component));
        }
    }

    private static void resolveSignedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        MinecraftServer minecraftServer = source.getServer();
        CompletableFuture<FilteredText> completableFuture = filterPlainText(source, message);
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = minecraftServer.getChatDecorator().decorate(source.getPlayer(), source, message.decoratedContent());
        source.getChatMessageChainer().append(CompletableFuture.allOf(completableFuture, componentFuture), filtered -> {
            PlayerChatMessage playerChatMessage2 = message.withUnsignedContent(componentFuture.join()).filter(completableFuture.join().mask());
            // Paper end - support asynchronous chat decoration
            callback.accept(playerChatMessage2);
        });
    }

    private static void resolveDisguisedMessage(Consumer<PlayerChatMessage> callback, CommandSourceStack source, PlayerChatMessage message) {
        ChatDecorator chatDecorator = source.getServer().getChatDecorator();
        // Paper start - support asynchronous chat decoration
        CompletableFuture<Component> componentFuture = chatDecorator.decorate(source.getPlayer(), source, message.decoratedContent());
        source.getChatMessageChainer().append(componentFuture, (result) -> callback.accept(message.withUnsignedContent(result)));
        // Paper end - support asynchronous chat decoration
    }

    private static CompletableFuture<FilteredText> filterPlainText(CommandSourceStack source, PlayerChatMessage message) {
        ServerPlayer serverPlayer = source.getPlayer();
        return serverPlayer != null && message.hasSignatureFrom(serverPlayer.getUUID())
            ? serverPlayer.getTextFilter().processStreamMessage(message.signedContent())
            : CompletableFuture.completedFuture(FilteredText.passThrough(message.signedContent()));
    }

    public MessageArgument.Message parse(StringReader stringReader) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(stringReader, true);
    }

    public <S> MessageArgument.Message parse(StringReader stringReader, @Nullable S object) throws CommandSyntaxException {
        return MessageArgument.Message.parseText(stringReader, EntitySelectorParser.allowSelectors(object));
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    public static record Message(String text, MessageArgument.Part[] parts) {
        Component resolveComponent(CommandSourceStack source) throws CommandSyntaxException {
            return this.toComponent(source, EntitySelectorParser.allowSelectors(source));
        }

        public Component toComponent(CommandSourceStack source, boolean canUseSelectors) throws CommandSyntaxException {
            if (this.parts.length != 0 && canUseSelectors) {
                MutableComponent mutableComponent = Component.literal(this.text.substring(0, this.parts[0].start()));
                int i = this.parts[0].start();

                for (MessageArgument.Part part : this.parts) {
                    Component component = part.toComponent(source);
                    if (i < part.start()) {
                        mutableComponent.append(this.text.substring(i, part.start()));
                    }

                    mutableComponent.append(component);
                    i = part.end();
                }

                if (i < this.text.length()) {
                    mutableComponent.append(this.text.substring(i));
                }

                return mutableComponent;
            } else {
                return Component.literal(this.text);
            }
        }

        public static MessageArgument.Message parseText(StringReader reader, boolean allowAtSelectors) throws CommandSyntaxException {
            if (reader.getRemainingLength() > 256) {
                throw MessageArgument.TOO_LONG.create(reader.getRemainingLength(), 256);
            } else {
                String string = reader.getRemaining();
                if (!allowAtSelectors) {
                    reader.setCursor(reader.getTotalLength());
                    return new MessageArgument.Message(string, new MessageArgument.Part[0]);
                } else {
                    List<MessageArgument.Part> list = Lists.newArrayList();
                    int i = reader.getCursor();

                    while (true) {
                        int j;
                        EntitySelector entitySelector;
                        while (true) {
                            if (!reader.canRead()) {
                                return new MessageArgument.Message(string, list.toArray(new MessageArgument.Part[0]));
                            }

                            if (reader.peek() == '@') {
                                j = reader.getCursor();

                                try {
                                    EntitySelectorParser entitySelectorParser = new EntitySelectorParser(reader, true);
                                    entitySelector = entitySelectorParser.parse();
                                    break;
                                } catch (CommandSyntaxException var8) {
                                    if (var8.getType() != EntitySelectorParser.ERROR_MISSING_SELECTOR_TYPE
                                        && var8.getType() != EntitySelectorParser.ERROR_UNKNOWN_SELECTOR_TYPE) {
                                        throw var8;
                                    }

                                    reader.setCursor(j + 1);
                                }
                            } else {
                                reader.skip();
                            }
                        }

                        list.add(new MessageArgument.Part(j - i, reader.getCursor() - i, entitySelector));
                    }
                }
            }
        }
    }

    public static record Part(int start, int end, EntitySelector selector) {
        public Component toComponent(CommandSourceStack source) throws CommandSyntaxException {
            return EntitySelector.joinNames(this.selector.findEntities(source));
        }
    }
}
