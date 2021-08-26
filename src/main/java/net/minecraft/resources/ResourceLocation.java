package net.minecraft.resources;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Type;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.GsonHelper;

public final class ResourceLocation implements Comparable<ResourceLocation> {
    public static final Codec<ResourceLocation> CODEC = Codec.STRING
        .<ResourceLocation>comapFlatMap(ResourceLocation::read, ResourceLocation::toString)
        .stable();
    public static final StreamCodec<ByteBuf, ResourceLocation> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
        .map(ResourceLocation::parse, ResourceLocation::toString);
    public static final SimpleCommandExceptionType ERROR_INVALID = new SimpleCommandExceptionType(Component.translatable("argument.id.invalid"));
    public static final char NAMESPACE_SEPARATOR = ':';
    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final String REALMS_NAMESPACE = "realms";
    public static final String PAPER_NAMESPACE = "paper"; // Paper
    private final String namespace;
    private final String path;

    private ResourceLocation(String namespace, String path) {
        assert isValidNamespace(namespace);

        assert isValidPath(path);

        this.namespace = namespace;
        this.path = path;
    }

    private static ResourceLocation createUntrusted(String namespace, String path) {
        return new ResourceLocation(assertValidNamespace(namespace, path), assertValidPath(namespace, path));
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return createUntrusted(namespace, path);
    }

    public static ResourceLocation parse(String id) {
        return bySeparator(id, ':');
    }

    public static ResourceLocation withDefaultNamespace(String path) {
        return new ResourceLocation("minecraft", assertValidPath("minecraft", path));
    }

    @Nullable
    public static ResourceLocation tryParse(String id) {
        return tryBySeparator(id, ':');
    }

    @Nullable
    public static ResourceLocation tryBuild(String namespace, String path) {
        return isValidNamespace(namespace) && isValidPath(path) ? new ResourceLocation(namespace, path) : null;
    }

    public static ResourceLocation bySeparator(String id, char delimiter) {
        int i = id.indexOf(delimiter);
        if (i >= 0) {
            String string = id.substring(i + 1);
            if (i != 0) {
                String string2 = id.substring(0, i);
                return createUntrusted(string2, string);
            } else {
                return withDefaultNamespace(string);
            }
        } else {
            return withDefaultNamespace(id);
        }
    }

    @Nullable
    public static ResourceLocation tryBySeparator(String id, char delimiter) {
        int i = id.indexOf(delimiter);
        if (i >= 0) {
            String string = id.substring(i + 1);
            if (!isValidPath(string)) {
                return null;
            } else if (i != 0) {
                String string2 = id.substring(0, i);
                return isValidNamespace(string2) ? new ResourceLocation(string2, string) : null;
            } else {
                return new ResourceLocation("minecraft", string);
            }
        } else {
            return isValidPath(id) ? new ResourceLocation("minecraft", id) : null;
        }
    }

    public static DataResult<ResourceLocation> read(String id) {
        try {
            return DataResult.success(parse(id));
        } catch (ResourceLocationException var2) {
            return DataResult.error(() -> "Not a valid resource location: " + id + " " + var2.getMessage());
        }
    }

    public String getPath() {
        return this.path;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public ResourceLocation withPath(String path) {
        return new ResourceLocation(this.namespace, assertValidPath(this.namespace, path));
    }

    public ResourceLocation withPath(UnaryOperator<String> pathFunction) {
        return this.withPath(pathFunction.apply(this.path));
    }

    public ResourceLocation withPrefix(String prefix) {
        return this.withPath(prefix + this.path);
    }

    public ResourceLocation withSuffix(String suffix) {
        return this.withPath(this.path + suffix);
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.path;
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof ResourceLocation resourceLocation
                && this.namespace.equals(resourceLocation.namespace)
                && this.path.equals(resourceLocation.path);
    }

    @Override
    public int hashCode() {
        return 31 * this.namespace.hashCode() + this.path.hashCode();
    }

    @Override
    public int compareTo(ResourceLocation resourceLocation) {
        int i = this.path.compareTo(resourceLocation.path);
        if (i == 0) {
            i = this.namespace.compareTo(resourceLocation.namespace);
        }

        return i;
    }

    public String toDebugFileName() {
        return this.toString().replace('/', '_').replace(':', '_');
    }

    public String toLanguageKey() {
        return this.namespace + "." + this.path;
    }

    public String toShortLanguageKey() {
        return this.namespace.equals("minecraft") ? this.path : this.toLanguageKey();
    }

    public String toLanguageKey(String prefix) {
        return prefix + "." + this.toLanguageKey();
    }

    public String toLanguageKey(String prefix, String suffix) {
        return prefix + "." + this.toLanguageKey() + "." + suffix;
    }

    private static String readGreedy(StringReader reader) {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInResourceLocation(reader.peek())) {
            reader.skip();
        }

        return reader.getString().substring(i, reader.getCursor());
    }

    public static ResourceLocation read(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        String string = readGreedy(reader);

        try {
            return parse(string);
        } catch (ResourceLocationException var4) {
            reader.setCursor(i);
            throw ERROR_INVALID.createWithContext(reader);
        }
    }

    public static ResourceLocation readNonEmpty(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        String string = readGreedy(reader);
        if (string.isEmpty()) {
            throw ERROR_INVALID.createWithContext(reader);
        } else {
            try {
                return parse(string);
            } catch (ResourceLocationException var4) {
                reader.setCursor(i);
                throw ERROR_INVALID.createWithContext(reader);
            }
        }
    }

    public static boolean isAllowedInResourceLocation(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c == '_' || c == ':' || c == '/' || c == '.' || c == '-';
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace)) {
            throw new ResourceLocationException("Non [a-z0-9_.-] character in namespace of location: " + org.apache.commons.lang3.StringUtils.normalizeSpace(namespace) + ":" + org.apache.commons.lang3.StringUtils.normalizeSpace(path)); // Paper - Sanitize ResourceLocation error logging
        } else {
            return namespace;
        }
    }

    public static boolean validPathChar(char character) {
        return character == '_'
            || character == '-'
            || character >= 'a' && character <= 'z'
            || character >= '0' && character <= '9'
            || character == '/'
            || character == '.';
    }

    private static boolean validNamespaceChar(char character) {
        return character == '_' || character == '-' || character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '.';
    }

    private static String assertValidPath(String namespace, String path) {
        if (!isValidPath(path)) {
            throw new ResourceLocationException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + org.apache.commons.lang3.StringUtils.normalizeSpace(path)); // Paper - Sanitize ResourceLocation error logging
        } else {
            return path;
        }
    }

    public static class Serializer implements JsonDeserializer<ResourceLocation>, JsonSerializer<ResourceLocation> {
        @Override
        public ResourceLocation deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            return ResourceLocation.parse(GsonHelper.convertToString(jsonElement, "location"));
        }

        @Override
        public JsonElement serialize(ResourceLocation resourceLocation, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(resourceLocation.toString());
        }
    }
}
