package net.minecraft.advancements.critereon;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.util.GsonHelper;

public record WrappedMinMaxBounds(@Nullable Float min, @Nullable Float max) {
    public static final WrappedMinMaxBounds ANY = new WrappedMinMaxBounds(null, null);
    public static final SimpleCommandExceptionType ERROR_INTS_ONLY = new SimpleCommandExceptionType(Component.translatable("argument.range.ints"));

    public static WrappedMinMaxBounds exactly(float value) {
        return new WrappedMinMaxBounds(value, value);
    }

    public static WrappedMinMaxBounds between(float min, float max) {
        return new WrappedMinMaxBounds(min, max);
    }

    public static WrappedMinMaxBounds atLeast(float value) {
        return new WrappedMinMaxBounds(value, null);
    }

    public static WrappedMinMaxBounds atMost(float value) {
        return new WrappedMinMaxBounds(null, value);
    }

    public boolean matches(float value) {
        return (this.min == null || this.max == null || !(this.min > this.max) || !(this.min > value) || !(this.max < value))
            && (this.min == null || !(this.min > value))
            && (this.max == null || !(this.max < value));
    }

    public boolean matchesSqr(double value) {
        return (
                this.min == null
                    || this.max == null
                    || !(this.min > this.max)
                    || !((double)(this.min * this.min) > value)
                    || !((double)(this.max * this.max) < value)
            )
            && (this.min == null || !((double)(this.min * this.min) > value))
            && (this.max == null || !((double)(this.max * this.max) < value));
    }

    public JsonElement serializeToJson() {
        if (this == ANY) {
            return JsonNull.INSTANCE;
        } else if (this.min != null && this.max != null && this.min.equals(this.max)) {
            return new JsonPrimitive(this.min);
        } else {
            JsonObject jsonObject = new JsonObject();
            if (this.min != null) {
                jsonObject.addProperty("min", this.min);
            }

            if (this.max != null) {
                jsonObject.addProperty("max", this.min);
            }

            return jsonObject;
        }
    }

    public static WrappedMinMaxBounds fromJson(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ANY;
        } else if (GsonHelper.isNumberValue(json)) {
            float f = GsonHelper.convertToFloat(json, "value");
            return new WrappedMinMaxBounds(f, f);
        } else {
            JsonObject jsonObject = GsonHelper.convertToJsonObject(json, "value");
            Float float_ = jsonObject.has("min") ? GsonHelper.getAsFloat(jsonObject, "min") : null;
            Float float2 = jsonObject.has("max") ? GsonHelper.getAsFloat(jsonObject, "max") : null;
            return new WrappedMinMaxBounds(float_, float2);
        }
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean allowFloats) throws CommandSyntaxException {
        return fromReader(reader, allowFloats, value -> value);
    }

    public static WrappedMinMaxBounds fromReader(StringReader reader, boolean allowFloats, Function<Float, Float> transform) throws CommandSyntaxException {
        if (!reader.canRead()) {
            throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
        } else {
            int i = reader.getCursor();
            Float float_ = optionallyFormat(readNumber(reader, allowFloats), transform);
            Float float2;
            if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                reader.skip();
                reader.skip();
                float2 = optionallyFormat(readNumber(reader, allowFloats), transform);
                if (float_ == null && float2 == null) {
                    reader.setCursor(i);
                    throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
                }
            } else {
                if (!allowFloats && reader.canRead() && reader.peek() == '.') {
                    reader.setCursor(i);
                    throw ERROR_INTS_ONLY.createWithContext(reader);
                }

                float2 = float_;
            }

            if (float_ == null && float2 == null) {
                reader.setCursor(i);
                throw MinMaxBounds.ERROR_EMPTY.createWithContext(reader);
            } else {
                return new WrappedMinMaxBounds(float_, float2);
            }
        }
    }

    @Nullable
    private static Float readNumber(StringReader reader, boolean allowFloats) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedNumber(reader, allowFloats)) {
            reader.skip();
        }

        String string = reader.getString().substring(i, reader.getCursor());
        if (string.isEmpty()) {
            return null;
        } else {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException var5) {
                if (allowFloats) {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, string);
                } else {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, string);
                }
            }
        }
    }

    private static boolean isAllowedNumber(StringReader reader, boolean allowFloats) {
        char c = reader.peek();
        return c >= '0' && c <= '9' || c == '-' || allowFloats && c == '.' && (!reader.canRead(2) || reader.peek(1) != '.');
    }

    @Nullable
    private static Float optionallyFormat(@Nullable Float value, Function<Float, Float> function) {
        return value == null ? null : function.apply(value);
    }
}
