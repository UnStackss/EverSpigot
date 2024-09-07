package net.minecraft.world.scores;

import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;

public interface ScoreAccess {
    int get();

    void set(int score);

    default int add(int amount) {
        int i = this.get() + amount;
        this.set(i);
        return i;
    }

    default int increment() {
        return this.add(1);
    }

    default void reset() {
        this.set(0);
    }

    boolean locked();

    void unlock();

    void lock();

    @Nullable
    Component display();

    void display(@Nullable Component text);

    void numberFormatOverride(@Nullable NumberFormat numberFormat);
}
