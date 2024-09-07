package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class EntryGroup extends CompositeEntryBase {
    public static final MapCodec<EntryGroup> CODEC = createCodec(EntryGroup::new);

    EntryGroup(List<LootPoolEntryContainer> terms, List<LootItemCondition> conditions) {
        super(terms, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.GROUP;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> terms) {
        return switch (terms.size()) {
            case 0 -> ALWAYS_TRUE;
            case 1 -> (ComposableEntryContainer)terms.get(0);
            case 2 -> {
                ComposableEntryContainer composableEntryContainer = terms.get(0);
                ComposableEntryContainer composableEntryContainer2 = terms.get(1);
                yield (context, choiceConsumer) -> {
                    composableEntryContainer.expand(context, choiceConsumer);
                    composableEntryContainer2.expand(context, choiceConsumer);
                    return true;
                };
            }
            default -> (context, lootChoiceExpander) -> {
            for (ComposableEntryContainer composableEntryContainerx : terms) {
                composableEntryContainerx.expand(context, lootChoiceExpander);
            }

            return true;
        };
        };
    }

    public static EntryGroup.Builder list(LootPoolEntryContainer.Builder<?>... entries) {
        return new EntryGroup.Builder(entries);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<EntryGroup.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... entries) {
            for (LootPoolEntryContainer.Builder<?> builder : entries) {
                this.entries.add(builder.build());
            }
        }

        @Override
        protected EntryGroup.Builder getThis() {
            return this;
        }

        @Override
        public EntryGroup.Builder append(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new EntryGroup(this.entries.build(), this.getConditions());
        }
    }
}
