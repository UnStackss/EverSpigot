package net.minecraft.world.level.storage.loot.providers.number;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

public record UniformGenerator(NumberProvider min, NumberProvider max) implements NumberProvider {
    public static final MapCodec<UniformGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    NumberProviders.CODEC.fieldOf("min").forGetter(UniformGenerator::min),
                    NumberProviders.CODEC.fieldOf("max").forGetter(UniformGenerator::max)
                )
                .apply(instance, UniformGenerator::new)
    );

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.UNIFORM;
    }

    public static UniformGenerator between(float min, float max) {
        return new UniformGenerator(ConstantValue.exactly(min), ConstantValue.exactly(max));
    }

    @Override
    public int getInt(LootContext context) {
        return Mth.nextInt(context.getRandom(), this.min.getInt(context), this.max.getInt(context));
    }

    @Override
    public float getFloat(LootContext context) {
        return Mth.nextFloat(context.getRandom(), this.min.getFloat(context), this.max.getFloat(context));
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(this.min.getReferencedContextParams(), this.max.getReferencedContextParams());
    }
}
