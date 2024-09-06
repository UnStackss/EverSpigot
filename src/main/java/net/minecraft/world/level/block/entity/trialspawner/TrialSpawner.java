package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseLootEvent;
// CraftBukkit end

public final class TrialSpawner {

    public static final String NORMAL_CONFIG_TAG_NAME = "normal_config";
    public static final String OMINOUS_CONFIG_TAG_NAME = "ominous_config";
    public static final int DETECT_PLAYER_SPAWN_BUFFER = 40;
    private static final int DEFAULT_TARGET_COOLDOWN_LENGTH = 36000;
    private static final int DEFAULT_PLAYER_SCAN_RANGE = 14;
    private static final int MAX_MOB_TRACKING_DISTANCE = 47;
    private static final int MAX_MOB_TRACKING_DISTANCE_SQR = Mth.square(47);
    private static final float SPAWNING_AMBIENT_SOUND_CHANCE = 0.02F;
    public TrialSpawnerConfig normalConfig;
    public TrialSpawnerConfig ominousConfig;
    private final TrialSpawnerData data;
    public int requiredPlayerRange;
    public int targetCooldownLength;
    public final TrialSpawner.StateAccessor stateAccessor;
    private PlayerDetector playerDetector;
    private final PlayerDetector.EntitySelector entitySelector;
    private boolean overridePeacefulAndMobSpawnRule;
    public boolean isOminous;

    public Codec<TrialSpawner> codec() {
        return RecordCodecBuilder.create((instance) -> {
            return instance.group(TrialSpawnerConfig.CODEC.optionalFieldOf("normal_config", TrialSpawnerConfig.DEFAULT).forGetter(TrialSpawner::getNormalConfig), TrialSpawnerConfig.CODEC.optionalFieldOf("ominous_config", TrialSpawnerConfig.DEFAULT).forGetter(TrialSpawner::getOminousConfigForSerialization), TrialSpawnerData.MAP_CODEC.forGetter(TrialSpawner::getData), Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("target_cooldown_length", 36000).forGetter(TrialSpawner::getTargetCooldownLength), Codec.intRange(1, 128).optionalFieldOf("required_player_range", 14).forGetter(TrialSpawner::getRequiredPlayerRange)).apply(instance, (trialspawnerconfig, trialspawnerconfig1, trialspawnerdata, integer, integer1) -> {
                return new TrialSpawner(trialspawnerconfig, trialspawnerconfig1, trialspawnerdata, integer, integer1, this.stateAccessor, this.playerDetector, this.entitySelector);
            });
        });
    }

    public TrialSpawner(TrialSpawner.StateAccessor trialSpawner, PlayerDetector entityDetector, PlayerDetector.EntitySelector entitySelector) {
        this(TrialSpawnerConfig.DEFAULT, TrialSpawnerConfig.DEFAULT, new TrialSpawnerData(), 36000, 14, trialSpawner, entityDetector, entitySelector);
    }

    public TrialSpawner(TrialSpawnerConfig normalConfig, TrialSpawnerConfig ominousConfig, TrialSpawnerData data, int cooldownLength, int entityDetectionRange, TrialSpawner.StateAccessor trialSpawner, PlayerDetector entityDetector, PlayerDetector.EntitySelector entitySelector) {
        this.normalConfig = normalConfig;
        this.ominousConfig = ominousConfig;
        this.data = data;
        this.targetCooldownLength = cooldownLength;
        this.requiredPlayerRange = entityDetectionRange;
        this.stateAccessor = trialSpawner;
        this.playerDetector = entityDetector;
        this.entitySelector = entitySelector;
    }

    public TrialSpawnerConfig getConfig() {
        return this.isOminous ? this.ominousConfig : this.normalConfig;
    }

    @VisibleForTesting
    public TrialSpawnerConfig getNormalConfig() {
        return this.normalConfig;
    }

    @VisibleForTesting
    public TrialSpawnerConfig getOminousConfig() {
        return this.ominousConfig;
    }

    private TrialSpawnerConfig getOminousConfigForSerialization() {
        return !this.ominousConfig.equals(this.normalConfig) ? this.ominousConfig : TrialSpawnerConfig.DEFAULT;
    }

