package net.minecraft.world.level;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class SimpleExplosionDamageCalculator extends ExplosionDamageCalculator {
    private final boolean explodesBlocks;
    private final boolean damagesEntities;
    private final Optional<Float> knockbackMultiplier;
    private final Optional<HolderSet<Block>> immuneBlocks;

    public SimpleExplosionDamageCalculator(
        boolean destroyBlocks, boolean damageEntities, Optional<Float> knockbackModifier, Optional<HolderSet<Block>> immuneBlocks
    ) {
        this.explodesBlocks = destroyBlocks;
        this.damagesEntities = damageEntities;
        this.knockbackMultiplier = knockbackModifier;
        this.immuneBlocks = immuneBlocks;
    }

    @Override
    public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (this.immuneBlocks.isPresent()) {
            return blockState.is(this.immuneBlocks.get()) ? Optional.of(3600000.0F) : Optional.empty();
        } else {
            return super.getBlockExplosionResistance(explosion, world, pos, blockState, fluidState);
        }
    }

    @Override
    public boolean shouldBlockExplode(Explosion explosion, BlockGetter world, BlockPos pos, BlockState state, float power) {
        return this.explodesBlocks;
    }

    @Override
    public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
        return this.damagesEntities;
    }

    @Override
    public float getKnockbackMultiplier(Entity entity) {
        boolean var10000;
        label17: {
            if (entity instanceof Player player && player.getAbilities().flying) {
                var10000 = true;
                break label17;
            }

            var10000 = false;
        }

        boolean bl = var10000;
        return bl ? 0.0F : this.knockbackMultiplier.orElseGet(() -> super.getKnockbackMultiplier(entity));
    }
}
