package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import it.unimi.dsi.fastutil.shorts.ShortListIterator;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import org.slf4j.Logger;

public class ChunkSerializer {

    public static final Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC = PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_UPGRADE_DATA = "UpgradeData";
    private static final String BLOCK_TICKS_TAG = "block_ticks";
    private static final String FLUID_TICKS_TAG = "fluid_ticks";
    public static final String X_POS_TAG = "xPos";
    public static final String Z_POS_TAG = "zPos";
    public static final String HEIGHTMAPS_TAG = "Heightmaps";
    public static final String IS_LIGHT_ON_TAG = "isLightOn";
    public static final String SECTIONS_TAG = "sections";
    public static final String BLOCK_LIGHT_TAG = "BlockLight";
    public static final String SKY_LIGHT_TAG = "SkyLight";

    // Paper start - Do not let the server load chunks from newer versions
    private static final int CURRENT_DATA_VERSION = net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion();
    private static final boolean JUST_CORRUPT_IT = Boolean.getBoolean("Paper.ignoreWorldDataVersion");
    // Paper end - Do not let the server load chunks from newer versions
    public ChunkSerializer() {}

    // Paper start - guard against serializing mismatching coordinates
    // TODO Note: This needs to be re-checked each update
    public static ChunkPos getChunkCoordinate(final CompoundTag chunkData) {
        final int dataVersion = ChunkStorage.getVersion(chunkData);
        if (dataVersion < 2842) { // Level tag is removed after this version
            final CompoundTag levelData = chunkData.getCompound("Level");
            return new ChunkPos(levelData.getInt("xPos"), levelData.getInt("zPos"));
        } else {
            return new ChunkPos(chunkData.getInt("xPos"), chunkData.getInt("zPos"));
        }
    }
    // Paper end - guard against serializing mismatching coordinates

