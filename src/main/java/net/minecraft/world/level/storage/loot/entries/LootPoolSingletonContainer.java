package net.minecraft.world.level.storage.loot.entries;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.Products.P4;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.codecs.RecordCodecBuilder.Mu;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.FunctionUserBuilder;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class LootPoolSingletonContainer extends LootPoolEntryContainer {
    public static final int DEFAULT_WEIGHT = 1;
    public static final int DEFAULT_QUALITY = 0;
    protected final int weight;
    protected final int quality;
    protected final List<LootItemFunction> functions;
    final BiFunction<ItemStack, LootContext, ItemStack> compositeFunction;
    private final LootPoolEntry entry = new LootPoolSingletonContainer.EntryBase() {
        @Override
        public void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context) {
            LootPoolSingletonContainer.this.createItemStack(
                LootItemFunction.decorate(LootPoolSingletonContainer.this.compositeFunction, lootConsumer, context), context
            );
        }
    };

    protected LootPoolSingletonContainer(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions) {
        super(conditions);
        this.weight = weight;
        this.quality = quality;
        this.functions = functions;
        this.compositeFunction = LootItemFunctions.compose(functions);
    }

    protected static <T extends LootPoolSingletonContainer> P4<Mu<T>, Integer, Integer, List<LootItemCondition>, List<LootItemFunction>> singletonFields(
        Instance<T> instance
    ) {
        return instance.group(
                Codec.INT.optionalFieldOf("weight", Integer.valueOf(1)).forGetter(entry -> entry.weight),
                Codec.INT.optionalFieldOf("quality", Integer.valueOf(0)).forGetter(entry -> entry.quality)
            )
            .and(commonFields(instance).t1())
            .and(LootItemFunctions.ROOT_CODEC.listOf().optionalFieldOf("functions", List.of()).forGetter(entry -> entry.functions));
    }

    @Override
    public void validate(ValidationContext reporter) {
        super.validate(reporter);

        for (int i = 0; i < this.functions.size(); i++) {
            this.functions.get(i).validate(reporter.forChild(".functions[" + i + "]"));
        }
    }

    protected abstract void createItemStack(Consumer<ItemStack> lootConsumer, LootContext context);

    @Override
    public boolean expand(LootContext context, Consumer<LootPoolEntry> choiceConsumer) {
        if (this.canRun(context)) {
            choiceConsumer.accept(this.entry);
            return true;
        } else {
            return false;
        }
    }

    public static LootPoolSingletonContainer.Builder<?> simpleBuilder(LootPoolSingletonContainer.EntryConstructor factory) {
        return new LootPoolSingletonContainer.DummyBuilder(factory);
    }

    public abstract static class Builder<T extends LootPoolSingletonContainer.Builder<T>>
        extends LootPoolEntryContainer.Builder<T>
        implements FunctionUserBuilder<T> {
        protected int weight = 1;
        protected int quality = 0;
        private final ImmutableList.Builder<LootItemFunction> functions = ImmutableList.builder();

        @Override
        public T apply(LootItemFunction.Builder builder) {
            this.functions.add(builder.build());
            return this.getThis();
        }

        protected List<LootItemFunction> getFunctions() {
            return this.functions.build();
        }

        public T setWeight(int weight) {
            this.weight = weight;
            return this.getThis();
        }

        public T setQuality(int quality) {
            this.quality = quality;
            return this.getThis();
        }
    }

    static class DummyBuilder extends LootPoolSingletonContainer.Builder<LootPoolSingletonContainer.DummyBuilder> {
        private final LootPoolSingletonContainer.EntryConstructor constructor;

        public DummyBuilder(LootPoolSingletonContainer.EntryConstructor factory) {
            this.constructor = factory;
        }

        @Override
        protected LootPoolSingletonContainer.DummyBuilder getThis() {
            return this;
        }

        @Override
        public LootPoolEntryContainer build() {
            return this.constructor.build(this.weight, this.quality, this.getConditions(), this.getFunctions());
        }
    }

    protected abstract class EntryBase implements LootPoolEntry {
        @Override
        public int getWeight(float luck) {
            return Math.max(Mth.floor((float)LootPoolSingletonContainer.this.weight + (float)LootPoolSingletonContainer.this.quality * luck), 0);
        }
    }

    @FunctionalInterface
    protected interface EntryConstructor {
        LootPoolSingletonContainer build(int weight, int quality, List<LootItemCondition> conditions, List<LootItemFunction> functions);
    }
}
