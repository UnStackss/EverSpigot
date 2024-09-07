package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;

public class YieldJobSite {
    public static BehaviorControl<Villager> create(float speed) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(MemoryModuleType.POTENTIAL_JOB_SITE),
                        context.absent(MemoryModuleType.JOB_SITE),
                        context.present(MemoryModuleType.NEAREST_LIVING_ENTITIES),
                        context.registered(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET)
                    )
                    .apply(
                        context,
                        (potentialJobSite, jobSite, mobs, walkTarget, lookTarget) -> (world, entity, time) -> {
                                if (entity.isBaby()) {
                                    return false;
                                } else if (entity.getVillagerData().getProfession() != VillagerProfession.NONE) {
                                    return false;
                                } else {
                                    BlockPos blockPos = context.<GlobalPos>get(potentialJobSite).pos();
                                    Optional<Holder<PoiType>> optional = world.getPoiManager().getType(blockPos);
                                    if (optional.isEmpty()) {
                                        return true;
                                    } else {
                                        context.<List<LivingEntity>>get(mobs)
                                            .stream()
                                            .filter(mob -> mob instanceof Villager && mob != entity)
                                            .map(villager -> (Villager)villager)
                                            .filter(LivingEntity::isAlive)
                                            .filter(villager -> nearbyWantsJobsite(optional.get(), villager, blockPos))
                                            .findFirst()
                                            .ifPresent(villager -> {
                                                walkTarget.erase();
                                                lookTarget.erase();
                                                potentialJobSite.erase();
                                                if (villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
                                                    BehaviorUtils.setWalkAndLookTargetMemories(villager, blockPos, speed, 1);
                                                    villager.getBrain()
                                                        .setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(world.dimension(), blockPos));
                                                    DebugPackets.sendPoiTicketCountPacket(world, blockPos);
                                                }
                                            });
                                        return true;
                                    }
                                }
                            }
                    )
        );
    }

    private static boolean nearbyWantsJobsite(Holder<PoiType> poiType, Villager villager, BlockPos pos) {
        boolean bl = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent();
        if (bl) {
            return false;
        } else {
            Optional<GlobalPos> optional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
            VillagerProfession villagerProfession = villager.getVillagerData().getProfession();
            if (villagerProfession.heldJobSite().test(poiType)) {
                return optional.isEmpty() ? canReachPos(villager, pos, poiType.value()) : optional.get().pos().equals(pos);
            } else {
                return false;
            }
        }
    }

    private static boolean canReachPos(PathfinderMob entity, BlockPos pos, PoiType poiType) {
        Path path = entity.getNavigation().createPath(pos, poiType.validRange());
        return path != null && path.canReach();
    }
}
