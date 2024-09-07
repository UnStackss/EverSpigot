package net.minecraft.util.valueproviders;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;

public class WeightedListInt extends IntProvider {
    public static final MapCodec<WeightedListInt> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    SimpleWeightedRandomList.wrappedCodec(IntProvider.CODEC).fieldOf("distribution").forGetter(provider -> provider.distribution)
                )
                .apply(instance, WeightedListInt::new)
    );
    private final SimpleWeightedRandomList<IntProvider> distribution;
    private final int minValue;
    private final int maxValue;

    public WeightedListInt(SimpleWeightedRandomList<IntProvider> weightedList) {
        this.distribution = weightedList;
        List<WeightedEntry.Wrapper<IntProvider>> list = weightedList.unwrap();
        int i = Integer.MAX_VALUE;
        int j = Integer.MIN_VALUE;

        for (WeightedEntry.Wrapper<IntProvider> wrapper : list) {
            int k = wrapper.data().getMinValue();
            int l = wrapper.data().getMaxValue();
            i = Math.min(i, k);
            j = Math.max(j, l);
        }

        this.minValue = i;
        this.maxValue = j;
    }

    @Override
    public int sample(RandomSource random) {
        return this.distribution.getRandomValue(random).orElseThrow(IllegalStateException::new).sample(random);
    }

    @Override
    public int getMinValue() {
        return this.minValue;
    }

    @Override
    public int getMaxValue() {
        return this.maxValue;
    }

    @Override
    public IntProviderType<?> getType() {
        return IntProviderType.WEIGHTED_LIST;
    }
}
