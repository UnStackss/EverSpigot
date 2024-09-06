package net.minecraft.world.level.block.entity;

import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

public abstract class ContainerOpenersCounter {

    private static final int CHECK_TICK_DELAY = 5;
    private int openCount;
    private double maxInteractionRange;
    public boolean opened; // CraftBukkit

    public ContainerOpenersCounter() {}

    protected abstract void onOpen(Level world, BlockPos pos, BlockState state);

    protected abstract void onClose(Level world, BlockPos pos, BlockState state);

    protected abstract void openerCountChanged(Level world, BlockPos pos, BlockState state, int oldViewerCount, int newViewerCount);

    // CraftBukkit start
    public void onAPIOpen(Level world, BlockPos blockposition, BlockState iblockdata) {
        this.onOpen(world, blockposition, iblockdata);
    }

    public void onAPIClose(Level world, BlockPos blockposition, BlockState iblockdata) {
        this.onClose(world, blockposition, iblockdata);
    }

    public void openerAPICountChanged(Level world, BlockPos blockposition, BlockState iblockdata, int i, int j) {
        this.openerCountChanged(world, blockposition, iblockdata, i, j);
    }
    // CraftBukkit end

    protected abstract boolean isOwnContainer(Player player);

    public void incrementOpeners(Player player, Level world, BlockPos pos, BlockState state) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        int i = this.openCount++;

        // CraftBukkit start - Call redstone event
        if (world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (i == 0) {
            this.onOpen(world, pos, state);
            world.gameEvent((Entity) player, (Holder) GameEvent.CONTAINER_OPEN, pos);
            ContainerOpenersCounter.scheduleRecheck(world, pos, state);
        }

        this.openerCountChanged(world, pos, state, i, this.openCount);
        this.maxInteractionRange = Math.max(player.blockInteractionRange(), this.maxInteractionRange);
    }

    public void decrementOpeners(Player player, Level world, BlockPos pos, BlockState state) {
        int oldPower = Math.max(0, Math.min(15, this.openCount)); // CraftBukkit - Get power before new viewer is added
        int i = this.openCount--;

        // CraftBukkit start - Call redstone event
        if (world.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.TRAPPED_CHEST)) {
            int newPower = Math.max(0, Math.min(15, this.openCount));

            if (oldPower != newPower) {
                org.bukkit.craftbukkit.event.CraftEventFactory.callRedstoneChange(world, pos, oldPower, newPower);
            }
        }
        // CraftBukkit end

        if (this.openCount == 0) {
            this.onClose(world, pos, state);
            world.gameEvent((Entity) player, (Holder) GameEvent.CONTAINER_CLOSE, pos);
            this.maxInteractionRange = 0.0D;
        }

        this.openerCountChanged(world, pos, state, i, this.openCount);
    }

    private List<Player> getPlayersWithContainerOpen(Level world, BlockPos pos) {
        double d0 = this.maxInteractionRange + 4.0D;
        AABB axisalignedbb = (new AABB(pos)).inflate(d0);

        return world.getEntities(EntityTypeTest.forClass(Player.class), axisalignedbb, this::isOwnContainer);
    }

    public void recheckOpeners(Level world, BlockPos pos, BlockState state) {
        List<Player> list = this.getPlayersWithContainerOpen(world, pos);

        this.maxInteractionRange = 0.0D;

        Player entityhuman;

        for (Iterator iterator = list.iterator(); iterator.hasNext(); this.maxInteractionRange = Math.max(entityhuman.blockInteractionRange(), this.maxInteractionRange)) {
            entityhuman = (Player) iterator.next();
        }

        int i = list.size();
        if (this.opened) i++; // CraftBukkit - add dummy count from API
        int j = this.openCount;

        if (j != i) {
            boolean flag = i != 0;
            boolean flag1 = j != 0;

            if (flag && !flag1) {
                this.onOpen(world, pos, state);
                world.gameEvent((Entity) null, (Holder) GameEvent.CONTAINER_OPEN, pos);
            } else if (!flag) {
                this.onClose(world, pos, state);
                world.gameEvent((Entity) null, (Holder) GameEvent.CONTAINER_CLOSE, pos);
            }

            this.openCount = i;
        }

        this.openerCountChanged(world, pos, state, j, i);
        if (i > 0) {
            ContainerOpenersCounter.scheduleRecheck(world, pos, state);
        }

    }

    public int getOpenerCount() {
        return this.openCount;
    }

    private static void scheduleRecheck(Level world, BlockPos pos, BlockState state) {
        world.scheduleTick(pos, state.getBlock(), 5);
    }
}
