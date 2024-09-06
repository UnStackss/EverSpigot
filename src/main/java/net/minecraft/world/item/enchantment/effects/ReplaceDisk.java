package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Iterator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceDisk(LevelBasedValue radius, LevelBasedValue height, Vec3i offset, Optional<BlockPredicate> predicate, BlockStateProvider blockState, Optional<Holder<GameEvent>> triggerGameEvent) implements EnchantmentEntityEffect {

    public static final MapCodec<ReplaceDisk> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LevelBasedValue.CODEC.fieldOf("radius").forGetter(ReplaceDisk::radius), LevelBasedValue.CODEC.fieldOf("height").forGetter(ReplaceDisk::height), Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceDisk::offset), BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceDisk::predicate), BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceDisk::blockState), GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceDisk::triggerGameEvent)).apply(instance, ReplaceDisk::new);
    });

    @Override
    public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
        BlockPos blockposition = BlockPos.containing(pos).offset(this.offset);
        RandomSource randomsource = user.getRandom();
        int j = (int) this.radius.calculate(level);
        int k = (int) this.height.calculate(level);
        Iterator iterator = BlockPos.betweenClosed(blockposition.offset(-j, 0, -j), blockposition.offset(j, Math.min(k - 1, 0), j)).iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition1 = (BlockPos) iterator.next();

            if (blockposition1.distToCenterSqr(pos.x(), (double) blockposition1.getY() + 0.5D, pos.z()) < (double) Mth.square(j) && (Boolean) this.predicate.map((blockpredicate) -> {
                return blockpredicate.test(world, blockposition1);
            }).orElse(true) && org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition1,  this.blockState.getState(randomsource, blockposition1), user)) { // CraftBukkit - Call EntityBlockFormEvent for Frost Walker
                this.triggerGameEvent.ifPresent((holder) -> {
                    world.gameEvent(user, holder, blockposition1);
                });
            }
        }

    }

    @Override
    public MapCodec<ReplaceDisk> codec() {
        return ReplaceDisk.CODEC;
    }
}
