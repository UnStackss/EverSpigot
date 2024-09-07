package net.minecraft.world.level.storage.loot.entries;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class NestedLootTable extends LootPoolSingletonContainer {
    public static final MapCodec<NestedLootTable> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.either(ResourceKey.codec(Registries.LOOT_TABLE), LootTable.DIRECT_CODEC).fieldOf("value").forGetter(entry -> entry.contents)
                )
                .and(singletonFields(instance))
                .apply(instance, NestedLootTable::new)
    );
    private final Either<ResourceKey<LootTable>, LootTable> contents;

    private NestedLootTable(
        Either<ResourceKey<LootTable>, LootTable> value, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions
    ) {
        super(weight, quality, conditions, functions);
        this.contents = value;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.LOOT_TABLE;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
        this.contents
            .map(
                key -> context.getResolver().get(Registries.LOOT_TABLE, (ResourceKey<LootTable>)key).map(Holder::value).orElse(LootTable.EMPTY),
                table -> (LootTable)table
            )
            .getRandomItemsRaw(context, lootConsumer);
    }

    @Override
    public void validate(ValidationContext reporter) {
        Optional<ResourceKey<LootTable>> optional = this.contents.left();
        if (optional.isPresent()) {
            ResourceKey<LootTable> resourceKey = optional.get();
            if (!reporter.allowsReferences()) {
                reporter.reportProblem("Uses reference to " + resourceKey.location() + ", but references are not allowed");
                return;
            }

            if (reporter.hasVisitedElement(resourceKey)) {
                reporter.reportProblem("Table " + resourceKey.location() + " is recursively called");
                return;
            }
        }

        super.validate(reporter);
        this.contents
            .ifLeft(
                key -> reporter.resolver()
                        .get(Registries.LOOT_TABLE, (ResourceKey<LootTable>)key)
                        .ifPresentOrElse(
                            entry -> entry.value().validate(reporter.enterElement("->{" + key.location() + "}", (ResourceKey<?>)key)),
                            () -> reporter.reportProblem("Unknown loot table called " + key.location())
                        )
            )
            .ifRight(table -> table.validate(reporter.forChild("->{inline}")));
    }

    public static LootPoolSingletonContainer.Builder<?> lootTableReference(ResourceKey<LootTable> key) {
        return simpleBuilder((weight, quality, conditions, functions) -> new NestedLootTable(Either.left(key), weight, quality, conditions, functions));
    }

    public static LootPoolSingletonContainer.Builder<?> inlineLootTable(LootTable table) {
        return simpleBuilder((weight, quality, conditions, functions) -> new NestedLootTable(Either.right(table), weight, quality, conditions, functions));
    }
}
