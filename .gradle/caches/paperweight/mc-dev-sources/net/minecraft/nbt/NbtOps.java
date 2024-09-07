package net.minecraft.nbt;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.RecordBuilder.AbstractStringBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class NbtOps implements DynamicOps<Tag> {
    public static final NbtOps INSTANCE = new NbtOps();
    private static final String WRAPPER_MARKER = "";

    protected NbtOps() {
    }

    public Tag empty() {
        return EndTag.INSTANCE;
    }

    public <U> U convertTo(DynamicOps<U> dynamicOps, Tag tag) {
        return (U)(switch (tag.getId()) {
            case 0 -> (Object)dynamicOps.empty();
            case 1 -> (Object)dynamicOps.createByte(((NumericTag)tag).getAsByte());
            case 2 -> (Object)dynamicOps.createShort(((NumericTag)tag).getAsShort());
            case 3 -> (Object)dynamicOps.createInt(((NumericTag)tag).getAsInt());
            case 4 -> (Object)dynamicOps.createLong(((NumericTag)tag).getAsLong());
            case 5 -> (Object)dynamicOps.createFloat(((NumericTag)tag).getAsFloat());
            case 6 -> (Object)dynamicOps.createDouble(((NumericTag)tag).getAsDouble());
            case 7 -> (Object)dynamicOps.createByteList(ByteBuffer.wrap(((ByteArrayTag)tag).getAsByteArray()));
            case 8 -> (Object)dynamicOps.createString(tag.getAsString());
            case 9 -> (Object)this.convertList(dynamicOps, tag);
            case 10 -> (Object)this.convertMap(dynamicOps, tag);
            case 11 -> (Object)dynamicOps.createIntList(Arrays.stream(((IntArrayTag)tag).getAsIntArray()));
            case 12 -> (Object)dynamicOps.createLongList(Arrays.stream(((LongArrayTag)tag).getAsLongArray()));
            default -> throw new IllegalStateException("Unknown tag type: " + tag);
        });
    }

    public DataResult<Number> getNumberValue(Tag tag) {
        return tag instanceof NumericTag numericTag ? DataResult.success(numericTag.getAsNumber()) : DataResult.error(() -> "Not a number");
    }

    public Tag createNumeric(Number number) {
        return DoubleTag.valueOf(number.doubleValue());
    }

    public Tag createByte(byte b) {
        return ByteTag.valueOf(b);
    }

    public Tag createShort(short s) {
        return ShortTag.valueOf(s);
    }

    public Tag createInt(int i) {
        return IntTag.valueOf(i);
    }

    public Tag createLong(long l) {
        return LongTag.valueOf(l);
    }

    public Tag createFloat(float f) {
        return FloatTag.valueOf(f);
    }

    public Tag createDouble(double d) {
        return DoubleTag.valueOf(d);
    }

    public Tag createBoolean(boolean bl) {
        return ByteTag.valueOf(bl);
    }

    public DataResult<String> getStringValue(Tag tag) {
        return tag instanceof StringTag stringTag ? DataResult.success(stringTag.getAsString()) : DataResult.error(() -> "Not a string");
    }

    public Tag createString(String string) {
        return StringTag.valueOf(string);
    }

    public DataResult<Tag> mergeToList(Tag tag, Tag tag2) {
        return createCollector(tag)
            .map(merger -> DataResult.success(merger.accept(tag2).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + tag, tag));
    }

    public DataResult<Tag> mergeToList(Tag tag, List<Tag> list) {
        return createCollector(tag)
            .map(merger -> DataResult.success(merger.acceptAll(list).result()))
            .orElseGet(() -> DataResult.error(() -> "mergeToList called with not a list: " + tag, tag));
    }

    public DataResult<Tag> mergeToMap(Tag tag, Tag tag2, Tag tag3) {
        if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
        } else if (!(tag2 instanceof StringTag)) {
            return DataResult.error(() -> "key is not a string: " + tag2, tag);
        } else {
            CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
            compoundTag2.put(tag2.getAsString(), tag3);
            return DataResult.success(compoundTag2);
        }
    }

    public DataResult<Tag> mergeToMap(Tag tag, MapLike<Tag> mapLike) {
        if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
        } else {
            CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
            List<Tag> list = new ArrayList<>();
            mapLike.entries().forEach(pair -> {
                Tag tagx = pair.getFirst();
                if (!(tagx instanceof StringTag)) {
                    list.add(tagx);
                } else {
                    compoundTag2.put(tagx.getAsString(), pair.getSecond());
                }
            });
            return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
        }
    }

    public DataResult<Tag> mergeToMap(Tag tag, Map<Tag, Tag> map) {
        if (!(tag instanceof CompoundTag) && !(tag instanceof EndTag)) {
            return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
        } else {
            CompoundTag compoundTag2 = tag instanceof CompoundTag compoundTag ? compoundTag.shallowCopy() : new CompoundTag();
            List<Tag> list = new ArrayList<>();

            for (Entry<Tag, Tag> entry : map.entrySet()) {
                Tag tag2 = entry.getKey();
                if (tag2 instanceof StringTag) {
                    compoundTag2.put(tag2.getAsString(), entry.getValue());
                } else {
                    list.add(tag2);
                }
            }

            return !list.isEmpty() ? DataResult.error(() -> "some keys are not strings: " + list, compoundTag2) : DataResult.success(compoundTag2);
        }
    }

    public DataResult<Stream<Pair<Tag, Tag>>> getMapValues(Tag tag) {
        return tag instanceof CompoundTag compoundTag
            ? DataResult.success(compoundTag.entrySet().stream().map(entry -> Pair.of(this.createString(entry.getKey()), entry.getValue())))
            : DataResult.error(() -> "Not a map: " + tag);
    }

    public DataResult<Consumer<BiConsumer<Tag, Tag>>> getMapEntries(Tag tag) {
        return tag instanceof CompoundTag compoundTag ? DataResult.success(biConsumer -> {
            for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                biConsumer.accept(this.createString(entry.getKey()), entry.getValue());
            }
        }) : DataResult.error(() -> "Not a map: " + tag);
    }

    public DataResult<MapLike<Tag>> getMap(Tag tag) {
        return tag instanceof CompoundTag compoundTag ? DataResult.success(new MapLike<Tag>() {
            @Nullable
            public Tag get(Tag tag) {
                return compoundTag.get(tag.getAsString());
            }

            @Nullable
            public Tag get(String string) {
                return compoundTag.get(string);
            }

            public Stream<Pair<Tag, Tag>> entries() {
                return compoundTag.entrySet().stream().map(entry -> Pair.of(NbtOps.this.createString(entry.getKey()), entry.getValue()));
            }

            @Override
            public String toString() {
                return "MapLike[" + compoundTag + "]";
            }
        }) : DataResult.error(() -> "Not a map: " + tag);
    }

    public Tag createMap(Stream<Pair<Tag, Tag>> stream) {
        CompoundTag compoundTag = new CompoundTag();
        stream.forEach(entry -> compoundTag.put(entry.getFirst().getAsString(), entry.getSecond()));
        return compoundTag;
    }

    private static Tag tryUnwrap(CompoundTag nbt) {
        if (nbt.size() == 1) {
            Tag tag = nbt.get("");
            if (tag != null) {
                return tag;
            }
        }

        return nbt;
    }

    public DataResult<Stream<Tag>> getStream(Tag tag) {
        if (tag instanceof ListTag listTag) {
            return listTag.getElementType() == 10
                ? DataResult.success(listTag.stream().map(nbt -> tryUnwrap((CompoundTag)nbt)))
                : DataResult.success(listTag.stream());
        } else {
            return tag instanceof CollectionTag<?> collectionTag
                ? DataResult.success(collectionTag.stream().map(nbt -> nbt))
                : DataResult.error(() -> "Not a list");
        }
    }

    public DataResult<Consumer<Consumer<Tag>>> getList(Tag tag) {
        if (tag instanceof ListTag listTag) {
            return listTag.getElementType() == 10 ? DataResult.success(consumer -> {
                for (Tag tagx : listTag) {
                    consumer.accept(tryUnwrap((CompoundTag)tagx));
                }
            }) : DataResult.success(listTag::forEach);
        } else {
            return tag instanceof CollectionTag<?> collectionTag ? DataResult.success(collectionTag::forEach) : DataResult.error(() -> "Not a list: " + tag);
        }
    }

    public DataResult<ByteBuffer> getByteBuffer(Tag tag) {
        return tag instanceof ByteArrayTag byteArrayTag
            ? DataResult.success(ByteBuffer.wrap(byteArrayTag.getAsByteArray()))
            : DynamicOps.super.getByteBuffer(tag);
    }

    public Tag createByteList(ByteBuffer byteBuffer) {
        ByteBuffer byteBuffer2 = byteBuffer.duplicate().clear();
        byte[] bs = new byte[byteBuffer.capacity()];
        byteBuffer2.get(0, bs, 0, bs.length);
        return new ByteArrayTag(bs);
    }

    public DataResult<IntStream> getIntStream(Tag tag) {
        return tag instanceof IntArrayTag intArrayTag ? DataResult.success(Arrays.stream(intArrayTag.getAsIntArray())) : DynamicOps.super.getIntStream(tag);
    }

    public Tag createIntList(IntStream intStream) {
        return new IntArrayTag(intStream.toArray());
    }

    public DataResult<LongStream> getLongStream(Tag tag) {
        return tag instanceof LongArrayTag longArrayTag
            ? DataResult.success(Arrays.stream(longArrayTag.getAsLongArray()))
            : DynamicOps.super.getLongStream(tag);
    }

    public Tag createLongList(LongStream longStream) {
        return new LongArrayTag(longStream.toArray());
    }

    public Tag createList(Stream<Tag> stream) {
        return NbtOps.InitialListCollector.INSTANCE.acceptAll(stream).result();
    }

    public Tag remove(Tag tag, String string) {
        if (tag instanceof CompoundTag compoundTag) {
            CompoundTag compoundTag2 = compoundTag.shallowCopy();
            compoundTag2.remove(string);
            return compoundTag2;
        } else {
            return tag;
        }
    }

    @Override
    public String toString() {
        return "NBT";
    }

    public RecordBuilder<Tag> mapBuilder() {
        return new NbtOps.NbtRecordBuilder();
    }

    private static Optional<NbtOps.ListCollector> createCollector(Tag nbt) {
        if (nbt instanceof EndTag) {
            return Optional.of(NbtOps.InitialListCollector.INSTANCE);
        } else {
            if (nbt instanceof CollectionTag<?> collectionTag) {
                if (collectionTag.isEmpty()) {
                    return Optional.of(NbtOps.InitialListCollector.INSTANCE);
                }

                if (collectionTag instanceof ListTag listTag) {
                    return switch (listTag.getElementType()) {
                        case 0 -> Optional.of(NbtOps.InitialListCollector.INSTANCE);
                        case 10 -> Optional.of(new NbtOps.HeterogenousListCollector(listTag));
                        default -> Optional.of(new NbtOps.HomogenousListCollector(listTag));
                    };
                }

                if (collectionTag instanceof ByteArrayTag byteArrayTag) {
                    return Optional.of(new NbtOps.ByteListCollector(byteArrayTag.getAsByteArray()));
                }

                if (collectionTag instanceof IntArrayTag intArrayTag) {
                    return Optional.of(new NbtOps.IntListCollector(intArrayTag.getAsIntArray()));
                }

                if (collectionTag instanceof LongArrayTag longArrayTag) {
                    return Optional.of(new NbtOps.LongListCollector(longArrayTag.getAsLongArray()));
                }
            }

            return Optional.empty();
        }
    }

    static class ByteListCollector implements NbtOps.ListCollector {
        private final ByteArrayList values = new ByteArrayList();

        public ByteListCollector(byte value) {
            this.values.add(value);
        }

        public ByteListCollector(byte[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            if (nbt instanceof ByteTag byteTag) {
                this.values.add(byteTag.getAsByte());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(nbt);
            }
        }

        @Override
        public Tag result() {
            return new ByteArrayTag(this.values.toByteArray());
        }
    }

    static class HeterogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        public HeterogenousListCollector() {
        }

        public HeterogenousListCollector(Collection<Tag> nbts) {
            this.result.addAll(nbts);
        }

        public HeterogenousListCollector(IntArrayList list) {
            list.forEach(value -> this.result.add(wrapElement(IntTag.valueOf(value))));
        }

        public HeterogenousListCollector(ByteArrayList list) {
            list.forEach(value -> this.result.add(wrapElement(ByteTag.valueOf(value))));
        }

        public HeterogenousListCollector(LongArrayList list) {
            list.forEach(value -> this.result.add(wrapElement(LongTag.valueOf(value))));
        }

        private static boolean isWrapper(CompoundTag nbt) {
            return nbt.size() == 1 && nbt.contains("");
        }

        private static Tag wrapIfNeeded(Tag value) {
            if (value instanceof CompoundTag compoundTag && !isWrapper(compoundTag)) {
                return compoundTag;
            }

            return wrapElement(value);
        }

        private static CompoundTag wrapElement(Tag value) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("", value);
            return compoundTag;
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            this.result.add(wrapIfNeeded(nbt));
            return this;
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class HomogenousListCollector implements NbtOps.ListCollector {
        private final ListTag result = new ListTag();

        HomogenousListCollector(Tag nbt) {
            this.result.add(nbt);
        }

        HomogenousListCollector(ListTag nbt) {
            this.result.addAll(nbt);
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            if (nbt.getId() != this.result.getElementType()) {
                return new NbtOps.HeterogenousListCollector().acceptAll(this.result).accept(nbt);
            } else {
                this.result.add(nbt);
                return this;
            }
        }

        @Override
        public Tag result() {
            return this.result;
        }
    }

    static class InitialListCollector implements NbtOps.ListCollector {
        public static final NbtOps.InitialListCollector INSTANCE = new NbtOps.InitialListCollector();

        private InitialListCollector() {
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            if (nbt instanceof CompoundTag compoundTag) {
                return new NbtOps.HeterogenousListCollector().accept(compoundTag);
            } else if (nbt instanceof ByteTag byteTag) {
                return new NbtOps.ByteListCollector(byteTag.getAsByte());
            } else if (nbt instanceof IntTag intTag) {
                return new NbtOps.IntListCollector(intTag.getAsInt());
            } else {
                return (NbtOps.ListCollector)(nbt instanceof LongTag longTag
                    ? new NbtOps.LongListCollector(longTag.getAsLong())
                    : new NbtOps.HomogenousListCollector(nbt));
            }
        }

        @Override
        public Tag result() {
            return new ListTag();
        }
    }

    static class IntListCollector implements NbtOps.ListCollector {
        private final IntArrayList values = new IntArrayList();

        public IntListCollector(int value) {
            this.values.add(value);
        }

        public IntListCollector(int[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            if (nbt instanceof IntTag intTag) {
                this.values.add(intTag.getAsInt());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(nbt);
            }
        }

        @Override
        public Tag result() {
            return new IntArrayTag(this.values.toIntArray());
        }
    }

    interface ListCollector {
        NbtOps.ListCollector accept(Tag nbt);

        default NbtOps.ListCollector acceptAll(Iterable<Tag> nbts) {
            NbtOps.ListCollector listCollector = this;

            for (Tag tag : nbts) {
                listCollector = listCollector.accept(tag);
            }

            return listCollector;
        }

        default NbtOps.ListCollector acceptAll(Stream<Tag> nbts) {
            return this.acceptAll(nbts::iterator);
        }

        Tag result();
    }

    static class LongListCollector implements NbtOps.ListCollector {
        private final LongArrayList values = new LongArrayList();

        public LongListCollector(long value) {
            this.values.add(value);
        }

        public LongListCollector(long[] values) {
            this.values.addElements(0, values);
        }

        @Override
        public NbtOps.ListCollector accept(Tag nbt) {
            if (nbt instanceof LongTag longTag) {
                this.values.add(longTag.getAsLong());
                return this;
            } else {
                return new NbtOps.HeterogenousListCollector(this.values).accept(nbt);
            }
        }

        @Override
        public Tag result() {
            return new LongArrayTag(this.values.toLongArray());
        }
    }

    class NbtRecordBuilder extends AbstractStringBuilder<Tag, CompoundTag> {
        protected NbtRecordBuilder() {
            super(NbtOps.this);
        }

        protected CompoundTag initBuilder() {
            return new CompoundTag();
        }

        protected CompoundTag append(String string, Tag tag, CompoundTag compoundTag) {
            compoundTag.put(string, tag);
            return compoundTag;
        }

        protected DataResult<Tag> build(CompoundTag compoundTag, Tag tag) {
            if (tag == null || tag == EndTag.INSTANCE) {
                return DataResult.success(compoundTag);
            } else if (!(tag instanceof CompoundTag compoundTag2)) {
                return DataResult.error(() -> "mergeToMap called with not a map: " + tag, tag);
            } else {
                CompoundTag compoundTag3 = compoundTag2.shallowCopy();

                for (Entry<String, Tag> entry : compoundTag.entrySet()) {
                    compoundTag3.put(entry.getKey(), entry.getValue());
                }

                return DataResult.success(compoundTag3);
            }
        }
    }
}
