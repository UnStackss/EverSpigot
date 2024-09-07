package net.minecraft.tags;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

public class TagEntry {
    private static final Codec<TagEntry> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ExtraCodecs.TAG_OR_ELEMENT_ID.fieldOf("id").forGetter(TagEntry::elementOrTag),
                    Codec.BOOL.optionalFieldOf("required", Boolean.valueOf(true)).forGetter(entry -> entry.required)
                )
                .apply(instance, TagEntry::new)
    );
    public static final Codec<TagEntry> CODEC = Codec.either(ExtraCodecs.TAG_OR_ELEMENT_ID, FULL_CODEC)
        .xmap(
            either -> either.map(id -> new TagEntry(id, true), entry -> (TagEntry)entry),
            entry -> entry.required ? Either.left(entry.elementOrTag()) : Either.right(entry)
        );
    private final ResourceLocation id;
    private final boolean tag;
    private final boolean required;

    private TagEntry(ResourceLocation id, boolean tag, boolean required) {
        this.id = id;
        this.tag = tag;
        this.required = required;
    }

    private TagEntry(ExtraCodecs.TagOrElementLocation id, boolean required) {
        this.id = id.id();
        this.tag = id.tag();
        this.required = required;
    }

    private ExtraCodecs.TagOrElementLocation elementOrTag() {
        return new ExtraCodecs.TagOrElementLocation(this.id, this.tag);
    }

    public static TagEntry element(ResourceLocation id) {
        return new TagEntry(id, false, true);
    }

    public static TagEntry optionalElement(ResourceLocation id) {
        return new TagEntry(id, false, false);
    }

    public static TagEntry tag(ResourceLocation id) {
        return new TagEntry(id, true, true);
    }

    public static TagEntry optionalTag(ResourceLocation id) {
        return new TagEntry(id, true, false);
    }

    public <T> boolean build(TagEntry.Lookup<T> valueGetter, Consumer<T> idConsumer) {
        if (this.tag) {
            Collection<T> collection = valueGetter.tag(this.id);
            if (collection == null) {
                return !this.required;
            }

            collection.forEach(idConsumer);
        } else {
            T object = valueGetter.element(this.id);
            if (object == null) {
                return !this.required;
            }

            idConsumer.accept(object);
        }

        return true;
    }

    public void visitRequiredDependencies(Consumer<ResourceLocation> idConsumer) {
        if (this.tag && this.required) {
            idConsumer.accept(this.id);
        }
    }

    public void visitOptionalDependencies(Consumer<ResourceLocation> idConsumer) {
        if (this.tag && !this.required) {
            idConsumer.accept(this.id);
        }
    }

    public boolean verifyIfPresent(Predicate<ResourceLocation> directEntryPredicate, Predicate<ResourceLocation> tagEntryPredicate) {
        return !this.required || (this.tag ? tagEntryPredicate : directEntryPredicate).test(this.id);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (this.tag) {
            stringBuilder.append('#');
        }

        stringBuilder.append(this.id);
        if (!this.required) {
            stringBuilder.append('?');
        }

        return stringBuilder.toString();
    }

    public interface Lookup<T> {
        @Nullable
        T element(ResourceLocation id);

        @Nullable
        Collection<T> tag(ResourceLocation id);
    }
}
