package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

public record ReplaceBlock(Vec3i offset, Optional<BlockPredicate> predicate, BlockStateProvider blockState, Optional<Holder<GameEvent>> triggerGameEvent) implements EnchantmentEntityEffect {

    public static final MapCodec<ReplaceBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(Vec3i.CODEC.optionalFieldOf("offset", Vec3i.ZERO).forGetter(ReplaceBlock::offset), BlockPredicate.CODEC.optionalFieldOf("predicate").forGetter(ReplaceBlock::predicate), BlockStateProvider.CODEC.fieldOf("block_state").forGetter(ReplaceBlock::blockState), GameEvent.CODEC.optionalFieldOf("trigger_game_event").forGetter(ReplaceBlock::triggerGameEvent)).apply(instance, ReplaceBlock::new);
    });

    @Override
    public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
        BlockPos blockposition = BlockPos.containing(pos).offset(this.offset);

        if ((Boolean) this.predicate.map((blockpredicate) -> {
            return blockpredicate.test(world, blockposition);
        }).orElse(true) && org.bukkit.craftbukkit.event.CraftEventFactory.handleBlockFormEvent(world, blockposition, this.blockState.getState(user.getRandom(), blockposition), user)) { // CraftBukkit - Call EntityBlockFormEvent
            this.triggerGameEvent.ifPresent((holder) -> {
                world.gameEvent(user, holder, blockposition);
            });
        }

    }

    @Override
    public MapCodec<ReplaceBlock> codec() {
        return ReplaceBlock.CODEC;
    }
}
