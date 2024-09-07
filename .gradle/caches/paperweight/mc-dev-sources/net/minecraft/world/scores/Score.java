package net.minecraft.world.scores;

import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;

public class Score implements ReadOnlyScoreInfo {
    private static final String TAG_SCORE = "Score";
    private static final String TAG_LOCKED = "Locked";
    private static final String TAG_DISPLAY = "display";
    private static final String TAG_FORMAT = "format";
    private int value;
    private boolean locked = true;
    @Nullable
    private Component display;
    @Nullable
    private NumberFormat numberFormat;

    @Override
    public int value() {
        return this.value;
    }

    public void value(int score) {
        this.value = score;
    }

    @Override
    public boolean isLocked() {
        return this.locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Nullable
    public Component display() {
        return this.display;
    }

    public void display(@Nullable Component text) {
        this.display = text;
    }

    @Nullable
    @Override
    public NumberFormat numberFormat() {
        return this.numberFormat;
    }

    public void numberFormat(@Nullable NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putInt("Score", this.value);
        compoundTag.putBoolean("Locked", this.locked);
        if (this.display != null) {
            compoundTag.putString("display", Component.Serializer.toJson(this.display, registries));
        }

        if (this.numberFormat != null) {
            NumberFormatTypes.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this.numberFormat)
                .ifSuccess(formatElement -> compoundTag.put("format", formatElement));
        }

        return compoundTag;
    }

    public static Score read(CompoundTag nbt, HolderLookup.Provider registries) {
        Score score = new Score();
        score.value = nbt.getInt("Score");
        score.locked = nbt.getBoolean("Locked");
        if (nbt.contains("display", 8)) {
            score.display = Component.Serializer.fromJson(nbt.getString("display"), registries);
        }

        if (nbt.contains("format", 10)) {
            NumberFormatTypes.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt.get("format"))
                .ifSuccess(format -> score.numberFormat = format);
        }

        return score;
    }
}
