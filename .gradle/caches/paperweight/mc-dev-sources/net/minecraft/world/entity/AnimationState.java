package net.minecraft.world.entity;

import java.util.function.Consumer;
import net.minecraft.util.Mth;

public class AnimationState {
    private static final long STOPPED = Long.MAX_VALUE;
    private long lastTime = Long.MAX_VALUE;
    private long accumulatedTime;

    public void start(int age) {
        this.lastTime = (long)age * 1000L / 20L;
        this.accumulatedTime = 0L;
    }

    public void startIfStopped(int age) {
        if (!this.isStarted()) {
            this.start(age);
        }
    }

    public void animateWhen(boolean running, int age) {
        if (running) {
            this.startIfStopped(age);
        } else {
            this.stop();
        }
    }

    public void stop() {
        this.lastTime = Long.MAX_VALUE;
    }

    public void ifStarted(Consumer<AnimationState> consumer) {
        if (this.isStarted()) {
            consumer.accept(this);
        }
    }

    public void updateTime(float animationProgress, float speedMultiplier) {
        if (this.isStarted()) {
            long l = Mth.lfloor((double)(animationProgress * 1000.0F / 20.0F));
            this.accumulatedTime = this.accumulatedTime + (long)((float)(l - this.lastTime) * speedMultiplier);
            this.lastTime = l;
        }
    }

    public void fastForward(int seconds, float speedMultiplier) {
        if (this.isStarted()) {
            this.accumulatedTime += (long)((float)(seconds * 1000) * speedMultiplier) / 20L;
        }
    }

    public long getAccumulatedTime() {
        return this.accumulatedTime;
    }

    public boolean isStarted() {
        return this.lastTime != Long.MAX_VALUE;
    }
}
