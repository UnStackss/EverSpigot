package net.minecraft.world.level.storage.loot.functions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetContainerLootTable extends LootItemConditionalFunction {
    public static final MapCodec<SetContainerLootTable> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("name").forGetter(function -> function.name),
                        Codec.LONG.optionalFieldOf("seed", Long.valueOf(0L)).forGetter(function -> function.seed),
                        BuiltInRegistries.BLOCK_ENTITY_TYPE.holderByNameCodec().fieldOf("type").forGetter(function -> function.type)
                    )
                )
                .apply(instance, SetContainerLootTable::new)
    );
    private final ResourceKey<LootTable> name;
    private final long seed;
    private final Holder<BlockEntityType<?>> type;

    private SetContainerLootTable(List<LootItemCondition> conditions, ResourceKey<LootTable> lootTable, long seed, Holder<BlockEntityType<?>> blockEntityType) {
        super(conditions);
        this.name = lootTable;
        this.seed = seed;
        this.type = blockEntityType;
    }

    @Override
    public LootItemFunctionType<SetContainerLootTable> getType() {
        return LootItemFunctions.SET_LOOT_TABLE;
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (stack.isEmpty()) {
            return stack;
        } else {
            stack.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(this.name, this.seed));
            return stack;
        }
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);
        if (!reporter.allowsReferences()) {
            reporter.reportProblem("Uses reference to " + this.name.location() + ", but references are not allowed");
        } else {
            if (reporter.resolver().get(Registries.LOOT_TABLE, this.name).isEmpty()) {
                reporter.reportProblem("Missing loot table used for container: " + this.name.location());
            }
        }
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> lootTable) {
        return simpleBuilder(conditions -> new SetContainerLootTable(conditions, lootTable, 0L, type.builtInRegistryHolder()));
    }

    public static LootItemConditionalFunction.Builder<?> withLootTable(BlockEntityType<?> type, ResourceKey<LootTable> lootTable, long seed) {
        return simpleBuilder(conditions -> new SetContainerLootTable(conditions, lootTable, seed, type.builtInRegistryHolder()));
    }
}
