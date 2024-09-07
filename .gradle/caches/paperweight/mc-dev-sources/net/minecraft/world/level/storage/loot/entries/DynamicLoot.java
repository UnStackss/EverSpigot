package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class DynamicLoot extends LootPoolSingletonContainer {
    public static final MapCodec<DynamicLoot> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(ResourceLocation.CODEC.fieldOf("name").forGetter(entry -> entry.name))
                .and(singletonFields(instance))
                .apply(instance, DynamicLoot::new)
    );
    private final ResourceLocation name;

    private DynamicLoot(ResourceLocation name, int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(weight, quality, conditions, functions);
        this.name = name;
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.DYNAMIC;
    }

    @Override
    public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
        context.addDynamicDrops(this.name, lootConsumer);
    }

    public static LootPoolSingletonContainer.Builder<?> dynamicEntry(ResourceLocation name) {
        return simpleBuilder((weight, quality, conditions, functions) -> new DynamicLoot(name, weight, quality, conditions, functions));
    }
}
