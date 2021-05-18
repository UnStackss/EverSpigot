package net.minecraft.world.entity;

import net.minecraft.sounds.SoundSource;

public interface Shearable {
    default void shear(SoundSource soundCategory, java.util.List<net.minecraft.world.item.ItemStack> drops) { this.shear(soundCategory); } // Paper - Add drops to shear events
    void shear(SoundSource shearedSoundCategory);

    boolean readyForShearing();
    // Paper start - custom shear drops; ensure all implementing entities override this
    default java.util.List<net.minecraft.world.item.ItemStack> generateDefaultDrops() {
        return java.util.Collections.emptyList();
    }
    // Paper end - custom shear drops
}
