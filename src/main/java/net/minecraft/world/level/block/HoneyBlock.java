package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HoneyBlock extends HalfTransparentBlock {
    public static final MapCodec<HoneyBlock> CODEC = simpleCodec(HoneyBlock::new);
    private static final double SLIDE_STARTS_WHEN_VERTICAL_SPEED_IS_AT_LEAST = 0.13;
    private static final double MIN_FALL_SPEED_TO_BE_CONSIDERED_SLIDING = 0.08;
    private static final double THROTTLE_SLIDE_SPEED_TO = 0.05;
    private static final int SLIDE_ADVANCEMENT_CHECK_INTERVAL = 20;
    protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

    @Override
    public MapCodec<HoneyBlock> codec() {
        return CODEC;
    }

    public HoneyBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    private static boolean doesEntityDoHoneyBlockSlideEffects(Entity entity) {
        return entity instanceof LivingEntity || entity instanceof AbstractMinecart || entity instanceof PrimedTnt || entity instanceof Boat;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void fallOn(Level world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.0F);
        if (!world.isClientSide) {
            world.broadcastEntityEvent(entity, (byte)54);
        }

        if (entity.causeFallDamage(fallDistance, 0.2F, world.damageSources().fall())) {
            entity.playSound(this.soundType.getFallSound(), this.soundType.getVolume() * 0.5F, this.soundType.getPitch() * 0.75F);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (this.isSlidingDown(pos, entity)) {
            this.maybeDoSlideAchievement(entity, pos);
            this.doSlideMovement(entity);
            this.maybeDoSlideEffects(world, entity);
        }

        super.entityInside(state, world, pos, entity);
    }

    private boolean isSlidingDown(BlockPos pos, Entity entity) {
        if (entity.onGround()) {
            return false;
        } else if (entity.getY() > (double)pos.getY() + 0.9375 - 1.0E-7) {
            return false;
        } else if (entity.getDeltaMovement().y >= -0.08) {
            return false;
        } else {
            double d = Math.abs((double)pos.getX() + 0.5 - entity.getX());
            double e = Math.abs((double)pos.getZ() + 0.5 - entity.getZ());
            double f = 0.4375 + (double)(entity.getBbWidth() / 2.0F);
            return d + 1.0E-7 > f || e + 1.0E-7 > f;
        }
    }

    private void maybeDoSlideAchievement(Entity entity, BlockPos pos) {
        if (entity instanceof ServerPlayer && entity.level().getGameTime() % 20L == 0L) {
            CriteriaTriggers.HONEY_BLOCK_SLIDE.trigger((ServerPlayer)entity, entity.level().getBlockState(pos));
        }
    }

    private void doSlideMovement(Entity entity) {
        Vec3 vec3 = entity.getDeltaMovement();
        if (vec3.y < -0.13) {
            double d = -0.05 / vec3.y;
            entity.setDeltaMovement(new Vec3(vec3.x * d, -0.05, vec3.z * d));
        } else {
            entity.setDeltaMovement(new Vec3(vec3.x, -0.05, vec3.z));
        }

        entity.resetFallDistance();
    }

    private void maybeDoSlideEffects(Level world, Entity entity) {
        if (doesEntityDoHoneyBlockSlideEffects(entity)) {
            if (world.random.nextInt(5) == 0) {
                entity.playSound(SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 1.0F);
            }

            if (!world.isClientSide && world.random.nextInt(5) == 0) {
                world.broadcastEntityEvent(entity, (byte)53);
            }
        }
    }

    public static void showSlideParticles(Entity entity) {
        showParticles(entity, 5);
    }

    public static void showJumpParticles(Entity entity) {
        showParticles(entity, 10);
    }

    private static void showParticles(Entity entity, int count) {
        if (entity.level().isClientSide) {
            BlockState blockState = Blocks.HONEY_BLOCK.defaultBlockState();

            for (int i = 0; i < count; i++) {
                entity.level()
                    .addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState), entity.getX(), entity.getY(), entity.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }
}
