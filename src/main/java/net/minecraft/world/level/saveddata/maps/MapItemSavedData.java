package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

// CraftBukkit start
import io.papermc.paper.adventure.PaperAdventure; // Paper
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.map.CraftMapCursor;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.bukkit.craftbukkit.util.CraftChatMessage;
// CraftBukkit end

public class MapItemSavedData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public int centerX;
    public int centerZ;
    public ResourceKey<Level> dimension;
    public boolean trackingPosition;
    public boolean unlimitedTracking;
    public byte scale;
    public byte[] colors = new byte[16384];
    public boolean locked;
    public final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    public final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    public final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();
    private int trackedDecorationCount;

    // CraftBukkit start
    public final CraftMapView mapView;
    private CraftServer server;
    public UUID uniqueId = null;
    public MapId id;
    // CraftBukkit end

    public static SavedData.Factory<MapItemSavedData> factory() {
        return new SavedData.Factory<>(() -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, MapItemSavedData::load, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(int centerX, int centerZ, byte scale, boolean showDecorations, boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        this.scale = scale;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.dimension = dimension;
        this.trackingPosition = showDecorations;
        this.unlimitedTracking = unlimitedTracking;
        this.locked = locked;
        this.setDirty();
        // CraftBukkit start
        this.mapView = new CraftMapView(this);
        this.server = (CraftServer) org.bukkit.Bukkit.getServer();
        // CraftBukkit end
    }

    public static MapItemSavedData createFresh(double centerX, double centerZ, byte scale, boolean showDecorations, boolean unlimitedTracking, ResourceKey<Level> dimension) {
        int i = 128 * (1 << scale);
        int j = Mth.floor((centerX + 64.0D) / (double) i);
        int k = Mth.floor((centerZ + 64.0D) / (double) i);
        int l = j * i + i / 2 - 64;
        int i1 = k * i + i / 2 - 64;

        return new MapItemSavedData(l, i1, scale, showDecorations, unlimitedTracking, false, dimension);
    }

    public static MapItemSavedData createForClient(byte scale, boolean locked, ResourceKey<Level> dimension) {
        return new MapItemSavedData(0, 0, scale, false, false, locked, dimension);
    }

    public static MapItemSavedData load(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        // Paper start - fix "Not a string" spam
        Tag dimension = nbt.get("dimension");
        if (dimension instanceof NumericTag && ((NumericTag) dimension).getAsInt() >= CraftWorld.CUSTOM_DIMENSION_OFFSET) {
            long least = nbt.getLong("UUIDLeast");
            long most = nbt.getLong("UUIDMost");

            if (least != 0L && most != 0L) {
                UUID uuid = new UUID(most, least);
                CraftWorld world = (CraftWorld) Bukkit.getWorld(uuid);
                if (world != null) {
                    dimension = StringTag.valueOf("minecraft:" + world.getName().toLowerCase(java.util.Locale.ENGLISH));
                } else {
                    dimension = StringTag.valueOf("bukkit:_invalidworld_");
                }
            } else {
                dimension = StringTag.valueOf("bukkit:_invalidworld_");
            }
        }
        DataResult<ResourceKey<Level>> dataresult = DimensionType.parseLegacy(new Dynamic(NbtOps.INSTANCE, dimension)); // CraftBukkit - decompile error
        // Paper end - fix "Not a string" spam
        Logger logger = MapItemSavedData.LOGGER;

        Objects.requireNonNull(logger);
        // CraftBukkit start
        ResourceKey<Level> resourcekey = (ResourceKey) dataresult.resultOrPartial(logger::error).orElseGet(() -> {
            long least = nbt.getLong("UUIDLeast");
            long most = nbt.getLong("UUIDMost");

            if (least != 0L && most != 0L) {
                UUID uniqueId = new UUID(most, least);

                CraftWorld world = (CraftWorld) Bukkit.getWorld(uniqueId);
                // Check if the stored world details are correct.
                if (world == null) {
                    /* All Maps which do not have their valid world loaded are set to a dimension which hopefully won't be reached.
                       This is to prevent them being corrupted with the wrong map data. */
                    // PAIL: Use Vanilla exception handling for now
                } else {
                    return world.getHandle().dimension();
                }
            }
            throw new IllegalArgumentException("Invalid map dimension: " + String.valueOf(nbt.get("dimension")));
            // CraftBukkit end
        });
        int i = nbt.getInt("xCenter");
        int j = nbt.getInt("zCenter");
        byte b0 = (byte) Mth.clamp(nbt.getByte("scale"), 0, 4);
        boolean flag = !nbt.contains("trackingPosition", 1) || nbt.getBoolean("trackingPosition");
        boolean flag1 = nbt.getBoolean("unlimitedTracking");
        boolean flag2 = nbt.getBoolean("locked");
        MapItemSavedData worldmap = new MapItemSavedData(i, j, b0, flag, flag1, flag2, resourcekey);
        byte[] abyte = nbt.getByteArray("colors");

        if (abyte.length == 16384) {
            worldmap.colors = abyte;
        }

        RegistryOps<Tag> registryops = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        List<MapBanner> list = (List) MapBanner.LIST_CODEC.parse(registryops, nbt.get("banners")).resultOrPartial((s) -> {
            MapItemSavedData.LOGGER.warn("Failed to parse map banner: '{}'", s);
        }).orElse(List.of());
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            MapBanner mapiconbanner = (MapBanner) iterator.next();

            worldmap.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
            // CraftBukkit - decompile error
            worldmap.addDecoration(mapiconbanner.getDecoration(), (LevelAccessor) null, mapiconbanner.getId(), (double) mapiconbanner.pos().getX(), (double) mapiconbanner.pos().getZ(), 180.0D, (Component) mapiconbanner.name().orElse(null));
        }

        ListTag nbttaglist = nbt.getList("frames", 10);

        for (int k = 0; k < nbttaglist.size(); ++k) {
            MapFrame worldmapframe = MapFrame.load(nbttaglist.getCompound(k));

            if (worldmapframe != null) {
                worldmap.frameMarkers.put(worldmapframe.getId(), worldmapframe);
                worldmap.addDecoration(MapDecorationTypes.FRAME, (LevelAccessor) null, MapItemSavedData.getFrameKey(worldmapframe.getEntityId()), (double) worldmapframe.getPos().getX(), (double) worldmapframe.getPos().getZ(), (double) worldmapframe.getRotation(), (Component) null);
            }
        }

        return worldmap;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        DataResult<Tag> dataresult = ResourceLocation.CODEC.encodeStart(NbtOps.INSTANCE, this.dimension.location()); // CraftBukkit - decompile error
        Logger logger = MapItemSavedData.LOGGER;

        Objects.requireNonNull(logger);
        dataresult.resultOrPartial(logger::error).ifPresent((nbtbase) -> {
            nbt.put("dimension", nbtbase);
        });
        // CraftBukkit start
        if (true) {
            if (this.uniqueId == null) {
                for (org.bukkit.World world : this.server.getWorlds()) {
                    CraftWorld cWorld = (CraftWorld) world;
                    if (cWorld.getHandle().dimension() == this.dimension) {
                        this.uniqueId = cWorld.getUID();
                        break;
                    }
                }
            }
            /* Perform a second check to see if a matching world was found, this is a necessary
               change incase Maps are forcefully unlinked from a World and lack a UID.*/
            if (this.uniqueId != null) {
                nbt.putLong("UUIDLeast", this.uniqueId.getLeastSignificantBits());
                nbt.putLong("UUIDMost", this.uniqueId.getMostSignificantBits());
            }
        }
        // CraftBukkit end
        nbt.putInt("xCenter", this.centerX);
        nbt.putInt("zCenter", this.centerZ);
        nbt.putByte("scale", this.scale);
        nbt.putByteArray("colors", this.colors);
        nbt.putBoolean("trackingPosition", this.trackingPosition);
        nbt.putBoolean("unlimitedTracking", this.unlimitedTracking);
        nbt.putBoolean("locked", this.locked);
        RegistryOps<Tag> registryops = registryLookup.createSerializationContext(NbtOps.INSTANCE);

        nbt.put("banners", (Tag) MapBanner.LIST_CODEC.encodeStart(registryops, List.copyOf(this.bannerMarkers.values())).getOrThrow());
        ListTag nbttaglist = new ListTag();
        Iterator iterator = this.frameMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapFrame worldmapframe = (MapFrame) iterator.next();

            nbttaglist.add(worldmapframe.save());
        }

        nbt.put("frames", nbttaglist);
        return nbt;
    }

    public MapItemSavedData locked() {
        MapItemSavedData worldmap = new MapItemSavedData(this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension);

        worldmap.bannerMarkers.putAll(this.bannerMarkers);
        worldmap.decorations.putAll(this.decorations);
        worldmap.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, worldmap.colors, 0, this.colors.length);
        worldmap.setDirty();
        return worldmap;
    }

    public MapItemSavedData scaled() {
        return MapItemSavedData.createFresh((double) this.centerX, (double) this.centerZ, (byte) Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack stack) {
        MapId mapid = (MapId) stack.get(DataComponents.MAP_ID);

        return (itemstack1) -> {
            return itemstack1 == stack ? true : itemstack1.is(stack.getItem()) && Objects.equals(mapid, itemstack1.get(DataComponents.MAP_ID));
        };
    }

    public void tickCarriedBy(Player player, ItemStack stack) {
        if (!this.carriedByPlayers.containsKey(player)) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = new MapItemSavedData.HoldingPlayer(player);

            this.carriedByPlayers.put(player, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        Predicate<ItemStack> predicate = MapItemSavedData.mapMatcher(stack);

        if (!player.getInventory().contains(predicate)) {
            this.removeDecoration(player.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); ++i) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker1 = (MapItemSavedData.HoldingPlayer) this.carriedBy.get(i);
            String s = worldmap_worldmaphumantracker1.player.getName().getString();

            if (!worldmap_worldmaphumantracker1.player.isRemoved() && (worldmap_worldmaphumantracker1.player.getInventory().contains(predicate) || stack.isFramed())) {
                if (!stack.isFramed() && worldmap_worldmaphumantracker1.player.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(MapDecorationTypes.PLAYER, worldmap_worldmaphumantracker1.player.level(), s, worldmap_worldmaphumantracker1.player.getX(), worldmap_worldmaphumantracker1.player.getZ(), (double) worldmap_worldmaphumantracker1.player.getYRot(), (Component) null);
                }
            } else {
                this.carriedByPlayers.remove(worldmap_worldmaphumantracker1.player);
                this.carriedBy.remove(worldmap_worldmaphumantracker1);
                this.removeDecoration(s);
            }
        }

        if (stack.isFramed() && this.trackingPosition) {
            ItemFrame entityitemframe = stack.getFrame();
            BlockPos blockposition = entityitemframe.getPos();
            MapFrame worldmapframe = (MapFrame) this.frameMarkers.get(MapFrame.frameId(blockposition));

            if (worldmapframe != null && entityitemframe.getId() != worldmapframe.getEntityId() && this.frameMarkers.containsKey(worldmapframe.getId())) {
                this.removeDecoration(MapItemSavedData.getFrameKey(worldmapframe.getEntityId()));
            }

            MapFrame worldmapframe1 = new MapFrame(blockposition, entityitemframe.getDirection().get2DDataValue() * 90, entityitemframe.getId());

            if (this.decorations.size() < player.level().paperConfig().maps.itemFrameCursorLimit) { // Paper - Limit item frame cursors on maps
            this.addDecoration(MapDecorationTypes.FRAME, player.level(), MapItemSavedData.getFrameKey(entityitemframe.getId()), (double) blockposition.getX(), (double) blockposition.getZ(), (double) (entityitemframe.getDirection().get2DDataValue() * 90), (Component) null);
            this.frameMarkers.put(worldmapframe1.getId(), worldmapframe1);
            } // Paper - Limit item frame cursors on maps
        }

        MapDecorations mapdecorations = (MapDecorations) stack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);

        if (!this.decorations.keySet().containsAll(mapdecorations.decorations().keySet())) {
            mapdecorations.decorations().forEach((s1, mapdecorations_a) -> {
                if (!this.decorations.containsKey(s1)) {
                    this.addDecoration(mapdecorations_a.type(), player.level(), s1, mapdecorations_a.x(), mapdecorations_a.z(), (double) mapdecorations_a.rotation(), (Component) null);
                }

            });
        }

    }

    private void removeDecoration(String id) {
        MapDecoration mapicon = (MapDecoration) this.decorations.remove(id);

        if (mapicon != null && ((MapDecorationType) mapicon.type().value()).trackCount()) {
            --this.trackedDecorationCount;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack stack, BlockPos pos, String id, Holder<MapDecorationType> decorationType) {
        MapDecorations.Entry mapdecorations_a = new MapDecorations.Entry(decorationType, (double) pos.getX(), (double) pos.getZ(), 180.0F);

        stack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, (mapdecorations) -> {
            return mapdecorations.withDecoration(id, mapdecorations_a);
        });
        if (((MapDecorationType) decorationType.value()).hasMapColor()) {
            stack.set(DataComponents.MAP_COLOR, new MapItemColor(((MapDecorationType) decorationType.value()).mapColor()));
        }

    }

    private void addDecoration(Holder<MapDecorationType> type, @Nullable LevelAccessor world, String key, double x, double z, double rotation, @Nullable Component text) {
        int i = 1 << this.scale;
        float f = (float) (x - (double) this.centerX) / (float) i;
        float f1 = (float) (z - (double) this.centerZ) / (float) i;
        byte b0 = (byte) ((int) ((double) (f * 2.0F) + 0.5D));
        byte b1 = (byte) ((int) ((double) (f1 * 2.0F) + 0.5D));
        boolean flag = true;
        byte b2;

        if (f >= -63.0F && f1 >= -63.0F && f <= 63.0F && f1 <= 63.0F) {
            rotation += rotation < 0.0D ? -8.0D : 8.0D;
            b2 = (byte) ((int) (rotation * 16.0D / 360.0D));
            if (this.dimension == Level.NETHER && world != null) {
                int j = (int) (world.getLevelData().getDayTime() / 10L);

                b2 = (byte) (j * j * 34187121 + j * 121 >> 15 & 15);
            }
        } else {
            if (!type.is(MapDecorationTypes.PLAYER)) {
                this.removeDecoration(key);
                return;
            }

            boolean flag1 = true;

            if (Math.abs(f) < 320.0F && Math.abs(f1) < 320.0F) {
                type = MapDecorationTypes.PLAYER_OFF_MAP;
            } else {
                if (!this.unlimitedTracking) {
                    this.removeDecoration(key);
                    return;
                }

                type = MapDecorationTypes.PLAYER_OFF_LIMITS;
            }

            b2 = 0;
            if (f <= -63.0F) {
                b0 = Byte.MIN_VALUE;
            }

            if (f1 <= -63.0F) {
                b1 = Byte.MIN_VALUE;
            }

            if (f >= 63.0F) {
                b0 = 127;
            }

            if (f1 >= 63.0F) {
                b1 = 127;
            }
        }

        MapDecoration mapicon = new MapDecoration(type, b0, b1, b2, Optional.ofNullable(text));
        MapDecoration mapicon1 = (MapDecoration) this.decorations.put(key, mapicon);

        if (!mapicon.equals(mapicon1)) {
            if (mapicon1 != null && ((MapDecorationType) mapicon1.type().value()).trackCount()) {
                --this.trackedDecorationCount;
            }

            if (((MapDecorationType) type.value()).trackCount()) {
                ++this.trackedDecorationCount;
            }

            this.setDecorationsDirty();
        }

    }

    @Nullable
    public Packet<?> getUpdatePacket(MapId mapId, Player player) {
        MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(player);

        return worldmap_worldmaphumantracker == null ? null : worldmap_worldmaphumantracker.nextUpdatePacket(mapId);
    }

    public void setColorsDirty(int x, int z) {
        this.setDirty();
        Iterator iterator = this.carriedBy.iterator();

        while (iterator.hasNext()) {
            MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) iterator.next();

            worldmap_worldmaphumantracker.markColorsDirty(x, z);
        }

    }

    public void setDecorationsDirty() {
        this.setDirty();
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player player) {
        MapItemSavedData.HoldingPlayer worldmap_worldmaphumantracker = (MapItemSavedData.HoldingPlayer) this.carriedByPlayers.get(player);

        if (worldmap_worldmaphumantracker == null) {
            worldmap_worldmaphumantracker = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, worldmap_worldmaphumantracker);
            this.carriedBy.add(worldmap_worldmaphumantracker);
        }

        return worldmap_worldmaphumantracker;
    }

    public boolean toggleBanner(LevelAccessor world, BlockPos pos) {
        double d0 = (double) pos.getX() + 0.5D;
        double d1 = (double) pos.getZ() + 0.5D;
        int i = 1 << this.scale;
        double d2 = (d0 - (double) this.centerX) / (double) i;
        double d3 = (d1 - (double) this.centerZ) / (double) i;
        boolean flag = true;

        if (d2 >= -63.0D && d3 >= -63.0D && d2 <= 63.0D && d3 <= 63.0D) {
            MapBanner mapiconbanner = MapBanner.fromWorld(world, pos);

            if (mapiconbanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapiconbanner.getId(), mapiconbanner)) {
                this.removeDecoration(mapiconbanner.getId());
                return true;
            }

            if (!this.isTrackedCountOverLimit(((Level) world).paperConfig().maps.itemFrameCursorLimit)) { // Paper - Limit item frame cursors on maps
                this.bannerMarkers.put(mapiconbanner.getId(), mapiconbanner);
                this.addDecoration(mapiconbanner.getDecoration(), world, mapiconbanner.getId(), d0, d1, 180.0D, (Component) mapiconbanner.name().orElse(null)); // CraftBukkit - decompile error
                return true;
            }
        }

        return false;
    }

    public void checkBanners(BlockGetter world, int x, int z) {
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapiconbanner = (MapBanner) iterator.next();

            if (mapiconbanner.pos().getX() == x && mapiconbanner.pos().getZ() == z) {
                MapBanner mapiconbanner1 = MapBanner.fromWorld(world, mapiconbanner.pos());

                if (!mapiconbanner.equals(mapiconbanner1)) {
                    iterator.remove();
                    this.removeDecoration(mapiconbanner.getId());
                }
            }
        }

    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPos pos, int id) {
        this.removeDecoration(MapItemSavedData.getFrameKey(id));
        this.frameMarkers.remove(MapFrame.frameId(pos));
    }

    public boolean updateColor(int x, int z, byte color) {
        byte b1 = this.colors[x + z * 128];

        if (b1 != color) {
            this.setColor(x, z, color);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int x, int z, byte color) {
        this.colors[x + z * 128] = color;
        this.setColorsDirty(x, z);
    }

    public boolean isExplorationMap() {
        Iterator iterator = this.decorations.values().iterator();

        MapDecoration mapicon;

        do {
            if (!iterator.hasNext()) {
                return false;
            }

            mapicon = (MapDecoration) iterator.next();
        } while (!((MapDecorationType) mapicon.type().value()).explorationMapElement());

        return true;
    }

    public void addClientSideDecorations(List<MapDecoration> decorations) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < decorations.size(); ++i) {
            MapDecoration mapicon = (MapDecoration) decorations.get(i);

            this.decorations.put("icon-" + i, mapicon);
            if (((MapDecorationType) mapicon.type().value()).trackCount()) {
                ++this.trackedDecorationCount;
            }
        }

    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int decorationCount) {
        return this.trackedDecorationCount >= decorationCount;
    }

    private static String getFrameKey(int id) {
        return "frame-" + id;
    }

    public class HoldingPlayer {

        public final Player player;
        private boolean dirtyData = true;
        private int minDirtyX;
        private int minDirtyY;
        private int maxDirtyX = 127;
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        HoldingPlayer(final Player entityhuman) {
            this.player = entityhuman;
        }

        private MapItemSavedData.MapPatch createPatch(byte[] buffer) { // CraftBukkit
            int i = this.minDirtyX;
            int j = this.minDirtyY;
            int k = this.maxDirtyX + 1 - this.minDirtyX;
            int l = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] abyte = new byte[k * l];

            for (int i1 = 0; i1 < k; ++i1) {
                for (int j1 = 0; j1 < l; ++j1) {
                    abyte[i1 + j1 * k] = buffer[i + i1 + (j + j1) * 128]; // CraftBukkit
                }
            }

            return new MapItemSavedData.MapPatch(i, j, k, l, abyte);
        }

        @Nullable
        Packet<?> nextUpdatePacket(MapId mapId) {
            MapItemSavedData.MapPatch worldmap_b;
            org.bukkit.craftbukkit.map.RenderData render = MapItemSavedData.this.mapView.render((org.bukkit.craftbukkit.entity.CraftPlayer) this.player.getBukkitEntity()); // CraftBukkit

            if (this.dirtyData) {
                this.dirtyData = false;
                worldmap_b = this.createPatch(render.buffer); // CraftBukkit
            } else {
                worldmap_b = null;
            }

            Collection collection;

            if ((true || this.dirtyDecorations) && this.tick++ % 5 == 0) { // CraftBukkit - custom maps don't update this yet
                this.dirtyDecorations = false;
                // CraftBukkit start
                java.util.Collection<MapDecoration> icons = new java.util.ArrayList<MapDecoration>();

                for (org.bukkit.map.MapCursor cursor : render.cursors) {
                    if (cursor.isVisible()) {
                        icons.add(new MapDecoration(CraftMapCursor.CraftType.bukkitToMinecraftHolder(cursor.getType()), cursor.getX(), cursor.getY(), cursor.getDirection(), Optional.ofNullable(PaperAdventure.asVanilla(cursor.caption()))));
                    }
                }
                collection = icons;
                // CraftBukkit end
            } else {
                collection = null;
            }

            return collection == null && worldmap_b == null ? null : new ClientboundMapItemDataPacket(mapId, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, worldmap_b);
        }

        void markColorsDirty(int startX, int startZ) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, startX);
                this.minDirtyY = Math.min(this.minDirtyY, startZ);
                this.maxDirtyX = Math.max(this.maxDirtyX, startX);
                this.maxDirtyY = Math.max(this.maxDirtyY, startZ);
            } else {
                this.dirtyData = true;
                this.minDirtyX = startX;
                this.minDirtyY = startZ;
                this.maxDirtyX = startX;
                this.maxDirtyY = startZ;
            }

        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    public static record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {

        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.of(MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read);

        private static void write(ByteBuf buf, Optional<MapItemSavedData.MapPatch> updateData) {
            if (updateData.isPresent()) {
                MapItemSavedData.MapPatch worldmap_b = (MapItemSavedData.MapPatch) updateData.get();

                buf.writeByte(worldmap_b.width);
                buf.writeByte(worldmap_b.height);
                buf.writeByte(worldmap_b.startX);
                buf.writeByte(worldmap_b.startY);
                FriendlyByteBuf.writeByteArray(buf, worldmap_b.mapColors);
            } else {
                buf.writeByte(0);
            }

        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf buf) {
            short short0 = buf.readUnsignedByte();

            if (short0 > 0) {
                short short1 = buf.readUnsignedByte();
                short short2 = buf.readUnsignedByte();
                short short3 = buf.readUnsignedByte();
                byte[] abyte = FriendlyByteBuf.readByteArray(buf);

                return Optional.of(new MapItemSavedData.MapPatch(short2, short3, short0, short1, abyte));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData mapState) {
            for (int i = 0; i < this.width; ++i) {
                for (int j = 0; j < this.height; ++j) {
                    mapState.setColor(this.startX + i, this.startY + j, this.mapColors[i + j * this.width]);
                }
            }

        }
    }
}
