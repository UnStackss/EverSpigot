package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class TagEntry extends LootPoolSingletonContainer {
    public static final MapCodec<TagEntry> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    TagKey.codec(Registries.ITEM).fieldOf("name").forGetter(entry -> entry.tag), Codec.BOOL.fieldOf("expand").forGetter(entry -> entry.expand)
                )
                .and(singletonFields(instance))
                .apply(instance, TagEntry::new)
    );
    private final TagKey<Item> tag;
    private final boolean expand;

    private TagEntry(TagKey<Item> name, boolean expand, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.tag = name;
        this.expand = expand;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.TAG;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
        BuiltInRegistries.ITEM.getTagOrEmpty(this.tag).forEach(entry -> lootConsumer.accept(new ItemStack((Holder<Item>)entry)));
    }

    private boolean expandTag(LootContext context, Consumer<LootPoolEntry> lootChoiceExpander) {
        if (!this.canRun(context)) {
            return false;
        } else {
            for (final Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(this.tag)) {
                lootChoiceExpander.accept(new LootPoolSingletonContainer.EntryBase() {
                    @Override
                    public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
                        lootConsumer.accept(new ItemStack(holder));
                    }
                });
            }

            return true;
        }
    }

    @Override
    public boolean expand(LootContext context, Consumer<LootPoolEntry> choiceConsumer) {
        return this.expand ? this.expandTag(context, choiceConsumer) : super.expand(context, choiceConsumer);
    }

    public static LootPoolSingletonContainer.Builder<?> tagContents(TagKey<Item> name) {
        return simpleBuilder((weight, quality, conditions, functions) -> new TagEntry(name, false, weight, quality, conditions, functions));
    }

    public static LootPoolSingletonContainer.Builder<?> expandTag(TagKey<Item> name) {
        return simpleBuilder((weight, quality, conditions, functions) -> new TagEntry(name, true, weight, quality, conditions, functions));
    }
}
