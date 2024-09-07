package net.minecraft.world.item;

import java.util.List;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class BottleItem extends Item {
    public BottleItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        List<AreaEffectCloud> list = world.getEntitiesOfClass(
            AreaEffectCloud.class, user.getBoundingBox().inflate(2.0), entity -> entity != null && entity.isAlive() && entity.getOwner() instanceof EnderDragon
        );
        ItemStack itemStack = user.getItemInHand(hand);
        if (!list.isEmpty()) {
            AreaEffectCloud areaEffectCloud = list.get(0);
            areaEffectCloud.setRadius(areaEffectCloud.getRadius() - 0.5F);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.NEUTRAL, 1.0F, 1.0F);
            world.gameEvent(user, GameEvent.FLUID_PICKUP, user.position());
            if (user instanceof ServerPlayer serverPlayer) {
                CriteriaTriggers.PLAYER_INTERACTED_WITH_ENTITY.trigger(serverPlayer, itemStack, areaEffectCloud);
            }

            return InteractionResultHolder.sidedSuccess(this.turnBottleIntoItem(itemStack, user, new ItemStack(Items.DRAGON_BREATH)), world.isClientSide());
        } else {
            BlockHitResult blockHitResult = getPlayerPOVHitResult(world, user, ClipContext.Fluid.SOURCE_ONLY);
            if (blockHitResult.getType() == HitResult.Type.MISS) {
                return InteractionResultHolder.pass(itemStack);
            } else {
                if (blockHitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos blockPos = blockHitResult.getBlockPos();
                    if (!world.mayInteract(user, blockPos)) {
                        return InteractionResultHolder.pass(itemStack);
                    }

                    if (world.getFluidState(blockPos).is(FluidTags.WATER)) {
                        world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 1.0F);
                        world.gameEvent(user, GameEvent.FLUID_PICKUP, blockPos);
                        return InteractionResultHolder.sidedSuccess(
                            this.turnBottleIntoItem(itemStack, user, PotionContents.createItemStack(Items.POTION, Potions.WATER)), world.isClientSide()
                        );
                    }
                }

                return InteractionResultHolder.pass(itemStack);
            }
        }
    }

    protected ItemStack turnBottleIntoItem(ItemStack stack, Player player, ItemStack outputStack) {
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.createFilledResult(stack, player, outputStack);
    }
}
