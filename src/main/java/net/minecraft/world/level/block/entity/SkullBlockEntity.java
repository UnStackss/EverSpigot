package net.minecraft.world.level.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class SkullBlockEntity extends BlockEntity {
    private static final String TAG_PROFILE = "profile";
    private static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    private static final String TAG_CUSTOM_NAME = "custom_name";
    private static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCacheByName;
    @Nullable
    private static LoadingCache<com.mojang.datafixers.util.Pair<java.util.UUID,  @org.jetbrains.annotations.Nullable GameProfile>, CompletableFuture<Optional<GameProfile>>> profileCacheById; // Paper - player profile events
    public static final Executor CHECKED_MAIN_THREAD_EXECUTOR = runnable -> {
        Executor executor = mainThreadExecutor;
        if (executor != null) {
            executor.execute(runnable);
        }
    };
    @Nullable
    public ResolvableProfile owner;
    @Nullable
    public ResourceLocation noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;
    @Nullable
    private Component customName;

    public SkullBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.SKULL, pos, state);
    }

    public static void setup(Services apiServices, Executor executor) {
        mainThreadExecutor = executor;
        final BooleanSupplier booleanSupplier = () -> profileCacheById == null;
        profileCacheByName = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
                @Override
                public CompletableFuture<Optional<GameProfile>> load(String string) {
                    return SkullBlockEntity.fetchProfileByName(string, apiServices);
                }
            });
        profileCacheById = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10L))
            .maximumSize(256L)
            .build(new CacheLoader<>() { // Paper - player profile events
                @Override
                public CompletableFuture<Optional<GameProfile>> load(com.mojang.datafixers.util.Pair<java.util.UUID, @org.jetbrains.annotations.Nullable GameProfile> uUID) { // Paper - player profile events
                    return SkullBlockEntity.fetchProfileById(uUID, apiServices, booleanSupplier);
                }
            });
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileByName(String name, Services apiServices) {
        return apiServices.profileCache()
            .getAsync(name)
            .thenCompose(
                optional -> {
                    LoadingCache<com.mojang.datafixers.util.Pair<java.util.UUID, @org.jetbrains.annotations.Nullable GameProfile>, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById; // Paper - player profile events
                    return loadingCache != null && !optional.isEmpty()
                        ? loadingCache.getUnchecked(new com.mojang.datafixers.util.Pair<>(optional.get().getId(), optional.get())).thenApply(optional2 -> optional2.or(() -> optional)) // Paper - player profile events
                        : CompletableFuture.completedFuture(Optional.empty());
                }
            );
    }

    static CompletableFuture<Optional<GameProfile>> fetchProfileById(com.mojang.datafixers.util.Pair<java.util.UUID, @org.jetbrains.annotations.Nullable GameProfile> pair, Services apiServices, BooleanSupplier booleanSupplier) { // Paper
        return CompletableFuture.supplyAsync(() -> {
            if (booleanSupplier.getAsBoolean()) {
                return Optional.empty();
            } else {
                // Paper start - fill player profile events
                if (apiServices.sessionService() instanceof com.destroystokyo.paper.profile.PaperMinecraftSessionService paperService) {
                    final GameProfile profile = pair.getSecond() != null ? pair.getSecond() : new com.mojang.authlib.GameProfile(pair.getFirst(), "");
                    return Optional.ofNullable(paperService.fetchProfile(profile, true)).map(ProfileResult::profile);
                }
                ProfileResult profileResult = apiServices.sessionService().fetchProfile(pair.getFirst(), true);
                // Paper end - fill player profile events
                return Optional.ofNullable(profileResult).map(ProfileResult::profile);
            }
        }, Util.PROFILE_EXECUTOR); // Paper - don't submit BLOCKING PROFILE LOOKUPS to the world gen thread
    }

    public static void clear() {
        mainThreadExecutor = null;
        profileCacheByName = null;
        profileCacheById = null;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if (this.owner != null) {
            nbt.put("profile", ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, this.owner).getOrThrow());
        }

        if (this.noteBlockSound != null) {
            nbt.putString("note_block_sound", this.noteBlockSound.toString());
        }

        if (this.customName != null) {
            nbt.putString("custom_name", Component.Serializer.toJson(this.customName, registryLookup));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("profile")) {
            ResolvableProfile.CODEC
                .parse(NbtOps.INSTANCE, nbt.get("profile"))
                .resultOrPartial(string -> LOGGER.error("Failed to load profile from player head: {}", string))
                .ifPresent(this::setOwner);
        }

        if (nbt.contains("note_block_sound", 8)) {
            this.noteBlockSound = ResourceLocation.tryParse(nbt.getString("note_block_sound"));
        }

        if (nbt.contains("custom_name", 8)) {
            this.customName = parseCustomNameSafe(nbt.getString("custom_name"), registryLookup);
        } else {
            this.customName = null;
        }
    }

    public static void animation(Level world, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
        if (state.hasProperty(SkullBlock.POWERED) && state.getValue(SkullBlock.POWERED)) {
            blockEntity.isAnimating = true;
            blockEntity.animationTickCount++;
        } else {
            blockEntity.isAnimating = false;
        }
    }

    public float getAnimation(float tickDelta) {
        return this.isAnimating ? (float)this.animationTickCount + tickDelta : (float)this.animationTickCount;
    }

    @Nullable
    public ResolvableProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveCustomOnly(registryLookup);
    }

    public void setOwner(@Nullable ResolvableProfile profile) {
        synchronized (this) {
            this.owner = profile;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !this.owner.isResolved()) {
            this.owner.resolve().thenAcceptAsync(owner -> {
                this.owner = owner;
                this.setChanged();
            }, CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String name) {
        LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheByName;
        return loadingCache != null && StringUtil.isValidPlayerName(name)
            ? loadingCache.getUnchecked(name)
            : CompletableFuture.completedFuture(Optional.empty());
    }

    // Paper start - player profile events
    public static CompletableFuture<Optional<GameProfile>> fetchGameProfile(UUID uuid, @Nullable String name) {
        LoadingCache<com.mojang.datafixers.util.Pair<java.util.UUID,  @org.jetbrains.annotations.Nullable GameProfile>, CompletableFuture<Optional<GameProfile>>> loadingCache = profileCacheById;
        return loadingCache != null ? loadingCache.getUnchecked(new com.mojang.datafixers.util.Pair<>(uuid, name != null ? new com.mojang.authlib.GameProfile(uuid, name) : null)) : CompletableFuture.completedFuture(Optional.empty());
        // Paper end - player profile events
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.setOwner(components.get(DataComponents.PROFILE));
        this.noteBlockSound = components.get(DataComponents.NOTE_BLOCK_SOUND);
        this.customName = components.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        componentMapBuilder.set(DataComponents.PROFILE, this.owner);
        componentMapBuilder.set(DataComponents.NOTE_BLOCK_SOUND, this.noteBlockSound);
        componentMapBuilder.set(DataComponents.CUSTOM_NAME, this.customName);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag nbt) {
        super.removeComponentsFromTag(nbt);
        nbt.remove("profile");
        nbt.remove("note_block_sound");
        nbt.remove("custom_name");
    }
}
