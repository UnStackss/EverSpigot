package net.minecraft.world.entity.monster;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class Bogged extends AbstractSkeleton implements Shearable {

    private static final int HARD_ATTACK_INTERVAL = 50;
    private static final int NORMAL_ATTACK_INTERVAL = 70;
    private static final EntityDataAccessor<Boolean> DATA_SHEARED = SynchedEntityData.defineId(Bogged.class, EntityDataSerializers.BOOLEAN);
    public static final String SHEARED_TAG_NAME = "sheared";

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes().add(Attributes.MAX_HEALTH, 16.0D);
    }

    public Bogged(EntityType<? extends Bogged> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(Bogged.DATA_SHEARED, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("sheared", this.isSheared());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setSheared(nbt.getBoolean("sheared"));
    }

    public boolean isSheared() {
        return (Boolean) this.entityData.get(Bogged.DATA_SHEARED);
    }

    public void setSheared(boolean sheared) {
        this.entityData.set(Bogged.DATA_SHEARED, sheared);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            // CraftBukkit start
            // Paper start - expose drops in event
            java.util.List<net.minecraft.world.item.ItemStack> drops = generateDefaultDrops();
            final org.bukkit.event.player.PlayerShearEntityEvent event = org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemstack, hand, drops);
            if (event != null) {
                if (event.isCancelled()) {
                    if (player instanceof final net.minecraft.server.level.ServerPlayer serverPlayer) this.resendPossiblyDesyncedDataValues(java.util.List.of(Bogged.DATA_SHEARED), serverPlayer);
                    return InteractionResult.PASS;
                }
                drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
            // Paper end - expose drops in event
            }
            // CraftBukkit end
            this.shear(SoundSource.PLAYERS, drops); // Paper - expose drops in event
            this.gameEvent(GameEvent.SHEAR, player);
            if (!this.level().isClientSide) {
                itemstack.hurtAndBreak(1, player, getSlotForHand(hand));
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BOGGED_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BOGGED_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.BOGGED_DEATH;
    }

    @Override
    protected SoundEvent getStepSound() {
        return SoundEvents.BOGGED_STEP;
    }

    @Override
    protected AbstractArrow getArrow(ItemStack arrow, float damageModifier, @Nullable ItemStack shotFrom) {
        AbstractArrow entityarrow = super.getArrow(arrow, damageModifier, shotFrom);

        if (entityarrow instanceof Arrow entitytippedarrow) {
            entitytippedarrow.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        }

        return entityarrow;
    }

    @Override
    protected int getHardAttackInterval() {
        return 50;
    }

    @Override
    protected int getAttackInterval() {
        return 70;
    }

    @Override
    public void shear(SoundSource shearedSoundCategory) {
    // Paper start - shear drop API
        this.shear(shearedSoundCategory, generateDefaultDrops());
    }

    @Override
    public void shear(SoundSource shearedSoundCategory, java.util.List<net.minecraft.world.item.ItemStack> drops) {
    // Paper end - shear drop API
        this.level().playSound((Player) null, (Entity) this, SoundEvents.BOGGED_SHEAR, shearedSoundCategory, 1.0F, 1.0F);
        this.spawnDrops(drops); // Paper - shear drop API
        this.setSheared(true);
    }

    private void spawnShearedMushrooms() {
    // Paper start - shear drops API
        this.spawnDrops(generateDefaultDrops()); // Only here for people calling spawnSheardMushrooms. Not used otherwise.
    }
    private void spawnDrops(java.util.List<net.minecraft.world.item.ItemStack> drops) {
        drops.forEach(stack -> {
            this.forceDrops = true;
            this.spawnAtLocation(stack, this.getBbHeight());
            this.forceDrops = false;
        });
    }
    private void generateShearedMushrooms(java.util.function.Consumer<ItemStack> stackConsumer) {
    // Paper end - shear drops API
        Level world = this.level();

        if (world instanceof ServerLevel worldserver) {
            LootTable loottable = worldserver.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.BOGGED_SHEAR);
            LootParams lootparams = (new LootParams.Builder(worldserver)).withParameter(LootContextParams.ORIGIN, this.position()).withParameter(LootContextParams.THIS_ENTITY, this).create(LootContextParamSets.SHEARING);
            ObjectListIterator objectlistiterator = loottable.getRandomItems(lootparams).iterator();

            while (objectlistiterator.hasNext()) {
                ItemStack itemstack = (ItemStack) objectlistiterator.next();

                stackConsumer.accept(itemstack); // Paper
            }
        }

    }

    // Paper start - shear drops API
    @Override
    public java.util.List<ItemStack> generateDefaultDrops() {
        final java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        this.generateShearedMushrooms(drops::add);
        return drops;
    }
    // Paper end - shear drops API

    @Override
    public boolean readyForShearing() {
        return !this.isSheared() && this.isAlive();
    }
}
