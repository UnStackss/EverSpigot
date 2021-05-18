package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

// CraftBukkit start
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTransformEvent;
// CraftBukkit end

public class MushroomCow extends Cow implements Shearable, VariantHolder<MushroomCow.MushroomType> {

    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.STRING);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    @Nullable
    public SuspiciousStewEffects stewEffects;
    @Nullable
    private UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> type, Level world) {
        super(type, world);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader world) {
        return world.getBlockState(pos.below()).is(Blocks.MYCELIUM) ? 10.0F : world.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean checkMushroomSpawnRules(EntityType<MushroomCow> type, LevelAccessor world, MobSpawnType spawnReason, BlockPos pos, RandomSource random) {
        return world.getBlockState(pos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(world, pos);
    }

    @Override
    public void thunderHit(ServerLevel world, LightningBolt lightning) {
        UUID uuid = lightning.getUUID();

        if (!uuid.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.MushroomType.RED ? MushroomCow.MushroomType.BROWN : MushroomCow.MushroomType.RED);
            this.lastLightningBoltUUID = uuid;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }

    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MushroomCow.DATA_TYPE, MushroomCow.MushroomType.RED.type);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(Items.BOWL) && !this.isBaby()) {
            boolean flag = false;
            ItemStack itemstack1;

            if (this.stewEffects != null) {
                flag = true;
                itemstack1 = new ItemStack(Items.SUSPICIOUS_STEW);
                itemstack1.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemstack1 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemstack2 = ItemUtils.createFilledResult(itemstack, player, itemstack1, false);

            player.setItemInHand(hand, itemstack2);
            SoundEvent soundeffect;

            if (flag) {
                soundeffect = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundeffect = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundeffect, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else if (itemstack.is(Items.SHEARS) && this.readyForShearing()) {
            // CraftBukkit start
            // Paper start - custom shear drops
            java.util.List<ItemStack> drops = this.generateDefaultDrops();
            org.bukkit.event.player.PlayerShearEntityEvent event = CraftEventFactory.handlePlayerShearEntityEvent(player, this, itemstack, hand, drops);
            if (event != null) {
                if (event.isCancelled()) {
                    return InteractionResult.PASS;
                }
                drops = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(event.getDrops());
            }
            // Paper end - custom shear drops
            // CraftBukkit end
            this.shear(SoundSource.PLAYERS, drops); // Paper - custom shear drops
            this.gameEvent(GameEvent.SHEAR, player);
            if (!this.level().isClientSide) {
                itemstack.hurtAndBreak(1, player, getSlotForHand(hand));
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else if (this.getVariant() == MushroomCow.MushroomType.BROWN && itemstack.is(ItemTags.SMALL_FLOWERS)) {
            if (this.stewEffects != null) {
                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.SMOKE, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                }
            } else {
                Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemstack);

                if (optional.isEmpty()) {
                    return InteractionResult.PASS;
                }

                itemstack.consume(1, player);

                for (int j = 0; j < 4; ++j) {
                    this.level().addParticle(ParticleTypes.EFFECT, this.getX() + this.random.nextDouble() / 2.0D, this.getY(0.5D), this.getZ() + this.random.nextDouble() / 2.0D, 0.0D, this.random.nextDouble() / 5.0D, 0.0D);
                }

                this.stewEffects = (SuspiciousStewEffects) optional.get();
                this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(SoundSource shearedSoundCategory) {
        // Paper start - custom shear drops
        this.shear(shearedSoundCategory, this.generateDefaultDrops());
    }

    @Override
    public java.util.List<ItemStack> generateDefaultDrops() {
        java.util.List<ItemStack> dropEntities = new java.util.ArrayList<>(5);
        for (int i = 0; i < 5; ++i) {
            dropEntities.add(new ItemStack(this.getVariant().getBlockState().getBlock()));
        }
        return dropEntities;
    }

    @Override
    public void shear(SoundSource shearedSoundCategory, java.util.List<ItemStack> drops) { // If drops is null, need to generate drops
        // Paper end - custom shear drops
        this.level().playSound((Player) null, (Entity) this, SoundEvents.MOOSHROOM_SHEAR, shearedSoundCategory, 1.0F, 1.0F);
        if (!this.level().isClientSide()) {
            Cow entitycow = (Cow) EntityType.COW.create(this.level());

            if (entitycow != null) {
                ((ServerLevel) this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5D), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                // this.discard(); // CraftBukkit - moved down
                entitycow.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                entitycow.setHealth(this.getHealth());
                entitycow.yBodyRot = this.yBodyRot;
                if (this.hasCustomName()) {
                    entitycow.setCustomName(this.getCustomName());
                    entitycow.setCustomNameVisible(this.isCustomNameVisible());
                }

                if (this.isPersistenceRequired()) {
                    entitycow.setPersistenceRequired();
                }

                entitycow.setInvulnerable(this.isInvulnerable());
                // CraftBukkit start
                if (CraftEventFactory.callEntityTransformEvent(this, entitycow, EntityTransformEvent.TransformReason.SHEARED).isCancelled()) {
                    return;
                }
                this.level().addFreshEntity(entitycow, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SHEARED);

                this.discard(EntityRemoveEvent.Cause.TRANSFORMATION); // CraftBukkit - from above and add Bukkit remove cause
                // CraftBukkit end

                // Paper start - custom shear drops; moved drop generation to separate method
                for (final ItemStack drop : drops) {
                    ItemEntity entityitem = new ItemEntity(this.level(), this.getX(), this.getY(1.0D), this.getZ(), drop);
                    this.spawnAtLocation(entityitem);
                }
                // Paper end - custom shear drops
            }
        }

    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("Type", this.getVariant().getSerializedName());
        if (this.stewEffects != null) {
            SuspiciousStewEffects.CODEC.encodeStart(NbtOps.INSTANCE, this.stewEffects).ifSuccess((nbtbase) -> {
                nbt.put("stew_effects", nbtbase);
            });
        }

    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setVariant(MushroomCow.MushroomType.byType(nbt.getString("Type")));
        if (nbt.contains("stew_effects", 9)) {
            SuspiciousStewEffects.CODEC.parse(NbtOps.INSTANCE, nbt.get("stew_effects")).ifSuccess((suspicioussteweffects) -> {
                this.stewEffects = suspicioussteweffects;
            });
        }

    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack flower) {
        SuspiciousEffectHolder suspiciouseffectholder = SuspiciousEffectHolder.tryGet(flower.getItem());

        return suspiciouseffectholder != null ? Optional.of(suspiciouseffectholder.getSuspiciousEffects()) : Optional.empty();
    }

    public void setVariant(MushroomCow.MushroomType variant) {
        this.entityData.set(MushroomCow.DATA_TYPE, variant.type);
    }

    @Override
    public MushroomCow.MushroomType getVariant() {
        return MushroomCow.MushroomType.byType((String) this.entityData.get(MushroomCow.DATA_TYPE));
    }

    @Nullable
    @Override
    public MushroomCow getBreedOffspring(ServerLevel world, AgeableMob entity) {
        MushroomCow entitymushroomcow = (MushroomCow) EntityType.MOOSHROOM.create(world);

        if (entitymushroomcow != null) {
            entitymushroomcow.setVariant(this.getOffspringType((MushroomCow) entity));
        }

        return entitymushroomcow;
    }

    private MushroomCow.MushroomType getOffspringType(MushroomCow mooshroom) {
        MushroomCow.MushroomType entitymushroomcow_type = this.getVariant();
        MushroomCow.MushroomType entitymushroomcow_type1 = mooshroom.getVariant();
        MushroomCow.MushroomType entitymushroomcow_type2;

        if (entitymushroomcow_type == entitymushroomcow_type1 && this.random.nextInt(1024) == 0) {
            entitymushroomcow_type2 = entitymushroomcow_type == MushroomCow.MushroomType.BROWN ? MushroomCow.MushroomType.RED : MushroomCow.MushroomType.BROWN;
        } else {
            entitymushroomcow_type2 = this.random.nextBoolean() ? entitymushroomcow_type : entitymushroomcow_type1;
        }

        return entitymushroomcow_type2;
    }

    public static enum MushroomType implements StringRepresentable {

        RED("red", Blocks.RED_MUSHROOM.defaultBlockState()), BROWN("brown", Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final StringRepresentable.EnumCodec<MushroomCow.MushroomType> CODEC = StringRepresentable.fromEnum(MushroomCow.MushroomType::values);
        final String type;
        final BlockState blockState;

        private MushroomType(final String s, final BlockState iblockdata) {
            this.type = s;
            this.blockState = iblockdata;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        static MushroomCow.MushroomType byType(String name) {
            return (MushroomCow.MushroomType) MushroomCow.MushroomType.CODEC.byName(name, MushroomCow.MushroomType.RED);
        }
    }
}
