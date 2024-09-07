package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.Util;

public class AnyOfCondition extends CompositeLootItemCondition {
    public static final MapCodec<AnyOfCondition> CODEC = createCodec(AnyOfCondition::new);

    AnyOfCondition(List<LootItemCondition> terms) {
        super(terms, Util.anyOf(terms));
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ANY_OF;
    }

    public static AnyOfCondition.Builder anyOf(LootItemCondition.Builder... terms) {
        return new AnyOfCondition.Builder(terms);
    }

    public static class Builder extends CompositeLootItemCondition.Builder {
        public Builder(LootItemCondition.Builder... terms) {
            super(terms);
        }

        @Override
        public AnyOfCondition.Builder or(LootItemCondition.Builder condition) {
            this.addTerm(condition);
            return this;
        }

        @Override
        protected LootItemCondition create(List<LootItemCondition> terms) {
            return new AnyOfCondition(terms);
        }
    }
}
