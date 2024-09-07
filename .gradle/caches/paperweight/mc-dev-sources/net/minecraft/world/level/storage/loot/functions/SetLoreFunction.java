package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public class SetLoreFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetLoreFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        ComponentSerialization.CODEC.sizeLimitedListOf(256).fieldOf("lore").forGetter(function -> function.lore),
                        ListOperation.codec(256).forGetter(function -> function.mode),
                        LootContext.EntityTarget.CODEC.optionalFieldOf("entity").forGetter(function -> function.resolutionContext)
                    )
                )
                .apply(instance, SetLoreFunction::new)
    );
    private final List<Component> lore;
    private final ListOperation mode;
    private final Optional<LootContext.EntityTarget> resolutionContext;

    public SetLoreFunction(List<LootItemCondition> conditions, List<Component> lore, ListOperation operation, Optional<LootContext.EntityTarget> entity) {
        super(conditions);
        this.lore = List.copyOf(lore);
        this.mode = operation;
        this.resolutionContext = entity;
    }

    @Override
    public LootItemFunctionType<SetLoreFunction> getType() {
        return LootItemFunctions.SET_LORE;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.resolutionContext.<Set<LootContextParam<?>>>map(entity -> Set.of(entity.getParam())).orElseGet(Set::of);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        stack.update(DataComponents.LORE, ItemLore.EMPTY, component -> new ItemLore(this.updateLore(component, context)));
        return stack;
    }

    private List<Component> updateLore(@Nullable ItemLore current, LootContext context) {
        if (current == null && this.lore.isEmpty()) {
            return List.of();
        } else {
            UnaryOperator<Component> unaryOperator = SetNameFunction.createResolver(context, this.resolutionContext.orElse(null));
            List<Component> list = this.lore.stream().map(unaryOperator).toList();
            return this.mode.apply(current.lines(), list, 256);
        }
    }

    public static SetLoreFunction.Builder setLore() {
        return new SetLoreFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetLoreFunction.Builder> {
        private Optional<LootContext.EntityTarget> resolutionContext = Optional.empty();
        private final ImmutableList.Builder<Component> lore = ImmutableList.builder();
        private ListOperation mode = ListOperation.Append.INSTANCE;

        public SetLoreFunction.Builder setMode(ListOperation operation) {
            this.mode = operation;
            return this;
        }

        public SetLoreFunction.Builder setResolutionContext(LootContext.EntityTarget target) {
            this.resolutionContext = Optional.of(target);
            return this;
        }

        public SetLoreFunction.Builder addLine(Component lore) {
            this.lore.add(lore);
            return this;
        }

        @Override
        protected SetLoreFunction.Builder getThis() {
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetLoreFunction(this.getConditions(), this.lore.build(), this.mode, this.resolutionContext);
        }
    }
}
