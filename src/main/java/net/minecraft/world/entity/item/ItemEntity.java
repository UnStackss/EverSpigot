package net.minecraft.world.entity.item;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
// CraftBukkit end

public class ItemEntity extends Entity implements TraceableEntity {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ItemEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final float FLOAT_HEIGHT = 0.1F;
    public static final float EYE_HEIGHT = 0.2125F;
    private static final int LIFETIME = 6000;
    private static final int INFINITE_PICKUP_DELAY = 32767;
    private static final int INFINITE_LIFETIME = -32768;
    public int age;
    public int pickupDelay;
    public int health;
    @Nullable
    public UUID thrower;
    @Nullable
    private Entity cachedThrower;
    @Nullable
    public UUID target;
    public final float bobOffs;
    private int lastTick = MinecraftServer.currentTick - 1; // CraftBukkit
    public boolean canMobPickup = true; // Paper - Item#canEntityPickup

    public ItemEntity(EntityType<? extends ItemEntity> type, Level world) {
        super(type, world);
        this.health = 5;
        this.bobOffs = this.random.nextFloat() * 3.1415927F * 2.0F;
        this.setYRot(this.random.nextFloat() * 360.0F);
    }

    public ItemEntity(Level world, double x, double y, double z, ItemStack stack) {
        this(world, x, y, z, stack, world.random.nextDouble() * 0.2D - 0.1D, 0.2D, world.random.nextDouble() * 0.2D - 0.1D);
    }

    public ItemEntity(Level world, double x, double y, double z, ItemStack stack, double velocityX, double velocityY, double velocityZ) {
        this(EntityType.ITEM, world);
        this.setPos(x, y, z);
        this.setDeltaMovement(velocityX, velocityY, velocityZ);
        this.setItem(stack);
    }

    private ItemEntity(ItemEntity entity) {
        super(entity.getType(), entity.level());
        this.health = 5;
        this.setItem(entity.getItem().copy());
        this.copyPosition(entity);
        this.age = entity.age;
        this.bobOffs = entity.bobOffs;
    }

    @Override
    public boolean dampensVibrations() {
        return this.getItem().is(ItemTags.DAMPENS_VIBRATIONS);
    }

    @Nullable
    @Override
    public Entity getOwner() {
        if (this.cachedThrower != null && !this.cachedThrower.isRemoved()) {
            return this.cachedThrower;
        } else {
            if (this.thrower != null) {
                Level world = this.level();

                if (world instanceof ServerLevel) {
                    ServerLevel worldserver = (ServerLevel) world;

                    this.cachedThrower = worldserver.getEntity(this.thrower);
                    return this.cachedThrower;
                }
            }

            return null;
        }
    }

    @Override
    public void restoreFrom(Entity original) {
        super.restoreFrom(original);
        if (original instanceof ItemEntity entityitem) {
            this.cachedThrower = entityitem.cachedThrower;
        }

    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ItemEntity.DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.04D;
    }

