package net.minecraft.world.level.gameevent.vibrations;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.GameEventTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.CraftGameEvent;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.block.BlockReceiveGameEvent;
// CraftBukkit end

public interface VibrationSystem {

    List<ResourceKey<GameEvent>> RESONANCE_EVENTS = List.of(GameEvent.RESONATE_1.key(), GameEvent.RESONATE_2.key(), GameEvent.RESONATE_3.key(), GameEvent.RESONATE_4.key(), GameEvent.RESONATE_5.key(), GameEvent.RESONATE_6.key(), GameEvent.RESONATE_7.key(), GameEvent.RESONATE_8.key(), GameEvent.RESONATE_9.key(), GameEvent.RESONATE_10.key(), GameEvent.RESONATE_11.key(), GameEvent.RESONATE_12.key(), GameEvent.RESONATE_13.key(), GameEvent.RESONATE_14.key(), GameEvent.RESONATE_15.key());
    int DEFAULT_VIBRATION_FREQUENCY = 0;
    ToIntFunction<ResourceKey<GameEvent>> VIBRATION_FREQUENCY_FOR_EVENT = (ToIntFunction) Util.make(new Reference2IntOpenHashMap(), (reference2intopenhashmap) -> {
        reference2intopenhashmap.defaultReturnValue(0);
        reference2intopenhashmap.put(GameEvent.STEP.key(), 1);
        reference2intopenhashmap.put(GameEvent.SWIM.key(), 1);
        reference2intopenhashmap.put(GameEvent.FLAP.key(), 1);
        reference2intopenhashmap.put(GameEvent.PROJECTILE_LAND.key(), 2);
        reference2intopenhashmap.put(GameEvent.HIT_GROUND.key(), 2);
        reference2intopenhashmap.put(GameEvent.SPLASH.key(), 2);
        reference2intopenhashmap.put(GameEvent.ITEM_INTERACT_FINISH.key(), 3);
        reference2intopenhashmap.put(GameEvent.PROJECTILE_SHOOT.key(), 3);
        reference2intopenhashmap.put(GameEvent.INSTRUMENT_PLAY.key(), 3);
        reference2intopenhashmap.put(GameEvent.ENTITY_ACTION.key(), 4);
        reference2intopenhashmap.put(GameEvent.ELYTRA_GLIDE.key(), 4);
        reference2intopenhashmap.put(GameEvent.UNEQUIP.key(), 4);
        reference2intopenhashmap.put(GameEvent.ENTITY_DISMOUNT.key(), 5);
        reference2intopenhashmap.put(GameEvent.EQUIP.key(), 5);
        reference2intopenhashmap.put(GameEvent.ENTITY_INTERACT.key(), 6);
        reference2intopenhashmap.put(GameEvent.SHEAR.key(), 6);
        reference2intopenhashmap.put(GameEvent.ENTITY_MOUNT.key(), 6);
        reference2intopenhashmap.put(GameEvent.ENTITY_DAMAGE.key(), 7);
        reference2intopenhashmap.put(GameEvent.DRINK.key(), 8);
        reference2intopenhashmap.put(GameEvent.EAT.key(), 8);
        reference2intopenhashmap.put(GameEvent.CONTAINER_CLOSE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_CLOSE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_DEACTIVATE.key(), 9);
        reference2intopenhashmap.put(GameEvent.BLOCK_DETACH.key(), 9);
        reference2intopenhashmap.put(GameEvent.CONTAINER_OPEN.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_OPEN.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_ACTIVATE.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_ATTACH.key(), 10);
        reference2intopenhashmap.put(GameEvent.PRIME_FUSE.key(), 10);
        reference2intopenhashmap.put(GameEvent.NOTE_BLOCK_PLAY.key(), 10);
        reference2intopenhashmap.put(GameEvent.BLOCK_CHANGE.key(), 11);
        reference2intopenhashmap.put(GameEvent.BLOCK_DESTROY.key(), 12);
        reference2intopenhashmap.put(GameEvent.FLUID_PICKUP.key(), 12);
        reference2intopenhashmap.put(GameEvent.BLOCK_PLACE.key(), 13);
        reference2intopenhashmap.put(GameEvent.FLUID_PLACE.key(), 13);
        reference2intopenhashmap.put(GameEvent.ENTITY_PLACE.key(), 14);
        reference2intopenhashmap.put(GameEvent.LIGHTNING_STRIKE.key(), 14);
        reference2intopenhashmap.put(GameEvent.TELEPORT.key(), 14);
        reference2intopenhashmap.put(GameEvent.ENTITY_DIE.key(), 15);
        reference2intopenhashmap.put(GameEvent.EXPLODE.key(), 15);

        for (int i = 1; i <= 15; ++i) {
            reference2intopenhashmap.put(VibrationSystem.getResonanceEventByFrequency(i), i);
        }

    });

