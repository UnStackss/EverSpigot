package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.breeze.Breeze;

public class BreezeAttackEntitySensor extends NearestLivingEntitySensor<Breeze> {
    public static final int BREEZE_SENSOR_RADIUS = 24;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.copyOf(Iterables.concat(super.requires(), List.of(MemoryModuleType.NEAREST_ATTACKABLE)));
    }

    @Override
    protected void doTick(ServerLevel world, Breeze entity) {
        super.doTick(world, entity);
        entity.getBrain()
            .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
            .stream()
            .flatMap(Collection::stream)
            .filter(EntitySelector.NO_CREATIVE_OR_SPECTATOR)
            .filter(livingEntity -> Sensor.isEntityAttackable(entity, livingEntity))
            .findFirst()
            .ifPresentOrElse(
                livingEntity -> entity.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, livingEntity),
                () -> entity.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE)
            );
    }

    @Override
    protected int radiusXZ() {
        return 24;
    }

    @Override
    protected int radiusY() {
        return 24;
    }
}