    @Override
    public void tick() {
        if (this.getItem().isEmpty()) {
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        } else {
            super.tick();
            // CraftBukkit start - Use wall time for pickup and despawn timers
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
            if (this.age != -32768) this.age += elapsedTicks;
            this.lastTick = MinecraftServer.currentTick;
            // CraftBukkit end

            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
            Vec3 vec3d = this.getDeltaMovement();

            if (this.isInWater() && this.getFluidHeight(FluidTags.WATER) > 0.10000000149011612D) {
                this.setUnderwaterMovement();
            } else if (this.isInLava() && this.getFluidHeight(FluidTags.LAVA) > 0.10000000149011612D) {
                this.setUnderLavaMovement();
            } else {
                this.applyGravity();
            }

            if (this.level().isClientSide) {
                this.noPhysics = false;
            } else {
                this.noPhysics = !this.level().noCollision(this, this.getBoundingBox().deflate(1.0E-7D));
                if (this.noPhysics) {
                    this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
                }
            }

            if (!this.onGround() || this.getDeltaMovement().horizontalDistanceSqr() > 9.999999747378752E-6D || (this.tickCount + this.getId()) % 4 == 0) {
                this.move(MoverType.SELF, this.getDeltaMovement());
                float f = 0.98F;

                if (this.onGround()) {
                    f = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.98F;
                }

                this.setDeltaMovement(this.getDeltaMovement().multiply((double) f, 0.98D, (double) f));
                if (this.onGround()) {
                    Vec3 vec3d1 = this.getDeltaMovement();

                    if (vec3d1.y < 0.0D) {
                        this.setDeltaMovement(vec3d1.multiply(1.0D, -0.5D, 1.0D));
                    }
                }
            }

            boolean flag = Mth.floor(this.xo) != Mth.floor(this.getX()) || Mth.floor(this.yo) != Mth.floor(this.getY()) || Mth.floor(this.zo) != Mth.floor(this.getZ());
            int i = flag ? 2 : 40;

            if (this.tickCount % i == 0 && !this.level().isClientSide && this.isMergable()) {
                this.mergeWithNeighbours();
            }

            /* CraftBukkit start - moved up
            if (this.age != -32768) {
                ++this.age;
            }
            // CraftBukkit end */

            this.hasImpulse |= this.updateInWaterStateAndDoFluidPushing();
            if (!this.level().isClientSide) {
                double d0 = this.getDeltaMovement().subtract(vec3d).lengthSqr();

                if (d0 > 0.01D) {
                    this.hasImpulse = true;
                }
            }

            if (!this.level().isClientSide && this.age >= this.level().spigotConfig.itemDespawnRate) { // Spigot
                // CraftBukkit start - fire ItemDespawnEvent
                if (CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                    this.age = 0;
                    return;
                }
                // CraftBukkit end
                this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
            }

        }
    }

