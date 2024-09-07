package net.minecraft.world.entity.monster.warden;

import java.util.Arrays;
import net.minecraft.Util;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public enum AngerLevel {
    CALM(0, SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_LISTENING),
    AGITATED(40, SoundEvents.WARDEN_AGITATED, SoundEvents.WARDEN_LISTENING_ANGRY),
    ANGRY(80, SoundEvents.WARDEN_ANGRY, SoundEvents.WARDEN_LISTENING_ANGRY);

    private static final AngerLevel[] SORTED_LEVELS = Util.make(
        values(), values -> Arrays.sort(values, (a, b) -> Integer.compare(b.minimumAnger, a.minimumAnger))
    );
    private final int minimumAnger;
    private final SoundEvent ambientSound;
    private final SoundEvent listeningSound;

    private AngerLevel(final int threshold, final SoundEvent sound, final SoundEvent listeningSound) {
        this.minimumAnger = threshold;
        this.ambientSound = sound;
        this.listeningSound = listeningSound;
    }

    public int getMinimumAnger() {
        return this.minimumAnger;
    }

    public SoundEvent getAmbientSound() {
        return this.ambientSound;
    }

    public SoundEvent getListeningSound() {
        return this.listeningSound;
    }

    public static AngerLevel byAnger(int anger) {
        for (AngerLevel angerLevel : SORTED_LEVELS) {
            if (anger >= angerLevel.minimumAnger) {
                return angerLevel;
            }
        }

        return CALM;
    }

    public boolean isAngry() {
        return this == ANGRY;
    }
}
