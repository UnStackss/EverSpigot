package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Locale;

public record ColorRGBA(int rgba) {
    private static final String CUSTOM_COLOR_PREFIX = "#";
    public static final Codec<ColorRGBA> CODEC = Codec.STRING.comapFlatMap(code -> {
        if (!code.startsWith("#")) {
            return DataResult.error(() -> "Not a color code: " + code);
        } else {
            try {
                int i = (int)Long.parseLong(code.substring(1), 16);
                return DataResult.success(new ColorRGBA(i));
            } catch (NumberFormatException var2) {
                return DataResult.error(() -> "Exception parsing color code: " + var2.getMessage());
            }
        }
    }, ColorRGBA::formatValue);

    private String formatValue() {
        return String.format(Locale.ROOT, "#%08X", this.rgba);
    }

    @Override
    public String toString() {
        return this.formatValue();
    }
}
