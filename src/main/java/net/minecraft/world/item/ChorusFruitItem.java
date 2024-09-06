package net.minecraft.world.item;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class ChorusFruitItem extends Item {

    public ChorusFruitItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        ItemStack itemstack1 = super.finishUsingItem(stack, world, user);

        if (!world.isClientSide) {
            for (int i = 0; i < 16; ++i) {
                double d0 = user.getX() + (user.getRandom().nextDouble() - 0.5D) * 16.0D;
                double d1 = Mth.clamp(user.getY() + (double) (user.getRandom().nextInt(16) - 8), (double) world.getMinBuildHeight(), (double) (world.getMinBuildHeight() + ((ServerLevel) world).getLogicalHeight() - 1));
                double d2 = user.getZ() + (user.getRandom().nextDouble() - 0.5D) * 16.0D;

                if (user.isPassenger()) {
                    user.stopRiding();
                }

                Vec3 vec3d = user.position();

                // CraftBukkit start - handle canceled status of teleport event
                java.util.Optional<Boolean> status = user.randomTeleport(d0, d1, d2, true, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT);

                if (!status.isPresent()) {
                    // teleport event was canceled, no more tries
                    break;
                }

                if (status.get()) {
                    // CraftBukkit end
                    world.gameEvent((Holder) GameEvent.TELEPORT, vec3d, GameEvent.Context.of((Entity) user));
                    SoundEvent soundeffect;
                    SoundSource soundcategory;

                    if (user instanceof Fox) {
                        soundeffect = SoundEvents.FOX_TELEPORT;
                        soundcategory = SoundSource.NEUTRAL;
                    } else {
                        soundeffect = SoundEvents.CHORUS_FRUIT_TELEPORT;
                        soundcategory = SoundSource.PLAYERS;
                    }

                    world.playSound((Player) null, user.getX(), user.getY(), user.getZ(), soundeffect, soundcategory);
                    user.resetFallDistance();
                    break;
                }
            }

            if (user instanceof Player) {
                Player entityhuman = (Player) user;

                entityhuman.resetCurrentImpulseContext();
                entityhuman.getCooldowns().addCooldown(this, 20);
            }
        }

        return itemstack1;
    }
}
