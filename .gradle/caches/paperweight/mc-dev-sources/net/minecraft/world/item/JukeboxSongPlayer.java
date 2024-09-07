package net.minecraft.world.item;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class JukeboxSongPlayer {
    public static final int PLAY_EVENT_INTERVAL_TICKS = 20;
    private long ticksSinceSongStarted;
    @Nullable
    public Holder<JukeboxSong> song;
    private final BlockPos blockPos;
    private final JukeboxSongPlayer.OnSongChanged onSongChanged;

    public JukeboxSongPlayer(JukeboxSongPlayer.OnSongChanged changeNotifier, BlockPos pos) {
        this.onSongChanged = changeNotifier;
        this.blockPos = pos;
    }

    public boolean isPlaying() {
        return this.song != null;
    }

    @Nullable
    public JukeboxSong getSong() {
        return this.song == null ? null : this.song.value();
    }

    public long getTicksSinceSongStarted() {
        return this.ticksSinceSongStarted;
    }

    public void setSongWithoutPlaying(Holder<JukeboxSong> song, long ticksPlaying) {
        if (!song.value().hasFinished(ticksPlaying)) {
            this.song = song;
            this.ticksSinceSongStarted = ticksPlaying;
        }
    }

    public void play(LevelAccessor world, Holder<JukeboxSong> song) {
        this.song = song;
        this.ticksSinceSongStarted = 0L;
        int i = world.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG).getId(this.song.value());
        world.levelEvent(null, 1010, this.blockPos, i);
        this.onSongChanged.notifyChange();
    }

    public void stop(LevelAccessor world, @Nullable BlockState state) {
        if (this.song != null) {
            this.song = null;
            this.ticksSinceSongStarted = 0L;
            world.gameEvent(GameEvent.JUKEBOX_STOP_PLAY, this.blockPos, GameEvent.Context.of(state));
            world.levelEvent(1011, this.blockPos, 0);
            this.onSongChanged.notifyChange();
        }
    }

    public void tick(LevelAccessor world, @Nullable BlockState state) {
        if (this.song != null) {
            if (this.song.value().hasFinished(this.ticksSinceSongStarted)) {
                this.stop(world, state);
            } else {
                if (this.shouldEmitJukeboxPlayingEvent()) {
                    world.gameEvent(GameEvent.JUKEBOX_PLAY, this.blockPos, GameEvent.Context.of(state));
                    spawnMusicParticles(world, this.blockPos);
                }

                this.ticksSinceSongStarted++;
            }
        }
    }

    private boolean shouldEmitJukeboxPlayingEvent() {
        return this.ticksSinceSongStarted % 20L == 0L;
    }

    private static void spawnMusicParticles(LevelAccessor world, BlockPos pos) {
        if (world instanceof ServerLevel serverLevel) {
            Vec3 vec3 = Vec3.atBottomCenterOf(pos).add(0.0, 1.2F, 0.0);
            float f = (float)world.getRandom().nextInt(4) / 24.0F;
            serverLevel.sendParticles(ParticleTypes.NOTE, vec3.x(), vec3.y(), vec3.z(), 0, (double)f, 0.0, 0.0, 1.0);
        }
    }

    @FunctionalInterface
    public interface OnSongChanged {
        void notifyChange();
    }
}
