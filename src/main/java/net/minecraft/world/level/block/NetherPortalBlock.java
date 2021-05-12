package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.event.CraftPortalEvent;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class NetherPortalBlock extends Block implements Portal {

    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(NetherPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final int AABB_OFFSET = 2;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return NetherPortalBlock.CODEC;
    }

    public NetherPortalBlock(BlockBehaviour.Properties settings) {
        super(settings);
        this.registerDefaultState((BlockState) ((BlockState) this.stateDefinition.any()).setValue(NetherPortalBlock.AXIS, Direction.Axis.X));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        switch ((Direction.Axis) state.getValue(NetherPortalBlock.AXIS)) {
            case Z:
                return NetherPortalBlock.Z_AXIS_AABB;
            case X:
            default:
                return NetherPortalBlock.X_AXIS_AABB;
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (world.spigotConfig.enableZombiePigmenPortalSpawns && world.dimensionType().natural() && world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && random.nextInt(2000) < world.getDifficulty().getId()) { // Spigot
            while (world.getBlockState(pos).is((Block) this)) {
                pos = pos.below();
            }

            if (world.getBlockState(pos).isValidSpawn(world, pos, EntityType.ZOMBIFIED_PIGLIN)) {
                // CraftBukkit - set spawn reason to NETHER_PORTAL
                Entity entity = EntityType.ZOMBIFIED_PIGLIN.spawn(world, pos.above(), MobSpawnType.STRUCTURE, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NETHER_PORTAL);

                if (entity != null) {
                    entity.setPortalCooldown();
                    entity.fromNetherPortal = true; // Paper - Add option to nerf pigmen from nether portals
                    if (world.paperConfig().entities.behavior.nerfPigmenFromNetherPortals) ((net.minecraft.world.entity.Mob) entity).aware = false; // Paper - Add option to nerf pigmen from nether portals
                }
            }
        }

    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis enumdirection_enumaxis = direction.getAxis();
        Direction.Axis enumdirection_enumaxis1 = (Direction.Axis) state.getValue(NetherPortalBlock.AXIS);
        boolean flag = enumdirection_enumaxis1 != enumdirection_enumaxis && enumdirection_enumaxis.isHorizontal();

        return !flag && !neighborState.is((Block) this) && !(new PortalShape(world, pos, enumdirection_enumaxis1)).isComplete() ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)) {
            // CraftBukkit start - Entity in portal
            EntityPortalEnterEvent event = new EntityPortalEnterEvent(entity.getBukkitEntity(), new org.bukkit.Location(world.getWorld(), pos.getX(), pos.getY(), pos.getZ()));
            world.getCraftServer().getPluginManager().callEvent(event);
            // CraftBukkit end
            entity.setAsInsidePortal(this, pos);
        }

    }

    @Override
    public int getPortalTransitionTime(ServerLevel world, Entity entity) {
        if (entity instanceof Player entityhuman) {
            return Math.max(1, world.getGameRules().getInt(entityhuman.getAbilities().invulnerable ? GameRules.RULE_PLAYERS_NETHER_PORTAL_CREATIVE_DELAY : GameRules.RULE_PLAYERS_NETHER_PORTAL_DEFAULT_DELAY));
        } else {
            return 0;
        }
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel world, Entity entity, BlockPos pos) {
        // CraftBukkit start
        ResourceKey<Level> resourcekey = world.getTypeKey() == LevelStem.NETHER ? Level.OVERWORLD : Level.NETHER;
        ServerLevel worldserver1 = world.getServer().getLevel(resourcekey);
        // Paper start - Add EntityPortalReadyEvent
        io.papermc.paper.event.entity.EntityPortalReadyEvent portalReadyEvent = new io.papermc.paper.event.entity.EntityPortalReadyEvent(entity.getBukkitEntity(), worldserver1 == null ? null : worldserver1.getWorld(), org.bukkit.PortalType.NETHER);
        if (!portalReadyEvent.callEvent()) {
            entity.portalProcess = null;
            return null;
        }
        worldserver1 = portalReadyEvent.getTargetWorld() == null ? null : ((org.bukkit.craftbukkit.CraftWorld) portalReadyEvent.getTargetWorld()).getHandle();
        // Paper end - Add EntityPortalReadyEvent

        if (worldserver1 == null) {
            return new DimensionTransition(PlayerTeleportEvent.TeleportCause.NETHER_PORTAL); // always fire event in case plugins wish to change it
        } else {
            boolean flag = worldserver1.getTypeKey() == LevelStem.NETHER;
            // CraftBukkit end
            WorldBorder worldborder = worldserver1.getWorldBorder();
            double d0 = DimensionType.getTeleportationScale(world.dimensionType(), worldserver1.dimensionType());
            BlockPos blockposition1 = worldborder.clampToBounds(entity.getX() * d0, entity.getY(), entity.getZ() * d0);
            // Paper start - Configurable portal search radius
            int portalSearchRadius = worldserver1.paperConfig().environment.portalSearchRadius;
            if (entity.level().paperConfig().environment.portalSearchVanillaDimensionScaling && flag) { // flag = is going to nether
                portalSearchRadius = (int) (portalSearchRadius / worldserver1.dimensionType().coordinateScale());
            }
            // Paper end - Configurable portal search radius
            // CraftBukkit start
            CraftPortalEvent event = entity.callPortalEvent(entity, CraftLocation.toBukkit(blockposition1, worldserver1.getWorld()), PlayerTeleportEvent.TeleportCause.NETHER_PORTAL, portalSearchRadius, worldserver1.paperConfig().environment.portalCreateRadius); // Paper - use custom portal search radius
            if (event == null) {
                return null;
            }
            worldserver1 = ((CraftWorld) event.getTo().getWorld()).getHandle();
            worldborder = worldserver1.getWorldBorder();
            blockposition1 = worldborder.clampToBounds(event.getTo().getX(), event.getTo().getY(), event.getTo().getZ());

            return this.getExitPortal(worldserver1, entity, pos, blockposition1, flag, worldborder, event.getSearchRadius(), event.getCanCreatePortal(), event.getCreationRadius());
        }
    }

    @Nullable
    private DimensionTransition getExitPortal(ServerLevel worldserver, Entity entity, BlockPos blockposition, BlockPos blockposition1, boolean flag, WorldBorder worldborder, int searchRadius, boolean canCreatePortal, int createRadius) {
        Optional<BlockPos> optional = worldserver.getPortalForcer().findClosestPortalPosition(blockposition1, worldborder, searchRadius);
        BlockUtil.FoundRectangle blockutil_rectangle;
        DimensionTransition.PostDimensionTransition dimensiontransition_a;

        if (optional.isPresent()) {
            BlockPos blockposition2 = (BlockPos) optional.get();
            BlockState iblockdata = worldserver.getBlockState(blockposition2);

            blockutil_rectangle = BlockUtil.getLargestRectangleAround(blockposition2, (Direction.Axis) iblockdata.getValue(BlockStateProperties.HORIZONTAL_AXIS), 21, Direction.Axis.Y, 21, (blockposition3) -> {
                return worldserver.getBlockState(blockposition3) == iblockdata;
            });
            dimensiontransition_a = DimensionTransition.PLAY_PORTAL_SOUND.then((entity1) -> {
                entity1.placePortalTicket(blockposition2);
            });
        } else if (canCreatePortal) {
            Direction.Axis enumdirection_enumaxis = (Direction.Axis) entity.level().getBlockState(blockposition).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X);
            Optional<BlockUtil.FoundRectangle> optional1 = worldserver.getPortalForcer().createPortal(blockposition1, enumdirection_enumaxis, entity, createRadius);
            // CraftBukkit end

            if (optional1.isEmpty()) {
                // BlockPortal.LOGGER.error("Unable to create a portal, likely target out of worldborder"); // CraftBukkit
                return null;
            }

            blockutil_rectangle = (BlockUtil.FoundRectangle) optional1.get();
            dimensiontransition_a = DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET);
            // CraftBukkit start
        } else {
            return null;
            // CraftBukkit end
        }

        return NetherPortalBlock.getDimensionTransitionFromExit(entity, blockposition, blockutil_rectangle, worldserver, dimensiontransition_a);
    }

    private static DimensionTransition getDimensionTransitionFromExit(Entity entity, BlockPos pos, BlockUtil.FoundRectangle exitPortalRectangle, ServerLevel world, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        BlockState iblockdata = entity.level().getBlockState(pos);
        Direction.Axis enumdirection_enumaxis;
        Vec3 vec3d;

        if (iblockdata.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            enumdirection_enumaxis = (Direction.Axis) iblockdata.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle blockutil_rectangle1 = BlockUtil.getLargestRectangleAround(pos, enumdirection_enumaxis, 21, Direction.Axis.Y, 21, (blockposition1) -> {
                return entity.level().getBlockState(blockposition1) == iblockdata;
            });

            vec3d = entity.getRelativePortalPosition(enumdirection_enumaxis, blockutil_rectangle1);
        } else {
            enumdirection_enumaxis = Direction.Axis.X;
            vec3d = new Vec3(0.5D, 0.0D, 0.0D);
        }

        return NetherPortalBlock.createDimensionTransition(world, exitPortalRectangle, enumdirection_enumaxis, vec3d, entity, entity.getDeltaMovement(), entity.getYRot(), entity.getXRot(), postDimensionTransition);
    }

    private static DimensionTransition createDimensionTransition(ServerLevel world, BlockUtil.FoundRectangle exitPortalRectangle, Direction.Axis axis, Vec3 positionInPortal, Entity entity, Vec3 velocity, float yaw, float pitch, DimensionTransition.PostDimensionTransition postDimensionTransition) {
        BlockPos blockposition = exitPortalRectangle.minCorner;
        BlockState iblockdata = world.getBlockState(blockposition);
        Direction.Axis enumdirection_enumaxis1 = (Direction.Axis) iblockdata.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Direction.Axis.X);
        double d0 = (double) exitPortalRectangle.axis1Size;
        double d1 = (double) exitPortalRectangle.axis2Size;
        EntityDimensions entitysize = entity.getDimensions(entity.getPose());
        int i = axis == enumdirection_enumaxis1 ? 0 : 90;
        Vec3 vec3d2 = axis == enumdirection_enumaxis1 ? velocity : new Vec3(velocity.z, velocity.y, -velocity.x);
        double d2 = (double) entitysize.width() / 2.0D + (d0 - (double) entitysize.width()) * positionInPortal.x();
        double d3 = (d1 - (double) entitysize.height()) * positionInPortal.y();
        double d4 = 0.5D + positionInPortal.z();
        boolean flag = enumdirection_enumaxis1 == Direction.Axis.X;
        Vec3 vec3d3 = new Vec3((double) blockposition.getX() + (flag ? d2 : d4), (double) blockposition.getY() + d3, (double) blockposition.getZ() + (flag ? d4 : d2));
        Vec3 vec3d4 = PortalShape.findCollisionFreePosition(vec3d3, world, entity, entitysize);

        return new DimensionTransition(world, vec3d4, vec3d2, yaw + (float) i, pitch, postDimensionTransition, PlayerTeleportEvent.TeleportCause.NETHER_PORTAL); // CraftBukkit
    }

    @Override
    public Portal.Transition getLocalTransition() {
        return Portal.Transition.CONFUSION;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            world.playLocalSound((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; ++i) {
            double d0 = (double) pos.getX() + random.nextDouble();
            double d1 = (double) pos.getY() + random.nextDouble();
            double d2 = (double) pos.getZ() + random.nextDouble();
            double d3 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            double d4 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            double d5 = ((double) random.nextFloat() - 0.5D) * 0.5D;
            int j = random.nextInt(2) * 2 - 1;

            if (!world.getBlockState(pos.west()).is((Block) this) && !world.getBlockState(pos.east()).is((Block) this)) {
                d0 = (double) pos.getX() + 0.5D + 0.25D * (double) j;
                d3 = (double) (random.nextFloat() * 2.0F * (float) j);
            } else {
                d2 = (double) pos.getZ() + 0.5D + 0.25D * (double) j;
                d5 = (double) (random.nextFloat() * 2.0F * (float) j);
            }

            world.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
        }

    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        switch (rotation) {
            case COUNTERCLOCKWISE_90:
            case CLOCKWISE_90:
                switch ((Direction.Axis) state.getValue(NetherPortalBlock.AXIS)) {
                    case Z:
                        return (BlockState) state.setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
                    case X:
                        return (BlockState) state.setValue(NetherPortalBlock.AXIS, Direction.Axis.Z);
                    default:
                        return state;
                }
            default:
                return state;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NetherPortalBlock.AXIS);
    }
}
