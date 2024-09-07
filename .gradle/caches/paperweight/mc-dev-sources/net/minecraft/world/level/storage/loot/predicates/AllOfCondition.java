package net.minecraft.world.level.storage.loot.predicates;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.Util;

public class AllOfCondition extends CompositeLootItemCondition {
    public static final MapCodec<AllOfCondition> CODEC = createCodec(AllOfCondition::new);
    public static final Codec<AllOfCondition> INLINE_CODEC = createInlineCodec(AllOfCondition::new);

    AllOfCondition(List<LootItemCondition> terms) {
        super(terms, Util.allOf(terms));
    }

    public static AllOfCondition allOf(List<LootItemCondition> terms) {
        return new AllOfCondition(List.copyOf(terms));
    }

    @Override
    public LootItemConditionType getType() {
        return LootItemConditions.ALL_OF;
    }

    public static AllOfCondition.Builder allOf(LootItemCondition.Builder... terms) {
        return new AllOfCondition.Builder(terms);
    }

    public static class Builder extends CompositeLootItemCondition.Builder {
        public Builder(LootItemCondition.Builder... terms) {
            super(terms);
        }

        @Override
        public AllOfCondition.Builder and(LootItemCondition.Builder condition) {
            this.addTerm(condition);
            return this;
        }

        @Override
        protected LootItemCondition create(List<LootItemCondition> terms) {
            return new AllOfCondition(terms);
        }
    }
}
