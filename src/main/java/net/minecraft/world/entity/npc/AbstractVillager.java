package net.minecraft.world.entity.npc;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.inventory.CraftMerchant;
import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
// CraftBukkit end

public abstract class AbstractVillager extends AgeableMob implements InventoryCarrier, Npc, Merchant {

    // CraftBukkit start
    private CraftMerchant craftMerchant;

    @Override
    public CraftMerchant getCraftMerchant() {
        return (this.craftMerchant == null) ? this.craftMerchant = new CraftMerchant(this) : this.craftMerchant;
    }
    // CraftBukkit end
    private static final EntityDataAccessor<Integer> DATA_UNHAPPY_COUNTER = SynchedEntityData.defineId(AbstractVillager.class, EntityDataSerializers.INT);
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int VILLAGER_SLOT_OFFSET = 300;
    private static final int VILLAGER_INVENTORY_SIZE = 8;
    @Nullable
    private Player tradingPlayer;
    @Nullable
    protected MerchantOffers offers;
    private final SimpleContainer inventory = new SimpleContainer(8, (org.bukkit.craftbukkit.entity.CraftAbstractVillager) this.getBukkitEntity()); // CraftBukkit add argument

    public AbstractVillager(EntityType<? extends AbstractVillager> type, Level world) {
        super(type, world);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType spawnReason, @Nullable SpawnGroupData entityData) {
        if (entityData == null) {
            entityData = new AgeableMob.AgeableMobGroupData(false);
        }

        return super.finalizeSpawn(world, difficulty, spawnReason, (SpawnGroupData) entityData);
    }

    public int getUnhappyCounter() {
        return (Integer) this.entityData.get(AbstractVillager.DATA_UNHAPPY_COUNTER);
    }

    public void setUnhappyCounter(int ticks) {
        this.entityData.set(AbstractVillager.DATA_UNHAPPY_COUNTER, ticks);
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AbstractVillager.DATA_UNHAPPY_COUNTER, 0);
    }

    @Override
    public void setTradingPlayer(@Nullable Player customer) {
        this.tradingPlayer = customer;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return this.tradingPlayer;
    }

    public boolean isTrading() {
        return this.tradingPlayer != null;
    }

    @Override
    public MerchantOffers getOffers() {
        if (this.level().isClientSide) {
            throw new IllegalStateException("Cannot load Villager offers on the client");
        } else {
            if (this.offers == null) {
                this.offers = new MerchantOffers();
                this.updateTrades();
            }

            return this.offers;
        }
    }

    @Override
    public void overrideOffers(@Nullable MerchantOffers offers) {}

    @Override
    public void overrideXp(int experience) {}

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.ambientSoundTime = -this.getAmbientSoundInterval();
        this.rewardTradeXp(offer);
        if (this.tradingPlayer instanceof ServerPlayer) {
            CriteriaTriggers.TRADE.trigger((ServerPlayer) this.tradingPlayer, this, offer.getResult());
        }

    }

    protected abstract void rewardTradeXp(MerchantOffer offer);

    @Override
    public boolean showProgressBar() {
        return true;
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
        if (!this.level().isClientSide && this.ambientSoundTime > -this.getAmbientSoundInterval() + 20) {
            this.ambientSoundTime = -this.getAmbientSoundInterval();
            this.makeSound(this.getTradeUpdatedSound(!stack.isEmpty()));
        }

    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    protected SoundEvent getTradeUpdatedSound(boolean sold) {
        return sold ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO;
    }

    public void playCelebrateSound() {
        this.makeSound(SoundEvents.VILLAGER_CELEBRATE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        if (!this.level().isClientSide) {
            MerchantOffers merchantrecipelist = this.getOffers();

            if (!merchantrecipelist.isEmpty()) {
                nbt.put("Offers", (Tag) MerchantOffers.CODEC.encodeStart(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), merchantrecipelist).getOrThrow());
            }
        }

        this.writeInventoryToTag(nbt, this.registryAccess());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        if (nbt.contains("Offers")) {
            DataResult<MerchantOffers> dataresult = MerchantOffers.CODEC.parse(this.registryAccess().createSerializationContext(NbtOps.INSTANCE), nbt.get("Offers")); // CraftBukkit - decompile error
            Logger logger = AbstractVillager.LOGGER;

            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(Util.prefix("Failed to load offers: ", logger::warn)).ifPresent((merchantrecipelist) -> {
                this.offers = merchantrecipelist;
            });
        }

        this.readInventoryFromTag(nbt, this.registryAccess());
    }

    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition teleportTarget) {
        this.stopTrading();
        return super.changeDimension(teleportTarget);
    }

    protected void stopTrading() {
        this.setTradingPlayer((Player) null);
    }

    @Override
    public void die(DamageSource damageSource) {
        super.die(damageSource);
        this.stopTrading();
    }

    protected void addParticlesAroundSelf(ParticleOptions parameters) {
        for (int i = 0; i < 5; ++i) {
            double d0 = this.random.nextGaussian() * 0.02D;
            double d1 = this.random.nextGaussian() * 0.02D;
            double d2 = this.random.nextGaussian() * 0.02D;

            this.level().addParticle(parameters, this.getRandomX(1.0D), this.getRandomY() + 1.0D, this.getRandomZ(1.0D), d0, d1, d2);
        }

    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public SimpleContainer getInventory() {
        return this.inventory;
    }

    @Override
    public SlotAccess getSlot(int mappedIndex) {
        int j = mappedIndex - 300;

        return j >= 0 && j < this.inventory.getContainerSize() ? SlotAccess.forContainer(this.inventory, j) : super.getSlot(mappedIndex);
    }

    protected abstract void updateTrades();

    protected void addOffersFromItemListings(MerchantOffers recipeList, VillagerTrades.ItemListing[] pool, int count) {
        ArrayList<VillagerTrades.ItemListing> arraylist = Lists.newArrayList(pool);
        int j = 0;

        while (j < count && !arraylist.isEmpty()) {
            MerchantOffer merchantrecipe = ((VillagerTrades.ItemListing) arraylist.remove(this.random.nextInt(arraylist.size()))).getOffer(this, this.random);

            if (merchantrecipe != null) {
                // CraftBukkit start
                VillagerAcquireTradeEvent event = new VillagerAcquireTradeEvent((org.bukkit.entity.AbstractVillager) this.getBukkitEntity(), merchantrecipe.asBukkit());
                // Suppress during worldgen
                if (this.valid) {
                    Bukkit.getPluginManager().callEvent(event);
                }
                if (!event.isCancelled()) {
                    recipeList.add(CraftMerchantRecipe.fromBukkit(event.getRecipe()).toMinecraft());
                }
                // CraftBukkit end
                ++j;
            }
        }

    }

    @Override
    public Vec3 getRopeHoldPosition(float delta) {
        float f1 = Mth.lerp(delta, this.yBodyRotO, this.yBodyRot) * 0.017453292F;
        Vec3 vec3d = new Vec3(0.0D, this.getBoundingBox().getYsize() - 1.0D, 0.2D);

        return this.getPosition(delta).add(vec3d.yRot(-f1));
    }

    @Override
    public boolean isClientSide() {
        return this.level().isClientSide;
    }
}
