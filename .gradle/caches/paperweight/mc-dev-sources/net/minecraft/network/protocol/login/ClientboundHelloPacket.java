package net.minecraft.network.protocol.login;

import java.security.PublicKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;

public class ClientboundHelloPacket implements Packet<ClientLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ClientboundHelloPacket> STREAM_CODEC = Packet.codec(
        ClientboundHelloPacket::write, ClientboundHelloPacket::new
    );
    private final String serverId;
    private final byte[] publicKey;
    private final byte[] challenge;
    private final boolean shouldAuthenticate;

    public ClientboundHelloPacket(String serverId, byte[] publicKey, byte[] nonce, boolean needsAuthentication) {
        this.serverId = serverId;
        this.publicKey = publicKey;
        this.challenge = nonce;
        this.shouldAuthenticate = needsAuthentication;
    }

    private ClientboundHelloPacket(FriendlyByteBuf buf) {
        this.serverId = buf.readUtf(20);
        this.publicKey = buf.readByteArray();
        this.challenge = buf.readByteArray();
        this.shouldAuthenticate = buf.readBoolean();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(this.serverId);
        buf.writeByteArray(this.publicKey);
        buf.writeByteArray(this.challenge);
        buf.writeBoolean(this.shouldAuthenticate);
    }

    @Override
    public PacketType<ClientboundHelloPacket> type() {
        return LoginPacketTypes.CLIENTBOUND_HELLO;
    }

    @Override
    public void handle(ClientLoginPacketListener listener) {
        listener.handleHello(this);
    }

    public String getServerId() {
        return this.serverId;
    }

    public PublicKey getPublicKey() throws CryptException {
        return Crypt.byteToPublicKey(this.publicKey);
    }

    public byte[] getChallenge() {
        return this.challenge;
    }

    public boolean shouldAuthenticate() {
        return this.shouldAuthenticate;
    }
}
