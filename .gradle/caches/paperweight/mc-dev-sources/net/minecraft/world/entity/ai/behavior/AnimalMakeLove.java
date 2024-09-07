package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;

public class AnimalMakeLove extends Behavior<Animal> {
    private static final int BREED_RANGE = 3;
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 110;
    private final EntityType<? extends Animal> partnerType;
    private final float speedModifier;
    private final int closeEnoughDistance;
    private static final int DEFAULT_CLOSE_ENOUGH_DISTANCE = 2;
    private long spawnChildAtTime;

    public AnimalMakeLove(EntityType<? extends Animal> targetType) {
        this(targetType, 1.0F, 2);
    }

    public AnimalMakeLove(EntityType<? extends Animal> targetType, float speed, int approachDistance) {
        super(
            ImmutableMap.of(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.BREED_TARGET,
                MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.IS_PANICKING,
                MemoryStatus.VALUE_ABSENT
            ),
            110
        );
        this.partnerType = targetType;
        this.speedModifier = speed;
        this.closeEnoughDistance = approachDistance;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Animal entity) {
        return entity.isInLove() && this.findValidBreedPartner(entity).isPresent();
    }

    @Override
    protected void start(ServerLevel serverLevel, Animal animal, long l) {
        Animal animal2 = this.findValidBreedPartner(animal).get();
        animal.getBrain().setMemory(MemoryModuleType.BREED_TARGET, animal2);
        animal2.getBrain().setMemory(MemoryModuleType.BREED_TARGET, animal);
        BehaviorUtils.lockGazeAndWalkToEachOther(animal, animal2, this.speedModifier, this.closeEnoughDistance);
        int i = 60 + animal.getRandom().nextInt(50);
        this.spawnChildAtTime = l + (long)i;
    }

    @Override
    protected boolean canStillUse(ServerLevel serverLevel, Animal animal, long l) {
        if (!this.hasBreedTargetOfRightType(animal)) {
            return false;
        } else {
            Animal animal2 = this.getBreedTarget(animal);
            return animal2.isAlive()
                && animal.canMate(animal2)
                && BehaviorUtils.entityIsVisible(animal.getBrain(), animal2)
                && l <= this.spawnChildAtTime
                && !animal.isPanicking()
                && !animal2.isPanicking();
        }
    }

    @Override
    protected void tick(ServerLevel world, Animal entity, long time) {
        Animal animal = this.getBreedTarget(entity);
        BehaviorUtils.lockGazeAndWalkToEachOther(entity, animal, this.speedModifier, this.closeEnoughDistance);
        if (entity.closerThan(animal, 3.0)) {
            if (time >= this.spawnChildAtTime) {
                entity.spawnChildFromBreeding(world, animal);
                entity.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
                animal.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
            }
        }
    }

    @Override
    protected void stop(ServerLevel serverLevel, Animal animal, long l) {
        animal.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        animal.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        animal.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        this.spawnChildAtTime = 0L;
    }

    private Animal getBreedTarget(Animal animal) {
        return (Animal)animal.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get();
    }

    private boolean hasBreedTargetOfRightType(Animal animal) {
        Brain<?> brain = animal.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.BREED_TARGET) && brain.getMemory(MemoryModuleType.BREED_TARGET).get().getType() == this.partnerType;
    }

    private Optional<? extends Animal> findValidBreedPartner(Animal animal) {
        return animal.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().findClosest(entity -> {
            if (entity.getType() == this.partnerType && entity instanceof Animal animal2 && animal.canMate(animal2) && !animal2.isPanicking()) {
                return true;
            }

            return false;
        }).map(Animal.class::cast);
    }
}
