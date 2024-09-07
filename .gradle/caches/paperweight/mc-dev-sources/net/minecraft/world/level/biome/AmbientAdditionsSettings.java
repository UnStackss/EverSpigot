package net.minecraft.world.level.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public class AmbientAdditionsSettings {
    public static final Codec<AmbientAdditionsSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    SoundEvent.CODEC.fieldOf("sound").forGetter(sound -> sound.soundEvent),
                    Codec.DOUBLE.fieldOf("tick_chance").forGetter(sound -> sound.tickChance)
                )
                .apply(instance, AmbientAdditionsSettings::new)
    );
    private final Holder<SoundEvent> soundEvent;
    private final double tickChance;

    public AmbientAdditionsSettings(Holder<SoundEvent> sound, double chance) {
        this.soundEvent = sound;
        this.tickChance = chance;
    }

    public Holder<SoundEvent> getSoundEvent() {
        return this.soundEvent;
    }

    public double getTickChance() {
        return this.tickChance;
    }
}
