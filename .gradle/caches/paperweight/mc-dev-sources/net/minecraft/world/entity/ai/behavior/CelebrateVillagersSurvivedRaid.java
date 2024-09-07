package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

public class CelebrateVillagersSurvivedRaid extends Behavior<Villager> {
    @Nullable
    private Raid currentRaid;

    public CelebrateVillagersSurvivedRaid(int minRunTime, int maxRunTime) {
        super(ImmutableMap.of(), minRunTime, maxRunTime);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Villager entity) {
        BlockPos blockPos = entity.blockPosition();
        this.currentRaid = world.getRaidAt(blockPos);
        return this.currentRaid != null && this.currentRaid.isVictory() && MoveToSkySeeingSpot.hasNoBlocksAbove(world, entity, blockPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Villager entity, long time) {
        return this.currentRaid != null && !this.currentRaid.isStopped();
    }

    @Override
    protected void stop(ServerLevel world, Villager entity, long time) {
        this.currentRaid = null;
        entity.getBrain().updateActivityFromSchedule(world.getDayTime(), world.getGameTime());
    }

    @Override
    protected void tick(ServerLevel world, Villager entity, long time) {
        RandomSource randomSource = entity.getRandom();
        if (randomSource.nextInt(100) == 0) {
            entity.playCelebrateSound();
        }

        if (randomSource.nextInt(200) == 0 && MoveToSkySeeingSpot.hasNoBlocksAbove(world, entity, entity.blockPosition())) {
            DyeColor dyeColor = Util.getRandom(DyeColor.values(), randomSource);
            int i = randomSource.nextInt(3);
            ItemStack itemStack = this.getFirework(dyeColor, i);
            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(
                entity.level(), entity, entity.getX(), entity.getEyeY(), entity.getZ(), itemStack
            );
            entity.level().addFreshEntity(fireworkRocketEntity);
        }
    }

    private ItemStack getFirework(DyeColor color, int flight) {
        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
        itemStack.set(
            DataComponents.FIREWORKS,
            new Fireworks(
                (byte)flight, List.of(new FireworkExplosion(FireworkExplosion.Shape.BURST, IntList.of(color.getFireworkColor()), IntList.of(), false, false))
            )
        );
        return itemStack;
    }
}
