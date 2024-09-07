package net.minecraft.nbt;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.bytes.ByteOpenHashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

public class TextComponentTagVisitor implements TagVisitor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INLINE_LIST_THRESHOLD = 8;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_LENGTH = 128;
    private static final ByteCollection INLINE_ELEMENT_TYPES = new ByteOpenHashSet(Arrays.asList((byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6));
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_KEY = ChatFormatting.AQUA;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_STRING = ChatFormatting.GREEN;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER = ChatFormatting.GOLD;
    private static final ChatFormatting SYNTAX_HIGHLIGHTING_NUMBER_TYPE = ChatFormatting.RED;
    private static final Pattern SIMPLE_VALUE = Pattern.compile("[A-Za-z0-9._+-]+");
    private static final String LIST_OPEN = "[";
    private static final String LIST_CLOSE = "]";
    private static final String LIST_TYPE_SEPARATOR = ";";
    private static final String ELEMENT_SPACING = " ";
    private static final String STRUCT_OPEN = "{";
    private static final String STRUCT_CLOSE = "}";
    private static final String NEWLINE = "\n";
    private static final String NAME_VALUE_SEPARATOR = ": ";
    private static final String ELEMENT_SEPARATOR = String.valueOf(',');
    private static final String WRAPPED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + "\n";
    private static final String SPACED_ELEMENT_SEPARATOR = ELEMENT_SEPARATOR + " ";
    private static final Component FOLDED = Component.literal("<...>").withStyle(ChatFormatting.GRAY);
    private static final Component BYTE_TYPE = Component.literal("b").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component SHORT_TYPE = Component.literal("s").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component INT_TYPE = Component.literal("I").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component LONG_TYPE = Component.literal("L").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component FLOAT_TYPE = Component.literal("f").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component DOUBLE_TYPE = Component.literal("d").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private static final Component BYTE_ARRAY_TYPE = Component.literal("B").withStyle(SYNTAX_HIGHLIGHTING_NUMBER_TYPE);
    private final String indentation;
    private int indentDepth;
    private int depth;
    private final MutableComponent result = Component.empty();

    public TextComponentTagVisitor(String prefix) {
        this.indentation = prefix;
    }

    public Component visit(Tag element) {
        element.accept(this);
        return this.result;
    }

    @Override
    public void visitString(StringTag element) {
        String string = StringTag.quoteAndEscape(element.getAsString());
        String string2 = string.substring(0, 1);
        Component component = Component.literal(string.substring(1, string.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_STRING);
        this.result.append(string2).append(component).append(string2);
    }

    @Override
    public void visitByte(ByteTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(BYTE_TYPE);
    }

    @Override
    public void visitShort(ShortTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(SHORT_TYPE);
    }

    @Override
    public void visitInt(IntTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
    }

    @Override
    public void visitLong(LongTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsNumber())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(LONG_TYPE);
    }

    @Override
    public void visitFloat(FloatTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsFloat())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(FLOAT_TYPE);
    }

    @Override
    public void visitDouble(DoubleTag element) {
        this.result.append(Component.literal(String.valueOf(element.getAsDouble())).withStyle(SYNTAX_HIGHLIGHTING_NUMBER)).append(DOUBLE_TYPE);
    }

    @Override
    public void visitByteArray(ByteArrayTag element) {
        this.result.append("[").append(BYTE_ARRAY_TYPE).append(";");
        byte[] bs = element.getAsByteArray();

        for (int i = 0; i < bs.length && i < 128; i++) {
            MutableComponent mutableComponent = Component.literal(String.valueOf(bs[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(mutableComponent).append(BYTE_ARRAY_TYPE);
            if (i != bs.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (bs.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitIntArray(IntArrayTag element) {
        this.result.append("[").append(INT_TYPE).append(";");
        int[] is = element.getAsIntArray();

        for (int i = 0; i < is.length && i < 128; i++) {
            this.result.append(" ").append(Component.literal(String.valueOf(is[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER));
            if (i != is.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (is.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitLongArray(LongArrayTag element) {
        this.result.append("[").append(LONG_TYPE).append(";");
        long[] ls = element.getAsLongArray();

        for (int i = 0; i < ls.length && i < 128; i++) {
            Component component = Component.literal(String.valueOf(ls[i])).withStyle(SYNTAX_HIGHLIGHTING_NUMBER);
            this.result.append(" ").append(component).append(LONG_TYPE);
            if (i != ls.length - 1) {
                this.result.append(ELEMENT_SEPARATOR);
            }
        }

        if (ls.length > 128) {
            this.result.append(FOLDED);
        }

        this.result.append("]");
    }

    @Override
    public void visitList(ListTag element) {
        if (element.isEmpty()) {
            this.result.append("[]");
        } else if (this.depth >= 64) {
            this.result.append("[").append(FOLDED).append("]");
        } else if (INLINE_ELEMENT_TYPES.contains(element.getElementType()) && element.size() <= 8) {
            this.result.append("[");

            for (int i = 0; i < element.size(); i++) {
                if (i != 0) {
                    this.result.append(SPACED_ELEMENT_SEPARATOR);
                }

                this.appendSubTag(element.get(i), false);
            }

            this.result.append("]");
        } else {
            this.result.append("[");
            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String string = Strings.repeat(this.indentation, this.indentDepth + 1);

            for (int j = 0; j < element.size() && j < 128; j++) {
                this.result.append(string);
                this.appendSubTag(element.get(j), true);
                if (j != element.size() - 1) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (element.size() > 128) {
                this.result.append(string).append(FOLDED);
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("]");
        }
    }

    @Override
    public void visitCompound(CompoundTag compound) {
        if (compound.isEmpty()) {
            this.result.append("{}");
        } else if (this.depth >= 64) {
            this.result.append("{").append(FOLDED).append("}");
        } else {
            this.result.append("{");
            Collection<String> collection = compound.getAllKeys();
            if (LOGGER.isDebugEnabled()) {
                List<String> list = Lists.newArrayList(compound.getAllKeys());
                Collections.sort(list);
                collection = list;
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n");
            }

            String string = Strings.repeat(this.indentation, this.indentDepth + 1);
            Iterator<String> iterator = collection.iterator();

            while (iterator.hasNext()) {
                String string2 = iterator.next();
                this.result.append(string).append(handleEscapePretty(string2)).append(": ");
                this.appendSubTag(compound.get(string2), true);
                if (iterator.hasNext()) {
                    this.result.append(this.indentation.isEmpty() ? SPACED_ELEMENT_SEPARATOR : WRAPPED_ELEMENT_SEPARATOR);
                }
            }

            if (!this.indentation.isEmpty()) {
                this.result.append("\n" + Strings.repeat(this.indentation, this.indentDepth));
            }

            this.result.append("}");
        }
    }

    private void appendSubTag(Tag element, boolean indent) {
        if (indent) {
            this.indentDepth++;
        }

        this.depth++;

        try {
            element.accept(this);
        } finally {
            if (indent) {
                this.indentDepth--;
            }

            this.depth--;
        }
    }

    protected static Component handleEscapePretty(String name) {
        if (SIMPLE_VALUE.matcher(name).matches()) {
            return Component.literal(name).withStyle(SYNTAX_HIGHLIGHTING_KEY);
        } else {
            String string = StringTag.quoteAndEscape(name);
            String string2 = string.substring(0, 1);
            Component component = Component.literal(string.substring(1, string.length() - 1)).withStyle(SYNTAX_HIGHLIGHTING_KEY);
            return Component.literal(string2).append(component).append(string2);
        }
    }

    @Override
    public void visitEnd(EndTag element) {
    }
}
