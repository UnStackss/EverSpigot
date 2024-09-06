package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.CraftEquipmentSlot;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class LeadItem extends Item {

    public LeadItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos blockposition = context.getClickedPos();
        BlockState iblockdata = world.getBlockState(blockposition);

        if (iblockdata.is(BlockTags.FENCES)) {
            Player entityhuman = context.getPlayer();

            if (!world.isClientSide && entityhuman != null) {
                LeadItem.bindPlayerMobs(entityhuman, world, blockposition, context.getHand()); // CraftBukkit - Pass hand
            }

            return InteractionResult.sidedSuccess(world.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static InteractionResult bindPlayerMobs(Player entityhuman, Level world, BlockPos blockposition, net.minecraft.world.InteractionHand enumhand) { // CraftBukkit - Add EnumHand
        LeashFenceKnotEntity entityleash = null;
        List<Leashable> list = LeadItem.leashableInArea(world, blockposition, (leashable) -> {
            return leashable.getLeashHolder() == entityhuman;
        });

        Leashable leashable;

        for (Iterator iterator = list.iterator(); iterator.hasNext();) { // CraftBukkit - handle setLeashedTo at end of loop
            leashable = (Leashable) iterator.next();
            if (entityleash == null) {
                entityleash = LeashFenceKnotEntity.getOrCreateKnot(world, blockposition);

                // CraftBukkit start - fire HangingPlaceEvent
                org.bukkit.inventory.EquipmentSlot hand = CraftEquipmentSlot.getHand(enumhand);
                HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) entityleash.getBukkitEntity(), entityhuman != null ? (org.bukkit.entity.Player) entityhuman.getBukkitEntity() : null, CraftBlock.at(world, blockposition), org.bukkit.block.BlockFace.SELF, hand);
                world.getCraftServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    entityleash.discard(null); // CraftBukkit - add Bukkit remove cause
                    return InteractionResult.PASS;
                }
                // CraftBukkit end
                entityleash.playPlacementSound();
            }

            // CraftBukkit start
            if (leashable instanceof Entity leashed) {
                if (org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerLeashEntityEvent(leashed, entityleash, entityhuman, enumhand).isCancelled()) {
                    iterator.remove();
                    continue;
                }
            }

            leashable.setLeashedTo(entityleash, true);
            // CraftBukkit end
        }

        if (!list.isEmpty()) {
            world.gameEvent((Holder) GameEvent.BLOCK_ATTACH, blockposition, GameEvent.Context.of((Entity) entityhuman));
            return InteractionResult.SUCCESS;
        } else {
            // CraftBukkit start- remove leash if we do not leash any entity because of the cancelled event
            if (entityleash != null) {
                entityleash.discard(null);
            }
            // CraftBukkit end
            return InteractionResult.PASS;
        }
    }

    // CraftBukkit start
    public static InteractionResult bindPlayerMobs(Player player, Level world, BlockPos pos) {
        return LeadItem.bindPlayerMobs(player, world, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    // CraftBukkit end

    public static List<Leashable> leashableInArea(Level world, BlockPos pos, Predicate<Leashable> predicate) {
        double d0 = 7.0D;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        AABB axisalignedbb = new AABB((double) i - 7.0D, (double) j - 7.0D, (double) k - 7.0D, (double) i + 7.0D, (double) j + 7.0D, (double) k + 7.0D);
        Stream stream = world.getEntitiesOfClass(Entity.class, axisalignedbb, (entity) -> {
            boolean flag;

            if (entity instanceof Leashable leashable) {
                if (predicate.test(leashable)) {
                    flag = true;
                    return flag;
                }
            }

            flag = false;
            return flag;
        }).stream();

        Objects.requireNonNull(Leashable.class);
        return stream.map(Leashable.class::cast).toList();
    }
}
