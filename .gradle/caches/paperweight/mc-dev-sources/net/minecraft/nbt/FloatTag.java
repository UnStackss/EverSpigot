package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.minecraft.util.Mth;

public class FloatTag extends NumericTag {
    private static final int SELF_SIZE_IN_BYTES = 12;
    public static final FloatTag ZERO = new FloatTag(0.0F);
    public static final TagType<FloatTag> TYPE = new TagType.StaticSize<FloatTag>() {
        @Override
        public FloatTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
            return FloatTag.valueOf(readAccounted(dataInput, nbtAccounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor visitor, NbtAccounter tracker) throws IOException {
            return visitor.visit(readAccounted(input, tracker));
        }

        private static float readAccounted(DataInput input, NbtAccounter tracker) throws IOException {
            tracker.accountBytes(12L);
            return input.readFloat();
        }

        @Override
        public int size() {
            return 4;
        }

        @Override
        public String getName() {
            return "FLOAT";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Float";
        }

        @Override
        public boolean isValue() {
            return true;
        }
    };
    private final float data;

    private FloatTag(float value) {
        this.data = value;
    }

    public static FloatTag valueOf(float value) {
        return value == 0.0F ? ZERO : new FloatTag(value);
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeFloat(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 12;
    }

    @Override
    public byte getId() {
        return 5;
    }

    @Override
    public TagType<FloatTag> getType() {
        return TYPE;
    }

    @Override
    public FloatTag copy() {
        return this;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof FloatTag && this.data == ((FloatTag)object).data;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitFloat(this);
    }

    @Override
    public long getAsLong() {
        return (long)this.data;
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
        return (double)this.data;
    }

    @Override
    public float getAsFloat() {
        return this.data;
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
