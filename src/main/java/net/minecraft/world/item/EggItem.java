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
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.Level;

public class EggItem extends Item implements ProjectileItem {

    public EggItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        // world.playSound((EntityHuman) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.EGG_THROW, SoundCategory.PLAYERS, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F)); // CraftBukkit - moved down
        if (!world.isClientSide) {
            ThrownEgg entityegg = new ThrownEgg(world, user);

            entityegg.setItem(itemstack);
            entityegg.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
            // CraftBukkit start
            if (!world.addFreshEntity(entityegg)) {
                if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                }
                return InteractionResultHolder.fail(itemstack);
            }
            // CraftBukkit end
        }
        world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

        user.awardStat(Stats.ITEM_USED.get(this));
        itemstack.consume(1, user);
        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        ThrownEgg entityegg = new ThrownEgg(world, pos.x(), pos.y(), pos.z());

        entityegg.setItem(stack);
        return entityegg;
    }
}
