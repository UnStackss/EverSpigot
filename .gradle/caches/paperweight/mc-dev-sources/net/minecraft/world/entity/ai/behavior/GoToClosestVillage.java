package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public class GoToClosestVillage {
    public static BehaviorControl<Villager> create(float speed, int completionRange) {
        return BehaviorBuilder.create(
            context -> context.group(context.absent(MemoryModuleType.WALK_TARGET)).apply(context, walkTarget -> (world, entity, time) -> {
                        if (world.isVillage(entity.blockPosition())) {
                            return false;
                        } else {
                            PoiManager poiManager = world.getPoiManager();
                            int j = poiManager.sectionsToVillage(SectionPos.of(entity.blockPosition()));
                            Vec3 vec3 = null;

                            for (int k = 0; k < 5; k++) {
                                Vec3 vec32 = LandRandomPos.getPos(entity, 15, 7, pos -> (double)(-poiManager.sectionsToVillage(SectionPos.of(pos))));
                                if (vec32 != null) {
                                    int l = poiManager.sectionsToVillage(SectionPos.of(BlockPos.containing(vec32)));
                                    if (l < j) {
                                        vec3 = vec32;
                                        break;
                                    }

                                    if (l == j) {
                                        vec3 = vec32;
                                    }
                                }
                            }

                            if (vec3 != null) {
                                walkTarget.set(new WalkTarget(vec3, speed, completionRange));
                            }

                            return true;
                        }
                    })
        );
    }
}