    VibrationSystem.Data getVibrationData();

    VibrationSystem.User getVibrationUser();

    static int getGameEventFrequency(Holder<GameEvent> gameEvent) {
        return (Integer) gameEvent.unwrapKey().map(VibrationSystem::getGameEventFrequency).orElse(0);
    }

    static int getGameEventFrequency(ResourceKey<GameEvent> gameEvent) {
        return VibrationSystem.VIBRATION_FREQUENCY_FOR_EVENT.applyAsInt(gameEvent);
    }

    static ResourceKey<GameEvent> getResonanceEventByFrequency(int frequency) {
        return (ResourceKey) VibrationSystem.RESONANCE_EVENTS.get(frequency - 1);
    }

    static int getRedstoneStrengthForDistance(float distance, int range) {
        double d0 = 15.0D / (double) range;

        return Math.max(1, 15 - Mth.floor(d0 * (double) distance));
    }

    public interface User {

        int getListenerRadius();

        PositionSource getPositionSource();

        boolean canReceiveVibration(ServerLevel world, BlockPos pos, Holder<GameEvent> event, GameEvent.Context emitter);

        void onReceiveVibration(ServerLevel world, BlockPos pos, Holder<GameEvent> event, @Nullable Entity sourceEntity, @Nullable Entity entity, float distance);

        default TagKey<GameEvent> getListenableEvents() {
            return GameEventTags.VIBRATIONS;
        }

        default boolean canTriggerAvoidVibration() {
            return false;
        }

        default boolean requiresAdjacentChunksToBeTicking() {
            return false;
        }

        default int calculateTravelTimeInTicks(float distance) {
            return Mth.floor(distance);
        }

        default boolean isValidVibration(Holder<GameEvent> gameEvent, GameEvent.Context emitter) {
            if (!gameEvent.is(this.getListenableEvents())) {
                return false;
            } else {
                Entity entity = emitter.sourceEntity();

                if (entity != null) {
                    if (entity.isSpectator()) {
                        return false;
                    }

                    if (entity.isSteppingCarefully() && gameEvent.is(GameEventTags.IGNORE_VIBRATIONS_SNEAKING)) {
                        if (this.canTriggerAvoidVibration() && entity instanceof ServerPlayer) {
                            ServerPlayer entityplayer = (ServerPlayer) entity;

                            CriteriaTriggers.AVOID_VIBRATION.trigger(entityplayer);
                        }

                        return false;
                    }

                    if (entity.dampensVibrations()) {
                        return false;
                    }
                }

                return emitter.affectedState() != null ? !emitter.affectedState().is(BlockTags.DAMPENS_VIBRATIONS) : true;
            }
        }

        default void onDataChanged() {}
    }

    public interface Ticker {

        static void tick(Level world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            if (world instanceof ServerLevel worldserver) {
                if (listenerData.currentVibration == null) {
                    Ticker.trySelectAndScheduleVibration(worldserver, listenerData, callback);
                }

                if (listenerData.currentVibration != null) {
                    boolean flag = listenerData.getTravelTimeInTicks() > 0;

                    Ticker.tryReloadVibrationParticle(worldserver, listenerData, callback);
                    listenerData.decrementTravelTime();
                    if (listenerData.getTravelTimeInTicks() <= 0) {
                        flag = Ticker.receiveVibration(worldserver, listenerData, callback, listenerData.currentVibration);
                    }

                    if (flag) {
                        callback.onDataChanged();
                    }

                }
            }
        }

