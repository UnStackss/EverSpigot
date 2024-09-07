package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public class DoubleTag extends NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 16;
    public static final DoubleTag ZERO = new DoubleTag(0.0);
    public static final TagType<DoubleTag> TYPE = new TagType.StaticSize<DoubleTag>() {
        @Override
        public DoubleTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
            return DoubleTag.valueOf(readAccounted(dataInput, nbtAccounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            return visitor.visit(readAccounted(input, tracker));
        }

        private static double readAccounted(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(16L);
            return input.readDouble();
        }

        @Override
        public int size() {
            return 8;
        }

        @Override
        public String getName() {
            return "DOUBLE";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Double";
        }

        @Override
        public boolean isValue() {
            return true;
        }
    };
    private final double data;

    private DoubleTag(double value) {
        this.data = value;
    }

    public static DoubleTag valueOf(double value) {
        return value == 0.0 ? ZERO : new DoubleTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeDouble(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 16;
    }

    @Override
    public byte getId() {
        return 6;
    }

    @Override
    public TagType<DoubleTag> getType() {
        return TYPE;
    }

    @Override
    public DoubleTag copy() {
        return this;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof DoubleTag && this.data == ((DoubleTag)object).data;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.data);
        return (int)(l ^ l >>> 32);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitDouble(this);
    }

    @Override
    public long getAsLong() {
        return (long)Math.floor(this.data);
    }

    @Override
    public int getAsInt() {
        return Mth.floor(this.data);
    }

    @Override
    public short getAsShort() {
        return (short)(Mth.floor(this.data) & 65535);
    }

    @Override
    public byte getAsByte() {
        return (byte)(Mth.floor(this.data) & 0xFF);
    }

    @Override
    public double getAsDouble() {
        return this.data;
    }

    @Override
    public float getAsFloat() {
        return (float)this.data;
    }

    @Override
    public Number getAsNumber() {
        return this.data;
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
