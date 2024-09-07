package net.minecraft.network.protocol.common.custom;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record GoalDebugPayload(int entityId, BlockPos pos, List<GoalDebugPayload.DebugGoal> goals) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, GoalDebugPayload> STREAM_CODEC = CustomPacketPayload.codec(GoalDebugPayload::write, GoalDebugPayload::new);
    public static final CustomPacketPayload.Type<GoalDebugPayload> TYPE = CustomPacketPayload.createType("debug/goal_selector");

    private GoalDebugPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readBlockPos(), buf.readList(GoalDebugPayload.DebugGoal::new));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(this.entityId);
        buf.writeBlockPos(this.pos);
        buf.writeCollection(this.goals, (bufx, goal) -> goal.write(bufx));
    }

    @Override
    public CustomPacketPayload.Type<GoalDebugPayload> type() {
        return TYPE;
    }

    public static record DebugGoal(int priority, boolean isRunning, String name) {
        public DebugGoal(FriendlyByteBuf buf) {
            this(buf.readInt(), buf.readBoolean(), buf.readUtf(255));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(this.priority);
            buf.writeBoolean(this.isRunning);
            buf.writeUtf(this.name);
        }
    }
}
