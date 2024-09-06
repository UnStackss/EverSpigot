package net.minecraft.server;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import net.minecraft.network.protocol.game.ClientboundTickingStepPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.world.TickRateManager;

public class ServerTickRateManager extends TickRateManager {

    private long remainingSprintTicks = 0L;
    private long sprintTickStartTime = 0L;
    private long sprintTimeSpend = 0L;
    private long scheduledCurrentSprintTicks = 0L;
    private boolean previousIsFrozen = false;
    private final MinecraftServer server;

    public ServerTickRateManager(MinecraftServer server) {
        this.server = server;
    }

    public boolean isSprinting() {
        return this.scheduledCurrentSprintTicks > 0L;
    }

    @Override
    public void setFrozen(boolean frozen) {
        super.setFrozen(frozen);
        this.updateStateToClients();
    }

    private void updateStateToClients() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStatePacket.from(this));
    }

    private void updateStepTicks() {
        this.server.getPlayerList().broadcastAll(ClientboundTickingStepPacket.from(this));
    }

    public boolean stepGameIfPaused(int ticks) {
        if (!this.isFrozen()) {
            return false;
        } else {
            this.frozenTicksToRun = ticks;
            this.updateStepTicks();
            return true;
        }
    }

    public boolean stopStepping() {
        if (this.frozenTicksToRun > 0) {
            this.frozenTicksToRun = 0;
            this.updateStepTicks();
            return true;
        } else {
            return false;
        }
    }

    public boolean stopSprinting() {
        // CraftBukkit start, add sendLog parameter
        return this.stopSprinting(true);
    }

    public boolean stopSprinting(boolean sendLog) {
        // CraftBukkit end
        if (this.remainingSprintTicks > 0L) {
            this.finishTickSprint(sendLog); // CraftBukkit - add sendLog parameter
            return true;
        } else {
            return false;
        }
    }

    public boolean requestGameToSprint(int ticks) {
        boolean flag = this.remainingSprintTicks > 0L;

        this.sprintTimeSpend = 0L;
        this.scheduledCurrentSprintTicks = (long) ticks;
        this.remainingSprintTicks = (long) ticks;
        this.previousIsFrozen = this.isFrozen();
        this.setFrozen(false);
        return flag;
    }

    private void finishTickSprint(boolean sendLog) { // CraftBukkit - add sendLog parameter
        long i = this.scheduledCurrentSprintTicks - this.remainingSprintTicks;
        double d0 = Math.max(1.0D, (double) this.sprintTimeSpend) / (double) TimeUtil.NANOSECONDS_PER_MILLISECOND;
        int j = (int) ((double) (TimeUtil.MILLISECONDS_PER_SECOND * i) / d0);
        String s = String.format("%.2f", i == 0L ? (double) this.millisecondsPerTick() : d0 / (double) i);

        this.scheduledCurrentSprintTicks = 0L;
        this.sprintTimeSpend = 0L;
        // CraftBukkit start - add sendLog parameter
        if (sendLog) {
            this.server.createCommandSourceStack().sendSuccess(() -> {
                return Component.translatable("commands.tick.sprint.report", j, s);
            }, true);
        }
        // CraftBukkit end
        this.remainingSprintTicks = 0L;
        this.setFrozen(this.previousIsFrozen);
        this.server.onTickRateChanged();
    }

    public boolean checkShouldSprintThisTick() {
        if (!this.runGameElements) {
            return false;
        } else if (this.remainingSprintTicks > 0L) {
            this.sprintTickStartTime = System.nanoTime();
            --this.remainingSprintTicks;
            return true;
        } else {
            this.finishTickSprint(true); // CraftBukkit - add sendLog parameter
            return false;
        }
    }

    public void endTickWork() {
        this.sprintTimeSpend += System.nanoTime() - this.sprintTickStartTime;
    }

    @Override
    public void setTickRate(float tickRate) {
        super.setTickRate(tickRate);
        this.server.onTickRateChanged();
        this.updateStateToClients();
    }

    public void updateJoiningPlayer(ServerPlayer player) {
        player.connection.send(ClientboundTickingStatePacket.from(this));
        player.connection.send(ClientboundTickingStepPacket.from(this));
    }
}
