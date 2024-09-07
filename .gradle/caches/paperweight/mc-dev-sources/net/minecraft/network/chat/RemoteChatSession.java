package net.minecraft.network.chat;

import com.mojang.authlib.GameProfile;
import java.time.Duration;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.SignatureValidator;
import net.minecraft.world.entity.player.ProfilePublicKey;

public record RemoteChatSession(UUID sessionId, ProfilePublicKey profilePublicKey) {
    public SignedMessageValidator createMessageValidator(Duration gracePeriod) {
        return new SignedMessageValidator.KeyBased(this.profilePublicKey.createSignatureValidator(), () -> this.profilePublicKey.data().hasExpired(gracePeriod));
    }

    public SignedMessageChain.Decoder createMessageDecoder(UUID sender) {
        return new SignedMessageChain(sender, this.sessionId).decoder(this.profilePublicKey);
    }

    public RemoteChatSession.Data asData() {
        return new RemoteChatSession.Data(this.sessionId, this.profilePublicKey.data());
    }

    public boolean hasExpired() {
        return this.profilePublicKey.data().hasExpired();
    }

    public static record Data(UUID sessionId, ProfilePublicKey.Data profilePublicKey) {
        public static RemoteChatSession.Data read(FriendlyByteBuf buf) {
            return new RemoteChatSession.Data(buf.readUUID(), new ProfilePublicKey.Data(buf));
        }

        public static void write(FriendlyByteBuf buf, RemoteChatSession.Data serialized) {
            buf.writeUUID(serialized.sessionId);
            serialized.profilePublicKey.write(buf);
        }

        public RemoteChatSession validate(GameProfile gameProfile, SignatureValidator servicesSignatureVerifier) throws ProfilePublicKey.ValidationException {
            return new RemoteChatSession(
                this.sessionId, ProfilePublicKey.createValidated(servicesSignatureVerifier, gameProfile.getId(), this.profilePublicKey)
            );
        }
    }
}
