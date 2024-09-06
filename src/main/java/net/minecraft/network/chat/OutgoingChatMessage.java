package net.minecraft.network.chat;

import net.minecraft.server.level.ServerPlayer;

public interface OutgoingChatMessage {
    Component content();

    void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params);

    static OutgoingChatMessage create(PlayerChatMessage message) {
        return (OutgoingChatMessage)(message.isSystem()
            ? new OutgoingChatMessage.Disguised(message.decoratedContent())
            : new OutgoingChatMessage.Player(message));
    }

    public static record Disguised(@Override Component content) implements OutgoingChatMessage {
        @Override
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params) {
            sender.connection.sendDisguisedChatMessage(this.content, params);
        }
    }

    public static record Player(PlayerChatMessage message) implements OutgoingChatMessage {
        @Override
        public Component content() {
            return this.message.decoratedContent();
        }

        @Override
        public void sendToPlayer(ServerPlayer sender, boolean filterMaskEnabled, ChatType.Bound params) {
            PlayerChatMessage playerChatMessage = this.message.filter(filterMaskEnabled);
            if (!playerChatMessage.isFullyFiltered()) {
                sender.connection.sendPlayerChatMessage(playerChatMessage, params);
            }
        }
    }
}
