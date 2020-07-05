package net.minecraft.world.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

public class NameTagItem extends Item {
    public NameTagItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand) {
        Component component = stack.get(DataComponents.CUSTOM_NAME);
        if (component != null && !(entity instanceof Player)) {
            if (!user.level().isClientSide && entity.isAlive()) {
                // Paper start - Add PlayerNameEntityEvent
                io.papermc.paper.event.player.PlayerNameEntityEvent event = new io.papermc.paper.event.player.PlayerNameEntityEvent(((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity(), entity.getBukkitLivingEntity(), io.papermc.paper.adventure.PaperAdventure.asAdventure(stack.getHoverName()), true);
                if (!event.callEvent()) return InteractionResult.PASS;
                LivingEntity newEntity = ((org.bukkit.craftbukkit.entity.CraftLivingEntity) event.getEntity()).getHandle();
                newEntity.setCustomName(event.getName() != null ? io.papermc.paper.adventure.PaperAdventure.asVanilla(event.getName()) : null);
                if (event.isPersistent() && newEntity instanceof Mob mob) {
                // Paper end - Add PlayerNameEntityEvent
                    mob.setPersistenceRequired();
                }

                stack.shrink(1);
            }

            return InteractionResult.sidedSuccess(user.level().isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }
}
