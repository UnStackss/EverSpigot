package net.minecraft.world.level.storage.loot.providers.number;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;

public record BinomialDistributionGenerator(NumberProvider n, NumberProvider p) implements NumberProvider {
    public static final MapCodec<BinomialDistributionGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    NumberProviders.CODEC.fieldOf("n").forGetter(BinomialDistributionGenerator::n),
                    NumberProviders.CODEC.fieldOf("p").forGetter(BinomialDistributionGenerator::p)
                )
                .apply(instance, BinomialDistributionGenerator::new)
    );

    @Override
    public LootNumberProviderType getType() {
        return NumberProviders.BINOMIAL;
    }

    @Override
    public int getInt(LootContext context) {
        int i = this.n.getInt(context);
        float f = this.p.getFloat(context);
        RandomSource randomSource = context.getRandom();
        int j = 0;

        for (int k = 0; k < i; k++) {
            if (randomSource.nextFloat() < f) {
                j++;
            }
        }

        return j;
    }

    @Override
    public float getFloat(LootContext context) {
        return (float)this.getInt(context);
    }

    public static BinomialDistributionGenerator binomial(int n, float p) {
        return new BinomialDistributionGenerator(ConstantValue.exactly((float)n), ConstantValue.exactly(p));
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Sets.union(this.n.getReferencedContextParams(), this.p.getReferencedContextParams());
    }
}
