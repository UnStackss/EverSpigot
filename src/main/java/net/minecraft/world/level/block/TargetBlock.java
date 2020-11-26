package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class TargetBlock extends Block {
    public static final MapCodec<TargetBlock> CODEC = simpleCodec(TargetBlock::new);
    private static final IntegerProperty OUTPUT_POWER = BlockStateProperties.POWER;
    private static final int ACTIVATION_TICKS_ARROWS = 20;
    private static final int ACTIVATION_TICKS_OTHER = 8;

    @Override
    public MapCodec<TargetBlock> codec() {
        return CODEC;
    }

    public TargetBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(OUTPUT_POWER, Integer.valueOf(0)));
    }

    @Override
    protected void onProjectileHit(Level world, BlockState state, BlockHitResult hit, Projectile projectile) {
        int i = updateRedstoneOutput(world, state, hit, projectile);
        // Paper start - Add TargetHitEvent
    }
    private static void awardTargetHitCriteria(Projectile projectile, BlockHitResult hit, int i) {
        // Paper end - Add TargetHitEvent
        if (projectile.getOwner() instanceof ServerPlayer serverPlayer) {
            serverPlayer.awardStat(Stats.TARGET_HIT);
            CriteriaTriggers.TARGET_BLOCK_HIT.trigger(serverPlayer, projectile, hit.getLocation(), i);
        }
    }

    private static int updateRedstoneOutput(LevelAccessor world, BlockState state, BlockHitResult hitResult, Entity entity) {
        int i = getRedstoneStrength(hitResult, hitResult.getLocation());
        int j = entity instanceof AbstractArrow ? 20 : 8;
        // Paper start - Add TargetHitEvent
        boolean shouldAward = false;
        if (entity instanceof Projectile) {
            final Projectile projectile = (Projectile) entity;
            final org.bukkit.craftbukkit.block.CraftBlock craftBlock = org.bukkit.craftbukkit.block.CraftBlock.at(world, hitResult.getBlockPos());
            final org.bukkit.block.BlockFace blockFace = org.bukkit.craftbukkit.block.CraftBlock.notchToBlockFace(hitResult.getDirection());
            final io.papermc.paper.event.block.TargetHitEvent targetHitEvent = new io.papermc.paper.event.block.TargetHitEvent((org.bukkit.entity.Projectile) projectile.getBukkitEntity(), craftBlock, blockFace, i);
            if (targetHitEvent.callEvent()) {
                i = targetHitEvent.getSignalStrength();
                shouldAward = true;
            } else {
                return i;
            }
        }
        // Paper end - Add TargetHitEvent
        if (!world.getBlockTicks().hasScheduledTick(hitResult.getBlockPos(), state.getBlock())) {
            setOutputPower(world, state, i, hitResult.getBlockPos(), j);
        }

        // Paper start - Award Hit Criteria after Block Update
        if (shouldAward) {
            awardTargetHitCriteria((Projectile) entity, hitResult, i);
        }
        // Paper end - Award Hit Criteria after Block Update

        return i;
    }

    private static int getRedstoneStrength(BlockHitResult hitResult, Vec3 pos) {
        Direction direction = hitResult.getDirection();
        double d = Math.abs(Mth.frac(pos.x) - 0.5);
        double e = Math.abs(Mth.frac(pos.y) - 0.5);
        double f = Math.abs(Mth.frac(pos.z) - 0.5);
        Direction.Axis axis = direction.getAxis();
        double g;
        if (axis == Direction.Axis.Y) {
            g = Math.max(d, f);
        } else if (axis == Direction.Axis.Z) {
            g = Math.max(d, e);
        } else {
            g = Math.max(e, f);
        }

        return Math.max(1, Mth.ceil(15.0 * Mth.clamp((0.5 - g) / 0.5, 0.0, 1.0)));
    }

    private static void setOutputPower(LevelAccessor world, BlockState state, int power, BlockPos pos, int delay) {
        world.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(power)), 3);
        world.scheduleTick(pos, state.getBlock(), delay);
    }

    @Override
    protected void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (state.getValue(OUTPUT_POWER) != 0) {
            world.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(0)), 3);
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter world, BlockPos pos, Direction direction) {
        return state.getValue(OUTPUT_POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OUTPUT_POWER);
    }

    @Override
    protected void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean notify) {
        if (!world.isClientSide() && !state.is(oldState.getBlock())) {
            if (state.getValue(OUTPUT_POWER) > 0 && !world.getBlockTicks().hasScheduledTick(pos, this)) {
                world.setBlock(pos, state.setValue(OUTPUT_POWER, Integer.valueOf(0)), 18);
            }
        }
    }
}