    // Spigot start - copied from above
    @Override
    public void inactiveTick() {
        // CraftBukkit start - Use wall time for pickup and despawn timers
        int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
        if (this.pickupDelay != 32767) this.pickupDelay -= elapsedTicks;
        if (this.age != -32768) this.age += elapsedTicks;
        this.lastTick = MinecraftServer.currentTick;
        // CraftBukkit end

        if (!this.level().isClientSide && this.age >= this.level().spigotConfig.itemDespawnRate) { // Spigot
            // CraftBukkit start - fire ItemDespawnEvent
            if (org.bukkit.craftbukkit.event.CraftEventFactory.callItemDespawnEvent(this).isCancelled()) {
                this.age = 0;
                return;
            }
            // CraftBukkit end
            this.discard(EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - add Bukkit remove cause
        }
    }
    // Spigot end

    @Override
    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.999999F);
    }

    private void setUnderwaterMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.9900000095367432D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.9900000095367432D);
    }

    private void setUnderLavaMovement() {
        Vec3 vec3d = this.getDeltaMovement();

        this.setDeltaMovement(vec3d.x * 0.949999988079071D, vec3d.y + (double) (vec3d.y < 0.05999999865889549D ? 5.0E-4F : 0.0F), vec3d.z * 0.949999988079071D);
    }

    private void mergeWithNeighbours() {
        if (this.isMergable()) {
            // Spigot start
            double radius = this.level().spigotConfig.itemMerge;
            List<ItemEntity> list = this.level().getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(radius, radius - 0.5D, radius), (entityitem) -> {
                // Spigot end
                return entityitem != this && entityitem.isMergable();
            });
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                ItemEntity entityitem = (ItemEntity) iterator.next();

                if (entityitem.isMergable()) {
                    this.tryToMerge(entityitem);
                    if (this.isRemoved()) {
                        break;
                    }
                }
            }

        }
    }

    private boolean isMergable() {
        ItemStack itemstack = this.getItem();

        return this.isAlive() && this.pickupDelay != 32767 && this.age != -32768 && this.age < 6000 && itemstack.getCount() < itemstack.getMaxStackSize();
    }

    private void tryToMerge(ItemEntity other) {
        ItemStack itemstack = this.getItem();
        ItemStack itemstack1 = other.getItem();

        if (Objects.equals(this.target, other.target) && ItemEntity.areMergable(itemstack, itemstack1)) {
            if (true || itemstack1.getCount() < itemstack.getCount()) { // Spigot
                ItemEntity.merge(this, itemstack, other, itemstack1);
            } else {
                ItemEntity.merge(other, itemstack1, this, itemstack);
            }

        }
    }

    public static boolean areMergable(ItemStack stack1, ItemStack stack2) {
        return stack2.getCount() + stack1.getCount() > stack2.getMaxStackSize() ? false : ItemStack.isSameItemSameComponents(stack1, stack2);
    }

    public static ItemStack merge(ItemStack stack1, ItemStack stack2, int maxCount) {
        int j = Math.min(Math.min(stack1.getMaxStackSize(), maxCount) - stack1.getCount(), stack2.getCount());
        ItemStack itemstack2 = stack1.copyWithCount(stack1.getCount() + j);

        stack2.shrink(j);
        return itemstack2;
    }

    private static void merge(ItemEntity targetEntity, ItemStack stack1, ItemStack stack2) {
        ItemStack itemstack2 = ItemEntity.merge(stack1, stack2, 64);

        targetEntity.setItem(itemstack2);
    }

    private static void merge(ItemEntity targetEntity, ItemStack targetStack, ItemEntity sourceEntity, ItemStack sourceStack) {
        // CraftBukkit start
        if (!CraftEventFactory.callItemMergeEvent(sourceEntity, targetEntity)) {
            return;
        }
        // CraftBukkit end
        ItemEntity.merge(targetEntity, targetStack, sourceStack);
        targetEntity.pickupDelay = Math.max(targetEntity.pickupDelay, sourceEntity.pickupDelay);
        targetEntity.age = Math.min(targetEntity.age, sourceEntity.age);
        if (sourceStack.isEmpty()) {
            sourceEntity.discard(EntityRemoveEvent.Cause.MERGE); // CraftBukkit - add Bukkit remove cause);
        }

    }

    @Override
    public boolean fireImmune() {
        return this.getItem().has(DataComponents.FIRE_RESISTANT) || super.fireImmune();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else if (!this.getItem().isEmpty() && this.getItem().is(Items.NETHER_STAR) && source.is(DamageTypeTags.IS_EXPLOSION)) {
            return false;
        } else if (!this.getItem().canBeHurtBy(source)) {
            return false;
        } else if (this.level().isClientSide) {
            return true;
        } else {
            // CraftBukkit start
            if (CraftEventFactory.handleNonLivingEntityDamageEvent(this, source, amount)) {
                return false;
            }
            // CraftBukkit end
            this.markHurt();
            this.health = (int) ((float) this.health - amount);
            this.gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
            if (this.health <= 0) {
                this.getItem().onDestroyed(this);
                this.discard(EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
            }

            return true;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        nbt.putShort("Health", (short) this.health);
        nbt.putShort("Age", (short) this.age);
        nbt.putShort("PickupDelay", (short) this.pickupDelay);
        if (this.thrower != null) {
            nbt.putUUID("Thrower", this.thrower);
        }

        if (this.target != null) {
            nbt.putUUID("Owner", this.target);
        }

        if (!this.getItem().isEmpty()) {
            nbt.put("Item", this.getItem().save(this.registryAccess()));
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        this.health = nbt.getShort("Health");
        this.age = nbt.getShort("Age");
        if (nbt.contains("PickupDelay")) {
            this.pickupDelay = nbt.getShort("PickupDelay");
        }

        if (nbt.hasUUID("Owner")) {
            this.target = nbt.getUUID("Owner");
        }

        if (nbt.hasUUID("Thrower")) {
            this.thrower = nbt.getUUID("Thrower");
            this.cachedThrower = null;
        }

        if (nbt.contains("Item", 10)) {
            CompoundTag nbttagcompound1 = nbt.getCompound("Item");

            this.setItem((ItemStack) ItemStack.parse(this.registryAccess(), nbttagcompound1).orElse(ItemStack.EMPTY));
        } else {
            this.setItem(ItemStack.EMPTY);
        }

        if (this.getItem().isEmpty()) {
            this.discard(null); // CraftBukkit - add Bukkit remove cause
        }

    }

    @Override
    public void playerTouch(net.minecraft.world.entity.player.Player player) {
        if (!this.level().isClientSide) {
            ItemStack itemstack = this.getItem();
            Item item = itemstack.getItem();
            int i = itemstack.getCount();

            // CraftBukkit start - fire PlayerPickupItemEvent
            int canHold = player.getInventory().canHold(itemstack);
            int remaining = i - canHold;

            if (this.pickupDelay <= 0 && canHold > 0) {
                itemstack.setCount(canHold);
                // Call legacy event
                PlayerPickupItemEvent playerEvent = new PlayerPickupItemEvent((Player) player.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                playerEvent.setCancelled(!playerEvent.getPlayer().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(playerEvent);
                if (playerEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Call newer event afterwards
                EntityPickupItemEvent entityEvent = new EntityPickupItemEvent((Player) player.getBukkitEntity(), (org.bukkit.entity.Item) this.getBukkitEntity(), remaining);
                entityEvent.setCancelled(!entityEvent.getEntity().getCanPickupItems());
                this.level().getCraftServer().getPluginManager().callEvent(entityEvent);
                if (entityEvent.isCancelled()) {
                    itemstack.setCount(i); // SPIGOT-5294 - restore count
                    return;
                }

                // Update the ItemStack if it was changed in the event
                ItemStack current = this.getItem();
                if (!itemstack.equals(current)) {
                    itemstack = current;
                } else {
                    itemstack.setCount(canHold + remaining); // = i
                }

                // Possibly < 0; fix here so we do not have to modify code below
                this.pickupDelay = 0;
            } else if (this.pickupDelay == 0) {
                // ensure that the code below isn't triggered if canHold says we can't pick the items up
                this.pickupDelay = -1;
            }
            // CraftBukkit end

            if (this.pickupDelay == 0 && (this.target == null || this.target.equals(player.getUUID())) && player.getInventory().add(itemstack)) {
                player.take(this, i);
                if (itemstack.isEmpty()) {
                    this.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
                    itemstack.setCount(i);
                }

                player.awardStat(Stats.ITEM_PICKED_UP.get(item), i);
                player.onItemPickup(this);
            }

        }
    }

    @Override
    public Component getName() {
        Component ichatbasecomponent = this.getCustomName();

        return (Component) (ichatbasecomponent != null ? ichatbasecomponent : Component.translatable(this.getItem().getDescriptionId()));
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition teleportTarget) {
        Entity entity = super.changeDimension(teleportTarget);

        if (!this.level().isClientSide && entity instanceof ItemEntity entityitem) {
            entityitem.mergeWithNeighbours();
        }

        return entity;
    }

    public ItemStack getItem() {
        return (ItemStack) this.getEntityData().get(ItemEntity.DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.getEntityData().set(ItemEntity.DATA_ITEM, stack);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
        if (ItemEntity.DATA_ITEM.equals(data)) {
            this.getItem().setEntityRepresentation(this);
        }

    }

    public void setTarget(@Nullable UUID owner) {
        this.target = owner;
    }

    public void setThrower(Entity thrower) {
        this.thrower = thrower.getUUID();
        this.cachedThrower = thrower;
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickupDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickupDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickupDelay = 32767;
    }

    public void setPickUpDelay(int pickupDelay) {
        this.pickupDelay = pickupDelay;
    }

    public boolean hasPickUpDelay() {
        return this.pickupDelay > 0;
    }

    public void setUnlimitedLifetime() {
        this.age = -32768;
    }

    public void setExtendedLifetime() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = this.level().spigotConfig.itemDespawnRate - 1; // Spigot
    }

    public float getSpin(float tickDelta) {
        return ((float) this.getAge() + tickDelta) / 20.0F + this.bobOffs;
    }

    public ItemEntity copy() {
        return new ItemEntity(this);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.AMBIENT;
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return 180.0F - this.getSpin(0.5F) / 6.2831855F * 360.0F;
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        return mappedIndex == 0 ? SlotAccess.of(this::getItem, this::setItem) : super.getSlot(mappedIndex);
    }
}
