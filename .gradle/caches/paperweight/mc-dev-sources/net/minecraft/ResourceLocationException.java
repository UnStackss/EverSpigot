package net.minecraft;

import org.apache.commons.lang3.StringEscapeUtils;

public class ResourceLocationException extends RuntimeException {
    public ResourceLocationException(String message) {
        super(StringEscapeUtils.escapeJava(message));
    }

    public ResourceLocationException(String message, Throwable throwable) {
        super(StringEscapeUtils.escapeJava(message), throwable);
    }
}
