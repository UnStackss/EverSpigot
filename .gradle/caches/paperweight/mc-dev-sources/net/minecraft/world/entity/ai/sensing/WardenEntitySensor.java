package net.minecraft.world.entity.ai.sensing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;

public class WardenEntitySensor extends NearestLivingEntitySensor<Warden> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.copyOf(Iterables.concat(super.requires(), List.of(MemoryModuleType.NEAREST_ATTACKABLE)));
    }

    @Override
    protected void doTick(ServerLevel world, Warden entity) {
        super.doTick(world, entity);
        getClosest(entity, entityx -> entityx.getType() == EntityType.PLAYER)
            .or(() -> getClosest(entity, entityx -> entityx.getType() != EntityType.PLAYER))
            .ifPresentOrElse(
                entityx -> entity.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, entityx),
                () -> entity.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE)
            );
    }

    private static Optional<LivingEntity> getClosest(Warden warden, Predicate<LivingEntity> targetPredicate) {
        return warden.getBrain()
            .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
            .stream()
            .flatMap(Collection::stream)
            .filter(warden::canTargetEntity)
            .filter(targetPredicate)
            .findFirst();
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
