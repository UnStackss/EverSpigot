package net.minecraft.network.protocol.login;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import javax.crypto.SecretKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;

public class ServerboundKeyPacket implements Packet<ServerLoginPacketListener> {
    public static final StreamCodec<FriendlyByteBuf, ServerboundKeyPacket> STREAM_CODEC = Packet.codec(ServerboundKeyPacket::write, ServerboundKeyPacket::new);
    private final byte[] keybytes;
    private final byte[] encryptedChallenge;

    public ServerboundKeyPacket(SecretKey secretKey, PublicKey publicKey, byte[] nonce) throws CryptException {
        this.keybytes = Crypt.encryptUsingKey(publicKey, secretKey.getEncoded());
        this.encryptedChallenge = Crypt.encryptUsingKey(publicKey, nonce);
    }

    private ServerboundKeyPacket(FriendlyByteBuf buf) {
        this.keybytes = buf.readByteArray();
        this.encryptedChallenge = buf.readByteArray();
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByteArray(this.keybytes);
        buf.writeByteArray(this.encryptedChallenge);
    }

    @Override
    public PacketType<ServerboundKeyPacket> type() {
        return LoginPacketTypes.SERVERBOUND_KEY;
    }

    @Override
    public void handle(ServerLoginPacketListener listener) {
        listener.handleKey(this);
    }

    public SecretKey getSecretKey(PrivateKey privateKey) throws CryptException {
        return Crypt.decryptByteToSecretKey(privateKey, this.keybytes);
    }

    public boolean isChallengeValid(byte[] nonce, PrivateKey privateKey) {
        try {
            return Arrays.equals(nonce, Crypt.decryptUsingKey(privateKey, this.encryptedChallenge));
        } catch (CryptException var4) {
            return false;
        }
    }
}
