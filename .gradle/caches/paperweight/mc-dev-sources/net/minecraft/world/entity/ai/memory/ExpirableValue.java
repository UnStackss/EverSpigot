package net.minecraft.world.entity.ai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.util.VisibleForDebug;

public class ExpirableValue<T> {
    private final T value;
    private long timeToLive;

    public ExpirableValue(T value, long expiry) {
        this.value = value;
        this.timeToLive = expiry;
    }

    public void tick() {
        if (this.canExpire()) {
            this.timeToLive--;
        }
    }

    public static <T> ExpirableValue<T> of(T value) {
        return new ExpirableValue<>(value, Long.MAX_VALUE);
    }

    public static <T> ExpirableValue<T> of(T value, long expiry) {
        return new ExpirableValue<>(value, expiry);
    }

    public long getTimeToLive() {
        return this.timeToLive;
    }

    public T getValue() {
        return this.value;
    }

    public boolean hasExpired() {
        return this.timeToLive <= 0L;
    }

    @Override
    public String toString() {
        return this.value + (this.canExpire() ? " (ttl: " + this.timeToLive + ")" : "");
    }

    @VisibleForDebug
    public boolean canExpire() {
        return this.timeToLive != Long.MAX_VALUE;
    }

    public static <T> Codec<ExpirableValue<T>> codec(Codec<T> codec) {
        return RecordCodecBuilder.create(
            instance -> instance.group(
                        codec.fieldOf("value").forGetter(memory -> memory.value),
                        Codec.LONG.lenientOptionalFieldOf("ttl").forGetter(memory -> memory.canExpire() ? Optional.of(memory.timeToLive) : Optional.empty())
                    )
                    .apply(instance, (value, expiry) -> new ExpirableValue<>(value, expiry.orElse(Long.MAX_VALUE)))
        );
    }
}
