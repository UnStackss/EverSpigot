package net.minecraft.world.item.alchemy;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.FastColor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

public record PotionContents(Optional<Holder<Potion>> potion, Optional<Integer> customColor, List<MobEffectInstance> customEffects) {
    public static final PotionContents EMPTY = new PotionContents(Optional.empty(), Optional.empty(), List.of());
    private static final Component NO_EFFECT = Component.translatable("effect.none").withStyle(ChatFormatting.GRAY);
    private static final int BASE_POTION_COLOR = -13083194;
    private static final Codec<PotionContents> FULL_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    Potion.CODEC.optionalFieldOf("potion").forGetter(PotionContents::potion),
                    Codec.INT.optionalFieldOf("custom_color").forGetter(PotionContents::customColor),
                    MobEffectInstance.CODEC.listOf().optionalFieldOf("custom_effects", List.of()).forGetter(PotionContents::customEffects)
                )
                .apply(instance, PotionContents::new)
    );
    public static final Codec<PotionContents> CODEC = Codec.withAlternative(FULL_CODEC, Potion.CODEC, PotionContents::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, PotionContents> STREAM_CODEC = StreamCodec.composite(
        Potion.STREAM_CODEC.apply(ByteBufCodecs::optional),
        PotionContents::potion,
        ByteBufCodecs.INT.apply(ByteBufCodecs::optional),
        PotionContents::customColor,
        MobEffectInstance.STREAM_CODEC.apply(ByteBufCodecs.list()),
        PotionContents::customEffects,
        PotionContents::new
    );

    public PotionContents(Holder<Potion> potion) {
        this(Optional.of(potion), Optional.empty(), List.of());
    }

    public static ItemStack createItemStack(Item item, Holder<Potion> potion) {
        ItemStack itemStack = new ItemStack(item);
        itemStack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        return itemStack;
    }

    public boolean is(Holder<Potion> potion) {
        return this.potion.isPresent() && this.potion.get().is(potion) && this.customEffects.isEmpty();
    }

    public Iterable<MobEffectInstance> getAllEffects() {
        if (this.potion.isEmpty()) {
            return this.customEffects;
        } else {
            return (Iterable<MobEffectInstance>)(this.customEffects.isEmpty()
                ? this.potion.get().value().getEffects()
                : Iterables.concat(this.potion.get().value().getEffects(), this.customEffects));
        }
    }

    public void forEachEffect(Consumer<MobEffectInstance> effectConsumer) {
        if (this.potion.isPresent()) {
            for (MobEffectInstance mobEffectInstance : this.potion.get().value().getEffects()) {
                effectConsumer.accept(new MobEffectInstance(mobEffectInstance));
            }
        }

        for (MobEffectInstance mobEffectInstance2 : this.customEffects) {
            effectConsumer.accept(new MobEffectInstance(mobEffectInstance2));
        }
    }

    public PotionContents withPotion(Holder<Potion> potion) {
        return new PotionContents(Optional.of(potion), this.customColor, this.customEffects);
    }

    public PotionContents withEffectAdded(MobEffectInstance customEffect) {
        return new PotionContents(this.potion, this.customColor, Util.copyAndAdd(this.customEffects, customEffect));
    }

    public int getColor() {
        return this.customColor.isPresent() ? this.customColor.get() : getColor(this.getAllEffects());
    }

    public static int getColor(Holder<Potion> potion) {
        return getColor(potion.value().getEffects());
    }

    public static int getColor(Iterable<MobEffectInstance> effects) {
        return getColorOptional(effects).orElse(-13083194);
    }

    public static OptionalInt getColorOptional(Iterable<MobEffectInstance> effects) {
        int i = 0;
        int j = 0;
        int k = 0;
        int l = 0;

        for (MobEffectInstance mobEffectInstance : effects) {
            if (mobEffectInstance.isVisible()) {
                int m = mobEffectInstance.getEffect().value().getColor();
                int n = mobEffectInstance.getAmplifier() + 1;
                i += n * FastColor.ARGB32.red(m);
                j += n * FastColor.ARGB32.green(m);
                k += n * FastColor.ARGB32.blue(m);
                l += n;
            }
        }

        return l == 0 ? OptionalInt.empty() : OptionalInt.of(FastColor.ARGB32.color(i / l, j / l, k / l));
    }

    public boolean hasEffects() {
        return !this.customEffects.isEmpty() || this.potion.isPresent() && !this.potion.get().value().getEffects().isEmpty();
    }

    public List<MobEffectInstance> customEffects() {
        return Lists.transform(this.customEffects, MobEffectInstance::new);
    }

    public void addPotionTooltip(Consumer<Component> textConsumer, float durationMultiplier, float tickRate) {
        addPotionTooltip(this.getAllEffects(), textConsumer, durationMultiplier, tickRate);
    }

    public static void addPotionTooltip(Iterable<MobEffectInstance> effects, Consumer<Component> textConsumer, float durationMultiplier, float tickRate) {
        List<Pair<Holder<Attribute>, AttributeModifier>> list = Lists.newArrayList();
        boolean bl = true;

        for (MobEffectInstance mobEffectInstance : effects) {
            bl = false;
            MutableComponent mutableComponent = Component.translatable(mobEffectInstance.getDescriptionId());
            Holder<MobEffect> holder = mobEffectInstance.getEffect();
            holder.value().createModifiers(mobEffectInstance.getAmplifier(), (attribute, modifier) -> list.add(new Pair<>(attribute, modifier)));
            if (mobEffectInstance.getAmplifier() > 0) {
                mutableComponent = Component.translatable(
                    "potion.withAmplifier", mutableComponent, Component.translatable("potion.potency." + mobEffectInstance.getAmplifier())
                );
            }

            if (!mobEffectInstance.endsWithin(20)) {
                mutableComponent = Component.translatable(
                    "potion.withDuration", mutableComponent, MobEffectUtil.formatDuration(mobEffectInstance, durationMultiplier, tickRate)
                );
            }

            textConsumer.accept(mutableComponent.withStyle(holder.value().getCategory().getTooltipFormatting()));
        }

        if (bl) {
            textConsumer.accept(NO_EFFECT);
        }

        if (!list.isEmpty()) {
            textConsumer.accept(CommonComponents.EMPTY);
            textConsumer.accept(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));

            for (Pair<Holder<Attribute>, AttributeModifier> pair : list) {
                AttributeModifier attributeModifier = pair.getSecond();
                double d = attributeModifier.amount();
                double f;
                if (attributeModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                    && attributeModifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                    f = attributeModifier.amount();
                } else {
                    f = attributeModifier.amount() * 100.0;
                }

                if (d > 0.0) {
                    textConsumer.accept(
                        Component.translatable(
                                "attribute.modifier.plus." + attributeModifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(f),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.BLUE)
                    );
                } else if (d < 0.0) {
                    f *= -1.0;
                    textConsumer.accept(
                        Component.translatable(
                                "attribute.modifier.take." + attributeModifier.operation().id(),
                                ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(f),
                                Component.translatable(pair.getFirst().value().getDescriptionId())
                            )
                            .withStyle(ChatFormatting.RED)
                    );
                }
            }
        }
    }
}
