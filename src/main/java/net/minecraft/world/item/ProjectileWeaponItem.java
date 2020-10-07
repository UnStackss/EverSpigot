package net.minecraft.world.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

public abstract class ProjectileWeaponItem extends Item {

    public static final Predicate<ItemStack> ARROW_ONLY = (itemstack) -> {
        return itemstack.is(ItemTags.ARROWS);
    };
    public static final Predicate<ItemStack> ARROW_OR_FIREWORK = ProjectileWeaponItem.ARROW_ONLY.or((itemstack) -> {
        return itemstack.is(Items.FIREWORK_ROCKET);
    });

    public ProjectileWeaponItem(Item.Properties settings) {
        super(settings);
    }

    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return this.getAllSupportedProjectiles();
    }

    public abstract Predicate<ItemStack> getAllSupportedProjectiles();

    public static ItemStack getHeldProjectile(LivingEntity entity, Predicate<ItemStack> predicate) {
        return predicate.test(entity.getItemInHand(InteractionHand.OFF_HAND)) ? entity.getItemInHand(InteractionHand.OFF_HAND) : (predicate.test(entity.getItemInHand(InteractionHand.MAIN_HAND)) ? entity.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY);
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    public abstract int getDefaultProjectileRange();

    protected void shoot(ServerLevel world, LivingEntity shooter, InteractionHand hand, ItemStack stack, List<ItemStack> projectiles, float speed, float divergence, boolean critical, @Nullable LivingEntity target) {
        float f2 = EnchantmentHelper.processProjectileSpread(world, stack, shooter, 0.0F);
        float f3 = projectiles.size() == 1 ? 0.0F : 2.0F * f2 / (float) (projectiles.size() - 1);
        float f4 = (float) ((projectiles.size() - 1) % 2) * f3 / 2.0F;
        float f5 = 1.0F;

        for (int i = 0; i < projectiles.size(); ++i) {
            ItemStack itemstack1 = (ItemStack) projectiles.get(i);

            if (!itemstack1.isEmpty()) {
                float f6 = f4 + f5 * (float) ((i + 1) / 2) * f3;

                f5 = -f5;
                Projectile iprojectile = this.createProjectile(world, shooter, stack, itemstack1, critical);

                this.shootProjectile(shooter, iprojectile, i, speed, divergence, f6, target);
                // CraftBukkit start
                org.bukkit.event.entity.EntityShootBowEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.callEntityShootBowEvent(shooter, stack, itemstack1, iprojectile, hand, speed, true);
                if (event.isCancelled()) {
                    event.getProjectile().remove();
                    return;
                }

                if (event.getProjectile() == iprojectile.getBukkitEntity()) {
                    if (!world.addFreshEntity(iprojectile)) {
                        if (shooter instanceof net.minecraft.server.level.ServerPlayer) {
                            ((net.minecraft.server.level.ServerPlayer) shooter).getBukkitEntity().updateInventory();
                        }
                        return;
                    }
                }
                // CraftBukkit end
                stack.hurtAndBreak(this.getDurabilityUse(itemstack1), shooter, LivingEntity.getSlotForHand(hand));
                if (stack.isEmpty()) {
                    break;
                }
            }
        }

    }

    protected int getDurabilityUse(ItemStack projectile) {
        return 1;
    }

    protected abstract void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float speed, float divergence, float yaw, @Nullable LivingEntity target);

    protected Projectile createProjectile(Level world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        Item item = projectileStack.getItem();
        ArrowItem itemarrow;

        if (item instanceof ArrowItem itemarrow1) {
            itemarrow = itemarrow1;
        } else {
            itemarrow = (ArrowItem) Items.ARROW;
        }

        ArrowItem itemarrow2 = itemarrow;
        AbstractArrow entityarrow = itemarrow2.createArrow(world, projectileStack, shooter, weaponStack);

        if (critical) {
            entityarrow.setCritArrow(true);
        }

        return entityarrow;
    }

    protected static List<ItemStack> draw(ItemStack stack, ItemStack projectileStack, LivingEntity shooter) {
        // Paper start
        return draw(stack, projectileStack, shooter, true);
    }
    protected static List<ItemStack> draw(ItemStack stack, ItemStack projectileStack, LivingEntity shooter, boolean consume) {
        // Paper end
        if (projectileStack.isEmpty()) {
            return List.of();
        } else {
            Level world = shooter.level();
            int i;

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                i = EnchantmentHelper.processProjectileCount(worldserver, stack, shooter, 1);
            } else {
                i = 1;
            }

            int j = i;
            List<ItemStack> list = new ArrayList(j);
            ItemStack itemstack2 = projectileStack.copy();

            for (int k = 0; k < j; ++k) {
                ItemStack itemstack3 = ProjectileWeaponItem.useAmmo(stack, k == 0 ? projectileStack : itemstack2, shooter, k > 0 || !consume); // Paper

                if (!itemstack3.isEmpty()) {
                    list.add(itemstack3);
                }
            }

            return list;
        }
    }

    protected static ItemStack useAmmo(ItemStack stack, ItemStack projectileStack, LivingEntity shooter, boolean multishot) {
        int i;
        label28:
        {
            if (!multishot && !shooter.hasInfiniteMaterials()) {
                Level world = shooter.level();

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    i = EnchantmentHelper.processAmmoUse(worldserver, stack, projectileStack, 1);
                    break label28;
                }
            }

            i = 0;
        }

        int j = i;

        if (j > projectileStack.getCount()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack2;

            if (j == 0) {
                itemstack2 = projectileStack.copyWithCount(1);
                itemstack2.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
                return itemstack2;
            } else {
                itemstack2 = projectileStack.split(j);
                if (projectileStack.isEmpty() && shooter instanceof Player) {
                    Player entityhuman = (Player) shooter;

                    entityhuman.getInventory().removeItem(projectileStack);
                }

                return itemstack2;
            }
        }
    }
}
