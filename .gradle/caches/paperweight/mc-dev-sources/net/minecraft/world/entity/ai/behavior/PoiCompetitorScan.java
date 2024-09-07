package net.minecraft.world.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public class PoiCompetitorScan {
    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(
            context -> context.group(context.present(MemoryModuleType.JOB_SITE), context.present(MemoryModuleType.NEAREST_LIVING_ENTITIES))
                    .apply(
                        context,
                        (jobSite, mobs) -> (world, entity, time) -> {
                                GlobalPos globalPos = context.get(jobSite);
                                world.getPoiManager()
                                    .getType(globalPos.pos())
                                    .ifPresent(
                                        poiType -> context.<List<LivingEntity>>get(mobs)
                                                .stream()
                                                .filter(mob -> mob instanceof Villager && mob != entity)
                                                .map(villagerx -> (Villager)villagerx)
                                                .filter(LivingEntity::isAlive)
                                                .filter(villagerx -> competesForSameJobsite(globalPos, poiType, villagerx))
                                                .reduce(entity, PoiCompetitorScan::selectWinner)
                                    );
                                return true;
                            }
                    )
        );
    }

    private static Villager selectWinner(Villager first, Villager second) {
        Villager villager;
        Villager villager2;
        if (first.getVillagerXp() > second.getVillagerXp()) {
            villager = first;
            villager2 = second;
        } else {
            villager = second;
            villager2 = first;
        }

        villager2.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        return villager;
    }

    private static boolean competesForSameJobsite(GlobalPos pos, Holder<PoiType> poiType, Villager villager) {
        Optional<GlobalPos> optional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        return optional.isPresent() && pos.equals(optional.get()) && hasMatchingProfession(poiType, villager.getVillagerData().getProfession());
    }

    private static boolean hasMatchingProfession(Holder<PoiType> poiType, VillagerProfession profession) {
        return profession.heldJobSite().test(poiType);
    }
}
