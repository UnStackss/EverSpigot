package net.minecraft.world.level;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.ticks.LevelTickAccess;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public interface LevelAccessor extends CommonLevelAccessor, LevelTimeAccess {

    @Override
    default long dayTime() {
        return this.getLevelData().getDayTime();
    }

    long nextSubTickCount();

    LevelTickAccess<Block> getBlockTicks();

    private <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return new ScheduledTick<>(type, pos, this.getLevelData().getGameTime() + (long) delay, priority, this.nextSubTickCount());
    }

    private <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return new ScheduledTick<>(type, pos, this.getLevelData().getGameTime() + (long) delay, this.nextSubTickCount());
    }

    default void scheduleTick(BlockPos pos, Block block, int delay, TickPriority priority) {
        this.getBlockTicks().schedule(this.createTick(pos, block, delay, priority));
    }

    default void scheduleTick(BlockPos pos, Block block, int delay) {
        this.getBlockTicks().schedule(this.createTick(pos, block, delay));
    }

    LevelTickAccess<Fluid> getFluidTicks();

    default void scheduleTick(BlockPos pos, Fluid fluid, int delay, TickPriority priority) {
        this.getFluidTicks().schedule(this.createTick(pos, fluid, delay, priority));
    }

    default void scheduleTick(BlockPos pos, Fluid fluid, int delay) {
        this.getFluidTicks().schedule(this.createTick(pos, fluid, delay));
    }

    LevelData getLevelData();

    DifficultyInstance getCurrentDifficultyAt(BlockPos pos);

    @Nullable
    MinecraftServer getServer();

    default Difficulty getDifficulty() {
        return this.getLevelData().getDifficulty();
    }

    ChunkSource getChunkSource();

    @Override
    default boolean hasChunk(int chunkX, int chunkZ) {
        return this.getChunkSource().hasChunk(chunkX, chunkZ);
    }

    RandomSource getRandom();

    default void blockUpdated(BlockPos pos, Block block) {}

    default void neighborShapeChanged(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos, int flags, int maxUpdateDepth) {
        NeighborUpdater.executeShapeUpdate(this, direction, neighborState, pos, neighborPos, flags, maxUpdateDepth - 1);
    }

    default void playSound(@Nullable Player except, BlockPos pos, SoundEvent sound, SoundSource category) {
        this.playSound(except, pos, sound, category, 1.0F, 1.0F);
    }

    void playSound(@Nullable Player source, BlockPos pos, SoundEvent sound, SoundSource category, float volume, float pitch);

    void addParticle(ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

    void levelEvent(@Nullable Player player, int eventId, BlockPos pos, int data);

    default void levelEvent(int eventId, BlockPos pos, int data) {
        this.levelEvent((Player) null, eventId, pos, data);
    }

    void gameEvent(Holder<GameEvent> event, Vec3 emitterPos, GameEvent.Context emitter);

    default void gameEvent(@Nullable Entity entity, Holder<GameEvent> event, Vec3 pos) {
        this.gameEvent(event, pos, new GameEvent.Context(entity, (BlockState) null));
    }

    default void gameEvent(@Nullable Entity entity, Holder<GameEvent> event, BlockPos pos) {
        this.gameEvent(event, pos, new GameEvent.Context(entity, (BlockState) null));
    }

    default void gameEvent(Holder<GameEvent> event, BlockPos pos, GameEvent.Context emitter) {
        this.gameEvent(event, Vec3.atCenterOf(pos), emitter);
    }

    default void gameEvent(ResourceKey<GameEvent> event, BlockPos pos, GameEvent.Context emitter) {
        this.gameEvent((Holder) this.registryAccess().registryOrThrow(Registries.GAME_EVENT).getHolderOrThrow(event), pos, emitter);
    }

    net.minecraft.server.level.ServerLevel getMinecraftWorld(); // CraftBukkit
}
