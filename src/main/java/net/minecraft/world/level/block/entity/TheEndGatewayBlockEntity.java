package net.minecraft.world.level.block.entity;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.EndFeatures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class TheEndGatewayBlockEntity extends TheEndPortalBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SPAWN_TIME = 200;
    private static final int COOLDOWN_TIME = 40;
    private static final int ATTENTION_INTERVAL = 2400;
    private static final int EVENT_COOLDOWN = 1;
    private static final int GATEWAY_HEIGHT_ABOVE_SURFACE = 10;
    public long age;
    private int teleportCooldown;
    @Nullable
    public BlockPos exitPortal;
    public boolean exactTeleport;

    public TheEndGatewayBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.END_GATEWAY, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putLong("Age", this.age);
        if (this.exitPortal != null) {
            nbt.put("exit_portal", NbtUtils.writeBlockPos(this.exitPortal));
        }

        if (this.exactTeleport) {
            nbt.putBoolean("ExactTeleport", true);
        }

    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.age = nbt.getLong("Age");
        NbtUtils.readBlockPos(nbt, "exit_portal").filter(Level::isInSpawnableBounds).ifPresent((blockposition) -> {
            this.exitPortal = blockposition;
        });
        this.exactTeleport = nbt.getBoolean("ExactTeleport");
    }

    public static void beamAnimationTick(Level world, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        ++blockEntity.age;
        if (blockEntity.isCoolingDown()) {
            --blockEntity.teleportCooldown;
        }

    }

    public static void portalTick(Level world, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        boolean flag = blockEntity.isSpawning();
        boolean flag1 = blockEntity.isCoolingDown();

        ++blockEntity.age;
        if (flag1) {
            --blockEntity.teleportCooldown;
        } else if (blockEntity.age % 2400L == 0L) {
            TheEndGatewayBlockEntity.triggerCooldown(world, pos, state, blockEntity);
        }

        if (flag != blockEntity.isSpawning() || flag1 != blockEntity.isCoolingDown()) {
            setChanged(world, pos, state);
        }

    }

    public boolean isSpawning() {
        return this.age < 200L;
    }

    public boolean isCoolingDown() {
        return this.teleportCooldown > 0;
    }

    public float getSpawnPercent(float tickDelta) {
        return Mth.clamp(((float) this.age + tickDelta) / 200.0F, 0.0F, 1.0F);
    }

    public float getCooldownPercent(float tickDelta) {
        return 1.0F - Mth.clamp(((float) this.teleportCooldown - tickDelta) / 40.0F, 0.0F, 1.0F);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return this.saveCustomOnly(registryLookup);
    }

    public static void triggerCooldown(Level world, BlockPos pos, BlockState state, TheEndGatewayBlockEntity blockEntity) {
        if (!world.isClientSide) {
            blockEntity.teleportCooldown = 40;
            world.blockEvent(pos, state.getBlock(), 1, 0);
            setChanged(world, pos, state);
        }

    }

    @Override
    public boolean triggerEvent(int type, int data) {
        if (type == 1) {
            this.teleportCooldown = 40;
            return true;
        } else {
            return super.triggerEvent(type, data);
        }
    }

    @Nullable
    public Vec3 getPortalPosition(ServerLevel world, BlockPos pos) {
        BlockPos blockposition1;

        if (this.exitPortal == null && world.getTypeKey() == LevelStem.END) { // CraftBukkit - work in alternate worlds
            blockposition1 = TheEndGatewayBlockEntity.findOrCreateValidTeleportPos(world, pos);
            blockposition1 = blockposition1.above(10);
            TheEndGatewayBlockEntity.LOGGER.debug("Creating portal at {}", blockposition1);
            TheEndGatewayBlockEntity.spawnGatewayPortal(world, blockposition1, EndGatewayConfiguration.knownExit(pos, false));
            this.setExitPosition(blockposition1, this.exactTeleport);
        }

        if (this.exitPortal != null) {
            blockposition1 = this.exactTeleport ? this.exitPortal : TheEndGatewayBlockEntity.findExitPosition(world, this.exitPortal);
            return blockposition1.getBottomCenter();
        } else {
            return null;
        }
    }

    private static BlockPos findExitPosition(Level world, BlockPos pos) {
        BlockPos blockposition1 = TheEndGatewayBlockEntity.findTallestBlock(world, pos.offset(0, 2, 0), 5, false);

        TheEndGatewayBlockEntity.LOGGER.debug("Best exit position for portal at {} is {}", pos, blockposition1);
        return blockposition1.above();
    }

    private static BlockPos findOrCreateValidTeleportPos(ServerLevel world, BlockPos pos) {
        Vec3 vec3d = TheEndGatewayBlockEntity.findExitPortalXZPosTentative(world, pos);
        LevelChunk chunk = TheEndGatewayBlockEntity.getChunk(world, vec3d);
        BlockPos blockposition1 = TheEndGatewayBlockEntity.findValidSpawnInChunk(chunk);

        if (blockposition1 == null) {
            BlockPos blockposition2 = BlockPos.containing(vec3d.x + 0.5D, 75.0D, vec3d.z + 0.5D);

            TheEndGatewayBlockEntity.LOGGER.debug("Failed to find a suitable block to teleport to, spawning an island on {}", blockposition2);
            world.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap((iregistry) -> {
                return iregistry.getHolder(EndFeatures.END_ISLAND);
            }).ifPresent((holder_c) -> {
                ((ConfiguredFeature) holder_c.value()).place(world, world.getChunkSource().getGenerator(), RandomSource.create(blockposition2.asLong()), blockposition2);
            });
            blockposition1 = blockposition2;
        } else {
            TheEndGatewayBlockEntity.LOGGER.debug("Found suitable block to teleport to: {}", blockposition1);
        }

        return TheEndGatewayBlockEntity.findTallestBlock(world, blockposition1, 16, true);
    }

    private static Vec3 findExitPortalXZPosTentative(ServerLevel world, BlockPos pos) {
        Vec3 vec3d = (new Vec3((double) pos.getX(), 0.0D, (double) pos.getZ())).normalize();
        boolean flag = true;
        Vec3 vec3d1 = vec3d.scale(1024.0D);

        int i;

        for (i = 16; !TheEndGatewayBlockEntity.isChunkEmpty(world, vec3d1) && i-- > 0; vec3d1 = vec3d1.add(vec3d.scale(-16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping backwards past nonempty chunk at {}", vec3d1);
        }

        for (i = 16; TheEndGatewayBlockEntity.isChunkEmpty(world, vec3d1) && i-- > 0; vec3d1 = vec3d1.add(vec3d.scale(16.0D))) {
            TheEndGatewayBlockEntity.LOGGER.debug("Skipping forward past empty chunk at {}", vec3d1);
        }

        TheEndGatewayBlockEntity.LOGGER.debug("Found chunk at {}", vec3d1);
        return vec3d1;
    }

    private static boolean isChunkEmpty(ServerLevel world, Vec3 pos) {
        return TheEndGatewayBlockEntity.getChunk(world, pos).getHighestFilledSectionIndex() == -1;
    }

    private static BlockPos findTallestBlock(BlockGetter world, BlockPos pos, int searchRadius, boolean force) {
        BlockPos blockposition1 = null;

        for (int j = -searchRadius; j <= searchRadius; ++j) {
            for (int k = -searchRadius; k <= searchRadius; ++k) {
                if (j != 0 || k != 0 || force) {
                    for (int l = world.getMaxBuildHeight() - 1; l > (blockposition1 == null ? world.getMinBuildHeight() : blockposition1.getY()); --l) {
                        BlockPos blockposition2 = new BlockPos(pos.getX() + j, l, pos.getZ() + k);
                        BlockState iblockdata = world.getBlockState(blockposition2);

                        if (iblockdata.isCollisionShapeFullBlock(world, blockposition2) && (force || !iblockdata.is(Blocks.BEDROCK))) {
                            blockposition1 = blockposition2;
                            break;
                        }
                    }
                }
            }
        }

        return blockposition1 == null ? pos : blockposition1;
    }

    private static LevelChunk getChunk(Level world, Vec3 pos) {
        return world.getChunk(Mth.floor(pos.x / 16.0D), Mth.floor(pos.z / 16.0D));
    }

    @Nullable
    private static BlockPos findValidSpawnInChunk(LevelChunk chunk) {
        ChunkPos chunkcoordintpair = chunk.getPos();
        BlockPos blockposition = new BlockPos(chunkcoordintpair.getMinBlockX(), 30, chunkcoordintpair.getMinBlockZ());
        int i = chunk.getHighestSectionPosition() + 16 - 1;
        BlockPos blockposition1 = new BlockPos(chunkcoordintpair.getMaxBlockX(), i, chunkcoordintpair.getMaxBlockZ());
        BlockPos blockposition2 = null;
        double d0 = 0.0D;
        Iterator iterator = BlockPos.betweenClosed(blockposition, blockposition1).iterator();

        while (iterator.hasNext()) {
            BlockPos blockposition3 = (BlockPos) iterator.next();
            BlockState iblockdata = chunk.getBlockState(blockposition3);
            BlockPos blockposition4 = blockposition3.above();
            BlockPos blockposition5 = blockposition3.above(2);

            if (iblockdata.is(Blocks.END_STONE) && !chunk.getBlockState(blockposition4).isCollisionShapeFullBlock(chunk, blockposition4) && !chunk.getBlockState(blockposition5).isCollisionShapeFullBlock(chunk, blockposition5)) {
                double d1 = blockposition3.distToCenterSqr(0.0D, 0.0D, 0.0D);

                if (blockposition2 == null || d1 < d0) {
                    blockposition2 = blockposition3;
                    d0 = d1;
                }
            }
        }

        return blockposition2;
    }

    private static void spawnGatewayPortal(ServerLevel world, BlockPos pos, EndGatewayConfiguration config) {
        Feature.END_GATEWAY.place(config, world, world.getChunkSource().getGenerator(), RandomSource.create(), pos);
    }

    @Override
    public boolean shouldRenderFace(Direction direction) {
        return Block.shouldRenderFace(this.getBlockState(), this.level, this.getBlockPos(), direction, this.getBlockPos().relative(direction));
    }

    public int getParticleAmount() {
        int i = 0;
        Direction[] aenumdirection = Direction.values();
        int j = aenumdirection.length;

        for (int k = 0; k < j; ++k) {
            Direction enumdirection = aenumdirection[k];

            i += this.shouldRenderFace(enumdirection) ? 1 : 0;
        }

        return i;
    }

    public void setExitPosition(BlockPos pos, boolean exactTeleport) {
        this.exactTeleport = exactTeleport;
        this.exitPortal = pos;
        this.setChanged();
    }
}
