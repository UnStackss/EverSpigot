package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.OptionalDynamic;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.slf4j.Logger;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import org.bukkit.Bukkit;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
// CraftBukkit end

public class PrimaryLevelData implements ServerLevelData, WorldData {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String LEVEL_NAME = "LevelName";
    protected static final String PLAYER = "Player";
    protected static final String WORLD_GEN_SETTINGS = "WorldGenSettings";
    public LevelSettings settings;
    private final WorldOptions worldOptions;
    private final PrimaryLevelData.SpecialWorldProperty specialWorldProperty;
    private final Lifecycle worldGenSettingsLifecycle;
    private BlockPos spawnPos;
    private float spawnAngle;
    private long gameTime;
    private long dayTime;
    @Nullable
    private final CompoundTag loadedPlayerTag;
    private final int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private boolean difficultyLocked;
    private WorldBorder.Settings worldBorder;
    private EndDragonFight.Data endDragonFightData;
    @Nullable
    private CompoundTag customBossEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    @Nullable
    private UUID wanderingTraderId;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final Set<String> removedFeatureFlags;
    private final TimerQueue<MinecraftServer> scheduledEvents;
    // CraftBukkit start - Add world and pdc
    public Registry<LevelStem> customDimensions;
    private ServerLevel world;
    protected Tag pdc;

    public void setWorld(ServerLevel world) {
        if (this.world != null) {
            return;
        }
        this.world = world;
        world.getWorld().readBukkitValues(this.pdc);
        this.pdc = null;
    }
    // CraftBukkit end

    private PrimaryLevelData(@Nullable CompoundTag playerData, boolean modded, BlockPos spawnPos, float spawnAngle, long time, long timeOfDay, int version, int clearWeatherTime, int rainTime, boolean raining, int thunderTime, boolean thundering, boolean initialized, boolean difficultyLocked, WorldBorder.Settings worldBorder, int wanderingTraderSpawnDelay, int wanderingTraderSpawnChance, @Nullable UUID wanderingTraderId, Set<String> serverBrands, Set<String> removedFeatures, TimerQueue<MinecraftServer> scheduledEvents, @Nullable CompoundTag customBossEvents, EndDragonFight.Data dragonFight, LevelSettings levelInfo, WorldOptions generatorOptions, PrimaryLevelData.SpecialWorldProperty specialProperty, Lifecycle lifecycle) {
        this.wasModded = modded;
        this.spawnPos = spawnPos;
        this.spawnAngle = spawnAngle;
        this.gameTime = time;
        this.dayTime = timeOfDay;
        this.version = version;
        this.clearWeatherTime = clearWeatherTime;
        this.rainTime = rainTime;
        this.raining = raining;
        this.thunderTime = thunderTime;
        this.thundering = thundering;
        this.initialized = initialized;
        this.difficultyLocked = difficultyLocked;
        this.worldBorder = worldBorder;
        this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
        this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
        this.wanderingTraderId = wanderingTraderId;
        this.knownServerBrands = serverBrands;
        this.removedFeatureFlags = removedFeatures;
        this.loadedPlayerTag = playerData;
        this.scheduledEvents = scheduledEvents;
        this.customBossEvents = customBossEvents;
        this.endDragonFightData = dragonFight;
        this.settings = levelInfo;
        this.worldOptions = generatorOptions;
        this.specialWorldProperty = specialProperty;
        this.worldGenSettingsLifecycle = lifecycle;
    }

