package net.minecraft.nbt;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

public class TagParser {
    public static final SimpleCommandExceptionType ERROR_TRAILING_DATA = new SimpleCommandExceptionType(Component.translatable("argument.nbt.trailing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_KEY = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.key"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_VALUE = new SimpleCommandExceptionType(Component.translatable("argument.nbt.expected.value"));
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_LIST = new Dynamic2CommandExceptionType(
        (receivedType, expectedType) -> Component.translatableEscape("argument.nbt.list.mixed", receivedType, expectedType)
    );
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_ARRAY = new Dynamic2CommandExceptionType(
        (receivedType, expectedType) -> Component.translatableEscape("argument.nbt.array.mixed", receivedType, expectedType)
    );
    public static final DynamicCommandExceptionType ERROR_INVALID_ARRAY = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("argument.nbt.array.invalid", type)
    );
    public static final char ELEMENT_SEPARATOR = ',';
    public static final char NAME_VALUE_SEPARATOR = ':';
    private static final char LIST_OPEN = '[';
    private static final char LIST_CLOSE = ']';
    private static final char STRUCT_CLOSE = '}';
    private static final char STRUCT_OPEN = '{';
    private static final Pattern DOUBLE_PATTERN_NOSUFFIX = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", 2);
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", 2);
    private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", 2);
    private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", 2);
    private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", 2);
    private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", 2);
    private static final Pattern INT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");
    public static final Codec<CompoundTag> AS_CODEC = Codec.STRING.comapFlatMap(nbt -> {
        try {
            return DataResult.success(new TagParser(new StringReader(nbt)).readSingleStruct(), Lifecycle.stable());
        } catch (CommandSyntaxException var2) {
            return DataResult.error(var2::getMessage);
        }
    }, CompoundTag::toString);
    public static final Codec<CompoundTag> LENIENT_CODEC = Codec.withAlternative(AS_CODEC, CompoundTag.CODEC);
    private final StringReader reader;
    private int depth; // Paper

    public static CompoundTag parseTag(String string) throws CommandSyntaxException {
        return new TagParser(new StringReader(string)).readSingleStruct();
    }

    @VisibleForTesting
    CompoundTag readSingleStruct() throws CommandSyntaxException {
        CompoundTag compoundTag = this.readStruct();
        this.reader.skipWhitespace();
        if (this.reader.canRead()) {
            throw ERROR_TRAILING_DATA.createWithContext(this.reader);
        } else {
            return compoundTag;
        }
    }

    public TagParser(StringReader reader) {
        this.reader = reader;
    }

    protected String readKey() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
        } else {
            return this.reader.readString();
        }
    }

    protected Tag readTypedValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        int i = this.reader.getCursor();
        if (StringReader.isQuotedStringStart(this.reader.peek())) {
            return StringTag.valueOf(this.reader.readQuotedString());
        } else {
            String string = this.reader.readUnquotedString();
            if (string.isEmpty()) {
                this.reader.setCursor(i);
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
            } else {
                return this.type(string);
            }
        }
    }

    public Tag type(String input) {
        try {
            if (FLOAT_PATTERN.matcher(input).matches()) {
                return FloatTag.valueOf(Float.parseFloat(input.substring(0, input.length() - 1)));
            }

            if (BYTE_PATTERN.matcher(input).matches()) {
                return ByteTag.valueOf(Byte.parseByte(input.substring(0, input.length() - 1)));
            }

            if (LONG_PATTERN.matcher(input).matches()) {
                return LongTag.valueOf(Long.parseLong(input.substring(0, input.length() - 1)));
            }

            if (SHORT_PATTERN.matcher(input).matches()) {
                return ShortTag.valueOf(Short.parseShort(input.substring(0, input.length() - 1)));
            }

            if (INT_PATTERN.matcher(input).matches()) {
                return IntTag.valueOf(Integer.parseInt(input));
            }

            if (DOUBLE_PATTERN.matcher(input).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(input.substring(0, input.length() - 1)));
            }

            if (DOUBLE_PATTERN_NOSUFFIX.matcher(input).matches()) {
                return DoubleTag.valueOf(Double.parseDouble(input));
            }

            if ("true".equalsIgnoreCase(input)) {
                return ByteTag.ONE;
            }

            if ("false".equalsIgnoreCase(input)) {
                return ByteTag.ZERO;
            }
        } catch (NumberFormatException var3) {
        }

        return StringTag.valueOf(input);
    }

    public Tag readValue() throws CommandSyntaxException {
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        } else {
            char c = this.reader.peek();
            if (c == '{') {
                return this.readStruct();
            } else {
                return c == '[' ? this.readList() : this.readTypedValue();
            }
        }
    }

    protected Tag readList() throws CommandSyntaxException {
        return this.reader.canRead(3) && !StringReader.isQuotedStringStart(this.reader.peek(1)) && this.reader.peek(2) == ';'
            ? this.readArrayTag()
            : this.readListTag();
    }

    public CompoundTag readStruct() throws CommandSyntaxException {
        this.expect('{');
        this.increaseDepth(); // Paper
        CompoundTag compoundTag = new CompoundTag();
        this.reader.skipWhitespace();

        while (this.reader.canRead() && this.reader.peek() != '}') {
            int i = this.reader.getCursor();
            String string = this.readKey();
            if (string.isEmpty()) {
                this.reader.setCursor(i);
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
            }

            this.expect(':');
            compoundTag.put(string, this.readValue());
            if (!this.hasElementSeparator()) {
                break;
            }

            if (!this.reader.canRead()) {
                throw ERROR_EXPECTED_KEY.createWithContext(this.reader);
            }
        }

        this.expect('}');
        this.depth--; // Paper
        return compoundTag;
    }

    private Tag readListTag() throws CommandSyntaxException {
        this.expect('[');
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        } else {
            this.increaseDepth(); // Paper
            ListTag listTag = new ListTag();
            TagType<?> tagType = null;

            while (this.reader.peek() != ']') {
                int i = this.reader.getCursor();
                Tag tag = this.readValue();
                TagType<?> tagType2 = tag.getType();
                if (tagType == null) {
                    tagType = tagType2;
                } else if (tagType2 != tagType) {
                    this.reader.setCursor(i);
                    throw ERROR_INSERT_MIXED_LIST.createWithContext(this.reader, tagType2.getPrettyName(), tagType.getPrettyName());
                }

                listTag.add(tag);
                if (!this.hasElementSeparator()) {
                    break;
                }

                if (!this.reader.canRead()) {
                    throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
                }
            }

            this.expect(']');
            this.depth--; // Paper
            return listTag;
        }
    }

    public Tag readArrayTag() throws CommandSyntaxException {
        this.expect('[');
        int i = this.reader.getCursor();
        char c = this.reader.read();
        this.reader.read();
        this.reader.skipWhitespace();
        if (!this.reader.canRead()) {
            throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
        } else if (c == 'B') {
            return new ByteArrayTag(this.readArray(ByteArrayTag.TYPE, ByteTag.TYPE));
        } else if (c == 'L') {
            return new LongArrayTag(this.readArray(LongArrayTag.TYPE, LongTag.TYPE));
        } else if (c == 'I') {
            return new IntArrayTag(this.readArray(IntArrayTag.TYPE, IntTag.TYPE));
        } else {
            this.reader.setCursor(i);
            throw ERROR_INVALID_ARRAY.createWithContext(this.reader, String.valueOf(c));
        }
    }

    private <T extends Number> List<T> readArray(TagType<?> arrayTypeReader, TagType<?> typeReader) throws CommandSyntaxException {
        List<T> list = Lists.newArrayList();

        while (this.reader.peek() != ']') {
            int i = this.reader.getCursor();
            Tag tag = this.readValue();
            TagType<?> tagType = tag.getType();
            if (tagType != typeReader) {
                this.reader.setCursor(i);
                throw ERROR_INSERT_MIXED_ARRAY.createWithContext(this.reader, tagType.getPrettyName(), arrayTypeReader.getPrettyName());
            }

            if (typeReader == ByteTag.TYPE) {
                list.add((T)(Byte)((NumericTag)tag).getAsByte()); // Paper - decompile fix
            } else if (typeReader == LongTag.TYPE) {
                list.add((T)(Long)((NumericTag)tag).getAsLong()); // Paper - decompile fix
            } else {
                list.add((T)(Integer)((NumericTag)tag).getAsInt()); // Paper - decompile fix
            }

            if (!this.hasElementSeparator()) {
                break;
            }

            if (!this.reader.canRead()) {
                throw ERROR_EXPECTED_VALUE.createWithContext(this.reader);
            }
        }

        this.expect(']');
        return list;
    }

    private boolean hasElementSeparator() {
        this.reader.skipWhitespace();
        if (this.reader.canRead() && this.reader.peek() == ',') {
            this.reader.skip();
            this.reader.skipWhitespace();
            return true;
        } else {
            return false;
        }
    }

    private void expect(char c) throws CommandSyntaxException {
        this.reader.skipWhitespace();
        this.reader.expect(c);
    }

    private void increaseDepth() throws CommandSyntaxException {
        this.depth++;
        if (this.depth > 512) {
            throw new io.papermc.paper.brigadier.TagParseCommandSyntaxException("NBT tag is too complex, depth > 512");
        }
    }
}
