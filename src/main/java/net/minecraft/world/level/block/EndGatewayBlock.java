package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.player.PlayerTeleportEvent;
// CraftBukkit end

public class EndGatewayBlock extends BaseEntityBlock implements Portal {

    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return EndGatewayBlock.CODEC;
    }

    protected EndGatewayBlock(BlockBehaviour.Properties settings) {
        super(settings);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TheEndGatewayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntityType.END_GATEWAY, world.isClientSide ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick);
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof TheEndGatewayBlockEntity) {
            int i = ((TheEndGatewayBlockEntity) tileentity).getParticleAmount();

            for (int j = 0; j < i; ++j) {
                double d0 = (double) pos.getX() + random.nextDouble();
                double d1 = (double) pos.getY() + random.nextDouble();
                double d2 = (double) pos.getZ() + random.nextDouble();
                double d3 = (random.nextDouble() - 0.5D) * 0.5D;
                double d4 = (random.nextDouble() - 0.5D) * 0.5D;
                double d5 = (random.nextDouble() - 0.5D) * 0.5D;
                int k = random.nextInt(2) * 2 - 1;

                if (random.nextBoolean()) {
                    d2 = (double) pos.getZ() + 0.5D + 0.25D * (double) k;
                    d5 = (double) (random.nextFloat() * 2.0F * (float) k);
                } else {
                    d0 = (double) pos.getX() + 0.5D + 0.25D * (double) k;
                    d3 = (double) (random.nextFloat() * 2.0F * (float) k);
                }

                world.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
            }

        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!new io.papermc.paper.event.entity.EntityInsideBlockEvent(entity.getBukkitEntity(), org.bukkit.craftbukkit.block.CraftBlock.at(world, pos)).callEvent()) { return; } // Paper - Add EntityInsideBlockEvent
        if (entity.canUsePortal(false)) {
            // Paper start - call EntityPortalEnterEvent
            org.bukkit.event.entity.EntityPortalEnterEvent event = new org.bukkit.event.entity.EntityPortalEnterEvent(entity.getBukkitEntity(), new org.bukkit.Location(world.getWorld(), pos.getX(), pos.getY(), pos.getZ()), org.bukkit.PortalType.END_GATEWAY); // Paper - add portal type
            if (!event.callEvent()) return;
            // Paper end - call EntityPortalEnterEvent
            BlockEntity tileentity = world.getBlockEntity(pos);

            if (!world.isClientSide && tileentity instanceof TheEndGatewayBlockEntity) {
                TheEndGatewayBlockEntity tileentityendgateway = (TheEndGatewayBlockEntity) tileentity;

                if (!tileentityendgateway.isCoolingDown()) {
                    entity.setAsInsidePortal(this, pos);
                    TheEndGatewayBlockEntity.triggerCooldown(world, pos, state, tileentityendgateway);
                }
            }
        }

    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel world, Entity entity, BlockPos pos) {
        BlockEntity tileentity = world.getBlockEntity(pos);

        if (tileentity instanceof TheEndGatewayBlockEntity tileentityendgateway) {
            Vec3 vec3d = tileentityendgateway.getPortalPosition(world, pos);

            return vec3d != null ? new DimensionTransition(world, vec3d, EndGatewayBlock.calculateExitMovement(entity), entity.getYRot(), entity.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET, PlayerTeleportEvent.TeleportCause.END_GATEWAY) : null; // CraftBukkit
        } else {
            return null;
        }
    }

    private static Vec3 calculateExitMovement(Entity entity) {
        return entity instanceof ThrownEnderpearl ? new Vec3(0.0D, -1.0D, 0.0D) : entity.getDeltaMovement();
    }
}
