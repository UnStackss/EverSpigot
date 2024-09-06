package net.minecraft.world.item;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class MilkBucketItem extends Item {

    private static final int DRINK_DURATION = 32;

    public MilkBucketItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        if (user instanceof ServerPlayer entityplayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(entityplayer, stack);
            entityplayer.awardStat(Stats.ITEM_USED.get(this));
        }

        if (!world.isClientSide) {
            user.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.MILK); // CraftBukkit
        }

        if (user instanceof Player entityhuman) {
            return ItemUtils.createFilledResult(stack, entityhuman, new ItemStack(Items.BUCKET), false);
        } else {
            stack.consume(1, user);
            return stack;
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(world, user, hand);
    }
}
