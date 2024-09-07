package net.minecraft.network.protocol.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;

public class ServerboundPlayerActionPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundPlayerActionPacket> STREAM_CODEC = Packet.codec(
        ServerboundPlayerActionPacket::write, ServerboundPlayerActionPacket::new
    );
    private final BlockPos pos;
    private final Direction direction;
    private final ServerboundPlayerActionPacket.Action action;
    private final int sequence;

    public ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction direction, int sequence) {
        this.action = action;
        this.pos = pos.immutable();
        this.direction = direction;
        this.sequence = sequence;
    }

    public ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action action, BlockPos pos, Direction direction) {
        this(action, pos, direction, 0);
    }

    private ServerboundPlayerActionPacket(FriendlyByteBuf buf) {
        this.action = buf.readEnum(ServerboundPlayerActionPacket.Action.class);
        this.pos = buf.readBlockPos();
        this.direction = Direction.from3DDataValue(buf.readUnsignedByte());
        this.sequence = buf.readVarInt();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.direction.get3DDataValue());
        buf.writeVarInt(this.sequence);
    }

    @Override
    public PacketType<ServerboundPlayerActionPacket> type() {
        return GamePacketTypes.SERVERBOUND_PLAYER_ACTION;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handlePlayerAction(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Direction getDirection() {
        return this.direction;
    }

    public ServerboundPlayerActionPacket.Action getAction() {
        return this.action;
    }

    public int getSequence() {
        return this.sequence;
    }

    public static enum Action {
        START_DESTROY_BLOCK,
        ABORT_DESTROY_BLOCK,
        STOP_DESTROY_BLOCK,
        DROP_ALL_ITEMS,
        DROP_ITEM,
        RELEASE_USE_ITEM,
        SWAP_ITEM_WITH_OFFHAND;
    }
}
