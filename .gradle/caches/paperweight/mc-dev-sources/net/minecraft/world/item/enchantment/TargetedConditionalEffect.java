package net.minecraft.world.item.enchantment;

import com.mojang.datafixers.util.Function4;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record TargetedConditionalEffect<T>(EnchantmentTarget enchanted, EnchantmentTarget affected, T effect, Optional<LootItemCondition> requirements) {
    public static <S> Codec<TargetedConditionalEffect<S>> codec(Codec<S> effectCodec, LootContextParamSet lootContextType) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                        EnchantmentTarget.CODEC.fieldOf("enchanted").forGetter(TargetedConditionalEffect::enchanted),
                        EnchantmentTarget.CODEC.fieldOf("affected").forGetter(TargetedConditionalEffect::affected),
                        effectCodec.fieldOf("effect").forGetter((Function<TargetedConditionalEffect<S>, S>)(TargetedConditionalEffect::effect)),
                        ConditionalEffect.conditionCodec(lootContextType).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
                    )
                    .apply(
                        instance,
                        (Function4<EnchantmentTarget, EnchantmentTarget, S, Optional<LootItemCondition>, TargetedConditionalEffect<S>>)(TargetedConditionalEffect::new)
                    )
        );
    }

    public static <S> Codec<TargetedConditionalEffect<S>> equipmentDropsCodec(Codec<S> effectCodec, LootContextParamSet lootContextType) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                        EnchantmentTarget.CODEC
                            .validate(
                                enchanted -> enchanted != EnchantmentTarget.DAMAGING_ENTITY
                                        ? DataResult.success(enchanted)
                                        : DataResult.error(() -> "enchanted must be attacker or victim")
                            )
                            .fieldOf("enchanted")
                            .forGetter(TargetedConditionalEffect::enchanted),
                        effectCodec.fieldOf("effect").forGetter((Function<TargetedConditionalEffect<S>, S>)(TargetedConditionalEffect::effect)),
                        ConditionalEffect.conditionCodec(lootContextType).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
                    )
                    .apply(
                        instance,
                        (enchantedx, effect, requirements) -> new TargetedConditionalEffect<>(enchantedx, EnchantmentTarget.VICTIM, effect, requirements)
                    )
        );
    }

    public boolean matches(LootContext lootContext) {
        return this.requirements.isEmpty() || this.requirements.get().test(lootContext);
    }
}
