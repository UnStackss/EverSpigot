package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public class BeehiveBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_FLOWER_POS = "flower_pos";
    private static final String BEES = "bees";
    static final List<String> IGNORED_BEE_TAGS = Arrays.asList("Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime", "FallDistance", "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp", "HurtTime", "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos", "Rotation", "SleepingX", "SleepingY", "SleepingZ", "CannotEnterHiveTicks", "TicksSincePollination", "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID");
    public static final int MAX_OCCUPANTS = 3;
    private static final int MIN_TICKS_BEFORE_REENTERING_HIVE = 400;
    private static final int MIN_OCCUPATION_TICKS_NECTAR = 2400;
    public static final int MIN_OCCUPATION_TICKS_NECTARLESS = 600;
    private List<BeehiveBlockEntity.BeeData> stored = Lists.newArrayList();
    @Nullable
    public BlockPos savedFlowerPos;
    public int maxBees = 3; // CraftBukkit - allow setting max amount of bees a hive can hold

    public BeehiveBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.BEEHIVE, pos, state);
    }

    @Override
    public void setChanged() {
        if (this.isFireNearby()) {
            this.emptyAllLivingFromHive((Player) null, this.level.getBlockState(this.getBlockPos()), BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        super.setChanged();
    }

    public boolean isFireNearby() {
        if (this.level == null) {
            return false;
        } else {
            Iterator iterator = BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1)).iterator();

            BlockPos blockposition;

            do {
                if (!iterator.hasNext()) {
                    return false;
                }

                blockposition = (BlockPos) iterator.next();
            } while (!(this.level.getBlockState(blockposition).getBlock() instanceof FireBlock));

            return true;
        }
    }

    public boolean isEmpty() {
        return this.stored.isEmpty();
    }

    public boolean isFull() {
        return this.stored.size() == this.maxBees; // CraftBukkit
    }

    public void emptyAllLivingFromHive(@Nullable Player player, BlockState state, BeehiveBlockEntity.BeeReleaseStatus beeState) {
        List<Entity> list = this.releaseAllOccupants(state, beeState);

        if (player != null) {
            Iterator iterator = list.iterator();

            while (iterator.hasNext()) {
                Entity entity = (Entity) iterator.next();

                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (player.position().distanceToSqr(entity.position()) <= 16.0D) {
                        if (!this.isSedated()) {
                            entitybee.setTarget(player, org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER, true); // CraftBukkit
                        } else {
                            entitybee.setStayOutOfHiveCountdown(400);
                        }
                    }
                }
            }
        }

    }

    private List<Entity> releaseAllOccupants(BlockState state, BeehiveBlockEntity.BeeReleaseStatus beeState) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return this.releaseBees(state, beeState, false);
    }

    public List<Entity> releaseBees(BlockState iblockdata, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, boolean force) {
        List<Entity> list = Lists.newArrayList();

        this.stored.removeIf((tileentitybeehive_hivebee) -> {
            return BeehiveBlockEntity.releaseOccupant(this.level, this.worldPosition, iblockdata, tileentitybeehive_hivebee.toOccupant(), list, tileentitybeehive_releasestatus, this.savedFlowerPos, force);
            // CraftBukkit end
        });
        if (!list.isEmpty()) {
            super.setChanged();
        }

        return list;
    }

    @VisibleForDebug
    public int getOccupantCount() {
        return this.stored.size();
    }

    // Paper start - Add EntityBlockStorage clearEntities
    public void clearBees() {
        this.stored.clear();
    }
    // Paper end - Add EntityBlockStorage clearEntities
    public static int getHoneyLevel(BlockState state) {
        return (Integer) state.getValue(BeehiveBlock.HONEY_LEVEL);
    }

    @VisibleForDebug
    public boolean isSedated() {
        return CampfireBlock.isSmokeyPos(this.level, this.getBlockPos());
    }

    public void addOccupant(Entity entity) {
        if (this.stored.size() < this.maxBees) { // CraftBukkit
            // CraftBukkit start
            if (this.level != null) {
                org.bukkit.event.entity.EntityEnterBlockEvent event = new org.bukkit.event.entity.EntityEnterBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(this.level, this.getBlockPos()));
                org.bukkit.Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    if (entity instanceof Bee) {
                        ((Bee) entity).setStayOutOfHiveCountdown(400);
                    }
                    return;
                }
            }
            // CraftBukkit end
            entity.stopRiding();
            entity.ejectPassengers();
            this.storeBee(BeehiveBlockEntity.Occupant.of(entity));
            if (this.level != null) {
                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    if (entitybee.hasSavedFlowerPos() && (!this.hasSavedFlowerPos() || this.level.random.nextBoolean())) {
                        this.savedFlowerPos = entitybee.getSavedFlowerPos();
                    }
                }

                BlockPos blockposition = this.getBlockPos();

                this.level.playSound((Player) null, (double) blockposition.getX(), (double) blockposition.getY(), (double) blockposition.getZ(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                this.level.gameEvent((Holder) GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entity, this.getBlockState()));
            }

            entity.discard(EntityRemoveEvent.Cause.ENTER_BLOCK); // CraftBukkit - add Bukkit remove cause
            super.setChanged();
        }
    }

    public void storeBee(BeehiveBlockEntity.Occupant bee) {
        this.stored.add(new BeehiveBlockEntity.BeeData(bee));
    }

    private static boolean releaseOccupant(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity.Occupant bee, @Nullable List<Entity> entities, BeehiveBlockEntity.BeeReleaseStatus beeState, @Nullable BlockPos flowerPos) {
        // CraftBukkit start - This allows us to bypass the night/rain/emergency check
        return BeehiveBlockEntity.releaseOccupant(world, pos, state, bee, entities, beeState, flowerPos, false);
    }

    private static boolean releaseOccupant(Level world, BlockPos blockposition, BlockState iblockdata, BeehiveBlockEntity.Occupant tileentitybeehive_c, @Nullable List<Entity> list, BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus, @Nullable BlockPos blockposition1, boolean force) {
        if (!force && (world.isNight() || world.isRaining()) && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
            // CraftBukkit end
            return false;
        } else {
            Direction enumdirection = (Direction) iblockdata.getValue(BeehiveBlock.FACING);
            BlockPos blockposition2 = blockposition.relative(enumdirection);
            boolean flag = !world.getBlockState(blockposition2).getCollisionShape(world, blockposition2).isEmpty();

            if (flag && tileentitybeehive_releasestatus != BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY) {
                return false;
            } else {
                Entity entity = tileentitybeehive_c.createEntity(world, blockposition);

                if (entity != null) {
                    // CraftBukkit start
                    if (entity instanceof Bee) {
                        float f = entity.getBbWidth();
                        double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                        double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                        double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                        double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                        entity.moveTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                    }
                    if (!world.addFreshEntity(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.BEEHIVE)) return false; // CraftBukkit - SpawnReason, moved from below
                    // CraftBukkit end
                    if (entity instanceof Bee) {
                        Bee entitybee = (Bee) entity;

                        if (blockposition1 != null && !entitybee.hasSavedFlowerPos() && world.random.nextFloat() < 0.9F) {
                            entitybee.setSavedFlowerPos(blockposition1);
                        }

                        if (tileentitybeehive_releasestatus == BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED) {
                            entitybee.dropOffNectar();
                            if (iblockdata.is(BlockTags.BEEHIVES, (blockbase_blockdata) -> {
                                return blockbase_blockdata.hasProperty(BeehiveBlock.HONEY_LEVEL);
                            })) {
                                int i = BeehiveBlockEntity.getHoneyLevel(iblockdata);

                                if (i < 5) {
                                    int j = world.random.nextInt(100) == 0 ? 2 : 1;

                                    if (i + j > 5) {
                                        --j;
                                    }

                                    // Paper start - Fire EntityChangeBlockEvent in more places
                                    BlockState newBlockState = iblockdata.setValue(BeehiveBlock.HONEY_LEVEL, i + j);

                                    if (org.bukkit.craftbukkit.event.CraftEventFactory.callEntityChangeBlockEvent(entitybee, blockposition, newBlockState)) {
                                        world.setBlockAndUpdate(blockposition, newBlockState);
                                    }
                                    // Paper end - Fire EntityChangeBlockEvent in more places
                                }
                            }
                        }

                        if (list != null) {
                            list.add(entitybee);
                        }

                        /* // CraftBukkit start
                        float f = entity.getBbWidth();
                        double d0 = flag ? 0.0D : 0.55D + (double) (f / 2.0F);
                        double d1 = (double) blockposition.getX() + 0.5D + d0 * (double) enumdirection.getStepX();
                        double d2 = (double) blockposition.getY() + 0.5D - (double) (entity.getBbHeight() / 2.0F);
                        double d3 = (double) blockposition.getZ() + 0.5D + d0 * (double) enumdirection.getStepZ();

                        entity.moveTo(d1, d2, d3, entity.getYRot(), entity.getXRot());
                         */ // CraftBukkit end
                    }

                    world.playSound((Player) null, blockposition, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
                    world.gameEvent((Holder) GameEvent.BLOCK_CHANGE, blockposition, GameEvent.Context.of(entity, world.getBlockState(blockposition)));
                    return true; // return this.world.addFreshEntity(entity); // CraftBukkit - moved up
                } else {
                    return false;
                }
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return this.savedFlowerPos != null;
    }

    private static void tickOccupants(Level world, BlockPos pos, BlockState state, List<BeehiveBlockEntity.BeeData> bees, @Nullable BlockPos flowerPos) {
        boolean flag = false;
        Iterator<BeehiveBlockEntity.BeeData> iterator = bees.iterator();

        while (iterator.hasNext()) {
            BeehiveBlockEntity.BeeData tileentitybeehive_hivebee = (BeehiveBlockEntity.BeeData) iterator.next();

            if (tileentitybeehive_hivebee.tick()) {
                BeehiveBlockEntity.BeeReleaseStatus tileentitybeehive_releasestatus = tileentitybeehive_hivebee.hasNectar() ? BeehiveBlockEntity.BeeReleaseStatus.HONEY_DELIVERED : BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED;

                if (BeehiveBlockEntity.releaseOccupant(world, pos, state, tileentitybeehive_hivebee.toOccupant(), (List) null, tileentitybeehive_releasestatus, flowerPos)) {
                    flag = true;
                    iterator.remove();
                    // CraftBukkit start
                } else {
                    tileentitybeehive_hivebee.ticksInHive = tileentitybeehive_hivebee.occupant.minTicksInHive / 2; // Not strictly Vanilla behaviour in cases where bees cannot spawn but still reasonable
                    // CraftBukkit end
                }
            }
        }

        if (flag) {
            setChanged(world, pos, state);
        }

    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity) {
        BeehiveBlockEntity.tickOccupants(world, pos, state, blockEntity.stored, blockEntity.savedFlowerPos);
        if (!blockEntity.stored.isEmpty() && world.getRandom().nextDouble() < 0.005D) {
            double d0 = (double) pos.getX() + 0.5D;
            double d1 = (double) pos.getY();
            double d2 = (double) pos.getZ() + 0.5D;

            world.playSound((Player) null, d0, d1, d2, SoundEvents.BEEHIVE_WORK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        DebugPackets.sendHiveInfo(world, pos, state, blockEntity);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        if (nbt.contains("bees")) {
            BeehiveBlockEntity.Occupant.LIST_CODEC.parse(NbtOps.INSTANCE, nbt.get("bees")).resultOrPartial((s) -> {
                BeehiveBlockEntity.LOGGER.error("Failed to parse bees: '{}'", s);
            }).ifPresent((list) -> {
                list.forEach(this::storeBee);
            });
        }

        this.savedFlowerPos = (BlockPos) NbtUtils.readBlockPos(nbt, "flower_pos").orElse(null); // CraftBukkit - decompile error
        // CraftBukkit start
        if (nbt.contains("Bukkit.MaxEntities")) {
            this.maxBees = nbt.getInt("Bukkit.MaxEntities");
        }
        // CraftBukkit end
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.put("bees", (Tag) BeehiveBlockEntity.Occupant.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.getBees()).getOrThrow());
        if (this.hasSavedFlowerPos()) {
            nbt.put("flower_pos", NbtUtils.writeBlockPos(this.savedFlowerPos));
        }
        nbt.putInt("Bukkit.MaxEntities", this.maxBees); // CraftBukkit

    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.stored = Lists.newArrayList(); // CraftBukkit - SPIGOT-7790: create new copy (may be modified in physics event triggered by honey change)
        List<BeehiveBlockEntity.Occupant> list = (List) components.getOrDefault(DataComponents.BEES, List.of());

        list.forEach(this::storeBee);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder componentMapBuilder) {
        super.collectImplicitComponents(componentMapBuilder);
        componentMapBuilder.set(DataComponents.BEES, this.getBees());
    }

    @Override
    public void removeComponentsFromTag(CompoundTag nbt) {
        super.removeComponentsFromTag(nbt);
        nbt.remove("bees");
    }

    private List<BeehiveBlockEntity.Occupant> getBees() {
        return this.stored.stream().map(BeehiveBlockEntity.BeeData::toOccupant).toList();
    }

    public static enum BeeReleaseStatus {

        HONEY_DELIVERED, BEE_RELEASED, EMERGENCY;

        private BeeReleaseStatus() {}
    }

    public static record Occupant(CustomData entityData, int ticksInHive, int minTicksInHive) {

        public static final Codec<BeehiveBlockEntity.Occupant> CODEC = RecordCodecBuilder.create((instance) -> {
            return instance.group(CustomData.CODEC.optionalFieldOf("entity_data", CustomData.EMPTY).forGetter(BeehiveBlockEntity.Occupant::entityData), Codec.INT.fieldOf("ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::ticksInHive), Codec.INT.fieldOf("min_ticks_in_hive").forGetter(BeehiveBlockEntity.Occupant::minTicksInHive)).apply(instance, BeehiveBlockEntity.Occupant::new);
        });
        public static final Codec<List<BeehiveBlockEntity.Occupant>> LIST_CODEC = BeehiveBlockEntity.Occupant.CODEC.listOf();
        public static final StreamCodec<ByteBuf, BeehiveBlockEntity.Occupant> STREAM_CODEC = StreamCodec.composite(CustomData.STREAM_CODEC, BeehiveBlockEntity.Occupant::entityData, ByteBufCodecs.VAR_INT, BeehiveBlockEntity.Occupant::ticksInHive, ByteBufCodecs.VAR_INT, BeehiveBlockEntity.Occupant::minTicksInHive, BeehiveBlockEntity.Occupant::new);

        public static BeehiveBlockEntity.Occupant of(Entity entity) {
            CompoundTag nbttagcompound = new CompoundTag();

            entity.save(nbttagcompound);
            List<String> list = BeehiveBlockEntity.IGNORED_BEE_TAGS; // CraftBukkit - decompile error

            Objects.requireNonNull(nbttagcompound);
            list.forEach(nbttagcompound::remove);
            boolean flag = nbttagcompound.getBoolean("HasNectar");

            return new BeehiveBlockEntity.Occupant(CustomData.of(nbttagcompound), 0, flag ? 2400 : 600);
        }

        public static BeehiveBlockEntity.Occupant create(int ticksInHive) {
            CompoundTag nbttagcompound = new CompoundTag();

            nbttagcompound.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.BEE).toString());
            return new BeehiveBlockEntity.Occupant(CustomData.of(nbttagcompound), ticksInHive, 600);
        }

        @Nullable
        public Entity createEntity(Level world, BlockPos pos) {
            CompoundTag nbttagcompound = this.entityData.copyTag();
            List<String> list = BeehiveBlockEntity.IGNORED_BEE_TAGS; // CraftBukkit - decompile error

            Objects.requireNonNull(nbttagcompound);
            list.forEach(nbttagcompound::remove);
            Entity entity = EntityType.loadEntityRecursive(nbttagcompound, world, (entity1) -> {
                return entity1;
            });

            if (entity != null && entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof Bee) {
                    Bee entitybee = (Bee) entity;

                    entitybee.setHivePos(pos);
                    setBeeReleaseData(this.ticksInHive, entitybee);
                }

                return entity;
            } else {
                return null;
            }
        }

        private static void setBeeReleaseData(int ticksInHive, Bee beeEntity) {
            if (!beeEntity.ageLocked) { // Paper - Honor ageLock
            int j = beeEntity.getAge();

            if (j < 0) {
                beeEntity.setAge(Math.min(0, j + ticksInHive));
            } else if (j > 0) {
                beeEntity.setAge(Math.max(0, j - ticksInHive));
            }

            beeEntity.setInLoveTime(Math.max(0, beeEntity.getInLoveTime() - ticksInHive));
            } // Paper - Honor ageLock
        }
    }

    private static class BeeData {

        private final BeehiveBlockEntity.Occupant occupant;
        private int ticksInHive;

        BeeData(BeehiveBlockEntity.Occupant data) {
            this.occupant = data;
            this.ticksInHive = data.ticksInHive();
        }

        public boolean tick() {
            return this.ticksInHive++ > this.occupant.minTicksInHive;
        }

        public BeehiveBlockEntity.Occupant toOccupant() {
            return new BeehiveBlockEntity.Occupant(this.occupant.entityData, this.ticksInHive, this.occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return this.occupant.entityData.getUnsafe().getBoolean("HasNectar");
        }
    }
}
