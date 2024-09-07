package net.minecraft.network.chat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.BitSet;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.apache.commons.lang3.StringUtils;

public class FilterMask {
    public static final Codec<FilterMask> CODEC = StringRepresentable.fromEnum(FilterMask.Type::values).dispatch(FilterMask::type, FilterMask.Type::codec);
    public static final FilterMask FULLY_FILTERED = new FilterMask(new BitSet(0), FilterMask.Type.FULLY_FILTERED);
    public static final FilterMask PASS_THROUGH = new FilterMask(new BitSet(0), FilterMask.Type.PASS_THROUGH);
    public static final Style FILTERED_STYLE = Style.EMPTY
        .withColor(ChatFormatting.DARK_GRAY)
        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.filtered")));
    static final MapCodec<FilterMask> PASS_THROUGH_CODEC = MapCodec.unit(PASS_THROUGH);
    static final MapCodec<FilterMask> FULLY_FILTERED_CODEC = MapCodec.unit(FULLY_FILTERED);
    static final MapCodec<FilterMask> PARTIALLY_FILTERED_CODEC = ExtraCodecs.BIT_SET.xmap(FilterMask::new, FilterMask::mask).fieldOf("value");
    private static final char HASH = '#';
    private final BitSet mask;
    private final FilterMask.Type type;

    private FilterMask(BitSet mask, FilterMask.Type status) {
        this.mask = mask;
        this.type = status;
    }

    private FilterMask(BitSet mask) {
        this.mask = mask;
        this.type = FilterMask.Type.PARTIALLY_FILTERED;
    }

    public FilterMask(int length) {
        this(new BitSet(length), FilterMask.Type.PARTIALLY_FILTERED);
    }

    private FilterMask.Type type() {
        return this.type;
    }

    private BitSet mask() {
        return this.mask;
    }

    public static FilterMask read(FriendlyByteBuf buf) {
        FilterMask.Type type = buf.readEnum(FilterMask.Type.class);

        return switch (type) {
            case PASS_THROUGH -> PASS_THROUGH;
            case FULLY_FILTERED -> FULLY_FILTERED;
            case PARTIALLY_FILTERED -> new FilterMask(buf.readBitSet(), FilterMask.Type.PARTIALLY_FILTERED);
        };
    }

    public static void write(FriendlyByteBuf buf, FilterMask mask) {
        buf.writeEnum(mask.type);
        if (mask.type == FilterMask.Type.PARTIALLY_FILTERED) {
            buf.writeBitSet(mask.mask);
        }
    }

    public void setFiltered(int index) {
        this.mask.set(index);
    }

    @Nullable
    public String apply(String raw) {
        return switch (this.type) {
            case PASS_THROUGH -> raw;
            case FULLY_FILTERED -> null;
            case PARTIALLY_FILTERED -> {
                char[] cs = raw.toCharArray();

                for (int i = 0; i < cs.length && i < this.mask.length(); i++) {
                    if (this.mask.get(i)) {
                        cs[i] = '#';
                    }
                }

                yield new String(cs);
            }
        };
    }

    @Nullable
    public Component applyWithFormatting(String message) {
        return switch (this.type) {
            case PASS_THROUGH -> Component.literal(message);
            case FULLY_FILTERED -> null;
            case PARTIALLY_FILTERED -> {
                MutableComponent mutableComponent = Component.empty();
                int i = 0;
                boolean bl = this.mask.get(0);

                while (true) {
                    int j = bl ? this.mask.nextClearBit(i) : this.mask.nextSetBit(i);
                    j = j < 0 ? message.length() : j;
                    if (j == i) {
                        yield mutableComponent;
                    }

                    if (bl) {
                        mutableComponent.append(Component.literal(StringUtils.repeat('#', j - i)).withStyle(FILTERED_STYLE));
                    } else {
                        mutableComponent.append(message.substring(i, j));
                    }

                    bl = !bl;
                    i = j;
                }
            }
        };
    }

    public boolean isEmpty() {
        return this.type == FilterMask.Type.PASS_THROUGH;
    }

    public boolean isFullyFiltered() {
        return this.type == FilterMask.Type.FULLY_FILTERED;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            FilterMask filterMask = (FilterMask)object;
            return this.mask.equals(filterMask.mask) && this.type == filterMask.type;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int i = this.mask.hashCode();
        return 31 * i + this.type.hashCode();
    }

    static enum Type implements StringRepresentable {
        PASS_THROUGH("pass_through", () -> FilterMask.PASS_THROUGH_CODEC),
        FULLY_FILTERED("fully_filtered", () -> FilterMask.FULLY_FILTERED_CODEC),
        PARTIALLY_FILTERED("partially_filtered", () -> FilterMask.PARTIALLY_FILTERED_CODEC);

        private final String serializedName;
        private final Supplier<MapCodec<FilterMask>> codec;

        private Type(final String id, final Supplier<MapCodec<FilterMask>> codecSupplier) {
            this.serializedName = id;
            this.codec = codecSupplier;
        }

        @Override
        public String getSerializedName() {
            return this.serializedName;
        }

        private MapCodec<FilterMask> codec() {
            return this.codec.get();
        }
    }
}
