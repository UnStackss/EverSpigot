package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;

public record EntityEquipmentPredicate(
    Optional<ItemPredicate> head,
    Optional<ItemPredicate> chest,
    Optional<ItemPredicate> legs,
    Optional<ItemPredicate> feet,
    Optional<ItemPredicate> body,
    Optional<ItemPredicate> mainhand,
    Optional<ItemPredicate> offhand
) {
    public static final Codec<EntityEquipmentPredicate> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    ItemPredicate.CODEC.optionalFieldOf("head").forGetter(EntityEquipmentPredicate::head),
                    ItemPredicate.CODEC.optionalFieldOf("chest").forGetter(EntityEquipmentPredicate::chest),
                    ItemPredicate.CODEC.optionalFieldOf("legs").forGetter(EntityEquipmentPredicate::legs),
                    ItemPredicate.CODEC.optionalFieldOf("feet").forGetter(EntityEquipmentPredicate::feet),
                    ItemPredicate.CODEC.optionalFieldOf("body").forGetter(EntityEquipmentPredicate::body),
                    ItemPredicate.CODEC.optionalFieldOf("mainhand").forGetter(EntityEquipmentPredicate::mainhand),
                    ItemPredicate.CODEC.optionalFieldOf("offhand").forGetter(EntityEquipmentPredicate::offhand)
                )
                .apply(instance, EntityEquipmentPredicate::new)
    );

    public static EntityEquipmentPredicate captainPredicate(HolderGetter<BannerPattern> bannerPatternLookup) {
        return EntityEquipmentPredicate.Builder.equipment()
            .head(
                ItemPredicate.Builder.item()
                    .of(Items.WHITE_BANNER)
                    .hasComponents(DataComponentPredicate.allOf(Raid.getLeaderBannerInstance(bannerPatternLookup).getComponents()))
            )
            .build();
    }

    public boolean matches(@Nullable Entity entity) {
        return entity instanceof LivingEntity livingEntity
            && (!this.head.isPresent() || this.head.get().test(livingEntity.getItemBySlot(EquipmentSlot.HEAD)))
            && (!this.chest.isPresent() || this.chest.get().test(livingEntity.getItemBySlot(EquipmentSlot.CHEST)))
            && (!this.legs.isPresent() || this.legs.get().test(livingEntity.getItemBySlot(EquipmentSlot.LEGS)))
            && (!this.feet.isPresent() || this.feet.get().test(livingEntity.getItemBySlot(EquipmentSlot.FEET)))
            && (!this.body.isPresent() || this.body.get().test(livingEntity.getItemBySlot(EquipmentSlot.BODY)))
            && (!this.mainhand.isPresent() || this.mainhand.get().test(livingEntity.getItemBySlot(EquipmentSlot.MAINHAND)))
            && (!this.offhand.isPresent() || this.offhand.get().test(livingEntity.getItemBySlot(EquipmentSlot.OFFHAND)));
    }

    public static class Builder {
        private Optional<ItemPredicate> head = Optional.empty();
        private Optional<ItemPredicate> chest = Optional.empty();
        private Optional<ItemPredicate> legs = Optional.empty();
        private Optional<ItemPredicate> feet = Optional.empty();
        private Optional<ItemPredicate> body = Optional.empty();
        private Optional<ItemPredicate> mainhand = Optional.empty();
        private Optional<ItemPredicate> offhand = Optional.empty();

        public static EntityEquipmentPredicate.Builder equipment() {
            return new EntityEquipmentPredicate.Builder();
        }

        public EntityEquipmentPredicate.Builder head(ItemPredicate.Builder item) {
            this.head = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder chest(ItemPredicate.Builder item) {
            this.chest = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder legs(ItemPredicate.Builder item) {
            this.legs = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder feet(ItemPredicate.Builder item) {
            this.feet = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder body(ItemPredicate.Builder item) {
            this.body = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder mainhand(ItemPredicate.Builder item) {
            this.mainhand = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate.Builder offhand(ItemPredicate.Builder item) {
            this.offhand = Optional.of(item.build());
            return this;
        }

        public EntityEquipmentPredicate build() {
            return new EntityEquipmentPredicate(this.head, this.chest, this.legs, this.feet, this.body, this.mainhand, this.offhand);
        }
    }
}
