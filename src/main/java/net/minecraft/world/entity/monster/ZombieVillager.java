package net.minecraft.world.entity.monster;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Iterator;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

// CraftBukkit start
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class ZombieVillager extends Zombie implements VillagerDataHolder {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final EntityDataAccessor<Boolean> DATA_CONVERTING_ID = SynchedEntityData.defineId(ZombieVillager.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER_DATA = SynchedEntityData.defineId(ZombieVillager.class, EntityDataSerializers.VILLAGER_DATA);
    private static final int VILLAGER_CONVERSION_WAIT_MIN = 3600;
    private static final int VILLAGER_CONVERSION_WAIT_MAX = 6000;
    private static final int MAX_SPECIAL_BLOCKS_COUNT = 14;
    private static final int SPECIAL_BLOCK_RADIUS = 4;
    public int villagerConversionTime;
    @Nullable
    public UUID conversionStarter;
    @Nullable
    private Tag gossips;
    @Nullable
    private MerchantOffers tradeOffers;
    private int villagerXp;
    private int lastTick = MinecraftServer.currentTick; // CraftBukkit - add field

    public ZombieVillager(EntityType<? extends ZombieVillager> type, Level world) {
        super(type, world);
        BuiltInRegistries.VILLAGER_PROFESSION.getRandom(this.random).ifPresent((holder_c) -> {
            this.setVillagerData(this.getVillagerData().setProfession((VillagerProfession) holder_c.value()));
        });
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ZombieVillager.DATA_CONVERTING_ID, false);
        builder.define(ZombieVillager.DATA_VILLAGER_DATA, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        DataResult<Tag> dataresult = VillagerData.CODEC.encodeStart(NbtOps.INSTANCE, this.getVillagerData()); // CraftBukkit - decompile error
        Logger logger = ZombieVillager.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("VillagerData", nbtbase);
        });
        if (this.tradeOffers != null) {
            nbt.put("Offers", (Tag) MerchantOffers.CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), this.tradeOffers).getOrThrow());
        }

        if (this.gossips != null) {
            nbt.put("Gossips", this.gossips);
        }

        nbt.putInt("ConversionTime", this.isConverting() ? this.villagerConversionTime : -1);
        if (this.conversionStarter != null) {
            nbt.putUUID("ConversionPlayer", this.conversionStarter);
        }

        nbt.putInt("Xp", this.villagerXp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("VillagerData", 10)) {
            DataResult<VillagerData> dataresult = VillagerData.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.get("VillagerData")));
            Logger logger = ZombieVillager.LOGGER;

            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent(this::setVillagerData);
        }

        if (nbt.contains("Offers")) {
            DataResult<MerchantOffers> dataresult1 = MerchantOffers.CODEC.parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), nbt.get("Offers")); // CraftBukkit - decompile error
            Logger logger1 = ZombieVillager.LOGGER;

            Objects.requireNonNull(logger1);
            dataresult1.resultOrPartial(Util.prefix("Failed to load offers: ", logger1::warn)).ifPresent((merchantrecipelist) -> {
                this.tradeOffers = merchantrecipelist;
            });
        }

        if (nbt.contains("Gossips", 9)) {
            this.gossips = nbt.getList("Gossips", 10);
        }

        if (nbt.contains("ConversionTime", 99) && nbt.getInt("ConversionTime") > -1) {
            this.startConverting(nbt.hasUUID("ConversionPlayer") ? nbt.getUUID("ConversionPlayer") : null, nbt.getInt("ConversionTime"));
        }

        if (nbt.contains("Xp", 3)) {
            this.villagerXp = nbt.getInt("Xp");
        }

    }

    @Override
    public void tick() {
        if (!this.level().isClientSide && this.isAlive() && this.isConverting()) {
            int i = this.getConversionProgress();
            // CraftBukkit start - Use wall time instead of ticks for villager conversion
            int elapsedTicks = MinecraftServer.currentTick - this.lastTick;
            i *= elapsedTicks;
            // CraftBukkit end

            this.villagerConversionTime -= i;
            if (this.villagerConversionTime <= 0) {
                this.finishConversion((ServerLevel) this.level());
            }
        }

        super.tick();
        this.lastTick = MinecraftServer.currentTick; // CraftBukkit
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.GOLDEN_APPLE)) {
            if (this.hasEffect(MobEffects.WEAKNESS)) {
                itemstack.consume(1, player);
                if (!this.level().isClientSide) {
                    this.startConverting(player.getUUID(), this.random.nextInt(2401) + 3600);
                }

                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return !this.isConverting() && this.villagerXp == 0;
    }

    public boolean isConverting() {
        return (Boolean) this.getEntityData().get(ZombieVillager.DATA_CONVERTING_ID);
    }

    public void startConverting(@Nullable UUID uuid, int delay) {
    // Paper start - missing entity behaviour api - converting without entity event
        this.startConverting(uuid, delay, true);
    }

    public void startConverting(@Nullable UUID uuid, int delay, boolean broadcastEntityEvent) {
    // Paper end - missing entity behaviour api - converting without entity event
        this.conversionStarter = uuid;
        this.villagerConversionTime = delay;
        this.getEntityData().set(ZombieVillager.DATA_CONVERTING_ID, true);
        // CraftBukkit start
        this.removeEffect(MobEffects.WEAKNESS, org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, delay, Math.min(this.level().getDifficulty().getId() - 1, 0)), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION);
        // CraftBukkit end
        if (broadcastEntityEvent) this.level().broadcastEntityEvent(this, (byte) 16); // Paper - missing entity behaviour api - converting without entity event
    }

    @Override
    public void handleEntityEvent(byte status) {
        if (status == 16) {
            if (!this.isSilent()) {
                this.level().playLocalSound(this.getX(), this.getEyeY(), this.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, this.getSoundSource(), 1.0F + this.random.nextFloat(), this.random.nextFloat() * 0.7F + 0.3F, false);
            }

        } else {
            super.handleEntityEvent(status);
        }
    }

    private void finishConversion(ServerLevel world) {
        // CraftBukkit start
        Villager entityvillager = (Villager) this.convertTo(EntityType.VILLAGER, false, EntityTransformEvent.TransformReason.CURED, CreatureSpawnEvent.SpawnReason.CURED);
        if (entityvillager == null) {
            ((org.bukkit.entity.ZombieVillager) this.getBukkitEntity()).setConversionTime(-1); // SPIGOT-5208: End conversion to stop event spam
            return;
        }
        // CraftBukkit end

        if (entityvillager != null) {
            this.forceDrops = true; // CraftBukkit
            Iterator iterator = this.dropPreservedEquipment((itemstack) -> {
                return !EnchantmentHelper.has(itemstack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
            }).iterator();
            this.forceDrops = false; // CraftBukkit

            while (iterator.hasNext()) {
                EquipmentSlot enumitemslot = (EquipmentSlot) iterator.next();
                SlotAccess slotaccess = entityvillager.getSlot(enumitemslot.getIndex() + 300);

                slotaccess.set(this.getItemBySlot(enumitemslot));
            }

            entityvillager.setVillagerData(this.getVillagerData());
            if (this.gossips != null) {
                entityvillager.setGossips(this.gossips);
            }

            if (this.tradeOffers != null) {
                entityvillager.setOffers(this.tradeOffers.copy());
            }

            entityvillager.setVillagerXp(this.villagerXp);
            entityvillager.finalizeSpawn(world, world.getCurrentDifficultyAt(entityvillager.blockPosition()), MobSpawnType.CONVERSION, (SpawnGroupData) null);
            entityvillager.refreshBrain(world);
            if (this.conversionStarter != null) {
                Player entityhuman = world.getPlayerByUUID(this.conversionStarter);

                if (entityhuman instanceof ServerPlayer) {
                    CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger((ServerPlayer) entityhuman, this, entityvillager);
                    world.onReputationEvent(ReputationEventType.ZOMBIE_VILLAGER_CURED, entityhuman, entityvillager);
                }
            }

            entityvillager.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.CONVERSION); // CraftBukkit
            if (!this.isSilent()) {
                world.levelEvent((Player) null, 1027, this.blockPosition(), 0);
            }

        }
    }

    private int getConversionProgress() {
        int i = 1;

        if (this.random.nextFloat() < 0.01F) {
            int j = 0;
            BlockPos.MutableBlockPos blockposition_mutableblockposition = new BlockPos.MutableBlockPos();

            for (int k = (int) this.getX() - 4; k < (int) this.getX() + 4 && j < 14; ++k) {
                for (int l = (int) this.getY() - 4; l < (int) this.getY() + 4 && j < 14; ++l) {
                    for (int i1 = (int) this.getZ() - 4; i1 < (int) this.getZ() + 4 && j < 14; ++i1) {
                        BlockState iblockdata = this.level().getBlockState(blockposition_mutableblockposition.set(k, l, i1));

                        if (iblockdata.is(Blocks.IRON_BARS) || iblockdata.getBlock() instanceof BedBlock) {
                            if (this.random.nextFloat() < 0.3F) {
                                ++i;
                            }

                            ++j;
                        }
                    }
                }
            }
        }

        return i;
    }

    @Override
    public float getVoicePitch() {
        return this.isBaby() ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 2.0F : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }

    @Override
    public SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }

    @Override
    public SoundEvent getStepSound() {
        return SoundEvents.ZOMBIE_VILLAGER_STEP;
    }

    @Override
    protected ItemStack getSkull() {
        return ItemStack.EMPTY;
    }

    public void setTradeOffers(MerchantOffers offerData) {
        this.tradeOffers = offerData;
    }

    public void setGossips(Tag gossipData) {
        this.gossips = gossipData;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        this.setVillagerData(this.getVillagerData().setType(VillagerType.byBiome(world.getBiome(this.blockPosition()))));
        return super.finalizeSpawn(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void setVillagerData(VillagerData villagerData) {
        VillagerData villagerdata1 = this.getVillagerData();

        if (villagerdata1.getProfession() != villagerData.getProfession()) {
            this.tradeOffers = null;
        }

        this.entityData.set(ZombieVillager.DATA_VILLAGER_DATA, villagerData);
    }

    @Override
    public VillagerData getVillagerData() {
        return (VillagerData) this.entityData.get(ZombieVillager.DATA_VILLAGER_DATA);
    }

    public int getVillagerXp() {
        return this.villagerXp;
    }

    public void setVillagerXp(int xp) {
        this.villagerXp = xp;
    }
}
