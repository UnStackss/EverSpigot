package net.minecraft.world.entity;

import net.minecraft.util.Mth;

public class WalkAnimationState {
    private float speedOld;
    private float speed;
    private float position;

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void update(float speed, float multiplier) {
        this.speedOld = this.speed;
        this.speed = this.speed + (speed - this.speed) * multiplier;
        this.position = this.position + this.speed;
    }

    public float speed() {
        return this.speed;
    }

    public float speed(float tickDelta) {
        return Mth.lerp(tickDelta, this.speedOld, this.speed);
    }

    public float position() {
        return this.position;
    }

    public float position(float tickDelta) {
        return this.position - this.speed * (1.0F - tickDelta);
    }

    public boolean isMoving() {
        return this.speed > 1.0E-5F;
    }
}
