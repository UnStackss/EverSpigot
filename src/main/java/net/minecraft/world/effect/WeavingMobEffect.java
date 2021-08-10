package net.minecraft.world.effect;

import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

class WeavingMobEffect extends MobEffect {
    private final ToIntFunction<RandomSource> maxCobwebs;

    protected WeavingMobEffect(MobEffectCategory category, int color, ToIntFunction<RandomSource> cobwebChanceFunction) {
        super(category, color, ParticleTypes.ITEM_COBWEB);
        this.maxCobwebs = cobwebChanceFunction;
    }

    @Override
    public void onMobRemoved(LivingEntity entity, int amplifier, Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED && (entity instanceof Player || entity.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))) {
            this.spawnCobwebsRandomlyAround(entity, entity.level(), entity.getRandom(), entity.getOnPos()); // Paper - Fire EntityChangeBlockEvent in more places
        }
    }

    private void spawnCobwebsRandomlyAround(LivingEntity entity, Level world, RandomSource random, BlockPos pos) { // Paper - Fire EntityChangeBlockEvent in more places
        Set<BlockPos> set = Sets.newHashSet();
        int i = this.maxCobwebs.applyAsInt(random);

        for (BlockPos blockPos : BlockPos.randomInCube(random, 15, pos, 1)) {
            BlockPos blockPos2 = blockPos.below();
            if (!set.contains(blockPos)
                && world.getBlockState(blockPos).canBeReplaced()
                && world.getBlockState(blockPos2).isFaceSturdy(world, blockPos2, Direction.UP)) {
                set.add(blockPos.immutable());
                if (set.size() >= i) {
                    break;
                }
            }
        }

        for (BlockPos blockPos3 : set) {
            if (!org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entity, blockPos3, Blocks.COBWEB.defaultBlockState())) continue; // Paper - Fire EntityChangeBlockEvent in more places
            world.setBlock(blockPos3, Blocks.COBWEB.defaultBlockState(), 3);
            world.levelEvent(3018, blockPos3, 0);
        }
    }
}