        private static void trySelectAndScheduleVibration(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            listenerData.getSelectionStrategy().chosenCandidate(world.getGameTime()).ifPresent((vibrationinfo) -> {
                listenerData.setCurrentVibration(vibrationinfo);
                Vec3 vec3d = vibrationinfo.pos();

                listenerData.setTravelTimeInTicks(callback.calculateTravelTimeInTicks(vibrationinfo.distance()));
                world.sendParticles(new VibrationParticleOption(callback.getPositionSource(), listenerData.getTravelTimeInTicks()), vec3d.x, vec3d.y, vec3d.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                callback.onDataChanged();
                listenerData.getSelectionStrategy().startOver();
            });
        }

        private static void tryReloadVibrationParticle(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback) {
            if (listenerData.shouldReloadVibrationParticle()) {
                if (listenerData.currentVibration == null) {
                    listenerData.setReloadVibrationParticle(false);
                } else {
                    Vec3 vec3d = listenerData.currentVibration.pos();
                    PositionSource positionsource = callback.getPositionSource();
                    Vec3 vec3d1 = (Vec3) positionsource.getPosition(world).orElse(vec3d);
                    int i = listenerData.getTravelTimeInTicks();
                    int j = callback.calculateTravelTimeInTicks(listenerData.currentVibration.distance());
                    double d0 = 1.0D - (double) i / (double) j;
                    double d1 = Mth.lerp(d0, vec3d.x, vec3d1.x);
                    double d2 = Mth.lerp(d0, vec3d.y, vec3d1.y);
                    double d3 = Mth.lerp(d0, vec3d.z, vec3d1.z);
                    boolean flag = world.sendParticles(new VibrationParticleOption(positionsource, i), d1, d2, d3, 1, 0.0D, 0.0D, 0.0D, 0.0D) > 0;

                    if (flag) {
                        listenerData.setReloadVibrationParticle(false);
                    }

                }
            }
        }

        private static boolean receiveVibration(ServerLevel world, VibrationSystem.Data listenerData, VibrationSystem.User callback, VibrationInfo vibration) {
            BlockPos blockposition = BlockPos.containing(vibration.pos());
            BlockPos blockposition1 = (BlockPos) callback.getPositionSource().getPosition(world).map(BlockPos::containing).orElse(blockposition);

            if (callback.requiresAdjacentChunksToBeTicking() && !Ticker.areAdjacentChunksTicking(world, blockposition1)) {
                return false;
            } else {
                // CraftBukkit - decompile error
                callback.onReceiveVibration(world, blockposition, vibration.gameEvent(), (Entity) vibration.getEntity(world).orElse(null), (Entity) vibration.getProjectileOwner(world).orElse(null), VibrationSystem.Listener.distanceBetweenInBlocks(blockposition, blockposition1));
                listenerData.setCurrentVibration((VibrationInfo) null);
                return true;
            }
        }

        private static boolean areAdjacentChunksTicking(Level world, BlockPos pos) {
            ChunkPos chunkcoordintpair = new ChunkPos(pos);

            for (int i = chunkcoordintpair.x - 1; i <= chunkcoordintpair.x + 1; ++i) {
                for (int j = chunkcoordintpair.z - 1; j <= chunkcoordintpair.z + 1; ++j) {
                    if (!world.shouldTickBlocksAt(ChunkPos.asLong(i, j)) || world.getChunkSource().getChunkNow(i, j) == null) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public static class Listener implements GameEventListener {

        private final VibrationSystem system;

        public Listener(VibrationSystem receiver) {
            this.system = receiver;
        }

        @Override
        public PositionSource getListenerSource() {
            return this.system.getVibrationUser().getPositionSource();
        }

        @Override
        public int getListenerRadius() {
            return this.system.getVibrationUser().getListenerRadius();
        }

        @Override
        public boolean handleGameEvent(ServerLevel world, Holder<GameEvent> event, GameEvent.Context emitter, Vec3 emitterPos) {
            VibrationSystem.Data vibrationsystem_a = this.system.getVibrationData();
            VibrationSystem.User vibrationsystem_d = this.system.getVibrationUser();

            if (vibrationsystem_a.getCurrentVibration() != null) {
                return false;
            } else if (!vibrationsystem_d.isValidVibration(event, emitter)) {
                return false;
            } else {
                Optional<Vec3> optional = vibrationsystem_d.getPositionSource().getPosition(world);

                if (optional.isEmpty()) {
                    return false;
                } else {
                    Vec3 vec3d1 = (Vec3) optional.get();
                    // CraftBukkit start
                    boolean defaultCancel = !vibrationsystem_d.canReceiveVibration(world, BlockPos.containing(emitterPos), event, emitter);
                    Entity entity = emitter.sourceEntity();
                    BlockReceiveGameEvent event1 = new BlockReceiveGameEvent(CraftGameEvent.minecraftToBukkit(event.value()), CraftBlock.at(world, BlockPos.containing(vec3d1)), (entity == null) ? null : entity.getBukkitEntity());
                    event1.setCancelled(defaultCancel);
                    world.getCraftServer().getPluginManager().callEvent(event1);
                    if (event1.isCancelled()) {
                        // CraftBukkit end
                        return false;
                    } else if (Listener.isOccluded(world, emitterPos, vec3d1)) {
                        return false;
                    } else {
                        this.scheduleVibration(world, vibrationsystem_a, event, emitter, emitterPos, vec3d1);
                        return true;
                    }
                }
            }
        }

        public void forceScheduleVibration(ServerLevel world, Holder<GameEvent> event, GameEvent.Context emitter, Vec3 emitterPos) {
            this.system.getVibrationUser().getPositionSource().getPosition(world).ifPresent((vec3d1) -> {
                this.scheduleVibration(world, this.system.getVibrationData(), event, emitter, emitterPos, vec3d1);
            });
        }

        private void scheduleVibration(ServerLevel world, VibrationSystem.Data listenerData, Holder<GameEvent> event, GameEvent.Context emitter, Vec3 emitterPos, Vec3 listenerPos) {
            listenerData.selectionStrategy.addCandidate(new VibrationInfo(event, (float) emitterPos.distanceTo(listenerPos), emitterPos, emitter.sourceEntity()), world.getGameTime());
        }

        public static float distanceBetweenInBlocks(BlockPos emitterPos, BlockPos listenerPos) {
            return (float) Math.sqrt(emitterPos.distSqr(listenerPos));
        }

        private static boolean isOccluded(Level world, Vec3 emitterPos, Vec3 listenerPos) {
            Vec3 vec3d2 = new Vec3((double) Mth.floor(emitterPos.x) + 0.5D, (double) Mth.floor(emitterPos.y) + 0.5D, (double) Mth.floor(emitterPos.z) + 0.5D);
            Vec3 vec3d3 = new Vec3((double) Mth.floor(listenerPos.x) + 0.5D, (double) Mth.floor(listenerPos.y) + 0.5D, (double) Mth.floor(listenerPos.z) + 0.5D);
            Direction[] aenumdirection = Direction.values();
            int i = aenumdirection.length;

            for (int j = 0; j < i; ++j) {
                Direction enumdirection = aenumdirection[j];
                Vec3 vec3d4 = vec3d2.relative(enumdirection, 9.999999747378752E-6D);

                if (world.isBlockInLine(new ClipBlockStateContext(vec3d4, vec3d3, (iblockdata) -> {
                    return iblockdata.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
                })).getType() != HitResult.Type.BLOCK) {
                    return false;
                }
            }

            return true;
        }
    }

    public static final class Data {

        public static Codec<VibrationSystem.Data> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(VibrationInfo.CODEC.lenientOptionalFieldOf("event").forGetter((vibrationsystem_a) -> {
                return Optional.ofNullable(vibrationsystem_a.currentVibration);
            }), VibrationSelector.CODEC.optionalFieldOf("selector").xmap(o -> o.orElseGet(VibrationSelector::new), Optional::of).forGetter(VibrationSystem.Data::getSelectionStrategy), ExtraCodecs.NON_NEGATIVE_INT.fieldOf("event_delay").orElse(0).forGetter(VibrationSystem.Data::getTravelTimeInTicks)).apply(instance, (optional, vibrationselector, integer) -> { // Paper - fix MapLike spam for missing "selector" in 1.19.2
                return new VibrationSystem.Data((VibrationInfo) optional.orElse(null), vibrationselector, integer, true); // CraftBukkit - decompile error
            });
        });
        public static final String NBT_TAG_KEY = "listener";
        @Nullable
        VibrationInfo currentVibration;
        private int travelTimeInTicks;
        final VibrationSelector selectionStrategy;
        private boolean reloadVibrationParticle;

        private Data(@Nullable VibrationInfo vibration, VibrationSelector vibrationSelector, int delay, boolean spawnParticle) {
            this.currentVibration = vibration;
            this.travelTimeInTicks = delay;
            this.selectionStrategy = vibrationSelector;
            this.reloadVibrationParticle = spawnParticle;
        }

        public Data() {
            this((VibrationInfo) null, new VibrationSelector(), 0, false);
        }

        public VibrationSelector getSelectionStrategy() {
            return this.selectionStrategy;
        }

        @Nullable
        public VibrationInfo getCurrentVibration() {
            return this.currentVibration;
        }

        public void setCurrentVibration(@Nullable VibrationInfo vibration) {
            this.currentVibration = vibration;
        }

        public int getTravelTimeInTicks() {
            return this.travelTimeInTicks;
        }

        public void setTravelTimeInTicks(int delay) {
            this.travelTimeInTicks = delay;
        }

        public void decrementTravelTime() {
            this.travelTimeInTicks = Math.max(0, this.travelTimeInTicks - 1);
        }

        public boolean shouldReloadVibrationParticle() {
            return this.reloadVibrationParticle;
        }

        public void setReloadVibrationParticle(boolean spawnParticle) {
            this.reloadVibrationParticle = spawnParticle;
        }
    }
}
