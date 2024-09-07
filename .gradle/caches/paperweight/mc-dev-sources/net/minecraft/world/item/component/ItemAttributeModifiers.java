package net.minecraft.world.item.component;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public record ItemAttributeModifiers(List<ItemAttributeModifiers.Entry> modifiers, boolean showInTooltip) {
    public static final ItemAttributeModifiers EMPTY = new ItemAttributeModifiers(List.of(), true);
    private static final Codec<ItemAttributeModifiers> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ItemAttributeModifiers.Entry.CODEC.listOf().fieldOf("modifiers").forGetter(ItemAttributeModifiers::modifiers),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(ItemAttributeModifiers::showInTooltip)
                )
                .apply(instance, ItemAttributeModifiers::new)
    );
    public static final Codec<ItemAttributeModifiers> CODEC = Codec.withAlternative(
        FULL_CODEC, ItemAttributeModifiers.Entry.CODEC.listOf(), attributeModifiers -> new ItemAttributeModifiers(attributeModifiers, true)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers> STREAM_CODEC = StreamCodec.composite(
        ItemAttributeModifiers.Entry.STREAM_CODEC.apply(ByteBufCodecs.list()),
        ItemAttributeModifiers::modifiers,
        ByteBufCodecs.BOOL,
        ItemAttributeModifiers::showInTooltip,
        ItemAttributeModifiers::new
    );
    public static final DecimalFormat ATTRIBUTE_MODIFIER_FORMAT = Util.make(
        new DecimalFormat("#.##"), format -> format.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ROOT))
    );

    public ItemAttributeModifiers withTooltip(boolean showInTooltip) {
        return new ItemAttributeModifiers(this.modifiers, showInTooltip);
    }

    public static ItemAttributeModifiers.Builder builder() {
        return new ItemAttributeModifiers.Builder();
    }

    public ItemAttributeModifiers withModifierAdded(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        ImmutableList.Builder<ItemAttributeModifiers.Entry> builder = ImmutableList.builderWithExpectedSize(this.modifiers.size() + 1);

        for (ItemAttributeModifiers.Entry entry : this.modifiers) {
            if (!entry.matches(attribute, modifier.id())) {
                builder.add(entry);
            }
        }

        builder.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
        return new ItemAttributeModifiers(builder.build(), this.showInTooltip);
    }

    public void forEach(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeConsumer) {
        for (ItemAttributeModifiers.Entry entry : this.modifiers) {
            if (entry.slot.equals(slot)) {
                attributeConsumer.accept(entry.attribute, entry.modifier);
            }
        }
    }

    public void forEach(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeConsumer) {
        for (ItemAttributeModifiers.Entry entry : this.modifiers) {
            if (entry.slot.test(slot)) {
                attributeConsumer.accept(entry.attribute, entry.modifier);
            }
        }
    }

    public double compute(double base, EquipmentSlot slot) {
        double d = base;

        for (ItemAttributeModifiers.Entry entry : this.modifiers) {
            if (entry.slot.test(slot)) {
                double e = entry.modifier.amount();

                d += switch (entry.modifier.operation()) {
                    case ADD_VALUE -> e;
                    case ADD_MULTIPLIED_BASE -> e * base;
                    case ADD_MULTIPLIED_TOTAL -> e * d;
                };
            }
        }

        return d;
    }

    public static class Builder {
        private final ImmutableList.Builder<ItemAttributeModifiers.Entry> entries = ImmutableList.builder();

        Builder() {
        }

        public ItemAttributeModifiers.Builder add(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
            this.entries.add(new ItemAttributeModifiers.Entry(attribute, modifier, slot));
            return this;
        }

        public ItemAttributeModifiers build() {
            return new ItemAttributeModifiers(this.entries.build(), true);
        }
    }

    public static record Entry(Holder<Attribute> attribute, AttributeModifier modifier, EquipmentSlotGroup slot) {
        public static final Codec<ItemAttributeModifiers.Entry> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                        Attribute.CODEC.fieldOf("type").forGetter(ItemAttributeModifiers.Entry::attribute),
                        AttributeModifier.MAP_CODEC.forGetter(ItemAttributeModifiers.Entry::modifier),
                        EquipmentSlotGroup.CODEC.optionalFieldOf("slot", EquipmentSlotGroup.ANY).forGetter(ItemAttributeModifiers.Entry::slot)
                    )
                    .apply(instance, ItemAttributeModifiers.Entry::new)
        );
        public static final StreamCodec<RegistryFriendlyByteBuf, ItemAttributeModifiers.Entry> STREAM_CODEC = StreamCodec.composite(
            Attribute.STREAM_CODEC,
            ItemAttributeModifiers.Entry::attribute,
            AttributeModifier.STREAM_CODEC,
            ItemAttributeModifiers.Entry::modifier,
            EquipmentSlotGroup.STREAM_CODEC,
            ItemAttributeModifiers.Entry::slot,
            ItemAttributeModifiers.Entry::new
        );

        public boolean matches(Holder<Attribute> attribute, ResourceLocation modifierId) {
            return attribute.equals(this.attribute) && this.modifier.is(modifierId);
        }
    }
}
