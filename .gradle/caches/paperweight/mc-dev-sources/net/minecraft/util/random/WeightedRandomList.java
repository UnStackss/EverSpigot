package net.minecraft.util.random;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;

public class WeightedRandomList<E extends WeightedEntry> {
    private final int totalWeight;
    private final ImmutableList<E> items;

    WeightedRandomList(List<? extends E> entries) {
        this.items = ImmutableList.copyOf(entries);
        this.totalWeight = WeightedRandom.getTotalWeight(entries);
    }

    public static <E extends WeightedEntry> WeightedRandomList<E> create() {
        return new WeightedRandomList<>(ImmutableList.of());
    }

    @SafeVarargs
    public static <E extends WeightedEntry> WeightedRandomList<E> create(E... entries) {
        return new WeightedRandomList<>(ImmutableList.copyOf(entries));
    }

    public static <E extends WeightedEntry> WeightedRandomList<E> create(List<E> entries) {
        return new WeightedRandomList<>(entries);
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public Optional<E> getRandom(RandomSource random) {
        if (this.totalWeight == 0) {
            return Optional.empty();
        } else {
            int i = random.nextInt(this.totalWeight);
            return WeightedRandom.getWeightedItem(this.items, i);
        }
    }

    public List<E> unwrap() {
        return this.items;
    }

    public static <E extends WeightedEntry> Codec<WeightedRandomList<E>> codec(Codec<E> entryCodec) {
        return entryCodec.listOf().xmap(WeightedRandomList::create, WeightedRandomList::unwrap);
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            WeightedRandomList<?> weightedRandomList = (WeightedRandomList<?>)object;
            return this.totalWeight == weightedRandomList.totalWeight && Objects.equals(this.items, weightedRandomList.items);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.totalWeight, this.items);
    }
}
