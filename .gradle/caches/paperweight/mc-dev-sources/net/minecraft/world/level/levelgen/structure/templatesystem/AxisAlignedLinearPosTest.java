package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class AxisAlignedLinearPosTest extends PosRuleTest {
    public static final MapCodec<AxisAlignedLinearPosTest> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                    Codec.FLOAT.fieldOf("min_chance").orElse(0.0F).forGetter(ruleTest -> ruleTest.minChance),
                    Codec.FLOAT.fieldOf("max_chance").orElse(0.0F).forGetter(ruleTest -> ruleTest.maxChance),
                    Codec.INT.fieldOf("min_dist").orElse(0).forGetter(ruleTest -> ruleTest.minDist),
                    Codec.INT.fieldOf("max_dist").orElse(0).forGetter(ruleTest -> ruleTest.maxDist),
                    Direction.Axis.CODEC.fieldOf("axis").orElse(Direction.Axis.Y).forGetter(ruleTest -> ruleTest.axis)
                )
                .apply(instance, AxisAlignedLinearPosTest::new)
    );
    private final float minChance;
    private final float maxChance;
    private final int minDist;
    private final int maxDist;
    private final Direction.Axis axis;

    public AxisAlignedLinearPosTest(float minChance, float maxChance, int minDistance, int maxDistance, Direction.Axis axis) {
        if (minDistance >= maxDistance) {
            throw new IllegalArgumentException("Invalid range: [" + minDistance + "," + maxDistance + "]");
        } else {
            this.minChance = minChance;
            this.maxChance = maxChance;
            this.minDist = minDistance;
            this.maxDist = maxDistance;
            this.axis = axis;
        }
    }

    @Override
    public boolean test(BlockPos originalPos, BlockPos currentPos, BlockPos pivot, RandomSource random) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, this.axis);
        float f = (float)Math.abs((currentPos.getX() - pivot.getX()) * direction.getStepX());
        float g = (float)Math.abs((currentPos.getY() - pivot.getY()) * direction.getStepY());
        float h = (float)Math.abs((currentPos.getZ() - pivot.getZ()) * direction.getStepZ());
        int i = (int)(f + g + h);
        float j = random.nextFloat();
        return j <= Mth.clampedLerp(this.minChance, this.maxChance, Mth.inverseLerp((float)i, (float)this.minDist, (float)this.maxDist));
    }

    @Override
    protected PosRuleTestType<?> getType() {
        return PosRuleTestType.AXIS_ALIGNED_LINEAR_POS_TEST;
    }
}
