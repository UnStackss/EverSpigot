package net.minecraft.server.players;

import co.aikar.timings.MinecraftTimings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.File;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.FileUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDelayPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderWarningDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.stream.Collectors;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRespawnEvent.RespawnReason;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
// CraftBukkit end

public abstract class PlayerList {

    public static final File USERBANLIST_FILE = new File("banned-players.json");
    public static final File IPBANLIST_FILE = new File("banned-ips.json");
    public static final File OPLIST_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    public static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    public static final Component DUPLICATE_LOGIN_DISCONNECT_MESSAGE = Component.translatable("multiplayer.disconnect.duplicate_login");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEND_PLAYER_INFO_INTERVAL = 600;
    private static final SimpleDateFormat BAN_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    public final List<ServerPlayer> players = new java.util.concurrent.CopyOnWriteArrayList(); // CraftBukkit - ArrayList -> CopyOnWriteArrayList: Iterator safety
    private final Map<UUID, ServerPlayer> playersByUUID = Maps.newHashMap();
    private final UserBanList bans;
    private final IpBanList ipBans;
    private final ServerOpList ops;
    private final UserWhiteList whitelist;
    // CraftBukkit start
    // private final Map<UUID, ServerStatisticManager> stats;
    // private final Map<UUID, AdvancementDataPlayer> advancements;
    // CraftBukkit end
    public final PlayerDataStorage playerIo;
    private boolean doWhiteList;
    private final LayeredRegistryAccess<RegistryLayer> registries;
    public int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean allowCommandsForAllPlayers;
    private static final boolean ALLOW_LOGOUTIVATOR = false;
    private int sendAllPlayerInfoIn;

    // CraftBukkit start
    private CraftServer cserver;
    private final Map<String,ServerPlayer> playersByName = new java.util.HashMap<>();
    public @Nullable String collideRuleTeamName; // Paper - Configurable player collision

    public PlayerList(MinecraftServer server, LayeredRegistryAccess<RegistryLayer> registryManager, PlayerDataStorage saveHandler, int maxPlayers) {
        this.cserver = server.server = new CraftServer((DedicatedServer) server, this);
        server.console = new com.destroystokyo.paper.console.TerminalConsoleCommandSender(); // Paper
        // CraftBukkit end

        this.bans = new UserBanList(PlayerList.USERBANLIST_FILE);
        this.ipBans = new IpBanList(PlayerList.IPBANLIST_FILE);
        this.ops = new ServerOpList(PlayerList.OPLIST_FILE);
        this.whitelist = new UserWhiteList(PlayerList.WHITELIST_FILE);
        // CraftBukkit start
        // this.stats = Maps.newHashMap();
        // this.advancements = Maps.newHashMap();
        // CraftBukkit end
        this.server = server;
        this.registries = registryManager;
        this.maxPlayers = maxPlayers;
        this.playerIo = saveHandler;
    }
    abstract public void loadAndSaveFiles(); // Paper - fix converting txt to json file; moved from DedicatedPlayerList constructor

