package net.minecraft.world.item.alchemy;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class Potion implements FeatureElement {
    public static final Codec<Holder<Potion>> CODEC = BuiltInRegistries.POTION.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Potion>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.POTION);
    @Nullable
    private final String name;
    private final List<MobEffectInstance> effects;
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    public Potion(MobEffectInstance... effects) {
        this(null, effects);
    }

    public Potion(@Nullable String baseName, MobEffectInstance... effects) {
        this.name = baseName;
        this.effects = List.of(effects);
    }

    public Potion requiredFeatures(FeatureFlag... requiredFeatures) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public static String getName(Optional<Holder<Potion>> potion, String prefix) {
        if (potion.isPresent()) {
            String string = potion.get().value().name;
            if (string != null) {
                return prefix + string;
            }
        }

        String string2 = potion.flatMap(Holder::unwrapKey).map(key -> key.location().getPath()).orElse("empty");
        return prefix + string2;
    }

    public List<MobEffectInstance> getEffects() {
        return this.effects;
    }

    public boolean hasInstantEffects() {
        if (!this.effects.isEmpty()) {
            for (MobEffectInstance mobEffectInstance : this.effects) {
                if (mobEffectInstance.getEffect().value().isInstantenous()) {
                    return true;
                }
            }
        }

        return false;
    }
}
