package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.Optionull;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

public class SculkCatalystBlockEntity extends BlockEntity implements GameEventListener.Provider<SculkCatalystBlockEntity.CatalystListener> {

    private final SculkCatalystBlockEntity.CatalystListener catalystListener;

    public SculkCatalystBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.SCULK_CATALYST, pos, state);
        this.catalystListener = new SculkCatalystBlockEntity.CatalystListener(state, new BlockPositionSource(pos));
        this.catalystListener.level = this.level; // CraftBukkit
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, SculkCatalystBlockEntity blockEntity) {
        org.bukkit.craftbukkit.event.CraftEventFactory.sourceBlockOverride = blockEntity.getBlockPos(); // CraftBukkit - SPIGOT-7068: Add source block override, not the most elegant way but better than passing down a BlockPosition up to five methods deep.
        blockEntity.catalystListener.getSculkSpreader().updateCursors(world, pos, world.getRandom(), true);
        org.bukkit.craftbukkit.event.CraftEventFactory.sourceBlockOverride = null; // CraftBukkit
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.catalystListener.sculkSpreader.load(nbt);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        this.catalystListener.sculkSpreader.save(nbt);
        super.saveAdditional(nbt, registryLookup);
    }

    @Override
    public SculkCatalystBlockEntity.CatalystListener getListener() {
        return this.catalystListener;
    }

    public static class CatalystListener implements GameEventListener {

        public static final int PULSE_TICKS = 8;
        final SculkSpreader sculkSpreader;
        private final BlockState blockState;
        private final PositionSource positionSource;
        private Level level; // CraftBukkit

        public CatalystListener(BlockState state, PositionSource positionSource) {
            this.blockState = state;
            this.positionSource = positionSource;
            this.sculkSpreader = SculkSpreader.createLevelSpreader();
            this.sculkSpreader.level = this.level; // CraftBukkit
        }

        @Override
        public PositionSource getListenerSource() {
            return this.positionSource;
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public GameEventListener.DeliveryMode getDeliveryMode() {
            return GameEventListener.DeliveryMode.BY_DISTANCE;
        }

        @Override
        public boolean handleGameEvent(ServerLevel world, Holder<GameEvent> event, GameEvent.Context emitter, Vec3 emitterPos) {
            if (event.is((Holder) GameEvent.ENTITY_DIE)) {
                Entity entity = emitter.sourceEntity();

                if (entity instanceof LivingEntity) {
                    LivingEntity entityliving = (LivingEntity) entity;

                    if (!entityliving.wasExperienceConsumed()) {
                        DamageSource damagesource = entityliving.getLastDamageSource();
                        int i = entityliving.getExperienceReward(world, (Entity) Optionull.map(damagesource, DamageSource::getEntity));

                        if (entityliving.shouldDropExperience() && i > 0) {
                            this.sculkSpreader.addCursors(BlockPos.containing(emitterPos.relative(Direction.UP, 0.5D)), i);
                            this.tryAwardItSpreadsAdvancement(world, entityliving);
                        }

                        entityliving.skipDropExperience();
                        this.positionSource.getPosition(world).ifPresent((vec3d1) -> {
                            this.bloom(world, BlockPos.containing(vec3d1), this.blockState, world.getRandom());
                        });
                    }

                    return true;
                }
            }

            return false;
        }

        @VisibleForTesting
        public SculkSpreader getSculkSpreader() {
            return this.sculkSpreader;
        }

        public void bloom(ServerLevel world, BlockPos pos, BlockState state, RandomSource random) {
            world.setBlock(pos, (BlockState) state.setValue(SculkCatalystBlock.PULSE, true), 3);
            world.scheduleTick(pos, state.getBlock(), 8);
            world.sendParticles(ParticleTypes.SCULK_SOUL, (double) pos.getX() + 0.5D, (double) pos.getY() + 1.15D, (double) pos.getZ() + 0.5D, 2, 0.2D, 0.0D, 0.2D, 0.0D);
            world.playSound((Player) null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2.0F, 0.6F + random.nextFloat() * 0.4F);
        }

        private void tryAwardItSpreadsAdvancement(Level world, LivingEntity deadEntity) {
            LivingEntity entityliving1 = deadEntity.getLastHurtByMob();

            if (entityliving1 instanceof ServerPlayer entityplayer) {
                DamageSource damagesource = deadEntity.getLastDamageSource() == null ? world.damageSources().playerAttack(entityplayer) : deadEntity.getLastDamageSource();

                CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST.trigger(entityplayer, deadEntity, damagesource);
            }

        }
    }
}
