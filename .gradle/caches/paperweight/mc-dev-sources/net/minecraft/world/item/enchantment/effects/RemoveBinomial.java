package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record RemoveBinomial(LevelBasedValue chance) implements EnchantmentValueEffect {
    public static final MapCodec<RemoveBinomial> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("chance").forGetter(RemoveBinomial::chance)).apply(instance, RemoveBinomial::new)
    );

    @Override
    public float process(int level, RandomSource random, float inputValue) {
        float f = this.chance.calculate(level);
        int i = 0;

        for (int j = 0; (float)j < inputValue; j++) {
            if (random.nextFloat() < f) {
                i++;
            }
        }

        return inputValue - (float)i;
    }

    @Override
    public MapCodec<RemoveBinomial> codec() {
        return CODEC;
    }
}
