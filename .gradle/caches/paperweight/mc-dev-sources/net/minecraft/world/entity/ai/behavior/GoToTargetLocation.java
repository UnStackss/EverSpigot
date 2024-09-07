package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class GoToTargetLocation {
    private static BlockPos getNearbyPos(Mob mob, BlockPos pos) {
        RandomSource randomSource = mob.level().random;
        return pos.offset(getRandomOffset(randomSource), 0, getRandomOffset(randomSource));
    }

    private static int getRandomOffset(RandomSource random) {
        return random.nextInt(3) - 1;
    }

    public static <E extends Mob> OneShot<E> create(MemoryModuleType<BlockPos> posModule, int completionRange, float speed) {
        return BehaviorBuilder.create(
            context -> context.group(
                        context.present(posModule),
                        context.absent(MemoryModuleType.ATTACK_TARGET),
                        context.absent(MemoryModuleType.WALK_TARGET),
                        context.registered(MemoryModuleType.LOOK_TARGET)
                    )
                    .apply(context, (pos, attackTarget, walkTarget, lookTarget) -> (world, entity, time) -> {
                            BlockPos blockPos = context.get(pos);
                            boolean bl = blockPos.closerThan(entity.blockPosition(), (double)completionRange);
                            if (!bl) {
                                BehaviorUtils.setWalkAndLookTargetMemories(entity, getNearbyPos(entity, blockPos), speed, completionRange);
                            }

                            return true;
                        })
        );
    }
}
