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
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;

public class SnowballItem extends Item implements ProjectileItem {

    public SnowballItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        // CraftBukkit - moved down
        // world.playSound((EntityHuman) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!world.isClientSide) {
            Snowball entitysnowball = new Snowball(world, user);

            entitysnowball.setItem(itemstack);
            entitysnowball.shootFromRotation(user, user.getXRot(), user.getYRot(), 0.0F, 1.5F, 1.0F);
            if (world.addFreshEntity(entitysnowball)) {
                itemstack.consume(1, user);

                world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            } else if (user instanceof net.minecraft.server.level.ServerPlayer) {
                ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
            }
        }
        // CraftBukkit end

        user.awardStat(Stats.ITEM_USED.get(this));
        // itemstack.consume(1, entityhuman); // CraftBukkit - moved up
        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        Snowball entitysnowball = new Snowball(world, pos.x(), pos.y(), pos.z());

        entitysnowball.setItem(stack);
        return entitysnowball;
    }
}
