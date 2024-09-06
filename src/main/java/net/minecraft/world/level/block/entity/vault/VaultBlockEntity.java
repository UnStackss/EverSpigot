package net.minecraft.world.level.block.entity.vault;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

// CraftBukkit start
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.block.VaultDisplayItemEvent;
// CraftBukkit end

public class VaultBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final VaultServerData serverData = new VaultServerData();
    private final VaultSharedData sharedData = new VaultSharedData();
    private final VaultClientData clientData = new VaultClientData();
    private VaultConfig config;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.VAULT, pos, state);
        this.config = VaultConfig.DEFAULT;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return (CompoundTag) Util.make(new CompoundTag(), (nbttagcompound) -> {
            nbttagcompound.put("shared_data", VaultBlockEntity.encode(VaultSharedData.CODEC, this.sharedData, registryLookup));
        });
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.put("config", VaultBlockEntity.encode(VaultConfig.CODEC, this.config, registryLookup));
        nbt.put("shared_data", VaultBlockEntity.encode(VaultSharedData.CODEC, this.sharedData, registryLookup));
        nbt.put("server_data", VaultBlockEntity.encode(VaultServerData.CODEC, this.serverData, registryLookup));
    }

    private static <T> Tag encode(Codec<T> codec, T value, HolderLookup.Provider registries) {
        return (Tag) codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        DynamicOps<Tag> dynamicops = registryLookup.createSerializationContext(NbtOps.INSTANCE);
        DataResult dataresult;
        Logger logger;
        Optional optional;

        if (nbt.contains("server_data")) {
            dataresult = VaultServerData.CODEC.parse(dynamicops, nbt.get("server_data"));
            logger = VaultBlockEntity.LOGGER;
            Objects.requireNonNull(logger);
            optional = ((DataResult<VaultServerData>) dataresult).resultOrPartial(logger::error); // CraftBukkit - decompile error
            VaultServerData vaultserverdata = this.serverData;

            Objects.requireNonNull(this.serverData);
            ((Optional<VaultServerData>) optional).ifPresent(vaultserverdata::set); // CraftBukkit - decompile error
        }

        if (nbt.contains("config")) {
            dataresult = VaultConfig.CODEC.parse(dynamicops, nbt.get("config"));
            logger = VaultBlockEntity.LOGGER;
            Objects.requireNonNull(logger);
            ((DataResult<VaultConfig>) dataresult).resultOrPartial(logger::error).ifPresent((vaultconfig) -> { // CraftBukkit - decompile error
                this.config = vaultconfig;
            });
        }

        if (nbt.contains("shared_data")) {
            dataresult = VaultSharedData.CODEC.parse(dynamicops, nbt.get("shared_data"));
            logger = VaultBlockEntity.LOGGER;
            Objects.requireNonNull(logger);
            optional = ((DataResult<VaultSharedData>) dataresult).resultOrPartial(logger::error); // CraftBukkit - decompile error
            VaultSharedData vaultshareddata = this.sharedData;

            Objects.requireNonNull(this.sharedData);
            ((Optional<VaultSharedData>) optional).ifPresent(vaultshareddata::set); // CraftBukkit - decompile error
        }

    }

    @Nullable
    public VaultServerData getServerData() {
        return this.level != null && !this.level.isClientSide ? this.serverData : null;
    }

    public VaultSharedData getSharedData() {
        return this.sharedData;
    }

    public VaultClientData getClientData() {
        return this.clientData;
    }

    public VaultConfig getConfig() {
        return this.config;
    }

    @VisibleForTesting
    public void setConfig(VaultConfig config) {
        this.config = config;
    }

    public static final class Client {

        private static final int PARTICLE_TICK_RATE = 20;
        private static final float IDLE_PARTICLE_CHANCE = 0.5F;
        private static final float AMBIENT_SOUND_CHANCE = 0.02F;
        private static final int ACTIVATION_PARTICLE_COUNT = 20;
        private static final int DEACTIVATION_PARTICLE_COUNT = 20;

        public Client() {}

        public static void tick(Level world, BlockPos pos, BlockState state, VaultClientData clientData, VaultSharedData sharedData) {
            clientData.updateDisplayItemSpin();
            if (world.getGameTime() % 20L == 0L) {
                Client.emitConnectionParticlesForNearbyPlayers(world, pos, state, sharedData);
            }

            Client.emitIdleParticles(world, pos, sharedData, (Boolean) state.getValue(VaultBlock.OMINOUS) ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME);
            Client.playIdleSounds(world, pos, sharedData);
        }

        public static void emitActivationParticles(Level world, BlockPos pos, BlockState state, VaultSharedData sharedData, ParticleOptions particle) {
            Client.emitConnectionParticlesForNearbyPlayers(world, pos, state, sharedData);
            RandomSource randomsource = world.random;

            for (int i = 0; i < 20; ++i) {
                Vec3 vec3d = Client.randomPosInsideCage(pos, randomsource);

                world.addParticle(ParticleTypes.SMOKE, vec3d.x(), vec3d.y(), vec3d.z(), 0.0D, 0.0D, 0.0D);
                world.addParticle(particle, vec3d.x(), vec3d.y(), vec3d.z(), 0.0D, 0.0D, 0.0D);
            }

        }

        public static void emitDeactivationParticles(Level world, BlockPos pos, ParticleOptions particle) {
            RandomSource randomsource = world.random;

            for (int i = 0; i < 20; ++i) {
                Vec3 vec3d = Client.randomPosCenterOfCage(pos, randomsource);
                Vec3 vec3d1 = new Vec3(randomsource.nextGaussian() * 0.02D, randomsource.nextGaussian() * 0.02D, randomsource.nextGaussian() * 0.02D);

                world.addParticle(particle, vec3d.x(), vec3d.y(), vec3d.z(), vec3d1.x(), vec3d1.y(), vec3d1.z());
            }

        }

        private static void emitIdleParticles(Level world, BlockPos pos, VaultSharedData sharedData, ParticleOptions particle) {
            RandomSource randomsource = world.getRandom();

            if (randomsource.nextFloat() <= 0.5F) {
                Vec3 vec3d = Client.randomPosInsideCage(pos, randomsource);

                world.addParticle(ParticleTypes.SMOKE, vec3d.x(), vec3d.y(), vec3d.z(), 0.0D, 0.0D, 0.0D);
                if (Client.shouldDisplayActiveEffects(sharedData)) {
                    world.addParticle(particle, vec3d.x(), vec3d.y(), vec3d.z(), 0.0D, 0.0D, 0.0D);
                }
            }

        }

        private static void emitConnectionParticlesForPlayer(Level world, Vec3 pos, Player player) {
            RandomSource randomsource = world.random;
            Vec3 vec3d1 = pos.vectorTo(player.position().add(0.0D, (double) (player.getBbHeight() / 2.0F), 0.0D));
            int i = Mth.nextInt(randomsource, 2, 5);

            for (int j = 0; j < i; ++j) {
                Vec3 vec3d2 = vec3d1.offsetRandom(randomsource, 1.0F);

                world.addParticle(ParticleTypes.VAULT_CONNECTION, pos.x(), pos.y(), pos.z(), vec3d2.x(), vec3d2.y(), vec3d2.z());
            }

        }

        private static void emitConnectionParticlesForNearbyPlayers(Level world, BlockPos pos, BlockState state, VaultSharedData sharedData) {
            Set<UUID> set = sharedData.getConnectedPlayers();

            if (!set.isEmpty()) {
                Vec3 vec3d = Client.keyholePos(pos, (Direction) state.getValue(VaultBlock.FACING));
                Iterator iterator = set.iterator();

                while (iterator.hasNext()) {
                    UUID uuid = (UUID) iterator.next();
                    Player entityhuman = world.getPlayerByUUID(uuid);

                    if (entityhuman != null && Client.isWithinConnectionRange(pos, sharedData, entityhuman)) {
                        Client.emitConnectionParticlesForPlayer(world, vec3d, entityhuman);
                    }
                }

            }
        }

        private static boolean isWithinConnectionRange(BlockPos pos, VaultSharedData sharedData, Player player) {
            return player.blockPosition().distSqr(pos) <= Mth.square(sharedData.connectedParticlesRange());
        }

        private static void playIdleSounds(Level world, BlockPos pos, VaultSharedData sharedData) {
            if (Client.shouldDisplayActiveEffects(sharedData)) {
                RandomSource randomsource = world.getRandom();

                if (randomsource.nextFloat() <= 0.02F) {
                    world.playLocalSound(pos, SoundEvents.VAULT_AMBIENT, SoundSource.BLOCKS, randomsource.nextFloat() * 0.25F + 0.75F, randomsource.nextFloat() + 0.5F, false);
                }

            }
        }

        public static boolean shouldDisplayActiveEffects(VaultSharedData sharedData) {
            return sharedData.hasDisplayItem();
        }

        private static Vec3 randomPosCenterOfCage(BlockPos pos, RandomSource random) {
            return Vec3.atLowerCornerOf(pos).add(Mth.nextDouble(random, 0.4D, 0.6D), Mth.nextDouble(random, 0.4D, 0.6D), Mth.nextDouble(random, 0.4D, 0.6D));
        }

        private static Vec3 randomPosInsideCage(BlockPos pos, RandomSource random) {
            return Vec3.atLowerCornerOf(pos).add(Mth.nextDouble(random, 0.1D, 0.9D), Mth.nextDouble(random, 0.25D, 0.75D), Mth.nextDouble(random, 0.1D, 0.9D));
        }

        private static Vec3 keyholePos(BlockPos pos, Direction direction) {
            return Vec3.atBottomCenterOf(pos).add((double) direction.getStepX() * 0.5D, 1.75D, (double) direction.getStepZ() * 0.5D);
        }
    }

    public static final class Server {

        private static final int UNLOCKING_DELAY_TICKS = 14;
        private static final int DISPLAY_CYCLE_TICK_RATE = 20;
        private static final int INSERT_FAIL_SOUND_BUFFER_TICKS = 15;

        public Server() {}

        public static void tick(ServerLevel world, BlockPos pos, BlockState state, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData) {
            VaultState vaultstate = (VaultState) state.getValue(VaultBlock.STATE);

            if (Server.shouldCycleDisplayItem(world.getGameTime(), vaultstate)) {
                Server.cycleDisplayItemFromLootTable(world, vaultstate, config, sharedData, pos);
            }

            BlockState iblockdata1 = state;

            if (world.getGameTime() >= serverData.stateUpdatingResumesAt()) {
                iblockdata1 = (BlockState) state.setValue(VaultBlock.STATE, vaultstate.tickAndGetNext(world, pos, config, serverData, sharedData));
                if (!state.equals(iblockdata1)) {
                    Server.setVaultState(world, pos, state, iblockdata1, config, sharedData);
                }
            }

            if (serverData.isDirty || sharedData.isDirty) {
                VaultBlockEntity.setChanged(world, pos, state);
                if (sharedData.isDirty) {
                    world.sendBlockUpdated(pos, state, iblockdata1, 2);
                }

                serverData.isDirty = false;
                sharedData.isDirty = false;
            }

        }

        public static void tryInsertKey(ServerLevel world, BlockPos pos, BlockState state, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, Player player, ItemStack stack) {
            VaultState vaultstate = (VaultState) state.getValue(VaultBlock.STATE);

            if (Server.canEjectReward(config, vaultstate)) {
                if (!Server.isValidToInsert(config, stack)) {
                    Server.playInsertFailSound(world, serverData, pos, SoundEvents.VAULT_INSERT_ITEM_FAIL);
                } else if (serverData.hasRewardedPlayer(player)) {
                    Server.playInsertFailSound(world, serverData, pos, SoundEvents.VAULT_REJECT_REWARDED_PLAYER);
                } else {
                    List<ItemStack> list = Server.resolveItemsToEject(world, config, pos, player);

                    if (!list.isEmpty()) {
                        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                        stack.consume(config.keyItem().getCount(), player);
                        // CraftBukkit start
                        BlockDispenseLootEvent vaultDispenseLootEvent = CraftEventFactory.callBlockDispenseLootEvent(world, pos, player, list);
                        if (vaultDispenseLootEvent.isCancelled()) {
                            return;
                        }

                        list = vaultDispenseLootEvent.getDispensedLoot().stream().map(CraftItemStack::asNMSCopy).toList();
                        // CraftBukkit end
                        Server.unlock(world, state, pos, config, serverData, sharedData, list);
                        serverData.addToRewardedPlayers(player);
                        sharedData.updateConnectedPlayersWithinRange(world, pos, serverData, config, config.deactivationRange());
                    }
                }
            }
        }

        static void setVaultState(ServerLevel world, BlockPos pos, BlockState oldState, BlockState newState, VaultConfig config, VaultSharedData sharedData) {
            VaultState vaultstate = (VaultState) oldState.getValue(VaultBlock.STATE);
            VaultState vaultstate1 = (VaultState) newState.getValue(VaultBlock.STATE);

            world.setBlock(pos, newState, 3);
            vaultstate.onTransition(world, pos, vaultstate1, config, sharedData, (Boolean) newState.getValue(VaultBlock.OMINOUS));
        }

        static void cycleDisplayItemFromLootTable(ServerLevel world, VaultState state, VaultConfig config, VaultSharedData sharedData, BlockPos pos) {
            if (!Server.canEjectReward(config, state)) {
                sharedData.setDisplayItem(ItemStack.EMPTY);
            } else {
                ItemStack itemstack = Server.getRandomDisplayItemFromLootTable(world, pos, (ResourceKey) config.overrideLootTableToDisplay().orElse(config.lootTable()));
                // CraftBukkit start
                VaultDisplayItemEvent event = CraftEventFactory.callVaultDisplayItemEvent(world, pos, itemstack);
                if (event.isCancelled()) {
                    return;
                }

                itemstack = CraftItemStack.asNMSCopy(event.getDisplayItem());
                // CraftBukkit end

                sharedData.setDisplayItem(itemstack);
            }
        }

        private static ItemStack getRandomDisplayItemFromLootTable(ServerLevel world, BlockPos pos, ResourceKey<LootTable> lootTable) {
            LootTable loottable = world.getServer().reloadableRegistries().getLootTable(lootTable);
            LootParams lootparams = (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).create(LootContextParamSets.VAULT);
            List<ItemStack> list = loottable.getRandomItems(lootparams, world.getRandom());

            return list.isEmpty() ? ItemStack.EMPTY : (ItemStack) Util.getRandom((List) list, world.getRandom());
        }

        private static void unlock(ServerLevel world, BlockState state, BlockPos pos, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, List<ItemStack> itemsToEject) {
            serverData.setItemsToEject(itemsToEject);
            sharedData.setDisplayItem(serverData.getNextItemToEject());
            serverData.pauseStateUpdatingUntil(world.getGameTime() + 14L);
            Server.setVaultState(world, pos, state, (BlockState) state.setValue(VaultBlock.STATE, VaultState.UNLOCKING), config, sharedData);
        }

        private static List<ItemStack> resolveItemsToEject(ServerLevel world, VaultConfig config, BlockPos pos, Player player) {
            LootTable loottable = world.getServer().reloadableRegistries().getLootTable(config.lootTable());
            LootParams lootparams = (new LootParams.Builder(world)).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos)).withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.VAULT);

            return loottable.getRandomItems(lootparams);
        }

        private static boolean canEjectReward(VaultConfig config, VaultState state) {
            return config.lootTable() != BuiltInLootTables.EMPTY && !config.keyItem().isEmpty() && state != VaultState.INACTIVE;
        }

        private static boolean isValidToInsert(VaultConfig config, ItemStack stack) {
            return ItemStack.isSameItemSameComponents(stack, config.keyItem()) && stack.getCount() >= config.keyItem().getCount();
        }

        private static boolean shouldCycleDisplayItem(long time, VaultState state) {
            return time % 20L == 0L && state == VaultState.ACTIVE;
        }

        private static void playInsertFailSound(ServerLevel world, VaultServerData serverData, BlockPos pos, SoundEvent sound) {
            if (world.getGameTime() >= serverData.getLastInsertFailTimestamp() + 15L) {
                world.playSound((Player) null, pos, sound, SoundSource.BLOCKS);
                serverData.setLastInsertFailTimestamp(world.getGameTime());
            }

        }
    }
}
