package io.papermc.paper.configuration;

import co.aikar.timings.MinecraftTimings;
import com.mojang.logging.LogUtils;
import io.papermc.paper.configuration.constraint.Constraints;
import io.papermc.paper.configuration.type.number.DoubleOr;
import io.papermc.paper.configuration.type.number.IntOr;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.PostProcess;
import org.spongepowered.configurate.objectmapping.meta.Required;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

@SuppressWarnings({"CanBeFinal", "FieldCanBeLocal", "FieldMayBeFinal", "NotNullFieldNotInitialized", "InnerClassMayBeStatic"})
public class GlobalConfiguration extends ConfigurationPart {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final int CURRENT_VERSION = 29; // (when you change the version, change the comment, so it conflicts on rebases): <insert changes here>
    private static GlobalConfiguration instance;
    public static GlobalConfiguration get() {
        return instance;
    }
    static void set(GlobalConfiguration instance) {
        GlobalConfiguration.instance = instance;
    }

    @Setting(Configuration.VERSION_FIELD)
    public int version = CURRENT_VERSION;

    public Messages messages;

    public class Messages extends ConfigurationPart {
        public Kick kick;

        public class Kick extends ConfigurationPart {
            public Component authenticationServersDown = Component.translatable("multiplayer.disconnect.authservers_down");
            public Component connectionThrottle = Component.text("Connection throttled! Please wait before reconnecting.");
            public Component flyingPlayer = Component.translatable("multiplayer.disconnect.flying");
            public Component flyingVehicle = Component.translatable("multiplayer.disconnect.flying");
        }

        public Component noPermission = Component.text("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", NamedTextColor.RED);
        public boolean useDisplayNameInQuitMessage = false;
    }

    public Spark spark;

    public class Spark extends ConfigurationPart {
        public boolean enabled = true;
        public boolean enableImmediately = false;
    }

    @Deprecated(forRemoval = true)
    public Timings timings;

    @Deprecated(forRemoval = true)
    public class Timings extends ConfigurationPart {
        public boolean enabled = false;
        public boolean verbose = true;
        public String url = "https://timings.aikar.co/";
        public boolean serverNamePrivacy = false;
        public List<String> hiddenConfigEntries = List.of(
            "database",
            "proxies.velocity.secret"
        );
        public int historyInterval = 300;
        public int historyLength = 3600;
        public String serverName = "Unknown Server";

        @PostProcess
        private void postProcess() {
            MinecraftTimings.processConfig(this);
        }
    }

    public Proxies proxies;

    public class Proxies extends ConfigurationPart {
        public BungeeCord bungeeCord;

        public class BungeeCord extends ConfigurationPart {
            public boolean onlineMode = true;
        }

        public Velocity velocity;

        public class Velocity extends ConfigurationPart {
            public boolean enabled = false;
            public boolean onlineMode = true;
            public String secret = "";

            @PostProcess
            private void postProcess() {
                if (this.enabled && this.secret.isEmpty()) {
                    LOGGER.error("Velocity is enabled, but no secret key was specified. A secret key is required. Disabling velocity...");
                    this.enabled = false;
                }
            }
        }
        public boolean proxyProtocol = false;
        public boolean isProxyOnlineMode() {
            return org.bukkit.Bukkit.getOnlineMode() || (org.spigotmc.SpigotConfig.bungee && this.bungeeCord.onlineMode) || (this.velocity.enabled && this.velocity.onlineMode);
        }
    }

    public Console console;

    public class Console extends ConfigurationPart {
        public boolean enableBrigadierHighlighting = true;
        public boolean enableBrigadierCompletions = true;
        public boolean hasAllPermissions = false;
    }

    public Watchdog watchdog;

    public class Watchdog extends ConfigurationPart {
        public int earlyWarningEvery = 5000;
        public int earlyWarningDelay = 10000;
    }

    public SpamLimiter spamLimiter;

    public class SpamLimiter extends ConfigurationPart {
        public int tabSpamIncrement = 1;
        public int tabSpamLimit = 500;
        public int recipeSpamIncrement = 1;
        public int recipeSpamLimit = 20;
        public int incomingPacketThreshold = 300;
    }

    public ChunkLoading chunkLoading;

    public class ChunkLoading extends ConfigurationPart {
        public int minLoadRadius = 2;
        public int maxConcurrentSends = 2;
        public boolean autoconfigSendDistance = true;
        public double targetPlayerChunkSendRate = 100.0;
        public double globalMaxChunkSendRate = -1.0;
        public boolean enableFrustumPriority = false;
        public double globalMaxChunkLoadRate = -1.0;
        public double playerMaxConcurrentLoads = 20.0;
        public double globalMaxConcurrentLoads = 500.0;
        public double playerMaxChunkLoadRate = -1.0;
    }

    public UnsupportedSettings unsupportedSettings;

    public class UnsupportedSettings extends ConfigurationPart {
        @Comment("This setting controls if the broken behavior of disarmed tripwires not breaking should be allowed. This also allows for dupes")
        public boolean allowTripwireDisarmingExploits = false;
        @Comment("This setting allows for exploits related to end portals, for example sand duping")
        public boolean allowUnsafeEndPortalTeleportation = false;
        @Comment("This setting controls if players should be able to break bedrock, end portals and other intended to be permanent blocks.")
        public boolean allowPermanentBlockBreakExploits = false;
        @Comment("This setting controls if player should be able to use TNT duplication, but this also allows duplicating carpet, rails and potentially other items")
        public boolean allowPistonDuplication = false;
        public boolean performUsernameValidation = true;
        @Comment("This setting controls if players should be able to create headless pistons.")
        public boolean allowHeadlessPistons = false;
        @Comment("This setting controls if the vanilla damage tick should be skipped if damage was blocked via a shield.")
        public boolean skipVanillaDamageTickWhenShieldBlocked = false;
        @Comment("This setting controls what compression format is used for region files.")
        public CompressionFormat compressionFormat = CompressionFormat.ZLIB;