    public void applyOminous(ServerLevel world, BlockPos pos) {
        world.setBlock(pos, (BlockState) world.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, true), 3);
        world.levelEvent(3020, pos, 1);
        this.isOminous = true;
        this.data.resetAfterBecomingOminous(this, world);
    }

    public void removeOminous(ServerLevel world, BlockPos pos) {
        world.setBlock(pos, (BlockState) world.getBlockState(pos).setValue(TrialSpawnerBlock.OMINOUS, false), 3);
        this.isOminous = false;
    }

    public boolean isOminous() {
        return this.isOminous;
    }

    public TrialSpawnerData getData() {
        return this.data;
    }

    public int getTargetCooldownLength() {
        return this.targetCooldownLength;
    }

    public int getRequiredPlayerRange() {
        return this.requiredPlayerRange;
    }

    public TrialSpawnerState getState() {
        return this.stateAccessor.getState();
    }

    public void setState(Level world, TrialSpawnerState spawnerState) {
        this.stateAccessor.setState(world, spawnerState);
    }

    public void markUpdated() {
        this.stateAccessor.markUpdated();
    }

    public PlayerDetector getPlayerDetector() {
        return this.playerDetector;
    }

    public PlayerDetector.EntitySelector getEntitySelector() {
        return this.entitySelector;
    }

    public boolean canSpawnInLevel(Level world) {
        return this.overridePeacefulAndMobSpawnRule ? true : (world.getDifficulty() == Difficulty.PEACEFUL ? false : world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING));
    }

    public Optional<UUID> spawnMob(ServerLevel world, BlockPos pos) {
        RandomSource randomsource = world.getRandom();
        SpawnData mobspawnerdata = this.data.getOrCreateNextSpawnData(this, world.getRandom());
        CompoundTag nbttagcompound = mobspawnerdata.entityToSpawn();
        ListTag nbttaglist = nbttagcompound.getList("Pos", 6);
        Optional<EntityType<?>> optional = EntityType.by(nbttagcompound);

        if (optional.isEmpty()) {
            return Optional.empty();
        } else {
            int i = nbttaglist.size();
            double d0 = i >= 1 ? nbttaglist.getDouble(0) : (double) pos.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.getConfig().spawnRange() + 0.5D;
            double d1 = i >= 2 ? nbttaglist.getDouble(1) : (double) (pos.getY() + randomsource.nextInt(3) - 1);
            double d2 = i >= 3 ? nbttaglist.getDouble(2) : (double) pos.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.getConfig().spawnRange() + 0.5D;

            if (!world.noCollision(((EntityType) optional.get()).getSpawnAABB(d0, d1, d2))) {
                return Optional.empty();
            } else {
                Vec3 vec3d = new Vec3(d0, d1, d2);

                if (!TrialSpawner.inLineOfSight(world, pos.getCenter(), vec3d)) {
                    return Optional.empty();
                } else {
                    BlockPos blockposition1 = BlockPos.containing(vec3d);

                    if (!SpawnPlacements.checkSpawnRules((EntityType) optional.get(), world, MobSpawnType.TRIAL_SPAWNER, blockposition1, world.getRandom())) {
                        return Optional.empty();
                    } else {
                        if (mobspawnerdata.getCustomSpawnRules().isPresent()) {
                            SpawnData.CustomSpawnRules mobspawnerdata_a = (SpawnData.CustomSpawnRules) mobspawnerdata.getCustomSpawnRules().get();

                            if (!mobspawnerdata_a.isValidPosition(blockposition1, world)) {
                                return Optional.empty();
                            }
                        }

                        Entity entity = EntityType.loadEntityRecursive(nbttagcompound, world, (entity1) -> {
                            entity1.moveTo(d0, d1, d2, randomsource.nextFloat() * 360.0F, 0.0F);
                            return entity1;
                        });

                        if (entity == null) {
                            return Optional.empty();
                        } else {
                            if (entity instanceof Mob) {
                                Mob entityinsentient = (Mob) entity;

                                if (!entityinsentient.checkSpawnObstruction(world)) {
                                    return Optional.empty();
                                }

                                boolean flag = mobspawnerdata.getEntityToSpawn().size() == 1 && mobspawnerdata.getEntityToSpawn().contains("id", 8);

                                if (flag) {
                                    entityinsentient.finalizeSpawn(world, world.getCurrentDifficultyAt(entityinsentient.blockPosition()), MobSpawnType.TRIAL_SPAWNER, (SpawnGroupData) null);
                                }

                                entityinsentient.setPersistenceRequired();
                                Optional<net.minecraft.world.entity.EquipmentTable> optional1 = mobspawnerdata.getEquipment(); // CraftBukkit - decompile error

                                Objects.requireNonNull(entityinsentient);
                                optional1.ifPresent(entityinsentient::equip);
                            }

                            // CraftBukkit start
                            if (org.bukkit.craftbukkit.event.CraftEventFactory.callTrialSpawnerSpawnEvent(entity, pos).isCancelled()) {
                                return Optional.empty();
                            }
                            if (!world.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER)) {
                                // CraftBukkit end
                                return Optional.empty();
                            } else {
                                TrialSpawner.FlameParticle trialspawner_a = this.isOminous ? TrialSpawner.FlameParticle.OMINOUS : TrialSpawner.FlameParticle.NORMAL;

                                world.levelEvent(3011, pos, trialspawner_a.encode());
                                world.levelEvent(3012, blockposition1, trialspawner_a.encode());
                                world.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, blockposition1);
                                return Optional.of(entity.getUUID());
                            }
                        }
                    }
                }
            }
        }
    }

    public void ejectReward(ServerLevel world, BlockPos pos, ResourceKey<LootTable> lootTable) {
        LootTable loottable = world.getServer().reloadableRegistries().getLootTable(lootTable);
        LootParams lootparams = (new LootParams.Builder(world)).create(LootContextParamSets.EMPTY);
        ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams);

        if (!objectarraylist.isEmpty()) {
            // CraftBukkit start
            BlockDispenseLootEvent spawnerDispenseLootEvent = CraftEventFactory.callBlockDispenseLootEvent(world, pos, null, objectarraylist);
            if (spawnerDispenseLootEvent.isCancelled()) {
                return;
            }

            objectarraylist = new ObjectArrayList<>(spawnerDispenseLootEvent.getDispensedLoot().stream().map(CraftItemStack::asNMSCopy).toList());
            // CraftBukkit end

            ObjectListIterator objectlistiterator = objectarraylist.iterator();

            while (objectlistiterator.hasNext()) {
                ItemStack itemstack = (ItemStack) objectlistiterator.next();

                DefaultDispenseItemBehavior.spawnItem(world, itemstack, 2, Direction.UP, Vec3.atBottomCenterOf(pos).relative(Direction.UP, 1.2D));
            }

            world.levelEvent(3014, pos, 0);
        }

    }

    public void tickClient(Level world, BlockPos pos, boolean ominous) {
        TrialSpawnerState trialspawnerstate = this.getState();

        trialspawnerstate.emitParticles(world, pos, ominous);
        if (trialspawnerstate.hasSpinningMob()) {
            double d0 = (double) Math.max(0L, this.data.nextMobSpawnsAt - world.getGameTime());

            this.data.oSpin = this.data.spin;
            this.data.spin = (this.data.spin + trialspawnerstate.spinningMobSpeed() / (d0 + 200.0D)) % 360.0D;
        }

        if (trialspawnerstate.isCapableOfSpawning()) {
            RandomSource randomsource = world.getRandom();

            if (randomsource.nextFloat() <= 0.02F) {
                SoundEvent soundeffect = ominous ? SoundEvents.TRIAL_SPAWNER_AMBIENT_OMINOUS : SoundEvents.TRIAL_SPAWNER_AMBIENT;

                world.playLocalSound(pos, soundeffect, SoundSource.BLOCKS, randomsource.nextFloat() * 0.25F + 0.75F, randomsource.nextFloat() + 0.5F, false);
            }
        }

    }

    public void tickServer(ServerLevel world, BlockPos pos, boolean ominous) {
        this.isOminous = ominous;
        TrialSpawnerState trialspawnerstate = this.getState();

        if (this.data.currentMobs.removeIf((uuid) -> {
            return TrialSpawner.shouldMobBeUntracked(world, pos, uuid);
        })) {
            this.data.nextMobSpawnsAt = world.getGameTime() + (long) this.getConfig().ticksBetweenSpawn();
        }

        TrialSpawnerState trialspawnerstate1 = trialspawnerstate.tickAndGetNext(pos, this, world);

        if (trialspawnerstate1 != trialspawnerstate) {
            this.setState(world, trialspawnerstate1);
        }

    }

    private static boolean shouldMobBeUntracked(ServerLevel world, BlockPos pos, UUID uuid) {
        Entity entity = world.getEntity(uuid);

        return entity == null || !entity.isAlive() || !entity.level().dimension().equals(world.dimension()) || entity.blockPosition().distSqr(pos) > (double) TrialSpawner.MAX_MOB_TRACKING_DISTANCE_SQR;
    }

    private static boolean inLineOfSight(Level world, Vec3 spawnerPos, Vec3 spawnPos) {
        BlockHitResult movingobjectpositionblock = world.clip(new ClipContext(spawnPos, spawnerPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));

        return movingobjectpositionblock.getBlockPos().equals(BlockPos.containing(spawnerPos)) || movingobjectpositionblock.getType() == HitResult.Type.MISS;
    }

    public static void addSpawnParticles(Level world, BlockPos pos, RandomSource random, SimpleParticleType particle) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d1 = (double) pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;

            world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            world.addParticle(particle, d0, d1, d2, 0.0D, 0.0D, 0.0D);
        }

    }

    public static void addBecomeOminousParticles(Level world, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d1 = (double) pos.getY() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d2 = (double) pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 2.0D;
            double d3 = random.nextGaussian() * 0.02D;
            double d4 = random.nextGaussian() * 0.02D;
            double d5 = random.nextGaussian() * 0.02D;

            world.addParticle(ParticleTypes.TRIAL_OMEN, d0, d1, d2, d3, d4, d5);
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, d0, d1, d2, d3, d4, d5);
        }

    }

    public static void addDetectPlayerParticles(Level world, BlockPos pos, RandomSource random, int playerCount, ParticleOptions particle) {
        for (int j = 0; j < 30 + Math.min(playerCount, 10) * 5; ++j) {
            double d0 = (double) (2.0F * random.nextFloat() - 1.0F) * 0.65D;
            double d1 = (double) (2.0F * random.nextFloat() - 1.0F) * 0.65D;
            double d2 = (double) pos.getX() + 0.5D + d0;
            double d3 = (double) pos.getY() + 0.1D + (double) random.nextFloat() * 0.8D;
            double d4 = (double) pos.getZ() + 0.5D + d1;

            world.addParticle(particle, d2, d3, d4, 0.0D, 0.0D, 0.0D);
        }

    }

    public static void addEjectItemParticles(Level world, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 20; ++i) {
            double d0 = (double) pos.getX() + 0.4D + random.nextDouble() * 0.2D;
            double d1 = (double) pos.getY() + 0.4D + random.nextDouble() * 0.2D;
            double d2 = (double) pos.getZ() + 0.4D + random.nextDouble() * 0.2D;
            double d3 = random.nextGaussian() * 0.02D;
            double d4 = random.nextGaussian() * 0.02D;
            double d5 = random.nextGaussian() * 0.02D;

            world.addParticle(ParticleTypes.SMALL_FLAME, d0, d1, d2, d3, d4, d5 * 0.25D);
            world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, d3, d4, d5);
        }

    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void setPlayerDetector(PlayerDetector detector) {
        this.playerDetector = detector;
    }

    /** @deprecated */
    @Deprecated(forRemoval = true)
    @VisibleForTesting
    public void overridePeacefulAndMobSpawnRule() {
        this.overridePeacefulAndMobSpawnRule = true;
    }

    public interface StateAccessor {

        void setState(Level world, TrialSpawnerState spawnerState);

        TrialSpawnerState getState();

        void markUpdated();
    }

    public static enum FlameParticle {

        NORMAL(ParticleTypes.FLAME), OMINOUS(ParticleTypes.SOUL_FIRE_FLAME);

        public final SimpleParticleType particleType;

        private FlameParticle(final SimpleParticleType particletype) {
            this.particleType = particletype;
        }

        public static TrialSpawner.FlameParticle decode(int index) {
            TrialSpawner.FlameParticle[] atrialspawner_a = values();

            return index <= atrialspawner_a.length && index >= 0 ? atrialspawner_a[index] : TrialSpawner.FlameParticle.NORMAL;
        }

        public int encode() {
            return this.ordinal();
        }
    }
}
