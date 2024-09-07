package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

public record InvertedLootItemCondition(LootItemCondition term) implements LootItemCondition {
    public static final MapCodec<InvertedLootItemCondition> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LootItemCondition.DIRECT_CODEC.fieldOf("term").forGetter(InvertedLootItemCondition::term))
                .apply(instance, InvertedLootItemCondition::new)
    );

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.INVERTED;
    }

    @Override
    public boolean test(LootContext lootContext) {
        return !this.term.test(lootContext);
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.term.getReferencedContextParams();
    }

    @Override
    public void validate(ValidationContext reporter) {
        LootItemCondition.super.validate(reporter);
        this.term.validate(reporter);
    }

    public static LootItemCondition.Builder invert(LootItemCondition.Builder term) {
        InvertedLootItemCondition invertedLootItemCondition = new InvertedLootItemCondition(term.build());
        return () -> invertedLootItemCondition;
    }
}
