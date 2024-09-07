package net.minecraft.world.entity.monster.breeze;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.LongJumpUtil;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class LongJump extends Behavior<Breeze> {
    private static final int REQUIRED_AIR_BLOCKS_ABOVE = 4;
    private static final int JUMP_COOLDOWN_TICKS = 10;
    private static final int JUMP_COOLDOWN_WHEN_HURT_TICKS = 2;
    private static final int INHALING_DURATION_TICKS = Math.round(10.0F);
    private static final float MAX_JUMP_VELOCITY = 1.4F;
    private static final ObjectArrayList<Integer> ALLOWED_ANGLES = new ObjectArrayList<>(Lists.newArrayList(40, 55, 60, 75, 80));

    @VisibleForTesting
    public LongJump() {
        super(
            Map.of(
                MemoryModuleType.ATTACK_TARGET,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.BREEZE_JUMP_COOLDOWN,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_JUMP_INHALING,
                MemoryStatus.REGISTERED,
                MemoryModuleType.BREEZE_JUMP_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.BREEZE_SHOOT,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.BREEZE_LEAVING_WATER,
                MemoryStatus.REGISTERED
            ),
            200
        );
    }

    public static boolean canRun(ServerLevel world, Breeze breeze) {
        if (!breeze.onGround() && !breeze.isInWater()) {
            return false;
        } else if (Swim.shouldSwim(breeze)) {
            return false;
        } else if (breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_JUMP_TARGET, MemoryStatus.VALUE_PRESENT)) {
            return true;
        } else {
            LivingEntity livingEntity = breeze.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            if (livingEntity == null) {
                return false;
            } else if (outOfAggroRange(breeze, livingEntity)) {
                breeze.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return false;
            } else if (tooCloseForJump(breeze, livingEntity)) {
                return false;
            } else if (!canJumpFromCurrentPosition(world, breeze)) {
                return false;
            } else {
                BlockPos blockPos = snapToSurface(breeze, BreezeUtil.randomPointBehindTarget(livingEntity, breeze.getRandom()));
                if (blockPos == null) {
                    return false;
                } else {
                    BlockState blockState = world.getBlockState(blockPos.below());
                    if (breeze.getType().isBlockDangerous(blockState)) {
                        return false;
                    } else if (!BreezeUtil.hasLineOfSight(breeze, blockPos.getCenter()) && !BreezeUtil.hasLineOfSight(breeze, blockPos.above(4).getCenter())) {
                        return false;
                    } else {
                        breeze.getBrain().setMemory(MemoryModuleType.BREEZE_JUMP_TARGET, blockPos);
                        return true;
                    }
                }
            }
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverLevel, Breeze breeze) {
        return canRun(serverLevel, breeze);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Breeze entity, long time) {
        return entity.getPose() != Pose.STANDING && !entity.getBrain().hasMemoryValue(MemoryModuleType.BREEZE_JUMP_COOLDOWN);
    }

    @Override
    protected void start(ServerLevel serverLevel, Breeze breeze, long l) {
        if (breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_JUMP_INHALING, MemoryStatus.VALUE_ABSENT)) {
            breeze.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_INHALING, Unit.INSTANCE, (long)INHALING_DURATION_TICKS);
        }

        breeze.setPose(Pose.INHALING);
        serverLevel.playSound(null, breeze, SoundEvents.BREEZE_CHARGE, SoundSource.HOSTILE, 1.0F, 1.0F);
        breeze.getBrain()
            .getMemory(MemoryModuleType.BREEZE_JUMP_TARGET)
            .ifPresent(jumpTarget -> breeze.lookAt(EntityAnchorArgument.Anchor.EYES, jumpTarget.getCenter()));
    }

    @Override
    protected void tick(ServerLevel world, Breeze entity, long time) {
        boolean bl = entity.isInWater();
        if (!bl && entity.getBrain().checkMemory(MemoryModuleType.BREEZE_LEAVING_WATER, MemoryStatus.VALUE_PRESENT)) {
            entity.getBrain().eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
        }

        if (isFinishedInhaling(entity)) {
            Vec3 vec3 = entity.getBrain()
                .getMemory(MemoryModuleType.BREEZE_JUMP_TARGET)
                .flatMap(jumpTarget -> calculateOptimalJumpVector(entity, entity.getRandom(), Vec3.atBottomCenterOf(jumpTarget)))
                .orElse(null);
            if (vec3 == null) {
                entity.setPose(Pose.STANDING);
                return;
            }

            if (bl) {
                entity.getBrain().setMemory(MemoryModuleType.BREEZE_LEAVING_WATER, Unit.INSTANCE);
            }

            entity.playSound(SoundEvents.BREEZE_JUMP, 1.0F, 1.0F);
            entity.setPose(Pose.LONG_JUMPING);
            entity.setYRot(entity.yBodyRot);
            entity.setDiscardFriction(true);
            entity.setDeltaMovement(vec3);
        } else if (isFinishedJumping(entity)) {
            entity.playSound(SoundEvents.BREEZE_LAND, 1.0F, 1.0F);
            entity.setPose(Pose.STANDING);
            entity.setDiscardFriction(false);
            boolean bl2 = entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
            entity.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_JUMP_COOLDOWN, Unit.INSTANCE, bl2 ? 2L : 10L);
            entity.getBrain().setMemoryWithExpiry(MemoryModuleType.BREEZE_SHOOT, Unit.INSTANCE, 100L);
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Breeze breeze, long l) {
        if (breeze.getPose() == Pose.LONG_JUMPING || breeze.getPose() == Pose.INHALING) {
            breeze.setPose(Pose.STANDING);
        }

        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_TARGET);
        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_JUMP_INHALING);
        breeze.getBrain().eraseMemory(MemoryModuleType.BREEZE_LEAVING_WATER);
    }

    private static boolean isFinishedInhaling(Breeze breeze) {
        return breeze.getBrain().getMemory(MemoryModuleType.BREEZE_JUMP_INHALING).isEmpty() && breeze.getPose() == Pose.INHALING;
    }

    private static boolean isFinishedJumping(Breeze breeze) {
        boolean bl = breeze.getPose() == Pose.LONG_JUMPING;
        boolean bl2 = breeze.onGround();
        boolean bl3 = breeze.isInWater() && breeze.getBrain().checkMemory(MemoryModuleType.BREEZE_LEAVING_WATER, MemoryStatus.VALUE_ABSENT);
        return bl && (bl2 || bl3);
    }

    @Nullable
    private static BlockPos snapToSurface(LivingEntity breeze, Vec3 pos) {
        ClipContext clipContext = new ClipContext(pos, pos.relative(Direction.DOWN, 10.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, breeze);
        HitResult hitResult = breeze.level().clip(clipContext);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return BlockPos.containing(hitResult.getLocation()).above();
        } else {
            ClipContext clipContext2 = new ClipContext(pos, pos.relative(Direction.UP, 10.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, breeze);
            HitResult hitResult2 = breeze.level().clip(clipContext2);
            return hitResult2.getType() == HitResult.Type.BLOCK ? BlockPos.containing(hitResult2.getLocation()).above() : null;
        }
    }

    private static boolean outOfAggroRange(Breeze breeze, LivingEntity target) {
        return !target.closerThan(breeze, 24.0);
    }

    private static boolean tooCloseForJump(Breeze breeze, LivingEntity target) {
        return target.distanceTo(breeze) - 4.0F <= 0.0F;
    }

    private static boolean canJumpFromCurrentPosition(ServerLevel world, Breeze breeze) {
        BlockPos blockPos = breeze.blockPosition();

        for (int i = 1; i <= 4; i++) {
            BlockPos blockPos2 = blockPos.relative(Direction.UP, i);
            if (!world.getBlockState(blockPos2).isAir() && !world.getFluidState(blockPos2).is(FluidTags.WATER)) {
                return false;
            }
        }

        return true;
    }

    private static Optional<Vec3> calculateOptimalJumpVector(Breeze breeze, RandomSource random, Vec3 jumpTarget) {
        for (int i : Util.shuffledCopy(ALLOWED_ANGLES, random)) {
            Optional<Vec3> optional = LongJumpUtil.calculateJumpVectorForAngle(breeze, jumpTarget, 1.4F, i, false);
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }
}
