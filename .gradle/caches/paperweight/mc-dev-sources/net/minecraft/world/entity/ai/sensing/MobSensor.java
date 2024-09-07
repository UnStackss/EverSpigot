package net.minecraft.world.entity.ai.sensing;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class MobSensor<T extends LivingEntity> extends Sensor<T> {
    private final BiPredicate<T, LivingEntity> mobTest;
    private final Predicate<T> readyTest;
    private final MemoryModuleType<Boolean> toSet;
    private final int memoryTimeToLive;

    public MobSensor(
        int senseInterval,
        BiPredicate<T, LivingEntity> threateningEntityPredicate,
        Predicate<T> canRollUpPredicate,
        MemoryModuleType<Boolean> memoryModuleType,
        int expiry
    ) {
        super(senseInterval);
        this.mobTest = threateningEntityPredicate;
        this.readyTest = canRollUpPredicate;
        this.toSet = memoryModuleType;
        this.memoryTimeToLive = expiry;
    }

    @Override
    protected void doTick(ServerLevel world, T entity) {
        if (!this.readyTest.test(entity)) {
            this.clearMemory(entity);
        } else {
            this.checkForMobsNearby(entity);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES);
    }

    public void checkForMobsNearby(T entity) {
        Optional<List<LivingEntity>> optional = entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (!optional.isEmpty()) {
            boolean bl = optional.get().stream().anyMatch(threat -> this.mobTest.test(entity, threat));
            if (bl) {
                this.mobDetected(entity);
            }
        }
    }

    public void mobDetected(T entity) {
        entity.getBrain().setMemoryWithExpiry(this.toSet, true, (long)this.memoryTimeToLive);
    }

    public void clearMemory(T entity) {
        entity.getBrain().eraseMemory(this.toSet);
    }
}
