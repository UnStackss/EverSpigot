package net.minecraft.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class StringUtil {
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");
    private static final Pattern LINE_PATTERN = Pattern.compile("\\r\\n|\\v");
    private static final Pattern LINE_END_PATTERN = Pattern.compile("(?:\\r\\n|\\v)$");

    public static String formatTickDuration(int ticks, float tickRate) {
        int i = Mth.floor((float)ticks / tickRate);
        int j = i / 60;
        i %= 60;
        int k = j / 60;
        j %= 60;
        return k > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", k, j, i) : String.format(Locale.ROOT, "%02d:%02d", j, i);
    }

    public static String stripColor(String text) {
        return STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    public static boolean isNullOrEmpty(@Nullable String text) {
        return StringUtils.isEmpty(text);
    }

    public static String truncateStringIfNecessary(String text, int maxLength, boolean addEllipsis) {
        if (text.length() <= maxLength) {
            return text;
        } else {
            return addEllipsis && maxLength > 3 ? text.substring(0, maxLength - 3) + "..." : text.substring(0, maxLength);
        }
    }

    public static int lineCount(String text) {
        if (text.isEmpty()) {
            return 0;
        } else {
            Matcher matcher = LINE_PATTERN.matcher(text);
            int i = 1;

            while (matcher.find()) {
                i++;
            }

            return i;
        }
    }

    public static boolean endsWithNewLine(String text) {
        return LINE_END_PATTERN.matcher(text).find();
    }

    public static String trimChatMessage(String text) {
        return truncateStringIfNecessary(text, 256, false);
    }

    public static boolean isAllowedChatCharacter(char c) {
        return c != 167 && c >= ' ' && c != 127;
    }

    public static boolean isValidPlayerName(String name) {
        return name.length() <= 16 && name.chars().filter(c -> c <= 32 || c >= 127).findAny().isEmpty();
    }

    // Paper start - Username validation
    public static boolean isReasonablePlayerName(final String name) {
        if (name.isEmpty() || name.length() > 16) {
            return false;
        }

        for (int i = 0, len = name.length(); i < len; ++i) {
            final char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || (c == '_' || c == '.')) {
                continue;
            }

            return false;
        }

        return true;
    }
    // Paper end - Username validation

    public static String filterText(String string) {
        return filterText(string, false);
    }

    public static String filterText(String string, boolean allowLinebreak) {
        StringBuilder stringBuilder = new StringBuilder();

        for (char c : string.toCharArray()) {
            if (isAllowedChatCharacter(c)) {
                stringBuilder.append(c);
            } else if (allowLinebreak && c == '\n') {
                stringBuilder.append(c);
            }
        }

        return stringBuilder.toString();
    }

    public static boolean isWhitespace(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c);
    }

    public static boolean isBlank(@Nullable String string) {
        return string == null || string.length() == 0 || string.chars().allMatch(StringUtil::isWhitespace);
    }
}
