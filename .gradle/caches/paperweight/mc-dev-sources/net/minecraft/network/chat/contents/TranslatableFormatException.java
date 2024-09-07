package net.minecraft.network.chat.contents;

import java.util.Locale;

public class TranslatableFormatException extends IllegalArgumentException {
    public TranslatableFormatException(TranslatableContents text, String message) {
        super(String.format(Locale.ROOT, "Error parsing: %s: %s", text, message));
    }

    public TranslatableFormatException(TranslatableContents text, int index) {
        super(String.format(Locale.ROOT, "Invalid index %d requested for %s", index, text));
    }

    public TranslatableFormatException(TranslatableContents text, Throwable cause) {
        super(String.format(Locale.ROOT, "Error while parsing: %s", text), cause);
    }
}
