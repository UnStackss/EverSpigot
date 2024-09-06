package net.minecraft.world.entity.ai.behavior;

import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class TryLaySpawnOnWaterNearLand {

    public TryLaySpawnOnWaterNearLand() {}

    public static BehaviorControl<LivingEntity> create(Block frogSpawn) {
        return BehaviorBuilder.create((behaviorbuilder_b) -> {
            return behaviorbuilder_b.group(behaviorbuilder_b.absent(MemoryModuleType.ATTACK_TARGET), behaviorbuilder_b.present(MemoryModuleType.WALK_TARGET), behaviorbuilder_b.present(MemoryModuleType.IS_PREGNANT)).apply(behaviorbuilder_b, (memoryaccessor, memoryaccessor1, memoryaccessor2) -> {
                return (worldserver, entityliving, i) -> {
                    if (!entityliving.isInWater() && entityliving.onGround()) {
                        BlockPos blockposition = entityliving.blockPosition().below();
                        Iterator iterator = Direction.Plane.HORIZONTAL.iterator();

                        while (iterator.hasNext()) {
                            Direction enumdirection = (Direction) iterator.next();
                            BlockPos blockposition1 = blockposition.relative(enumdirection);

                            if (worldserver.getBlockState(blockposition1).getCollisionShape(worldserver, blockposition1).getFaceShape(Direction.UP).isEmpty() && worldserver.getFluidState(blockposition1).is((Fluid) Fluids.WATER)) {
                                BlockPos blockposition2 = blockposition1.above();

                                if (worldserver.getBlockState(blockposition2).isAir()) {
                                    BlockState iblockdata = frogSpawn.defaultBlockState();

                                    // CraftBukkit start
                                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entityliving, blockposition2, iblockdata)) {
                                        memoryaccessor2.erase();
                                        return true;
                                    }
                                    // CraftBukkit end
                                    worldserver.setBlock(blockposition2, iblockdata, 3);
                                    worldserver.gameEvent((Holder) GameEvent.BLOCK_PLACE, blockposition2, GameEvent.Context.of(entityliving, iblockdata));
                                    worldserver.playSound((Player) null, (Entity) entityliving, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
                                    memoryaccessor2.erase();
                                    return true;
                                }
                            }
                        }

                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}
