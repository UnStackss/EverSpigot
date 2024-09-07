package net.minecraft.world.level.levelgen.carver;

import com.mojang.serialization.Codec;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;

public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {
    public CaveWorldCarver(Codec<CaveCarverConfiguration> configCodec) {
        super(configCodec);
    }

    @Override
    public boolean isStartChunk(CaveCarverConfiguration config, RandomSource random) {
        return random.nextFloat() <= config.probability;
    }

    @Override
    public boolean carve(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> posToBiome,
        RandomSource random,
        Aquifer aquiferSampler,
        ChunkPos pos,
        CarvingMask mask
    ) {
        int i = SectionPos.sectionToBlockCoord(this.getRange() * 2 - 1);
        int j = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (int k = 0; k < j; k++) {
            double d = (double)pos.getBlockX(random.nextInt(16));
            double e = (double)config.y.sample(random, context);
            double f = (double)pos.getBlockZ(random.nextInt(16));
            double g = (double)config.horizontalRadiusMultiplier.sample(random);
            double h = (double)config.verticalRadiusMultiplier.sample(random);
            double l = (double)config.floorLevel.sample(random);
            WorldCarver.CarveSkipChecker carveSkipChecker = (contextx, scaledRelativeX, scaledRelativeY, scaledRelativeZ, y) -> shouldSkip(
                    scaledRelativeX, scaledRelativeY, scaledRelativeZ, l
                );
            int m = 1;
            if (random.nextInt(4) == 0) {
                double n = (double)config.yScale.sample(random);
                float o = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, config, chunk, posToBiome, aquiferSampler, d, e, f, o, n, mask, carveSkipChecker);
                m += random.nextInt(4);
            }

            for (int p = 0; p < m; p++) {
                float q = random.nextFloat() * (float) (Math.PI * 2);
                float r = (random.nextFloat() - 0.5F) / 4.0F;
                float s = this.getThickness(random);
                int t = i - random.nextInt(i / 4);
                int u = 0;
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    posToBiome,
                    random.nextLong(),
                    aquiferSampler,
                    d,
                    e,
                    f,
                    g,
                    h,
                    s,
                    q,
                    r,
                    0,
                    t,
                    this.getYScale(),
                    mask,
                    carveSkipChecker
                );
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return 1.0;
    }

    protected void createRoom(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> posToBiome,
        Aquifer aquiferSampler,
        double d,
        double e,
        double f,
        float g,
        double h,
        CarvingMask mask,
        WorldCarver.CarveSkipChecker skipPredicate
    ) {
        double i = 1.5 + (double)(Mth.sin((float) (Math.PI / 2)) * g);
        double j = i * h;
        this.carveEllipsoid(context, config, chunk, posToBiome, aquiferSampler, d + 1.0, e, f, i, j, mask, skipPredicate);
    }

    protected void createTunnel(
        CarvingContext context,
        CaveCarverConfiguration config,
        ChunkAccess chunk,
        Function<BlockPos, Holder<Biome>> posToBiome,
        long seed,
        Aquifer aquiferSampler,
        double x,
        double y,
        double z,
        double horizontalScale,
        double verticalScale,
        float width,
        float yaw,
        float pitch,
        int branchStartIndex,
        int branchCount,
        double yawPitchRatio,
        CarvingMask mask,
        WorldCarver.CarveSkipChecker skipPredicate
    ) {
        RandomSource randomSource = RandomSource.create(seed);
        int i = randomSource.nextInt(branchCount / 2) + branchCount / 4;
        boolean bl = randomSource.nextInt(6) == 0;
        float f = 0.0F;
        float g = 0.0F;

        for (int j = branchStartIndex; j < branchCount; j++) {
            double d = 1.5 + (double)(Mth.sin((float) Math.PI * (float)j / (float)branchCount) * width);
            double e = d * yawPitchRatio;
            float h = Mth.cos(pitch);
            x += (double)(Mth.cos(yaw) * h);
            y += (double)Mth.sin(pitch);
            z += (double)(Mth.sin(yaw) * h);
            pitch *= bl ? 0.92F : 0.7F;
            pitch += g * 0.1F;
            yaw += f * 0.1F;
            g *= 0.9F;
            f *= 0.75F;
            g += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (j == i && width > 1.0F) {
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    posToBiome,
                    randomSource.nextLong(),
                    aquiferSampler,
                    x,
                    y,
                    z,
                    horizontalScale,
                    verticalScale,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw - (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    mask,
                    skipPredicate
                );
                this.createTunnel(
                    context,
                    config,
                    chunk,
                    posToBiome,
                    randomSource.nextLong(),
                    aquiferSampler,
                    x,
                    y,
                    z,
                    horizontalScale,
                    verticalScale,
                    randomSource.nextFloat() * 0.5F + 0.5F,
                    yaw + (float) (Math.PI / 2),
                    pitch / 3.0F,
                    j,
                    branchCount,
                    1.0,
                    mask,
                    skipPredicate
                );
                return;
            }

            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, j, branchCount, width)) {
                    return;
                }

                this.carveEllipsoid(context, config, chunk, posToBiome, aquiferSampler, x, y, z, d * horizontalScale, e * verticalScale, mask, skipPredicate);
            }
        }
    }

    private static boolean shouldSkip(double scaledRelativeX, double scaledRelativeY, double scaledRelativeZ, double floorY) {
        return scaledRelativeY <= floorY || scaledRelativeX * scaledRelativeX + scaledRelativeY * scaledRelativeY + scaledRelativeZ * scaledRelativeZ >= 1.0;
    }
}
