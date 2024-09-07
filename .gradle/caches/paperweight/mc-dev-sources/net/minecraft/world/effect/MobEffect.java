package net.minecraft.world.effect;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class MobEffect implements FeatureElement {
    public static final Codec<Holder<MobEffect>> CODEC = BuiltInRegistries.MOB_EFFECT.holderByNameCodec();
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<MobEffect>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.MOB_EFFECT);
    private static final int AMBIENT_ALPHA = Mth.floor(38.25F);
    public final Map<Holder<Attribute>, MobEffect.AttributeTemplate> attributeModifiers = new Object2ObjectOpenHashMap<>();
    private final MobEffectCategory category;
    private final int color;
    private final Function<MobEffectInstance, ParticleOptions> particleFactory;
    @Nullable
    private String descriptionId;
    private int blendDurationTicks;
    private Optional<SoundEvent> soundOnAdded = Optional.empty();
    private FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;

    protected MobEffect(MobEffectCategory category, int color) {
        this.category = category;
        this.color = color;
        this.particleFactory = effect -> {
            int j = effect.isAmbient() ? AMBIENT_ALPHA : 255;
            return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, FastColor.ARGB32.color(j, color));
        };
    }

    protected MobEffect(MobEffectCategory category, int color, ParticleOptions particleEffect) {
        this.category = category;
        this.color = color;
        this.particleFactory = effect -> particleEffect;
    }

    public int getBlendDurationTicks() {
        return this.blendDurationTicks;
    }

    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return true;
    }

    public void applyInstantenousEffect(@Nullable Entity source, @Nullable Entity attacker, LivingEntity target, int amplifier, double proximity) {
        this.applyEffectTick(target, amplifier);
    }

    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    public void onEffectStarted(LivingEntity entity, int amplifier) {
    }

    public void onEffectAdded(LivingEntity entity, int amplifier) {
        this.soundOnAdded
            .ifPresent(sound -> entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, entity.getSoundSource(), 1.0F, 1.0F));
    }

    public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
    }

    public void onMobHurt(LivingEntity entity, int amplifier, DamageSource source, float amount) {
    }

    public boolean isInstantenous() {
        return false;
    }

    protected String getOrCreateDescriptionId() {
        if (this.descriptionId == null) {
            this.descriptionId = Util.makeDescriptionId("effect", BuiltInRegistries.MOB_EFFECT.getKey(this));
        }

        return this.descriptionId;
    }

    public String getDescriptionId() {
        return this.getOrCreateDescriptionId();
    }

    public Component getDisplayName() {
        return Component.translatable(this.getDescriptionId());
    }

    public MobEffectCategory getCategory() {
        return this.category;
    }

    public int getColor() {
        return this.color;
    }

    public MobEffect addAttributeModifier(Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        this.attributeModifiers.put(attribute, new MobEffect.AttributeTemplate(id, amount, operation));
        return this;
    }

    public MobEffect setBlendDuration(int fadeTicks) {
        this.blendDurationTicks = fadeTicks;
        return this;
    }

    public void createModifiers(int amplifier, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
        this.attributeModifiers
            .forEach((attribute, attributeModifierCreator) -> consumer.accept((Holder<Attribute>)attribute, attributeModifierCreator.create(amplifier)));
    }

    public void removeAttributeModifiers(AttributeMap attributeContainer) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeInstance = attributeContainer.getInstance(entry.getKey());
            if (attributeInstance != null) {
                attributeInstance.removeModifier(entry.getValue().id());
            }
        }
    }

    public void addAttributeModifiers(AttributeMap attributeContainer, int amplifier) {
        for (Entry<Holder<Attribute>, MobEffect.AttributeTemplate> entry : this.attributeModifiers.entrySet()) {
            AttributeInstance attributeInstance = attributeContainer.getInstance(entry.getKey());
            if (attributeInstance != null) {
                attributeInstance.removeModifier(entry.getValue().id());
                attributeInstance.addPermanentModifier(entry.getValue().create(amplifier));
            }
        }
    }

    public boolean isBeneficial() {
        return this.category == MobEffectCategory.BENEFICIAL;
    }

    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return this.particleFactory.apply(effect);
    }

    public MobEffect withSoundOnAdded(SoundEvent sound) {
        this.soundOnAdded = Optional.of(sound);
        return this;
    }

    public MobEffect requiredFeatures(FeatureFlag... requiredFeatures) {
        this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
        return this;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    public static record AttributeTemplate(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        public AttributeModifier create(int amplifier) {
            return new AttributeModifier(this.id, this.amount * (double)(amplifier + 1), this.operation);
        }
    }
}
