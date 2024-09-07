package net.minecraft.world.level.block.entity.trialspawner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public enum TrialSpawnerState implements StringRepresentable {
    INACTIVE("inactive", 0, TrialSpawnerState.ParticleEmission.NONE, -1.0, false),
    WAITING_FOR_PLAYERS("waiting_for_players", 4, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, 200.0, true),
    ACTIVE("active", 8, TrialSpawnerState.ParticleEmission.FLAMES_AND_SMOKE, 1000.0, true),
    WAITING_FOR_REWARD_EJECTION("waiting_for_reward_ejection", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    EJECTING_REWARD("ejecting_reward", 8, TrialSpawnerState.ParticleEmission.SMALL_FLAMES, -1.0, false),
    COOLDOWN("cooldown", 0, TrialSpawnerState.ParticleEmission.SMOKE_INSIDE_AND_TOP_FACE, -1.0, false);

    private static final float DELAY_BEFORE_EJECT_AFTER_KILLING_LAST_MOB = 40.0F;
    private static final int TIME_BETWEEN_EACH_EJECTION = Mth.floor(30.0F);
    private final String name;
    private final int lightLevel;
    private final double spinningMobSpeed;
    private final TrialSpawnerState.ParticleEmission particleEmission;
    private final boolean isCapableOfSpawning;

    private TrialSpawnerState(
        final String id,
        final int luminance,
        final TrialSpawnerState.ParticleEmission particleEmitter,
        final double displayRotationSpeed,
        final boolean playsSound
    ) {
        this.name = id;
        this.lightLevel = luminance;
        this.particleEmission = particleEmitter;
        this.spinningMobSpeed = displayRotationSpeed;
        this.isCapableOfSpawning = playsSound;
    }

    TrialSpawnerState tickAndGetNext(BlockPos pos, TrialSpawner logic, ServerLevel world) {
        TrialSpawnerData trialSpawnerData = logic.getData();
        TrialSpawnerConfig trialSpawnerConfig = logic.getConfig();

        return switch (this) {
            case INACTIVE -> trialSpawnerData.getOrCreateDisplayEntity(logic, world, WAITING_FOR_PLAYERS) == null ? this : WAITING_FOR_PLAYERS;
            case WAITING_FOR_PLAYERS -> {
                if (!logic.canSpawnInLevel(world)) {
                    trialSpawnerData.reset();
                    yield this;
                } else if (!trialSpawnerData.hasMobToSpawn(logic, world.random)) {
                    yield INACTIVE;
                } else {
                    trialSpawnerData.tryDetectPlayers(world, pos, logic);
                    yield trialSpawnerData.detectedPlayers.isEmpty() ? this : ACTIVE;
                }
            }
            case ACTIVE -> {
                if (!logic.canSpawnInLevel(world)) {
                    trialSpawnerData.reset();
                    yield WAITING_FOR_PLAYERS;
                } else if (!trialSpawnerData.hasMobToSpawn(logic, world.random)) {
                    yield INACTIVE;
                } else {
                    int i = trialSpawnerData.countAdditionalPlayers(pos);
                    trialSpawnerData.tryDetectPlayers(world, pos, logic);
                    if (logic.isOminous()) {
                        this.spawnOminousOminousItemSpawner(world, pos, logic);
                    }

                    if (trialSpawnerData.hasFinishedSpawningAllMobs(trialSpawnerConfig, i)) {
                        if (trialSpawnerData.haveAllCurrentMobsDied()) {
                            trialSpawnerData.cooldownEndsAt = world.getGameTime() + (long)logic.getTargetCooldownLength();
                            trialSpawnerData.totalMobsSpawned = 0;
                            trialSpawnerData.nextMobSpawnsAt = 0L;
                            yield WAITING_FOR_REWARD_EJECTION;
                        }
                    } else if (trialSpawnerData.isReadyToSpawnNextMob(world, trialSpawnerConfig, i)) {
                        logic.spawnMob(world, pos).ifPresent(uuid -> {
                            trialSpawnerData.currentMobs.add(uuid);
                            trialSpawnerData.totalMobsSpawned++;
                            trialSpawnerData.nextMobSpawnsAt = world.getGameTime() + (long)trialSpawnerConfig.ticksBetweenSpawn();
                            trialSpawnerConfig.spawnPotentialsDefinition().getRandom(world.getRandom()).ifPresent(spawnData -> {
                                trialSpawnerData.nextSpawnData = Optional.of(spawnData.data());
                                logic.markUpdated();
                            });
                        });
                    }

                    yield this;
                }
            }
            case WAITING_FOR_REWARD_EJECTION -> {
                if (trialSpawnerData.isReadyToOpenShutter(world, 40.0F, logic.getTargetCooldownLength())) {
                    world.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_OPEN_SHUTTER, SoundSource.BLOCKS);
                    yield EJECTING_REWARD;
                } else {
                    yield this;
                }
            }
            case EJECTING_REWARD -> {
                if (!trialSpawnerData.isReadyToEjectItems(world, (float)TIME_BETWEEN_EACH_EJECTION, logic.getTargetCooldownLength())) {
                    yield this;
                } else if (trialSpawnerData.detectedPlayers.isEmpty()) {
                    world.playSound(null, pos, SoundEvents.TRIAL_SPAWNER_CLOSE_SHUTTER, SoundSource.BLOCKS);
                    trialSpawnerData.ejectingLootTable = Optional.empty();
                    yield COOLDOWN;
                } else {
                    if (trialSpawnerData.ejectingLootTable.isEmpty()) {
                        trialSpawnerData.ejectingLootTable = trialSpawnerConfig.lootTablesToEject().getRandomValue(world.getRandom());
                    }

                    trialSpawnerData.ejectingLootTable.ifPresent(lootTable -> logic.ejectReward(world, pos, (ResourceKey<LootTable>)lootTable));
                    trialSpawnerData.detectedPlayers.remove(trialSpawnerData.detectedPlayers.iterator().next());
                    yield this;
                }
            }
            case COOLDOWN -> {
                trialSpawnerData.tryDetectPlayers(world, pos, logic);
                if (!trialSpawnerData.detectedPlayers.isEmpty()) {
                    trialSpawnerData.totalMobsSpawned = 0;
                    trialSpawnerData.nextMobSpawnsAt = 0L;
                    yield ACTIVE;
                } else if (trialSpawnerData.isCooldownFinished(world)) {
                    logic.removeOminous(world, pos);
                    trialSpawnerData.reset();
                    yield WAITING_FOR_PLAYERS;
                } else {
                    yield this;
                }
            }
        };
    }

    private void spawnOminousOminousItemSpawner(ServerLevel world, BlockPos pos, TrialSpawner logic) {
        TrialSpawnerData trialSpawnerData = logic.getData();
        TrialSpawnerConfig trialSpawnerConfig = logic.getConfig();
        ItemStack itemStack = trialSpawnerData.getDispensingItems(world, trialSpawnerConfig, pos).getRandomValue(world.random).orElse(ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            if (this.timeToSpawnItemSpawner(world, trialSpawnerData)) {
                calculatePositionToSpawnSpawner(world, pos, logic, trialSpawnerData).ifPresent(posx -> {
                    OminousItemSpawner ominousItemSpawner = OminousItemSpawner.create(world, itemStack);
                    ominousItemSpawner.moveTo(posx);
                    world.addFreshEntity(ominousItemSpawner);
                    float f = (world.getRandom().nextFloat() - world.getRandom().nextFloat()) * 0.2F + 1.0F;
                    world.playSound(null, BlockPos.containing(posx), SoundEvents.TRIAL_SPAWNER_SPAWN_ITEM_BEGIN, SoundSource.BLOCKS, 1.0F, f);
                    trialSpawnerData.cooldownEndsAt = world.getGameTime() + logic.getOminousConfig().ticksBetweenItemSpawners();
                });
            }
        }
    }

    private static Optional<Vec3> calculatePositionToSpawnSpawner(ServerLevel world, BlockPos pos, TrialSpawner logic, TrialSpawnerData data) {
        List<Player> list = data.detectedPlayers
            .stream()
            .map(world::getPlayerByUUID)
            .filter(Objects::nonNull)
            .filter(
                player -> !player.isCreative()
                        && !player.isSpectator()
                        && player.isAlive()
                        && player.distanceToSqr(pos.getCenter()) <= (double)Mth.square(logic.getRequiredPlayerRange())
            )
            .toList();
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            Entity entity = selectEntityToSpawnItemAbove(list, data.currentMobs, logic, pos, world);
            return entity == null ? Optional.empty() : calculatePositionAbove(entity, world);
        }
    }

    private static Optional<Vec3> calculatePositionAbove(Entity entity, ServerLevel world) {
        Vec3 vec3 = entity.position();
        Vec3 vec32 = vec3.relative(Direction.UP, (double)(entity.getBbHeight() + 2.0F + (float)world.random.nextInt(4)));
        BlockHitResult blockHitResult = world.clip(new ClipContext(vec3, vec32, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 vec33 = blockHitResult.getBlockPos().getCenter().relative(Direction.DOWN, 1.0);
        BlockPos blockPos = BlockPos.containing(vec33);
        return !world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty() ? Optional.empty() : Optional.of(vec33);
    }

    @Nullable
    private static Entity selectEntityToSpawnItemAbove(List<Player> players, Set<UUID> entityUuids, TrialSpawner logic, BlockPos pos, ServerLevel world) {
        Stream<Entity> stream = entityUuids.stream()
            .map(world::getEntity)
            .filter(Objects::nonNull)
            .filter(entity -> entity.isAlive() && entity.distanceToSqr(pos.getCenter()) <= (double)Mth.square(logic.getRequiredPlayerRange()));
        List<? extends Entity> list = world.random.nextBoolean() ? stream.toList() : players;
        if (list.isEmpty()) {
            return null;
        } else {
            return list.size() == 1 ? list.getFirst() : Util.getRandom(list, world.random);
        }
    }

    private boolean timeToSpawnItemSpawner(ServerLevel world, TrialSpawnerData data) {
        return world.getGameTime() >= data.cooldownEndsAt;
    }

    public int lightLevel() {
        return this.lightLevel;
    }

    public double spinningMobSpeed() {
        return this.spinningMobSpeed;
    }

    public boolean hasSpinningMob() {
        return this.spinningMobSpeed >= 0.0;
    }

    public boolean isCapableOfSpawning() {
        return this.isCapableOfSpawning;
    }

    public void emitParticles(Level world, BlockPos pos, boolean ominous) {
        this.particleEmission.emit(world, world.getRandom(), pos, ominous);
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    static class LightLevel {
        private static final int UNLIT = 0;
        private static final int HALF_LIT = 4;
        private static final int LIT = 8;

        private LightLevel() {
        }
    }

    interface ParticleEmission {
        TrialSpawnerState.ParticleEmission NONE = (world, random, pos, ominous) -> {
        };
        TrialSpawnerState.ParticleEmission SMALL_FLAMES = (world, random, pos, ominous) -> {
            if (random.nextInt(2) == 0) {
                Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
                addParticle(ominous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME, vec3, world);
            }
        };
        TrialSpawnerState.ParticleEmission FLAMES_AND_SMOKE = (world, random, pos, ominous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 1.0F);
            addParticle(ParticleTypes.SMOKE, vec3, world);
            addParticle(ominous ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, vec3, world);
        };
        TrialSpawnerState.ParticleEmission SMOKE_INSIDE_AND_TOP_FACE = (world, random, pos, ominous) -> {
            Vec3 vec3 = pos.getCenter().offsetRandom(random, 0.9F);
            if (random.nextInt(3) == 0) {
                addParticle(ParticleTypes.SMOKE, vec3, world);
            }

            if (world.getGameTime() % 20L == 0L) {
                Vec3 vec32 = pos.getCenter().add(0.0, 0.5, 0.0);
                int i = world.getRandom().nextInt(4) + 20;

                for (int j = 0; j < i; j++) {
                    addParticle(ParticleTypes.SMOKE, vec32, world);
                }
            }
        };

        private static void addParticle(SimpleParticleType type, Vec3 pos, Level world) {
            world.addParticle(type, pos.x(), pos.y(), pos.z(), 0.0, 0.0, 0.0);
        }

        void emit(Level world, RandomSource random, BlockPos pos, boolean ominous);
    }

    static class SpinningMob {
        private static final double NONE = -1.0;
        private static final double SLOW = 200.0;
        private static final double FAST = 1000.0;

        private SpinningMob() {
        }
    }
}
