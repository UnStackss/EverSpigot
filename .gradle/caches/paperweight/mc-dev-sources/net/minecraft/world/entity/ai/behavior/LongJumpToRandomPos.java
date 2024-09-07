package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

public class LongJumpToRandomPos<E extends Mob> extends Behavior<E> {
    protected static final int FIND_JUMP_TRIES = 20;
    private static final int PREPARE_JUMP_DURATION = 40;
    protected static final int MIN_PATHFIND_DISTANCE_TO_VALID_JUMP = 8;
    private static final int TIME_OUT_DURATION = 200;
    private static final List<Integer> ALLOWED_ANGLES = Lists.newArrayList(65, 70, 75, 80);
    private final UniformInt timeBetweenLongJumps;
    protected final int maxLongJumpHeight;
    protected final int maxLongJumpWidth;
    protected final float maxJumpVelocityMultiplier;
    protected List<LongJumpToRandomPos.PossibleJump> jumpCandidates = Lists.newArrayList();
    protected Optional<Vec3> initialPosition = Optional.empty();
    @Nullable
    protected Vec3 chosenJump;
    protected int findJumpTries;
    protected long prepareJumpStart;
    private final Function<E, SoundEvent> getJumpSound;
    private final BiPredicate<E, BlockPos> acceptableLandingSpot;

    public LongJumpToRandomPos(UniformInt cooldownRange, int verticalRange, int horizontalRange, float maxRange, Function<E, SoundEvent> entityToSound) {
        this(cooldownRange, verticalRange, horizontalRange, maxRange, entityToSound, LongJumpToRandomPos::defaultAcceptableLandingSpot);
    }

    public static <E extends Mob> boolean defaultAcceptableLandingSpot(E entity, BlockPos pos) {
        Level level = entity.level();
        BlockPos blockPos = pos.below();
        return level.getBlockState(blockPos).isSolidRender(level, blockPos)
            && entity.getPathfindingMalus(WalkNodeEvaluator.getPathTypeStatic(entity, pos)) == 0.0F;
    }

