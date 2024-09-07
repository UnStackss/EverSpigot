package net.minecraft.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;

public class GsonHelper {
    private static final Gson GSON = new GsonBuilder().create();

    public static boolean isStringValue(JsonObject object, String element) {
        return isValidPrimitive(object, element) && object.getAsJsonPrimitive(element).isString();
    }

    public static boolean isStringValue(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    public static boolean isNumberValue(JsonObject object, String element) {
        return isValidPrimitive(object, element) && object.getAsJsonPrimitive(element).isNumber();
    }

    public static boolean isNumberValue(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    public static boolean isBooleanValue(JsonObject object, String element) {
        return isValidPrimitive(object, element) && object.getAsJsonPrimitive(element).isBoolean();
    }

    public static boolean isBooleanValue(JsonElement object) {
        return object.isJsonPrimitive() && object.getAsJsonPrimitive().isBoolean();
    }

    public static boolean isArrayNode(JsonObject object, String element) {
        return isValidNode(object, element) && object.get(element).isJsonArray();
    }

    public static boolean isObjectNode(JsonObject object, String element) {
        return isValidNode(object, element) && object.get(element).isJsonObject();
    }

    public static boolean isValidPrimitive(JsonObject object, String element) {
        return isValidNode(object, element) && object.get(element).isJsonPrimitive();
    }

    public static boolean isValidNode(@Nullable JsonObject object, String element) {
        return object != null && object.get(element) != null;
    }

    public static JsonElement getNonNull(JsonObject object, String name) {
        JsonElement jsonElement = object.get(name);
        if (jsonElement != null && !jsonElement.isJsonNull()) {
            return jsonElement;
        } else {
            throw new JsonSyntaxException("Missing field " + name);
        }
    }

    public static String convertToString(JsonElement element, String name) {
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a string, was " + getType(element));
        }
    }

    public static String getAsString(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToString(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a string");
        }
    }

    @Nullable
    @Contract("_,_,!null->!null;_,_,null->_")
    public static String getAsString(JsonObject object, String element, @Nullable String defaultStr) {
        return object.has(element) ? convertToString(object.get(element), element) : defaultStr;
    }

    public static Holder<Item> convertToItem(JsonElement element, String name) {
        if (element.isJsonPrimitive()) {
            String string = element.getAsString();
            return BuiltInRegistries.ITEM
                .getHolder(ResourceLocation.parse(string))
                .orElseThrow(() -> new JsonSyntaxException("Expected " + name + " to be an item, was unknown string '" + string + "'"));
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be an item, was " + getType(element));
        }
    }

    public static Holder<Item> getAsItem(JsonObject object, String key) {
        if (object.has(key)) {
            return convertToItem(object.get(key), key);
        } else {
            throw new JsonSyntaxException("Missing " + key + ", expected to find an item");
        }
    }

    @Nullable
    @Contract("_,_,!null->!null;_,_,null->_")
    public static Holder<Item> getAsItem(JsonObject object, String key, @Nullable Holder<Item> defaultValue) {
        return object.has(key) ? convertToItem(object.get(key), key) : defaultValue;
    }

    public static boolean convertToBoolean(JsonElement element, String name) {
        if (element.isJsonPrimitive()) {
            return element.getAsBoolean();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Boolean, was " + getType(element));
        }
    }

    public static boolean getAsBoolean(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToBoolean(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Boolean");
        }
    }

    public static boolean getAsBoolean(JsonObject object, String element, boolean defaultBoolean) {
        return object.has(element) ? convertToBoolean(object.get(element), element) : defaultBoolean;
    }

    public static double convertToDouble(JsonElement object, String name) {
        if (object.isJsonPrimitive() && object.getAsJsonPrimitive().isNumber()) {
            return object.getAsDouble();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Double, was " + getType(object));
        }
    }

    public static double getAsDouble(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToDouble(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Double");
        }
    }

    public static double getAsDouble(JsonObject object, String element, double defaultDouble) {
        return object.has(element) ? convertToDouble(object.get(element), element) : defaultDouble;
    }

    public static float convertToFloat(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsFloat();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Float, was " + getType(element));
        }
    }

    public static float getAsFloat(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToFloat(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Float");
        }
    }

    public static float getAsFloat(JsonObject object, String element, float defaultFloat) {
        return object.has(element) ? convertToFloat(object.get(element), element) : defaultFloat;
    }

