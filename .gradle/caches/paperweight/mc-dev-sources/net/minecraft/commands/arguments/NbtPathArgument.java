package net.minecraft.commands.arguments;

import com.google.common.collect.Lists;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;

public class NbtPathArgument implements ArgumentType<NbtPathArgument.NbtPath> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo.bar", "foo[0]", "[0]", "[]", "{foo=bar}");
    public static final SimpleCommandExceptionType ERROR_INVALID_NODE = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.node.invalid"));
    public static final SimpleCommandExceptionType ERROR_DATA_TOO_DEEP = new SimpleCommandExceptionType(Component.translatable("arguments.nbtpath.too_deep"));
    public static final DynamicCommandExceptionType ERROR_NOTHING_FOUND = new DynamicCommandExceptionType(
        path -> Component.translatableEscape("arguments.nbtpath.nothing_found", path)
    );
    static final DynamicCommandExceptionType ERROR_EXPECTED_LIST = new DynamicCommandExceptionType(
        nbt -> Component.translatableEscape("commands.data.modify.expected_list", nbt)
    );
    static final DynamicCommandExceptionType ERROR_INVALID_INDEX = new DynamicCommandExceptionType(
        index -> Component.translatableEscape("commands.data.modify.invalid_index", index)
    );
    private static final char INDEX_MATCH_START = '[';
    private static final char INDEX_MATCH_END = ']';
    private static final char KEY_MATCH_START = '{';
    private static final char KEY_MATCH_END = '}';
    private static final char QUOTED_KEY_START = '"';
    private static final char SINGLE_QUOTED_KEY_START = '\'';

    public static NbtPathArgument nbtPath() {
        return new NbtPathArgument();
    }

    public static NbtPathArgument.NbtPath getPath(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, NbtPathArgument.NbtPath.class);
    }

    public NbtPathArgument.NbtPath parse(StringReader stringReader) throws CommandSyntaxException {
        List<NbtPathArgument.Node> list = Lists.newArrayList();
        int i = stringReader.getCursor();
        Object2IntMap<NbtPathArgument.Node> object2IntMap = new Object2IntOpenHashMap<>();
        boolean bl = true;

        while (stringReader.canRead() && stringReader.peek() != ' ') {
            NbtPathArgument.Node node = parseNode(stringReader, bl);
            list.add(node);
            object2IntMap.put(node, stringReader.getCursor() - i);
            bl = false;
            if (stringReader.canRead()) {
                char c = stringReader.peek();
                if (c != ' ' && c != '[' && c != '{') {
                    stringReader.expect('.');
                }
            }
        }

        return new NbtPathArgument.NbtPath(
            stringReader.getString().substring(i, stringReader.getCursor()), list.toArray(new NbtPathArgument.Node[0]), object2IntMap
        );
    }

    private static NbtPathArgument.Node parseNode(StringReader reader, boolean root) throws CommandSyntaxException {
        return (NbtPathArgument.Node)(switch (reader.peek()) {
            case '"', '\'' -> readObjectNode(reader, reader.readString());
            case '[' -> {
                reader.skip();
                int i = reader.peek();
                if (i == 123) {
                    CompoundTag compoundTag2 = new TagParser(reader).readStruct();
                    reader.expect(']');
                    yield new NbtPathArgument.MatchElementNode(compoundTag2);
                } else if (i == 93) {
                    reader.skip();
                    yield NbtPathArgument.AllElementsNode.INSTANCE;
                } else {
                    int j = reader.readInt();
                    reader.expect(']');
                    yield new NbtPathArgument.IndexedElementNode(j);
                }
            }
            case '{' -> {
                if (!root) {
                    throw ERROR_INVALID_NODE.createWithContext(reader);
                }

                CompoundTag compoundTag = new TagParser(reader).readStruct();
                yield new NbtPathArgument.MatchRootObjectNode(compoundTag);
            }
            default -> readObjectNode(reader, readUnquotedName(reader));
        });
    }

    private static NbtPathArgument.Node readObjectNode(StringReader reader, String name) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '{') {
            CompoundTag compoundTag = new TagParser(reader).readStruct();
            return new NbtPathArgument.MatchObjectNode(name, compoundTag);
        } else {
            return new NbtPathArgument.CompoundChildNode(name);
        }
    }

    private static String readUnquotedName(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();

        while (reader.canRead() && isAllowedInUnquotedName(reader.peek())) {
            reader.skip();
        }

        if (reader.getCursor() == i) {
            throw ERROR_INVALID_NODE.createWithContext(reader);
        } else {
            return reader.getString().substring(i, reader.getCursor());
        }
    }

    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    private static boolean isAllowedInUnquotedName(char c) {
        return c != ' ' && c != '"' && c != '\'' && c != '[' && c != ']' && c != '.' && c != '{' && c != '}';
    }

    static Predicate<Tag> createTagPredicate(CompoundTag filter) {
        return nbt -> NbtUtils.compareNbt(filter, nbt, true);
    }

    static class AllElementsNode implements NbtPathArgument.Node {
        public static final NbtPathArgument.AllElementsNode INSTANCE = new NbtPathArgument.AllElementsNode();

        private AllElementsNode() {
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof CollectionTag) {
                results.addAll((CollectionTag)current);
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            if (current instanceof CollectionTag<?> collectionTag) {
                if (collectionTag.isEmpty()) {
                    Tag tag = source.get();
                    if (collectionTag.addTag(0, tag)) {
                        results.add(tag);
                    }
                } else {
                    results.addAll((Collection<? extends Tag>)collectionTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            if (!(current instanceof CollectionTag<?> collectionTag)) {
                return 0;
            } else {
                int i = collectionTag.size();
                if (i == 0) {
                    collectionTag.addTag(0, source.get());
                    return 1;
                } else {
                    Tag tag = source.get();
                    int j = i - (int)collectionTag.stream().filter(tag::equals).count();
                    if (j == 0) {
                        return 0;
                    } else {
                        collectionTag.clear();
                        if (!collectionTag.addTag(0, tag)) {
                            return 0;
                        } else {
                            for (int k = 1; k < i; k++) {
                                collectionTag.addTag(k, source.get());
                            }

                            return j;
                        }
                    }
                }
            }
        }

        @Override
        public int removeTag(Tag current) {
            if (current instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                if (i > 0) {
                    collectionTag.clear();
                    return i;
                }
            }

            return 0;
        }
    }

    static class CompoundChildNode implements NbtPathArgument.Node {
        private final String name;

        public CompoundChildNode(String name) {
            this.name = name;
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof CompoundTag) {
                Tag tag = ((CompoundTag)current).get(this.name);
                if (tag != null) {
                    results.add(tag);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            if (current instanceof CompoundTag compoundTag) {
                Tag tag;
                if (compoundTag.contains(this.name)) {
                    tag = compoundTag.get(this.name);
                } else {
                    tag = source.get();
                    compoundTag.put(this.name, tag);
                }

                results.add(tag);
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            if (current instanceof CompoundTag compoundTag) {
                Tag tag = source.get();
                Tag tag2 = compoundTag.put(this.name, tag);
                if (!tag.equals(tag2)) {
                    return 1;
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag current) {
            if (current instanceof CompoundTag compoundTag && compoundTag.contains(this.name)) {
                compoundTag.remove(this.name);
                return 1;
            }

            return 0;
        }
    }

    static class IndexedElementNode implements NbtPathArgument.Node {
        private final int index;

        public IndexedElementNode(int index) {
            this.index = index;
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    results.add(collectionTag.get(j));
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            this.getTag(current, results);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            if (current instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    Tag tag = collectionTag.get(j);
                    Tag tag2 = source.get();
                    if (!tag2.equals(tag) && collectionTag.setTag(j, tag2)) {
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag current) {
            if (current instanceof CollectionTag<?> collectionTag) {
                int i = collectionTag.size();
                int j = this.index < 0 ? i + this.index : this.index;
                if (0 <= j && j < i) {
                    collectionTag.remove(j);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchElementNode implements NbtPathArgument.Node {
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchElementNode(CompoundTag filter) {
            this.pattern = filter;
            this.predicate = NbtPathArgument.createTagPredicate(filter);
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(results::add);
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            MutableBoolean mutableBoolean = new MutableBoolean();
            if (current instanceof ListTag listTag) {
                listTag.stream().filter(this.predicate).forEach(nbt -> {
                    results.add(nbt);
                    mutableBoolean.setTrue();
                });
                if (mutableBoolean.isFalse()) {
                    CompoundTag compoundTag = this.pattern.copy();
                    listTag.add(compoundTag);
                    results.add(compoundTag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new ListTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            int i = 0;
            if (current instanceof ListTag listTag) {
                int j = listTag.size();
                if (j == 0) {
                    listTag.add(source.get());
                    i++;
                } else {
                    for (int k = 0; k < j; k++) {
                        Tag tag = listTag.get(k);
                        if (this.predicate.test(tag)) {
                            Tag tag2 = source.get();
                            if (!tag2.equals(tag) && listTag.setTag(k, tag2)) {
                                i++;
                            }
                        }
                    }
                }
            }

            return i;
        }

        @Override
        public int removeTag(Tag current) {
            int i = 0;
            if (current instanceof ListTag listTag) {
                for (int j = listTag.size() - 1; j >= 0; j--) {
                    if (this.predicate.test(listTag.get(j))) {
                        listTag.remove(j);
                        i++;
                    }
                }
            }

            return i;
        }
    }

    static class MatchObjectNode implements NbtPathArgument.Node {
        private final String name;
        private final CompoundTag pattern;
        private final Predicate<Tag> predicate;

        public MatchObjectNode(String name, CompoundTag filter) {
            this.name = name;
            this.pattern = filter;
            this.predicate = NbtPathArgument.createTagPredicate(filter);
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof CompoundTag) {
                Tag tag = ((CompoundTag)current).get(this.name);
                if (this.predicate.test(tag)) {
                    results.add(tag);
                }
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            if (current instanceof CompoundTag compoundTag) {
                Tag tag = compoundTag.get(this.name);
                if (tag == null) {
                    Tag var6 = this.pattern.copy();
                    compoundTag.put(this.name, var6);
                    results.add(var6);
                } else if (this.predicate.test(tag)) {
                    results.add(tag);
                }
            }
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            if (current instanceof CompoundTag compoundTag) {
                Tag tag = compoundTag.get(this.name);
                if (this.predicate.test(tag)) {
                    Tag tag2 = source.get();
                    if (!tag2.equals(tag)) {
                        compoundTag.put(this.name, tag2);
                        return 1;
                    }
                }
            }

            return 0;
        }

        @Override
        public int removeTag(Tag current) {
            if (current instanceof CompoundTag compoundTag) {
                Tag tag = compoundTag.get(this.name);
                if (this.predicate.test(tag)) {
                    compoundTag.remove(this.name);
                    return 1;
                }
            }

            return 0;
        }
    }

    static class MatchRootObjectNode implements NbtPathArgument.Node {
        private final Predicate<Tag> predicate;

        public MatchRootObjectNode(CompoundTag filter) {
            this.predicate = NbtPathArgument.createTagPredicate(filter);
        }

        @Override
        public void getTag(Tag current, List<Tag> results) {
            if (current instanceof CompoundTag && this.predicate.test(current)) {
                results.add(current);
            }
        }

        @Override
        public void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results) {
            this.getTag(current, results);
        }

        @Override
        public Tag createPreferredParentTag() {
            return new CompoundTag();
        }

        @Override
        public int setTag(Tag current, Supplier<Tag> source) {
            return 0;
        }

        @Override
        public int removeTag(Tag current) {
            return 0;
        }
    }

    public static class NbtPath {
        private final String original;
        private final Object2IntMap<NbtPathArgument.Node> nodeToOriginalPosition;
        private final NbtPathArgument.Node[] nodes;
        public static final Codec<NbtPathArgument.NbtPath> CODEC = Codec.STRING.comapFlatMap(path -> {
            try {
                NbtPathArgument.NbtPath nbtPath = new NbtPathArgument().parse(new StringReader(path));
                return DataResult.success(nbtPath);
            } catch (CommandSyntaxException var2) {
                return DataResult.error(() -> "Failed to parse path " + path + ": " + var2.getMessage());
            }
        }, NbtPathArgument.NbtPath::asString);

        public static NbtPathArgument.NbtPath of(String path) throws CommandSyntaxException {
            return new NbtPathArgument().parse(new StringReader(path));
        }

        public NbtPath(String string, NbtPathArgument.Node[] nodes, Object2IntMap<NbtPathArgument.Node> nodeEndIndices) {
            this.original = string;
            this.nodes = nodes;
            this.nodeToOriginalPosition = nodeEndIndices;
        }

        public List<Tag> get(Tag element) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(element);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public int countMatching(Tag element) {
            List<Tag> list = Collections.singletonList(element);

            for (NbtPathArgument.Node node : this.nodes) {
                list = node.get(list);
                if (list.isEmpty()) {
                    return 0;
                }
            }

            return list.size();
        }

        private List<Tag> getOrCreateParents(Tag start) throws CommandSyntaxException {
            List<Tag> list = Collections.singletonList(start);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                NbtPathArgument.Node node = this.nodes[i];
                int j = i + 1;
                list = node.getOrCreate(list, this.nodes[j]::createPreferredParentTag);
                if (list.isEmpty()) {
                    throw this.createNotFoundException(node);
                }
            }

            return list;
        }

        public List<Tag> getOrCreate(Tag element, Supplier<Tag> source) throws CommandSyntaxException {
            List<Tag> list = this.getOrCreateParents(element);
            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return node.getOrCreate(list, source);
        }

        private static int apply(List<Tag> elements, Function<Tag, Integer> operation) {
            return elements.stream().map(operation).reduce(0, (a, b) -> a + b);
        }

        public static boolean isTooDeep(Tag element, int depth) {
            if (depth >= 512) {
                return true;
            } else {
                if (element instanceof CompoundTag compoundTag) {
                    for (String string : compoundTag.getAllKeys()) {
                        Tag tag = compoundTag.get(string);
                        if (tag != null && isTooDeep(tag, depth + 1)) {
                            return true;
                        }
                    }
                } else if (element instanceof ListTag) {
                    for (Tag tag2 : (ListTag)element) {
                        if (isTooDeep(tag2, depth + 1)) {
                            return true;
                        }
                    }
                }

                return false;
            }
        }

        public int set(Tag element, Tag source) throws CommandSyntaxException {
            if (isTooDeep(source, this.estimatePathDepth())) {
                throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
            } else {
                Tag tag = source.copy();
                List<Tag> list = this.getOrCreateParents(element);
                if (list.isEmpty()) {
                    return 0;
                } else {
                    NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
                    MutableBoolean mutableBoolean = new MutableBoolean(false);
                    return apply(list, nbt -> node.setTag(nbt, () -> {
                            if (mutableBoolean.isFalse()) {
                                mutableBoolean.setTrue();
                                return tag;
                            } else {
                                return tag.copy();
                            }
                        }));
                }
            }
        }

        private int estimatePathDepth() {
            return this.nodes.length;
        }

        public int insert(int index, CompoundTag compound, List<Tag> elements) throws CommandSyntaxException {
            List<Tag> list = new ArrayList<>(elements.size());

            for (Tag tag : elements) {
                Tag tag2 = tag.copy();
                list.add(tag2);
                if (isTooDeep(tag2, this.estimatePathDepth())) {
                    throw NbtPathArgument.ERROR_DATA_TOO_DEEP.create();
                }
            }

            Collection<Tag> collection = this.getOrCreate(compound, ListTag::new);
            int i = 0;
            boolean bl = false;

            for (Tag tag3 : collection) {
                if (!(tag3 instanceof CollectionTag<?> collectionTag)) {
                    throw NbtPathArgument.ERROR_EXPECTED_LIST.create(tag3);
                }

                boolean bl2 = false;
                int j = index < 0 ? collectionTag.size() + index + 1 : index;

                for (Tag tag4 : list) {
                    try {
                        if (collectionTag.addTag(j, bl ? tag4.copy() : tag4)) {
                            j++;
                            bl2 = true;
                        }
                    } catch (IndexOutOfBoundsException var16) {
                        throw NbtPathArgument.ERROR_INVALID_INDEX.create(j);
                    }
                }

                bl = true;
                i += bl2 ? 1 : 0;
            }

            return i;
        }

        public int remove(Tag element) {
            List<Tag> list = Collections.singletonList(element);

            for (int i = 0; i < this.nodes.length - 1; i++) {
                list = this.nodes[i].get(list);
            }

            NbtPathArgument.Node node = this.nodes[this.nodes.length - 1];
            return apply(list, node::removeTag);
        }

        private CommandSyntaxException createNotFoundException(NbtPathArgument.Node node) {
            int i = this.nodeToOriginalPosition.getInt(node);
            return NbtPathArgument.ERROR_NOTHING_FOUND.create(this.original.substring(0, i));
        }

        @Override
        public String toString() {
            return this.original;
        }

        public String asString() {
            return this.original;
        }
    }

    interface Node {
        void getTag(Tag current, List<Tag> results);

        void getOrCreateTag(Tag current, Supplier<Tag> source, List<Tag> results);

        Tag createPreferredParentTag();

        int setTag(Tag current, Supplier<Tag> source);

        int removeTag(Tag current);

        default List<Tag> get(List<Tag> elements) {
            return this.collect(elements, this::getTag);
        }

        default List<Tag> getOrCreate(List<Tag> elements, Supplier<Tag> supplier) {
            return this.collect(elements, (current, results) -> this.getOrCreateTag(current, supplier, results));
        }

        default List<Tag> collect(List<Tag> elements, BiConsumer<Tag, List<Tag>> action) {
            List<Tag> list = Lists.newArrayList();

            for (Tag tag : elements) {
                action.accept(tag, list);
            }

            return list;
        }
    }
}