    public LongJumpToRandomPos(
        UniformInt cooldownRange,
        int verticalRange,
        int horizontalRange,
        float maxRange,
        Function<E, SoundEvent> entityToSound,
        BiPredicate<E, BlockPos> jumpToPredicate
    ) {
        super(
            ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LONG_JUMP_MID_JUMP,
                MemoryStatus.VALUE_ABSENT
            ),
            200
        );
        this.timeBetweenLongJumps = cooldownRange;
        this.maxLongJumpHeight = verticalRange;
        this.maxLongJumpWidth = horizontalRange;
        this.maxJumpVelocityMultiplier = maxRange;
        this.getJumpSound = entityToSound;
        this.acceptableLandingSpot = jumpToPredicate;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
        boolean bl = entity.onGround() && !entity.isInWater() && !entity.isInLava() && !world.getBlockState(entity.blockPosition()).is(Blocks.HONEY_BLOCK);
        if (!bl) {
            entity.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(world.random) / 2);
        }

        return bl;
    }

    @Override
    protected boolean canStillUse(ServerLevel serverLevel, Mob mob, long l) {
        boolean bl = this.initialPosition.isPresent()
            && this.initialPosition.get().equals(mob.position())
            && this.findJumpTries > 0
            && !mob.isInWaterOrBubble()
            && (this.chosenJump != null || !this.jumpCandidates.isEmpty());
        if (!bl && mob.getBrain().getMemory(MemoryModuleType.LONG_JUMP_MID_JUMP).isEmpty()) {
            mob.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, this.timeBetweenLongJumps.sample(serverLevel.random) / 2);
            mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }

        return bl;
    }

    @Override
    protected void start(ServerLevel serverLevel, E mob, long l) {
        this.chosenJump = null;
        this.findJumpTries = 20;
        this.initialPosition = Optional.of(mob.position());
        BlockPos blockPos = mob.blockPosition();
        int i = blockPos.getX();
        int j = blockPos.getY();
        int k = blockPos.getZ();
        this.jumpCandidates = BlockPos.betweenClosedStream(
                i - this.maxLongJumpWidth,
                j - this.maxLongJumpHeight,
                k - this.maxLongJumpWidth,
                i + this.maxLongJumpWidth,
                j + this.maxLongJumpHeight,
                k + this.maxLongJumpWidth
            )
            .filter(blockPos2 -> !blockPos2.equals(blockPos))
            .map(blockPos2 -> new LongJumpToRandomPos.PossibleJump(blockPos2.immutable(), Mth.ceil(blockPos.distSqr(blockPos2))))
            .collect(Collectors.toCollection(Lists::newArrayList));
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        if (this.chosenJump != null) {
            if (time - this.prepareJumpStart >= 40L) {
                entity.setYRot(entity.yBodyRot);
                entity.setDiscardFriction(true);
                double d = this.chosenJump.length();
                double e = d + (double)entity.getJumpBoostPower();
                entity.setDeltaMovement(this.chosenJump.scale(e / d));
                entity.getBrain().setMemory(MemoryModuleType.LONG_JUMP_MID_JUMP, true);
                world.playSound(null, entity, this.getJumpSound.apply(entity), SoundSource.NEUTRAL, 1.0F, 1.0F);
            }
        } else {
            this.findJumpTries--;
            this.pickCandidate(world, entity, time);
        }
    }

    protected void pickCandidate(ServerLevel world, E entity, long time) {
        while (!this.jumpCandidates.isEmpty()) {
            Optional<LongJumpToRandomPos.PossibleJump> optional = this.getJumpCandidate(world);
            if (!optional.isEmpty()) {
                LongJumpToRandomPos.PossibleJump possibleJump = optional.get();
                BlockPos blockPos = possibleJump.getJumpTarget();
                if (this.isAcceptableLandingPosition(world, entity, blockPos)) {
                    Vec3 vec3 = Vec3.atCenterOf(blockPos);
                    Vec3 vec32 = this.calculateOptimalJumpVector(entity, vec3);
                    if (vec32 != null) {
                        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(blockPos));
                        PathNavigation pathNavigation = entity.getNavigation();
                        Path path = pathNavigation.createPath(blockPos, 0, 8);
                        if (path == null || !path.canReach()) {
                            this.chosenJump = vec32;
                            this.prepareJumpStart = time;
                            return;
                        }
                    }
                }
            }
        }
    }

    protected Optional<LongJumpToRandomPos.PossibleJump> getJumpCandidate(ServerLevel world) {
        Optional<LongJumpToRandomPos.PossibleJump> optional = WeightedRandom.getRandomItem(world.random, this.jumpCandidates);
        optional.ifPresent(this.jumpCandidates::remove);
        return optional;
    }

    private boolean isAcceptableLandingPosition(ServerLevel world, E entity, BlockPos pos) {
        BlockPos blockPos = entity.blockPosition();
        int i = blockPos.getX();
        int j = blockPos.getZ();
        return (i != pos.getX() || j != pos.getZ()) && this.acceptableLandingSpot.test(entity, pos);
    }

    @Nullable
    protected Vec3 calculateOptimalJumpVector(Mob entity, Vec3 targetPos) {
        List<Integer> list = Lists.newArrayList(ALLOWED_ANGLES);
        Collections.shuffle(list);
        float f = (float)(entity.getAttributeValue(Attributes.JUMP_STRENGTH) * (double)this.maxJumpVelocityMultiplier);

        for (int i : list) {
            Optional<Vec3> optional = LongJumpUtil.calculateJumpVectorForAngle(entity, targetPos, f, i, true);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return null;
    }

    public static class PossibleJump extends WeightedEntry.IntrusiveBase {
        private final BlockPos jumpTarget;

        public PossibleJump(BlockPos pos, int weight) {
            super(weight);
            this.jumpTarget = pos;
        }

        public BlockPos getJumpTarget() {
            return this.jumpTarget;
        }
    }
}
