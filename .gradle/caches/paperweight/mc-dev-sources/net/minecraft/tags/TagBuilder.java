package net.minecraft.tags;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class TagBuilder {
    private final List<TagEntry> entries = new ArrayList<>();

    public static TagBuilder create() {
        return new TagBuilder();
    }

    public List<TagEntry> build() {
        return List.copyOf(this.entries);
    }

    public TagBuilder add(TagEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public TagBuilder addElement(ResourceLocation id) {
        return this.add(TagEntry.element(id));
    }

    public TagBuilder addOptionalElement(ResourceLocation id) {
        return this.add(TagEntry.optionalElement(id));
    }

    public TagBuilder addTag(ResourceLocation id) {
        return this.add(TagEntry.tag(id));
    }

    public TagBuilder addOptionalTag(ResourceLocation id) {
        return this.add(TagEntry.optionalTag(id));
    }
}
