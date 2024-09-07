package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {
    static <T, A extends T> MapCodec<A> codec(Codec<T> baseCodec, Function<List<T>, A> fromList, Function<A, List<T>> toList) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(baseCodec.listOf().fieldOf("effects").forGetter(toList)).apply(instance, fromList));
    }

    static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... entityEffects) {
        return new AllOf.EntityEffects(List.of(entityEffects));
    }

    static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... locationBasedEffects) {
        return new AllOf.LocationBasedEffects(List.of(locationBasedEffects));
    }

    static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... valueEffects) {
        return new AllOf.ValueEffects(List.of(valueEffects));
    }

    public static record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {
        public static final MapCodec<AllOf.EntityEffects> CODEC = AllOf.codec(
            EnchantmentEntityEffect.CODEC, AllOf.EntityEffects::new, AllOf.EntityEffects::effects
        );

        @Override
        public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
            for (EnchantmentEntityEffect enchantmentEntityEffect : this.effects) {
                enchantmentEntityEffect.apply(world, level, context, user, pos);
            }
        }

        @Override
        public MapCodec<AllOf.EntityEffects> codec() {
            return CODEC;
        }
    }

    public static record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {
        public static final MapCodec<AllOf.LocationBasedEffects> CODEC = AllOf.codec(
            EnchantmentLocationBasedEffect.CODEC, AllOf.LocationBasedEffects::new, AllOf.LocationBasedEffects::effects
        );

        @Override
        public void onChangedBlock(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos, boolean newlyApplied) {
            for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
                enchantmentLocationBasedEffect.onChangedBlock(world, level, context, user, pos, newlyApplied);
            }
        }

        @Override
        public void onDeactivated(EnchantedItemInUse context, Entity user, Vec3 pos, int level) {
            for (EnchantmentLocationBasedEffect enchantmentLocationBasedEffect : this.effects) {
                enchantmentLocationBasedEffect.onDeactivated(context, user, pos, level);
            }
        }

        @Override
        public MapCodec<AllOf.LocationBasedEffects> codec() {
            return CODEC;
        }
    }

    public static record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {
        public static final MapCodec<AllOf.ValueEffects> CODEC = AllOf.codec(EnchantmentValueEffect.CODEC, AllOf.ValueEffects::new, AllOf.ValueEffects::effects);

        @Override
        public float process(int level, RandomSource random, float inputValue) {
            for (EnchantmentValueEffect enchantmentValueEffect : this.effects) {
                inputValue = enchantmentValueEffect.process(level, random, inputValue);
            }

            return inputValue;
        }

        @Override
        public MapCodec<AllOf.ValueEffects> codec() {
            return CODEC;
        }
    }
}