    public static long convertToLong(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsLong();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Long, was " + getType(element));
        }
    }

    public static long getAsLong(JsonObject object, String name) {
        if (object.has(name)) {
            return convertToLong(object.get(name), name);
        } else {
            throw new JsonSyntaxException("Missing " + name + ", expected to find a Long");
        }
    }

    public static long getAsLong(JsonObject object, String element, long defaultLong) {
        return object.has(element) ? convertToLong(object.get(element), element) : defaultLong;
    }

    public static int convertToInt(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Int, was " + getType(element));
        }
    }

    public static int getAsInt(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToInt(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Int");
        }
    }

    public static int getAsInt(JsonObject object, String element, int defaultInt) {
        return object.has(element) ? convertToInt(object.get(element), element) : defaultInt;
    }

    public static byte convertToByte(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsByte();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Byte, was " + getType(element));
        }
    }

    public static byte getAsByte(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToByte(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Byte");
        }
    }

    public static byte getAsByte(JsonObject object, String element, byte defaultByte) {
        return object.has(element) ? convertToByte(object.get(element), element) : defaultByte;
    }

    public static char convertToCharacter(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsCharacter();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Character, was " + getType(element));
        }
    }

    public static char getAsCharacter(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToCharacter(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Character");
        }
    }

    public static char getAsCharacter(JsonObject object, String element, char defaultChar) {
        return object.has(element) ? convertToCharacter(object.get(element), element) : defaultChar;
    }

    public static BigDecimal convertToBigDecimal(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsBigDecimal();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a BigDecimal, was " + getType(element));
        }
    }

    public static BigDecimal getAsBigDecimal(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToBigDecimal(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a BigDecimal");
        }
    }

    public static BigDecimal getAsBigDecimal(JsonObject object, String element, BigDecimal defaultBigDecimal) {
        return object.has(element) ? convertToBigDecimal(object.get(element), element) : defaultBigDecimal;
    }

    public static BigInteger convertToBigInteger(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsBigInteger();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a BigInteger, was " + getType(element));
        }
    }

    public static BigInteger getAsBigInteger(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToBigInteger(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a BigInteger");
        }
    }

    public static BigInteger getAsBigInteger(JsonObject object, String element, BigInteger defaultBigInteger) {
        return object.has(element) ? convertToBigInteger(object.get(element), element) : defaultBigInteger;
    }

    public static short convertToShort(JsonElement element, String name) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsShort();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a Short, was " + getType(element));
        }
    }

    public static short getAsShort(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToShort(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a Short");
        }
    }

    public static short getAsShort(JsonObject object, String element, short defaultShort) {
        return object.has(element) ? convertToShort(object.get(element), element) : defaultShort;
    }

    public static JsonObject convertToJsonObject(JsonElement element, String name) {
        if (element.isJsonObject()) {
            return element.getAsJsonObject();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a JsonObject, was " + getType(element));
        }
    }

    public static JsonObject getAsJsonObject(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToJsonObject(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a JsonObject");
        }
    }

    @Nullable
    @Contract("_,_,!null->!null;_,_,null->_")
    public static JsonObject getAsJsonObject(JsonObject object, String element, @Nullable JsonObject defaultObject) {
        return object.has(element) ? convertToJsonObject(object.get(element), element) : defaultObject;
    }

    public static JsonArray convertToJsonArray(JsonElement element, String name) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray();
        } else {
            throw new JsonSyntaxException("Expected " + name + " to be a JsonArray, was " + getType(element));
        }
    }

    public static JsonArray getAsJsonArray(JsonObject object, String element) {
        if (object.has(element)) {
            return convertToJsonArray(object.get(element), element);
        } else {
            throw new JsonSyntaxException("Missing " + element + ", expected to find a JsonArray");
        }
    }

    @Nullable
    @Contract("_,_,!null->!null;_,_,null->_")
    public static JsonArray getAsJsonArray(JsonObject object, String name, @Nullable JsonArray defaultArray) {
        return object.has(name) ? convertToJsonArray(object.get(name), name) : defaultArray;
    }

    public static <T> T convertToObject(@Nullable JsonElement element, String name, JsonDeserializationContext context, Class<? extends T> type) {
        if (element != null) {
            return context.deserialize(element, type);
        } else {
            throw new JsonSyntaxException("Missing " + name);
        }
    }

    public static <T> T getAsObject(JsonObject object, String element, JsonDeserializationContext context, Class<? extends T> type) {
        if (object.has(element)) {
            return convertToObject(object.get(element), element, context, type);
        } else {
            throw new JsonSyntaxException("Missing " + element);
        }
    }

    @Nullable
    @Contract("_,_,!null,_,_->!null;_,_,null,_,_->_")
    public static <T> T getAsObject(JsonObject object, String element, @Nullable T defaultValue, JsonDeserializationContext context, Class<? extends T> type) {
        return object.has(element) ? convertToObject(object.get(element), element, context, type) : defaultValue;
    }

    public static String getType(@Nullable JsonElement element) {
        String string = StringUtils.abbreviateMiddle(String.valueOf(element), "...", 10);
        if (element == null) {
            return "null (missing)";
        } else if (element.isJsonNull()) {
            return "null (json)";
        } else if (element.isJsonArray()) {
            return "an array (" + string + ")";
        } else if (element.isJsonObject()) {
            return "an object (" + string + ")";
        } else {
            if (element.isJsonPrimitive()) {
                JsonPrimitive jsonPrimitive = element.getAsJsonPrimitive();
                if (jsonPrimitive.isNumber()) {
                    return "a number (" + string + ")";
                }

                if (jsonPrimitive.isBoolean()) {
                    return "a boolean (" + string + ")";
                }
            }

            return string;
        }
    }

    @Nullable
    public static <T> T fromNullableJson(Gson gson, Reader reader, Class<T> type, boolean lenient) {
        try {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(lenient);
            return gson.getAdapter(type).read(jsonReader);
        } catch (IOException var5) {
            throw new JsonParseException(var5);
        }
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> type, boolean lenient) {
        T object = fromNullableJson(gson, reader, type, lenient);
        if (object == null) {
            throw new JsonParseException("JSON data was null or empty");
        } else {
            return object;
        }
    }

    @Nullable
    public static <T> T fromNullableJson(Gson gson, Reader reader, TypeToken<T> typeToken, boolean lenient) {
        try {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(lenient);
            return gson.getAdapter(typeToken).read(jsonReader);
        } catch (IOException var5) {
            throw new JsonParseException(var5);
        }
    }

    public static <T> T fromJson(Gson gson, Reader reader, TypeToken<T> typeToken, boolean lenient) {
        T object = fromNullableJson(gson, reader, typeToken, lenient);
        if (object == null) {
            throw new JsonParseException("JSON data was null or empty");
        } else {
            return object;
        }
    }

    @Nullable
    public static <T> T fromNullableJson(Gson gson, String content, TypeToken<T> typeToken, boolean lenient) {
        return fromNullableJson(gson, new StringReader(content), typeToken, lenient);
    }

    public static <T> T fromJson(Gson gson, String content, Class<T> type, boolean lenient) {
        return fromJson(gson, new StringReader(content), type, lenient);
    }

    @Nullable
    public static <T> T fromNullableJson(Gson gson, String content, Class<T> type, boolean lenient) {
        return fromNullableJson(gson, new StringReader(content), type, lenient);
    }

    public static <T> T fromJson(Gson gson, Reader reader, TypeToken<T> typeToken) {
        return fromJson(gson, reader, typeToken, false);
    }

    @Nullable
    public static <T> T fromNullableJson(Gson gson, String content, TypeToken<T> typeToken) {
        return fromNullableJson(gson, content, typeToken, false);
    }

    public static <T> T fromJson(Gson gson, Reader reader, Class<T> type) {
        return fromJson(gson, reader, type, false);
    }

    public static <T> T fromJson(Gson gson, String content, Class<T> type) {
        return fromJson(gson, content, type, false);
    }

    public static JsonObject parse(String content, boolean lenient) {
        return parse(new StringReader(content), lenient);
    }

    public static JsonObject parse(Reader reader, boolean lenient) {
        return fromJson(GSON, reader, JsonObject.class, lenient);
    }

    public static JsonObject parse(String content) {
        return parse(content, false);
    }

    public static JsonObject parse(Reader reader) {
        return parse(reader, false);
    }

    public static JsonArray parseArray(String content) {
        return parseArray(new StringReader(content));
    }

    public static JsonArray parseArray(Reader reader) {
        return fromJson(GSON, reader, JsonArray.class, false);
    }

    public static String toStableString(JsonElement json) {
        StringWriter stringWriter = new StringWriter();
        JsonWriter jsonWriter = new JsonWriter(stringWriter);

        try {
            writeValue(jsonWriter, json, Comparator.naturalOrder());
        } catch (IOException var4) {
            throw new AssertionError(var4);
        }

        return stringWriter.toString();
    }

    public static void writeValue(JsonWriter writer, @Nullable JsonElement json, @Nullable Comparator<String> comparator) throws IOException {
        if (json == null || json.isJsonNull()) {
            writer.nullValue();
        } else if (json.isJsonPrimitive()) {
            JsonPrimitive jsonPrimitive = json.getAsJsonPrimitive();
            if (jsonPrimitive.isNumber()) {
                writer.value(jsonPrimitive.getAsNumber());
            } else if (jsonPrimitive.isBoolean()) {
                writer.value(jsonPrimitive.getAsBoolean());
            } else {
                writer.value(jsonPrimitive.getAsString());
            }
        } else if (json.isJsonArray()) {
            writer.beginArray();

            for (JsonElement jsonElement : json.getAsJsonArray()) {
                writeValue(writer, jsonElement, comparator);
            }

            writer.endArray();
        } else {
            if (!json.isJsonObject()) {
                throw new IllegalArgumentException("Couldn't write " + json.getClass());
            }

            writer.beginObject();

            for (Entry<String, JsonElement> entry : sortByKeyIfNeeded(json.getAsJsonObject().entrySet(), comparator)) {
                writer.name(entry.getKey());
                writeValue(writer, entry.getValue(), comparator);
            }

            writer.endObject();
        }
    }

    private static Collection<Entry<String, JsonElement>> sortByKeyIfNeeded(
        Collection<Entry<String, JsonElement>> entries, @Nullable Comparator<String> comparator
    ) {
        if (comparator == null) {
            return entries;
        } else {
            List<Entry<String, JsonElement>> list = new ArrayList<>(entries);
            list.sort(Entry.comparingByKey(comparator));
            return list;
        }
    }
}
