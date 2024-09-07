package net.minecraft.commands;

import net.minecraft.network.chat.Component;

public class FunctionInstantiationException extends Exception {
    private final Component messageComponent;

    public FunctionInstantiationException(Component message) {
        super(message.getString());
        this.messageComponent = message;
    }

    public Component messageComponent() {
        return this.messageComponent;
    }
}