    public static ProtoChunk read(ServerLevel world, PoiManager poiStorage, RegionStorageInfo key, ChunkPos chunkPos, CompoundTag nbt) {
        // Paper start - Do not let the server load chunks from newer versions
        if (nbt.contains("DataVersion", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            final int dataVersion = nbt.getInt("DataVersion");
            if (!JUST_CORRUPT_IT && dataVersion > CURRENT_DATA_VERSION) {
                new RuntimeException("Server attempted to load chunk saved with newer version of minecraft! " + dataVersion + " > " + CURRENT_DATA_VERSION).printStackTrace();
                System.exit(1);
            }
        }
        // Paper end - Do not let the server load chunks from newer versions
        ChunkPos chunkcoordintpair1 = new ChunkPos(nbt.getInt("xPos"), nbt.getInt("zPos")); // Paper - guard against serializing mismatching coordinates; diff on change, see ChunkSerializer#getChunkCoordinate

        if (!Objects.equals(chunkPos, chunkcoordintpair1)) {
            ChunkSerializer.LOGGER.error("Chunk file at {} is in the wrong location; relocating. (Expected {}, got {})", new Object[]{chunkPos, chunkPos, chunkcoordintpair1});
            world.getServer().reportMisplacedChunk(chunkcoordintpair1, chunkPos, key);
        }

        UpgradeData chunkconverter = nbt.contains("UpgradeData", 10) ? new UpgradeData(nbt.getCompound("UpgradeData"), world) : UpgradeData.EMPTY;
        boolean flag = nbt.getBoolean("isLightOn");
        ListTag nbttaglist = nbt.getList("sections", 10);
        int i = world.getSectionsCount();
        LevelChunkSection[] achunksection = new LevelChunkSection[i];
        boolean flag1 = world.dimensionType().hasSkyLight();
        ServerChunkCache chunkproviderserver = world.getChunkSource();
        LevelLightEngine levellightengine = chunkproviderserver.getLightEngine();
        Registry<Biome> iregistry = world.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainer<Holder<Biome>>> codec = ChunkSerializer.makeBiomeCodecRW(iregistry); // CraftBukkit - read/write
        boolean flag2 = false;

        for (int j = 0; j < nbttaglist.size(); ++j) {
            CompoundTag nbttagcompound1 = nbttaglist.getCompound(j);
            byte b0 = nbttagcompound1.getByte("Y");
            int k = world.getSectionIndexFromSectionY(b0);

            if (k >= 0 && k < achunksection.length) {
                PalettedContainer datapaletteblock;

                if (nbttagcompound1.contains("block_states", 10)) {
                    datapaletteblock = (PalettedContainer) ChunkSerializer.BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, nbttagcompound1.getCompound("block_states")).promotePartial((s) -> {
                        ChunkSerializer.logErrors(chunkPos, b0, s);
                    }).getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    datapaletteblock = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
                }

                PalettedContainer object; // CraftBukkit - read/write

                if (nbttagcompound1.contains("biomes", 10)) {
                    object = codec.parse(NbtOps.INSTANCE, nbttagcompound1.getCompound("biomes")).promotePartial((s) -> { // CraftBukkit - decompile error
                        ChunkSerializer.logErrors(chunkPos, b0, s);
                    }).getOrThrow(ChunkSerializer.ChunkReadException::new);
                } else {
                    object = new PalettedContainer<>(iregistry.asHolderIdMap(), iregistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES);
                }

                LevelChunkSection chunksection = new LevelChunkSection(datapaletteblock, (PalettedContainer) object); // CraftBukkit - read/write

                achunksection[k] = chunksection;
                SectionPos sectionposition = SectionPos.of(chunkPos, b0);

                // Paper - rewrite chunk system - moved to final load stage
            }

            boolean flag3 = nbttagcompound1.contains("BlockLight", 7);
            boolean flag4 = flag1 && nbttagcompound1.contains("SkyLight", 7);

            if (flag3 || flag4) {
                if (!flag2) {
                    levellightengine.retainData(chunkPos, true);
                    flag2 = true;
                }

                if (flag3) {
                    levellightengine.queueSectionData(LightLayer.BLOCK, SectionPos.of(chunkPos, b0), new DataLayer(nbttagcompound1.getByteArray("BlockLight")));
                }

                if (flag4) {
                    levellightengine.queueSectionData(LightLayer.SKY, SectionPos.of(chunkPos, b0), new DataLayer(nbttagcompound1.getByteArray("SkyLight")));
                }
            }
        }

        long l = nbt.getLong("InhabitedTime");
        ChunkType chunktype = ChunkSerializer.getChunkTypeFromTag(nbt);
        DataResult dataresult;
        Logger logger;
        BlendingData blendingdata;

        if (nbt.contains("blending_data", 10)) {
            dataresult = BlendingData.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.getCompound("blending_data")));
            logger = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger);
            blendingdata = (BlendingData) ((DataResult<BlendingData>) dataresult).resultOrPartial(logger::error).orElse(null); // CraftBukkit - decompile error
        } else {
            blendingdata = null;
        }

        Object object1;

        if (chunktype == ChunkType.LEVELCHUNK) {
            LevelChunkTicks<Block> levelchunkticks = LevelChunkTicks.load(nbt.getList("block_ticks", 10), (s) -> {
                return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            LevelChunkTicks<Fluid> levelchunkticks1 = LevelChunkTicks.load(nbt.getList("fluid_ticks", 10), (s) -> {
                return BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);

            object1 = new LevelChunk(world.getLevel(), chunkPos, chunkconverter, levelchunkticks, levelchunkticks1, l, achunksection, ChunkSerializer.postLoadChunk(world, nbt), blendingdata);
        } else {
            ProtoChunkTicks<Block> protochunkticklist = ProtoChunkTicks.load(nbt.getList("block_ticks", 10), (s) -> {
                return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            ProtoChunkTicks<Fluid> protochunkticklist1 = ProtoChunkTicks.load(nbt.getList("fluid_ticks", 10), (s) -> {
                return BuiltInRegistries.FLUID.getOptional(ResourceLocation.tryParse(s));
            }, chunkPos);
            ProtoChunk protochunk = new ProtoChunk(chunkPos, chunkconverter, achunksection, protochunkticklist, protochunkticklist1, world, iregistry, blendingdata);

            object1 = protochunk;
            protochunk.setInhabitedTime(l);
            if (nbt.contains("below_zero_retrogen", 10)) {
                dataresult = BelowZeroRetrogen.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.getCompound("below_zero_retrogen")));
                logger = ChunkSerializer.LOGGER;
                Objects.requireNonNull(logger);
                Optional<BelowZeroRetrogen> optional = ((DataResult<BelowZeroRetrogen>) dataresult).resultOrPartial(logger::error); // CraftBukkit - decompile error

                Objects.requireNonNull(protochunk);
                optional.ifPresent(protochunk::setBelowZeroRetrogen);
            }

            ChunkStatus chunkstatus = ChunkStatus.byName(nbt.getString("Status"));

            protochunk.setPersistedStatus(chunkstatus);
            if (chunkstatus.isOrAfter(ChunkStatus.INITIALIZE_LIGHT)) {
                protochunk.setLightEngine(levellightengine);
            }
        }

        // CraftBukkit start - load chunk persistent data from nbt - SPIGOT-6814: Already load PDC here to account for 1.17 to 1.18 chunk upgrading.
        net.minecraft.nbt.Tag persistentBase = nbt.get("ChunkBukkitValues");
        if (persistentBase instanceof CompoundTag) {
            ((ChunkAccess) object1).persistentDataContainer.putAll((CompoundTag) persistentBase);
        }
        // CraftBukkit end

        ((ChunkAccess) object1).setLightCorrect(flag);
        CompoundTag nbttagcompound2 = nbt.getCompound("Heightmaps");
        EnumSet<Heightmap.Types> enumset = EnumSet.noneOf(Heightmap.Types.class);
        Iterator iterator = ((ChunkAccess) object1).getPersistedStatus().heightmapsAfter().iterator();

        while (iterator.hasNext()) {
            Heightmap.Types heightmap_type = (Heightmap.Types) iterator.next();
            String s = heightmap_type.getSerializationKey();

            if (nbttagcompound2.contains(s, 12)) {
                ((ChunkAccess) object1).setHeightmap(heightmap_type, nbttagcompound2.getLongArray(s));
            } else {
                enumset.add(heightmap_type);
            }
        }

        Heightmap.primeHeightmaps((ChunkAccess) object1, enumset);
        CompoundTag nbttagcompound3 = nbt.getCompound("structures");

        ((ChunkAccess) object1).setAllStarts(ChunkSerializer.unpackStructureStart(StructurePieceSerializationContext.fromLevel(world), nbttagcompound3, world.getSeed()));
        ((ChunkAccess) object1).setAllReferences(ChunkSerializer.unpackStructureReferences(world.registryAccess(), chunkPos, nbttagcompound3));
        if (nbt.getBoolean("shouldSave")) {
            ((ChunkAccess) object1).setUnsaved(true);
        }

        ListTag nbttaglist1 = nbt.getList("PostProcessing", 9);

        ListTag nbttaglist2;
        int i1;

        for (int j1 = 0; j1 < nbttaglist1.size(); ++j1) {
            nbttaglist2 = nbttaglist1.getList(j1);

            for (i1 = 0; i1 < nbttaglist2.size(); ++i1) {
                ((ChunkAccess) object1).addPackedPostProcess(nbttaglist2.getShort(i1), j1);
            }
        }

        ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.loadLightHook(world, chunkPos, nbt, (ChunkAccess)object1); // Paper - rewrite chunk system - note: it's ok to pass the raw value instead of wrapped

        if (chunktype == ChunkType.LEVELCHUNK) {
            return new ImposterProtoChunk((LevelChunk) object1, false);
        } else {
            ProtoChunk protochunk1 = (ProtoChunk) object1;

            nbttaglist2 = nbt.getList("entities", 10);

            for (i1 = 0; i1 < nbttaglist2.size(); ++i1) {
                protochunk1.addEntity(nbttaglist2.getCompound(i1));
            }

            ListTag nbttaglist3 = nbt.getList("block_entities", 10);

            for (int k1 = 0; k1 < nbttaglist3.size(); ++k1) {
                CompoundTag nbttagcompound4 = nbttaglist3.getCompound(k1);

                // Paper start - do not read tile entities positioned outside the chunk
                BlockPos blockposition = BlockEntity.getPosFromTag(nbttagcompound4);
                if ((blockposition.getX() >> 4) != chunkPos.x || (blockposition.getZ() >> 4) != chunkPos.z) {
                    LOGGER.warn("Tile entity serialized in chunk " + chunkPos + " in world '" + world.getWorld().getName() + "' positioned at " + blockposition + " is located outside of the chunk");
                    continue;
                }
                // Paper end - do not read tile entities positioned outside the chunk
                ((ChunkAccess) object1).setBlockEntityNbt(nbttagcompound4);
            }

            CompoundTag nbttagcompound5 = nbt.getCompound("CarvingMasks");
            Iterator iterator1 = nbttagcompound5.getAllKeys().iterator();

            while (iterator1.hasNext()) {
                String s1 = (String) iterator1.next();
                GenerationStep.Carving worldgenstage_features = GenerationStep.Carving.valueOf(s1);

                protochunk1.setCarvingMask(worldgenstage_features, new CarvingMask(nbttagcompound5.getLongArray(s1), ((ChunkAccess) object1).getMinBuildHeight()));
            }

            return protochunk1;
        }
    }

    private static void logErrors(ChunkPos chunkPos, int y, String message) {
        ChunkSerializer.LOGGER.error("Recoverable errors when loading section [{}, {}, {}]: {}", new Object[]{chunkPos.x, y, chunkPos.z, message});
    }

    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS));
    }

    // CraftBukkit start - read/write
    private static Codec<PalettedContainer<Holder<Biome>>> makeBiomeCodecRW(Registry<Biome> iregistry) {
        return PalettedContainer.codecRW(iregistry.asHolderIdMap(), iregistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, iregistry.getHolderOrThrow(Biomes.PLAINS));
    }
    // CraftBukkit end

    // Paper start - async chunk saving
    // must be called sync
    public static ca.spottedleaf.moonrise.patches.chunk_system.async_save.AsyncChunkSaveData getAsyncSaveData(ServerLevel world, ChunkAccess chunk) {
        ca.spottedleaf.moonrise.common.util.TickThread.ensureTickThread(world, chunk.locX, chunk.locZ, "Preparing async chunk save data");

        final CompoundTag tickLists = new CompoundTag();
        ChunkSerializer.saveTicks(world, tickLists, chunk.getTicksForSerialization());

        ListTag blockEntitiesSerialized = new ListTag();
        for (final BlockPos blockPos : chunk.getBlockEntitiesPos()) {
            final CompoundTag blockEntityNbt = chunk.getBlockEntityNbtForSaving(blockPos, world.registryAccess());
            if (blockEntityNbt != null) {
                blockEntitiesSerialized.add(blockEntityNbt);
            }
        }

        return new ca.spottedleaf.moonrise.patches.chunk_system.async_save.AsyncChunkSaveData(
            tickLists.get(BLOCK_TICKS_TAG),
            tickLists.get(FLUID_TICKS_TAG),
            blockEntitiesSerialized,
            world.getGameTime()
        );
     }
    // Paper end - async chunk saving

    public static CompoundTag write(ServerLevel world, ChunkAccess chunk) {
        // Paper start - async chunk saving
        return saveChunk(world, chunk, null);
    }
    public static CompoundTag saveChunk(ServerLevel world, ChunkAccess chunk, ca.spottedleaf.moonrise.patches.chunk_system.async_save.AsyncChunkSaveData asyncsavedata) {
        // Paper end - async chunk saving
        ChunkPos chunkcoordintpair = chunk.getPos();
        CompoundTag nbttagcompound = NbtUtils.addCurrentDataVersion(new CompoundTag());

        nbttagcompound.putInt("xPos", chunkcoordintpair.x);
        nbttagcompound.putInt("yPos", chunk.getMinSection());
        nbttagcompound.putInt("zPos", chunkcoordintpair.z);
        nbttagcompound.putLong("LastUpdate", asyncsavedata != null ? asyncsavedata.worldTime() : world.getGameTime()); // Paper - async chunk saving
        nbttagcompound.putLong("InhabitedTime", chunk.getInhabitedTime());
        nbttagcompound.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getPersistedStatus()).toString());
        BlendingData blendingdata = chunk.getBlendingData();
        DataResult<Tag> dataresult; // CraftBukkit - decompile error
        Logger logger;

        if (blendingdata != null) {
            dataresult = BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingdata);
            logger = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbttagcompound.put("blending_data", nbtbase);
            });
        }

        BelowZeroRetrogen belowzeroretrogen = chunk.getBelowZeroRetrogen();

        if (belowzeroretrogen != null) {
            dataresult = BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowzeroretrogen);
            logger = ChunkSerializer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbttagcompound.put("below_zero_retrogen", nbtbase);
            });
        }

        UpgradeData chunkconverter = chunk.getUpgradeData();

        if (!chunkconverter.isEmpty()) {
            nbttagcompound.put("UpgradeData", chunkconverter.write());
        }

        LevelChunkSection[] achunksection = chunk.getSections();
        ListTag nbttaglist = new ListTag();
        ThreadedLevelLightEngine lightenginethreaded = world.getChunkSource().getLightEngine();
        Registry<Biome> iregistry = world.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = ChunkSerializer.makeBiomeCodec(iregistry);
        boolean flag = chunk.isLightCorrect();

        for (int i = lightenginethreaded.getMinLightSection(); i < lightenginethreaded.getMaxLightSection(); ++i) {
            int j = chunk.getSectionIndexFromSectionY(i);
            boolean flag1 = j >= 0 && j < achunksection.length;
            DataLayer nibblearray = lightenginethreaded.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkcoordintpair, i));
            DataLayer nibblearray1 = lightenginethreaded.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkcoordintpair, i));

            if (flag1 || nibblearray != null || nibblearray1 != null) {
                CompoundTag nbttagcompound1 = new CompoundTag();

                if (flag1) {
                    LevelChunkSection chunksection = achunksection[j];

                    nbttagcompound1.put("block_states", (Tag) ChunkSerializer.BLOCK_STATE_CODEC.encodeStart(NbtOps.INSTANCE, chunksection.getStates()).getOrThrow());
                    nbttagcompound1.put("biomes", (Tag) codec.encodeStart(NbtOps.INSTANCE, chunksection.getBiomes()).getOrThrow());
                }

                if (nibblearray != null && !nibblearray.isEmpty()) {
                    nbttagcompound1.putByteArray("BlockLight", nibblearray.getData());
                }

                if (nibblearray1 != null && !nibblearray1.isEmpty()) {
                    nbttagcompound1.putByteArray("SkyLight", nibblearray1.getData());
                }

                if (!nbttagcompound1.isEmpty()) {
                    nbttagcompound1.putByte("Y", (byte) i);
                    nbttaglist.add(nbttagcompound1);
                }
            }
        }

        nbttagcompound.put("sections", nbttaglist);
        if (flag) {
            nbttagcompound.putBoolean("isLightOn", true);
        }

        // Paper start - async chunk saving
        ListTag nbttaglist1;
        Iterator<BlockPos> iterator;
        if (asyncsavedata != null) {
            nbttaglist1 = asyncsavedata.blockEntities();
            iterator = java.util.Collections.emptyIterator();
        } else {
            nbttaglist1 = new ListTag();
            iterator = chunk.getBlockEntitiesPos().iterator();
        }
        // Paper end - async chunk saving

        CompoundTag nbttagcompound2;

        while (iterator.hasNext()) {
            BlockPos blockposition = (BlockPos) iterator.next();

            nbttagcompound2 = chunk.getBlockEntityNbtForSaving(blockposition, world.registryAccess());
            if (nbttagcompound2 != null) {
                nbttaglist1.add(nbttagcompound2);
            }
        }

        nbttagcompound.put("block_entities", nbttaglist1);
        if (chunk.getPersistedStatus().getChunkType() == ChunkType.PROTOCHUNK) {
            ProtoChunk protochunk = (ProtoChunk) chunk;
            ListTag nbttaglist2 = new ListTag();

            nbttaglist2.addAll(protochunk.getEntities());
            nbttagcompound.put("entities", nbttaglist2);
            nbttagcompound2 = new CompoundTag();
            GenerationStep.Carving[] aworldgenstage_features = GenerationStep.Carving.values();
            int k = aworldgenstage_features.length;

            for (int l = 0; l < k; ++l) {
                GenerationStep.Carving worldgenstage_features = aworldgenstage_features[l];
                CarvingMask carvingmask = protochunk.getCarvingMask(worldgenstage_features);

                if (carvingmask != null) {
                    nbttagcompound2.putLongArray(worldgenstage_features.toString(), carvingmask.toArray());
                }
            }

            nbttagcompound.put("CarvingMasks", nbttagcompound2);
        }

        // Paper start
        if (asyncsavedata != null) {
            nbttagcompound.put(BLOCK_TICKS_TAG, asyncsavedata.blockTickList());
            nbttagcompound.put(FLUID_TICKS_TAG, asyncsavedata.fluidTickList());
        } else {
        ChunkSerializer.saveTicks(world, nbttagcompound, chunk.getTicksForSerialization());
        }
        // Paper end
        nbttagcompound.put("PostProcessing", ChunkSerializer.packOffsets(chunk.getPostProcessing()));
        CompoundTag nbttagcompound3 = new CompoundTag();
        Iterator iterator1 = chunk.getHeightmaps().iterator();

        while (iterator1.hasNext()) {
            Entry<Heightmap.Types, Heightmap> entry = (Entry) iterator1.next();

            if (chunk.getPersistedStatus().heightmapsAfter().contains(entry.getKey())) {
                nbttagcompound3.put(((Heightmap.Types) entry.getKey()).getSerializationKey(), new LongArrayTag(((Heightmap) entry.getValue()).getRawData()));
            }
        }

        nbttagcompound.put("Heightmaps", nbttagcompound3);
        nbttagcompound.put("structures", ChunkSerializer.packStructureData(StructurePieceSerializationContext.fromLevel(world), chunkcoordintpair, chunk.getAllStarts(), chunk.getAllReferences()));
        // CraftBukkit start - store chunk persistent data in nbt
        if (!chunk.persistentDataContainer.isEmpty()) { // SPIGOT-6814: Always save PDC to account for 1.17 to 1.18 chunk upgrading.
            nbttagcompound.put("ChunkBukkitValues", chunk.persistentDataContainer.toTagCompound());
        }
        // CraftBukkit end
        ca.spottedleaf.moonrise.patches.starlight.util.SaveUtil.saveLightHook(world, chunk, nbttagcompound); // Paper - rewrite chunk system
        return nbttagcompound;
    }

    private static void saveTicks(ServerLevel world, CompoundTag nbt, ChunkAccess.TicksToSave tickSchedulers) {
        long i = world.getLevelData().getGameTime();

        nbt.put("block_ticks", tickSchedulers.blocks().save(i, (block) -> {
            return BuiltInRegistries.BLOCK.getKey(block).toString();
        }));
        nbt.put("fluid_ticks", tickSchedulers.fluids().save(i, (fluidtype) -> {
            return BuiltInRegistries.FLUID.getKey(fluidtype).toString();
        }));
    }

    public static ChunkType getChunkTypeFromTag(@Nullable CompoundTag nbt) {
        return nbt != null ? ChunkStatus.byName(nbt.getString("Status")).getChunkType() : ChunkType.PROTOCHUNK;
    }

    @Nullable
    private static LevelChunk.PostLoadProcessor postLoadChunk(ServerLevel world, CompoundTag nbt) {
        ListTag nbttaglist = ChunkSerializer.getListOfCompoundsOrNull(nbt, "entities");
        ListTag nbttaglist1 = ChunkSerializer.getListOfCompoundsOrNull(nbt, "block_entities");

        return nbttaglist == null && nbttaglist1 == null ? null : (chunk) -> {
            if (nbttaglist != null) {
                world.addLegacyChunkEntities(EntityType.loadEntitiesRecursive(nbttaglist, world), chunk.getPos()); // Paper - rewrite chunk system
            }

            if (nbttaglist1 != null) {
                for (int i = 0; i < nbttaglist1.size(); ++i) {
                    CompoundTag nbttagcompound1 = nbttaglist1.getCompound(i);
                    boolean flag = nbttagcompound1.getBoolean("keepPacked");

                    // Paper start - do not read tile entities positioned outside the chunk
                    BlockPos blockposition = BlockEntity.getPosFromTag(nbttagcompound1); // moved up
                    ChunkPos chunkPos = chunk.getPos();
                    if ((blockposition.getX() >> 4) != chunkPos.x || (blockposition.getZ() >> 4) != chunkPos.z) {
                        LOGGER.warn("Tile entity serialized in chunk " + chunkPos + " in world '" + world.getWorld().getName() + "' positioned at " + blockposition + " is located outside of the chunk");
                        continue;
                    }
                    // Paper end - do not read tile entities positioned outside the chunk

                    if (flag) {
                        chunk.setBlockEntityNbt(nbttagcompound1);
                    } else {
                        // Paper - do not read tile entities positioned outside the chunk; move up
                        BlockEntity tileentity = BlockEntity.loadStatic(blockposition, chunk.getBlockState(blockposition), nbttagcompound1, world.registryAccess());

                        if (tileentity != null) {
                            chunk.setBlockEntity(tileentity);
                        }
                    }
                }
            }

        };
    }

    @Nullable
    private static ListTag getListOfCompoundsOrNull(CompoundTag nbt, String key) {
        ListTag nbttaglist = nbt.getList(key, 10);

        return nbttaglist.isEmpty() ? null : nbttaglist;
    }

    private static CompoundTag packStructureData(StructurePieceSerializationContext context, ChunkPos pos, Map<Structure, StructureStart> starts, Map<Structure, LongSet> references) {
        CompoundTag nbttagcompound = new CompoundTag();
        CompoundTag nbttagcompound1 = new CompoundTag();
        Registry<Structure> iregistry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Iterator iterator = starts.entrySet().iterator();

        while (iterator.hasNext()) {
            Entry<Structure, StructureStart> entry = (Entry) iterator.next();
            ResourceLocation minecraftkey = iregistry.getKey((Structure) entry.getKey());

            nbttagcompound1.put(minecraftkey.toString(), ((StructureStart) entry.getValue()).createTag(context, pos));
        }

        nbttagcompound.put("starts", nbttagcompound1);
        CompoundTag nbttagcompound2 = new CompoundTag();
        Iterator iterator1 = references.entrySet().iterator();

        while (iterator1.hasNext()) {
            Entry<Structure, LongSet> entry1 = (Entry) iterator1.next();

            if (!((LongSet) entry1.getValue()).isEmpty()) {
                ResourceLocation minecraftkey1 = iregistry.getKey((Structure) entry1.getKey());

                nbttagcompound2.put(minecraftkey1.toString(), new LongArrayTag((LongSet) entry1.getValue()));
            }
        }

        nbttagcompound.put("References", nbttagcompound2);
        return nbttagcompound;
    }

    private static Map<Structure, StructureStart> unpackStructureStart(StructurePieceSerializationContext context, CompoundTag nbt, long worldSeed) {
        Map<Structure, StructureStart> map = Maps.newHashMap();
        Registry<Structure> iregistry = context.registryAccess().registryOrThrow(Registries.STRUCTURE);
        CompoundTag nbttagcompound1 = nbt.getCompound("starts");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            ResourceLocation minecraftkey = ResourceLocation.tryParse(s);
            Structure structure = (Structure) iregistry.get(minecraftkey);

            if (structure == null) {
                ChunkSerializer.LOGGER.error("Unknown structure start: {}", minecraftkey);
            } else {
                StructureStart structurestart = StructureStart.loadStaticStart(context, nbttagcompound1.getCompound(s), worldSeed);

                if (structurestart != null) {
                    // CraftBukkit start - load persistent data for structure start
                    net.minecraft.nbt.Tag persistentBase = nbttagcompound1.getCompound(s).get("StructureBukkitValues");
                    if (persistentBase instanceof CompoundTag) {
                        structurestart.persistentDataContainer.putAll((CompoundTag) persistentBase);
                    }
                    // CraftBukkit end
                    map.put(structure, structurestart);
                }
            }
        }

        return map;
    }

    private static Map<Structure, LongSet> unpackStructureReferences(RegistryAccess registryManager, ChunkPos pos, CompoundTag nbt) {
        Map<Structure, LongSet> map = Maps.newHashMap();
        Registry<Structure> iregistry = registryManager.registryOrThrow(Registries.STRUCTURE);
        CompoundTag nbttagcompound1 = nbt.getCompound("References");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            ResourceLocation minecraftkey = ResourceLocation.tryParse(s);
            Structure structure = (Structure) iregistry.get(minecraftkey);

            if (structure == null) {
                ChunkSerializer.LOGGER.warn("Found reference to unknown structure '{}' in chunk {}, discarding", minecraftkey, pos);
            } else {
                long[] along = nbttagcompound1.getLongArray(s);

                if (along.length != 0) {
                    map.put(structure, new LongOpenHashSet(Arrays.stream(along).filter((i) -> {
                        ChunkPos chunkcoordintpair1 = new ChunkPos(i);

                        if (chunkcoordintpair1.getChessboardDistance(pos) > 8) {
                            ChunkSerializer.LOGGER.warn("Found invalid structure reference [ {} @ {} ] for chunk {}.", new Object[]{minecraftkey, chunkcoordintpair1, pos});
                            return false;
                        } else {
                            return true;
                        }
                    }).toArray()));
                }
            }
        }

        return map;
    }

    public static ListTag packOffsets(ShortList[] lists) {
        ListTag nbttaglist = new ListTag();
        ShortList[] ashortlist1 = lists;
        int i = lists.length;

        for (int j = 0; j < i; ++j) {
            ShortList shortlist = ashortlist1[j];
            ListTag nbttaglist1 = new ListTag();

            if (shortlist != null) {
                ShortListIterator shortlistiterator = shortlist.iterator();

                while (shortlistiterator.hasNext()) {
                    Short oshort = (Short) shortlistiterator.next();

                    nbttaglist1.add(ShortTag.valueOf(oshort));
                }
            }

            nbttaglist.add(nbttaglist1);
        }

        return nbttaglist;
    }

    public static class ChunkReadException extends NbtException {

        public ChunkReadException(String message) {
            super(message);
        }
    }
}
