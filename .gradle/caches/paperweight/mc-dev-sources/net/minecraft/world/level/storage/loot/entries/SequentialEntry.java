package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SequentialEntry extends CompositeEntryBase {
    public static final MapCodec<SequentialEntry> CODEC = createCodec(SequentialEntry::new);

    SequentialEntry(List<LootPoolEntryContainer> terms, List<LootItemCondition> conditions) {
        super(terms, conditions);
    }

    @Override
    public LootPoolEntryType getType() {
        return LootPoolEntries.SEQUENCE;
    }

    @Override
    protected ComposableEntryContainer compose(List<? extends ComposableEntryContainer> terms) {
        return switch (terms.size()) {
            case 0 -> ALWAYS_TRUE;
            case 1 -> (ComposableEntryContainer)terms.get(0);
            case 2 -> terms.get(0).and(terms.get(1));
            default -> (context, lootChoiceExpander) -> {
            for (ComposableEntryContainer composableEntryContainer : terms) {
                if (!composableEntryContainer.expand(context, lootChoiceExpander)) {
                    return false;
                }
            }

            return true;
        };
        };
    }

    public static SequentialEntry.Builder sequential(LootPoolEntryContainer.Builder<?>... entries) {
        return new SequentialEntry.Builder(entries);
    }

    public static class Builder extends LootPoolEntryContainer.Builder<SequentialEntry.Builder> {
        private final ImmutableList.Builder<LootPoolEntryContainer> entries = ImmutableList.builder();

        public Builder(LootPoolEntryContainer.Builder<?>... entries) {
            for (LootPoolEntryContainer.Builder<?> builder : entries) {
                this.entries.add(builder.build());
            }
        }

        @Override
        protected SequentialEntry.Builder getThis() {
            return this;
        }

        @Override
        public SequentialEntry.Builder then(LootPoolEntryContainer.Builder<?> entry) {
            this.entries.add(entry.build());
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return new SequentialEntry(this.entries.build(), this.getConditions());
        }
    }
}
