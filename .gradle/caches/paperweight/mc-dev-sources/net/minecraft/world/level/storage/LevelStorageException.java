package net.minecraft.world.level.storage;

import net.minecraft.network.chat.Component;

public class LevelStorageException extends RuntimeException {
    private final Component messageComponent;

    public LevelStorageException(Component messageText) {
        super(messageText.getString());
        this.messageComponent = messageText;
    }

    public Component getMessageComponent() {
        return this.messageComponent;
    }
}
