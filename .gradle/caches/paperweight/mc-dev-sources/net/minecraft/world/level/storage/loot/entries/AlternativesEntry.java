package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class AlternativesEntry extends CompositeEntryBase {
    public static final MapCodec<AlternativesEntry> CODEC = createCodec(AlternativesEntry::new);

    AlternativesEntry(List<LootPoolEntryContainer> terms, List<LootItemCondition> conditions) {
        super(terms, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.ALTERNATIVES;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> terms) {
        return switch (terms.size()) {
            case 0 -> ALWAYS_FALSE;
            case 1 -> (ComposableEntryContainer)terms.get(0);
            case 2 -> terms.get(0).or(terms.get(1));
            default -> (context, lootChoiceExpander) -> {
            for (ComposableEntryContainer composableEntryContainer : terms) {
                if (composableEntryContainer.expand(context, lootChoiceExpander)) {
                    return true;
                }
            }

            return false;
        };
        };
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);

        for (int i = 0; i < this.children.size() - 1; i++) {
            if (this.children.get(i).conditions.isEmpty()) {
                reporter.reportProblem("Unreachable entry!");
            }
        }
    }

    public static AlternativesEntry.Builder alternatives(LootPoolEntryContainer.Builder<?>... children) {
        return new AlternativesEntry.Builder(children);
    }

    public static <E> AlternativesEntry.Builder alternatives(Collection<E> children, Function<E, LootPoolEntryContainer.Builder<?>> toBuilderFunction) {
        return new AlternativesEntry.Builder(children.stream().map(toBuilderFunction::apply).toArray(LootPoolEntryContainer.Builder[]::new));
    }

    public static class Builder extends LootPoolEntryContainer.Builder<AlternativesEntry.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... children) {
            for (LootPoolEntryContainer.Builder<?> builder : children) {
                this.entries.add(builder.build());
            }
        }

        @Override
        protected AlternativesEntry.Builder getThis() {
            return this;
        }

        @Override
        public AlternativesEntry.Builder otherwise(LootPoolEntryContainer.Builder<?> builder) {
            this.entries.add(builder.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new AlternativesEntry(this.entries.build(), this.getConditions());
        }
    }
}
