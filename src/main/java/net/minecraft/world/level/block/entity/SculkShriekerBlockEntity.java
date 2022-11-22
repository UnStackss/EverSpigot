package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.OptionalInt;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.SpawnUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.monster.warden.WardenSpawnTracker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkShriekerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class SculkShriekerBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WARNING_SOUND_RADIUS = 10;
    private static final int WARDEN_SPAWN_ATTEMPTS = 20;
    private static final int WARDEN_SPAWN_RANGE_XZ = 5;
    private static final int WARDEN_SPAWN_RANGE_Y = 6;
    private static final int DARKNESS_RADIUS = 40;
    private static final int SHRIEKING_TICKS = 90;
    private static final Int2ObjectMap<SoundEvent> SOUND_BY_LEVEL = Util.make(new Int2ObjectOpenHashMap<>(), warningSounds -> {
        warningSounds.put(1, SoundEvents.WARDEN_NEARBY_CLOSE);
        warningSounds.put(2, SoundEvents.WARDEN_NEARBY_CLOSER);
        warningSounds.put(3, SoundEvents.WARDEN_NEARBY_CLOSEST);
        warningSounds.put(4, SoundEvents.WARDEN_LISTENING_ANGRY);
    });
    public int warningLevel;
    private final VibrationSystem.User vibrationUser = new SculkShriekerBlockEntity.VibrationUser();
    private VibrationSystem.Data vibrationData = new VibrationSystem.Data();
    private final VibrationSystem.Listener vibrationListener = new VibrationSystem.Listener(this);

    public SculkShriekerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.SCULK_SHRIEKER, pos, state);
    }

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("warning_level", 99)) {
            this.warningLevel = nbt.getInt("warning_level");
        }

        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        if (nbt.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryOps, nbt.getCompound("listener"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse vibration listener for Sculk Shrieker: '{}'", string))
                .ifPresent(vibrationListener -> this.vibrationData = vibrationListener);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("warning_level", this.warningLevel);
        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        VibrationSystem.Data.CODEC
            .encodeStart(registryOps, this.vibrationData)
            .resultOrPartial(string -> LOGGER.error("Failed to encode vibration listener for Sculk Shrieker: '{}'", string))
            .ifPresent(tag -> nbt.put("listener", tag));
    }

    @Nullable
    public static ServerPlayer tryGetPlayer(@Nullable Entity entity) {
        // Paper start - check global player list where appropriate; ensure level is the same for sculk events
        final ServerPlayer player = tryGetPlayer0(entity);
        return player != null && player.level() == entity.level() ? player : null;
    }
    @Nullable
    private static ServerPlayer tryGetPlayer0(@Nullable Entity entity) {
        // Paper end - check global player list where appropriate
        if (entity instanceof ServerPlayer) {
            return (ServerPlayer)entity;
        } else {
            if (entity != null) {
                LivingEntity serverPlayer4 = entity.getControllingPassenger();
                if (serverPlayer4 instanceof ServerPlayer) {
                    return (ServerPlayer)serverPlayer4;
                }
            }

            if (entity instanceof Projectile projectile) {
                Entity var3 = projectile.getOwner();
                if (var3 instanceof ServerPlayer) {
                    return (ServerPlayer)var3;
                }
            }

            if (entity instanceof ItemEntity itemEntity) {
                Entity var9 = itemEntity.getOwner();
                if (var9 instanceof ServerPlayer) {
                    return (ServerPlayer)var9;
                }
            }

            return null;
        }
    }

    public void tryShriek(ServerLevel world, @Nullable ServerPlayer player) {
        if (player != null) {
            BlockState blockState = this.getBlockState();
            if (!blockState.getValue(SculkShriekerBlock.SHRIEKING)) {
                this.warningLevel = 0;
                if (!this.canRespond(world) || this.tryToWarn(world, player)) {
                    this.shriek(world, player);
                }
            }
        }
    }

    private boolean tryToWarn(ServerLevel world, ServerPlayer player) {
        OptionalInt optionalInt = WardenSpawnTracker.tryWarn(world, this.getBlockPos(), player);
        optionalInt.ifPresent(warningLevel -> this.warningLevel = warningLevel);
        return optionalInt.isPresent();
    }

    private void shriek(ServerLevel world, @Nullable Entity entity) {
        BlockPos blockPos = this.getBlockPos();
        BlockState blockState = this.getBlockState();
        world.setBlock(blockPos, blockState.setValue(SculkShriekerBlock.SHRIEKING, Boolean.valueOf(true)), 2);
        world.scheduleTick(blockPos, blockState.getBlock(), 90);
        world.levelEvent(3007, blockPos, 0);
        world.gameEvent(GameEvent.SHRIEK, blockPos, GameEvent.Context.of(entity));
    }

    private boolean canRespond(ServerLevel world) {
        return this.getBlockState().getValue(SculkShriekerBlock.CAN_SUMMON)
            && world.getDifficulty() != Difficulty.PEACEFUL
            && world.getGameRules().getBoolean(GameRules.RULE_DO_WARDEN_SPAWNING);
    }

    public void tryRespond(ServerLevel world) {
        if (this.canRespond(world) && this.warningLevel > 0) {
            if (!this.trySummonWarden(world)) {
                this.playWardenReplySound(world);
            }

            Warden.applyDarknessAround(world, Vec3.atCenterOf(this.getBlockPos()), null, 40);
        }
    }

    private void playWardenReplySound(Level world) {
        SoundEvent soundEvent = SOUND_BY_LEVEL.get(this.warningLevel);
        if (soundEvent != null) {
            BlockPos blockPos = this.getBlockPos();
            int i = blockPos.getX() + Mth.randomBetweenInclusive(world.random, -10, 10);
            int j = blockPos.getY() + Mth.randomBetweenInclusive(world.random, -10, 10);
            int k = blockPos.getZ() + Mth.randomBetweenInclusive(world.random, -10, 10);
            world.playSound(null, (double)i, (double)j, (double)k, soundEvent, SoundSource.HOSTILE, 5.0F, 1.0F);
        }
    }

    private boolean trySummonWarden(ServerLevel world) {
        return this.warningLevel >= 4
            && SpawnUtil.trySpawnMob(EntityType.WARDEN, MobSpawnType.TRIGGERED, world, this.getBlockPos(), 20, 5, 6, SpawnUtil.Strategy.ON_TOP_OF_COLLIDER, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL, null) // Paper - Entity#getEntitySpawnReason
                .isPresent();
    }

    @Override
    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    class VibrationUser implements VibrationSystem.User {
        private static final int LISTENER_RADIUS = 8;
        private final PositionSource positionSource = new BlockPositionSource(SculkShriekerBlockEntity.this.worldPosition);

        public VibrationUser() {
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.SHRIEKER_CAN_LISTEN;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel world, BlockPos pos, Holder<GameEvent> event, GameEvent.Context emitter) {
            return !SculkShriekerBlockEntity.this.getBlockState().getValue(SculkShriekerBlock.SHRIEKING)
                && SculkShriekerBlockEntity.tryGetPlayer(emitter.sourceEntity()) != null;
        }

        @Override
        public void onReceiveVibration(
            ServerLevel world, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance
        ) {
            SculkShriekerBlockEntity.this.tryShriek(world, SculkShriekerBlockEntity.tryGetPlayer(entity != null ? entity : sourceEntity));
        }

        @Override
        public void onDataChanged() {
            SculkShriekerBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
