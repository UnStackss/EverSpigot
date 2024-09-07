package net.minecraft.data.tags;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public abstract class ItemTagsProvider extends IntrinsicHolderTagsProvider<Item> {
    private final CompletableFuture<TagsProvider.TagLookup<Block>> blockTags;
    private final Map<TagKey<Block>, TagKey<Item>> tagsToCopy = new HashMap<>();

    public ItemTagsProvider(
        PackOutput output, CompletableFuture<HolderLookup.Provider> registryLookupFuture, CompletableFuture<TagsProvider.TagLookup<Block>> blockTagLookupFuture
    ) {
        super(output, Registries.ITEM, registryLookupFuture, item -> item.builtInRegistryHolder().key());
        this.blockTags = blockTagLookupFuture;
    }

    public ItemTagsProvider(
        PackOutput output,
        CompletableFuture<HolderLookup.Provider> registryLookupFuture,
        CompletableFuture<TagsProvider.TagLookup<Item>> parentTagLookupFuture,
        CompletableFuture<TagsProvider.TagLookup<Block>> blockTagLookupFuture
    ) {
        super(output, Registries.ITEM, registryLookupFuture, parentTagLookupFuture, item -> item.builtInRegistryHolder().key());
        this.blockTags = blockTagLookupFuture;
    }

    protected void copy(TagKey<Block> blockTag, TagKey<Item> itemTag) {
        this.tagsToCopy.put(blockTag, itemTag);
    }

    @Override
    protected CompletableFuture<HolderLookup.Provider> createContentsProvider() {
        return super.createContentsProvider().thenCombine(this.blockTags, (lookup, blockTags) -> {
            this.tagsToCopy.forEach((blockTag, itemTag) -> {
                TagBuilder tagBuilder = this.getOrCreateRawBuilder((TagKey<Item>)itemTag);
                Optional<TagBuilder> optional = blockTags.apply((TagKey<? super TagKey<Block>>)blockTag);
                optional.orElseThrow(() -> new IllegalStateException("Missing block tag " + itemTag.location())).build().forEach(tagBuilder::add);
            });
            return (HolderLookup.Provider)lookup;
        });
    }
}
