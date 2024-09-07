package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class CompositeEntryBase extends LootPoolEntryContainer {
    protected final List<LootPoolEntryContainer> children;
    private final ComposableEntryContainer composedChildren;

    protected CompositeEntryBase(List<LootPoolEntryContainer> terms, List<LootItemCondition> conditions) {
        super(conditions);
        this.children = terms;
        this.composedChildren = this.compose(terms);
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);
        if (this.children.isEmpty()) {
            reporter.reportProblem("Empty children list");
        }

        for (int i = 0; i < this.children.size(); i++) {
            this.children.get(i).validate(reporter.forChild(".entry[" + i + "]"));
        }
    }

    protected abstract ComposableEntryContainer compose(List<? extends ComposableEntryContainer> terms);

    @Override
    public final boolean expand(LootContext context, Consumer<LootPoolEntry> choiceConsumer) {
        return this.canRun(context) && this.composedChildren.expand(context, choiceConsumer);
    }

    public static <T extends CompositeEntryBase> MapCodec<T> createCodec(CompositeEntryBase.CompositeEntryConstructor<T> factory) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(LootPoolEntries.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter(entry -> entry.children))
                    .and(commonFields(instance).t1())
                    .apply(instance, factory::create)
        );
    }

    @FunctionalInterface
    public interface CompositeEntryConstructor<T extends CompositeEntryBase> {
        T create(List<LootPoolEntryContainer> terms, List<LootItemCondition> conditions);
    }
}
