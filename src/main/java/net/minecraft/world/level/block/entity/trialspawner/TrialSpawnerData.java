package net.minecraft.world.level.block.entity.trialspawner;

import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.random.WeightedEntry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class TrialSpawnerData {

    public static final String TAG_SPAWN_DATA = "spawn_data";
    private static final String TAG_NEXT_MOB_SPAWNS_AT = "next_mob_spawns_at";
    private static final int DELAY_BETWEEN_PLAYER_SCANS = 20;
    private static final int TRIAL_OMEN_PER_BAD_OMEN_LEVEL = 18000;
    public static MapCodec<TrialSpawnerData> MAP_CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(UUIDUtil.CODEC_SET.lenientOptionalFieldOf("registered_players", Sets.newHashSet()).forGetter((trialspawnerdata) -> {
            return trialspawnerdata.detectedPlayers;
        }), UUIDUtil.CODEC_SET.lenientOptionalFieldOf("current_mobs", Sets.newHashSet()).forGetter((trialspawnerdata) -> {
            return trialspawnerdata.currentMobs;
        }), Codec.LONG.lenientOptionalFieldOf("cooldown_ends_at", 0L).forGetter((trialspawnerdata) -> {
            return trialspawnerdata.cooldownEndsAt;
        }), Codec.LONG.lenientOptionalFieldOf("next_mob_spawns_at", 0L).forGetter((trialspawnerdata) -> {
            return trialspawnerdata.nextMobSpawnsAt;
        }), Codec.intRange(0, Integer.MAX_VALUE).lenientOptionalFieldOf("total_mobs_spawned", 0).forGetter((trialspawnerdata) -> {
            return trialspawnerdata.totalMobsSpawned;
        }), SpawnData.CODEC.lenientOptionalFieldOf("spawn_data").forGetter((trialspawnerdata) -> {
            return trialspawnerdata.nextSpawnData;
        }), ResourceKey.codec(Registries.LOOT_TABLE).lenientOptionalFieldOf("ejecting_loot_table").forGetter((trialspawnerdata) -> {
            return trialspawnerdata.ejectingLootTable;
        })).apply(instance, TrialSpawnerData::new);
    });
    public final Set<UUID> detectedPlayers;
    public final Set<UUID> currentMobs;
    protected long cooldownEndsAt;
    protected long nextMobSpawnsAt;
    protected int totalMobsSpawned;
    public Optional<SpawnData> nextSpawnData;
    protected Optional<ResourceKey<LootTable>> ejectingLootTable;
    @Nullable
    protected Entity displayEntity;
    @Nullable
    private SimpleWeightedRandomList<ItemStack> dispensing;
    protected double spin;
    protected double oSpin;

    public TrialSpawnerData() {
        this(Collections.emptySet(), Collections.emptySet(), 0L, 0L, 0, Optional.empty(), Optional.empty());
    }

    public TrialSpawnerData(Set<UUID> players, Set<UUID> spawnedMobsAlive, long cooldownEnd, long nextMobSpawnsAt, int totalSpawnedMobs, Optional<SpawnData> spawnData, Optional<ResourceKey<LootTable>> rewardLootTable) {
        this.detectedPlayers = new HashSet();
        this.currentMobs = new HashSet();
        this.detectedPlayers.addAll(players);
        this.currentMobs.addAll(spawnedMobsAlive);
        this.cooldownEndsAt = cooldownEnd;
        this.nextMobSpawnsAt = nextMobSpawnsAt;
        this.totalMobsSpawned = totalSpawnedMobs;
        this.nextSpawnData = spawnData;
        this.ejectingLootTable = rewardLootTable;
    }

    public void reset() {
        this.detectedPlayers.clear();
        this.totalMobsSpawned = 0;
        this.nextMobSpawnsAt = 0L;
        this.cooldownEndsAt = 0L;
        this.currentMobs.clear();
        this.nextSpawnData = Optional.empty();
    }

    public boolean hasMobToSpawn(TrialSpawner logic, RandomSource random) {
        boolean flag = this.getOrCreateNextSpawnData(logic, random).getEntityToSpawn().contains("id", 8);

        return flag || !logic.getConfig().spawnPotentialsDefinition().isEmpty();
    }

    public boolean hasFinishedSpawningAllMobs(TrialSpawnerConfig config, int additionalPlayers) {
        return this.totalMobsSpawned >= config.calculateTargetTotalMobs(additionalPlayers);
    }

    public boolean haveAllCurrentMobsDied() {
        return this.currentMobs.isEmpty();
    }

    public boolean isReadyToSpawnNextMob(ServerLevel world, TrialSpawnerConfig config, int additionalPlayers) {
        return world.getGameTime() >= this.nextMobSpawnsAt && this.currentMobs.size() < config.calculateTargetSimultaneousMobs(additionalPlayers);
    }

    public int countAdditionalPlayers(BlockPos pos) {
        if (this.detectedPlayers.isEmpty()) {
            Util.logAndPauseIfInIde("Trial Spawner at " + String.valueOf(pos) + " has no detected players");
        }

        return Math.max(0, this.detectedPlayers.size() - 1);
    }

    public void tryDetectPlayers(ServerLevel world, BlockPos pos, TrialSpawner logic) {
        boolean flag = (pos.asLong() + world.getGameTime()) % 20L != 0L;

        if (!flag) {
            if (!logic.getState().equals(TrialSpawnerState.COOLDOWN) || !logic.isOminous()) {
                List<UUID> list = logic.getPlayerDetector().detect(world, logic.getEntitySelector(), pos, (double) logic.getRequiredPlayerRange(), true);
                boolean flag1;

                if (!logic.isOminous() && !list.isEmpty()) {
                    Optional<Pair<Player, Holder<MobEffect>>> optional = TrialSpawnerData.findPlayerWithOminousEffect(world, list);

                    optional.ifPresent((pair) -> {
                        Player entityhuman = (Player) pair.getFirst();

                        if (pair.getSecond() == MobEffects.BAD_OMEN) {
                            TrialSpawnerData.transformBadOmenIntoTrialOmen(entityhuman);
                        }

                        world.levelEvent(3020, BlockPos.containing(entityhuman.getEyePosition()), 0);
                        logic.applyOminous(world, pos);
                    });
                    flag1 = optional.isPresent();
                } else {
                    flag1 = false;
                }

                if (!logic.getState().equals(TrialSpawnerState.COOLDOWN) || flag1) {
                    boolean flag2 = logic.getData().detectedPlayers.isEmpty();
                    List<UUID> list1 = flag2 ? list : logic.getPlayerDetector().detect(world, logic.getEntitySelector(), pos, (double) logic.getRequiredPlayerRange(), false);

                    if (this.detectedPlayers.addAll(list1)) {
                        this.nextMobSpawnsAt = Math.max(world.getGameTime() + 40L, this.nextMobSpawnsAt);
                        if (!flag1) {
                            int i = logic.isOminous() ? 3019 : 3013;

                            world.levelEvent(i, pos, this.detectedPlayers.size());
                        }
                    }

                }
            }
        }
    }

    private static Optional<Pair<Player, Holder<MobEffect>>> findPlayerWithOminousEffect(ServerLevel world, List<UUID> players) {
        Player entityhuman = null;
        Iterator iterator = players.iterator();

        while (iterator.hasNext()) {
            UUID uuid = (UUID) iterator.next();
            Player entityhuman1 = world.getPlayerByUUID(uuid);

            if (entityhuman1 != null) {
                Holder<MobEffect> holder = MobEffects.TRIAL_OMEN;

                if (entityhuman1.hasEffect(holder)) {
                    return Optional.of(Pair.of(entityhuman1, holder));
                }

                if (entityhuman1.hasEffect(MobEffects.BAD_OMEN)) {
                    entityhuman = entityhuman1;
                }
            }
        }

        return Optional.ofNullable(entityhuman).map((entityhuman2) -> {
            return Pair.of(entityhuman2, MobEffects.BAD_OMEN);
        });
    }

    public void resetAfterBecomingOminous(TrialSpawner logic, ServerLevel world) {
        Stream<UUID> stream = this.currentMobs.stream(); // CraftBukkit - decompile error

        Objects.requireNonNull(world);
        stream.map(world::getEntity).forEach((entity) -> {
            if (entity != null) {
                world.levelEvent(3012, entity.blockPosition(), TrialSpawner.FlameParticle.NORMAL.encode());
                if (entity instanceof Mob) {
                    Mob entityinsentient = (Mob) entity;

                    entityinsentient.dropPreservedEquipment();
                }

                entity.remove(Entity.RemovalReason.DISCARDED, org.bukkit.event.entity.EntityRemoveEvent.Cause.DESPAWN); // CraftBukkit - Add bukkit remove cause;
            }
        });
        if (!logic.getOminousConfig().spawnPotentialsDefinition().isEmpty()) {
            this.nextSpawnData = Optional.empty();
        }

        this.totalMobsSpawned = 0;
        this.currentMobs.clear();
        this.nextMobSpawnsAt = world.getGameTime() + (long) logic.getOminousConfig().ticksBetweenSpawn();
        logic.markUpdated();
        this.cooldownEndsAt = world.getGameTime() + logic.getOminousConfig().ticksBetweenItemSpawners();
    }

    private static void transformBadOmenIntoTrialOmen(Player player) {
        MobEffectInstance mobeffect = player.getEffect(MobEffects.BAD_OMEN);

        if (mobeffect != null) {
            int i = mobeffect.getAmplifier() + 1;
            int j = 18000 * i;

            player.removeEffect(MobEffects.BAD_OMEN);
            player.addEffect(new MobEffectInstance(MobEffects.TRIAL_OMEN, j, 0));
        }
    }

    public boolean isReadyToOpenShutter(ServerLevel world, float f, int i) {
        long j = this.cooldownEndsAt - (long) i;

        return (float) world.getGameTime() >= (float) j + f;
    }

    public boolean isReadyToEjectItems(ServerLevel world, float f, int i) {
        long j = this.cooldownEndsAt - (long) i;

        return (float) (world.getGameTime() - j) % f == 0.0F;
    }

    public boolean isCooldownFinished(ServerLevel world) {
        return world.getGameTime() >= this.cooldownEndsAt;
    }

    public void setEntityId(TrialSpawner logic, RandomSource random, EntityType<?> type) {
        this.getOrCreateNextSpawnData(logic, random).getEntityToSpawn().putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    protected SpawnData getOrCreateNextSpawnData(TrialSpawner logic, RandomSource random) {
        if (this.nextSpawnData.isPresent()) {
            return (SpawnData) this.nextSpawnData.get();
        } else {
            SimpleWeightedRandomList<SpawnData> simpleweightedrandomlist = logic.getConfig().spawnPotentialsDefinition();
            Optional<SpawnData> optional = simpleweightedrandomlist.isEmpty() ? this.nextSpawnData : simpleweightedrandomlist.getRandom(random).map(WeightedEntry.Wrapper::data);

            this.nextSpawnData = Optional.of((SpawnData) optional.orElseGet(SpawnData::new));
            logic.markUpdated();
            return (SpawnData) this.nextSpawnData.get();
        }
    }

    @Nullable
    public Entity getOrCreateDisplayEntity(TrialSpawner logic, Level world, TrialSpawnerState state) {
        if (!state.hasSpinningMob()) {
            return null;
        } else {
            if (this.displayEntity == null) {
                CompoundTag nbttagcompound = this.getOrCreateNextSpawnData(logic, world.getRandom()).getEntityToSpawn();

                if (nbttagcompound.contains("id", 8)) {
                    this.displayEntity = EntityType.loadEntityRecursive(nbttagcompound, world, Function.identity());
                }
            }

            return this.displayEntity;
        }
    }

    public CompoundTag getUpdateTag(TrialSpawnerState state) {
        CompoundTag nbttagcompound = new CompoundTag();

        if (state == TrialSpawnerState.ACTIVE) {
            nbttagcompound.putLong("next_mob_spawns_at", this.nextMobSpawnsAt);
        }

        this.nextSpawnData.ifPresent((mobspawnerdata) -> {
            nbttagcompound.put("spawn_data", (Tag) SpawnData.CODEC.encodeStart(NbtOps.INSTANCE, mobspawnerdata).result().orElseThrow(() -> {
                return new IllegalStateException("Invalid SpawnData");
            }));
        });
        return nbttagcompound;
    }

    public double getSpin() {
        return this.spin;
    }

    public double getOSpin() {
        return this.oSpin;
    }

    SimpleWeightedRandomList<ItemStack> getDispensingItems(ServerLevel world, TrialSpawnerConfig config, BlockPos pos) {
        if (this.dispensing != null) {
            return this.dispensing;
        } else {
            LootTable loottable = world.getServer().reloadableRegistries().getLootTable(config.itemsToDropWhenOminous());
            LootParams lootparams = (new LootParams.Builder(world)).create(LootContextParamSets.EMPTY);
            long i = TrialSpawnerData.lowResolutionPosition(world, pos);
            ObjectArrayList<ItemStack> objectarraylist = loottable.getRandomItems(lootparams, i);

            if (objectarraylist.isEmpty()) {
                return SimpleWeightedRandomList.empty();
            } else {
                SimpleWeightedRandomList.Builder<ItemStack> simpleweightedrandomlist_a = new SimpleWeightedRandomList.Builder<>();
                ObjectListIterator objectlistiterator = objectarraylist.iterator();

                while (objectlistiterator.hasNext()) {
                    ItemStack itemstack = (ItemStack) objectlistiterator.next();

                    simpleweightedrandomlist_a.add(itemstack.copyWithCount(1), itemstack.getCount());
                }

                this.dispensing = simpleweightedrandomlist_a.build();
                return this.dispensing;
            }
        }
    }

    private static long lowResolutionPosition(ServerLevel world, BlockPos pos) {
        BlockPos blockposition1 = new BlockPos(Mth.floor((float) pos.getX() / 30.0F), Mth.floor((float) pos.getY() / 20.0F), Mth.floor((float) pos.getZ() / 30.0F));

        return world.getSeed() + blockposition1.asLong();
    }
}
