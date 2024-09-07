package net.minecraft.network.chat;

public class ThrowingComponent extends Exception {
    private final Component component;

    public ThrowingComponent(Component messageText) {
        super(messageText.getString());
        this.component = messageText;
    }

    public ThrowingComponent(Component messageText, Throwable cause) {
        super(messageText.getString(), cause);
        this.component = messageText;
    }

    public Component getComponent() {
        return this.component;
    }
}
