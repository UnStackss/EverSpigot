package net.minecraft.nbt;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;

public class LongArrayTag extends CollectionTag<LongTag> {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<LongArrayTag> TYPE = new TagType.VariableSize<LongArrayTag>() {
        @Override
        public LongArrayTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
            return new LongArrayTag(readAccounted(dataInput, nbtAccounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            return visitor.visit(readAccounted(input, tracker));
        }

        private static long[] readAccounted(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(24L);
            int i = input.readInt();
            tracker.accountBytes(8L, (long)i);
            long[] ls = new long[i];

            for (int j = 0; j < i; j++) {
                ls[j] = input.readLong();
            }

            return ls;
        }

        @Override
        public void skip(DataInput input, NbtAccounter tracker) throws IOException {
            input.skipBytes(input.readInt() * 8);
        }

        @Override
        public String getName() {
            return "LONG[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Long_Array";
        }
    };
    private long[] data;

    public LongArrayTag(long[] value) {
        this.data = value;
    }

    public LongArrayTag(LongSet value) {
        this.data = value.toLongArray();
    }

    public LongArrayTag(List<Long> value) {
        this(toArray(value));
    }

    private static long[] toArray(List<Long> list) {
        long[] ls = new long[list.size()];

        for (int i = 0; i < list.size(); i++) {
            Long long_ = list.get(i);
            ls[i] = long_ == null ? 0L : long_;
        }

        return ls;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);

        for (long l : this.data) {
            output.writeLong(l);
        }
    }

    @Override
    public int sizeInBytes() {
        return 24 + 8 * this.data.length;
    }

    @Override
    public byte getId() {
        return 12;
    }

    @Override
    public TagType<LongArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        return this.getAsString();
    }

    @Override
    public LongArrayTag copy() {
        long[] ls = new long[this.data.length];
        System.arraycopy(this.data, 0, ls, 0, this.data.length);
        return new LongArrayTag(ls);
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof LongArrayTag && Arrays.equals(this.data, ((LongArrayTag)object).data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitLongArray(this);
    }

    public long[] getAsLongArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    @Override
    public LongTag get(int i) {
        return LongTag.valueOf(this.data[i]);
    }

    @Override
    public LongTag set(int i, LongTag longTag) {
        long l = this.data[i];
        this.data[i] = longTag.getAsLong();
        return LongTag.valueOf(l);
    }

    @Override
    public void add(int i, LongTag longTag) {
        this.data = ArrayUtils.add(this.data, i, longTag.getAsLong());
    }

    @Override
    public boolean setTag(int index, Tag element) {
        if (element instanceof NumericTag) {
            this.data[index] = ((NumericTag)element).getAsLong();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag element) {
        if (element instanceof NumericTag) {
            this.data = ArrayUtils.add(this.data, index, ((NumericTag)element).getAsLong());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public LongTag remove(int i) {
        long l = this.data[i];
        this.data = ArrayUtils.remove(this.data, i);
        return LongTag.valueOf(l);
    }

    @Override
    public byte getElementType() {
        return 4;
    }

    @Override
    public void clear() {
        this.data = new long[0];
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
