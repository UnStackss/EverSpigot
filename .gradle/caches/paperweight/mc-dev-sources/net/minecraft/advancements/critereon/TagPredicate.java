package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;

public record TagPredicate<T>(TagKey<T> tag, boolean expected) {
    public static <T> Codec<TagPredicate<T>> codec(ResourceKey<? extends Registry<T>> registryRef) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                        TagKey.codec(registryRef).fieldOf("id").forGetter(TagPredicate::tag), Codec.BOOL.fieldOf("expected").forGetter(TagPredicate::expected)
                    )
                    .apply(instance, TagPredicate::new)
        );
    }

    public static <T> TagPredicate<T> is(TagKey<T> tag) {
        return new TagPredicate<>(tag, true);
    }

    public static <T> TagPredicate<T> isNot(TagKey<T> tag) {
        return new TagPredicate<>(tag, false);
    }

    public boolean matches(Holder<T> registryEntry) {
        return registryEntry.is(this.tag) == this.expected;
    }
}
