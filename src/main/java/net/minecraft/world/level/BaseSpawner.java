package net.minecraft.world.level;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public abstract class BaseSpawner {

    public static final String SPAWN_DATA_TAG = "SpawnData";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EVENT_SPAWN = 1;
    public int spawnDelay = 20;
    public SimpleWeightedRandomList<SpawnData> spawnPotentials = SimpleWeightedRandomList.empty();
    @Nullable
    public SpawnData nextSpawnData;
    private double spin;
    private double oSpin;
    public int minSpawnDelay = 200;
    public int maxSpawnDelay = 800;
    public int spawnCount = 4;
    @Nullable
    private Entity displayEntity;
    public int maxNearbyEntities = 6;
    public int requiredPlayerRange = 16;
    public int spawnRange = 4;
    private int tickDelay = 0; // Paper - Configurable mob spawner tick rate

    public BaseSpawner() {}

    public void setEntityId(EntityType<?> type, @Nullable Level world, RandomSource random, BlockPos pos) {
        this.getOrCreateNextSpawnData(world, random, pos).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        this.spawnPotentials = SimpleWeightedRandomList.empty(); // CraftBukkit - SPIGOT-3496, MC-92282
    }

    public boolean isNearPlayer(Level world, BlockPos pos) {
        return world.hasNearbyAlivePlayerThatAffectsSpawning((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, (double) this.requiredPlayerRange); // Paper - Affects Spawning API
    }

    public void clientTick(Level world, BlockPos pos) {
        if (!this.isNearPlayer(world, pos)) {
            this.oSpin = this.spin;
        } else if (this.displayEntity != null) {
            RandomSource randomsource = world.getRandom();
            double d0 = (double) pos.getX() + randomsource.nextDouble();
            double d1 = (double) pos.getY() + randomsource.nextDouble();
            double d2 = (double) pos.getZ() + randomsource.nextDouble();

            world.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            world.addParticle(ParticleTypes.FLAME, d0, d1, d2, 0.0D, 0.0D, 0.0D);
            if (this.spawnDelay > 0) {
                --this.spawnDelay;
            }

            this.oSpin = this.spin;
            this.spin = (this.spin + (double) (1000.0F / ((float) this.spawnDelay + 200.0F))) % 360.0D;
        }

    }

    public void serverTick(ServerLevel world, BlockPos pos) {
        // Paper start - Configurable mob spawner tick rate
        if (spawnDelay > 0 && --tickDelay > 0) return;
        tickDelay = world.paperConfig().tickRates.mobSpawner;
        if (tickDelay == -1) { return; } // If disabled
        // Paper end - Configurable mob spawner tick rate
        if (this.isNearPlayer(world, pos)) {
            if (this.spawnDelay < -tickDelay) { // Paper - Configurable mob spawner tick rate
                this.delay(world, pos);
            }

            if (this.spawnDelay > 0) {
                this.spawnDelay -= tickDelay; // Paper - Configurable mob spawner tick rate
            } else {
                boolean flag = false;
                RandomSource randomsource = world.getRandom();
                SpawnData mobspawnerdata = this.getOrCreateNextSpawnData(world, randomsource, pos);

                for (int i = 0; i < this.spawnCount; ++i) {
                    CompoundTag nbttagcompound = mobspawnerdata.getEntityToSpawn();
                    Optional<EntityType<?>> optional = EntityType.by(nbttagcompound);

                    if (optional.isEmpty()) {
                        this.delay(world, pos);
                        return;
                    }

                    ListTag nbttaglist = nbttagcompound.getList("Pos", 6);
                    int j = nbttaglist.size();
                    double d0 = j >= 1 ? nbttaglist.getDouble(0) : (double) pos.getX() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D;
                    double d1 = j >= 2 ? nbttaglist.getDouble(1) : (double) (pos.getY() + randomsource.nextInt(3) - 1);
                    double d2 = j >= 3 ? nbttaglist.getDouble(2) : (double) pos.getZ() + (randomsource.nextDouble() - randomsource.nextDouble()) * (double) this.spawnRange + 0.5D;

                    if (world.noCollision(((EntityType) optional.get()).getSpawnAABB(d0, d1, d2))) {
                        BlockPos blockposition1 = BlockPos.containing(d0, d1, d2);

                        if (mobspawnerdata.getCustomSpawnRules().isPresent()) {
                            if (!((EntityType) optional.get()).getCategory().isFriendly() && world.getDifficulty() == Difficulty.PEACEFUL) {
                                continue;
                            }

                            SpawnData.CustomSpawnRules mobspawnerdata_a = (SpawnData.CustomSpawnRules) mobspawnerdata.getCustomSpawnRules().get();

                            if (!mobspawnerdata_a.isValidPosition(blockposition1, world)) {
                                continue;
                            }
                        } else if (!SpawnPlacements.checkSpawnRules((EntityType) optional.get(), world, MobSpawnType.SPAWNER, blockposition1, world.getRandom())) {
                            continue;
                        }
                        // Paper start - PreCreatureSpawnEvent
                        com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent event = new com.destroystokyo.paper.event.entity.PreSpawnerSpawnEvent(
                            io.papermc.paper.util.MCUtil.toLocation(world, d0, d1, d2),
                            org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(optional.get()),
                            io.papermc.paper.util.MCUtil.toLocation(world, pos)
                        );
                        if (!event.callEvent()) {
                            flag = true;
                            if (event.shouldAbortSpawn()) {
                                break;
                            }
                            continue;
                        }
                        // Paper end - PreCreatureSpawnEvent

                        Entity entity = EntityType.loadEntityRecursive(nbttagcompound, world, (entity1) -> {
                            entity1.moveTo(d0, d1, d2, entity1.getYRot(), entity1.getXRot());
                            return entity1;
                        });

                        if (entity == null) {
                            this.delay(world, pos);
                            return;
                        }

                        int k = world.getEntities(EntityTypeTest.forExactClass(entity.getClass()), (new AABB((double) pos.getX(), (double) pos.getY(), (double) pos.getZ(), (double) (pos.getX() + 1), (double) (pos.getY() + 1), (double) (pos.getZ() + 1))).inflate((double) this.spawnRange), EntitySelector.NO_SPECTATORS).size();

                        if (k >= this.maxNearbyEntities) {
                            this.delay(world, pos);
                            return;
                        }

                        entity.preserveMotion = true; // Paper - Fix Entity Teleportation and cancel velocity if teleported; preserve entity motion from tag
                        entity.moveTo(entity.getX(), entity.getY(), entity.getZ(), randomsource.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob) {
                            Mob entityinsentient = (Mob) entity;

                            if (mobspawnerdata.getCustomSpawnRules().isEmpty() && !entityinsentient.checkSpawnRules(world, MobSpawnType.SPAWNER) || !entityinsentient.checkSpawnObstruction(world)) {
                                continue;
                            }

                            boolean flag1 = mobspawnerdata.getEntityToSpawn().size() == 1 && mobspawnerdata.getEntityToSpawn().contains("id", 8);

                            if (flag1) {
                                ((Mob) entity).finalizeSpawn(world, world.getCurrentDifficultyAt(entity.blockPosition()), MobSpawnType.SPAWNER, (SpawnGroupData) null);
                            }

                            Optional<net.minecraft.world.entity.EquipmentTable> optional1 = mobspawnerdata.getEquipment(); // CraftBukkit - decompile error

                            Objects.requireNonNull(entityinsentient);
                            optional1.ifPresent(entityinsentient::equip);
                            // Spigot Start
                            if ( entityinsentient.level().spigotConfig.nerfSpawnerMobs )
                            {
                                entityinsentient.aware = false;
                            }
                            // Spigot End
                        }

                        entity.spawnedViaMobSpawner = true; // Paper
                        entity.spawnReason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER; // Paper - Entity#getEntitySpawnReason
                        flag = true; // Paper
                        // CraftBukkit start
                        if (org.bukkit.craftbukkit.event.CraftEventFactory.callSpawnerSpawnEvent(entity, pos).isCancelled()) {
                            continue;
                        }
                        if (!world.tryAddFreshEntityWithPassengers(entity, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.SPAWNER)) {
                            // CraftBukkit end
                            this.delay(world, pos);
                            return;
                        }

                        world.levelEvent(2004, pos, 0);
                        world.gameEvent(entity, (Holder) GameEvent.ENTITY_PLACE, blockposition1);
                        if (entity instanceof Mob) {
                            ((Mob) entity).spawnAnim();
                        }

                        //flag = true; // Paper - moved up above cancellable event
                    }
                }

                if (flag) {
                    this.delay(world, pos);
                }

            }
        }
    }

    public void delay(Level world, BlockPos pos) {
        RandomSource randomsource = world.random;

        if (this.maxSpawnDelay <= this.minSpawnDelay) {
            this.spawnDelay = this.minSpawnDelay;
        } else {
            this.spawnDelay = this.minSpawnDelay + randomsource.nextInt(this.maxSpawnDelay - this.minSpawnDelay);
        }

        this.spawnPotentials.getRandom(randomsource).ifPresent((weightedentry_b) -> {
            this.setNextSpawnData(world, pos, (SpawnData) weightedentry_b.data());
        });
        this.broadcastEvent(world, pos, 1);
    }

    public void load(@Nullable Level world, BlockPos pos, CompoundTag nbt) {
        // Paper start - use larger int if set
        if (nbt.contains("Paper.Delay")) {
            this.spawnDelay = nbt.getInt("Paper.Delay");
        } else {
        this.spawnDelay = nbt.getShort("Delay");
        }
        // Paper end
        boolean flag = nbt.contains("SpawnData", 10);

        if (flag) {
            SpawnData mobspawnerdata = (SpawnData) SpawnData.CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("SpawnData")).resultOrPartial((s) -> {
                BaseSpawner.LOGGER.warn("Invalid SpawnData: {}", s);
            }).orElseGet(SpawnData::new);

            this.setNextSpawnData(world, pos, mobspawnerdata);
        }

        boolean flag1 = nbt.contains("SpawnPotentials", 9);

        if (flag1) {
            ListTag nbttaglist = nbt.getList("SpawnPotentials", 10);

            this.spawnPotentials = (SimpleWeightedRandomList) SpawnData.LIST_CODEC.parse(NbtOps.INSTANCE, nbttaglist).resultOrPartial((s) -> {
                BaseSpawner.LOGGER.warn("Invalid SpawnPotentials list: {}", s);
            }).orElseGet(SimpleWeightedRandomList::empty);
        } else {
            this.spawnPotentials = SimpleWeightedRandomList.single(this.nextSpawnData != null ? this.nextSpawnData : new SpawnData());
        }

        // Paper start - use ints if set
        if (nbt.contains("Paper.MinSpawnDelay", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            this.minSpawnDelay = nbt.getInt("Paper.MinSpawnDelay");
            this.maxSpawnDelay = nbt.getInt("Paper.MaxSpawnDelay");
            this.spawnCount = nbt.getShort("SpawnCount");
        } else // Paper end
        if (nbt.contains("MinSpawnDelay", 99)) {
            this.minSpawnDelay = nbt.getInt("MinSpawnDelay"); // Paper - short -> int
            this.maxSpawnDelay = nbt.getInt("MaxSpawnDelay"); // Paper - short -> int
            this.spawnCount = nbt.getShort("SpawnCount");
        }

        if (nbt.contains("MaxNearbyEntities", 99)) {
            this.maxNearbyEntities = nbt.getShort("MaxNearbyEntities");
            this.requiredPlayerRange = nbt.getShort("RequiredPlayerRange");
        }

        if (nbt.contains("SpawnRange", 99)) {
            this.spawnRange = nbt.getShort("SpawnRange");
        }

        this.displayEntity = null;
    }

    public CompoundTag save(CompoundTag nbt) {
        // Paper start
        if (spawnDelay > Short.MAX_VALUE) {
            nbt.putInt("Paper.Delay", this.spawnDelay);
        }
        nbt.putShort("Delay", (short) Math.min(Short.MAX_VALUE, this.spawnDelay));

        if (minSpawnDelay > Short.MAX_VALUE || maxSpawnDelay > Short.MAX_VALUE) {
            nbt.putInt("Paper.MinSpawnDelay", this.minSpawnDelay);
            nbt.putInt("Paper.MaxSpawnDelay", this.maxSpawnDelay);
        }

        nbt.putShort("MinSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.minSpawnDelay));
        nbt.putShort("MaxSpawnDelay", (short) Math.min(Short.MAX_VALUE, this.maxSpawnDelay));
        // Paper end
        nbt.putShort("SpawnCount", (short) this.spawnCount);
        nbt.putShort("MaxNearbyEntities", (short) this.maxNearbyEntities);
        nbt.putShort("RequiredPlayerRange", (short) this.requiredPlayerRange);
        nbt.putShort("SpawnRange", (short) this.spawnRange);
        if (this.nextSpawnData != null) {
            nbt.put("SpawnData", (Tag) SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, this.nextSpawnData).getOrThrow((s) -> {
                return new IllegalStateException("Invalid SpawnData: " + s);
            }));
        }

        nbt.put("SpawnPotentials", (Tag) SpawnData.LIST_CODEC.encodeStart(NbtOps.INSTANCE, this.spawnPotentials).getOrThrow());
        return nbt;
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(Level world, BlockPos pos) {
        if (this.displayEntity == null) {
            CompoundTag nbttagcompound = this.getOrCreateNextSpawnData(world, world.getRandom(), pos).getEntityToSpawn();

            if (!nbttagcompound.contains("id", 8)) {
                return null;
            }

            this.displayEntity = EntityType.loadEntityRecursive(nbttagcompound, world, Function.identity());
            if (nbttagcompound.size() == 1 && this.displayEntity instanceof Mob) {
                ;
            }
        }

        return this.displayEntity;
    }

    public boolean onEventTriggered(Level world, int status) {
        if (status == 1) {
            if (world.isClientSide) {
                this.spawnDelay = this.minSpawnDelay;
            }

            return true;
        } else {
            return false;
        }
    }

    public void setNextSpawnData(@Nullable Level world, BlockPos pos, SpawnData spawnEntry) {
        this.nextSpawnData = spawnEntry;
    }

    private SpawnData getOrCreateNextSpawnData(@Nullable Level world, RandomSource random, BlockPos pos) {
        if (this.nextSpawnData != null) {
            return this.nextSpawnData;
        } else {
            this.setNextSpawnData(world, pos, (SpawnData) this.spawnPotentials.getRandom(random).map(WeightedEntry.Wrapper::data).orElseGet(SpawnData::new));
            return this.nextSpawnData;
        }
    }

    public abstract void broadcastEvent(Level world, BlockPos pos, int status);

    public double getSpin() {
        return this.spin;
    }

    public double getoSpin() {
        return this.oSpin;
    }
}
