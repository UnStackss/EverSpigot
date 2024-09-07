package net.minecraft.network.protocol.game;

import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.MinecartCommandBlock;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.Level;

public class ServerboundSetCommandMinecartPacket implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSetCommandMinecartPacket> STREAM_CODEC = Packet.codec(
        ServerboundSetCommandMinecartPacket::write, ServerboundSetCommandMinecartPacket::new
    );
    private final int entity;
    private final String command;
    private final boolean trackOutput;

    public ServerboundSetCommandMinecartPacket(int entityId, String command, boolean trackOutput) {
        this.entity = entityId;
        this.command = command;
        this.trackOutput = trackOutput;
    }

    private ServerboundSetCommandMinecartPacket(FriendlyByteBuf buf) {
        this.entity = buf.readVarInt();
        this.command = buf.readUtf();
        this.trackOutput = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entity);
        buf.writeUtf(this.command);
        buf.writeBoolean(this.trackOutput);
    }

    @Override
    public PacketType<ServerboundSetCommandMinecartPacket> type() {
        return GamePacketTypes.SERVERBOUND_SET_COMMAND_MINECART;
    }

    @Override
    public void handle(ServerGamePacketListener listener) {
        listener.handleSetCommandMinecart(this);
    }

    @Nullable
    public BaseCommandBlock getCommandBlock(Level world) {
        Entity entity = world.getEntity(this.entity);
        return entity instanceof MinecartCommandBlock ? ((MinecartCommandBlock)entity).getCommandBlock() : null;
    }

    public String getCommand() {
        return this.command;
    }

    public boolean isTrackOutput() {
        return this.trackOutput;
    }
}
