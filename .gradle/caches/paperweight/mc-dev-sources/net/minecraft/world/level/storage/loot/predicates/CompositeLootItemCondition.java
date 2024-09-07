package net.minecraft.world.level.storage.loot.predicates;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;

public abstract class CompositeLootItemCondition implements LootItemCondition {
    protected final List<LootItemCondition> terms;
    private final Predicate<LootContext> composedPredicate;

    protected CompositeLootItemCondition(List<LootItemCondition> terms, Predicate<LootContext> predicate) {
        this.terms = terms;
        this.composedPredicate = predicate;
    }

    protected static <T extends CompositeLootItemCondition> MapCodec<T> createCodec(Function<List<LootItemCondition>, T> termsToCondition) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(LootItemCondition.DIRECT_CODEC.listOf().fieldOf("terms").forGetter(condition -> condition.terms))
                    .apply(instance, termsToCondition)
        );
    }

    protected static <T extends CompositeLootItemCondition> Codec<T> createInlineCodec(Function<List<LootItemCondition>, T> termsToCondition) {
        return LootItemCondition.DIRECT_CODEC.listOf().xmap(termsToCondition, condition -> condition.terms);
    }

    @Override
    public final boolean test(LootContext lootContext) {
        return this.composedPredicate.test(lootContext);
    }

    @Override
    public void validate(ValidationContext reporter) {
        LootItemCondition.super.validate(reporter);

        for (int i = 0; i < this.terms.size(); i++) {
            this.terms.get(i).validate(reporter.forChild(".term[" + i + "]"));
        }
    }

    public abstract static class Builder implements LootItemCondition.Builder {
        private final ImmutableList.Builder<LootItemCondition> terms = ImmutableList.builder();

        protected Builder(LootItemCondition.Builder... terms) {
            for (LootItemCondition.Builder builder : terms) {
                this.terms.add(builder.build());
            }
        }

        public void addTerm(LootItemCondition.Builder builder) {
            this.terms.add(builder.build());
        }

        @Override
        public LootItemCondition build() {
            return this.create(this.terms.build());
        }

        protected abstract LootItemCondition create(List<LootItemCondition> terms);
    }
}
