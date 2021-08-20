package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SculkSensorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.slf4j.Logger;

public class SculkSensorBlockEntity extends BlockEntity implements GameEventListener.Provider<VibrationSystem.Listener>, VibrationSystem {
    private static final Logger LOGGER = LogUtils.getLogger();
    private VibrationSystem.Data vibrationData;
    private final VibrationSystem.Listener vibrationListener;
    private final VibrationSystem.User vibrationUser = this.createVibrationUser();
    public int lastVibrationFrequency;
    @Nullable public Integer rangeOverride = null; // Paper - Configurable sculk sensor listener range

    protected SculkSensorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.vibrationData = new VibrationSystem.Data();
        this.vibrationListener = new VibrationSystem.Listener(this);
    }

    public SculkSensorBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntityType.SCULK_SENSOR, pos, state);
    }

    public VibrationSystem.User createVibrationUser() {
        return new SculkSensorBlockEntity.VibrationUser(this.getBlockPos());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.lastVibrationFrequency = nbt.getInt("last_vibration_frequency");
        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        if (nbt.contains("listener", 10)) {
            VibrationSystem.Data.CODEC
                .parse(registryOps, nbt.getCompound("listener"))
                .resultOrPartial(string -> LOGGER.error("Failed to parse vibration listener for Sculk Sensor: '{}'", string))
                .ifPresent(listener -> this.vibrationData = listener);
        }
        // Paper start - Configurable sculk sensor listener range
        if (nbt.contains(PAPER_LISTENER_RANGE_NBT_KEY)) {
            this.rangeOverride = nbt.getInt(PAPER_LISTENER_RANGE_NBT_KEY);
        } else {
            this.rangeOverride = null;
        }
        // Paper end - Configurable sculk sensor listener range
    }

    protected static final String PAPER_LISTENER_RANGE_NBT_KEY = "Paper.ListenerRange"; // Paper - Configurable sculk sensor listener range
    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("last_vibration_frequency", this.lastVibrationFrequency);
        RegistryOps<Tag> registryOps = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        VibrationSystem.Data.CODEC
            .encodeStart(registryOps, this.vibrationData)
            .resultOrPartial(string -> LOGGER.error("Failed to encode vibration listener for Sculk Sensor: '{}'", string))
            .ifPresent(listenerNbt -> nbt.put("listener", listenerNbt));
        this.saveRangeOverride(nbt); // Paper - Configurable sculk sensor listener range
    }
    // Paper start - Configurable sculk sensor listener range
    protected void saveRangeOverride(CompoundTag nbt) {
        if (this.rangeOverride != null && this.rangeOverride != VibrationUser.LISTENER_RANGE) nbt.putInt(PAPER_LISTENER_RANGE_NBT_KEY, this.rangeOverride); // only save if it's different from the default
    }
    // Paper end - Configurable sculk sensor listener range

    @Override
    public VibrationSystem.Data getVibrationData() {
        return this.vibrationData;
    }

    @Override
    public VibrationSystem.User getVibrationUser() {
        return this.vibrationUser;
    }

    public int getLastVibrationFrequency() {
        return this.lastVibrationFrequency;
    }

    public void setLastVibrationFrequency(int lastVibrationFrequency) {
        this.lastVibrationFrequency = lastVibrationFrequency;
    }

    @Override
    public VibrationSystem.Listener getListener() {
        return this.vibrationListener;
    }

    protected class VibrationUser implements VibrationSystem.User {
        public static final int LISTENER_RANGE = 8;
        protected final BlockPos blockPos;
        private final PositionSource positionSource;

        public VibrationUser(final BlockPos pos) {
            this.blockPos = pos;
            this.positionSource = new BlockPositionSource(pos);
        }

        @Override
        public int getListenerRadius() {
            if (SculkSensorBlockEntity.this.rangeOverride != null) return SculkSensorBlockEntity.this.rangeOverride; // Paper - Configurable sculk sensor listener range
            return 8;
        }

        @Override
        public PositionSource getPositionSource() {
            return this.positionSource;
        }

        @Override
        public boolean canTriggerAvoidVibration() {
            return true;
        }

        @Override
        public boolean canReceiveVibration(ServerLevel world, BlockPos pos, Holder<GameEvent> event, @Nullable GameEvent.Context emitter) {
            return (!pos.equals(this.blockPos) || !event.is(GameEvent.BLOCK_DESTROY) && !event.is(GameEvent.BLOCK_PLACE))
                && SculkSensorBlock.canActivate(SculkSensorBlockEntity.this.getBlockState());
        }

        @Override
        public void onReceiveVibration(
            ServerLevel world, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance
        ) {
            BlockState blockState = SculkSensorBlockEntity.this.getBlockState();
            if (SculkSensorBlock.canActivate(blockState)) {
                SculkSensorBlockEntity.this.setLastVibrationFrequency(VibrationSystem.getGameEventFrequency(event));
                int i = VibrationSystem.getRedstoneStrengthForDistance(distance, this.getListenerRadius());
                if (blockState.getBlock() instanceof SculkSensorBlock sculkSensorBlock) {
                    sculkSensorBlock.activate(sourceEntity, world, this.blockPos, blockState, i, SculkSensorBlockEntity.this.getLastVibrationFrequency());
                }
            }
        }

        @Override
        public void onDataChanged() {
            SculkSensorBlockEntity.this.setChanged();
        }

        @Override
        public boolean requiresAdjacentChunksToBeTicking() {
            return true;
        }
    }
}
