package net.minecraft.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

public class RandomSequence {
    public static final Codec<RandomSequence> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(XoroshiroRandomSource.CODEC.fieldOf("source").forGetter(sequence -> sequence.source)).apply(instance, RandomSequence::new)
    );
    private final XoroshiroRandomSource source;

    public RandomSequence(XoroshiroRandomSource source) {
        this.source = source;
    }

    public RandomSequence(long seed, ResourceLocation id) {
        this(createSequence(seed, Optional.of(id)));
    }

    public RandomSequence(long seed, Optional<ResourceLocation> id) {
        this(createSequence(seed, id));
    }

    private static XoroshiroRandomSource createSequence(long seed, Optional<ResourceLocation> id) {
        RandomSupport.Seed128bit seed128bit = RandomSupport.upgradeSeedTo128bitUnmixed(seed);
        if (id.isPresent()) {
            seed128bit = seed128bit.xor(seedForKey(id.get()));
        }

        return new XoroshiroRandomSource(seed128bit.mixed());
    }

    public static RandomSupport.Seed128bit seedForKey(ResourceLocation id) {
        return RandomSupport.seedFromHashOf(id.toString());
    }

    public RandomSource random() {
        return this.source;
    }
}
