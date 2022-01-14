package net.minecraft.server.level;

import com.google.common.net.InetAddresses;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEndPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatEnterPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundServerDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.TextFilter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ComplexItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ServerItemCooldowns;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import org.slf4j.Logger;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
// CraftBukkit start
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import io.papermc.paper.adventure.PaperAdventure; // Paper
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftDimensionUtil;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.MainHand;
// CraftBukkit end

public class ServerPlayer extends net.minecraft.world.entity.player.Player {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_XZ = 32;
    private static final int NEUTRAL_MOB_DEATH_NOTIFICATION_RADII_Y = 10;
    private static final int FLY_STAT_RECORDING_SPEED = 25;
    public static final double INTERACTION_DISTANCE_VERIFICATION_BUFFER = 1.0D;
    private static final AttributeModifier CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER = new AttributeModifier(ResourceLocation.withDefaultNamespace("creative_mode_block_range"), 0.5D, AttributeModifier.Operation.ADD_VALUE);
    private static final AttributeModifier CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER = new AttributeModifier(ResourceLocation.withDefaultNamespace("creative_mode_entity_range"), 2.0D, AttributeModifier.Operation.ADD_VALUE);
    public ServerGamePacketListenerImpl connection;
    public final MinecraftServer server;
    public final ServerPlayerGameMode gameMode;
    private final PlayerAdvancements advancements;
    private final ServerStatsCounter stats;
    private float lastRecordedHealthAndAbsorption = Float.MIN_VALUE;
    private int lastRecordedFoodLevel = Integer.MIN_VALUE;
    private int lastRecordedAirLevel = Integer.MIN_VALUE;
    private int lastRecordedArmor = Integer.MIN_VALUE;
    private int lastRecordedLevel = Integer.MIN_VALUE;
    private int lastRecordedExperience = Integer.MIN_VALUE;
    private float lastSentHealth = -1.0E8F;
    private int lastSentFood = -99999999;
    private boolean lastFoodSaturationZero = true;
    public int lastSentExp = -99999999;
    public int spawnInvulnerableTime = 60;
    private ChatVisiblity chatVisibility;
    private boolean canChatColor;
    private long lastActionTime;
    @Nullable
    private Entity camera;
    public boolean isChangingDimension;
    public boolean seenCredits;
    private final ServerRecipeBook recipeBook;
    @Nullable
    private Vec3 levitationStartPos;
    private int levitationStartTime;
    private boolean disconnected;
    private int requestedViewDistance;
    public String language = null; // CraftBukkit - default  // Paper - default to null
    public java.util.Locale adventure$locale = java.util.Locale.US; // Paper
    @Nullable
    private Vec3 startingToFallPosition;
    @Nullable
    private Vec3 enteredNetherPosition;
    @Nullable
    private Vec3 enteredLavaOnVehiclePosition;
    private SectionPos lastSectionPos;
    private ChunkTrackingView chunkTrackingView;
    private ResourceKey<Level> respawnDimension;
    @Nullable
    private BlockPos respawnPosition;
    private boolean respawnForced;
    private float respawnAngle;
    private final TextFilter textFilter;
    private boolean textFilteringEnabled;
    private boolean allowsListing;
    private boolean spawnExtraParticlesOnFall;
    public WardenSpawnTracker wardenSpawnTracker;
    @Nullable
    private BlockPos raidOmenPosition;
    private Vec3 lastKnownClientMovement;
    public final ContainerSynchronizer containerSynchronizer;
    private final ContainerListener containerListener;
    @Nullable
    private RemoteChatSession chatSession;
    @Nullable
    public final Object object;
    private int containerCounter;
    public boolean wonGame;
    private int containerUpdateDelay; // Paper - Configurable container update tick rate
    public long loginTime; // Paper - Replace OfflinePlayer#getLastPlayed
    public int patrolSpawnDelay; // Paper - Pillager patrol spawn settings and per player options
    // Paper start - cancellable death event
    public boolean queueHealthUpdatePacket;
    public net.minecraft.network.protocol.game.ClientboundSetHealthPacket queuedHealthUpdatePacket;
    // Paper end - cancellable death event

    // CraftBukkit start
    public CraftPlayer.TransferCookieConnection transferCookieConnection;
    public String displayName;
    public net.kyori.adventure.text.Component adventure$displayName; // Paper
    public Component listName;
    public org.bukkit.Location compassTarget;
    public int newExp = 0;
    public int newLevel = 0;
    public int newTotalExp = 0;
    public boolean keepLevel = false;
    public double maxHealthCache;
    public boolean joining = true;
    public boolean sentListPacket = false;
    public boolean supressTrackerForLogin = false; // Paper - Fire PlayerJoinEvent when Player is actually ready
    // CraftBukkit end
    public boolean isRealPlayer; // Paper
    public com.destroystokyo.paper.event.entity.PlayerNaturallySpawnCreaturesEvent playerNaturallySpawnedEvent; // Paper - PlayerNaturallySpawnCreaturesEvent
    public @Nullable String clientBrandName = null; // Paper - Brand support
    public org.bukkit.event.player.PlayerQuitEvent.QuitReason quitReason = null; // Paper - Add API for quit reason; there are a lot of changes to do if we change all methods leading to the event

    public ServerPlayer(MinecraftServer server, ServerLevel world, GameProfile profile, ClientInformation clientOptions) {
        super(world, world.getSharedSpawnPos(), world.getSharedSpawnAngle(), profile);
        this.chatVisibility = ChatVisiblity.FULL;
        this.canChatColor = true;
        this.lastActionTime = Util.getMillis();
        this.recipeBook = new ServerRecipeBook();
        this.requestedViewDistance = 2;
        this.language =  null; // Paper - default to null
        this.lastSectionPos = SectionPos.of(0, 0, 0);
        this.chunkTrackingView = ChunkTrackingView.EMPTY;
        this.respawnDimension = Level.OVERWORLD;
        this.wardenSpawnTracker = new WardenSpawnTracker(0, 0, 0);
        this.lastKnownClientMovement = Vec3.ZERO;
        this.containerSynchronizer = new ContainerSynchronizer() {
            @Override
            public void sendInitialData(AbstractContainerMenu handler, NonNullList<ItemStack> stacks, ItemStack cursorStack, int[] properties) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetContentPacket(handler.containerId, handler.incrementStateId(), stacks, cursorStack));

                for (int i = 0; i < properties.length; ++i) {
                    this.broadcastDataValue(handler, i, properties[i]);
                }

            }

