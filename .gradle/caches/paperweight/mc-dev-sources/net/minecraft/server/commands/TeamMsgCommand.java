package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

public class TeamMsgCommand {
    private static final Style SUGGEST_STYLE = Style.EMPTY
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.type.team.hover")))
        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/teammsg "));
    private static final SimpleCommandExceptionType ERROR_NOT_ON_TEAM = new SimpleCommandExceptionType(Component.translatable("commands.teammsg.failed.noteam"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("teammsg")
                .then(
                    Commands.argument("message", MessageArgument.message())
                        .executes(
                            context -> {
                                CommandSourceStack commandSourceStack = context.getSource();
                                Entity entity = commandSourceStack.getEntityOrException();
                                PlayerTeam playerTeam = entity.getTeam();
                                if (playerTeam == null) {
                                    throw ERROR_NOT_ON_TEAM.create();
                                } else {
                                    List<ServerPlayer> list = commandSourceStack.getServer()
                                        .getPlayerList()
                                        .getPlayers()
                                        .stream()
                                        .filter(player -> player == entity || player.getTeam() == playerTeam)
                                        .toList();
                                    if (!list.isEmpty()) {
                                        MessageArgument.resolveChatMessage(
                                            context, "message", message -> sendMessage(commandSourceStack, entity, playerTeam, list, message)
                                        );
                                    }

                                    return list.size();
                                }
                            }
                        )
                )
        );
        dispatcher.register(Commands.literal("tm").redirect(literalCommandNode));
    }

    private static void sendMessage(CommandSourceStack source, Entity entity, PlayerTeam team, List<ServerPlayer> recipients, PlayerChatMessage message) {
        Component component = team.getFormattedDisplayName().withStyle(SUGGEST_STYLE);
        ChatType.Bound bound = ChatType.bind(ChatType.TEAM_MSG_COMMAND_INCOMING, source).withTargetName(component);
        ChatType.Bound bound2 = ChatType.bind(ChatType.TEAM_MSG_COMMAND_OUTGOING, source).withTargetName(component);
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(message);
        boolean bl = false;

        for (ServerPlayer serverPlayer : recipients) {
            ChatType.Bound bound3 = serverPlayer == entity ? bound2 : bound;
            boolean bl2 = source.shouldFilterMessageTo(serverPlayer);
            serverPlayer.sendChatMessage(outgoingChatMessage, bl2, bound3);
            bl |= bl2 && message.isFullyFiltered();
        }

        if (bl) {
            source.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }
    }
}
