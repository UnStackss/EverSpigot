package net.minecraft.world.level.levelgen.structure;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2BooleanMap;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

public class StructureCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NO_STRUCTURE = -1;
    private final ChunkScanAccess storageAccess;
    private final RegistryAccess registryAccess;
    private final StructureTemplateManager structureTemplateManager;
    private final ResourceKey<net.minecraft.world.level.dimension.LevelStem> dimension; // Paper - fix missing CB diff
    private final ChunkGenerator chunkGenerator;
    private final RandomState randomState;
    private final LevelHeightAccessor heightAccessor;
    private final BiomeSource biomeSource;
    private final long seed;
    private final DataFixer fixerUpper;
    private final Long2ObjectMap<Object2IntMap<Structure>> loadedChunks = new Long2ObjectOpenHashMap<>();
    private final Map<Structure, Long2BooleanMap> featureChecks = new HashMap<>();

    public StructureCheck(
        ChunkScanAccess chunkIoWorker,
        RegistryAccess registryManager,
        StructureTemplateManager structureTemplateManager,
        ResourceKey<net.minecraft.world.level.dimension.LevelStem> worldKey, // Paper - fix missing CB diff
        ChunkGenerator chunkGenerator,
        RandomState noiseConfig,
        LevelHeightAccessor world,
        BiomeSource biomeSource,
        long seed,
        DataFixer dataFixer
    ) {
        this.storageAccess = chunkIoWorker;
        this.registryAccess = registryManager;
        this.structureTemplateManager = structureTemplateManager;
        this.dimension = worldKey;
        this.chunkGenerator = chunkGenerator;
        this.randomState = noiseConfig;
        this.heightAccessor = world;
        this.biomeSource = biomeSource;
        this.seed = seed;
        this.fixerUpper = dataFixer;
    }

    // Paper start - add missing structure salt configs
    @Nullable
    private Integer getSaltOverride(Structure type) {
        if (this.heightAccessor instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (type instanceof net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure) {
                return serverLevel.spigotConfig.mineshaftSeed;
            } else if (type instanceof net.minecraft.world.level.levelgen.structure.structures.BuriedTreasureStructure) {
                return serverLevel.spigotConfig.buriedTreasureSeed;
            }
        }
        return null;
    }
    // Paper end - add missing structure seed configs

    public StructureCheckResult checkStart(ChunkPos pos, Structure type, StructurePlacement placement, boolean skipReferencedStructures) {
        long l = pos.toLong();
        Object2IntMap<Structure> object2IntMap = this.loadedChunks.get(l);
        if (object2IntMap != null) {
            return this.checkStructureInfo(object2IntMap, type, skipReferencedStructures);
        } else {
            StructureCheckResult structureCheckResult = this.tryLoadFromStorage(pos, type, skipReferencedStructures, l);
            if (structureCheckResult != null) {
                return structureCheckResult;
            } else if (!placement.applyAdditionalChunkRestrictions(pos.x, pos.z, this.seed, this.getSaltOverride(type))) { // Paper - add missing structure seed configs
                return StructureCheckResult.START_NOT_PRESENT;
            } else {
                boolean bl = this.featureChecks
                    .computeIfAbsent(type, structure2 -> new Long2BooleanOpenHashMap())
                    .computeIfAbsent(l, chunkPos -> this.canCreateStructure(pos, type));
                return !bl ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.CHUNK_LOAD_NEEDED;
            }
        }
    }

    private boolean canCreateStructure(ChunkPos pos, Structure structure) {
        return structure.findValidGenerationPoint(
                new Structure.GenerationContext(
                    this.registryAccess,
                    this.chunkGenerator,
                    this.biomeSource,
                    this.randomState,
                    this.structureTemplateManager,
                    this.seed,
                    pos,
                    this.heightAccessor,
                    structure.biomes()::contains
                )
            )
            .isPresent();
    }

    @Nullable
    private StructureCheckResult tryLoadFromStorage(ChunkPos pos, Structure structure, boolean skipReferencedStructures, long posLong) {
        CollectFields collectFields = new CollectFields(
            new FieldSelector(IntTag.TYPE, "DataVersion"),
            new FieldSelector("Level", "Structures", CompoundTag.TYPE, "Starts"),
            new FieldSelector("structures", CompoundTag.TYPE, "starts")
        );

        try {
            this.storageAccess.scanChunk(pos, collectFields).join();
        } catch (Exception var13) {
            LOGGER.warn("Failed to read chunk {}", pos, var13);
            return StructureCheckResult.CHUNK_LOAD_NEEDED;
        }

        if (!(collectFields.getResult() instanceof CompoundTag compoundTag)) {
            return null;
        } else {
            int i = ChunkStorage.getVersion(compoundTag);
            if (i <= 1493) {
                return StructureCheckResult.CHUNK_LOAD_NEEDED;
            } else {
                ChunkStorage.injectDatafixingContext(compoundTag, this.dimension, this.chunkGenerator.getTypeNameForDataFixer());

                CompoundTag compoundTag2;
                try {
                    compoundTag2 = DataFixTypes.CHUNK.updateToCurrentVersion(this.fixerUpper, compoundTag, i);
                } catch (Exception var12) {
                    LOGGER.warn("Failed to partially datafix chunk {}", pos, var12);
                    return StructureCheckResult.CHUNK_LOAD_NEEDED;
                }

                Object2IntMap<Structure> object2IntMap = this.loadStructures(compoundTag2);
                if (object2IntMap == null) {
                    return null;
                } else {
                    this.storeFullResults(posLong, object2IntMap);
                    return this.checkStructureInfo(object2IntMap, structure, skipReferencedStructures);
                }
            }
        }
    }

    @Nullable
    private Object2IntMap<Structure> loadStructures(CompoundTag nbt) {
        if (!nbt.contains("structures", 10)) {
            return null;
        } else {
            CompoundTag compoundTag = nbt.getCompound("structures");
            if (!compoundTag.contains("starts", 10)) {
                return null;
            } else {
                CompoundTag compoundTag2 = compoundTag.getCompound("starts");
                if (compoundTag2.isEmpty()) {
                    return Object2IntMaps.emptyMap();
                } else {
                    Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
                    Registry<Structure> registry = this.registryAccess.registryOrThrow(Registries.STRUCTURE);

                    for (String string : compoundTag2.getAllKeys()) {
                        ResourceLocation resourceLocation = ResourceLocation.tryParse(string);
                        if (resourceLocation != null) {
                            Structure structure = registry.get(resourceLocation);
                            if (structure != null) {
                                CompoundTag compoundTag3 = compoundTag2.getCompound(string);
                                if (!compoundTag3.isEmpty()) {
                                    String string2 = compoundTag3.getString("id");
                                    if (!"INVALID".equals(string2)) {
                                        int i = compoundTag3.getInt("references");
                                        object2IntMap.put(structure, i);
                                    }
                                }
                            }
                        }
                    }

                    return object2IntMap;
                }
            }
        }
    }

    private static Object2IntMap<Structure> deduplicateEmptyMap(Object2IntMap<Structure> map) {
        return map.isEmpty() ? Object2IntMaps.emptyMap() : map;
    }

    private StructureCheckResult checkStructureInfo(Object2IntMap<Structure> referencesByStructure, Structure structure, boolean skipReferencedStructures) {
        int i = referencesByStructure.getOrDefault(structure, -1);
        return i == -1 || skipReferencedStructures && i != 0 ? StructureCheckResult.START_NOT_PRESENT : StructureCheckResult.START_PRESENT;
    }

    public void onStructureLoad(ChunkPos pos, Map<Structure, StructureStart> structureStarts) {
        long l = pos.toLong();
        Object2IntMap<Structure> object2IntMap = new Object2IntOpenHashMap<>();
        structureStarts.forEach((start, structureStart) -> {
            if (structureStart.isValid()) {
                object2IntMap.put(start, structureStart.getReferences());
            }
        });
        this.storeFullResults(l, object2IntMap);
    }

    private void storeFullResults(long pos, Object2IntMap<Structure> referencesByStructure) {
        this.loadedChunks.put(pos, deduplicateEmptyMap(referencesByStructure));
        this.featureChecks.values().forEach(generationPossibilityByChunkPos -> generationPossibilityByChunkPos.remove(pos));
    }

    public void incrementReference(ChunkPos pos, Structure structure) {
        this.loadedChunks.compute(pos.toLong(), (posx, referencesByStructure) -> {
            if (referencesByStructure == null || referencesByStructure.isEmpty()) {
                referencesByStructure = new Object2IntOpenHashMap<>();
            }

            referencesByStructure.computeInt(structure, (feature, references) -> references == null ? 1 : references + 1);
            return referencesByStructure;
        });
    }
}
