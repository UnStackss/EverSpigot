package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;

public class VibrationSelector {
    public static final Codec<VibrationSelector> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    VibrationInfo.CODEC
                        .lenientOptionalFieldOf("event")
                        .forGetter(vibrationSelector -> vibrationSelector.currentVibrationData.map(Pair::getLeft)),
                    Codec.LONG.fieldOf("tick").forGetter(vibrationSelector -> vibrationSelector.currentVibrationData.map(Pair::getRight).orElse(-1L))
                )
                .apply(instance, VibrationSelector::new)
    );
    private Optional<Pair<VibrationInfo, Long>> currentVibrationData;

    public VibrationSelector(Optional<VibrationInfo> vibration, long tick) {
        this.currentVibrationData = vibration.map(vibration2 -> Pair.of(vibration2, tick));
    }

    public VibrationSelector() {
        this.currentVibrationData = Optional.empty();
    }

    public void addCandidate(VibrationInfo vibration, long tick) {
        if (this.shouldReplaceVibration(vibration, tick)) {
            this.currentVibrationData = Optional.of(Pair.of(vibration, tick));
        }
    }

    private boolean shouldReplaceVibration(VibrationInfo vibration, long tick) {
        if (this.currentVibrationData.isEmpty()) {
            return true;
        } else {
            Pair<VibrationInfo, Long> pair = this.currentVibrationData.get();
            long l = pair.getRight();
            if (tick != l) {
                return false;
            } else {
                VibrationInfo vibrationInfo = pair.getLeft();
                return vibration.distance() < vibrationInfo.distance()
                    || !(vibration.distance() > vibrationInfo.distance())
                        && VibrationSystem.getGameEventFrequency(vibration.gameEvent()) > VibrationSystem.getGameEventFrequency(vibrationInfo.gameEvent());
            }
        }
    }

    public Optional<VibrationInfo> chosenCandidate(long currentTick) {
        if (this.currentVibrationData.isEmpty()) {
            return Optional.empty();
        } else {
            return this.currentVibrationData.get().getRight() < currentTick ? Optional.of(this.currentVibrationData.get().getLeft()) : Optional.empty();
        }
    }

    public void startOver() {
        this.currentVibrationData = Optional.empty();
    }
}
