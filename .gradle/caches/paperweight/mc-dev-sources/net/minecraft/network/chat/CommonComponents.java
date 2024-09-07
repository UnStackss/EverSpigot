package net.minecraft.network.chat;

import java.util.Arrays;
import java.util.Collection;

public class CommonComponents {
    public static final Component EMPTY = Component.empty();
    public static final Component OPTION_ON = Component.translatable("options.on");
    public static final Component OPTION_OFF = Component.translatable("options.off");
    public static final Component GUI_DONE = Component.translatable("gui.done");
    public static final Component GUI_CANCEL = Component.translatable("gui.cancel");
    public static final Component GUI_YES = Component.translatable("gui.yes");
    public static final Component GUI_NO = Component.translatable("gui.no");
    public static final Component GUI_OK = Component.translatable("gui.ok");
    public static final Component GUI_PROCEED = Component.translatable("gui.proceed");
    public static final Component GUI_CONTINUE = Component.translatable("gui.continue");
    public static final Component GUI_BACK = Component.translatable("gui.back");
    public static final Component GUI_TO_TITLE = Component.translatable("gui.toTitle");
    public static final Component GUI_ACKNOWLEDGE = Component.translatable("gui.acknowledge");
    public static final Component GUI_OPEN_IN_BROWSER = Component.translatable("chat.link.open");
    public static final Component GUI_COPY_LINK_TO_CLIPBOARD = Component.translatable("gui.copy_link_to_clipboard");
    public static final Component GUI_DISCONNECT = Component.translatable("menu.disconnect");
    public static final Component TRANSFER_CONNECT_FAILED = Component.translatable("connect.failed.transfer");
    public static final Component CONNECT_FAILED = Component.translatable("connect.failed");
    public static final Component NEW_LINE = Component.literal("\n");
    public static final Component NARRATION_SEPARATOR = Component.literal(". ");
    public static final Component ELLIPSIS = Component.literal("...");
    public static final Component SPACE = space();

    public static MutableComponent space() {
        return Component.literal(" ");
    }

    public static MutableComponent days(long days) {
        return Component.translatable("gui.days", days);
    }

    public static MutableComponent hours(long hours) {
        return Component.translatable("gui.hours", hours);
    }

    public static MutableComponent minutes(long minutes) {
        return Component.translatable("gui.minutes", minutes);
    }

    public static Component optionStatus(boolean on) {
        return on ? OPTION_ON : OPTION_OFF;
    }

    public static MutableComponent optionStatus(Component text, boolean value) {
        return Component.translatable(value ? "options.on.composed" : "options.off.composed", text);
    }

    public static MutableComponent optionNameValue(Component text, Component value) {
        return Component.translatable("options.generic_value", text, value);
    }

    public static MutableComponent joinForNarration(Component... sentences) {
        MutableComponent mutableComponent = Component.empty();

        for (int i = 0; i < sentences.length; i++) {
            mutableComponent.append(sentences[i]);
            if (i != sentences.length - 1) {
                mutableComponent.append(NARRATION_SEPARATOR);
            }
        }

        return mutableComponent;
    }

    public static Component joinLines(Component... texts) {
        return joinLines(Arrays.asList(texts));
    }

    public static Component joinLines(Collection<? extends Component> texts) {
        return ComponentUtils.formatList(texts, NEW_LINE);
    }
}