        public enum CompressionFormat {
            GZIP,
            ZLIB,
            NONE
        }
    }

    public Commands commands;

    public class Commands extends ConfigurationPart {
        public boolean suggestPlayerNamesWhenNullTabCompletions = true;
        public boolean fixTargetSelectorTagCompletion = true;
        public boolean timeCommandAffectsAllWorlds = false;
    }

    public Logging logging;

    public class Logging extends ConfigurationPart {
        public boolean deobfuscateStacktraces = true;
    }

    public Scoreboards scoreboards;

    public class Scoreboards extends ConfigurationPart {
        public boolean trackPluginScoreboards = false;
        public boolean saveEmptyScoreboardTeams = true;
    }

    @SuppressWarnings("unused") // used in postProcess
    public ChunkSystem chunkSystem;

    public class ChunkSystem extends ConfigurationPart {

        public int ioThreads = -1;
        public int workerThreads = -1;
        public String genParallelism = "default";

        @PostProcess
        private void postProcess() {
            //io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler.init(this);
        }
    }

    public ItemValidation itemValidation;

    public class ItemValidation extends ConfigurationPart {
        public int displayName = 8192;
        public int loreLine = 8192;
        public Book book;

        public class Book extends ConfigurationPart {
            public int title = 8192;
            public int author = 8192;
            public int page = 16384;
        }

        public BookSize bookSize;

        public class BookSize extends ConfigurationPart {
            public IntOr.Disabled pageMax = new IntOr.Disabled(OptionalInt.of(2560)); // TODO this appears to be a duplicate setting with one above
            public double totalMultiplier = 0.98D; // TODO this should probably be merged into the above inner class
        }
        public boolean resolveSelectorsInBooks = false;
    }

    public PacketLimiter packetLimiter;

    public class PacketLimiter extends ConfigurationPart {
        public Component kickMessage = Component.translatable("disconnect.exceeded_packet_rate", NamedTextColor.RED);
        public PacketLimit allPackets = new PacketLimit(7.0, 500.0, PacketLimit.ViolateAction.KICK);
        public Map<Class<? extends Packet<?>>, PacketLimit> overrides = Map.of(ServerboundPlaceRecipePacket.class, new PacketLimit(4.0, 5.0, PacketLimit.ViolateAction.DROP));

        @ConfigSerializable
        public record PacketLimit(@Required double interval, @Required double maxPacketRate, ViolateAction action) {
            public PacketLimit(final double interval, final double maxPacketRate, final @Nullable ViolateAction action) {
                this.interval = interval;
                this.maxPacketRate = maxPacketRate;
                this.action = Objects.requireNonNullElse(action, ViolateAction.KICK);
            }

            public boolean isEnabled() {
                return this.interval > 0.0 && this.maxPacketRate > 0.0;
            }

            public enum ViolateAction {
                KICK,
                DROP;
            }
        }
    }

    public Collisions collisions;

    public class Collisions extends ConfigurationPart {
        public boolean enablePlayerCollisions = true;
        public boolean sendFullPosForHardCollidingEntities = true;
    }

    public PlayerAutoSave playerAutoSave;


    public class PlayerAutoSave extends ConfigurationPart {
        public int rate = -1;
        private int maxPerTick = -1;
        public int maxPerTick() {
            if (this.maxPerTick < 0) {
                return (this.rate == 1 || this.rate > 100) ? 10 : 20;
            }
            return this.maxPerTick;
        }
    }

    public Misc misc;

    public class Misc extends ConfigurationPart {

        @SuppressWarnings("unused") // used in postProcess
        public ChatThreads chatThreads;
        public class ChatThreads extends ConfigurationPart {
            private int chatExecutorCoreSize = -1;
            private int chatExecutorMaxSize = -1;

            @PostProcess
            private void postProcess() {
                // TODO: fill in separate patch
            }
        }
        public int maxJoinsPerTick = 5;
        public boolean fixEntityPositionDesync = true;
        public boolean loadPermissionsYmlBeforePlugins = true;
        @Constraints.Min(4)
        public int regionFileCacheSize = 256;
        @Comment("See https://luckformula.emc.gs")
        public boolean useAlternativeLuckFormula = false;
        public boolean useDimensionTypeForCustomSpawners = false;
        public boolean strictAdvancementDimensionCheck = false;
        public IntOr.Default compressionLevel = IntOr.Default.USE_DEFAULT;
        @Comment("Defines the leniency distance added on the server to the interaction range of a player when validating interact packets.")
        public DoubleOr.Default clientInteractionLeniencyDistance = DoubleOr.Default.USE_DEFAULT;
    }

    public BlockUpdates blockUpdates;

    public class BlockUpdates extends ConfigurationPart {
        public boolean disableNoteblockUpdates = false;
        public boolean disableTripwireUpdates = false;
        public boolean disableChorusPlantUpdates = false;
        public boolean disableMushroomBlockUpdates = false;
    }
}