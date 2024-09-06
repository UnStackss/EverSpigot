package net.minecraft.world.level.chunk.status;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.Blender;

public class ChunkStatusTasks {

    public ChunkStatusTasks() {}

    private static boolean isLighted(ChunkAccess chunk) {
        return chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT) && chunk.isLightCorrect();
    }

    static CompletableFuture<ChunkAccess> passThrough(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();

        if (worldserver.serverLevelData.worldGenOptions().generateStructures()) { // CraftBukkit
            context.generator().createStructures(worldserver.registryAccess(), worldserver.getChunkSource().getGeneratorState(), worldserver.structureManager(), chunk, context.structureManager());
        }

        worldserver.onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> loadStructureStarts(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        context.level().onStructureStartsAvailable(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateStructureReferences(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        context.generator().createReferences(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateBiomes(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        return context.generator().createBiomes(worldserver.getChunkSource().randomState(), Blender.of(regionlimitedworldaccess), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), chunk);
    }

    static CompletableFuture<ChunkAccess> generateNoise(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        return context.generator().fillFromNoise(Blender.of(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), chunk).thenApply((ichunkaccess1) -> {
            if (ichunkaccess1 instanceof ProtoChunk protochunk) {
                BelowZeroRetrogen belowzeroretrogen = protochunk.getBelowZeroRetrogen();

                if (belowzeroretrogen != null) {
                    BelowZeroRetrogen.replaceOldBedrock(protochunk);
                    if (belowzeroretrogen.hasBedrockHoles()) {
                        belowzeroretrogen.applyBedrockMask(protochunk);
                    }
                }
            }

            return ichunkaccess1;
        });
    }

    static CompletableFuture<ChunkAccess> generateSurface(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        context.generator().buildSurface(regionlimitedworldaccess, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), worldserver.getChunkSource().randomState(), chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateCarvers(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        if (chunk instanceof ProtoChunk protochunk) {
            Blender.addAroundOldChunksCarvingMaskFilter(regionlimitedworldaccess, protochunk);
        }

        context.generator().applyCarvers(regionlimitedworldaccess, worldserver.getSeed(), worldserver.getChunkSource().randomState(), worldserver.getBiomeManager(), worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess), chunk, GenerationStep.Carving.AIR);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> generateFeatures(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ServerLevel worldserver = context.level();

        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.OCEAN_FLOOR, Heightmap.Types.WORLD_SURFACE));
        WorldGenRegion regionlimitedworldaccess = new WorldGenRegion(worldserver, chunks, step, chunk);

        context.generator().applyBiomeDecoration(regionlimitedworldaccess, chunk, worldserver.structureManager().forWorldGenRegion(regionlimitedworldaccess));
        Blender.generateBorderTicks(regionlimitedworldaccess, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> initializeLight(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ThreadedLevelLightEngine lightenginethreaded = context.lightEngine();

        chunk.initializeLightSources();
        ((ProtoChunk) chunk).setLightEngine(lightenginethreaded);
        boolean flag = ChunkStatusTasks.isLighted(chunk);

        return lightenginethreaded.initializeLight(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> light(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        boolean flag = ChunkStatusTasks.isLighted(chunk);

        return context.lightEngine().lightChunk(chunk, flag);
    }

    static CompletableFuture<ChunkAccess> generateSpawn(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        if (!chunk.isUpgrading()) {
            context.generator().spawnOriginalMobs(new WorldGenRegion(context.level(), chunks, step, chunk));
        }

        return CompletableFuture.completedFuture(chunk);
    }

    static CompletableFuture<ChunkAccess> full(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> chunks, ChunkAccess chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        GenerationChunkHolder generationchunkholder = (GenerationChunkHolder) chunks.get(chunkcoordintpair.x, chunkcoordintpair.z);

        return CompletableFuture.supplyAsync(() -> {
            ProtoChunk protochunk = (ProtoChunk) chunk;
            ServerLevel worldserver = context.level();
            LevelChunk chunk1;

            if (protochunk instanceof ImposterProtoChunk) {
                chunk1 = ((ImposterProtoChunk) protochunk).getWrapped();
            } else {
                chunk1 = new LevelChunk(worldserver, protochunk, ($) -> { // Paper - decompile fix
                    ChunkStatusTasks.postLoadProtoChunk(worldserver, protochunk.getEntities());
                });
                generationchunkholder.replaceProtoChunk(new ImposterProtoChunk(chunk1, false));
            }

            Objects.requireNonNull(generationchunkholder);
            chunk1.setFullStatus(generationchunkholder::getFullStatus);
            chunk1.runPostLoad();
            chunk1.setLoaded(true);
            chunk1.registerAllBlockEntitiesAfterLevelLoad();
            chunk1.registerTickContainerInLevel(worldserver);
            return chunk1;
        }, (runnable) -> {
            ProcessorHandle mailbox = context.mainThreadMailBox();
            long i = chunkcoordintpair.toLong();

            Objects.requireNonNull(generationchunkholder);
            mailbox.tell(ChunkTaskPriorityQueueSorter.message(runnable, i, generationchunkholder::getTicketLevel));
        });
    }

    private static void postLoadProtoChunk(ServerLevel world, List<CompoundTag> entities) {
        if (!entities.isEmpty()) {
            // CraftBukkit start - these are spawned serialized (DefinedStructure) and we don't call an add event below at the moment due to ordering complexities
            world.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(entities, world).filter((entity) -> {
                boolean needsRemoval = false;
                net.minecraft.server.dedicated.DedicatedServer server = world.getCraftServer().getServer();
                if (!server.areNpcsEnabled() && entity instanceof net.minecraft.world.entity.npc.Npc) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                if (!server.isSpawningAnimals() && (entity instanceof net.minecraft.world.entity.animal.Animal || entity instanceof net.minecraft.world.entity.animal.WaterAnimal)) {
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    needsRemoval = true;
                }
                checkDupeUUID(world, entity); // Paper - duplicate uuid resolving
                return !needsRemoval;
            }));
            // CraftBukkit end
        }

    }

    // Paper start - duplicate uuid resolving
    // rets true if to prevent the entity from being added
    public static boolean checkDupeUUID(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode mode = level.paperConfig().entities.spawning.duplicateUuid.mode;
        if (mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.WARN
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.DELETE
            && mode != io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN) {
            return false;
        }
        net.minecraft.world.entity.Entity other = level.getEntity(entity.getUUID());

        if (other == null || other == entity) {
            return false;
        }

        if (mode == io.papermc.paper.configuration.WorldConfiguration.Entities.Spawning.DuplicateUUID.DuplicateUUIDMode.SAFE_REGEN && other != null && !other.isRemoved()
            && Objects.equals(other.getEncodeId(), entity.getEncodeId())
            && entity.getBukkitEntity().getLocation().distance(other.getBukkitEntity().getLocation()) < level.paperConfig().entities.spawning.duplicateUuid.safeRegenDeleteRange
        ) {
            entity.discard(null);
            return true;
        }
        if (!other.isRemoved()) {
            switch (mode) {
                case SAFE_REGEN: {
                    entity.setUUID(java.util.UUID.randomUUID());
                    break;
                }
                case DELETE: {
                    entity.discard(org.bukkit.event.entity.EntityRemoveEvent.Cause.DISCARD);
                    return true;
                }
                default:
                    break;
            }
        }
        return false;
    }
    // Paper end - duplicate uuid resolving
}