    public void placeNewPlayer(Connection connection, ServerPlayer player, CommonListenerCookie clientData) {
        player.isRealPlayer = true; // Paper
        player.loginTime = System.currentTimeMillis(); // Paper - Replace OfflinePlayer#getLastPlayed
        GameProfile gameprofile = player.getGameProfile();
        GameProfileCache usercache = this.server.getProfileCache();
        // Optional optional; // CraftBukkit - decompile error
        String s;

        if (usercache != null) {
            Optional<GameProfile> optional = usercache.get(gameprofile.getId()); // CraftBukkit - decompile error
            s = (String) optional.map(GameProfile::getName).orElse(gameprofile.getName());
            usercache.add(gameprofile);
        } else {
            s = gameprofile.getName();
        }

        Optional<CompoundTag> optional = this.load(player); // CraftBukkit - decompile error
        ResourceKey<Level> resourcekey = null; // Paper
        // CraftBukkit start - Better rename detection
        if (optional.isPresent()) {
            CompoundTag nbttagcompound = optional.get();
            if (nbttagcompound.contains("bukkit")) {
                CompoundTag bukkit = nbttagcompound.getCompound("bukkit");
                s = bukkit.contains("lastKnownName", 8) ? bukkit.getString("lastKnownName") : s;
            }
        }
        // CraftBukkit end
        // Paper start - move logic in Entity to here, to use bukkit supplied world UUID & reset to main world spawn if no valid world is found
        boolean[] invalidPlayerWorld = {false};
        bukkitData: if (optional.isPresent()) {
            // The main way for bukkit worlds to store the world is the world UUID despite mojang adding custom worlds
            final org.bukkit.World bWorld;
            if (optional.get().contains("WorldUUIDMost") && optional.get().contains("WorldUUIDLeast")) {
                bWorld = org.bukkit.Bukkit.getServer().getWorld(new UUID(optional.get().getLong("WorldUUIDMost"), optional.get().getLong("WorldUUIDLeast")));
            } else if (optional.get().contains("world", net.minecraft.nbt.Tag.TAG_STRING)) { // Paper - legacy bukkit world name
                bWorld = org.bukkit.Bukkit.getServer().getWorld(optional.get().getString("world"));
            } else {
                break bukkitData; // if neither of the bukkit data points exist, proceed to the vanilla migration section
            }
            if (bWorld != null) {
                resourcekey = ((CraftWorld) bWorld).getHandle().dimension();
            } else {
                resourcekey = Level.OVERWORLD;
                invalidPlayerWorld[0] = true;
            }
        }
        if (resourcekey == null) { // only run the vanilla logic if we haven't found a world from the bukkit data
        // Below is the vanilla way of getting the dimension, this is for migration from vanilla servers
        resourcekey = optional.flatMap((nbttagcompound) -> {
            // Paper end
            DataResult<ResourceKey<Level>> dataresult = DimensionType.parseLegacy(new Dynamic(NbtOps.INSTANCE, nbttagcompound.get("Dimension"))); // CraftBukkit - decompile error
            Logger logger = PlayerList.LOGGER;

            Objects.requireNonNull(logger);
            // Paper start - reset to main world spawn if no valid world is found
            final Optional<ResourceKey<Level>> result = dataresult.resultOrPartial(logger::error);
            invalidPlayerWorld[0] = result.isEmpty();
            return result;
        }).orElse(Level.OVERWORLD); // Paper - revert to vanilla default main world, this isn't an "invalid world" since no player data existed
        }
        // Paper end
        ServerLevel worldserver = this.server.getLevel(resourcekey);
        ServerLevel worldserver1;

        if (worldserver == null) {
            PlayerList.LOGGER.warn("Unknown respawn dimension {}, defaulting to overworld", resourcekey);
            worldserver1 = this.server.overworld();
            invalidPlayerWorld[0] = true; // Paper - reset to main world if no world with parsed value is found
        } else {
            worldserver1 = worldserver;
        }

        // Paper start - Entity#getEntitySpawnReason
        if (optional.isEmpty()) {
            player.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT; // set Player SpawnReason to DEFAULT on first login
            // Paper start - reset to main world spawn if first spawn or invalid world
        }
        if (optional.isEmpty() || invalidPlayerWorld[0]) {
            // Paper end - reset to main world spawn if first spawn or invalid world
            player.moveTo(player.adjustSpawnLocation(worldserver1, worldserver1.getSharedSpawnPos()).getBottomCenter(), worldserver1.getSharedSpawnAngle(), 0.0F); // Paper - MC-200092 - fix first spawn pos yaw being ignored
        }
        // Paper end - Entity#getEntitySpawnReason
        player.setServerLevel(worldserver1);
        String s1 = connection.getLoggableAddress(this.server.logIPs());

        // Spigot start - spawn location event
        Player spawnPlayer = player.getBukkitEntity();
        org.spigotmc.event.player.PlayerSpawnLocationEvent ev = new org.spigotmc.event.player.PlayerSpawnLocationEvent(spawnPlayer, spawnPlayer.getLocation());
        this.cserver.getPluginManager().callEvent(ev);

        Location loc = ev.getSpawnLocation();
        worldserver1 = ((CraftWorld) loc.getWorld()).getHandle();

        player.spawnIn(worldserver1);
        player.gameMode.setLevel((ServerLevel) player.level());
        // Paper start - set raw so we aren't fully joined to the world (not added to chunk or world)
        player.setPosRaw(loc.getX(), loc.getY(), loc.getZ());
        player.setRot(loc.getYaw(), loc.getPitch());
        // Paper end - set raw so we aren't fully joined to the world
        // Spigot end

        // CraftBukkit - Moved message to after join
        // PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ({}, {}, {})", new Object[]{entityplayer.getName().getString(), s1, entityplayer.getId(), entityplayer.getX(), entityplayer.getY(), entityplayer.getZ()});
        LevelData worlddata = worldserver1.getLevelData();

        player.loadGameTypes((CompoundTag) optional.orElse(null)); // CraftBukkit - decompile error
        ServerGamePacketListenerImpl playerconnection = new ServerGamePacketListenerImpl(this.server, connection, player, clientData);

        connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(this.server.registryAccess())), playerconnection);
        GameRules gamerules = worldserver1.getGameRules();
        boolean flag = gamerules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        boolean flag1 = gamerules.getBoolean(GameRules.RULE_REDUCEDDEBUGINFO);
        boolean flag2 = gamerules.getBoolean(GameRules.RULE_LIMITED_CRAFTING);

        // Spigot - view distance
        playerconnection.send(new ClientboundLoginPacket(player.getId(), worlddata.isHardcore(), this.server.levelKeys(), this.getMaxPlayers(), worldserver1.spigotConfig.viewDistance, worldserver1.spigotConfig.simulationDistance, flag1, !flag, flag2, player.createCommonSpawnInfo(worldserver1), this.server.enforceSecureProfile()));
        player.getBukkitEntity().sendSupportedChannels(); // CraftBukkit
        playerconnection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        playerconnection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
        playerconnection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        playerconnection.send(new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes()));
        this.sendPlayerPermissionLevel(player);
        player.getStats().markAllDirty();
        player.getRecipeBook().sendInitialRecipeBook(player);
        this.updateEntireScoreboard(worldserver1.getScoreboard(), player);
        this.server.invalidateStatus();
        MutableComponent ichatmutablecomponent;

        if (player.getGameProfile().getName().equalsIgnoreCase(s)) {
            ichatmutablecomponent = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        } else {
            ichatmutablecomponent = Component.translatable("multiplayer.player.joined.renamed", player.getDisplayName(), s);
        }
        // CraftBukkit start
        ichatmutablecomponent.withStyle(ChatFormatting.YELLOW);
        Component joinMessage = ichatmutablecomponent; // Paper - Adventure

        playerconnection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        ServerStatus serverping = this.server.getStatus();

        if (serverping != null && !clientData.transferred()) {
            player.sendServerStatus(serverping);
        }

        // entityplayer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(this.players)); // CraftBukkit - replaced with loop below
        this.players.add(player);
        this.playersByName.put(player.getScoreboardName().toLowerCase(java.util.Locale.ROOT), player); // Spigot
        this.playersByUUID.put(player.getUUID(), player);
        // this.broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(entityplayer))); // CraftBukkit - replaced with loop below

        // Paper start - Fire PlayerJoinEvent when Player is actually ready; correctly register player BEFORE PlayerJoinEvent, so the entity is valid and doesn't require tick delay hacks
        player.supressTrackerForLogin = true;
        worldserver1.addNewPlayer(player);
        this.server.getCustomBossEvents().onPlayerConnect(player); // see commented out section below worldserver.addPlayerJoin(entityplayer);
        this.mountSavedVehicle(player, worldserver1, optional);
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        // CraftBukkit start
        CraftPlayer bukkitPlayer = player.getBukkitEntity();

        // Ensure that player inventory is populated with its viewer
        player.containerMenu.transferTo(player.containerMenu, bukkitPlayer);

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(bukkitPlayer, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        this.cserver.getPluginManager().callEvent(playerJoinEvent);

        if (!player.connection.isAcceptingMessages()) {
            return;
        }

        final net.kyori.adventure.text.Component jm = playerJoinEvent.joinMessage();

        if (jm != null && !jm.equals(net.kyori.adventure.text.Component.empty())) { // Paper - Adventure
            joinMessage = io.papermc.paper.adventure.PaperAdventure.asVanilla(jm); // Paper - Adventure
            this.server.getPlayerList().broadcastSystemMessage(joinMessage, false); // Paper - Adventure
        }
        // CraftBukkit end

        // CraftBukkit start - sendAll above replaced with this loop
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)); // Paper - Add Listing API for Player

        final List<ServerPlayer> onlinePlayers = Lists.newArrayListWithExpectedSize(this.players.size() - 1); // Paper - Use single player info update packet on join
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer1 = (ServerPlayer) this.players.get(i);

            if (entityplayer1.getBukkitEntity().canSee(bukkitPlayer)) {
                // Paper start - Add Listing API for Player
                if (entityplayer1.getBukkitEntity().isListed(bukkitPlayer)) {
                // Paper end - Add Listing API for Player
                entityplayer1.connection.send(packet);
                // Paper start - Add Listing API for Player
                } else {
                    entityplayer1.connection.send(ClientboundPlayerInfoUpdatePacket.createSinglePlayerInitializing(player, false));
                }
                // Paper end - Add Listing API for Player
            }

            if (entityplayer1 == player || !bukkitPlayer.canSee(entityplayer1.getBukkitEntity())) { // Paper - Use single player info update packet on join; Don't include joining player
                continue;
            }

            onlinePlayers.add(entityplayer1); // Paper - Use single player info update packet on join
        }
        // Paper start - Use single player info update packet on join
        if (!onlinePlayers.isEmpty()) {
            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(onlinePlayers, player)); // Paper - Add Listing API for Player
        }
        // Paper end - Use single player info update packet on join
        player.sentListPacket = true;
        player.supressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
        ((ServerLevel)player.level()).getChunkSource().chunkMap.addEntity(player); // Paper - Fire PlayerJoinEvent when Player is actually ready; track entity now
        // CraftBukkit end

        player.refreshEntityData(player); // CraftBukkit - BungeeCord#2321, send complete data to self on spawn

        this.sendLevelInfo(player, worldserver1);

        // CraftBukkit start - Only add if the player wasn't moved in the event
        if (player.level() == worldserver1 && !worldserver1.players().contains(player)) {
            worldserver1.addNewPlayer(player);
            this.server.getCustomBossEvents().onPlayerConnect(player);
        }

        worldserver1 = player.serverLevel(); // CraftBukkit - Update in case join event changed it
        // CraftBukkit end
        this.sendActivePlayerEffects(player);
        // Paper start - Fire PlayerJoinEvent when Player is actually ready; move vehicle into method so it can be called above - short circuit around that code
        this.onPlayerJoinFinish(player, worldserver1, s1);
        // Paper start - Send empty chunk, so players aren't stuck in the world loading screen with our chunk system not sending chunks when dead
        if (player.isDeadOrDying()) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains = worldserver1.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
                new net.minecraft.world.level.chunk.EmptyLevelChunk(worldserver1, player.chunkPosition(), plains),
                worldserver1.getLightEngine(), (java.util.BitSet)null, (java.util.BitSet) null)
            );
        }
        // Paper end - Send empty chunk
    }
    private void mountSavedVehicle(ServerPlayer player, ServerLevel worldserver1, Optional<CompoundTag> optional) {
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        if (optional.isPresent() && ((CompoundTag) optional.get()).contains("RootVehicle", 10)) {
            CompoundTag nbttagcompound = ((CompoundTag) optional.get()).getCompound("RootVehicle");
            ServerLevel finalWorldServer = worldserver1; // CraftBukkit - decompile error
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound.getCompound("Entity"), worldserver1, (entity1) -> {
                return !finalWorldServer.addWithUUID(entity1, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.MOUNT) ? null : entity1; // CraftBukkit - decompile error // Paper - Entity#getEntitySpawnReason
            });

            if (entity != null) {
                UUID uuid;

                if (nbttagcompound.hasUUID("Attach")) {
                    uuid = nbttagcompound.getUUID("Attach");
                } else {
                    uuid = null;
                }

                Iterator iterator;
                Entity entity1;

                if (entity.getUUID().equals(uuid)) {
                    player.startRiding(entity, true);
                } else {
                    iterator = entity.getIndirectPassengers().iterator();

                    while (iterator.hasNext()) {
                        entity1 = (Entity) iterator.next();
                        if (entity1.getUUID().equals(uuid)) {
                            player.startRiding(entity1, true);
                            break;
                        }
                    }
                }

                if (!player.isPassenger()) {
                    PlayerList.LOGGER.warn("Couldn't reattach entity to player");
                    entity.discard(null); // CraftBukkit - add Bukkit remove cause
                    iterator = entity.getIndirectPassengers().iterator();

                    while (iterator.hasNext()) {
                        entity1 = (Entity) iterator.next();
                        entity1.discard(null); // CraftBukkit - add Bukkit remove cause
                    }
                }
            }
        }

        // Paper start - Fire PlayerJoinEvent when Player is actually ready
    }
    public void onPlayerJoinFinish(ServerPlayer player, ServerLevel worldserver1, String s1) {
        // Paper end - Fire PlayerJoinEvent when Player is actually ready
        player.initInventoryMenu();
        // CraftBukkit - Moved from above, added world
        // Paper start - Configurable player collision; Add to collideRule team if needed
        final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
        final PlayerTeam collideRuleTeam = scoreboard.getPlayerTeam(this.collideRuleTeamName);
        if (this.collideRuleTeamName != null && collideRuleTeam != null && player.getTeam() == null) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), collideRuleTeam);
        }
        // Paper end - Configurable player collision
        PlayerList.LOGGER.info("{}[{}] logged in with entity id {} at ([{}]{}, {}, {})", player.getName().getString(), s1, player.getId(), worldserver1.serverLevelData.getLevelName(), player.getX(), player.getY(), player.getZ());
    }

    public void updateEntireScoreboard(ServerScoreboard scoreboard, ServerPlayer player) {
        Set<Objective> set = Sets.newHashSet();
        Iterator iterator = scoreboard.getPlayerTeams().iterator();

        while (iterator.hasNext()) {
            PlayerTeam scoreboardteam = (PlayerTeam) iterator.next();

            player.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(scoreboardteam, true));
        }

        DisplaySlot[] adisplayslot = DisplaySlot.values();
        int i = adisplayslot.length;

        for (int j = 0; j < i; ++j) {
            DisplaySlot displayslot = adisplayslot[j];
            Objective scoreboardobjective = scoreboard.getDisplayObjective(displayslot);

            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                List<Packet<?>> list = scoreboard.getStartTrackingPackets(scoreboardobjective);
                Iterator iterator1 = list.iterator();

                while (iterator1.hasNext()) {
                    Packet<?> packet = (Packet) iterator1.next();

                    player.connection.send(packet);
                }

                set.add(scoreboardobjective);
            }
        }

    }

    public void addWorldborderListener(ServerLevel world) {
        if (this.playerIo != null) return; // CraftBukkit
        world.getWorldBorder().addListener(new BorderChangeListener() {
            @Override
            public void onBorderSizeSet(WorldBorder border, double size) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSizeLerping(WorldBorder border, double fromSize, double toSize, long time) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderLerpSizePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderCenterSet(WorldBorder border, double centerX, double centerZ) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderCenterPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningTime(WorldBorder border, int warningTime) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDelayPacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetWarningBlocks(WorldBorder border, int warningBlockDistance) {
                PlayerList.this.broadcastAll(new ClientboundSetBorderWarningDistancePacket(border), border.world); // CraftBukkit
            }

            @Override
            public void onBorderSetDamagePerBlock(WorldBorder border, double damagePerBlock) {}

            @Override
            public void onBorderSetDamageSafeZOne(WorldBorder border, double safeZoneRadius) {}
        });
    }

    public Optional<CompoundTag> load(ServerPlayer player) {
        CompoundTag nbttagcompound = this.server.getWorldData().getLoadedPlayerTag();
        Optional optional;

        if (this.server.isSingleplayerOwner(player.getGameProfile()) && nbttagcompound != null) {
            optional = Optional.of(nbttagcompound);
            player.load(nbttagcompound);
            PlayerList.LOGGER.debug("loading single player");
        } else {
            optional = this.playerIo.load(player);
        }

        return optional;
    }

    protected void save(ServerPlayer player) {
        if (!player.getBukkitEntity().isPersistent()) return; // CraftBukkit
        this.playerIo.save(player);
        ServerStatsCounter serverstatisticmanager = (ServerStatsCounter) player.getStats(); // CraftBukkit

        if (serverstatisticmanager != null) {
            serverstatisticmanager.save();
        }

        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) player.getAdvancements(); // CraftBukkit

        if (advancementdataplayer != null) {
            advancementdataplayer.save();
        }

    }

    public net.kyori.adventure.text.Component remove(ServerPlayer entityplayer) { // CraftBukkit - return string // Paper - return Component
        // Paper start - Fix kick event leave message not being sent
        return this.remove(entityplayer, net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? entityplayer.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(entityplayer.getDisplayName())));
    }
    public net.kyori.adventure.text.Component remove(ServerPlayer entityplayer, net.kyori.adventure.text.Component leaveMessage) {
        // Paper end - Fix kick event leave message not being sent
        ServerLevel worldserver = entityplayer.serverLevel();

        entityplayer.awardStat(Stats.LEAVE_GAME);

        // CraftBukkit start - Quitting must be before we do final save of data, in case plugins need to modify it
        // See SPIGOT-5799, SPIGOT-6145
        if (entityplayer.containerMenu != entityplayer.inventoryMenu) {
            entityplayer.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DISCONNECT); // Paper - Inventory close reason
        }

        PlayerQuitEvent playerQuitEvent = new PlayerQuitEvent(entityplayer.getBukkitEntity(), net.kyori.adventure.text.Component.translatable("multiplayer.player.left", net.kyori.adventure.text.format.NamedTextColor.YELLOW, io.papermc.paper.configuration.GlobalConfiguration.get().messages.useDisplayNameInQuitMessage ? entityplayer.getBukkitEntity().displayName() : io.papermc.paper.adventure.PaperAdventure.asAdventure(entityplayer.getDisplayName())), entityplayer.quitReason); // Paper - Adventure & Add API for quit reason
        this.cserver.getPluginManager().callEvent(playerQuitEvent);
        entityplayer.getBukkitEntity().disconnect(playerQuitEvent.getQuitMessage());

        entityplayer.doTick(); // SPIGOT-924
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove from collideRule team if needed
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreBoard = this.server.getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreBoard.getPlayersTeam(this.collideRuleTeamName);
            if (entityplayer.getTeam() == team && team != null) {
                scoreBoard.removePlayerFromTeam(entityplayer.getScoreboardName(), team);
            }
        }
        // Paper end - Configurable player collision

        // Paper - Drop carried item when player has disconnected
        if (!entityplayer.containerMenu.getCarried().isEmpty()) {
            net.minecraft.world.item.ItemStack carried = entityplayer.containerMenu.getCarried();
            entityplayer.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
            entityplayer.drop(carried, false);
        }
        // Paper end - Drop carried item when player has disconnected

        this.save(entityplayer);
        if (entityplayer.isPassenger()) {
            Entity entity = entityplayer.getRootVehicle();

            if (entity.hasExactlyOnePlayerPassenger()) {
                PlayerList.LOGGER.debug("Removing player mount");
                entityplayer.stopRiding();
                entity.getPassengersAndSelf().forEach((entity1) -> {
                    // Paper start - Fix villager boat exploit
                    if (entity1 instanceof net.minecraft.world.entity.npc.AbstractVillager villager) {
                        final net.minecraft.world.entity.player.Player human = villager.getTradingPlayer();
                        if (human != null) {
                            villager.setTradingPlayer(null);
                        }
                    }
                    // Paper end - Fix villager boat exploit
                    entity1.setRemoved(Entity.RemovalReason.UNLOADED_WITH_PLAYER, EntityRemoveEvent.Cause.PLAYER_QUIT); // CraftBukkit - add Bukkit remove cause
                });
            }
        }

        entityplayer.unRide();
        worldserver.removePlayerImmediately(entityplayer, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        entityplayer.retireScheduler(); // Paper - Folia schedulers
        entityplayer.getAdvancements().stopListening();
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        this.server.getCustomBossEvents().onPlayerDisconnect(entityplayer);
        UUID uuid = entityplayer.getUUID();
        ServerPlayer entityplayer1 = (ServerPlayer) this.playersByUUID.get(uuid);

        if (entityplayer1 == entityplayer) {
            this.playersByUUID.remove(uuid);
            // CraftBukkit start
            // this.stats.remove(uuid);
            // this.advancements.remove(uuid);
            // CraftBukkit end
        }

        // CraftBukkit start
        // this.broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID())));
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(entityplayer.getUUID()));
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayer entityplayer2 = (ServerPlayer) this.players.get(i);

            if (entityplayer2.getBukkitEntity().canSee(entityplayer.getBukkitEntity())) {
                entityplayer2.connection.send(packet);
            } else {
                entityplayer2.getBukkitEntity().onEntityRemove(entityplayer);
            }
        }
        // This removes the scoreboard (and player reference) for the specific player in the manager
        this.cserver.getScoreboardManager().removePlayer(entityplayer.getBukkitEntity());
        // CraftBukkit end

        return playerQuitEvent.quitMessage(); // Paper - Adventure
    }

    // CraftBukkit start - Whole method, SocketAddress to LoginListener, added hostname to signature, return EntityPlayer
    public ServerPlayer canPlayerLogin(ServerLoginPacketListenerImpl loginlistener, GameProfile gameprofile) {
        MutableComponent ichatmutablecomponent;

        // Moved from processLogin
        UUID uuid = gameprofile.getId();
        List<ServerPlayer> list = Lists.newArrayList();

        ServerPlayer entityplayer;

        for (int i = 0; i < this.players.size(); ++i) {
            entityplayer = (ServerPlayer) this.players.get(i);
            if (entityplayer.getUUID().equals(uuid) || (io.papermc.paper.configuration.GlobalConfiguration.get().proxies.isProxyOnlineMode() && entityplayer.getGameProfile().getName().equalsIgnoreCase(gameprofile.getName()))) { // Paper - validate usernames
                list.add(entityplayer);
            }
        }

        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            entityplayer = (ServerPlayer) iterator.next();
            this.save(entityplayer); // CraftBukkit - Force the player's inventory to be saved
            entityplayer.connection.disconnect(Component.translatable("multiplayer.disconnect.duplicate_login"), org.bukkit.event.player.PlayerKickEvent.Cause.DUPLICATE_LOGIN); // Paper - kick event cause
        }

        // Instead of kicking then returning, we need to store the kick reason
        // in the event, check with plugins to see if it's ok, and THEN kick
        // depending on the outcome.
        SocketAddress socketaddress = loginlistener.connection.getRemoteAddress();

        ServerPlayer entity = new ServerPlayer(this.server, this.server.getLevel(Level.OVERWORLD), gameprofile, ClientInformation.createDefault());
        entity.transferCookieConnection = loginlistener;
        Player player = entity.getBukkitEntity();
        PlayerLoginEvent event = new PlayerLoginEvent(player, loginlistener.connection.hostname, ((java.net.InetSocketAddress) socketaddress).getAddress(), ((java.net.InetSocketAddress) loginlistener.connection.channel.remoteAddress()).getAddress());

        // Paper start - Fix MC-158900
        UserBanListEntry gameprofilebanentry;
        if (this.bans.isBanned(gameprofile) && (gameprofilebanentry = this.bans.get(gameprofile)) != null) {
            // Paper end - Fix MC-158900

            ichatmutablecomponent = Component.translatable("multiplayer.disconnect.banned.reason", gameprofilebanentry.getReason());
            if (gameprofilebanentry.getExpires() != null) {
                ichatmutablecomponent.append((Component) Component.translatable("multiplayer.disconnect.banned.expiration", PlayerList.BAN_DATE_FORMAT.format(gameprofilebanentry.getExpires())));
            }

            // return chatmessage;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        } else if (!this.isWhiteListed(gameprofile, event)) { // Paper - ProfileWhitelistVerifyEvent
            //ichatmutablecomponent = Component.translatable("multiplayer.disconnect.not_whitelisted"); // Paper
            //event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.whitelistMessage)); // Spigot // Paper - Adventure - moved to isWhitelisted
        } else if (this.getIpBans().isBanned(socketaddress) && getIpBans().get(socketaddress) != null && !this.getIpBans().get(socketaddress).hasExpired()) { // Paper - fix NPE with temp ip bans
            IpBanListEntry ipbanentry = this.ipBans.get(socketaddress);

            ichatmutablecomponent = Component.translatable("multiplayer.disconnect.banned_ip.reason", ipbanentry.getReason());
            if (ipbanentry.getExpires() != null) {
                ichatmutablecomponent.append((Component) Component.translatable("multiplayer.disconnect.banned_ip.expiration", PlayerList.BAN_DATE_FORMAT.format(ipbanentry.getExpires())));
            }

            // return chatmessage;
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent)); // Paper - Adventure
        } else {
            // return this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile) ? IChatBaseComponent.translatable("multiplayer.disconnect.server_full") : null;
            if (this.players.size() >= this.maxPlayers && !this.canBypassPlayerLimit(gameprofile)) {
                event.disallow(PlayerLoginEvent.Result.KICK_FULL, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.serverFullMessage)); // Spigot // Paper - Adventure
            }
        }

        this.cserver.getPluginManager().callEvent(event);
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            loginlistener.disconnect(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.kickMessage())); // Paper - Adventure
            return null;
        }
        return entity;
    }

    // CraftBukkit start - added EntityPlayer
    public ServerPlayer getPlayerForLogin(GameProfile gameprofile, ClientInformation clientinformation, ServerPlayer player) {
        player.updateOptions(clientinformation);
        return player;
        // CraftBukkit end
    }

    public boolean disconnectAllPlayersWithProfile(GameProfile gameprofile, ServerPlayer player) { // CraftBukkit - added EntityPlayer
        /* CraftBukkit startMoved up
        UUID uuid = gameprofile.getId();
        Set<EntityPlayer> set = Sets.newIdentityHashSet();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            if (entityplayer.getUUID().equals(uuid)) {
                set.add(entityplayer);
            }
        }

        EntityPlayer entityplayer1 = (EntityPlayer) this.playersByUUID.get(gameprofile.getId());

        if (entityplayer1 != null) {
            set.add(entityplayer1);
        }

        Iterator iterator1 = set.iterator();

        while (iterator1.hasNext()) {
            EntityPlayer entityplayer2 = (EntityPlayer) iterator1.next();

            entityplayer2.connection.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
        }

        return !set.isEmpty();
        */
        return player == null;
        // CraftBukkit end
    }

    // CraftBukkit start
    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, RespawnReason reason) {
        return this.respawn(entityplayer, flag, entity_removalreason, reason, null);
    }

    public ServerPlayer respawn(ServerPlayer entityplayer, boolean flag, Entity.RemovalReason entity_removalreason, RespawnReason reason, Location location) {
        entityplayer.stopRiding(); // CraftBukkit
        this.players.remove(entityplayer);
        this.playersByName.remove(entityplayer.getScoreboardName().toLowerCase(java.util.Locale.ROOT)); // Spigot
        entityplayer.serverLevel().removePlayerImmediately(entityplayer, entity_removalreason);
        /* CraftBukkit start
        DimensionTransition dimensiontransition = entityplayer.findRespawnPositionAndUseSpawnBlock(flag, DimensionTransition.DO_NOTHING);
        WorldServer worldserver = dimensiontransition.newLevel();
        EntityPlayer entityplayer1 = new EntityPlayer(this.server, worldserver, entityplayer.getGameProfile(), entityplayer.clientInformation());
        // */
        ServerPlayer entityplayer1 = entityplayer;
        Level fromWorld = entityplayer.level();
        entityplayer.wonGame = false;
        // CraftBukkit end

        entityplayer1.connection = entityplayer.connection;
        entityplayer1.restoreFrom(entityplayer, flag);
        entityplayer1.setId(entityplayer.getId());
        entityplayer1.setMainArm(entityplayer.getMainArm());
        // CraftBukkit - not required, just copies old location into reused entity
        /*
        if (!dimensiontransition.missingRespawnBlock()) {
            entityplayer1.copyRespawnPosition(entityplayer);
        }
         */
        // CraftBukkit end

        Iterator iterator = entityplayer.getTags().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            entityplayer1.addTag(s);
        }
        // Paper start - Add PlayerPostRespawnEvent
        boolean isBedSpawn = false;
        boolean isRespawn = false;
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit start - fire PlayerRespawnEvent
        DimensionTransition dimensiontransition;
        if (location == null) {
            dimensiontransition = entityplayer.findRespawnPositionAndUseSpawnBlock(flag, DimensionTransition.DO_NOTHING, reason);

            if (!flag) entityplayer.reset(); // SPIGOT-4785
            // Paper start - Add PlayerPostRespawnEvent
            isRespawn = true;
            location = CraftLocation.toBukkit(dimensiontransition.pos(), dimensiontransition.newLevel().getWorld(), dimensiontransition.yRot(), dimensiontransition.xRot());
            // Paper end - Add PlayerPostRespawnEvent
        } else {
            dimensiontransition = new DimensionTransition(((CraftWorld) location.getWorld()).getHandle(), CraftLocation.toVec3D(location), Vec3.ZERO, location.getYaw(), location.getPitch(), DimensionTransition.DO_NOTHING);
        }
        // Spigot Start
        if (dimensiontransition == null) {
            return entityplayer;
        }
        // Spigot End
        ServerLevel worldserver = dimensiontransition.newLevel();
        entityplayer1.spawnIn(worldserver);
        entityplayer1.unsetRemoved();
        entityplayer1.setShiftKeyDown(false);
        Vec3 vec3d = dimensiontransition.pos();

        entityplayer1.forceSetPositionRotation(vec3d.x, vec3d.y, vec3d.z, dimensiontransition.yRot(), dimensiontransition.xRot());
        // CraftBukkit end
        if (dimensiontransition.missingRespawnBlock()) {
            entityplayer1.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            entityplayer1.setRespawnPosition(null, null, 0f, false, false, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN); // CraftBukkit - SPIGOT-5988: Clear respawn location when obstructed // Paper - PlayerSetSpawnEvent
        }

        int i = flag ? 1 : 0;
        ServerLevel worldserver1 = entityplayer1.serverLevel();
        LevelData worlddata = worldserver1.getLevelData();

        entityplayer1.connection.send(new ClientboundRespawnPacket(entityplayer1.createCommonSpawnInfo(worldserver1), (byte) i));
        entityplayer1.connection.send(new ClientboundSetChunkCacheRadiusPacket(worldserver1.spigotConfig.viewDistance)); // Spigot
        entityplayer1.connection.send(new ClientboundSetSimulationDistancePacket(worldserver1.spigotConfig.simulationDistance)); // Spigot
        entityplayer1.connection.teleport(CraftLocation.toBukkit(entityplayer1.position(), worldserver1.getWorld(), entityplayer1.getYRot(), entityplayer1.getXRot())); // CraftBukkit
        entityplayer1.connection.send(new ClientboundSetDefaultSpawnPositionPacket(worldserver.getSharedSpawnPos(), worldserver.getSharedSpawnAngle()));
        entityplayer1.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        entityplayer1.connection.send(new ClientboundSetExperiencePacket(entityplayer1.experienceProgress, entityplayer1.totalExperience, entityplayer1.experienceLevel));
        this.sendActivePlayerEffects(entityplayer1);
        this.sendLevelInfo(entityplayer1, worldserver);
        this.sendPlayerPermissionLevel(entityplayer1);
        if (!entityplayer.connection.isDisconnected()) {
            worldserver.addRespawnedPlayer(entityplayer1);
            this.players.add(entityplayer1);
            this.playersByName.put(entityplayer1.getScoreboardName().toLowerCase(java.util.Locale.ROOT), entityplayer1); // Spigot
            this.playersByUUID.put(entityplayer1.getUUID(), entityplayer1);
        }
        // entityplayer1.initInventoryMenu();
        entityplayer1.setHealth(entityplayer1.getHealth());
        if (!flag) {
            BlockPos blockposition = BlockPos.containing(dimensiontransition.pos());
            BlockState iblockdata = worldserver.getBlockState(blockposition);

            if (iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
                entityplayer1.connection.send(new ClientboundSoundPacket(SoundEvents.RESPAWN_ANCHOR_DEPLETE, SoundSource.BLOCKS, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), 1.0F, 1.0F, worldserver.getRandom().nextLong()));
            }
            // Paper start - Add PlayerPostRespawnEvent
            if (iblockdata.is(net.minecraft.tags.BlockTags.BEDS) && !dimensiontransition.missingRespawnBlock()) {
                isBedSpawn = true;
            }
            // Paper end - Add PlayerPostRespawnEvent
        }
        // Added from changeDimension
        this.sendAllPlayerInfo(entityplayer); // Update health, etc...
        entityplayer.onUpdateAbilities();
        for (MobEffectInstance mobEffect : entityplayer.getActiveEffects()) {
            entityplayer.connection.send(new ClientboundUpdateMobEffectPacket(entityplayer.getId(), mobEffect, false)); // blend = false
        }

        // Fire advancement trigger
        entityplayer.triggerDimensionChangeTriggers(worldserver);

        // Don't fire on respawn
        if (fromWorld != worldserver) {
            PlayerChangedWorldEvent event = new PlayerChangedWorldEvent(entityplayer.getBukkitEntity(), fromWorld.getWorld());
            this.server.server.getPluginManager().callEvent(event);
        }

        // Save player file again if they were disconnected
        if (entityplayer.connection.isDisconnected()) {
            this.save(entityplayer);
        }

        // Paper start - Add PlayerPostRespawnEvent
        if (isRespawn) {
            cserver.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerPostRespawnEvent(entityplayer.getBukkitEntity(), location, isBedSpawn));
        }
        // Paper end - Add PlayerPostRespawnEvent

        // CraftBukkit end

        return entityplayer1;
    }

    public void sendActivePlayerEffects(ServerPlayer player) {
        this.sendActiveEffects(player, player.connection);
    }

    public void sendActiveEffects(LivingEntity entity, ServerGamePacketListenerImpl networkHandler) {
        Iterator iterator = entity.getActiveEffects().iterator();

        while (iterator.hasNext()) {
            MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

            networkHandler.send(new ClientboundUpdateMobEffectPacket(entity.getId(), mobeffect, false));
        }

    }

    public void sendPlayerPermissionLevel(ServerPlayer player) {
    // Paper start - avoid recalculating permissions if possible
        this.sendPlayerPermissionLevel(player, true);
    }

    public void sendPlayerPermissionLevel(ServerPlayer player, boolean recalculatePermissions) {
    // Paper end - avoid recalculating permissions if possible
        GameProfile gameprofile = player.getGameProfile();
        int i = this.server.getProfilePermissions(gameprofile);

        this.sendPlayerPermissionLevel(player, i, recalculatePermissions); // Paper - avoid recalculating permissions if possible
    }

    public void tick() {
        if (++this.sendAllPlayerInfoIn > 600) {
            // CraftBukkit start
            for (int i = 0; i < this.players.size(); ++i) {
                final ServerPlayer target = (ServerPlayer) this.players.get(i);

                target.connection.send(new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY), this.players.stream().filter(new Predicate<ServerPlayer>() {
                    @Override
                    public boolean test(ServerPlayer input) {
                        return target.getBukkitEntity().canSee(input.getBukkitEntity());
                    }
                }).collect(Collectors.toList())));
            }
            // CraftBukkit end
            this.sendAllPlayerInfoIn = 0;
        }

    }

    public void broadcastAll(Packet<?> packet) {
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            entityplayer.connection.send(packet);
        }

    }

    // CraftBukkit start - add a world/entity limited version
    public void broadcastAll(Packet packet, net.minecraft.world.entity.player.Player entityhuman) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer =  this.players.get(i);
            if (entityhuman != null && !entityplayer.getBukkitEntity().canSee(entityhuman.getBukkitEntity())) {
                continue;
            }
            ((ServerPlayer) this.players.get(i)).connection.send(packet);
        }
    }

    public void broadcastAll(Packet packet, Level world) {
        for (int i = 0; i < world.players().size(); ++i) {
            ((ServerPlayer) world.players().get(i)).connection.send(packet);
        }

    }
    // CraftBukkit end

    public void broadcastAll(Packet<?> packet, ResourceKey<Level> dimension) {
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.level().dimension() == dimension) {
                entityplayer.connection.send(packet);
            }
        }

    }

    public void broadcastSystemToTeam(net.minecraft.world.entity.player.Player source, Component message) {
        PlayerTeam scoreboardteam = source.getTeam();

        if (scoreboardteam != null) {
            Collection<String> collection = scoreboardteam.getPlayers();
            Iterator iterator = collection.iterator();

            while (iterator.hasNext()) {
                String s = (String) iterator.next();
                ServerPlayer entityplayer = this.getPlayerByName(s);

                if (entityplayer != null && entityplayer != source) {
                    entityplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public void broadcastSystemToAllExceptTeam(net.minecraft.world.entity.player.Player source, Component message) {
        PlayerTeam scoreboardteam = source.getTeam();

        if (scoreboardteam == null) {
            this.broadcastSystemMessage(message, false);
        } else {
            for (int i = 0; i < this.players.size(); ++i) {
                ServerPlayer entityplayer = (ServerPlayer) this.players.get(i);

                if (entityplayer.getTeam() != scoreboardteam) {
                    entityplayer.sendSystemMessage(message);
                }
            }

        }
    }

    public String[] getPlayerNamesArray() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); ++i) {
            astring[i] = ((ServerPlayer) this.players.get(i)).getGameProfile().getName();
        }

        return astring;
    }

    public UserBanList getBans() {
        return this.bans;
    }

    public IpBanList getIpBans() {
        return this.ipBans;
    }

    public void op(GameProfile profile) {
        this.ops.add(new ServerOpListEntry(profile, this.server.getOperatorUserPermissionLevel(), this.ops.canBypassPlayerLimit(profile)));
        ServerPlayer entityplayer = this.getPlayer(profile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    public void deop(GameProfile profile) {
        this.ops.remove(profile); // CraftBukkit - decompile error
        ServerPlayer entityplayer = this.getPlayer(profile.getId());

        if (entityplayer != null) {
            this.sendPlayerPermissionLevel(entityplayer);
        }

    }

    private void sendPlayerPermissionLevel(ServerPlayer player, int permissionLevel) {
        // Paper start - Add sendOpLevel API
        this.sendPlayerPermissionLevel(player, permissionLevel, true);
    }
    public void sendPlayerPermissionLevel(ServerPlayer player, int permissionLevel, boolean recalculatePermissions) {
        // Paper end - Add sendOpLevel API
        if (player.connection != null) {
            byte b0;

            if (permissionLevel <= 0) {
                b0 = 24;
            } else if (permissionLevel >= 4) {
                b0 = 28;
            } else {
                b0 = (byte) (24 + permissionLevel);
            }

            player.connection.send(new ClientboundEntityEventPacket(player, b0));
        }

        if (recalculatePermissions) { // Paper - Add sendOpLevel API
        player.getBukkitEntity().recalculatePermissions(); // CraftBukkit
        this.server.getCommands().sendCommands(player);
        } // Paper - Add sendOpLevel API
    }

    public boolean isWhiteListed(GameProfile profile) {
        // Paper start - ProfileWhitelistVerifyEvent
        return isWhiteListed(profile, null);
    }
    public boolean isWhiteListed(GameProfile gameprofile, org.bukkit.event.player.PlayerLoginEvent loginEvent) {
        boolean isOp = this.ops.contains(gameprofile);
        boolean isWhitelisted = !this.doWhiteList || isOp || this.whitelist.contains(gameprofile);
        final com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent event;

        event = new com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent(com.destroystokyo.paper.profile.CraftPlayerProfile.asBukkitMirror(gameprofile), this.doWhiteList, isWhitelisted, isOp, org.spigotmc.SpigotConfig.whitelistMessage);
        event.callEvent();
        if (!event.isWhitelisted()) {
            if (loginEvent != null) {
                loginEvent.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(event.getKickMessage() == null ? org.spigotmc.SpigotConfig.whitelistMessage : event.getKickMessage()));
            }
            return false;
        }
        return true;
        // Paper end - ProfileWhitelistVerifyEvent
    }

    public boolean isOp(GameProfile profile) {
        return this.ops.contains(profile) || this.server.isSingleplayerOwner(profile) && this.server.getWorldData().isAllowCommands() || this.allowCommandsForAllPlayers;
    }

    @Nullable
    public ServerPlayer getPlayerByName(String name) {
        return this.playersByName.get(name.toLowerCase(java.util.Locale.ROOT)); // Spigot
    }

    public void broadcast(@Nullable net.minecraft.world.entity.player.Player player, double x, double y, double z, double distance, ResourceKey<Level> worldKey, Packet<?> packet) {
        for (int i = 0; i < this.players.size(); ++i) {
            ServerPlayer entityplayer = (ServerPlayer) this.players.get(i);

            // CraftBukkit start - Test if player receiving packet can see the source of the packet
            if (player != null && !entityplayer.getBukkitEntity().canSee(player.getBukkitEntity())) {
               continue;
            }
            // CraftBukkit end

            if (entityplayer != player && entityplayer.level().dimension() == worldKey) {
                double d4 = x - entityplayer.getX();
                double d5 = y - entityplayer.getY();
                double d6 = z - entityplayer.getZ();

                if (d4 * d4 + d5 * d5 + d6 * d6 < distance * distance) {
                    entityplayer.connection.send(packet);
                }
            }
        }

    }

    public void saveAll() {
        io.papermc.paper.util.MCUtil.ensureMain("Save Players" , () -> { // Paper - Ensure main
        MinecraftTimings.savePlayers.startTiming(); // Paper
        for (int i = 0; i < this.players.size(); ++i) {
            this.save(this.players.get(i));
        }
        MinecraftTimings.savePlayers.stopTiming(); // Paper
        return null; }); // Paper - ensure main
    }

    public UserWhiteList getWhiteList() {
        return this.whitelist;
    }

    public String[] getWhiteListNames() {
        return this.whitelist.getUserList();
    }

    public ServerOpList getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getUserList();
    }

    public void reloadWhiteList() {}

    public void sendLevelInfo(ServerPlayer player, ServerLevel world) {
        WorldBorder worldborder = player.level().getWorldBorder(); // CraftBukkit

        player.connection.send(new ClientboundInitializeBorderPacket(worldborder));
        player.connection.send(new ClientboundSetTimePacket(world.getGameTime(), world.getDayTime(), world.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(world.getSharedSpawnPos(), world.getSharedSpawnAngle()));
        if (world.isRaining()) {
            // CraftBukkit start - handle player weather
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.START_RAINING, 0.0F));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.RAIN_LEVEL_CHANGE, worldserver.getRainLevel(1.0F)));
            // entityplayer.connection.send(new PacketPlayOutGameStateChange(PacketPlayOutGameStateChange.THUNDER_LEVEL_CHANGE, worldserver.getThunderLevel(1.0F)));
            player.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL, false);
            player.updateWeather(-world.rainLevel, world.rainLevel, -world.thunderLevel, world.thunderLevel);
            // CraftBukkit end
        }

        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0.0F));
        this.server.tickRateManager().updateJoiningPlayer(player);
    }

    public void sendAllPlayerInfo(ServerPlayer player) {
        player.inventoryMenu.sendAllDataToRemote();
        // entityplayer.resetSentInfo();
        player.getBukkitEntity().updateScaledHealth(); // CraftBukkit - Update scaled health on respawn and worldchange
        player.refreshEntityData(player); // CraftBukkkit - SPIGOT-7218: sync metadata
        player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
        // CraftBukkit start - from GameRules
        int i = player.level().getGameRules().getBoolean(GameRules.RULE_REDUCEDDEBUGINFO) ? 22 : 23;
        player.connection.send(new ClientboundEntityEventPacket(player, (byte) i));
        float immediateRespawn = player.level().getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN) ? 1.0F: 0.0F;
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.IMMEDIATE_RESPAWN, immediateRespawn));
        // CraftBukkit end
    }

    public int getPlayerCount() {
        return this.players.size();
    }

    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    public boolean isUsingWhitelist() {
        return this.doWhiteList;
    }

    public void setUsingWhiteList(boolean whitelistEnabled) {
        new com.destroystokyo.paper.event.server.WhitelistToggleEvent(whitelistEnabled).callEvent(); // Paper - WhitelistToggleEvent
        this.doWhiteList = whitelistEnabled;
    }

    public List<ServerPlayer> getPlayersWithAddress(String ip) {
        List<ServerPlayer> list = Lists.newArrayList();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();

            if (entityplayer.getIpAddress().equals(ip)) {
                list.add(entityplayer);
            }
        }

        return list;
    }

    public int getViewDistance() {
        return this.viewDistance;
    }

    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    @Nullable
    public CompoundTag getSingleplayerData() {
        return null;
    }

    public void setAllowCommandsForAllPlayers(boolean cheatsAllowed) {
        this.allowCommandsForAllPlayers = cheatsAllowed;
    }

    public void removeAll() {
        // Paper start - Extract method to allow for restarting flag
        this.removeAll(false);
    }

    public void removeAll(boolean isRestarting) {
        // Paper end
        // CraftBukkit start - disconnect safely
        for (ServerPlayer player : this.players) {
            if (isRestarting) player.connection.disconnect(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(org.spigotmc.SpigotConfig.restartMessage), org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); else // Paper - kick event cause (cause is never used here)
            player.connection.disconnect(java.util.Objects.requireNonNullElseGet(this.server.server.shutdownMessage(), net.kyori.adventure.text.Component::empty)); // CraftBukkit - add custom shutdown message // Paper - Adventure
        }
        // CraftBukkit end

        // Paper start - Configurable player collision; Remove collideRule team if it exists
        if (this.collideRuleTeamName != null) {
            final net.minecraft.world.scores.Scoreboard scoreboard = this.getServer().getLevel(Level.OVERWORLD).getScoreboard();
            final PlayerTeam team = scoreboard.getPlayersTeam(this.collideRuleTeamName);
            if (team != null) scoreboard.removePlayerTeam(team);
        }
        // Paper end - Configurable player collision
    }

    // CraftBukkit start
    public void broadcastMessage(Component[] iChatBaseComponents) {
        for (Component component : iChatBaseComponents) {
            this.broadcastSystemMessage(component, false);
        }
    }
    // CraftBukkit end

    public void broadcastSystemMessage(Component message, boolean overlay) {
        this.broadcastSystemMessage(message, (entityplayer) -> {
            return message;
        }, overlay);
    }

    public void broadcastSystemMessage(Component message, Function<ServerPlayer, Component> playerMessageFactory, boolean overlay) {
        this.server.sendSystemMessage(message);
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator.next();
            Component ichatbasecomponent1 = (Component) playerMessageFactory.apply(entityplayer);

            if (ichatbasecomponent1 != null) {
                entityplayer.sendSystemMessage(ichatbasecomponent1, overlay);
            }
        }

    }

    public void broadcastChatMessage(PlayerChatMessage message, CommandSourceStack source, ChatType.Bound params) {
        Objects.requireNonNull(source);
        this.broadcastChatMessage(message, source::shouldFilterMessageTo, source.getPlayer(), params);
    }

    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params) {
        // Paper start
        this.broadcastChatMessage(message, sender, params, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        Objects.requireNonNull(sender);
        this.broadcastChatMessage(message, sender::shouldFilterMessageTo, sender, params, unsignedFunction); // Paper
    }

    private void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldSendFiltered, @Nullable ServerPlayer sender, ChatType.Bound params) {
        // Paper start
        this.broadcastChatMessage(message, shouldSendFiltered, sender, params, null);
    }
    public void broadcastChatMessage(PlayerChatMessage message, Predicate<ServerPlayer> shouldSendFiltered, @Nullable ServerPlayer sender, ChatType.Bound params, @Nullable Function<net.kyori.adventure.audience.Audience, Component> unsignedFunction) {
        // Paper end
        boolean flag = this.verifyChatTrusted(message);

        this.server.logChatMessage((unsignedFunction == null ? message.decoratedContent() : unsignedFunction.apply(this.server.console)), params, flag ? null : "Not Secure"); // Paper
        OutgoingChatMessage outgoingchatmessage = OutgoingChatMessage.create(message);
        boolean flag1 = false;

        boolean flag2;
        Packet<?> disguised = sender != null && unsignedFunction == null ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(outgoingchatmessage.content(), params) : null; // Paper - don't send player chat packets from vanished players

        for (Iterator iterator = this.players.iterator(); iterator.hasNext(); flag1 |= flag2 && message.isFullyFiltered()) {
            ServerPlayer entityplayer1 = (ServerPlayer) iterator.next();

            flag2 = shouldSendFiltered.test(entityplayer1);
            // Paper start - don't send player chat packets from vanished players
            if (sender != null && !entityplayer1.getBukkitEntity().canSee(sender.getBukkitEntity())) {
                entityplayer1.connection.send(unsignedFunction != null
                    ? new net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket(unsignedFunction.apply(entityplayer1.getBukkitEntity()), params)
                    : disguised);
                continue;
            }
            // Paper end
            entityplayer1.sendChatMessage(outgoingchatmessage, flag2, params, unsignedFunction == null ? null : unsignedFunction.apply(entityplayer1.getBukkitEntity())); // Paper
        }

        if (flag1 && sender != null) {
            sender.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }

    }

    public boolean verifyChatTrusted(PlayerChatMessage message) { // Paper - private -> public
        return message.hasSignature() && !message.hasExpiredServer(Instant.now());
    }

    // CraftBukkit start
    public ServerStatsCounter getPlayerStats(ServerPlayer entityhuman) {
        ServerStatsCounter serverstatisticmanager = entityhuman.getStats();
        return serverstatisticmanager == null ? this.getPlayerStats(entityhuman.getUUID(), entityhuman.getGameProfile().getName()) : serverstatisticmanager; // Paper - use username and not display name
    }

    public ServerStatsCounter getPlayerStats(UUID uuid, String displayName) {
        ServerPlayer entityhuman = this.getPlayer(uuid);
        ServerStatsCounter serverstatisticmanager = entityhuman == null ? null : (ServerStatsCounter) entityhuman.getStats();
        // CraftBukkit end

        if (serverstatisticmanager == null) {
            File file = this.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).toFile();
            File file1 = new File(file, String.valueOf(uuid) + ".json");

            if (!file1.exists()) {
                File file2 = new File(file, displayName + ".json"); // CraftBukkit
                Path path = file2.toPath();

                if (FileUtil.isPathNormalized(path) && FileUtil.isPathPortable(path) && path.startsWith(file.getPath()) && file2.isFile()) {
                    file2.renameTo(file1);
                }
            }

            serverstatisticmanager = new ServerStatsCounter(this.server, file1);
            // this.stats.put(uuid, serverstatisticmanager); // CraftBukkit
        }

        return serverstatisticmanager;
    }

    public PlayerAdvancements getPlayerAdvancements(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerAdvancements advancementdataplayer = (PlayerAdvancements) player.getAdvancements(); // CraftBukkit

        if (advancementdataplayer == null) {
            Path path = this.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(String.valueOf(uuid) + ".json");

            advancementdataplayer = new PlayerAdvancements(this.server.getFixerUpper(), this, this.server.getAdvancements(), path, player);
            // this.advancements.put(uuid, advancementdataplayer); // CraftBukkit
        }

        advancementdataplayer.setPlayer(player);
        return advancementdataplayer;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        this.broadcastAll(new ClientboundSetChunkCacheRadiusPacket(viewDistance));
        Iterator iterator = this.server.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                worldserver.getChunkSource().setViewDistance(viewDistance);
            }
        }

    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
        this.broadcastAll(new ClientboundSetSimulationDistancePacket(simulationDistance));
        Iterator iterator = this.server.getAllLevels().iterator();

        while (iterator.hasNext()) {
            ServerLevel worldserver = (ServerLevel) iterator.next();

            if (worldserver != null) {
                worldserver.getChunkSource().setSimulationDistance(simulationDistance);
            }
        }

    }

    public List<ServerPlayer> getPlayers() {
        return this.players;
    }

    @Nullable
    public ServerPlayer getPlayer(UUID uuid) {
        return (ServerPlayer) this.playersByUUID.get(uuid);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        return false;
    }

    public void reloadResources() {
        // Paper start - API for updating recipes on clients
        this.reloadAdvancementData();
        this.reloadTagData();
        this.reloadRecipeData();
    }
    public void reloadAdvancementData() {
        // Paper end - API for updating recipes on clients
        // CraftBukkit start
        /*Iterator iterator = this.advancements.values().iterator();

        while (iterator.hasNext()) {
            AdvancementDataPlayer advancementdataplayer = (AdvancementDataPlayer) iterator.next();

            advancementdataplayer.reload(this.server.getAdvancements());
        }*/

        for (ServerPlayer player : this.players) {
            player.getAdvancements().reload(this.server.getAdvancements());
            player.getAdvancements().flushDirty(player); // CraftBukkit - trigger immediate flush of advancements
        }
        // CraftBukkit end

        // Paper start - API for updating recipes on clients
    }
    public void reloadTagData() {
        // Paper end - API for updating recipes on clients
        this.broadcastAll(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(this.registries)));
        // Paper start - API for updating recipes on clients
    }
    public void reloadRecipeData() {
        // Paper end - API for updating recipes on clients
        ClientboundUpdateRecipesPacket packetplayoutrecipeupdate = new ClientboundUpdateRecipesPacket(this.server.getRecipeManager().getOrderedRecipes());
        Iterator iterator1 = this.players.iterator();

        while (iterator1.hasNext()) {
            ServerPlayer entityplayer = (ServerPlayer) iterator1.next();

            entityplayer.connection.send(packetplayoutrecipeupdate);
            entityplayer.getRecipeBook().sendInitialRecipeBook(entityplayer);
        }

    }

    public boolean isAllowCommandsForAllPlayers() {
        return this.allowCommandsForAllPlayers;
    }
}