            // Paper start - Sync offhand slot in menus
            @Override
            public void sendOffHandSlotChange() {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(ServerPlayer.this.inventoryMenu.containerId, ServerPlayer.this.inventoryMenu.incrementStateId(), net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT, ServerPlayer.this.inventoryMenu.getSlot(net.minecraft.world.inventory.InventoryMenu.SHIELD_SLOT).getItem().copy()));
            }
            // Paper end - Sync offhand slot in menus

            @Override
            public void sendSlotChange(AbstractContainerMenu handler, int slot, ItemStack stack) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(handler.containerId, handler.incrementStateId(), slot, stack));
            }

            @Override
            public void sendCarriedChange(AbstractContainerMenu handler, ItemStack stack) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetSlotPacket(-1, handler.incrementStateId(), -1, stack));
            }

            @Override
            public void sendDataChange(AbstractContainerMenu handler, int property, int value) {
                this.broadcastDataValue(handler, property, value);
            }

            private void broadcastDataValue(AbstractContainerMenu handler, int property, int value) {
                ServerPlayer.this.connection.send(new ClientboundContainerSetDataPacket(handler.containerId, property, value));
            }
        };
        this.containerListener = new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack stack) {
                Slot slot = handler.getSlot(slotId);

                if (!(slot instanceof ResultSlot)) {
                    if (slot.container == ServerPlayer.this.getInventory()) {
                        CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                    }

                }
            }
            // Paper start - Add PlayerInventorySlotChangeEvent
            @Override
            public void slotChanged(AbstractContainerMenu handler, int slotId, ItemStack oldStack, ItemStack stack) {
                Slot slot = handler.getSlot(slotId);
                if (!(slot instanceof ResultSlot)) {
                    if (slot.container == ServerPlayer.this.getInventory()) {
                        if (io.papermc.paper.event.player.PlayerInventorySlotChangeEvent.getHandlerList().getRegisteredListeners().length == 0) {
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                            return;
                        }
                        io.papermc.paper.event.player.PlayerInventorySlotChangeEvent event = new io.papermc.paper.event.player.PlayerInventorySlotChangeEvent(ServerPlayer.this.getBukkitEntity(), slotId, CraftItemStack.asBukkitCopy(oldStack), CraftItemStack.asBukkitCopy(stack));
                        event.callEvent();
                        if (event.shouldTriggerAdvancements()) {
                            CriteriaTriggers.INVENTORY_CHANGED.trigger(ServerPlayer.this, ServerPlayer.this.getInventory(), stack);
                        }
                    }
                }
            }
            // Paper end - Add PlayerInventorySlotChangeEvent

            @Override
            public void dataChanged(AbstractContainerMenu handler, int property, int value) {}
        };
        this.textFilter = server.createTextFilterForPlayer(this);
        this.gameMode = server.createGameModeForPlayer(this);
        this.server = server;
        this.stats = server.getPlayerList().getPlayerStats(this);
        this.advancements = server.getPlayerList().getPlayerAdvancements(this);
        // this.moveTo(this.adjustSpawnLocation(world, world.getSharedSpawnPos()).getBottomCenter(), 0.0F, 0.0F); // Paper - Don't move existing players to world spawn
        this.updateOptionsNoEvents(clientOptions); // Paper - don't call options events on login
        this.object = null;

        // CraftBukkit start
        this.displayName = this.getScoreboardName();
        this.adventure$displayName = net.kyori.adventure.text.Component.text(this.getScoreboardName()); // Paper
        this.bukkitPickUpLoot = true;
        this.maxHealthCache = this.getMaxHealth();
    }

    // Use method to resend items in hands in case of client desync, because the item use got cancelled.
    // For example, when cancelling the leash event
    public void resendItemInHands() {
        this.containerMenu.findSlot(this.getInventory(), this.getInventory().selected).ifPresent(s -> {
            this.containerSynchronizer.sendSlotChange(this.containerMenu, s, this.getMainHandItem());
        });
        this.containerSynchronizer.sendSlotChange(this.inventoryMenu, InventoryMenu.SHIELD_SLOT, this.getOffhandItem());
    }

    // Yes, this doesn't match Vanilla, but it's the best we can do for now.
    // If this is an issue, PRs are welcome
    public final BlockPos getSpawnPoint(ServerLevel worldserver) {
        BlockPos blockposition = worldserver.getSharedSpawnPos();

        if (worldserver.dimensionType().hasSkyLight() && worldserver.serverLevelData.getGameType() != GameType.ADVENTURE) {
            int i = Math.max(0, this.server.getSpawnRadius(worldserver));
            int j = Mth.floor(worldserver.getWorldBorder().getDistanceToBorder((double) blockposition.getX(), (double) blockposition.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);
                BlockPos blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(worldserver, blockposition.getX() + j2 - i, blockposition.getZ() + k2 - i);

                if (blockposition1 != null) {
                    return blockposition1;
                }
            }
        }

        return blockposition;
    }
    // CraftBukkit end

    @Override
    public BlockPos adjustSpawnLocation(ServerLevel world, BlockPos basePos) {
        AABB axisalignedbb = this.getDimensions(Pose.STANDING).makeBoundingBox(Vec3.ZERO);
        BlockPos blockposition1 = basePos;

        if (world.dimensionType().hasSkyLight() && world.serverLevelData.getGameType() != GameType.ADVENTURE) { // CraftBukkit
            int i = Math.max(0, this.server.getSpawnRadius(world));
            int j = Mth.floor(world.getWorldBorder().getDistanceToBorder((double) basePos.getX(), (double) basePos.getZ()));

            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long k = (long) (i * 2 + 1);
            long l = k * k;
            int i1 = l > 2147483647L ? Integer.MAX_VALUE : (int) l;
            int j1 = this.getCoprime(i1);
            int k1 = RandomSource.create().nextInt(i1);

            for (int l1 = 0; l1 < i1; ++l1) {
                int i2 = (k1 + j1 * l1) % i1;
                int j2 = i2 % (i * 2 + 1);
                int k2 = i2 / (i * 2 + 1);

                blockposition1 = PlayerRespawnLogic.getOverworldRespawnPos(world, basePos.getX() + j2 - i, basePos.getZ() + k2 - i);
                if (blockposition1 != null && world.noCollision(this, axisalignedbb.move(blockposition1.getBottomCenter()))) {
                    return blockposition1;
                }
            }

            blockposition1 = basePos;
        }

        while (!world.noCollision(this, axisalignedbb.move(blockposition1.getBottomCenter())) && blockposition1.getY() < world.getMaxBuildHeight() - 1) {
            blockposition1 = blockposition1.above();
        }

        while (world.noCollision(this, axisalignedbb.move(blockposition1.below().getBottomCenter())) && blockposition1.getY() > world.getMinBuildHeight() + 1) {
            blockposition1 = blockposition1.below();
        }

        return blockposition1;
    }

    private int getCoprime(int horizontalSpawnArea) {
        return horizontalSpawnArea <= 16 ? horizontalSpawnArea - 1 : 17;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        DataResult dataresult;
        Logger logger;

        if (nbt.contains("warden_spawn_tracker", 10)) {
            dataresult = WardenSpawnTracker.CODEC.parse(new Dynamic(NbtOps.INSTANCE, nbt.get("warden_spawn_tracker")));
            logger = ServerPlayer.LOGGER;
            Objects.requireNonNull(logger);
            ((DataResult<WardenSpawnTracker>) dataresult).resultOrPartial(logger::error).ifPresent((wardenspawntracker) -> {
                this.wardenSpawnTracker = wardenspawntracker;
            });
        }

        if (nbt.contains("enteredNetherPosition", 10)) {
            CompoundTag nbttagcompound1 = nbt.getCompound("enteredNetherPosition");

            this.enteredNetherPosition = new Vec3(nbttagcompound1.getDouble("x"), nbttagcompound1.getDouble("y"), nbttagcompound1.getDouble("z"));
        }

        this.seenCredits = nbt.getBoolean("seenCredits");
        if (nbt.contains("recipeBook", 10)) {
            this.recipeBook.fromNbt(nbt.getCompound("recipeBook"), this.server.getRecipeManager());
        }
        this.getBukkitEntity().readExtraData(nbt); // CraftBukkit

        if (this.isSleeping()) {
            this.stopSleeping();
        }

        // CraftBukkit start
        String spawnWorld = nbt.getString("SpawnWorld");
        CraftWorld oldWorld = (CraftWorld) Bukkit.getWorld(spawnWorld);
        if (oldWorld != null) {
            this.respawnDimension = oldWorld.getHandle().dimension();
        }
        // CraftBukkit end

        if (nbt.contains("SpawnX", 99) && nbt.contains("SpawnY", 99) && nbt.contains("SpawnZ", 99)) {
            this.respawnPosition = new BlockPos(nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ"));
            this.respawnForced = nbt.getBoolean("SpawnForced");
            this.respawnAngle = nbt.getFloat("SpawnAngle");
            if (nbt.contains("SpawnDimension")) {
                DataResult<ResourceKey<Level>> dataresult1 = Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, nbt.get("SpawnDimension")); // CraftBukkit - decompile error
                Logger logger1 = ServerPlayer.LOGGER;

                Objects.requireNonNull(logger1);
                this.respawnDimension = (ResourceKey) dataresult1.resultOrPartial(logger1::error).orElse(Level.OVERWORLD);
            }
        }

        this.spawnExtraParticlesOnFall = nbt.getBoolean("spawn_extra_particles_on_fall");
        Tag nbtbase = nbt.get("raid_omen_position");

        if (nbtbase != null) {
            dataresult = BlockPos.CODEC.parse(NbtOps.INSTANCE, nbtbase);
            logger = ServerPlayer.LOGGER;
            Objects.requireNonNull(logger);
            ((DataResult<BlockPos>) dataresult).resultOrPartial(logger::error).ifPresent((blockposition) -> { // CraftBukkit - decompile error
                this.raidOmenPosition = blockposition;
            });
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        DataResult<Tag> dataresult = WardenSpawnTracker.CODEC.encodeStart(NbtOps.INSTANCE, this.wardenSpawnTracker); // CraftBukkit - decompile error
        Logger logger = ServerPlayer.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("warden_spawn_tracker", nbtbase);
        });
        this.storeGameTypes(nbt);
        nbt.putBoolean("seenCredits", this.seenCredits);
        if (this.enteredNetherPosition != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            nbttagcompound1.putDouble("x", this.enteredNetherPosition.x);
            nbttagcompound1.putDouble("y", this.enteredNetherPosition.y);
            nbttagcompound1.putDouble("z", this.enteredNetherPosition.z);
            nbt.put("enteredNetherPosition", nbttagcompound1);
        }

        Entity entity = this.getRootVehicle();
        Entity entity1 = this.getVehicle();

        // CraftBukkit start - handle non-persistent vehicles
        boolean persistVehicle = true;
        if (entity1 != null) {
            Entity vehicle;
            for (vehicle = entity1; vehicle != null; vehicle = vehicle.getVehicle()) {
                if (!vehicle.persist) {
                    persistVehicle = false;
                    break;
                }
            }
        }

        if (persistVehicle && entity1 != null && entity != this && entity.hasExactlyOnePlayerPassenger() && !entity.isRemoved()) { // Paper - Ensure valid vehicle status
            // CraftBukkit end
            CompoundTag nbttagcompound2 = new CompoundTag();
            CompoundTag nbttagcompound3 = new CompoundTag();

            entity.save(nbttagcompound3);
            nbttagcompound2.putUUID("Attach", entity1.getUUID());
            nbttagcompound2.put("Entity", nbttagcompound3);
            nbt.put("RootVehicle", nbttagcompound2);
        }

        nbt.put("recipeBook", this.recipeBook.toNbt());
        nbt.putString("Dimension", this.level().dimension().location().toString());
        if (this.respawnPosition != null) {
            nbt.putInt("SpawnX", this.respawnPosition.getX());
            nbt.putInt("SpawnY", this.respawnPosition.getY());
            nbt.putInt("SpawnZ", this.respawnPosition.getZ());
            nbt.putBoolean("SpawnForced", this.respawnForced);
            nbt.putFloat("SpawnAngle", this.respawnAngle);
            dataresult = ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.respawnDimension.location());
            logger = ServerPlayer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbt.put("SpawnDimension", nbtbase);
            });
        }
        this.getBukkitEntity().setExtraData(nbt); // CraftBukkit

        nbt.putBoolean("spawn_extra_particles_on_fall", this.spawnExtraParticlesOnFall);
        if (this.raidOmenPosition != null) {
            dataresult = BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, this.raidOmenPosition);
            logger = ServerPlayer.LOGGER;
            Objects.requireNonNull(logger);
            dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
                nbt.put("raid_omen_position", nbtbase);
            });
        }

    }

    // CraftBukkit start - World fallback code, either respawn location or global spawn
    public void spawnIn(Level world) {
        this.setLevel(world);
        if (world == null) {
            this.unsetRemoved();
            Vec3 position = null;
            if (this.respawnDimension != null) {
                world = this.server.getLevel(this.respawnDimension);
                if (world != null && this.getRespawnPosition() != null) {
                    position = ServerPlayer.findRespawnAndUseSpawnBlock((ServerLevel) world, this.getRespawnPosition(), this.getRespawnAngle(), false, false).map(ServerPlayer.RespawnPosAngle::position).orElse(null);
                }
            }
            if (world == null || position == null) {
                world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
                position = Vec3.atCenterOf(world.getSharedSpawnPos());
            }
            this.setLevel(world);
            this.setPosRaw(position.x(), position.y(), position.z()); // Paper - don't register to chunks yet
        }
        this.gameMode.setLevel((ServerLevel) world);
    }
    // CraftBukkit end

    public void setExperiencePoints(int points) {
        float f = (float) this.getXpNeededForNextLevel();
        float f1 = (f - 1.0F) / f;

        this.experienceProgress = Mth.clamp((float) points / f, 0.0F, f1);
        this.lastSentExp = -1;
    }

    public void setExperienceLevels(int level) {
        this.experienceLevel = level;
        this.lastSentExp = -1;
    }

    @Override
    public void giveExperienceLevels(int levels) {
        super.giveExperienceLevels(levels);
        this.lastSentExp = -1;
    }

    @Override
    public void onEnchantmentPerformed(ItemStack enchantedItem, int experienceLevels) {
        super.onEnchantmentPerformed(enchantedItem, experienceLevels);
        this.lastSentExp = -1;
    }

    public void initMenu(AbstractContainerMenu screenHandler) {
        screenHandler.addSlotListener(this.containerListener);
        screenHandler.setSynchronizer(this.containerSynchronizer);
    }

    public void initInventoryMenu() {
        this.initMenu(this.inventoryMenu);
    }

    @Override
    public void onEnterCombat() {
        super.onEnterCombat();
        this.connection.send(ClientboundPlayerCombatEnterPacket.INSTANCE);
    }

    @Override
    public void onLeaveCombat() {
        super.onLeaveCombat();
        this.connection.send(new ClientboundPlayerCombatEndPacket(this.getCombatTracker()));
    }

    @Override
    public void onInsideBlock(BlockState state) {
        CriteriaTriggers.ENTER_BLOCK.trigger(this, state);
    }

    @Override
    protected ItemCooldowns createItemCooldowns() {
        return new ServerItemCooldowns(this);
    }

    @Override
    public void tick() {
        // CraftBukkit start
        if (this.joining) {
            this.joining = false;
        }
        // CraftBukkit end
        this.gameMode.tick();
        this.wardenSpawnTracker.tick();
        --this.spawnInvulnerableTime;
        if (this.invulnerableTime > 0) {
            --this.invulnerableTime;
        }

        // Paper start - Configurable container update tick rate
        if (--containerUpdateDelay <= 0) {
            this.containerMenu.broadcastChanges();
            containerUpdateDelay = this.level().paperConfig().tickRates.containerUpdate;
        }
        // Paper end - Configurable container update tick rate
        if (!this.level().isClientSide && this.containerMenu != this.inventoryMenu && (this.isImmobile() || !this.containerMenu.stillValid(this))) { // Paper - Prevent opening inventories when frozen
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.CANT_USE); // Paper - Inventory close reason
            this.containerMenu = this.inventoryMenu;
        }

        Entity entity = this.getCamera();

        if (entity != this) {
            if (entity.isAlive()) {
                this.absMoveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
                this.serverLevel().getChunkSource().move(this);
                if (this.wantsToStopRiding()) {
                    this.setCamera(this);
                }
            } else {
                this.setCamera(this);
            }
        }

        CriteriaTriggers.TICK.trigger(this);
        if (this.levitationStartPos != null) {
            CriteriaTriggers.LEVITATION.trigger(this, this.levitationStartPos, this.tickCount - this.levitationStartTime);
        }

        this.trackStartFallingPosition();
        this.trackEnteredOrExitedLavaOnVehicle();
        this.updatePlayerAttributes();
        this.advancements.flushDirty(this);
    }

    private void updatePlayerAttributes() {
        AttributeInstance attributemodifiable = this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);

        if (attributemodifiable != null) {
            if (this.isCreative()) {
                attributemodifiable.addOrUpdateTransientModifier(ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            } else {
                attributemodifiable.removeModifier(ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER);
            }
        }

        AttributeInstance attributemodifiable1 = this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

        if (attributemodifiable1 != null) {
            if (this.isCreative()) {
                attributemodifiable1.addOrUpdateTransientModifier(ServerPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            } else {
                attributemodifiable1.removeModifier(ServerPlayer.CREATIVE_ENTITY_INTERACTION_RANGE_MODIFIER);
            }
        }

    }

    public void doTick() {
        try {
            if (valid && !this.isSpectator() || !this.touchingUnloadedChunk()) { // Paper - don't tick dead players that are not in the world currently (pending respawn)
                super.tick();
            }

            for (int i = 0; i < this.getInventory().getContainerSize(); ++i) {
                ItemStack itemstack = this.getInventory().getItem(i);

                if (itemstack.getItem().isComplex()) {
                    Packet<?> packet = ((ComplexItem) itemstack.getItem()).getUpdatePacket(itemstack, this.level(), this);

                    if (packet != null) {
                        this.connection.send(packet);
                    }
                }
            }

            if (this.getHealth() != this.lastSentHealth || this.lastSentFood != this.foodData.getFoodLevel() || this.foodData.getSaturationLevel() == 0.0F != this.lastFoodSaturationZero) {
                this.connection.send(new ClientboundSetHealthPacket(this.getBukkitEntity().getScaledHealth(), this.foodData.getFoodLevel(), this.foodData.getSaturationLevel())); // CraftBukkit
                this.lastSentHealth = this.getHealth();
                this.lastSentFood = this.foodData.getFoodLevel();
                this.lastFoodSaturationZero = this.foodData.getSaturationLevel() == 0.0F;
            }

            if (this.getHealth() + this.getAbsorptionAmount() != this.lastRecordedHealthAndAbsorption) {
                this.lastRecordedHealthAndAbsorption = this.getHealth() + this.getAbsorptionAmount();
                this.updateScoreForCriteria(ObjectiveCriteria.HEALTH, Mth.ceil(this.lastRecordedHealthAndAbsorption));
            }

            if (this.foodData.getFoodLevel() != this.lastRecordedFoodLevel) {
                this.lastRecordedFoodLevel = this.foodData.getFoodLevel();
                this.updateScoreForCriteria(ObjectiveCriteria.FOOD, Mth.ceil((float) this.lastRecordedFoodLevel));
            }

            if (this.getAirSupply() != this.lastRecordedAirLevel) {
                this.lastRecordedAirLevel = this.getAirSupply();
                this.updateScoreForCriteria(ObjectiveCriteria.AIR, Mth.ceil((float) this.lastRecordedAirLevel));
            }

            if (this.getArmorValue() != this.lastRecordedArmor) {
                this.lastRecordedArmor = this.getArmorValue();
                this.updateScoreForCriteria(ObjectiveCriteria.ARMOR, Mth.ceil((float) this.lastRecordedArmor));
            }

            if (this.totalExperience != this.lastRecordedExperience) {
                this.lastRecordedExperience = this.totalExperience;
                this.updateScoreForCriteria(ObjectiveCriteria.EXPERIENCE, Mth.ceil((float) this.lastRecordedExperience));
            }

            // CraftBukkit start - Force max health updates
            if (this.maxHealthCache != this.getMaxHealth()) {
                this.getBukkitEntity().updateScaledHealth();
            }
            // CraftBukkit end

            if (this.experienceLevel != this.lastRecordedLevel) {
                this.lastRecordedLevel = this.experienceLevel;
                this.updateScoreForCriteria(ObjectiveCriteria.LEVEL, Mth.ceil((float) this.lastRecordedLevel));
            }

            if (this.totalExperience != this.lastSentExp) {
                this.lastSentExp = this.totalExperience;
                this.connection.send(new ClientboundSetExperiencePacket(this.experienceProgress, this.totalExperience, this.experienceLevel));
            }

            if (this.tickCount % 20 == 0) {
                CriteriaTriggers.LOCATION.trigger(this);
            }

            // CraftBukkit start - initialize oldLevel, fire PlayerLevelChangeEvent, and tick client-sided world border
            if (this.oldLevel == -1) {
                this.oldLevel = this.experienceLevel;
            }

            if (this.oldLevel != this.experienceLevel) {
                CraftEventFactory.callPlayerLevelChangeEvent(this.getBukkitEntity(), this.oldLevel, this.experienceLevel);
                this.oldLevel = this.experienceLevel;
            }

            if (this.getBukkitEntity().hasClientWorldBorder()) {
                ((CraftWorldBorder) this.getBukkitEntity().getWorldBorder()).getHandle().tick();
            }
            // CraftBukkit end
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Ticking player");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("Player being ticked");

            this.fillCrashReportCategory(crashreportsystemdetails);
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public void resetFallDistance() {
        if (this.getHealth() > 0.0F && this.startingToFallPosition != null) {
            CriteriaTriggers.FALL_FROM_HEIGHT.trigger(this, this.startingToFallPosition);
        }

        this.startingToFallPosition = null;
        super.resetFallDistance();
    }

    public void trackStartFallingPosition() {
        if (this.fallDistance > 0.0F && this.startingToFallPosition == null) {
            this.startingToFallPosition = this.position();
            if (this.currentImpulseImpactPos != null && this.currentImpulseImpactPos.y <= this.startingToFallPosition.y) {
                CriteriaTriggers.FALL_AFTER_EXPLOSION.trigger(this, this.currentImpulseImpactPos, this.currentExplosionCause);
            }
        }

    }

    public void trackEnteredOrExitedLavaOnVehicle() {
        if (this.getVehicle() != null && this.getVehicle().isInLava()) {
            if (this.enteredLavaOnVehiclePosition == null) {
                this.enteredLavaOnVehiclePosition = this.position();
            } else {
                CriteriaTriggers.RIDE_ENTITY_IN_LAVA_TRIGGER.trigger(this, this.enteredLavaOnVehiclePosition);
            }
        }

        if (this.enteredLavaOnVehiclePosition != null && (this.getVehicle() == null || !this.getVehicle().isInLava())) {
            this.enteredLavaOnVehiclePosition = null;
        }

    }

    private void updateScoreForCriteria(ObjectiveCriteria criterion, int score) {
        // CraftBukkit - Use our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(criterion, this, (scoreaccess) -> {
            scoreaccess.set(score);
        });
    }

    // Paper start - PlayerDeathEvent#getItemsToKeep
    private static void processKeep(org.bukkit.event.entity.PlayerDeathEvent event, NonNullList<ItemStack> inv) {
        List<org.bukkit.inventory.ItemStack> itemsToKeep = event.getItemsToKeep();
        if (inv == null) {
            // remainder of items left in toKeep - plugin added stuff on death that wasn't in the initial loot?
            if (!itemsToKeep.isEmpty()) {
                for (org.bukkit.inventory.ItemStack itemStack : itemsToKeep) {
                    event.getEntity().getInventory().addItem(itemStack);
                }
            }

            return;
        }

        for (int i = 0; i < inv.size(); ++i) {
            ItemStack item = inv.get(i);
            if (EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) || itemsToKeep.isEmpty() || item.isEmpty()) {
                inv.set(i, ItemStack.EMPTY);
                continue;
            }

            final org.bukkit.inventory.ItemStack bukkitStack = item.getBukkitStack();
            boolean keep = false;
            final Iterator<org.bukkit.inventory.ItemStack> iterator = itemsToKeep.iterator();
            while (iterator.hasNext()) {
                final org.bukkit.inventory.ItemStack itemStack = iterator.next();
                if (bukkitStack.equals(itemStack)) {
                    iterator.remove();
                    keep = true;
                    break;
                }
            }

            if (!keep) {
                inv.set(i, ItemStack.EMPTY);
            }
        }
    }
    // Paper end - PlayerDeathEvent#getItemsToKeep

    @Override
    public void die(DamageSource damageSource) {
        // this.gameEvent(GameEvent.ENTITY_DIE); // Paper - move below event cancellation check
        boolean flag = this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES);
        // CraftBukkit start - fire PlayerDeathEvent
        if (this.isRemoved()) {
            return;
        }
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<>(this.getInventory().getContainerSize());
        boolean keepInventory = this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || this.isSpectator();

        if (!keepInventory) {
            for (ItemStack item : this.getInventory().getContents()) {
                if (!item.isEmpty() && !EnchantmentHelper.has(item, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
                    loot.add(CraftItemStack.asCraftMirror(item).markForInventoryDrop());
                }
            }
        }
        if (this.shouldDropLoot() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) { // Paper - fix player loottables running when mob loot gamerule is false
        // SPIGOT-5071: manually add player loot tables (SPIGOT-5195 - ignores keepInventory rule)
        this.dropFromLootTable(damageSource, this.lastHurtByPlayerTime > 0);
        this.dropCustomDeathLoot(this.serverLevel(), damageSource, flag);

        loot.addAll(this.drops);
        this.drops.clear(); // SPIGOT-5188: make sure to clear
        } // Paper - fix player loottables running when mob loot gamerule is false

        Component defaultMessage = this.getCombatTracker().getDeathMessage();

        String deathmessage = defaultMessage.getString();
        this.keepLevel = keepInventory; // SPIGOT-2222: pre-set keepLevel
        org.bukkit.event.entity.PlayerDeathEvent event = CraftEventFactory.callPlayerDeathEvent(this, damageSource, loot, PaperAdventure.asAdventure(defaultMessage), keepInventory); // Paper - Adventure
        // Paper start - cancellable death event
        if (event.isCancelled()) {
            // make compatible with plugins that might have already set the health in an event listener
            if (this.getHealth() <= 0) {
                this.setHealth((float) event.getReviveHealth());
            }
            return;
        }
        this.gameEvent(GameEvent.ENTITY_DIE); // moved from the top of this method
        // Paper end

        // SPIGOT-943 - only call if they have an inventory open
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.DEATH); // Paper - Inventory close reason
        }

        net.kyori.adventure.text.Component deathMessage = event.deathMessage() != null ? event.deathMessage() : net.kyori.adventure.text.Component.empty(); // Paper - Adventure

        if (deathMessage != null && deathMessage != net.kyori.adventure.text.Component.empty() && flag) { // Paper - Adventure // TODO: allow plugins to override?
            Component ichatbasecomponent = PaperAdventure.asVanilla(deathMessage); // Paper - Adventure

            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), ichatbasecomponent), PacketSendListener.exceptionallySend(() -> {
                boolean flag1 = true;
                String s = ichatbasecomponent.getString(256);
                MutableComponent ichatmutablecomponent = Component.translatable("death.attack.message_too_long", Component.literal(s).withStyle(ChatFormatting.YELLOW));
                MutableComponent ichatmutablecomponent1 = Component.translatable("death.attack.even_more_magic", this.getDisplayName()).withStyle((chatmodifier) -> {
                    return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ichatmutablecomponent));
                });

                return new ClientboundPlayerCombatKillPacket(this.getId(), ichatmutablecomponent1);
            }));
            PlayerTeam scoreboardteam = this.getTeam();

            if (scoreboardteam != null && scoreboardteam.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
                if (scoreboardteam.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                    this.server.getPlayerList().broadcastSystemToTeam(this, ichatbasecomponent);
                } else if (scoreboardteam.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                    this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, ichatbasecomponent);
                }
            } else {
                this.server.getPlayerList().broadcastSystemMessage(ichatbasecomponent, false);
            }
        } else {
            this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), CommonComponents.EMPTY));
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }
        // SPIGOT-5478 must be called manually now
        if (event.shouldDropExperience()) this.dropExperience(damageSource.getEntity()); // Paper - tie to event
        // we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.
        if (!event.getKeepInventory()) {
            // Paper start - PlayerDeathEvent#getItemsToKeep
            for (NonNullList<ItemStack> inv : this.getInventory().compartments) {
                processKeep(event, inv);
            }
            processKeep(event, null);
            // Paper end - PlayerDeathEvent#getItemsToKeep
        }

        this.setCamera(this); // Remove spectated target
        // CraftBukkit end

        // CraftBukkit - Get our scores instead
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.DEATH_COUNT, this, ScoreAccess::increment);
        LivingEntity entityliving = this.getKillCredit();

        if (entityliving != null) {
            this.awardStat(Stats.ENTITY_KILLED_BY.get(entityliving.getType()));
            entityliving.awardKillScore(this, this.deathScore, damageSource);
            this.createWitherRose(entityliving);
        }

        this.level().broadcastEntityEvent(this, (byte) 3);
        this.awardStat(Stats.DEATHS);
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        this.clearFire();
        this.setTicksFrozen(0);
        this.setSharedFlagOnFire(false);
        this.getCombatTracker().recheckStatus();
        this.setLastDeathLocation(Optional.of(GlobalPos.of(this.level().dimension(), this.blockPosition())));
    }

    private void tellNeutralMobsThatIDied() {
        AABB axisalignedbb = (new AABB(this.blockPosition())).inflate(32.0D, 10.0D, 32.0D);

        this.level().getEntitiesOfClass(Mob.class, axisalignedbb, EntitySelector.NO_SPECTATORS).stream().filter((entityinsentient) -> {
            return entityinsentient instanceof NeutralMob;
        }).forEach((entityinsentient) -> {
            ((NeutralMob) entityinsentient).playerDied(this);
        });
    }

    @Override
    public void awardKillScore(Entity entityKilled, int score, DamageSource damageSource) {
        if (entityKilled != this) {
            super.awardKillScore(entityKilled, score, damageSource);
            this.increaseScore(score);
            // CraftBukkit - Get our scores instead
            this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_ALL, this, ScoreAccess::increment);
            if (entityKilled instanceof net.minecraft.world.entity.player.Player) {
                this.awardStat(Stats.PLAYER_KILLS);
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(ObjectiveCriteria.KILL_COUNT_PLAYERS, this, ScoreAccess::increment);
            } else {
                this.awardStat(Stats.MOB_KILLS);
            }

            this.handleTeamKill(this, entityKilled, ObjectiveCriteria.TEAM_KILL);
            this.handleTeamKill(entityKilled, this, ObjectiveCriteria.KILLED_BY_TEAM);
            CriteriaTriggers.PLAYER_KILLED_ENTITY.trigger(this, entityKilled, damageSource);
        }
    }

    private void handleTeamKill(ScoreHolder targetScoreHolder, ScoreHolder aboutScoreHolder, ObjectiveCriteria[] criterions) {
        PlayerTeam scoreboardteam = this.getScoreboard().getPlayersTeam(aboutScoreHolder.getScoreboardName());

        if (scoreboardteam != null) {
            int i = scoreboardteam.getColor().getId();

            if (i >= 0 && i < criterions.length) {
                // CraftBukkit - Get our scores instead
                this.level().getCraftServer().getScoreboardManager().forAllObjectives(criterions[i], targetScoreHolder, ScoreAccess::increment);
            }
        }

    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            boolean flag = this.server.isDedicatedServer() && this.isPvpAllowed() && source.is(DamageTypeTags.IS_FALL);

            if (!flag && this.spawnInvulnerableTime > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                return false;
            } else {
                Entity entity = source.getEntity();

                if (entity instanceof net.minecraft.world.entity.player.Player) {
                    net.minecraft.world.entity.player.Player entityhuman = (net.minecraft.world.entity.player.Player) entity;

                    if (!this.canHarmPlayer(entityhuman)) {
                        return false;
                    }
                }

                if (entity instanceof AbstractArrow) {
                    AbstractArrow entityarrow = (AbstractArrow) entity;
                    Entity entity1 = entityarrow.getOwner();

                    if (entity1 instanceof net.minecraft.world.entity.player.Player) {
                        net.minecraft.world.entity.player.Player entityhuman1 = (net.minecraft.world.entity.player.Player) entity1;

                        if (!this.canHarmPlayer(entityhuman1)) {
                            return false;
                        }
                    }
                }
                // Paper start - cancellable death events
                //return super.hurt(source, amount);
                this.queueHealthUpdatePacket = true;
                boolean damaged = super.hurt(source, amount);
                this.queueHealthUpdatePacket = false;
                if (this.queuedHealthUpdatePacket != null) {
                    this.connection.send(this.queuedHealthUpdatePacket);
                    this.queuedHealthUpdatePacket = null;
                }
                return damaged;
                // Paper end
            }
        }
    }

    @Override
    public boolean canHarmPlayer(net.minecraft.world.entity.player.Player player) {
        return !this.isPvpAllowed() ? false : super.canHarmPlayer(player);
    }

    private boolean isPvpAllowed() {
        // CraftBukkit - this.server.isPvpAllowed() -> this.world.pvpMode
        return this.level().pvpMode;
    }

    // CraftBukkit start
    public DimensionTransition findRespawnPositionAndUseSpawnBlock(boolean flag, DimensionTransition.PostDimensionTransition dimensiontransition_a, PlayerRespawnEvent.RespawnReason reason) {
        DimensionTransition dimensionTransition;
        boolean isBedSpawn = false;
        boolean isAnchorSpawn = false;
        // CraftBukkit end
        BlockPos blockposition = this.getRespawnPosition();
        float f = this.getRespawnAngle();
        boolean flag1 = this.isRespawnForced();
        ServerLevel worldserver = this.server.getLevel(this.getRespawnDimension());

        if (worldserver != null && blockposition != null) {
            Optional<ServerPlayer.RespawnPosAngle> optional = ServerPlayer.findRespawnAndUseSpawnBlock(worldserver, blockposition, f, flag1, flag);

            if (optional.isPresent()) {
                ServerPlayer.RespawnPosAngle entityplayer_respawnposangle = (ServerPlayer.RespawnPosAngle) optional.get();

                // CraftBukkit start
                isBedSpawn = entityplayer_respawnposangle.isBedSpawn();
                isAnchorSpawn = entityplayer_respawnposangle.isAnchorSpawn();
                dimensionTransition = new DimensionTransition(worldserver, entityplayer_respawnposangle.position(), Vec3.ZERO, entityplayer_respawnposangle.yaw(), 0.0F, dimensiontransition_a);
                // CraftBukkit end
            } else {
                dimensionTransition = DimensionTransition.missingRespawnBlock(this.server.overworld(), this, dimensiontransition_a); // CraftBukkit
            }
        } else {
            dimensionTransition = new DimensionTransition(this.server.overworld(), this, dimensiontransition_a); // CraftBukkit
        }
        // CraftBukkit start
        if (reason == null) {
            return dimensionTransition;
        }

        Player respawnPlayer = this.getBukkitEntity();
        Location location = CraftLocation.toBukkit(dimensionTransition.pos(), dimensionTransition.newLevel().getWorld(), dimensionTransition.yRot(), dimensionTransition.xRot());

        // Paper start - respawn flags
        com.google.common.collect.ImmutableSet.Builder<org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag> builder = com.google.common.collect.ImmutableSet.builder();
        if (reason == org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            builder.add(org.bukkit.event.player.PlayerRespawnEvent.RespawnFlag.END_PORTAL);
        }
        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(respawnPlayer, location, isBedSpawn, isAnchorSpawn, reason, builder);
        // Paper end - respawn flags
        this.level().getCraftServer().getPluginManager().callEvent(respawnEvent);
        // Spigot Start
        if (this.connection.isDisconnected()) {
            return null;
        }
        // Spigot End

        location = respawnEvent.getRespawnLocation();

        return new DimensionTransition(((CraftWorld) location.getWorld()).getHandle(), CraftLocation.toVec3D(location), dimensionTransition.speed(), location.getYaw(), location.getPitch(), dimensionTransition.missingRespawnBlock(), dimensionTransition.postDimensionTransition(), dimensionTransition.cause());
        // CraftBukkit end
    }

    public static Optional<ServerPlayer.RespawnPosAngle> findRespawnAndUseSpawnBlock(ServerLevel world, BlockPos pos, float spawnAngle, boolean spawnForced, boolean alive) {
        BlockState iblockdata = world.getBlockState(pos);
        Block block = iblockdata.getBlock();

        if (block instanceof RespawnAnchorBlock && (spawnForced || (Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) > 0) && RespawnAnchorBlock.canSetSpawn(world)) {
            Optional<Vec3> optional = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, world, pos);

            if (!spawnForced && !alive && optional.isPresent()) {
                world.setBlock(pos, (BlockState) iblockdata.setValue(RespawnAnchorBlock.CHARGE, (Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) - 1), 3);
            }

            return optional.map((vec3d) -> {
                return ServerPlayer.RespawnPosAngle.of(vec3d, pos, false, true); // CraftBukkit
            });
        } else if (block instanceof BedBlock && BedBlock.canSetSpawn(world)) {
            return BedBlock.findStandUpPosition(EntityType.PLAYER, world, pos, (Direction) iblockdata.getValue(BedBlock.FACING), spawnAngle).map((vec3d) -> {
                return ServerPlayer.RespawnPosAngle.of(vec3d, pos, true, false); // CraftBukkit
            });
        } else if (!spawnForced) {
            return Optional.empty();
        } else {
            boolean flag2 = block.isPossibleToRespawnInThis(iblockdata);
            BlockState iblockdata1 = world.getBlockState(pos.above());
            boolean flag3 = iblockdata1.getBlock().isPossibleToRespawnInThis(iblockdata1);

            return flag2 && flag3 ? Optional.of(new ServerPlayer.RespawnPosAngle(new Vec3((double) pos.getX() + 0.5D, (double) pos.getY() + 0.1D, (double) pos.getZ() + 0.5D), spawnAngle, false, false)) : Optional.empty(); // CraftBukkit
        }
    }

    public void showEndCredits() {
        this.unRide();
        this.serverLevel().removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
        if (!this.wonGame) {
            this.wonGame = true;
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.WIN_GAME, 0.0F));
            this.seenCredits = true;
        }

    }

    @Nullable
    @Override
    public Entity changeDimension(DimensionTransition teleportTarget) {
        if (this.isSleeping()) return null; // CraftBukkit - SPIGOT-3154
        if (this.isRemoved()) {
            return null;
        } else {
            if (teleportTarget.missingRespawnBlock()) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE, 0.0F));
            }

            ServerLevel worldserver = teleportTarget.newLevel();
            ServerLevel worldserver1 = this.serverLevel();
            // CraftBukkit start
            ResourceKey<LevelStem> resourcekey = worldserver1.getTypeKey();

            if (worldserver != null && worldserver.dimension() == worldserver1.dimension()) { // CraftBukkit
                // Paper start - gateway-specific teleport event
                if (this.portalProcess != null && this.portalProcess.isSamePortal(((net.minecraft.world.level.block.EndGatewayBlock) net.minecraft.world.level.block.Blocks.END_GATEWAY)) && this.serverLevel().getBlockEntity(this.portalProcess.getEntryPosition()) instanceof net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
                    Location to = CraftLocation.toBukkit(teleportTarget.pos(), this.serverLevel().getWorld(), teleportTarget.yRot(), teleportTarget.xRot());
                    final com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent event = new com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent(this.getBukkitEntity(), this.getBukkitEntity().getLocation(), to, new org.bukkit.craftbukkit.block.CraftEndGateway(to.getWorld(), theEndGatewayBlockEntity));
                    if (!event.callEvent() || event.getTo() == null) {
                        return null;
                    }
                    this.connection.teleport(event.getTo());
                } else {
                    // Paper end - gateway-specific teleport event
                boolean result = this.connection.teleport(teleportTarget.pos().x, teleportTarget.pos().y, teleportTarget.pos().z, teleportTarget.yRot(), teleportTarget.xRot(), teleportTarget.cause());
                if (!result) {
                    return null;
                }
                // CraftBukkit end
                } // Paper
                this.connection.resetPosition();
                teleportTarget.postDimensionTransition().onTransition(this);
                return this;
            } else {
                // CraftBukkit start
                /*
                this.isChangingDimension = true;
                WorldData worlddata = worldserver.getLevelData();

                this.connection.send(new PacketPlayOutRespawn(this.createCommonSpawnInfo(worldserver), (byte) 3));
                this.connection.send(new PacketPlayOutServerDifficulty(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                */
                // CraftBukkit end
                worldserver1.getProfiler().push("moving");
                if (worldserver != null && resourcekey == LevelStem.OVERWORLD && worldserver.getTypeKey() == LevelStem.NETHER) { // CraftBukkit - empty to fall through to null to event
                    this.enteredNetherPosition = this.position();
                }
                // CraftBukkit start
                Location enter = this.getBukkitEntity().getLocation();
                Location exit = (worldserver == null) ? null : CraftLocation.toBukkit(teleportTarget.pos(), worldserver.getWorld(), teleportTarget.yRot(), teleportTarget.xRot());
                PlayerTeleportEvent tpEvent = new PlayerTeleportEvent(this.getBukkitEntity(), enter, exit, teleportTarget.cause());
                Bukkit.getServer().getPluginManager().callEvent(tpEvent);
                if (tpEvent.isCancelled() || tpEvent.getTo() == null) {
                    return null;
                }
                exit = tpEvent.getTo();
                worldserver = ((CraftWorld) exit.getWorld()).getHandle();
                // CraftBukkit end

                worldserver1.getProfiler().pop();
                worldserver1.getProfiler().push("placing");
                // CraftBukkit start
                this.isChangingDimension = true; // CraftBukkit - Set teleport invulnerability only if player changing worlds
                LevelData worlddata = worldserver.getLevelData();

                this.connection.send(new ClientboundRespawnPacket(this.createCommonSpawnInfo(worldserver), (byte) 3));
                this.connection.send(new ClientboundChangeDifficultyPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
                PlayerList playerlist = this.server.getPlayerList();

                playerlist.sendPlayerPermissionLevel(this);
                worldserver1.removePlayerImmediately(this, Entity.RemovalReason.CHANGED_DIMENSION);
                this.unsetRemoved();
                // CraftBukkit end
                this.setServerLevel(worldserver);
                this.connection.teleport(exit); // CraftBukkit - use internal teleport without event
                this.connection.resetPosition();
                worldserver.addDuringTeleport(this);
                worldserver1.getProfiler().pop();
                this.triggerDimensionChangeTriggers(worldserver1);
                this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
                playerlist.sendLevelInfo(this, worldserver);
                playerlist.sendAllPlayerInfo(this);
                playerlist.sendActivePlayerEffects(this);
                teleportTarget.postDimensionTransition().onTransition(this);
                this.lastSentExp = -1;
                this.lastSentHealth = -1.0F;
                this.lastSentFood = -1;

                // CraftBukkit start
                PlayerChangedWorldEvent changeEvent = new PlayerChangedWorldEvent(this.getBukkitEntity(), worldserver1.getWorld());
                this.level().getCraftServer().getPluginManager().callEvent(changeEvent);
                // CraftBukkit end
                // Paper start - Reset shield blocking on dimension change
                if (this.isBlocking()) {
                    this.stopUsingItem();
                }
                // Paper end - Reset shield blocking on dimension change
                return this;
            }
        }
    }

    // CraftBukkit start
    @Override
    public CraftPortalEvent callPortalEvent(Entity entity, Location exit, TeleportCause cause, int searchRadius, int creationRadius) {
        Location enter = this.getBukkitEntity().getLocation();
        PlayerPortalEvent event = new PlayerPortalEvent(this.getBukkitEntity(), enter, exit, cause, searchRadius, true, creationRadius);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getTo() == null || event.getTo().getWorld() == null) {
            return null;
        }
        return new CraftPortalEvent(event);
    }
    // CraftBukkit end

    public void triggerDimensionChangeTriggers(ServerLevel origin) {
        ResourceKey<Level> resourcekey = origin.dimension();
        ResourceKey<Level> resourcekey1 = this.level().dimension();
        // CraftBukkit start
        ResourceKey<Level> maindimensionkey = CraftDimensionUtil.getMainDimensionKey(origin);
        ResourceKey<Level> maindimensionkey1 = CraftDimensionUtil.getMainDimensionKey(this.level());

        // Paper start - Add option for strict advancement dimension checks
        if (io.papermc.paper.configuration.GlobalConfiguration.get().misc.strictAdvancementDimensionCheck) {
            maindimensionkey = resourcekey;
            maindimensionkey1 = resourcekey1;
        }
        // Paper end - Add option for strict advancement dimension checks
        CriteriaTriggers.CHANGED_DIMENSION.trigger(this, maindimensionkey, maindimensionkey1);
        if (maindimensionkey != resourcekey || maindimensionkey1 != resourcekey1) {
            CriteriaTriggers.CHANGED_DIMENSION.trigger(this, resourcekey, resourcekey1);
        }

        if (maindimensionkey == Level.NETHER && maindimensionkey1 == Level.OVERWORLD && this.enteredNetherPosition != null) {
            // CraftBukkit end
            CriteriaTriggers.NETHER_TRAVEL.trigger(this, this.enteredNetherPosition);
        }

        if (maindimensionkey1 != Level.NETHER) { // CraftBukkit
            this.enteredNetherPosition = null;
        }

    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer spectator) {
        return spectator.isSpectator() ? this.getCamera() == this : (this.isSpectator() ? false : super.broadcastToPlayer(spectator));
    }

    @Override
    public void take(Entity item, int count) {
        super.take(item, count);
        this.containerMenu.broadcastChanges();
    }

    // CraftBukkit start - moved bed result checks from below into separate method
    private Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> getBedResult(BlockPos blockposition, Direction enumdirection) {
        if (!this.isSleeping() && this.isAlive()) {
            if (!this.level().dimensionType().natural() || !this.level().dimensionType().bedWorks()) {
                return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
            } else if (!this.bedInRange(blockposition, enumdirection)) {
                return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.TOO_FAR_AWAY);
            } else if (this.bedBlocked(blockposition, enumdirection)) {
                return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.OBSTRUCTED);
            } else {
                this.setRespawnPosition(this.level().dimension(), blockposition, this.getYRot(), false, true, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.BED); // Paper - Add PlayerSetSpawnEvent
                if (this.level().isDay()) {
                    return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_POSSIBLE_NOW);
                } else {
                    if (!this.isCreative()) {
                        double d0 = 8.0D;
                        double d1 = 5.0D;
                        Vec3 vec3d = Vec3.atBottomCenterOf(blockposition);
                        List<Monster> list = this.level().getEntitiesOfClass(Monster.class, new AABB(vec3d.x() - 8.0D, vec3d.y() - 5.0D, vec3d.z() - 8.0D, vec3d.x() + 8.0D, vec3d.y() + 5.0D, vec3d.z() + 8.0D), (entitymonster) -> {
                            return entitymonster.isPreventingPlayerRest(this);
                        });

                        if (!list.isEmpty()) {
                            return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.NOT_SAFE);
                        }
                    }

                    return Either.right(Unit.INSTANCE);
                }
            }
        } else {
            return Either.left(net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    @Override
    public Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> startSleepInBed(BlockPos blockposition, boolean force) {
        Direction enumdirection = (Direction) this.level().getBlockState(blockposition).getValue(HorizontalDirectionalBlock.FACING);
        Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> bedResult = this.getBedResult(blockposition, enumdirection);

        if (bedResult.left().orElse(null) == net.minecraft.world.entity.player.Player.BedSleepingProblem.OTHER_PROBLEM) {
            return bedResult; // return immediately if the result is not bypassable by plugins
        }

        if (force) {
            bedResult = Either.right(Unit.INSTANCE);
        }

        bedResult = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerBedEnterEvent(this, blockposition, bedResult);
        if (bedResult.left().isPresent()) {
            return bedResult;
        }

        {
            {
                {
                    Either<net.minecraft.world.entity.player.Player.BedSleepingProblem, Unit> either = super.startSleepInBed(blockposition, force).ifRight((unit) -> {
                        this.awardStat(Stats.SLEEP_IN_BED);
                        CriteriaTriggers.SLEPT_IN_BED.trigger(this);
                    });

                    if (!this.serverLevel().canSleepThroughNights()) {
                        this.displayClientMessage(Component.translatable("sleep.not_possible"), true);
                    }

                    ((ServerLevel) this.level()).updateSleepingPlayerList();
                    return either;
                }
            }
        }
        // CraftBukkit end
    }

    @Override
    public void startSleeping(BlockPos pos) {
        this.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        super.startSleeping(pos);
    }

    private boolean bedInRange(BlockPos pos, Direction direction) {
        return this.isReachableBedBlock(pos) || this.isReachableBedBlock(pos.relative(direction.getOpposite()));
    }

    private boolean isReachableBedBlock(BlockPos pos) {
        Vec3 vec3d = Vec3.atBottomCenterOf(pos);

        return Math.abs(this.getX() - vec3d.x()) <= 3.0D && Math.abs(this.getY() - vec3d.y()) <= 2.0D && Math.abs(this.getZ() - vec3d.z()) <= 3.0D;
    }

    private boolean bedBlocked(BlockPos pos, Direction direction) {
        BlockPos blockposition1 = pos.above();

        return !this.freeAt(blockposition1) || !this.freeAt(blockposition1.relative(direction.getOpposite()));
    }

    @Override
    public void stopSleepInBed(boolean skipSleepTimer, boolean updateSleepingPlayers) {
        if (!this.isSleeping()) return; // CraftBukkit - Can't leave bed if not in one!
        // CraftBukkit start - fire PlayerBedLeaveEvent
        CraftPlayer player = this.getBukkitEntity();
        BlockPos bedPosition = this.getSleepingPos().orElse(null);

        org.bukkit.block.Block bed;
        if (bedPosition != null) {
            bed = this.level().getWorld().getBlockAt(bedPosition.getX(), bedPosition.getY(), bedPosition.getZ());
        } else {
            bed = this.level().getWorld().getBlockAt(player.getLocation());
        }

        PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed, true);
        this.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // CraftBukkit end
        if (this.isSleeping()) {
            this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(this, 2));
        }

        super.stopSleepInBed(skipSleepTimer, updateSleepingPlayers);
        if (this.connection != null) {
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), TeleportCause.EXIT_BED); // CraftBukkit
        }

    }

    @Override
    public void dismountTo(double destX, double destY, double destZ) {
        this.removeVehicle();
        this.setPos(destX, destY, destZ);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource damageSource) {
        return super.isInvulnerableTo(damageSource) || this.isChangingDimension()  || !this.level().paperConfig().collisions.allowPlayerCrammingDamage && damageSource == damageSources().cramming(); // Paper - disable player cramming
    }

    @Override
    protected void checkFallDamage(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {}

    @Override
    protected void onChangedBlock(ServerLevel world, BlockPos pos) {
        if (!this.isSpectator()) {
            super.onChangedBlock(world, pos);
        }

    }

    public void doCheckFallDamage(double xDifference, double yDifference, double zDifference, boolean onGround) {
        if (!this.touchingUnloadedChunk()) {
            this.checkSupportingBlock(onGround, new Vec3(xDifference, yDifference, zDifference));
            BlockPos blockposition = this.getOnPosLegacy();
            BlockState iblockdata = this.level().getBlockState(blockposition);

            if (this.spawnExtraParticlesOnFall && onGround && this.fallDistance > 0.0F) {
                Vec3 vec3d = blockposition.getCenter().add(0.0D, 0.5D, 0.0D);
                int i = (int) Mth.clamp(50.0F * this.fallDistance, 0.0F, 200.0F);

                this.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, iblockdata), vec3d.x, vec3d.y, vec3d.z, i, 0.30000001192092896D, 0.30000001192092896D, 0.30000001192092896D, 0.15000000596046448D);
                this.spawnExtraParticlesOnFall = false;
            }

            super.checkFallDamage(yDifference, onGround, iblockdata, blockposition);
        }
    }

    @Override
    public void onExplosionHit(@Nullable Entity entity) {
        super.onExplosionHit(entity);
        this.currentImpulseImpactPos = this.position();
        this.currentExplosionCause = entity;
        this.setIgnoreFallDamageFromCurrentImpulse(entity != null && entity.getType() == EntityType.WIND_CHARGE);
    }

    @Override
    protected void pushEntities() {
        if (this.level().tickRateManager().runsNormally()) {
            super.pushEntities();
        }

    }

    @Override
    public void openTextEdit(SignBlockEntity sign, boolean front) {
        this.connection.send(new ClientboundBlockUpdatePacket(this.level(), sign.getBlockPos()));
        this.connection.send(new ClientboundOpenSignEditorPacket(sign.getBlockPos(), front));
    }

    public int nextContainerCounter() { // CraftBukkit - void -> int
        this.containerCounter = this.containerCounter % 100 + 1;
        return this.containerCounter; // CraftBukkit
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider factory) {
        if (factory == null) {
            return OptionalInt.empty();
        } else {
            // CraftBukkit start - SPIGOT-6552: Handle inventory closing in CraftEventFactory#callInventoryOpenEvent(...)
            /*
            if (this.containerMenu != this.inventoryMenu) {
                this.closeContainer();
            }
            */
            // CraftBukkit end

            this.nextContainerCounter();
            AbstractContainerMenu container = factory.createMenu(this.containerCounter, this.getInventory(), this);

            // CraftBukkit start - Inventory open hook
            if (container != null) {
                container.setTitle(factory.getDisplayName());

                boolean cancelled = false;
                container = CraftEventFactory.callInventoryOpenEvent(this, container, cancelled);
                if (container == null && !cancelled) { // Let pre-cancelled events fall through
                    // SPIGOT-5263 - close chest if cancelled
                    if (factory instanceof Container) {
                        ((Container) factory).stopOpen(this);
                    } else if (factory instanceof ChestBlock.DoubleInventory) {
                        // SPIGOT-5355 - double chests too :(
                        ((ChestBlock.DoubleInventory) factory).inventorylargechest.stopOpen(this);
                    }
                    return OptionalInt.empty();
                }
            }
            // CraftBukkit end
            if (container == null) {
                if (this.isSpectator()) {
                    this.displayClientMessage(Component.translatable("container.spectatorCantOpen").withStyle(ChatFormatting.RED), true);
                }

                return OptionalInt.empty();
            } else {
                // CraftBukkit start
                this.containerMenu = container;
                if (!this.isImmobile()) this.connection.send(new ClientboundOpenScreenPacket(container.containerId, container.getType(), container.getTitle())); // Paper - Prevent opening inventories when frozen
                // CraftBukkit end
                this.initMenu(container);
                return OptionalInt.of(this.containerCounter);
            }
        }
    }

    @Override
    public void sendMerchantOffers(int syncId, MerchantOffers offers, int levelProgress, int experience, boolean leveled, boolean refreshable) {
        this.connection.send(new ClientboundMerchantOffersPacket(syncId, offers, levelProgress, experience, leveled, refreshable));
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
        // CraftBukkit start - Inventory open hook
        this.nextContainerCounter();
        AbstractContainerMenu container = new HorseInventoryMenu(this.containerCounter, this.getInventory(), inventory, horse, horse.getInventoryColumns());
        container.setTitle(horse.getDisplayName());
        container = CraftEventFactory.callInventoryOpenEvent(this, container);

        if (container == null) {
            inventory.stopOpen(this);
            return;
        }
        // CraftBukkit end
        if (this.containerMenu != this.inventoryMenu) {
            this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.OPEN_NEW); // Paper - Inventory close reason
        }

        // this.nextContainerCounter(); // CraftBukkit - moved up
        int i = horse.getInventoryColumns();

        this.connection.send(new ClientboundHorseScreenOpenPacket(this.containerCounter, i, horse.getId()));
        this.containerMenu = container; // CraftBukkit
        this.initMenu(this.containerMenu);
    }

    @Override
    public void openItemGui(ItemStack book, InteractionHand hand) {
        if (book.is(Items.WRITTEN_BOOK)) {
            if (WrittenBookItem.resolveBookComponents(book, this.createCommandSourceStack(), this)) {
                this.containerMenu.broadcastChanges();
            }

            this.connection.send(new ClientboundOpenBookPacket(hand));
        }

    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.connection.send(ClientboundBlockEntityDataPacket.create(commandBlock, BlockEntity::saveCustomOnly));
    }

    @Override
    public void closeContainer() {
        // Paper start - Inventory close reason
        this.closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason.UNKNOWN);
    }
    @Override
    public void closeContainer(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end - Inventory close reason
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.doCloseContainer();
    }
    // Paper start - special close for unloaded inventory
    @Override
    public void closeUnloadedInventory(org.bukkit.event.inventory.InventoryCloseEvent.Reason reason) {
        // copied from above
        CraftEventFactory.handleInventoryCloseEvent(this, reason); // CraftBukkit
        // Paper end
        // copied from below
        this.connection.send(new ClientboundContainerClosePacket(this.containerMenu.containerId));
        this.containerMenu = this.inventoryMenu;
        // do not run close logic
    }
    // Paper end - special close for unloaded inventory

    @Override
    public void doCloseContainer() {
        this.containerMenu.removed(this);
        this.inventoryMenu.transferState(this.containerMenu);
        this.containerMenu = this.inventoryMenu;
    }

    public void setPlayerInput(float sidewaysSpeed, float forwardSpeed, boolean jumping, boolean sneaking) {
        if (this.isPassenger()) {
            if (sidewaysSpeed >= -1.0F && sidewaysSpeed <= 1.0F) {
                this.xxa = sidewaysSpeed;
            }

            if (forwardSpeed >= -1.0F && forwardSpeed <= 1.0F) {
                this.zza = forwardSpeed;
            }

            this.jumping = jumping;
            // CraftBukkit start
            if (sneaking != this.isShiftKeyDown()) {
                PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getBukkitEntity(), sneaking);
                this.server.server.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
            }
            // CraftBukkit end
            this.setShiftKeyDown(sneaking);
        }

    }

    @Override
    public void travel(Vec3 movementInput) {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.travel(movementInput);
        this.checkMovementStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    @Override
    public void rideTick() {
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        super.rideTick();
        this.checkRidingStatistics(this.getX() - d0, this.getY() - d1, this.getZ() - d2);
    }

    public void checkMovementStatistics(double deltaX, double deltaY, double deltaZ) {
        if (!this.isPassenger() && !ServerPlayer.didNotMove(deltaX, deltaY, deltaZ)) {
            int i;

            if (this.isSwimming()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.SWIM_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.SWIM); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isEyeInFluid(FluidTags.WATER)) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_UNDER_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_UNDERWATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.isInWater()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    this.awardStat(Stats.WALK_ON_WATER_ONE_CM, i);
                    this.causeFoodExhaustion(this.level().spigotConfig.swimMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK_ON_WATER); // CraftBukkit - EntityExhaustionEvent // Spigot
                }
            } else if (this.onClimbable()) {
                if (deltaY > 0.0D) {
                    this.awardStat(Stats.CLIMB_ONE_CM, (int) Math.round(deltaY * 100.0D));
                }
            } else if (this.onGround()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 0) {
                    if (this.isSprinting()) {
                        this.awardStat(Stats.SPRINT_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.sprintMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.SPRINT); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else if (this.isCrouching()) {
                        this.awardStat(Stats.CROUCH_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.CROUCH); // CraftBukkit - EntityExhaustionEvent // Spigot
                    } else {
                        this.awardStat(Stats.WALK_ONE_CM, i);
                        this.causeFoodExhaustion(this.level().spigotConfig.otherMultiplier * (float) i * 0.01F, EntityExhaustionEvent.ExhaustionReason.WALK); // CraftBukkit - EntityExhaustionEvent // Spigot
                    }
                }
            } else if (this.isFallFlying()) {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
                this.awardStat(Stats.AVIATE_ONE_CM, i);
            } else {
                i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 100.0F);
                if (i > 25) {
                    this.awardStat(Stats.FLY_ONE_CM, i);
                }
            }

        }
    }

    private void checkRidingStatistics(double deltaX, double deltaY, double deltaZ) {
        if (this.isPassenger() && !ServerPlayer.didNotMove(deltaX, deltaY, deltaZ)) {
            int i = Math.round((float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 100.0F);
            Entity entity = this.getVehicle();

            if (entity instanceof AbstractMinecart) {
                this.awardStat(Stats.MINECART_ONE_CM, i);
            } else if (entity instanceof Boat) {
                this.awardStat(Stats.BOAT_ONE_CM, i);
            } else if (entity instanceof Pig) {
                this.awardStat(Stats.PIG_ONE_CM, i);
            } else if (entity instanceof AbstractHorse) {
                this.awardStat(Stats.HORSE_ONE_CM, i);
            } else if (entity instanceof Strider) {
                this.awardStat(Stats.STRIDER_ONE_CM, i);
            }

        }
    }

    private static boolean didNotMove(double deltaX, double deltaY, double deltaZ) {
        return deltaX == 0.0D && deltaY == 0.0D && deltaZ == 0.0D;
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
        this.stats.increment(this, stat, amount);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, (scoreaccess) -> {
            scoreaccess.add(amount);
        });
    }

    @Override
    public void resetStat(Stat<?> stat) {
        this.stats.setValue(this, stat, 0);
        this.level().getCraftServer().getScoreboardManager().forAllObjectives(stat, this, ScoreAccess::reset); // CraftBukkit - Get our scores instead
    }

    @Override
    public int awardRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.addRecipes(recipes, this);
    }

    @Override
    public void triggerRecipeCrafted(RecipeHolder<?> recipe, List<ItemStack> ingredients) {
        CriteriaTriggers.RECIPE_CRAFTED.trigger(this, recipe.id(), ingredients);
    }

    @Override
    public void awardRecipesByKey(List<ResourceLocation> recipes) {
        List<RecipeHolder<?>> list1 = (List) recipes.stream().flatMap((minecraftkey) -> {
            return this.server.getRecipeManager().byKey(minecraftkey).stream();
        }).collect(Collectors.toList());

        this.awardRecipes(list1);
    }

    @Override
    public int resetRecipes(Collection<RecipeHolder<?>> recipes) {
        return this.recipeBook.removeRecipes(recipes, this);
    }

    @Override
    public void giveExperiencePoints(int experience) {
        super.giveExperiencePoints(experience);
        this.lastSentExp = -1;
    }

    public void disconnect() {
        this.disconnected = true;
        this.ejectPassengers();

        // Paper start - Workaround vehicle not tracking the passenger disconnection dismount
        if (this.isPassenger() && this.getVehicle() instanceof ServerPlayer) {
            this.stopRiding();
        }
        // Paper end - Workaround vehicle not tracking the passenger disconnection dismount

        if (this.isSleeping()) {
            this.stopSleepInBed(true, false);
        }

    }

    public boolean hasDisconnected() {
        return this.disconnected;
    }

    public void resetSentInfo() {
        this.lastSentHealth = -1.0E8F;
        this.lastSentExp = -1; // CraftBukkit - Added to reset
    }

    @Override
    public void displayClientMessage(Component message, boolean overlay) {
        this.sendSystemMessage(message, overlay);
    }

    @Override
    public void completeUsingItem() {
        if (!this.useItem.isEmpty() && this.isUsingItem()) {
            this.connection.send(new ClientboundEntityEventPacket(this, (byte) 9));
            super.completeUsingItem();
        }

    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Vec3 target) {
        super.lookAt(anchorPoint, target);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchorPoint, target.x, target.y, target.z));
    }

    public void lookAt(EntityAnchorArgument.Anchor anchorPoint, Entity targetEntity, EntityAnchorArgument.Anchor targetAnchor) {
        Vec3 vec3d = targetAnchor.apply(targetEntity);

        super.lookAt(anchorPoint, vec3d);
        this.connection.send(new ClientboundPlayerLookAtPacket(anchorPoint, targetEntity, targetAnchor));
    }

    public void restoreFrom(ServerPlayer oldPlayer, boolean alive) {
        this.wardenSpawnTracker = oldPlayer.wardenSpawnTracker;
        this.chatSession = oldPlayer.chatSession;
        this.gameMode.setGameModeForPlayer(oldPlayer.gameMode.getGameModeForPlayer(), oldPlayer.gameMode.getPreviousGameModeForPlayer());
        this.onUpdateAbilities();
        this.getAttributes().assignBaseValues(oldPlayer.getAttributes());
        // this.setHealth(this.getMaxHealth()); // CraftBukkit
        if (alive) {
            this.getInventory().replaceWith(oldPlayer.getInventory());
            this.setHealth(oldPlayer.getHealth());
            this.foodData = oldPlayer.foodData;
            Iterator iterator = oldPlayer.getActiveEffects().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                // this.addEffect(new MobEffect(mobeffect)); // CraftBukkit
            }

            this.experienceLevel = oldPlayer.experienceLevel;
            this.totalExperience = oldPlayer.totalExperience;
            this.experienceProgress = oldPlayer.experienceProgress;
            this.setScore(oldPlayer.getScore());
            this.portalProcess = oldPlayer.portalProcess;
        } else if (this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || oldPlayer.isSpectator()) {
            this.getInventory().replaceWith(oldPlayer.getInventory());
            this.experienceLevel = oldPlayer.experienceLevel;
            this.totalExperience = oldPlayer.totalExperience;
            this.experienceProgress = oldPlayer.experienceProgress;
            this.setScore(oldPlayer.getScore());
        }

        this.enchantmentSeed = oldPlayer.enchantmentSeed;
        this.enderChestInventory = oldPlayer.enderChestInventory;
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (Byte) oldPlayer.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION));
        this.lastSentExp = -1;
        this.lastSentHealth = -1.0F;
        this.lastSentFood = -1;
        // this.recipeBook.copyOverData(entityplayer.recipeBook); // CraftBukkit
        this.seenCredits = oldPlayer.seenCredits;
        this.enteredNetherPosition = oldPlayer.enteredNetherPosition;
        this.chunkTrackingView = oldPlayer.chunkTrackingView;
        this.setShoulderEntityLeft(oldPlayer.getShoulderEntityLeft());
        this.setShoulderEntityRight(oldPlayer.getShoulderEntityRight());
        this.setLastDeathLocation(oldPlayer.getLastDeathLocation());
    }

    @Override
    protected void onEffectAdded(MobEffectInstance effect, @Nullable Entity source) {
        super.onEffectAdded(effect, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect, true));
        if (effect.is(MobEffects.LEVITATION)) {
            this.levitationStartTime = this.tickCount;
            this.levitationStartPos = this.position();
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, @Nullable Entity source) {
        super.onEffectUpdated(effect, reapplyEffect, source);
        this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effect, false));
        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, source);
    }

    @Override
    protected void onEffectRemoved(MobEffectInstance effect) {
        super.onEffectRemoved(effect);
        this.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), effect.getEffect()));
        if (effect.is(MobEffects.LEVITATION)) {
            this.levitationStartPos = null;
        }

        CriteriaTriggers.EFFECTS_CHANGED.trigger(this, (Entity) null);
    }

    @Override
    public void teleportTo(double destX, double destY, double destZ) {
        this.connection.teleport(destX, destY, destZ, this.getYRot(), this.getXRot(), RelativeMovement.ROTATION);
    }

    @Override
    public void teleportRelative(double offsetX, double offsetY, double offsetZ) {
        this.connection.teleport(this.getX() + offsetX, this.getY() + offsetY, this.getZ() + offsetZ, this.getYRot(), this.getXRot(), RelativeMovement.ALL);
    }

    @Override
    public boolean teleportTo(ServerLevel world, double destX, double destY, double destZ, Set<RelativeMovement> flags, float yaw, float pitch) {
        // CraftBukkit start
        return this.teleportTo(world, destX, destY, destZ, flags, yaw, pitch, TeleportCause.UNKNOWN);
    }

    public boolean teleportTo(ServerLevel worldserver, double d0, double d1, double d2, Set<RelativeMovement> set, float f, float f1, TeleportCause cause) {
        // CraftBukkit end
        ChunkPos chunkcoordintpair = new ChunkPos(BlockPos.containing(d0, d1, d2));

        worldserver.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkcoordintpair, 1, this.getId());
        this.stopRiding();
        if (this.isSleeping()) {
            this.stopSleepInBed(true, true);
        }

        if (worldserver == this.level()) {
            this.connection.teleport(d0, d1, d2, f, f1, set, cause); // CraftBukkit
        } else {
            this.teleportTo(worldserver, d0, d1, d2, f, f1, cause); // CraftBukkit
        }

        this.setYHeadRot(f);
        return true;
    }

    @Override
    public void moveTo(double x, double y, double z) {
        super.moveTo(x, y, z);
        this.connection.resetPosition();
    }

    @Override
    public void crit(Entity target) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(target, 4));
    }

    @Override
    public void magicCrit(Entity target) {
        this.serverLevel().getChunkSource().broadcastAndSend(this, new ClientboundAnimatePacket(target, 5));
    }

    @Override
    public void onUpdateAbilities() {
        if (this.connection != null) {
            this.connection.send(new ClientboundPlayerAbilitiesPacket(this.getAbilities()));
            this.updateInvisibilityStatus();
        }
    }

    public ServerLevel serverLevel() {
        return (ServerLevel) this.level();
    }

    public boolean setGameMode(GameType gameMode) {
        // Paper start - Expand PlayerGameModeChangeEvent
        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.setGameMode(gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.UNKNOWN, null);
        return event == null ? false : event.isCancelled();
    }
    @Nullable
    public org.bukkit.event.player.PlayerGameModeChangeEvent setGameMode(GameType gameMode, org.bukkit.event.player.PlayerGameModeChangeEvent.Cause cause, @Nullable net.kyori.adventure.text.Component message) {
        boolean flag = this.isSpectator();

        org.bukkit.event.player.PlayerGameModeChangeEvent event = this.gameMode.changeGameModeForPlayer(gameMode, cause, message);
        if (event == null || event.isCancelled()) {
            return null;
            // Paper end - Expand PlayerGameModeChangeEvent
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.CHANGE_GAME_MODE, (float) gameMode.getId()));
            if (gameMode == GameType.SPECTATOR) {
                this.removeEntitiesOnShoulder();
                this.stopRiding();
                EnchantmentHelper.stopLocationBasedEffects(this);
            } else {
                this.setCamera(this);
                if (flag) {
                    EnchantmentHelper.runLocationChangedEffects(this.serverLevel(), this);
                }
            }

            this.onUpdateAbilities();
            this.updateEffectVisibility();
            return event; // Paper - Expand PlayerGameModeChangeEvent
        }
    }

    @Override
    public boolean isSpectator() {
        return this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    @Override
    public boolean isCreative() {
        return this.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
    }

    @Override
    public void sendSystemMessage(Component message) {
        this.sendSystemMessage(message, false);
    }

    public void sendSystemMessage(Component message, boolean overlay) {
        if (this.acceptsSystemMessages(overlay)) {
            this.connection.send(new ClientboundSystemChatPacket(message, overlay), PacketSendListener.exceptionallySend(() -> {
                if (this.acceptsSystemMessages(false)) {
                    boolean flag1 = true;
                    String s = message.getString(256);
                    MutableComponent ichatmutablecomponent = Component.literal(s).withStyle(ChatFormatting.YELLOW);

                    return new ClientboundSystemChatPacket(Component.translatable("multiplayer.message_not_delivered", ichatmutablecomponent).withStyle(ChatFormatting.RED), false);
                } else {
                    return null;
                }
            }));
        }
    }

    public void sendChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled, ChatType.Bound params) {
        // Paper start
        this.sendChatMessage(message, filterMaskEnabled, params, null);
    }
    public void sendChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled, ChatType.Bound params, @Nullable Component unsigned) {
        // Paper end
        if (this.acceptsChatMessages()) {
            message.sendToPlayer(this, filterMaskEnabled, params, unsigned); // Paper
        }

    }

    public String getIpAddress() {
        SocketAddress socketaddress = this.connection.getRemoteAddress();

        if (socketaddress instanceof InetSocketAddress inetsocketaddress) {
            return InetAddresses.toAddrString(inetsocketaddress.getAddress());
        } else {
            return "<unknown>";
        }
    }

    // Paper start - Client option API
    private java.util.Map<com.destroystokyo.paper.ClientOption<?>, ?> getClientOptionMap(String locale, int viewDistance, com.destroystokyo.paper.ClientOption.ChatVisibility chatVisibility, boolean chatColors, com.destroystokyo.paper.PaperSkinParts skinParts, org.bukkit.inventory.MainHand mainHand, boolean allowsServerListing, boolean textFilteringEnabled) {
        java.util.Map<com.destroystokyo.paper.ClientOption<?>, Object> map = new java.util.HashMap<>();
        map.put(com.destroystokyo.paper.ClientOption.LOCALE, locale);
        map.put(com.destroystokyo.paper.ClientOption.VIEW_DISTANCE, viewDistance);
        map.put(com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY, chatVisibility);
        map.put(com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED, chatColors);
        map.put(com.destroystokyo.paper.ClientOption.SKIN_PARTS, skinParts);
        map.put(com.destroystokyo.paper.ClientOption.MAIN_HAND, mainHand);
        map.put(com.destroystokyo.paper.ClientOption.ALLOW_SERVER_LISTINGS, allowsServerListing);
        map.put(com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED, textFilteringEnabled);
        return map;
    }
    // Paper end

    public void updateOptions(ClientInformation clientOptions) {
        new com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent(getBukkitEntity(), getClientOptionMap(clientOptions.language(), clientOptions.viewDistance(), com.destroystokyo.paper.ClientOption.ChatVisibility.valueOf(clientOptions.chatVisibility().name()), clientOptions.chatColors(), new com.destroystokyo.paper.PaperSkinParts(clientOptions.modelCustomisation()), clientOptions.mainHand() == HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT, clientOptions.allowsListing(), clientOptions.textFilteringEnabled())).callEvent(); // Paper - settings event
        // CraftBukkit start
        if (this.getMainArm() != clientOptions.mainHand()) {
            PlayerChangedMainHandEvent event = new PlayerChangedMainHandEvent(this.getBukkitEntity(), this.getMainArm() == HumanoidArm.LEFT ? MainHand.LEFT : MainHand.RIGHT);
            this.server.server.getPluginManager().callEvent(event);
        }
        if (this.language == null || !this.language.equals(clientOptions.language())) { // Paper
            PlayerLocaleChangeEvent event = new PlayerLocaleChangeEvent(this.getBukkitEntity(), clientOptions.language());
            this.server.server.getPluginManager().callEvent(event);
        }
        // CraftBukkit end
        // Paper start - don't call options events on login
        this.updateOptionsNoEvents(clientOptions);
    }
    public void updateOptionsNoEvents(ClientInformation clientOptions) {
        // Paper end
        this.language = clientOptions.language();
        this.adventure$locale = java.util.Objects.requireNonNullElse(net.kyori.adventure.translation.Translator.parseLocale(this.language), java.util.Locale.US); // Paper
        this.requestedViewDistance = clientOptions.viewDistance();
        this.chatVisibility = clientOptions.chatVisibility();
        this.canChatColor = clientOptions.chatColors();
        this.textFilteringEnabled = clientOptions.textFilteringEnabled();
        this.allowsListing = clientOptions.allowsListing();
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION, (byte) clientOptions.modelCustomisation());
        this.getEntityData().set(ServerPlayer.DATA_PLAYER_MAIN_HAND, (byte) clientOptions.mainHand().getId());
    }

    public ClientInformation clientInformation() {
        byte b0 = (Byte) this.getEntityData().get(ServerPlayer.DATA_PLAYER_MODE_CUSTOMISATION);
        HumanoidArm enummainhand = (HumanoidArm) HumanoidArm.BY_ID.apply((Byte) this.getEntityData().get(ServerPlayer.DATA_PLAYER_MAIN_HAND));

        return new ClientInformation(this.language, this.requestedViewDistance, this.chatVisibility, this.canChatColor, b0, enummainhand, this.textFilteringEnabled, this.allowsListing);
    }

    public boolean canChatInColor() {
        return this.canChatColor;
    }

    public ChatVisiblity getChatVisibility() {
        return this.chatVisibility;
    }

    private boolean acceptsSystemMessages(boolean overlay) {
        return this.chatVisibility == ChatVisiblity.HIDDEN ? overlay : true;
    }

    private boolean acceptsChatMessages() {
        return this.chatVisibility == ChatVisiblity.FULL;
    }

    public int requestedViewDistance() {
        return this.requestedViewDistance;
    }

    public void sendServerStatus(ServerStatus metadata) {
        this.connection.send(new ClientboundServerDataPacket(metadata.description(), metadata.favicon().map(ServerStatus.Favicon::iconBytes)));
    }

    @Override
    protected int getPermissionLevel() {
        return this.server.getProfilePermissions(this.getGameProfile());
    }

    public void resetLastActionTime() {
        this.lastActionTime = Util.getMillis();
    }

    public ServerStatsCounter getStats() {
        return this.stats;
    }

    public ServerRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    @Override
    protected void updateInvisibilityStatus() {
        if (this.isSpectator()) {
            this.removeEffectParticles();
            this.setInvisible(true);
        } else {
            super.updateInvisibilityStatus();
        }

    }

    public Entity getCamera() {
        return (Entity) (this.camera == null ? this : this.camera);
    }

    public void setCamera(@Nullable Entity entity) {
        Entity entity1 = this.getCamera();

        this.camera = (Entity) (entity == null ? this : entity);
        if (entity1 != this.camera) {
            // Paper start - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            if (this.camera == this) {
                com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent playerStopSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent(this.getBukkitEntity(), entity1.getBukkitEntity());
                if (!playerStopSpectatingEntityEvent.callEvent()) {
                    this.camera = entity1; // rollback camera entity again
                    return;
                }
            } else {
                com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent playerStartSpectatingEntityEvent = new com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent(this.getBukkitEntity(), entity1.getBukkitEntity(), entity.getBukkitEntity());
                if (!playerStartSpectatingEntityEvent.callEvent()) {
                    this.camera = entity1; // rollback camera entity again
                    return;
                }
            }
            // Paper end - Add PlayerStartSpectatingEntityEvent and PlayerStopSpectatingEntity
            Level world = this.camera.level();

            if (world instanceof ServerLevel) {
                ServerLevel worldserver = (ServerLevel) world;

                this.teleportTo(worldserver, this.camera.getX(), this.camera.getY(), this.camera.getZ(), Set.of(), this.getYRot(), this.getXRot(), TeleportCause.SPECTATE); // CraftBukkit
            }

            if (entity != null) {
                this.serverLevel().getChunkSource().move(this);
            }

            this.connection.send(new ClientboundSetCameraPacket(this.camera));
            this.connection.resetPosition();
        }

    }

    @Override
    protected void processPortalCooldown() {
        if (!this.isChangingDimension) {
            super.processPortalCooldown();
        }

    }

    @Override
    public void attack(Entity target) {
        if (this.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            this.setCamera(target);
        } else {
            super.attack(target);
        }

    }

    public long getLastActionTime() {
        return this.lastActionTime;
    }

    @Nullable
    public Component getTabListDisplayName() {
        return this.listName; // CraftBukkit
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.resetAttackStrengthTicker();
    }

    public boolean isChangingDimension() {
        return this.isChangingDimension;
    }

    public void hasChangedDimension() {
        this.isChangingDimension = false;
    }

    public PlayerAdvancements getAdvancements() {
        return this.advancements;
    }

    // CraftBukkit start
    public void teleportTo(ServerLevel targetWorld, double x, double y, double z, float yaw, float pitch) {
        this.teleportTo(targetWorld, x, y, z, yaw, pitch, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.UNKNOWN);
    }

    public void teleportTo(ServerLevel worldserver, double d0, double d1, double d2, float f, float f1, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        // CraftBukkit end
        this.setCamera(this);
        this.stopRiding();
        /* CraftBukkit start - replace with bukkit handling for multi-world
        if (worldserver == this.level()) {
            this.connection.teleport(d0, d1, d2, f, f1);
        } else {
            this.changeDimension(new DimensionTransition(worldserver, new Vec3D(d0, d1, d2), Vec3D.ZERO, f, f1, DimensionTransition.DO_NOTHING));
        }
        */
        this.getBukkitEntity().teleport(new Location(worldserver.getWorld(), d0, d1, d2, f, f1), cause);
        // CraftBukkit end

    }

    @Nullable
    public BlockPos getRespawnPosition() {
        return this.respawnPosition;
    }

    public float getRespawnAngle() {
        return this.respawnAngle;
    }

    public ResourceKey<Level> getRespawnDimension() {
        return this.respawnDimension;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void copyRespawnPosition(ServerPlayer player) {
        this.setRespawnPosition(player.getRespawnDimension(), player.getRespawnPosition(), player.getRespawnAngle(), player.isRespawnForced(), false);
    }

    @Deprecated // Paper - Add PlayerSetSpawnEvent
    public void setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage) {
        // Paper start - Add PlayerSetSpawnEvent
        this.setRespawnPosition(dimension, pos, angle, forced, sendMessage, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.UNKNOWN);
    }
    @Deprecated
    public boolean setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage, PlayerSpawnChangeEvent.Cause cause) {
        return this.setRespawnPosition(dimension, pos, angle, forced, sendMessage, cause == PlayerSpawnChangeEvent.Cause.RESET ?
            com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN : com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.valueOf(cause.name()));
    }
    public boolean setRespawnPosition(ResourceKey<Level> dimension, @Nullable BlockPos pos, float angle, boolean forced, boolean sendMessage, com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause cause) {
        Location spawnLoc = null;
        boolean willNotify = false;
        if (pos != null) {
            boolean flag2 = pos.equals(this.respawnPosition) && dimension.equals(this.respawnDimension);
            spawnLoc = io.papermc.paper.util.MCUtil.toLocation(this.getServer().getLevel(dimension), pos);
            spawnLoc.setYaw(angle);
            willNotify = sendMessage && !flag2;
        }

        PlayerSpawnChangeEvent dumbEvent = new PlayerSpawnChangeEvent(this.getBukkitEntity(), spawnLoc, forced,
            cause == com.destroystokyo.paper.event.player.PlayerSetSpawnEvent.Cause.PLAYER_RESPAWN ? PlayerSpawnChangeEvent.Cause.RESET : PlayerSpawnChangeEvent.Cause.valueOf(cause.name()));
        dumbEvent.callEvent();

        com.destroystokyo.paper.event.player.PlayerSetSpawnEvent event = new com.destroystokyo.paper.event.player.PlayerSetSpawnEvent(this.getBukkitEntity(), cause, dumbEvent.getNewSpawn(), dumbEvent.isForced(), willNotify, willNotify ? net.kyori.adventure.text.Component.translatable("block.minecraft.set_spawn") : null);
        event.setCancelled(dumbEvent.isCancelled());
        if (!event.callEvent()) {
            return false;
        }
        if (event.getLocation() != null) {
            dimension = event.getLocation().getWorld() != null ? ((CraftWorld) event.getLocation().getWorld()).getHandle().dimension() : dimension;
            pos = io.papermc.paper.util.MCUtil.toBlockPosition(event.getLocation());
            angle = event.getLocation().getYaw();
            forced = event.isForced();
            // Paper end - Add PlayerSetSpawnEvent

            if (event.willNotifyPlayer() && event.getNotification() != null) { // Paper - Add PlayerSetSpawnEvent
                this.sendSystemMessage(PaperAdventure.asVanilla(event.getNotification())); // Paper - Add PlayerSetSpawnEvent
            }

            this.respawnPosition = pos;
            this.respawnDimension = dimension;
            this.respawnAngle = angle;
            this.respawnForced = forced;
        } else {
            this.respawnPosition = null;
            this.respawnDimension = Level.OVERWORLD;
            this.respawnAngle = 0.0F;
            this.respawnForced = false;
        }

        return true; // Paper - Add PlayerSetSpawnEvent
    }

    public SectionPos getLastSectionPos() {
        return this.lastSectionPos;
    }

    public void setLastSectionPos(SectionPos section) {
        this.lastSectionPos = section;
    }

    public ChunkTrackingView getChunkTrackingView() {
        return this.chunkTrackingView;
    }

    public void setChunkTrackingView(ChunkTrackingView chunkFilter) {
        this.chunkTrackingView = chunkFilter;
    }

    @Override
    public void playNotifySound(SoundEvent sound, SoundSource category, float volume, float pitch) {
        this.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, this.getX(), this.getY(), this.getZ(), volume, pitch, this.random.nextLong()));
    }

    @Override
    public ItemEntity drop(ItemStack stack, boolean throwRandomly, boolean retainOwnership) {
        // CraftBukkit start - SPIGOT-2942: Add boolean to call event
        return this.drop(stack, throwRandomly, retainOwnership, true);
    }

    @Override
    public ItemEntity drop(ItemStack itemstack, boolean flag, boolean flag1, boolean callEvent) {
        ItemEntity entityitem = super.drop(itemstack, flag, flag1, callEvent);
        // CraftBukkit end

        if (entityitem == null) {
            return null;
        } else {
            this.level().addFreshEntity(entityitem);
            ItemStack itemstack1 = entityitem.getItem();

            if (flag1) {
                if (!itemstack1.isEmpty()) {
                    this.awardStat(Stats.ITEM_DROPPED.get(itemstack1.getItem()), itemstack1.getCount()); // Paper - Fix PlayerDropItemEvent using wrong item
                }

                this.awardStat(Stats.DROP);
            }

            return entityitem;
        }
    }

    public TextFilter getTextFilter() {
        return this.textFilter;
    }

    public void setServerLevel(ServerLevel world) {
        this.setLevel(world);
        this.gameMode.setLevel(world);
    }

    @Nullable
    private static GameType readPlayerMode(@Nullable CompoundTag nbt, String key) {
        return nbt != null && nbt.contains(key, 99) ? GameType.byId(nbt.getInt(key)) : null;
    }

    private GameType calculateGameModeForNewPlayer(@Nullable GameType backupGameMode) {
        GameType enumgamemode1 = this.server.getForcedGameType();

        return enumgamemode1 != null ? enumgamemode1 : (backupGameMode != null ? backupGameMode : this.server.getDefaultGameType());
    }

    public void loadGameTypes(@Nullable CompoundTag nbt) {
        // Paper start - Expand PlayerGameModeChangeEvent
        if (this.server.getForcedGameType() != null && this.server.getForcedGameType() != ServerPlayer.readPlayerMode(nbt, "playerGameType")) {
            if (new org.bukkit.event.player.PlayerGameModeChangeEvent(this.getBukkitEntity(), org.bukkit.GameMode.getByValue(this.server.getDefaultGameType().getId()), org.bukkit.event.player.PlayerGameModeChangeEvent.Cause.DEFAULT_GAMEMODE, null).callEvent()) {
                this.gameMode.setGameModeForPlayer(this.server.getForcedGameType(), GameType.DEFAULT_MODE);
            } else {
                this.gameMode.setGameModeForPlayer(ServerPlayer.readPlayerMode(nbt,"playerGameType"), ServerPlayer.readPlayerMode(nbt, "previousPlayerGameType"));
            }
            return;
        }
        // Paper end - Expand PlayerGameModeChangeEvent
        this.gameMode.setGameModeForPlayer(this.calculateGameModeForNewPlayer(ServerPlayer.readPlayerMode(nbt, "playerGameType")), ServerPlayer.readPlayerMode(nbt, "previousPlayerGameType"));
    }

    private void storeGameTypes(CompoundTag nbt) {
        nbt.putInt("playerGameType", this.gameMode.getGameModeForPlayer().getId());
        GameType enumgamemode = this.gameMode.getPreviousGameModeForPlayer();

        if (enumgamemode != null) {
            nbt.putInt("previousPlayerGameType", enumgamemode.getId());
        }

    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.textFilteringEnabled;
    }

    public boolean shouldFilterMessageTo(ServerPlayer player) {
        return player == this ? false : this.textFilteringEnabled || player.textFilteringEnabled;
    }

    @Override
    public boolean mayInteract(Level world, BlockPos pos) {
        return super.mayInteract(world, pos) && world.mayInteract(this, pos);
    }

    @Override
    protected void updateUsingItem(ItemStack stack) {
        CriteriaTriggers.USING_ITEM.trigger(this, stack);
        super.updateUsingItem(stack);
    }

    public boolean drop(boolean entireStack) {
        Inventory playerinventory = this.getInventory();
        ItemStack itemstack = playerinventory.removeFromSelected(entireStack);

        this.containerMenu.findSlot(playerinventory, playerinventory.selected).ifPresent((i) -> {
            this.containerMenu.setRemoteSlot(i, playerinventory.getSelected());
        });
        return this.drop(itemstack, false, true) != null;
    }

    public boolean allowsListing() {
        return this.allowsListing;
    }

    @Override
    public Optional<WardenSpawnTracker> getWardenSpawnTracker() {
        return Optional.of(this.wardenSpawnTracker);
    }

    public void setSpawnExtraParticlesOnFall(boolean spawnExtraParticlesOnFall) {
        this.spawnExtraParticlesOnFall = spawnExtraParticlesOnFall;
    }

    @Override
    public void onItemPickup(ItemEntity item) {
        super.onItemPickup(item);
        Entity entity = item.getOwner();

        if (entity != null) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_PLAYER.trigger(this, item.getItem(), entity);
        }

    }

    public void setChatSession(RemoteChatSession session) {
        this.chatSession = session;
    }

    @Nullable
    public RemoteChatSession getChatSession() {
        return this.chatSession != null && this.chatSession.hasExpired() ? null : this.chatSession;
    }

    @Override
    public void indicateDamage(double deltaX, double deltaZ) {
        this.hurtDir = (float) (Mth.atan2(deltaZ, deltaX) * 57.2957763671875D - (double) this.getYRot());
        this.connection.send(new ClientboundHurtAnimationPacket(this));
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        if (super.startRiding(entity, force)) {
            this.setKnownMovement(Vec3.ZERO);
            entity.positionRider(this);
            this.connection.teleport(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
            if (entity instanceof LivingEntity) {
                LivingEntity entityliving = (LivingEntity) entity;

                this.server.getPlayerList().sendActiveEffects(entityliving, this.connection);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void stopRiding() {
        // Paper start - Force entity dismount during teleportation
        this.stopRiding(false);
    }
    @Override
    public void stopRiding(boolean suppressCancellation) {
        // Paper end - Force entity dismount during teleportation
        Entity entity = this.getVehicle();

        super.stopRiding(suppressCancellation); // Paper - Force entity dismount during teleportation
        if (entity instanceof LivingEntity entityliving) {
            Iterator iterator = entityliving.getActiveEffects().iterator();

            while (iterator.hasNext()) {
                MobEffectInstance mobeffect = (MobEffectInstance) iterator.next();

                this.connection.send(new ClientboundRemoveMobEffectPacket(entity.getId(), mobeffect.getEffect()));
            }
        }

    }

    public CommonPlayerSpawnInfo createCommonSpawnInfo(ServerLevel world) {
        return new CommonPlayerSpawnInfo(world.dimensionTypeRegistration(), world.dimension(), BiomeManager.obfuscateSeed(world.getSeed()), this.gameMode.getGameModeForPlayer(), this.gameMode.getPreviousGameModeForPlayer(), world.isDebug(), world.isFlat(), this.getLastDeathLocation(), this.getPortalCooldown());
    }

    public void setRaidOmenPosition(BlockPos startRaidPos) {
        this.raidOmenPosition = startRaidPos;
    }

    public void clearRaidOmenPosition() {
        this.raidOmenPosition = null;
    }

    @Nullable
    public BlockPos getRaidOmenPosition() {
        return this.raidOmenPosition;
    }

    @Override
    public Vec3 getKnownMovement() {
        Entity entity = this.getVehicle();

        return entity != null && entity.getControllingPassenger() != this ? entity.getKnownMovement() : this.lastKnownClientMovement;
    }

    public void setKnownMovement(Vec3 movement) {
        this.lastKnownClientMovement = movement;
    }

    @Override
    protected float getEnchantedDamage(Entity target, float baseDamage, DamageSource damageSource) {
        return EnchantmentHelper.modifyDamage(this.serverLevel(), this.getWeaponItem(), target, damageSource, baseDamage);
    }

    @Override
    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        super.onEquippedItemBroken(item, slot);
        this.awardStat(Stats.ITEM_BROKEN.get(item));
    }

    // CraftBukkit start
    public static record RespawnPosAngle(Vec3 position, float yaw, boolean isBedSpawn, boolean isAnchorSpawn) {

        public static ServerPlayer.RespawnPosAngle of(Vec3 vec3d, BlockPos blockposition, boolean isBedSpawn, boolean isAnchorSpawn) {
            return new ServerPlayer.RespawnPosAngle(vec3d, calculateLookAtYaw(vec3d, blockposition), isBedSpawn, isAnchorSpawn);
            // CraftBukkit end
        }

        private static float calculateLookAtYaw(Vec3 respawnPos, BlockPos currentPos) {
            Vec3 vec3d1 = Vec3.atBottomCenterOf(currentPos).subtract(respawnPos).normalize();

            return (float) Mth.wrapDegrees(Mth.atan2(vec3d1.z, vec3d1.x) * 57.2957763671875D - 90.0D);
        }
    }

    // CraftBukkit start - Add per-player time and weather.
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.level().getDayTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.level().getDayTime() - (this.level().getDayTime() % 24000) + this.timeOffset;
        }
    }

    public WeatherType weather = null;

    public WeatherType getPlayerWeather() {
        return this.weather;
    }

    public void setPlayerWeather(WeatherType type, boolean plugin) {
        if (!plugin && this.weather != null) {
            return;
        }

        if (plugin) {
            this.weather = type;
        }

        if (type == WeatherType.DOWNFALL) {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0));
        } else {
            this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        }
    }

    private float pluginRainPosition;
    private float pluginRainPositionPrevious;

    public void updateWeather(float oldRain, float newRain, float oldThunder, float newThunder) {
        if (this.weather == null) {
            // Vanilla
            if (oldRain != newRain) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, newRain));
            }
        } else {
            // Plugin
            if (this.pluginRainPositionPrevious != this.pluginRainPosition) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, this.pluginRainPosition));
            }
        }

        if (oldThunder != newThunder) {
            if (this.weather == WeatherType.DOWNFALL || this.weather == null) {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, newThunder));
            } else {
                this.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0));
            }
        }
    }

    public void tickWeather() {
        if (this.weather == null) return;

        this.pluginRainPositionPrevious = this.pluginRainPosition;
        if (this.weather == WeatherType.DOWNFALL) {
            this.pluginRainPosition += 0.01;
        } else {
            this.pluginRainPosition -= 0.01;
        }

        this.pluginRainPosition = Mth.clamp(this.pluginRainPosition, 0.0F, 1.0F);
    }

    public void resetPlayerWeather() {
        this.weather = null;
        this.setPlayerWeather(this.level().getLevelData().isRaining() ? WeatherType.DOWNFALL : WeatherType.CLEAR, false);
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.getScoreboardName() + " at " + this.getX() + "," + this.getY() + "," + this.getZ() + ")";
    }

    // SPIGOT-1903, MC-98153
    public void forceSetPositionRotation(double x, double y, double z, float yaw, float pitch) {
        this.moveTo(x, y, z, yaw, pitch);
        this.connection.resetPosition();
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || (this.connection != null && this.connection.isDisconnected()); // Paper - Fix duplication bugs
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.getBukkitEntity().getScoreboard().getHandle();
    }

    public void reset() {
        float exp = 0;
        boolean keepInventory = this.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            exp = this.experienceProgress;
            this.newTotalExp = this.totalExperience;
            this.newLevel = this.experienceLevel;
        }

        this.setHealth(this.getMaxHealth());
        this.stopUsingItem(); // CraftBukkit - SPIGOT-6682: Clear active item on reset
        this.setAirSupply(this.getMaxAirSupply()); // Paper - Reset players airTicks on respawn
        this.setRemainingFireTicks(0);
        this.fallDistance = 0;
        this.foodData = new FoodData(this);
        this.experienceLevel = this.newLevel;
        this.totalExperience = this.newTotalExp;
        this.experienceProgress = 0;
        this.deathTime = 0;
        this.setArrowCount(0, true); // CraftBukkit - ArrowBodyCountChangeEvent
        this.removeAllEffects(org.bukkit.event.entity.EntityPotionEffectEvent.Cause.DEATH);
        this.effectsDirty = true;
        this.containerMenu = this.inventoryMenu;
        this.lastHurtByPlayer = null;
        this.lastHurtByMob = null;
        this.combatTracker = new CombatTracker(this);
        this.lastSentExp = -1;
        if (this.keepLevel) { // CraftBukkit - SPIGOT-6687: Only use keepLevel (was pre-set with RULE_KEEPINVENTORY value in PlayerDeathEvent)
            this.experienceProgress = exp;
        } else {
            this.giveExperiencePoints(this.newExp);
        }
        this.keepLevel = false;
        this.setDeltaMovement(0, 0, 0); // CraftBukkit - SPIGOT-6948: Reset velocity on death
        this.skipDropExperience = false; // CraftBukkit - SPIGOT-7462: Reset experience drop skip, so that further deaths drop xp
    }

    @Override
    public CraftPlayer getBukkitEntity() {
        return (CraftPlayer) super.getBukkitEntity();
    }
    // CraftBukkit end
}
