package net.minecraft.world.level.storage.loot.functions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

public class SetAttributesFunction extends LootItemConditionalFunction {
    public static final MapCodec<SetAttributesFunction> CODEC = RecordCodecBuilder.mapCodec(
        instance -> commonFields(instance)
                .and(
                    instance.group(
                        SetAttributesFunction.Modifier.CODEC.listOf().fieldOf("modifiers").forGetter(function -> function.modifiers),
                        Codec.BOOL.optionalFieldOf("replace", Boolean.valueOf(true)).forGetter(lootFunction -> lootFunction.replace)
                    )
                )
                .apply(instance, SetAttributesFunction::new)
    );
    private final List<SetAttributesFunction.Modifier> modifiers;
    private final boolean replace;

    SetAttributesFunction(List<LootItemCondition> conditions, List<SetAttributesFunction.Modifier> attributes, boolean replace) {
        super(conditions);
        this.modifiers = List.copyOf(attributes);
        this.replace = replace;
    }

    @Override
    public LootItemFunctionType<SetAttributesFunction> getType() {
        return LootItemFunctions.SET_ATTRIBUTES;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return this.modifiers.stream().flatMap(attribute -> attribute.amount.getReferencedContextParams().stream()).collect(ImmutableSet.toImmutableSet());
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        if (this.replace) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, this.updateModifiers(context, ItemAttributeModifiers.EMPTY));
        } else {
            stack.update(
                DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY,
                component -> component.modifiers().isEmpty()
                        ? this.updateModifiers(context, stack.getItem().getDefaultAttributeModifiers())
                        : this.updateModifiers(context, component)
            );
        }

        return stack;
    }

    private ItemAttributeModifiers updateModifiers(LootContext context, ItemAttributeModifiers attributeModifiersComponent) {
        RandomSource randomSource = context.getRandom();

        for (SetAttributesFunction.Modifier modifier : this.modifiers) {
            EquipmentSlotGroup equipmentSlotGroup = Util.getRandom(modifier.slots, randomSource);
            attributeModifiersComponent = attributeModifiersComponent.withModifierAdded(
                modifier.attribute, new AttributeModifier(modifier.id, (double)modifier.amount.getFloat(context), modifier.operation), equipmentSlotGroup
            );
        }

        return attributeModifiersComponent;
    }

    public static SetAttributesFunction.ModifierBuilder modifier(
        ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amountRange
    ) {
        return new SetAttributesFunction.ModifierBuilder(id, attribute, operation, amountRange);
    }

    public static SetAttributesFunction.Builder setAttributes() {
        return new SetAttributesFunction.Builder();
    }

    public static class Builder extends LootItemConditionalFunction.Builder<SetAttributesFunction.Builder> {
        private final boolean replace;
        private final List<SetAttributesFunction.Modifier> modifiers = Lists.newArrayList();

        public Builder(boolean replace) {
            this.replace = replace;
        }

        public Builder() {
            this(false);
        }

        @Override
        protected SetAttributesFunction.Builder getThis() {
            return this;
        }

        public SetAttributesFunction.Builder withModifier(SetAttributesFunction.ModifierBuilder attribute) {
            this.modifiers.add(attribute.build());
            return this;
        }

        @Override
        public LootItemFunction build() {
            return new SetAttributesFunction(this.getConditions(), this.modifiers, this.replace);
        }
    }

    static record Modifier(
        ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount, List<EquipmentSlotGroup> slots
    ) {
        private static final Codec<List<EquipmentSlotGroup>> SLOTS_CODEC = ExtraCodecs.nonEmptyList(
            Codec.either(EquipmentSlotGroup.CODEC, EquipmentSlotGroup.CODEC.listOf())
                .xmap(
                    either -> either.map(List::of, Function.identity()),
                    slots -> slots.size() == 1 ? Either.left(slots.getFirst()) : Either.right((List<EquipmentSlotGroup>)slots)
                )
        );
        public static final Codec<SetAttributesFunction.Modifier> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("id").forGetter(SetAttributesFunction.Modifier::id),
                        Attribute.CODEC.fieldOf("attribute").forGetter(SetAttributesFunction.Modifier::attribute),
                        AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(SetAttributesFunction.Modifier::operation),
                        NumberProviders.CODEC.fieldOf("amount").forGetter(SetAttributesFunction.Modifier::amount),
                        SLOTS_CODEC.fieldOf("slot").forGetter(SetAttributesFunction.Modifier::slots)
                    )
                    .apply(instance, SetAttributesFunction.Modifier::new)
        );
    }

    public static class ModifierBuilder {
        private final ResourceLocation id;
        private final Holder<Attribute> attribute;
        private final AttributeModifier.Operation operation;
        private final NumberProvider amount;
        private final Set<EquipmentSlotGroup> slots = EnumSet.noneOf(EquipmentSlotGroup.class);

        public ModifierBuilder(ResourceLocation id, Holder<Attribute> attribute, AttributeModifier.Operation operation, NumberProvider amount) {
            this.id = id;
            this.attribute = attribute;
            this.operation = operation;
            this.amount = amount;
        }

        public SetAttributesFunction.ModifierBuilder forSlot(EquipmentSlotGroup slot) {
            this.slots.add(slot);
            return this;
        }

        public SetAttributesFunction.Modifier build() {
            return new SetAttributesFunction.Modifier(this.id, this.attribute, this.operation, this.amount, List.copyOf(this.slots));
        }
    }
}
