package net.minecraft.network.chat;

import com.mojang.logging.LogUtils;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.util.SignatureValidator;
import net.minecraft.util.Signer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.slf4j.Logger;

public class SignedMessageChain {
    static final Logger LOGGER = LogUtils.getLogger();
    @Nullable
    SignedMessageLink nextLink;
    Instant lastTimeStamp = Instant.EPOCH;

    public SignedMessageChain(UUID sender, UUID sessionId) {
        this.nextLink = SignedMessageLink.root(sender, sessionId);
    }

    public SignedMessageChain.Encoder encoder(Signer signer) {
        return body -> {
            SignedMessageLink signedMessageLink = this.nextLink;
            if (signedMessageLink == null) {
                return null;
            } else {
                this.nextLink = signedMessageLink.advance();
                return new MessageSignature(signer.sign(updatable -> PlayerChatMessage.updateSignature(updatable, signedMessageLink, body)));
            }
        };
    }

    public SignedMessageChain.Decoder decoder(ProfilePublicKey playerPublicKey) {
        final SignatureValidator signatureValidator = playerPublicKey.createSignatureValidator();
        return new SignedMessageChain.Decoder() {
            @Override
            public PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException {
                if (signature == null) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.MISSING_PROFILE_KEY);
                } else if (playerPublicKey.data().hasExpired()) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.EXPIRED_PROFILE_KEY,  org.bukkit.event.player.PlayerKickEvent.Cause.EXPIRED_PROFILE_PUBLIC_KEY); // Paper - kick event causes
                } else {
                    SignedMessageLink signedMessageLink = SignedMessageChain.this.nextLink;
                    if (signedMessageLink == null) {
                        throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.CHAIN_BROKEN);
                    } else if (body.timeStamp().isBefore(SignedMessageChain.this.lastTimeStamp)) {
                        this.setChainBroken();
                        throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.OUT_OF_ORDER_CHAT, org.bukkit.event.player.PlayerKickEvent.Cause.OUT_OF_ORDER_CHAT); // Paper - kick event causes
                    } else {
                        SignedMessageChain.this.lastTimeStamp = body.timeStamp();
                        PlayerChatMessage playerChatMessage = new PlayerChatMessage(signedMessageLink, signature, body, null, FilterMask.PASS_THROUGH);
                        if (!playerChatMessage.verify(signatureValidator)) {
                            this.setChainBroken();
                            throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.INVALID_SIGNATURE);
                        } else {
                            if (playerChatMessage.hasExpiredServer(Instant.now())) {
                                SignedMessageChain.LOGGER.warn("Received expired chat: '{}'. Is the client/server system time unsynchronized?", body.content());
                            }

                            SignedMessageChain.this.nextLink = signedMessageLink.advance();
                            return playerChatMessage;
                        }
                    }
                }
            }

            @Override
            public void setChainBroken() {
                SignedMessageChain.this.nextLink = null;
            }
        };
    }

    public static class DecodeException extends ThrowingComponent {
        static final Component MISSING_PROFILE_KEY = Component.translatable("chat.disabled.missingProfileKey");
        static final Component CHAIN_BROKEN = Component.translatable("chat.disabled.chain_broken");
        static final Component EXPIRED_PROFILE_KEY = Component.translatable("chat.disabled.expiredProfileKey");
        static final Component INVALID_SIGNATURE = Component.translatable("chat.disabled.invalid_signature");
        static final Component OUT_OF_ORDER_CHAT = Component.translatable("chat.disabled.out_of_order_chat");

        // Paper start
        public final org.bukkit.event.player.PlayerKickEvent.Cause kickCause;
        public DecodeException(Component message, org.bukkit.event.player.PlayerKickEvent.Cause event) {
            super(message);
            this.kickCause = event;
        }
        // Paper end
        public DecodeException(Component message) {
            this(message, org.bukkit.event.player.PlayerKickEvent.Cause.UNKNOWN); // Paper
        }
    }

    @FunctionalInterface
    public interface Decoder {
        static SignedMessageChain.Decoder unsigned(UUID sender, BooleanSupplier secureProfileEnforced) {
            return (signature, body) -> {
                if (secureProfileEnforced.getAsBoolean()) {
                    throw new SignedMessageChain.DecodeException(SignedMessageChain.DecodeException.MISSING_PROFILE_KEY);
                } else {
                    return PlayerChatMessage.unsigned(sender, body.content());
                }
            };
        }

        PlayerChatMessage unpack(@Nullable MessageSignature signature, SignedMessageBody body) throws SignedMessageChain.DecodeException;

        default void setChainBroken() {
        }
    }

    @FunctionalInterface
    public interface Encoder {
        SignedMessageChain.Encoder UNSIGNED = body -> null;

        @Nullable
        MessageSignature pack(SignedMessageBody body);
    }
}
