package net.minecraft.world.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.entity.Player;
import org.bukkit.event.hanging.HangingPlaceEvent;
// CraftBukkit end

public class HangingEntityItem extends Item {

    private static final Component TOOLTIP_RANDOM_VARIANT = Component.translatable("painting.random").withStyle(ChatFormatting.GRAY);
    private final EntityType<? extends HangingEntity> type;

    public HangingEntityItem(EntityType<? extends HangingEntity> type, Item.Properties settings) {
        super(settings);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos blockposition = context.getClickedPos();
        Direction enumdirection = context.getClickedFace();
        BlockPos blockposition1 = blockposition.relative(enumdirection);
        net.minecraft.world.entity.player.Player entityhuman = context.getPlayer();
        ItemStack itemstack = context.getItemInHand();

        if (entityhuman != null && !this.mayPlace(entityhuman, enumdirection, itemstack, blockposition1)) {
            return InteractionResult.FAIL;
        } else {
            Level world = context.getLevel();
            Object object;

            if (this.type == EntityType.PAINTING) {
                Optional<Painting> optional = Painting.create(world, blockposition1, enumdirection);

                if (optional.isEmpty()) {
                    return InteractionResult.CONSUME;
                }

                object = (HangingEntity) optional.get();
            } else if (this.type == EntityType.ITEM_FRAME) {
                object = new ItemFrame(world, blockposition1, enumdirection);
            } else {
                if (this.type != EntityType.GLOW_ITEM_FRAME) {
                    return InteractionResult.sidedSuccess(world.isClientSide);
                }

                object = new GlowItemFrame(world, blockposition1, enumdirection);
            }

            CustomData customdata = (CustomData) itemstack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);

            if (!customdata.isEmpty()) {
                EntityType.updateCustomEntityTag(world, entityhuman, (Entity) object, customdata);
            }

            if (((HangingEntity) object).survives()) {
                if (!world.isClientSide) {
                    // CraftBukkit start - fire HangingPlaceEvent
                    Player who = (context.getPlayer() == null) ? null : (Player) context.getPlayer().getBukkitEntity();
                    org.bukkit.block.Block blockClicked = world.getWorld().getBlockAt(blockposition.getX(), blockposition.getY(), blockposition.getZ());
                    org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(enumdirection);
                    org.bukkit.inventory.EquipmentSlot hand = org.bukkit.craftbukkit.CraftEquipmentSlot.getHand(context.getHand());

                    HangingPlaceEvent event = new HangingPlaceEvent((org.bukkit.entity.Hanging) ((HangingEntity) object).getBukkitEntity(), who, blockClicked, blockFace, hand, org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemstack));
                    world.getCraftServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return InteractionResult.FAIL;
                    }
                    // CraftBukkit end
                    ((HangingEntity) object).playPlacementSound();
                    world.gameEvent((Entity) entityhuman, (Holder) GameEvent.ENTITY_PLACE, ((HangingEntity) object).position());
                    world.addFreshEntity((Entity) object);
                }

                itemstack.shrink(1);
                return InteractionResult.sidedSuccess(world.isClientSide);
            } else {
                return InteractionResult.CONSUME;
            }
        }
    }

    protected boolean mayPlace(net.minecraft.world.entity.player.Player player, Direction side, ItemStack stack, BlockPos pos) {
        return !side.getAxis().isVertical() && player.mayUseItemAt(pos, side, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, tooltip, type);
        HolderLookup.Provider holderlookup_a = context.registries();

        if (holderlookup_a != null && this.type == EntityType.PAINTING) {
            CustomData customdata = (CustomData) stack.getOrDefault(DataComponents.ENTITY_DATA, CustomData.EMPTY);

            if (!customdata.isEmpty()) {
                customdata.read(holderlookup_a.createSerializationContext(NbtOps.INSTANCE), Painting.VARIANT_MAP_CODEC).result().ifPresentOrElse((holder) -> {
                    holder.unwrapKey().ifPresent((resourcekey) -> {
                        tooltip.add(Component.translatable(resourcekey.location().toLanguageKey("painting", "title")).withStyle(ChatFormatting.YELLOW));
                        tooltip.add(Component.translatable(resourcekey.location().toLanguageKey("painting", "author")).withStyle(ChatFormatting.GRAY));
                    });
                    tooltip.add(Component.translatable("painting.dimensions", ((PaintingVariant) holder.value()).width(), ((PaintingVariant) holder.value()).height()));
                }, () -> {
                    tooltip.add(HangingEntityItem.TOOLTIP_RANDOM_VARIANT);
                });
            } else if (type.isCreative()) {
                tooltip.add(HangingEntityItem.TOOLTIP_RANDOM_VARIANT);
            }
        }

    }
}
