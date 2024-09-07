package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

public class Crackiness {
    public static final Crackiness GOLEM = new Crackiness(0.75F, 0.5F, 0.25F);
    public static final Crackiness WOLF_ARMOR = new Crackiness(0.95F, 0.69F, 0.32F);
    private final float fractionLow;
    private final float fractionMedium;
    private final float fractionHigh;

    private Crackiness(float lowCrackThreshold, float mediumCrackThreshold, float highCrackThreshold) {
        this.fractionLow = lowCrackThreshold;
        this.fractionMedium = mediumCrackThreshold;
        this.fractionHigh = highCrackThreshold;
    }

    public Crackiness.Level byFraction(float health) {
        if (health < this.fractionHigh) {
            return Crackiness.Level.HIGH;
        } else if (health < this.fractionMedium) {
            return Crackiness.Level.MEDIUM;
        } else {
            return health < this.fractionLow ? Crackiness.Level.LOW : Crackiness.Level.NONE;
        }
    }

    public Crackiness.Level byDamage(ItemStack stack) {
        return !stack.isDamageableItem() ? Crackiness.Level.NONE : this.byDamage(stack.getDamageValue(), stack.getMaxDamage());
    }

    public Crackiness.Level byDamage(int currentDamage, int maxDamage) {
        return this.byFraction((float)(maxDamage - currentDamage) / (float)maxDamage);
    }

    public static enum Level {
        NONE,
        LOW,
        MEDIUM,
        HIGH;
    }
}
