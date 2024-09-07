package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.portal.DimensionTransition;

public class PortalProcessor {
    private Portal portal;
    private BlockPos entryPosition;
    private int portalTime;
    private boolean insidePortalThisTick;

    public PortalProcessor(Portal portal, BlockPos pos) {
        this.portal = portal;
        this.entryPosition = pos;
        this.insidePortalThisTick = true;
    }

    public boolean processPortalTeleportation(ServerLevel world, Entity entity, boolean canUsePortals) {
        if (!this.insidePortalThisTick) {
            this.decayTick();
            return false;
        } else {
            this.insidePortalThisTick = false;
            return canUsePortals && this.portalTime++ >= this.portal.getPortalTransitionTime(world, entity);
        }
    }

    @Nullable
    public DimensionTransition getPortalDestination(ServerLevel world, Entity entity) {
        return this.portal.getPortalDestination(world, entity, this.entryPosition);
    }

    public Portal.Transition getPortalLocalTransition() {
        return this.portal.getLocalTransition();
    }

    private void decayTick() {
        this.portalTime = Math.max(this.portalTime - 4, 0);
    }

    public boolean hasExpired() {
        return this.portalTime <= 0;
    }

    public BlockPos getEntryPosition() {
        return this.entryPosition;
    }

    public void updateEntryPosition(BlockPos pos) {
        this.entryPosition = pos;
    }

    public int getPortalTime() {
        return this.portalTime;
    }

    public boolean isInsidePortalThisTick() {
        return this.insidePortalThisTick;
    }

    public void setAsInsidePortalThisTick(boolean inPortal) {
        this.insidePortalThisTick = inPortal;
    }

    public boolean isSamePortal(Portal portal) {
        return this.portal == portal;
    }
}
