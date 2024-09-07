package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import org.slf4j.Logger;

public interface ListOperation {
    MapCodec<ListOperation> UNLIMITED_CODEC = codec(Integer.MAX_VALUE);

    static MapCodec<ListOperation> codec(int maxSize) {
        return ListOperation.Type.CODEC.<ListOperation>dispatchMap("mode", ListOperation::mode, mode -> mode.mapCodec).validate(operation -> {
            if (operation instanceof ListOperation.ReplaceSection replaceSection && replaceSection.size().isPresent()) {
                int j = replaceSection.size().get();
                if (j > maxSize) {
                    return DataResult.error(() -> "Size value too large: " + j + ", max size is " + maxSize);
                }
            }

            return DataResult.success(operation);
        });
    }

    ListOperation.Type mode();

    default <T> List<T> apply(List<T> current, List<T> values) {
        return this.apply(current, values, Integer.MAX_VALUE);
    }

    <T> List<T> apply(List<T> current, List<T> values, int maxSize);

    public static class Append implements ListOperation {
        private static final Logger LOGGER = LogUtils.getLogger();
        public static final ListOperation.Append INSTANCE = new ListOperation.Append();
        public static final MapCodec<ListOperation.Append> MAP_CODEC = MapCodec.unit(() -> INSTANCE);

        private Append() {
        }

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.APPEND;
        }

        @Override
        public <T> List<T> apply(List<T> current, List<T> values, int maxSize) {
            if (current.size() + values.size() > maxSize) {
                LOGGER.error("Contents overflow in section append");
                return current;
            } else {
                return Stream.concat(current.stream(), values.stream()).toList();
            }
        }
    }

    public static record Insert(int offset) implements ListOperation {
        private static final Logger LOGGER = LogUtils.getLogger();
        public static final MapCodec<ListOperation.Insert> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(ListOperation.Insert::offset))
                    .apply(instance, ListOperation.Insert::new)
        );

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.INSERT;
        }

        @Override
        public <T> List<T> apply(List<T> current, List<T> values, int maxSize) {
            int i = current.size();
            if (this.offset > i) {
                LOGGER.error("Cannot insert when offset is out of bounds");
                return current;
            } else if (i + values.size() > maxSize) {
                LOGGER.error("Contents overflow in section insertion");
                return current;
            } else {
                Builder<T> builder = ImmutableList.builder();
                builder.addAll(current.subList(0, this.offset));
                builder.addAll(values);
                builder.addAll(current.subList(this.offset, i));
                return builder.build();
            }
        }
    }

    public static class ReplaceAll implements ListOperation {
        public static final ListOperation.ReplaceAll INSTANCE = new ListOperation.ReplaceAll();
        public static final MapCodec<ListOperation.ReplaceAll> MAP_CODEC = MapCodec.unit(() -> INSTANCE);

        private ReplaceAll() {
        }

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.REPLACE_ALL;
        }

        @Override
        public <T> List<T> apply(List<T> current, List<T> values, int maxSize) {
            return values;
        }
    }

    public static record ReplaceSection(int offset, Optional<Integer> size) implements ListOperation {
        private static final Logger LOGGER = LogUtils.getLogger();
        public static final MapCodec<ListOperation.ReplaceSection> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                        ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("offset", 0).forGetter(ListOperation.ReplaceSection::offset),
                        ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("size").forGetter(ListOperation.ReplaceSection::size)
                    )
                    .apply(instance, ListOperation.ReplaceSection::new)
        );

        public ReplaceSection(int offset) {
            this(offset, Optional.empty());
        }

        @Override
        public ListOperation.Type mode() {
            return ListOperation.Type.REPLACE_SECTION;
        }

        @Override
        public <T> List<T> apply(List<T> current, List<T> values, int maxSize) {
            int i = current.size();
            if (this.offset > i) {
                LOGGER.error("Cannot replace when offset is out of bounds");
                return current;
            } else {
                Builder<T> builder = ImmutableList.builder();
                builder.addAll(current.subList(0, this.offset));
                builder.addAll(values);
                int j = this.offset + this.size.orElse(values.size());
                if (j < i) {
                    builder.addAll(current.subList(j, i));
                }

                List<T> list = builder.build();
                if (list.size() > maxSize) {
                    LOGGER.error("Contents overflow in section replacement");
                    return current;
                } else {
                    return list;
                }
            }
        }
    }

    public static record StandAlone<T>(List<T> value, ListOperation operation) {
        public static <T> Codec<ListOperation.StandAlone<T>> codec(Codec<T> codec, int maxSize) {
            return RecordCodecBuilder.create(
                instance -> instance.group(
                            codec.sizeLimitedListOf(maxSize).fieldOf("values").forGetter(values -> values.value),
                            ListOperation.codec(maxSize).forGetter(values -> values.operation)
                        )
                        .apply(instance, ListOperation.StandAlone::new)
            );
        }

        public List<T> apply(List<T> current) {
            return this.operation.apply(current, this.value);
        }
    }

    public static enum Type implements StringRepresentable {
        REPLACE_ALL("replace_all", ListOperation.ReplaceAll.MAP_CODEC),
        REPLACE_SECTION("replace_section", ListOperation.ReplaceSection.MAP_CODEC),
        INSERT("insert", ListOperation.Insert.MAP_CODEC),
        APPEND("append", ListOperation.Append.MAP_CODEC);

        public static final Codec<ListOperation.Type> CODEC = StringRepresentable.fromEnum(ListOperation.Type::values);
        private final String id;
        final MapCodec<? extends ListOperation> mapCodec;

        private Type(final String id, final MapCodec<? extends ListOperation> codec) {
            this.id = id;
            this.mapCodec = codec;
        }

        public MapCodec<? extends ListOperation> mapCodec() {
            return this.mapCodec;
        }

        @Override
        public String getSerializedName() {
            return this.id;
        }
    }
}
