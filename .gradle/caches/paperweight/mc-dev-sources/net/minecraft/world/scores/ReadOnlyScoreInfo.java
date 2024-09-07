package net.minecraft.world.scores;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;

public interface ReadOnlyScoreInfo {
    int value();

    boolean isLocked();

    @Nullable
    NumberFormat numberFormat();

    default MutableComponent formatValue(NumberFormat fallbackFormat) {
        return Objects.requireNonNullElse(this.numberFormat(), fallbackFormat).format(this.value());
    }

    static MutableComponent safeFormatValue(@Nullable ReadOnlyScoreInfo score, NumberFormat fallbackFormat) {
        return score != null ? score.formatValue(fallbackFormat) : fallbackFormat.format(0);
    }
}
