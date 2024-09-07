package net.minecraft.world.level.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;

public class SignText {
    private static final Codec<Component[]> LINES_CODEC = ComponentSerialization.FLAT_CODEC
        .listOf()
        .comapFlatMap(
            messages -> Util.fixedSize((List<Component>)messages, 4).map(list -> new Component[]{list.get(0), list.get(1), list.get(2), list.get(3)}),
            messages -> List.of(messages[0], messages[1], messages[2], messages[3])
        );
    public static final Codec<SignText> DIRECT_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    LINES_CODEC.fieldOf("messages").forGetter(signText -> signText.messages),
                    LINES_CODEC.lenientOptionalFieldOf("filtered_messages").forGetter(SignText::filteredMessages),
                    DyeColor.CODEC.fieldOf("color").orElse(DyeColor.BLACK).forGetter(signText -> signText.color),
                    Codec.BOOL.fieldOf("has_glowing_text").orElse(false).forGetter(signText -> signText.hasGlowingText)
                )
                .apply(instance, SignText::load)
    );
    public static final int LINES = 4;
    private final Component[] messages;
    private final Component[] filteredMessages;
    private final DyeColor color;
    private final boolean hasGlowingText;
    @Nullable
    private FormattedCharSequence[] renderMessages;
    private boolean renderMessagedFiltered;

    public SignText() {
        this(emptyMessages(), emptyMessages(), DyeColor.BLACK, false);
    }

    public SignText(Component[] messages, Component[] filteredMessages, DyeColor color, boolean glowing) {
        this.messages = messages;
        this.filteredMessages = filteredMessages;
        this.color = color;
        this.hasGlowingText = glowing;
    }

    private static Component[] emptyMessages() {
        return new Component[]{CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY, CommonComponents.EMPTY};
    }

    private static SignText load(Component[] messages, Optional<Component[]> filteredMessages, DyeColor color, boolean glowing) {
        return new SignText(messages, filteredMessages.orElse(Arrays.copyOf(messages, messages.length)), color, glowing);
    }

    public boolean hasGlowingText() {
        return this.hasGlowingText;
    }

    public SignText setHasGlowingText(boolean glowing) {
        return glowing == this.hasGlowingText ? this : new SignText(this.messages, this.filteredMessages, this.color, glowing);
    }

    public DyeColor getColor() {
        return this.color;
    }

    public SignText setColor(DyeColor color) {
        return color == this.getColor() ? this : new SignText(this.messages, this.filteredMessages, color, this.hasGlowingText);
    }

    public Component getMessage(int line, boolean filtered) {
        return this.getMessages(filtered)[line];
    }

    public SignText setMessage(int line, Component message) {
        return this.setMessage(line, message, message);
    }

    public SignText setMessage(int line, Component message, Component filteredMessage) {
        Component[] components = Arrays.copyOf(this.messages, this.messages.length);
        Component[] components2 = Arrays.copyOf(this.filteredMessages, this.filteredMessages.length);
        components[line] = message;
        components2[line] = filteredMessage;
        return new SignText(components, components2, this.color, this.hasGlowingText);
    }

    public boolean hasMessage(Player player) {
        return Arrays.stream(this.getMessages(player.isTextFilteringEnabled())).anyMatch(text -> !text.getString().isEmpty());
    }

    public Component[] getMessages(boolean filtered) {
        return filtered ? this.filteredMessages : this.messages;
    }

    public FormattedCharSequence[] getRenderMessages(boolean filtered, Function<Component, FormattedCharSequence> messageOrderer) {
        if (this.renderMessages == null || this.renderMessagedFiltered != filtered) {
            this.renderMessagedFiltered = filtered;
            this.renderMessages = new FormattedCharSequence[4];

            for (int i = 0; i < 4; i++) {
                this.renderMessages[i] = messageOrderer.apply(this.getMessage(i, filtered));
            }
        }

        return this.renderMessages;
    }

    private Optional<Component[]> filteredMessages() {
        for (int i = 0; i < 4; i++) {
            if (!this.filteredMessages[i].equals(this.messages[i])) {
                return Optional.of(this.filteredMessages);
            }
        }

        return Optional.empty();
    }

    public boolean hasAnyClickCommands(Player player) {
        for (Component component : this.getMessages(player.isTextFilteringEnabled())) {
            Style style = component.getStyle();
            ClickEvent clickEvent = style.getClickEvent();
            if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
                return true;
            }
        }

        return false;
    }
}
