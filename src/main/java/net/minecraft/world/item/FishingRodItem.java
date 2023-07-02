package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.craftbukkit.CraftEquipmentSlot;
// CraftBukkit end

public class FishingRodItem extends Item {

    public FishingRodItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemstack = user.getItemInHand(hand);

        if (user.fishing != null) {
            if (!world.isClientSide) {
                int i = user.fishing.retrieve(hand, itemstack); // Paper - Add hand parameter to PlayerFishEvent

                itemstack.hurtAndBreak(i, user, LivingEntity.getSlotForHand(hand));
            }

            world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            user.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        } else {
            // world.playSound((EntityHuman) null, entityhuman.getX(), entityhuman.getY(), entityhuman.getZ(), SoundEffects.FISHING_BOBBER_THROW, SoundCategory.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;
                int j = (int) (EnchantmentHelper.getFishingTimeReduction(worldserver, itemstack, user) * 20.0F);
                int k = EnchantmentHelper.getFishingLuckBonus(worldserver, itemstack, user);

                // CraftBukkit start
                FishingHook entityfishinghook = new FishingHook(user, world, k, j);
                PlayerFishEvent playerFishEvent = new PlayerFishEvent((org.bukkit.entity.Player) user.getBukkitEntity(), null, (org.bukkit.entity.FishHook) entityfishinghook.getBukkitEntity(), CraftEquipmentSlot.getHand(hand), PlayerFishEvent.State.FISHING);
                world.getCraftServer().getPluginManager().callEvent(playerFishEvent);

                if (playerFishEvent.isCancelled()) {
                    user.fishing = null;
                    return InteractionResultHolder.pass(itemstack);
                }
                world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
                world.addFreshEntity(entityfishinghook);
                // CraftBukkit end
            }

            user.awardStat(Stats.ITEM_USED.get(this));
            user.gameEvent(GameEvent.ITEM_INTERACT_START);
        }

        return InteractionResultHolder.sidedSuccess(itemstack, world.isClientSide());
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }
}
