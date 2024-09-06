package net.minecraft.network.chat;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;

@FunctionalInterface
public interface ChatDecorator {
    ChatDecorator PLAIN = (sender, message) -> message;

    Component decorate(@Nullable ServerPlayer sender, Component message);
}
