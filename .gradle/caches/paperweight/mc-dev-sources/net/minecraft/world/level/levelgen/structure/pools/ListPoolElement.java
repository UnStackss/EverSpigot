package net.minecraft.world.level.levelgen.structure.pools;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class ListPoolElement extends StructurePoolElement {
    public static final MapCodec<ListPoolElement> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(StructurePoolElement.CODEC.listOf().fieldOf("elements").forGetter(pool -> pool.elements), projectionCodec())
                .apply(instance, ListPoolElement::new)
    );
    private final List<StructurePoolElement> elements;

    public ListPoolElement(List<StructurePoolElement> elements, StructureTemplatePool.Projection projection) {
        super(projection);
        if (elements.isEmpty()) {
            throw new IllegalArgumentException("Elements are empty");
        } else {
            this.elements = elements;
            this.setProjectionOnEachElement(projection);
        }
    }

    @Override
    public Vec3i getSize(StructureTemplateManager structureTemplateManager, Rotation rotation) {
        int i = 0;
        int j = 0;
        int k = 0;

        for (StructurePoolElement structurePoolElement : this.elements) {
            Vec3i vec3i = structurePoolElement.getSize(structureTemplateManager, rotation);
            i = Math.max(i, vec3i.getX());
            j = Math.max(j, vec3i.getY());
            k = Math.max(k, vec3i.getZ());
        }

        return new Vec3i(i, j, k);
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> getShuffledJigsawBlocks(
        StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation, RandomSource random
    ) {
        return this.elements.get(0).getShuffledJigsawBlocks(structureTemplateManager, pos, rotation, random);
    }

    @Override
    public BoundingBox getBoundingBox(StructureTemplateManager structureTemplateManager, BlockPos pos, Rotation rotation) {
        Stream<BoundingBox> stream = this.elements
            .stream()
            .filter(element -> element != EmptyPoolElement.INSTANCE)
            .map(element -> element.getBoundingBox(structureTemplateManager, pos, rotation));
        return BoundingBox.encapsulatingBoxes(stream::iterator)
            .orElseThrow(() -> new IllegalStateException("Unable to calculate boundingbox for ListPoolElement"));
    }

    @Override
    public boolean place(
        StructureTemplateManager structureTemplateManager,
        WorldGenLevel world,
        StructureManager structureAccessor,
        ChunkGenerator chunkGenerator,
        BlockPos pos,
        BlockPos pivot,
        Rotation rotation,
        BoundingBox box,
        RandomSource random,
        LiquidSettings liquidSettings,
        boolean keepJigsaws
    ) {
        for (StructurePoolElement structurePoolElement : this.elements) {
            if (!structurePoolElement.place(
                structureTemplateManager, world, structureAccessor, chunkGenerator, pos, pivot, rotation, box, random, liquidSettings, keepJigsaws
            )) {
                return false;
            }
        }

        return true;
    }

    @Override
    public StructurePoolElementType<?> getType() {
        return StructurePoolElementType.LIST;
    }

    @Override
    public StructurePoolElement setProjection(StructureTemplatePool.Projection projection) {
        super.setProjection(projection);
        this.setProjectionOnEachElement(projection);
        return this;
    }

    @Override
    public String toString() {
        return "List[" + this.elements.stream().map(Object::toString).collect(Collectors.joining(", ")) + "]";
    }

    private void setProjectionOnEachElement(StructureTemplatePool.Projection projection) {
        this.elements.forEach(element -> element.setProjection(projection));
    }
}
