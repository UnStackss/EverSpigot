package net.minecraft.sounds;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

public class Music {
    public static final Codec<Music> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    SoundEvent.CODEC.fieldOf("sound").forGetter(sound -> sound.event),
                    Codec.INT.fieldOf("min_delay").forGetter(sound -> sound.minDelay),
                    Codec.INT.fieldOf("max_delay").forGetter(sound -> sound.maxDelay),
                    Codec.BOOL.fieldOf("replace_current_music").forGetter(sound -> sound.replaceCurrentMusic)
                )
                .apply(instance, Music::new)
    );
    private final Holder<SoundEvent> event;
    private final int minDelay;
    private final int maxDelay;
    private final boolean replaceCurrentMusic;

    public Music(Holder<SoundEvent> sound, int minDelay, int maxDelay, boolean replaceCurrentMusic) {
        this.event = sound;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.replaceCurrentMusic = replaceCurrentMusic;
    }

    public Holder<SoundEvent> getEvent() {
        return this.event;
    }

    public int getMinDelay() {
        return this.minDelay;
    }

    public int getMaxDelay() {
        return this.maxDelay;
    }

    public boolean replaceCurrentMusic() {
        return this.replaceCurrentMusic;
    }
}
