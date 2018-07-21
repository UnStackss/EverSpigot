package net.minecraft.world.item;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class FireworkRocketItem extends Item implements ProjectileItem {
    public static final byte[] CRAFTABLE_DURATIONS = new byte[]{1, 2, 3};
    public static final double ROCKET_PLACEMENT_OFFSET = 0.15;

    public FireworkRocketItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            ItemStack itemStack = context.getItemInHand();
            Vec3 vec3 = context.getClickLocation();
            Direction direction = context.getClickedFace();
            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(
                level,
                context.getPlayer(),
                vec3.x + (double)direction.getStepX() * 0.15,
                vec3.y + (double)direction.getStepY() * 0.15,
                vec3.z + (double)direction.getStepZ() * 0.15,
                itemStack
            );
            fireworkRocketEntity.spawningEntity = context.getPlayer() == null ? null : context.getPlayer().getUUID(); // Paper
            level.addFreshEntity(fireworkRocketEntity);
            itemStack.shrink(1);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        if (user.isFallFlying()) {
            ItemStack itemStack = user.getItemInHand(hand);
            if (!world.isClientSide) {
                FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(world, itemStack, user);
                fireworkRocketEntity.spawningEntity = user.getUUID(); // Paper
                // Paper start - PlayerElytraBoostEvent
                com.destroystokyo.paper.event.player.PlayerElytraBoostEvent event = new com.destroystokyo.paper.event.player.PlayerElytraBoostEvent((org.bukkit.entity.Player) user.getBukkitEntity(), org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack), (org.bukkit.entity.Firework) fireworkRocketEntity.getBukkitEntity(), org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(hand));
                if (event.callEvent() && world.addFreshEntity(fireworkRocketEntity)) {
                    user.awardStat(Stats.ITEM_USED.get(this));
                    if (event.shouldConsume() && !user.hasInfiniteMaterials()) {
                        itemStack.shrink(1);
                    } else ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                } else if (user instanceof net.minecraft.server.level.ServerPlayer) {
                    ((net.minecraft.server.level.ServerPlayer) user).getBukkitEntity().updateInventory();
                    // Paper end - PlayerElytraBoostEvent
                }

                // user.awardStat(Stats.ITEM_USED.get(this)); // Paper - PlayerElytraBoostEvent; move up
            }

            return InteractionResultHolder.sidedSuccess(user.getItemInHand(hand), world.isClientSide());
        } else {
            return InteractionResultHolder.pass(user.getItemInHand(hand));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (fireworks != null) {
            fireworks.addToTooltip(context, tooltip::add, type);
        }
    }

    @Override
    public Projectile asProjectile(Level world, Position pos, ItemStack stack, Direction direction) {
        return new FireworkRocketEntity(world, stack.copyWithCount(1), pos.x(), pos.y(), pos.z(), true);
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
            .positionFunction(FireworkRocketItem::getEntityPokingOutOfBlockPos)
            .uncertainty(1.0F)
            .power(0.5F)
            .overrideDispenseEvent(1004)
            .build();
    }

    private static Vec3 getEntityPokingOutOfBlockPos(BlockSource pointer, Direction facing) {
        return pointer.center()
            .add(
                (double)facing.getStepX() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getWidth() / 2.0),
                (double)facing.getStepY() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getHeight() / 2.0)
                    - (double)EntityType.FIREWORK_ROCKET.getHeight() / 2.0,
                (double)facing.getStepZ() * (0.5000099999997474 - (double)EntityType.FIREWORK_ROCKET.getWidth() / 2.0)
            );
    }
}
