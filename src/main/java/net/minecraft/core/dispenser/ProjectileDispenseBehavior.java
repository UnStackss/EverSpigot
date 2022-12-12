package net.minecraft.core.dispenser;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
// CraftBukkit start
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseEvent;
// CraftBukkit end

public class ProjectileDispenseBehavior extends DefaultDispenseItemBehavior {

    private final ProjectileItem projectileItem;
    private final ProjectileItem.DispenseConfig dispenseConfig;

    public ProjectileDispenseBehavior(Item item) {
        if (item instanceof ProjectileItem projectileitem) {
            this.projectileItem = projectileitem;
            this.dispenseConfig = projectileitem.createDispenseConfig();
        } else {
            String s = String.valueOf(item);

            throw new IllegalArgumentException(s + " not instance of " + ProjectileItem.class.getSimpleName());
        }
    }

    @Override
    public ItemStack execute(BlockSource pointer, ItemStack stack) {
        ServerLevel worldserver = pointer.level();
        Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
        Position iposition = this.dispenseConfig.positionFunction().getDispensePosition(pointer, enumdirection);
        Projectile iprojectile = this.projectileItem.asProjectile(worldserver, iposition, stack, enumdirection);

        // CraftBukkit start
        // this.projectileItem.shoot(iprojectile, (double) enumdirection.getStepX(), (double) enumdirection.getStepY(), (double) enumdirection.getStepZ(), this.dispenseConfig.power(), this.dispenseConfig.uncertainty());
        ItemStack itemstack1 = stack.copyWithCount(1); // Paper
        org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
        CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

        BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) enumdirection.getStepX(), (double) enumdirection.getStepY(), (double) enumdirection.getStepZ()));
        if (!DispenserBlock.eventFired) {
            worldserver.getCraftServer().getPluginManager().callEvent(event);
        }

        if (event.isCancelled()) {
            // stack.grow(1); // Paper - shrink below
            return stack;
        }

        boolean shrink = true; // Paper
        if (!event.getItem().equals(craftItem)) {
            shrink = false; // Paper - shrink below
            // Chain to handler for new item
            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                idispensebehavior.dispense(pointer, eventStack);
                return stack;
            }
        }

        this.projectileItem.shoot(iprojectile, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), this.dispenseConfig.power(), this.dispenseConfig.uncertainty());
        ((Entity) iprojectile).projectileSource = new org.bukkit.craftbukkit.projectiles.CraftBlockProjectileSource(pointer.blockEntity());
        // CraftBukkit end
        worldserver.addFreshEntity(iprojectile);
        if (shrink) stack.shrink(1); // Paper - actually handle here
        return stack;
    }

    @Override
    protected void playSound(BlockSource pointer) {
        pointer.level().levelEvent(this.dispenseConfig.overrideDispenseEvent().orElse(1002), pointer.pos(), 0);
    }
}
