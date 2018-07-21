package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownExperienceBottle;
import net.minecraft.world.level.Level;

public class ExperienceBottleItem extends Item implements ProjectileItem {
    public ExperienceBottleItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        // Paper - PlayerLaunchProjectileEvent; moved down
        if (!world.isClientSide) {
            ThrownExperienceBottle thrownExperienceBottle = new ThrownExperienceBottle(world, user);
            thrownExperienceBottle.setItem(itemStack);
            thrownExperienceBottle.shootFromRotation(user, user.getXRot(), user.getYRot(), -20.0F, 0.7F, 1.0F);
            // Paper start - PlayerLaunchProjectileEvent
            com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent event = new com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent((org.bukkit.entity.Player) user.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack), (org.bukkit.entity.Projectile) thrownExperienceBottle.getBukkitEntity());
            if (event.callEvent() && world.addFreshEntity(thrownExperienceBottle)) {
                if (event.shouldConsume()) {
                    itemStack.consume(1, user);
                } else if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                }

                world.playSound(
                    null,
                    user.getX(),
                    user.getY(),
                    user.getZ(),
                    SoundEvents.EXPERIENCE_BOTTLE_THROW,
                    SoundSource.NEUTRAL,
                    0.5F,
                    0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)
                );
                user.awardStat(Stats.ITEM_USED.get(this));
            } else {
                if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemStack);
            }
            // Paper end - PlayerLaunchProjectileEvent
        }

        /* // Paper start - PlayerLaunchProjectileEvent; moved up
        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);
        */ // Paper end - PlayerLaunchProjectileEvent
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        ThrownExperienceBottle thrownExperienceBottle = new ThrownExperienceBottle(world, pos.x(), pos.y(), pos.z());
        thrownExperienceBottle.setItem(stack);
        return thrownExperienceBottle;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
