package net.minecraft.world.effect;

import com.google.common.collect.ComparisonChain;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

public class MobEffectInstance implements Comparable<MobEffectInstance> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int INFINITE_DURATION = -1;
    public static final int MIN_AMPLIFIER = 0;
    public static final int MAX_AMPLIFIER = 255;
    public static final Codec<MobEffectInstance> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    MobEffect.CODEC.fieldOf("id").forGetter(MobEffectInstance::getEffect),
                    MobEffectInstance.Details.MAP_CODEC.forGetter(MobEffectInstance::asDetails)
                )
                .apply(instance, MobEffectInstance::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MobEffectInstance> STREAM_CODEC = StreamCodec.composite(
        MobEffect.STREAM_CODEC, MobEffectInstance::getEffect, MobEffectInstance.Details.STREAM_CODEC, MobEffectInstance::asDetails, MobEffectInstance::new
    );
    private final Holder<MobEffect> effect;
    private int duration;
    private int amplifier;
    private boolean ambient;
    private boolean visible;
    private boolean showIcon;
    @Nullable
    public MobEffectInstance hiddenEffect;
    private final MobEffectInstance.BlendState blendState = new MobEffectInstance.BlendState();

    public MobEffectInstance(Holder<MobEffect> effect) {
        this(effect, 0, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration) {
        this(effect, duration, 0);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier) {
        this(effect, duration, amplifier, false, true);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean visible) {
        this(effect, duration, amplifier, ambient, visible, visible);
    }

    public MobEffectInstance(Holder<MobEffect> effect, int duration, int amplifier, boolean ambient, boolean showParticles, boolean showIcon) {
        this(effect, duration, amplifier, ambient, showParticles, showIcon, null);
    }

    public MobEffectInstance(
        Holder<MobEffect> effect,
        int duration,
        int amplifier,
        boolean ambient,
        boolean showParticles,
        boolean showIcon,
        @Nullable MobEffectInstance hiddenEffect
    ) {
        this.effect = effect;
        this.duration = duration;
        this.amplifier = Mth.clamp(amplifier, 0, 255);
        this.ambient = ambient;
        this.visible = showParticles;
        this.showIcon = showIcon;
        this.hiddenEffect = hiddenEffect;
    }

    public MobEffectInstance(MobEffectInstance instance) {
        this.effect = instance.effect;
        this.setDetailsFrom(instance);
    }

    private MobEffectInstance(Holder<MobEffect> effect, MobEffectInstance.Details parameters) {
        this(
            effect,
            parameters.duration(),
            parameters.amplifier(),
            parameters.ambient(),
            parameters.showParticles(),
            parameters.showIcon(),
            parameters.hiddenEffect().map(parametersx -> new MobEffectInstance(effect, parametersx)).orElse(null)
        );
    }

    private MobEffectInstance.Details asDetails() {
        return new MobEffectInstance.Details(
            this.getAmplifier(),
            this.getDuration(),
            this.isAmbient(),
            this.isVisible(),
            this.showIcon(),
            Optional.ofNullable(this.hiddenEffect).map(MobEffectInstance::asDetails)
        );
    }

    public float getBlendFactor(LivingEntity entity, float tickDelta) {
        return this.blendState.getFactor(entity, tickDelta);
    }

    public ParticleOptions getParticleOptions() {
        return this.effect.value().createParticleOptions(this);
    }

    void setDetailsFrom(MobEffectInstance that) {
        this.duration = that.duration;
        this.amplifier = that.amplifier;
        this.ambient = that.ambient;
        this.visible = that.visible;
        this.showIcon = that.showIcon;
    }

    public boolean update(MobEffectInstance that) {
        if (!this.effect.equals(that.effect)) {
            LOGGER.warn("This method should only be called for matching effects!");
        }

        boolean bl = false;
        if (that.amplifier > this.amplifier) {
            if (that.isShorterDurationThan(this)) {
                MobEffectInstance mobEffectInstance = this.hiddenEffect;
                this.hiddenEffect = new MobEffectInstance(this);
                this.hiddenEffect.hiddenEffect = mobEffectInstance;
            }

            this.amplifier = that.amplifier;
            this.duration = that.duration;
            bl = true;
        } else if (this.isShorterDurationThan(that)) {
            if (that.amplifier == this.amplifier) {
                this.duration = that.duration;
                bl = true;
            } else if (this.hiddenEffect == null) {
                this.hiddenEffect = new MobEffectInstance(that);
            } else {
                this.hiddenEffect.update(that);
            }
        }

        if (!that.ambient && this.ambient || bl) {
            this.ambient = that.ambient;
            bl = true;
        }

        if (that.visible != this.visible) {
            this.visible = that.visible;
            bl = true;
        }

        if (that.showIcon != this.showIcon) {
            this.showIcon = that.showIcon;
            bl = true;
        }

        return bl;
    }

    private boolean isShorterDurationThan(MobEffectInstance effect) {
        return !this.isInfiniteDuration() && (this.duration < effect.duration || effect.isInfiniteDuration());
    }

    public boolean isInfiniteDuration() {
        return this.duration == -1;
    }

    public boolean endsWithin(int duration) {
        return !this.isInfiniteDuration() && this.duration <= duration;
    }

    public int mapDuration(Int2IntFunction mapper) {
        return !this.isInfiniteDuration() && this.duration != 0 ? mapper.applyAsInt(this.duration) : this.duration;
    }

    public Holder<MobEffect> getEffect() {
        return this.effect;
    }

    public int getDuration() {
        return this.duration;
    }

    public int getAmplifier() {
        return this.amplifier;
    }

    public boolean isAmbient() {
        return this.ambient;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean showIcon() {
        return this.showIcon;
    }

    public boolean tick(LivingEntity entity, Runnable overwriteCallback) {
        if (this.hasRemainingDuration()) {
            int i = this.isInfiniteDuration() ? entity.tickCount : this.duration;
            if (this.effect.value().shouldApplyEffectTickThisTick(i, this.amplifier) && !this.effect.value().applyEffectTick(entity, this.amplifier)) {
                entity.removeEffect(this.effect);
            }

            this.tickDownDuration();
            if (this.duration == 0 && this.hiddenEffect != null) {
                this.setDetailsFrom(this.hiddenEffect);
                this.hiddenEffect = this.hiddenEffect.hiddenEffect;
                overwriteCallback.run();
            }
        }

        this.blendState.tick(this);
        return this.hasRemainingDuration();
    }

    private boolean hasRemainingDuration() {
        return this.isInfiniteDuration() || this.duration > 0;
    }

    private int tickDownDuration() {
        if (this.hiddenEffect != null) {
            this.hiddenEffect.tickDownDuration();
        }

        return this.duration = this.mapDuration(duration -> duration - 1);
    }

    public void onEffectStarted(LivingEntity entity) {
        this.effect.value().onEffectStarted(entity, this.amplifier);
    }

    public void onMobRemoved(LivingEntity entity, Entity.RemovalReason reason) {
        this.effect.value().onMobRemoved(entity, this.amplifier, reason);
    }

    public void onMobHurt(LivingEntity entity, DamageSource source, float amount) {
        this.effect.value().onMobHurt(entity, this.amplifier, source, amount);
    }

    public String getDescriptionId() {
        return this.effect.value().getDescriptionId();
    }

    @Override
    public String toString() {
        String string;
        if (this.amplifier > 0) {
            string = this.getDescriptionId() + " x " + (this.amplifier + 1) + ", Duration: " + this.describeDuration();
        } else {
            string = this.getDescriptionId() + ", Duration: " + this.describeDuration();
        }

        if (!this.visible) {
            string = string + ", Particles: false";
        }

        if (!this.showIcon) {
            string = string + ", Show Icon: false";
        }

        return string;
    }

    private String describeDuration() {
        return this.isInfiniteDuration() ? "infinite" : Integer.toString(this.duration);
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof MobEffectInstance mobEffectInstance
                && this.duration == mobEffectInstance.duration
                && this.amplifier == mobEffectInstance.amplifier
                && this.ambient == mobEffectInstance.ambient
                && this.visible == mobEffectInstance.visible
                && this.showIcon == mobEffectInstance.showIcon
                && this.effect.equals(mobEffectInstance.effect);
    }

    @Override
    public int hashCode() {
        int i = this.effect.hashCode();
        i = 31 * i + this.duration;
        i = 31 * i + this.amplifier;
        i = 31 * i + (this.ambient ? 1 : 0);
        i = 31 * i + (this.visible ? 1 : 0);
        return 31 * i + (this.showIcon ? 1 : 0);
    }

    public Tag save() {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    @Nullable
    public static MobEffectInstance load(CompoundTag nbt) {
        return CODEC.parse(NbtOps.INSTANCE, nbt).resultOrPartial(LOGGER::error).orElse(null);
    }

    @Override
    public int compareTo(MobEffectInstance mobEffectInstance) {
        int i = 32147;
        return (this.getDuration() <= 32147 || mobEffectInstance.getDuration() <= 32147) && (!this.isAmbient() || !mobEffectInstance.isAmbient())
            ? ComparisonChain.start()
                .compareFalseFirst(this.isAmbient(), mobEffectInstance.isAmbient())
                .compareFalseFirst(this.isInfiniteDuration(), mobEffectInstance.isInfiniteDuration())
                .compare(this.getDuration(), mobEffectInstance.getDuration())
                .compare(this.getEffect().value().getColor(), mobEffectInstance.getEffect().value().getColor())
                .result()
            : ComparisonChain.start()
                .compare(this.isAmbient(), mobEffectInstance.isAmbient())
                .compare(this.getEffect().value().getColor(), mobEffectInstance.getEffect().value().getColor())
                .result();
    }

    public void onEffectAdded(LivingEntity entity) {
        this.effect.value().onEffectAdded(entity, this.amplifier);
    }

    public boolean is(Holder<MobEffect> effect) {
        return this.effect.equals(effect);
    }

    public void copyBlendState(MobEffectInstance effect) {
        this.blendState.copyFrom(effect.blendState);
    }

    public void skipBlending() {
        this.blendState.setImmediate(this);
    }

    static class BlendState {
        private float factor;
        private float factorPreviousFrame;

        public void setImmediate(MobEffectInstance effect) {
            this.factor = computeTarget(effect);
            this.factorPreviousFrame = this.factor;
        }

        public void copyFrom(MobEffectInstance.BlendState fading) {
            this.factor = fading.factor;
            this.factorPreviousFrame = fading.factorPreviousFrame;
        }

        public void tick(MobEffectInstance effect) {
            this.factorPreviousFrame = this.factor;
            int i = getBlendDuration(effect);
            if (i == 0) {
                this.factor = 1.0F;
            } else {
                float f = computeTarget(effect);
                if (this.factor != f) {
                    float g = 1.0F / (float)i;
                    this.factor = this.factor + Mth.clamp(f - this.factor, -g, g);
                }
            }
        }

        private static float computeTarget(MobEffectInstance effect) {
            boolean bl = !effect.endsWithin(getBlendDuration(effect));
            return bl ? 1.0F : 0.0F;
        }

        private static int getBlendDuration(MobEffectInstance effect) {
            return effect.getEffect().value().getBlendDurationTicks();
        }

        public float getFactor(LivingEntity entity, float tickDelta) {
            if (entity.isRemoved()) {
                this.factorPreviousFrame = this.factor;
            }

            return Mth.lerp(tickDelta, this.factorPreviousFrame, this.factor);
        }
    }

    static record Details(
        int amplifier, int duration, boolean ambient, boolean showParticles, boolean showIcon, Optional<MobEffectInstance.Details> hiddenEffect
    ) {
        public static final MapCodec<MobEffectInstance.Details> MAP_CODEC = MapCodec.recursive(
            "MobEffectInstance.Details",
            codec -> RecordCodecBuilder.mapCodec(
                    instance -> instance.group(
                                ExtraCodecs.UNSIGNED_BYTE.optionalFieldOf("amplifier", 0).forGetter(MobEffectInstance.Details::amplifier),
                                Codec.INT.optionalFieldOf("duration", Integer.valueOf(0)).forGetter(MobEffectInstance.Details::duration),
                                Codec.BOOL.optionalFieldOf("ambient", Boolean.valueOf(false)).forGetter(MobEffectInstance.Details::ambient),
                                Codec.BOOL.optionalFieldOf("show_particles", Boolean.valueOf(true)).forGetter(MobEffectInstance.Details::showParticles),
                                Codec.BOOL.optionalFieldOf("show_icon").forGetter(parameters -> Optional.of(parameters.showIcon())),
                                codec.optionalFieldOf("hidden_effect").forGetter(MobEffectInstance.Details::hiddenEffect)
                            )
                            .apply(instance, MobEffectInstance.Details::create)
                )
        );
        public static final StreamCodec<ByteBuf, MobEffectInstance.Details> STREAM_CODEC = StreamCodec.recursive(
            packetCodec -> StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::amplifier,
                    ByteBufCodecs.VAR_INT,
                    MobEffectInstance.Details::duration,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::ambient,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showParticles,
                    ByteBufCodecs.BOOL,
                    MobEffectInstance.Details::showIcon,
                    packetCodec.apply(ByteBufCodecs::optional),
                    MobEffectInstance.Details::hiddenEffect,
                    MobEffectInstance.Details::new
                )
        );

        private static MobEffectInstance.Details create(
            int amplifier, int duration, boolean ambient, boolean showParticles, Optional<Boolean> showIcon, Optional<MobEffectInstance.Details> hiddenEffect
        ) {
            return new MobEffectInstance.Details(amplifier, duration, ambient, showParticles, showIcon.orElse(showParticles), hiddenEffect);
        }
    }
}
