package net.minecraft.world.item;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class AnimalArmorItem extends ArmorItem {
    private final ResourceLocation textureLocation;
    @Nullable
    private final ResourceLocation overlayTextureLocation;
    private final AnimalArmorItem.BodyType bodyType;

    public AnimalArmorItem(Holder<ArmorMaterial> material, AnimalArmorItem.BodyType type, boolean hasOverlay, Item.Properties settings) {
        super(material, ArmorItem.Type.BODY, settings);
        this.bodyType = type;
        ResourceLocation resourceLocation = type.textureLocator.apply(material.unwrapKey().orElseThrow().location());
        this.textureLocation = resourceLocation.withSuffix(".png");
        if (hasOverlay) {
            this.overlayTextureLocation = resourceLocation.withSuffix("_overlay.png");
        } else {
            this.overlayTextureLocation = null;
        }
    }

    public ResourceLocation getTexture() {
        return this.textureLocation;
    }

    @Nullable
    public ResourceLocation getOverlayTexture() {
        return this.overlayTextureLocation;
    }

    public AnimalArmorItem.BodyType getBodyType() {
        return this.bodyType;
    }

    @Override
    public SoundEvent getBreakingSound() {
        return this.bodyType.breakingSound;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    public static enum BodyType {
        EQUESTRIAN(id -> id.withPath(path -> "textures/entity/horse/armor/horse_armor_" + path), SoundEvents.ITEM_BREAK),
        CANINE(id -> id.withPath("textures/entity/wolf/wolf_armor"), SoundEvents.WOLF_ARMOR_BREAK);

        final Function<ResourceLocation, ResourceLocation> textureLocator;
        final SoundEvent breakingSound;

        private BodyType(final Function<ResourceLocation, ResourceLocation> textureIdFunction, final SoundEvent breakSound) {
            this.textureLocator = textureIdFunction;
            this.breakingSound = breakSound;
        }
    }
}
