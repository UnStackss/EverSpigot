package net.minecraft.server.packs;

import java.util.Map;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;

public class BuiltInMetadata {
    private static final BuiltInMetadata EMPTY = new BuiltInMetadata(Map.of());
    private final Map<MetadataSectionSerializer<?>, ?> values;

    private BuiltInMetadata(Map<MetadataSectionSerializer<?>, ?> values) {
        this.values = values;
    }

    public <T> T get(MetadataSectionSerializer<T> reader) {
        return (T)this.values.get(reader);
    }

    public static BuiltInMetadata of() {
        return EMPTY;
    }

    public static <T> BuiltInMetadata of(MetadataSectionSerializer<T> reader, T value) {
        return new BuiltInMetadata(Map.of(reader, value));
    }

    public static <T1, T2> BuiltInMetadata of(MetadataSectionSerializer<T1> reader, T1 value, MetadataSectionSerializer<T2> reader2, T2 value2) {
        return new BuiltInMetadata(Map.of(reader, value, reader2, (T1)value2));
    }
}