    public PrimaryLevelData(LevelSettings levelInfo, WorldOptions generatorOptions, PrimaryLevelData.SpecialWorldProperty specialProperty, Lifecycle lifecycle) {
        this((CompoundTag) null, false, BlockPos.ZERO, 0.0F, 0L, 0L, 19133, 0, 0, false, 0, false, false, false, WorldBorder.DEFAULT_SETTINGS, 0, 0, (UUID) null, Sets.newLinkedHashSet(), new HashSet(), new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS), (CompoundTag) null, EndDragonFight.Data.DEFAULT, levelInfo.copy(), generatorOptions, specialProperty, lifecycle);
    }

    public static <T> PrimaryLevelData parse(Dynamic<T> dynamic, LevelSettings info, PrimaryLevelData.SpecialWorldProperty specialProperty, WorldOptions generatorOptions, Lifecycle lifecycle) {
        long i = dynamic.get("Time").asLong(0L);
        OptionalDynamic<T> optionaldynamic = dynamic.get("Player"); // CraftBukkit - decompile error
        Codec codec = CompoundTag.CODEC;

        Objects.requireNonNull(codec);
        CompoundTag nbttagcompound = (CompoundTag) optionaldynamic.flatMap(codec::parse).result().orElse((Object) null);
        boolean flag = dynamic.get("WasModded").asBoolean(false);
        BlockPos blockposition = new BlockPos(dynamic.get("SpawnX").asInt(0), dynamic.get("SpawnY").asInt(0), dynamic.get("SpawnZ").asInt(0));
        float f = dynamic.get("SpawnAngle").asFloat(0.0F);
        long j = dynamic.get("DayTime").asLong(i);
        int k = LevelVersion.parse(dynamic).levelDataVersion();
        int l = dynamic.get("clearWeatherTime").asInt(0);
        int i1 = dynamic.get("rainTime").asInt(0);
        boolean flag1 = dynamic.get("raining").asBoolean(false);
        int j1 = dynamic.get("thunderTime").asInt(0);
        boolean flag2 = dynamic.get("thundering").asBoolean(false);
        boolean flag3 = dynamic.get("initialized").asBoolean(true);
        boolean flag4 = dynamic.get("DifficultyLocked").asBoolean(false);
        WorldBorder.Settings worldborder_c = WorldBorder.Settings.read(dynamic, WorldBorder.DEFAULT_SETTINGS);
        int k1 = dynamic.get("WanderingTraderSpawnDelay").asInt(0);
        int l1 = dynamic.get("WanderingTraderSpawnChance").asInt(0);
        UUID uuid = (UUID) dynamic.get("WanderingTraderId").read(UUIDUtil.CODEC).result().orElse(null); // CraftBukkit - decompile error
        Set set = (Set) dynamic.get("ServerBrands").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().stream();
        }).collect(Collectors.toCollection(Sets::newLinkedHashSet));
        Set set1 = (Set) dynamic.get("removed_features").asStream().flatMap((dynamic1) -> {
            return dynamic1.asString().result().stream();
        }).collect(Collectors.toSet());
        TimerQueue customfunctioncallbacktimerqueue = new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS, dynamic.get("ScheduledEvents").asStream());
        CompoundTag nbttagcompound1 = (CompoundTag) dynamic.get("CustomBossEvents").orElseEmptyMap().getValue();
        DataResult<EndDragonFight.Data> dataresult = dynamic.get("DragonFight").read(EndDragonFight.Data.CODEC); // CraftBukkit - decompile error
        Logger logger = PrimaryLevelData.LOGGER;

        Objects.requireNonNull(logger);
        return new PrimaryLevelData(nbttagcompound, flag, blockposition, f, i, j, k, l, i1, flag1, j1, flag2, flag3, flag4, worldborder_c, k1, l1, uuid, set, set1, customfunctioncallbacktimerqueue, nbttagcompound1, (EndDragonFight.Data) dataresult.resultOrPartial(logger::error).orElse(EndDragonFight.Data.DEFAULT), info, generatorOptions, specialProperty, lifecycle);
    }

    @Override
    public CompoundTag createTag(RegistryAccess registryManager, @Nullable CompoundTag playerNbt) {
        if (playerNbt == null) {
            playerNbt = this.loadedPlayerTag;
        }

        CompoundTag nbttagcompound1 = new CompoundTag();

        this.setTagData(registryManager, nbttagcompound1, playerNbt);
        return nbttagcompound1;
    }

    private void setTagData(RegistryAccess registryManager, CompoundTag levelNbt, @Nullable CompoundTag playerNbt) {
        levelNbt.put("ServerBrands", PrimaryLevelData.stringCollectionToTag(this.knownServerBrands));
        levelNbt.putBoolean("WasModded", this.wasModded);
        if (!this.removedFeatureFlags.isEmpty()) {
            levelNbt.put("removed_features", PrimaryLevelData.stringCollectionToTag(this.removedFeatureFlags));
        }

        CompoundTag nbttagcompound2 = new CompoundTag();

        nbttagcompound2.putString("Name", SharedConstants.getCurrentVersion().getName());
        nbttagcompound2.putInt("Id", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        nbttagcompound2.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().isStable());
        nbttagcompound2.putString("Series", SharedConstants.getCurrentVersion().getDataVersion().getSeries());
        levelNbt.put("Version", nbttagcompound2);
        NbtUtils.addCurrentDataVersion(levelNbt);
        DynamicOps<Tag> dynamicops = registryManager.createSerializationContext(NbtOps.INSTANCE);
        DataResult<Tag> dataresult = WorldGenSettings.encode(dynamicops, this.worldOptions, new WorldDimensions(this.customDimensions != null ? this.customDimensions : registryManager.registryOrThrow(Registries.LEVEL_STEM))); // CraftBukkit
        Logger logger = PrimaryLevelData.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(Util.prefix("WorldGenSettings: ", logger::error)).ifPresent((nbtbase) -> {
            levelNbt.put("WorldGenSettings", nbtbase);
        });
        levelNbt.putInt("GameType", this.settings.gameType().getId());
        levelNbt.putInt("SpawnX", this.spawnPos.getX());
        levelNbt.putInt("SpawnY", this.spawnPos.getY());
        levelNbt.putInt("SpawnZ", this.spawnPos.getZ());
        levelNbt.putFloat("SpawnAngle", this.spawnAngle);
        levelNbt.putLong("Time", this.gameTime);
        levelNbt.putLong("DayTime", this.dayTime);
        levelNbt.putLong("LastPlayed", Util.getEpochMillis());
        levelNbt.putString("LevelName", this.settings.levelName());
        levelNbt.putInt("version", 19133);
        levelNbt.putInt("clearWeatherTime", this.clearWeatherTime);
        levelNbt.putInt("rainTime", this.rainTime);
        levelNbt.putBoolean("raining", this.raining);
        levelNbt.putInt("thunderTime", this.thunderTime);
        levelNbt.putBoolean("thundering", this.thundering);
        levelNbt.putBoolean("hardcore", this.settings.hardcore());
        levelNbt.putBoolean("allowCommands", this.settings.allowCommands());
        levelNbt.putBoolean("initialized", this.initialized);
        this.worldBorder.write(levelNbt);
        levelNbt.putByte("Difficulty", (byte) this.settings.difficulty().getId());
        levelNbt.putBoolean("DifficultyLocked", this.difficultyLocked);
        levelNbt.put("GameRules", this.settings.gameRules().createTag());
        levelNbt.put("DragonFight", (Tag) EndDragonFight.Data.CODEC.encodeStart(NbtOps.INSTANCE, this.endDragonFightData).getOrThrow());
        if (playerNbt != null) {
            levelNbt.put("Player", playerNbt);
        }

        WorldDataConfiguration.CODEC.encodeStart(NbtOps.INSTANCE, this.settings.getDataConfiguration()).ifSuccess((nbtbase) -> {
            levelNbt.merge((CompoundTag) nbtbase);
        }).ifError((error) -> {
            PrimaryLevelData.LOGGER.warn("Failed to encode configuration {}", error.message());
        });
        if (this.customBossEvents != null) {
            levelNbt.put("CustomBossEvents", this.customBossEvents);
        }

        levelNbt.put("ScheduledEvents", this.scheduledEvents.store());
        levelNbt.putInt("WanderingTraderSpawnDelay", this.wanderingTraderSpawnDelay);
        levelNbt.putInt("WanderingTraderSpawnChance", this.wanderingTraderSpawnChance);
        if (this.wanderingTraderId != null) {
            levelNbt.putUUID("WanderingTraderId", this.wanderingTraderId);
        }

        levelNbt.putString("Bukkit.Version", Bukkit.getName() + "/" + Bukkit.getVersion() + "/" + Bukkit.getBukkitVersion()); // CraftBukkit
        this.world.getWorld().storeBukkitValues(levelNbt); // CraftBukkit - add pdc
    }

    private static ListTag stringCollectionToTag(Set<String> strings) {
        ListTag nbttaglist = new ListTag();
        Stream<StringTag> stream = strings.stream().map(StringTag::valueOf); // CraftBukkit - decompile error

        Objects.requireNonNull(nbttaglist);
        stream.forEach(nbttaglist::add);
        return nbttaglist;
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.spawnPos;
    }

    @Override
    public float getSpawnAngle() {
        return this.spawnAngle;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public long getDayTime() {
        return this.dayTime;
    }

    @Nullable
    @Override
    public CompoundTag getLoadedPlayerTag() {
        return this.loadedPlayerTag;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public void setDayTime(long timeOfDay) {
        this.dayTime = timeOfDay;
    }

    @Override
    public void setSpawn(BlockPos pos, float angle) {
        this.spawnPos = pos.immutable();
        this.spawnAngle = angle;
    }

    @Override
    public String getLevelName() {
        return this.settings.levelName();
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int clearWeatherTime) {
        this.clearWeatherTime = clearWeatherTime;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        // CraftBukkit start
        if (this.thundering == thundering) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(this.getLevelName());
        if (world != null) {
            ThunderChangeEvent thunder = new ThunderChangeEvent(world, thundering);
            Bukkit.getServer().getPluginManager().callEvent(thunder);
            if (thunder.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        // CraftBukkit start
        if (this.raining == raining) {
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(this.getLevelName());
        if (world != null) {
            WeatherChangeEvent weather = new WeatherChangeEvent(world, raining);
            Bukkit.getServer().getPluginManager().callEvent(weather);
            if (weather.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end
        this.raining = raining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    @Override
    public GameType getGameType() {
        return this.settings.gameType();
    }

    @Override
    public void setGameType(GameType gameMode) {
        this.settings = this.settings.withGameType(gameMode);
    }

    @Override
    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    @Override
    public boolean isAllowCommands() {
        return this.settings.allowCommands();
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public GameRules getGameRules() {
        return this.settings.gameRules();
    }

    @Override
    public WorldBorder.Settings getWorldBorder() {
        return this.worldBorder;
    }

    @Override
    public void setWorldBorder(WorldBorder.Settings worldBorder) {
        this.worldBorder = worldBorder;
    }

    @Override
    public Difficulty getDifficulty() {
        return this.settings.difficulty();
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        this.settings = this.settings.withDifficulty(difficulty);
        // CraftBukkit start
        ClientboundChangeDifficultyPacket packet = new ClientboundChangeDifficultyPacket(this.getDifficulty(), this.isDifficultyLocked());
        for (ServerPlayer player : (java.util.List<ServerPlayer>) (java.util.List) this.world.players()) {
            player.connection.send(packet);
        }
        // CraftBukkit end
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    @Override
    public void setDifficultyLocked(boolean difficultyLocked) {
        this.difficultyLocked = difficultyLocked;
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    @Override
    public void fillCrashReportCategory(CrashReportCategory reportSection, LevelHeightAccessor world) {
        ServerLevelData.super.fillCrashReportCategory(reportSection, world);
        WorldData.super.fillCrashReportCategory(reportSection);
    }

    @Override
    public WorldOptions worldGenOptions() {
        return this.worldOptions;
    }

    @Override
    public boolean isFlatWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.FLAT;
    }

    @Override
    public boolean isDebugWorld() {
        return this.specialWorldProperty == PrimaryLevelData.SpecialWorldProperty.DEBUG;
    }

    @Override
    public Lifecycle worldGenSettingsLifecycle() {
        return this.worldGenSettingsLifecycle;
    }

    @Override
    public EndDragonFight.Data endDragonFightData() {
        return this.endDragonFightData;
    }

    @Override
    public void setEndDragonFightData(EndDragonFight.Data dragonFight) {
        this.endDragonFightData = dragonFight;
    }

    @Override
    public WorldDataConfiguration getDataConfiguration() {
        return this.settings.getDataConfiguration();
    }

    @Override
    public void setDataConfiguration(WorldDataConfiguration dataConfiguration) {
        this.settings = this.settings.withDataConfiguration(dataConfiguration);
    }

    @Nullable
    @Override
    public CompoundTag getCustomBossEvents() {
        return this.customBossEvents;
    }

    @Override
    public void setCustomBossEvents(@Nullable CompoundTag customBossEvents) {
        this.customBossEvents = customBossEvents;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int wanderingTraderSpawnDelay) {
        this.wanderingTraderSpawnDelay = wanderingTraderSpawnDelay;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int wanderingTraderSpawnChance) {
        this.wanderingTraderSpawnChance = wanderingTraderSpawnChance;
    }

    @Nullable
    @Override
    public UUID getWanderingTraderId() {
        return this.wanderingTraderId;
    }

    @Override
    public void setWanderingTraderId(UUID wanderingTraderId) {
        this.wanderingTraderId = wanderingTraderId;
    }

    @Override
    public void setModdedInfo(String brand, boolean modded) {
        this.knownServerBrands.add(brand);
        this.wasModded |= modded;
    }

    @Override
    public boolean wasModded() {
        return this.wasModded;
    }

    @Override
    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    @Override
    public Set<String> getRemovedFeatureFlags() {
        return Set.copyOf(this.removedFeatureFlags);
    }

    @Override
    public ServerLevelData overworldData() {
        return this;
    }

    @Override
    public LevelSettings getLevelSettings() {
        return this.settings.copy();
    }

    // CraftBukkit start - Check if the name stored in NBT is the correct one
    public void checkName(String name) {
        if (!this.settings.levelName.equals(name)) {
            this.settings.levelName = name;
        }
    }
    // CraftBukkit end

    /** @deprecated */
    @Deprecated
    public static enum SpecialWorldProperty {

        NONE, FLAT, DEBUG;

        private SpecialWorldProperty() {}
    }
}
