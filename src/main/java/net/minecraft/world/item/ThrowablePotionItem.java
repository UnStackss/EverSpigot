package net.minecraft.world.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.Level;

public class ThrowablePotionItem extends PotionItem implements ProjectileItem {
    public ThrowablePotionItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        if (!world.isClientSide) {
            ThrownPotion thrownPotion = new ThrownPotion(world, user);
            thrownPotion.setItem(itemStack);
            thrownPotion.shootFromRotation(user, user.getXRot(), user.getYRot(), -20.0F, 0.5F, 1.0F);
            world.addFreshEntity(thrownPotion);
        }

        user.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, user);
        return InteractionResultHolder.sidedSuccess(itemStack, world.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        ThrownPotion thrownPotion = new ThrownPotion(world, pos.x(), pos.y(), pos.z());
        thrownPotion.setItem(stack);
        return thrownPotion;
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .uncertainty(ProjectileItem.DispenseConfig.DEFAULT.uncertainty() * 0.5F)
            .power(ProjectileItem.DispenseConfig.DEFAULT.power() * 1.25F)
            .build();
    }
}
