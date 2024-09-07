package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.LevelBasedValue;

public record SetValue(LevelBasedValue value) implements EnchantmentValueEffect {
    public static final MapCodec<SetValue> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(LevelBasedValue.CODEC.fieldOf("value").forGetter(SetValue::value)).apply(instance, SetValue::new)
    );

    @Override
    public float process(int level, RandomSource random, float inputValue) {
        return this.value.calculate(level);
    }

    @Override
    public MapCodec<SetValue> codec() {
        return CODEC;
    }
}
