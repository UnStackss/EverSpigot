package net.minecraft.world.entity.animal.frog;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class ShootTongue extends Behavior<Frog> {

    public static final int TIME_OUT_DURATION = 100;
    public static final int CATCH_ANIMATION_DURATION = 6;
    public static final int TONGUE_ANIMATION_DURATION = 10;
    private static final float EATING_DISTANCE = 1.75F;
    private static final float EATING_MOVEMENT_FACTOR = 0.75F;
    public static final int UNREACHABLE_TONGUE_TARGETS_COOLDOWN_DURATION = 100;
    public static final int MAX_UNREACHBLE_TONGUE_TARGETS_IN_MEMORY = 5;
    private int eatAnimationTimer;
    private int calculatePathCounter;
    private final SoundEvent tongueSound;
    private final SoundEvent eatSound;
    private Vec3 itemSpawnPos;
    private ShootTongue.State state;

    public ShootTongue(SoundEvent tongueSound, SoundEvent eatSound) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), 100);
        this.state = ShootTongue.State.DONE;
        this.tongueSound = tongueSound;
        this.eatSound = eatSound;
    }

    protected boolean checkExtraStartConditions(ServerLevel world, Frog entity) {
        LivingEntity entityliving = (LivingEntity) entity.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        boolean flag = this.canPathfindToTarget(entity, entityliving);

        if (!flag) {
            entity.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            this.addUnreachableTargetToMemory(entity, entityliving);
        }

        return flag && entity.getPose() != Pose.CROAKING && Frog.canEat(entityliving);
    }

    protected boolean canStillUse(ServerLevel world, Frog entity, long time) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && this.state != ShootTongue.State.DONE && !entity.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    protected void start(ServerLevel worldserver, Frog frog, long i) {
        LivingEntity entityliving = (LivingEntity) frog.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();

        BehaviorUtils.lookAtEntity(frog, entityliving);
        frog.setTongueTarget(entityliving);
        frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(entityliving.position(), 2.0F, 0))); // CraftBukkit - decompile error
        this.calculatePathCounter = 10;
        this.state = ShootTongue.State.MOVE_TO_TARGET;
    }

    protected void stop(ServerLevel worldserver, Frog frog, long i) {
        frog.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        frog.eraseTongueTarget();
        frog.setPose(Pose.STANDING);
    }

    private void eatEntity(ServerLevel world, Frog frog) {
        world.playSound((Player) null, (Entity) frog, this.eatSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
        Optional<Entity> optional = frog.getTongueTarget();

        if (optional.isPresent()) {
            Entity entity = (Entity) optional.get();

            if (entity.isAlive()) {
                frog.doHurtTarget(entity);
                if (!entity.isAlive()) {
                    entity.remove(Entity.RemovalReason.KILLED, EntityRemoveEvent.Cause.DEATH); // CraftBukkit - add Bukkit remove cause
                }
            }
        }

    }

    protected void tick(ServerLevel worldserver, Frog frog, long i) {
        LivingEntity entityliving = (LivingEntity) frog.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();

        frog.setTongueTarget(entityliving);
        switch (this.state.ordinal()) {
            case 0:
                if (entityliving.distanceTo(frog) < 1.75F) {
                    worldserver.playSound((Player) null, (Entity) frog, this.tongueSound, SoundSource.NEUTRAL, 2.0F, 1.0F);
                    frog.setPose(Pose.USING_TONGUE);
                    entityliving.setDeltaMovement(entityliving.position().vectorTo(frog.position()).normalize().scale(0.75D));
                    this.itemSpawnPos = entityliving.position();
                    this.eatAnimationTimer = 0;
                    this.state = ShootTongue.State.CATCH_ANIMATION;
                } else if (this.calculatePathCounter <= 0) {
                    frog.getBrain().setMemory(MemoryModuleType.WALK_TARGET, (new WalkTarget(entityliving.position(), 2.0F, 0))); // CraftBukkit - decompile error
                    this.calculatePathCounter = 10;
                } else {
                    --this.calculatePathCounter;
                }
                break;
            case 1:
                if (this.eatAnimationTimer++ >= 6) {
                    this.state = ShootTongue.State.EAT_ANIMATION;
                    this.eatEntity(worldserver, frog);
                }
                break;
            case 2:
                if (this.eatAnimationTimer >= 10) {
                    this.state = ShootTongue.State.DONE;
                } else {
                    ++this.eatAnimationTimer;
                }
            case 3:
        }

    }

    private boolean canPathfindToTarget(Frog entity, LivingEntity target) {
        Path pathentity = entity.getNavigation().createPath((Entity) target, 0);

        return pathentity != null && pathentity.getDistToTarget() < 1.75F;
    }

    private void addUnreachableTargetToMemory(Frog entity, LivingEntity target) {
        List<UUID> list = (List) entity.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);
        boolean flag = !list.contains(target.getUUID());

        if (list.size() == 5 && flag) {
            list.remove(0);
        }

        if (flag) {
            list.add(target.getUUID());
        }

        entity.getBrain().setMemoryWithExpiry(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS, list, 100L);
    }

    private static enum State {

        MOVE_TO_TARGET, CATCH_ANIMATION, EAT_ANIMATION, DONE;

        private State() {}
    }
}
