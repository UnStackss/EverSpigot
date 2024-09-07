package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {
    private static final int TICKS_UNTIL_TIMEOUT = 1200;
    final float speedModifier;

    public GoToPotentialJobSite(float speed) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), 1200);
        this.speedModifier = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        return entity.getBrain()
            .getActiveNonCoreActivity()
            .map(activity -> activity == Activity.IDLE || activity == Activity.WORK || activity == Activity.PLAY)
            .orElse(true);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Villager entity, long time) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    @Override
    protected void tick(ServerLevel serverLevel, Villager villager, long l) {
        BehaviorUtils.setWalkAndLookTargetMemories(
            villager, villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(), this.speedModifier, 1
        );
    }

    @Override
    protected void stop(ServerLevel serverLevel, Villager villager, long l) {
        Optional<GlobalPos> optional = villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        optional.ifPresent(pos -> {
            BlockPos blockPos = pos.pos();
            ServerLevel serverLevel2 = serverLevel.getServer().getLevel(pos.dimension());
            if (serverLevel2 != null) {
                PoiManager poiManager = serverLevel2.getPoiManager();
                if (poiManager.exists(blockPos, poiType -> true)) {
                    poiManager.release(blockPos);
                }

                DebugPackets.sendPoiTicketCountPacket(serverLevel, blockPos);
            }
        });
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }
}
