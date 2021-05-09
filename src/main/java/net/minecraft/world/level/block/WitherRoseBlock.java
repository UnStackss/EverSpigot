package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WitherRoseBlock extends FlowerBlock {

    public static final MapCodec<WitherRoseBlock> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(WitherRoseBlock.EFFECTS_FIELD.forGetter(FlowerBlock::getSuspiciousEffects), propertiesCodec()).apply(instance, WitherRoseBlock::new);
    });

    @Override
    public MapCodec<WitherRoseBlock> codec() {
        return WitherRoseBlock.CODEC;
    }

    public WitherRoseBlock(Holder<MobEffect> holder, float f, BlockBehaviour.Properties settings) {
        this(makeEffectList(holder, f), settings);
    }

    public WitherRoseBlock(SuspiciousStewEffects stewEffects, BlockBehaviour.Properties settings) {
        super(stewEffects, settings);
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter world, BlockPos pos) {
        return super.mayPlaceOn(floor, world, pos) || floor.is(Blocks.NETHERRACK) || floor.is(Blocks.SOUL_SAND) || floor.is(Blocks.SOUL_SOIL);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        VoxelShape voxelshape = this.getShape(state, world, pos, CollisionContext.empty());
        Vec3 vec3d = voxelshape.bounds().getCenter();
        double d0 = (double) pos.getX() + vec3d.x;
        double d1 = (double) pos.getZ() + vec3d.z;

        for (int i = 0; i < 3; ++i) {
            if (random.nextBoolean()) {
                world.addParticle(ParticleTypes.SMOKE, d0 + random.nextDouble() / 5.0D, (double) pos.getY() + (0.5D - random.nextDouble()), d1 + random.nextDouble() / 5.0D, 0.0D, 0.0D, 0.0D);
            }
        }

    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (!world.isClientSide && world.getDifficulty() != Difficulty.PEACEFUL) {
            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                if (!entityliving.isInvulnerableTo(world.damageSources().wither())) {
                    entityliving.addEffect(new MobEffectInstance(MobEffects.WITHER, 40), org.bukkit.event.entity.EntityPotionEffectEvent.Cause.WITHER_ROSE); // CraftBukkit
                }
            }

        }
    }
}
