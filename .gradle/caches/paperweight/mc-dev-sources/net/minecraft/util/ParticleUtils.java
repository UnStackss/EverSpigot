package net.minecraft.util;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ParticleUtils {
    public static void spawnParticlesOnBlockFaces(Level world, BlockPos pos, ParticleOptions effect, IntProvider count) {
        for (Direction direction : Direction.values()) {
            spawnParticlesOnBlockFace(world, pos, effect, count, direction, () -> getRandomSpeedRanges(world.random), 0.55);
        }
    }

    public static void spawnParticlesOnBlockFace(
        Level world, BlockPos pos, ParticleOptions effect, IntProvider count, Direction direction, Supplier<Vec3> velocity, double offsetMultiplier
    ) {
        int i = count.sample(world.random);

        for (int j = 0; j < i; j++) {
            spawnParticleOnFace(world, pos, direction, effect, velocity.get(), offsetMultiplier);
        }
    }

    private static Vec3 getRandomSpeedRanges(RandomSource random) {
        return new Vec3(Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5), Mth.nextDouble(random, -0.5, 0.5));
    }

    public static void spawnParticlesAlongAxis(Direction.Axis axis, Level world, BlockPos pos, double variance, ParticleOptions effect, UniformInt range) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        boolean bl = axis == Direction.Axis.X;
        boolean bl2 = axis == Direction.Axis.Y;
        boolean bl3 = axis == Direction.Axis.Z;
        int i = range.sample(world.random);

        for (int j = 0; j < i; j++) {
            double d = vec3.x + Mth.nextDouble(world.random, -1.0, 1.0) * (bl ? 0.5 : variance);
            double e = vec3.y + Mth.nextDouble(world.random, -1.0, 1.0) * (bl2 ? 0.5 : variance);
            double f = vec3.z + Mth.nextDouble(world.random, -1.0, 1.0) * (bl3 ? 0.5 : variance);
            double g = bl ? Mth.nextDouble(world.random, -1.0, 1.0) : 0.0;
            double h = bl2 ? Mth.nextDouble(world.random, -1.0, 1.0) : 0.0;
            double k = bl3 ? Mth.nextDouble(world.random, -1.0, 1.0) : 0.0;
            world.addParticle(effect, d, e, f, g, h, k);
        }
    }

    public static void spawnParticleOnFace(Level world, BlockPos pos, Direction direction, ParticleOptions effect, Vec3 velocity, double offsetMultiplier) {
        Vec3 vec3 = Vec3.atCenterOf(pos);
        int i = direction.getStepX();
        int j = direction.getStepY();
        int k = direction.getStepZ();
        double d = vec3.x + (i == 0 ? Mth.nextDouble(world.random, -0.5, 0.5) : (double)i * offsetMultiplier);
        double e = vec3.y + (j == 0 ? Mth.nextDouble(world.random, -0.5, 0.5) : (double)j * offsetMultiplier);
        double f = vec3.z + (k == 0 ? Mth.nextDouble(world.random, -0.5, 0.5) : (double)k * offsetMultiplier);
        double g = i == 0 ? velocity.x() : 0.0;
        double h = j == 0 ? velocity.y() : 0.0;
        double l = k == 0 ? velocity.z() : 0.0;
        world.addParticle(effect, d, e, f, g, h, l);
    }

    public static void spawnParticleBelow(Level world, BlockPos pos, RandomSource random, ParticleOptions effect) {
        double d = (double)pos.getX() + random.nextDouble();
        double e = (double)pos.getY() - 0.05;
        double f = (double)pos.getZ() + random.nextDouble();
        world.addParticle(effect, d, e, f, 0.0, 0.0, 0.0);
    }

    public static void spawnParticleInBlock(LevelAccessor world, BlockPos pos, int count, ParticleOptions effect) {
        double d = 0.5;
        BlockState blockState = world.getBlockState(pos);
        double e = blockState.isAir() ? 1.0 : blockState.getShape(world, pos).max(Direction.Axis.Y);
        spawnParticles(world, pos, count, 0.5, e, true, effect);
    }

    public static void spawnParticles(
        LevelAccessor world, BlockPos pos, int count, double horizontalOffset, double verticalOffset, boolean force, ParticleOptions effect
    ) {
        RandomSource randomSource = world.getRandom();

        for (int i = 0; i < count; i++) {
            double d = randomSource.nextGaussian() * 0.02;
            double e = randomSource.nextGaussian() * 0.02;
            double f = randomSource.nextGaussian() * 0.02;
            double g = 0.5 - horizontalOffset;
            double h = (double)pos.getX() + g + randomSource.nextDouble() * horizontalOffset * 2.0;
            double j = (double)pos.getY() + randomSource.nextDouble() * verticalOffset;
            double k = (double)pos.getZ() + g + randomSource.nextDouble() * horizontalOffset * 2.0;
            if (force || !world.getBlockState(BlockPos.containing(h, j, k).below()).isAir()) {
                world.addParticle(effect, h, j, k, d, e, f);
            }
        }
    }

    public static void spawnSmashAttackParticles(LevelAccessor world, BlockPos pos, int count) {
        Vec3 vec3 = pos.getCenter().add(0.0, 0.5, 0.0);
        BlockParticleOption blockParticleOption = new BlockParticleOption(ParticleTypes.DUST_PILLAR, world.getBlockState(pos));

        for (int i = 0; (float)i < (float)count / 3.0F; i++) {
            double d = vec3.x + world.getRandom().nextGaussian() / 2.0;
            double e = vec3.y;
            double f = vec3.z + world.getRandom().nextGaussian() / 2.0;
            double g = world.getRandom().nextGaussian() * 0.2F;
            double h = world.getRandom().nextGaussian() * 0.2F;
            double j = world.getRandom().nextGaussian() * 0.2F;
            world.addParticle(blockParticleOption, d, e, f, g, h, j);
        }

        for (int k = 0; (float)k < (float)count / 1.5F; k++) {
            double l = vec3.x + 3.5 * Math.cos((double)k) + world.getRandom().nextGaussian() / 2.0;
            double m = vec3.y;
            double n = vec3.z + 3.5 * Math.sin((double)k) + world.getRandom().nextGaussian() / 2.0;
            double o = world.getRandom().nextGaussian() * 0.05F;
            double p = world.getRandom().nextGaussian() * 0.05F;
            double q = world.getRandom().nextGaussian() * 0.05F;
            world.addParticle(blockParticleOption, l, m, n, o, p, q);
        }
    }
}
