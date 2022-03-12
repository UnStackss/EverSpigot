package net.minecraft.world.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.chunk.StructureAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

public class StructureManager {
    public final LevelAccessor level;
    private final WorldOptions worldOptions;
    private final StructureCheck structureCheck;

    public StructureManager(LevelAccessor world, WorldOptions options, StructureCheck locator) {
        this.level = world;
        this.worldOptions = options;
        this.structureCheck = locator;
    }

    public StructureManager forWorldGenRegion(WorldGenRegion region) {
        if (region.getLevel() != this.level) {
            throw new IllegalStateException("Using invalid structure manager (source level: " + region.getLevel() + ", region: " + region);
        } else {
            return new StructureManager(region, this.worldOptions, this.structureCheck);
        }
    }

    public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> predicate) {
        // Paper start - Fix swamp hut cat generation deadlock
        return this.startsForStructure(pos, predicate, null);
    }
    public List<StructureStart> startsForStructure(ChunkPos pos, Predicate<Structure> predicate, @Nullable ServerLevelAccessor levelAccessor) {
        Map<Structure, LongSet> map = (levelAccessor == null ? this.level : levelAccessor).getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
        // Paper end - Fix swamp hut cat generation deadlock
        Builder<StructureStart> builder = ImmutableList.builder();

        for (Entry<Structure, LongSet> entry : map.entrySet()) {
            Structure structure = entry.getKey();
            if (predicate.test(structure)) {
                this.fillStartsForStructure(structure, entry.getValue(), builder::add);
            }
        }

        return builder.build();
    }

    public List<StructureStart> startsForStructure(SectionPos sectionPos, Structure structure) {
        LongSet longSet = this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).getReferencesForStructure(structure);
        Builder<StructureStart> builder = ImmutableList.builder();
        this.fillStartsForStructure(structure, longSet, builder::add);
        return builder.build();
    }

    public void fillStartsForStructure(Structure structure, LongSet structureStartPositions, Consumer<StructureStart> consumer) {
        for (long l : structureStartPositions) {
            SectionPos sectionPos = SectionPos.of(new ChunkPos(l), this.level.getMinSection());
            StructureStart structureStart = this.getStartForStructure(
                sectionPos, structure, this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_STARTS)
            );
            if (structureStart != null && structureStart.isValid()) {
                consumer.accept(structureStart);
            }
        }
    }

    @Nullable
    public StructureStart getStartForStructure(SectionPos pos, Structure structure, StructureAccess holder) {
        return holder.getStartForStructure(structure);
    }

    public void setStartForStructure(SectionPos pos, Structure structure, StructureStart structureStart, StructureAccess holder) {
        holder.setStartForStructure(structure, structureStart);
    }

    public void addReferenceForStructure(SectionPos pos, Structure structure, long reference, StructureAccess holder) {
        holder.addReferenceForStructure(structure, reference);
    }

    public boolean shouldGenerateStructures() {
        return this.worldOptions.generateStructures();
    }

    public StructureStart getStructureAt(BlockPos pos, Structure structure) {
        for (StructureStart structureStart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (structureStart.getBoundingBox().isInside(pos)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, TagKey<Structure> tag) {
        return this.getStructureWithPieceAt(pos, structure -> structure.is(tag));
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, HolderSet<Structure> structures) {
        return this.getStructureWithPieceAt(pos, structures::contains);
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Predicate<Holder<Structure>> predicate) {
        // Paper start - Fix swamp hut cat generation deadlock
        return this.getStructureWithPieceAt(pos, predicate, null);
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, TagKey<Structure> tag, @Nullable ServerLevelAccessor levelAccessor) {
        return this.getStructureWithPieceAt(pos, structure -> structure.is(tag), levelAccessor);
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Predicate<Holder<Structure>> predicate, @Nullable ServerLevelAccessor levelAccessor) {
        // Paper end - Fix swamp hut cat generation deadlock
        Registry<Structure> registry = this.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (StructureStart structureStart : this.startsForStructure(
            new ChunkPos(pos), structure -> registry.getHolder(registry.getId(structure)).map(predicate::test).orElse(false), levelAccessor // Paper - Fix swamp hut cat generation deadlock
        )) {
            if (this.structureHasPieceAt(pos, structureStart)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public StructureStart getStructureWithPieceAt(BlockPos pos, Structure structure) {
        for (StructureStart structureStart : this.startsForStructure(SectionPos.of(pos), structure)) {
            if (this.structureHasPieceAt(pos, structureStart)) {
                return structureStart;
            }
        }

        return StructureStart.INVALID_START;
    }

    public boolean structureHasPieceAt(BlockPos pos, StructureStart structureStart) {
        for (StructurePiece structurePiece : structureStart.getPieces()) {
            if (structurePiece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }

        return false;
    }

    public boolean hasAnyStructureAt(BlockPos pos) {
        SectionPos sectionPos = SectionPos.of(pos);
        return this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).hasAnyStructureReferences();
    }

    public Map<Structure, LongSet> getAllStructuresAt(BlockPos pos) {
        SectionPos sectionPos = SectionPos.of(pos);
        return this.level.getChunk(sectionPos.x(), sectionPos.z(), ChunkStatus.STRUCTURE_REFERENCES).getAllReferences();
    }

    public StructureCheckResult checkStructurePresence(ChunkPos chunkPos, Structure structure, StructurePlacement placement, boolean skipReferencedStructures) {
        return this.structureCheck.checkStart(chunkPos, structure, placement, skipReferencedStructures);
    }

    public void addReference(StructureStart structureStart) {
        structureStart.addReference();
        this.structureCheck.incrementReference(structureStart.getChunkPos(), structureStart.getStructure());
    }

    public RegistryAccess registryAccess() {
        return this.level.registryAccess();
    }
}
