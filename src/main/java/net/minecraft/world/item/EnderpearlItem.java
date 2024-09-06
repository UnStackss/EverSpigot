package net.minecraft.world.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;

public class EnderpearlItem extends Item {

    public EnderpearlItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        // CraftBukkit start - change order
        if (!world.isClientSide) {
            ThrownEnderpearl entityenderpearl = new ThrownEnderpearl(world, user);

            entityenderpearl.setItem(itemstack);
            entityenderpearl.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
            if (!world.addFreshEntity(entityenderpearl)) {
                if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemstack);
            }
        }

        world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        user.getCooldowns().addCooldown(this, 20);
        // CraftBukkit end

        user.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, user);
        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }
}
