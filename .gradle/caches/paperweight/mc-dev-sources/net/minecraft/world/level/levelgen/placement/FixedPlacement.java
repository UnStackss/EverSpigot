package net.minecraft.world.level.levelgen.placement;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;

public class FixedPlacement extends PlacementModifier {
    public static final MapCodec<FixedPlacement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(BlockPos.CODEC.listOf().fieldOf("positions").forGetter(placementModifier -> placementModifier.positions))
                .apply(instance, FixedPlacement::new)
    );
    private final List<BlockPos> positions;

    public static FixedPlacement of(BlockPos... positions) {
        return new FixedPlacement(List.of(positions));
    }

    private FixedPlacement(List<BlockPos> positions) {
        this.positions = positions;
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int i = SectionPos.blockToSectionCoord(pos.getX());
        int j = SectionPos.blockToSectionCoord(pos.getZ());
        boolean bl = false;

        for (BlockPos blockPos : this.positions) {
            if (isSameChunk(i, j, blockPos)) {
                bl = true;
                break;
            }
        }

        return !bl ? Stream.empty() : this.positions.stream().filter(posx -> isSameChunk(i, j, posx));
    }

    private static boolean isSameChunk(int chunkSectionX, int chunkSectionZ, BlockPos pos) {
        return chunkSectionX == SectionPos.blockToSectionCoord(pos.getX()) && chunkSectionZ == SectionPos.blockToSectionCoord(pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type() {
        return PlacementModifierType.FIXED_PLACEMENT;
    }
}
