package net.minecraft.network;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.Packet;

public interface PacketSendListener {
    static PacketSendListener thenRun(Runnable runnable) {
        return new PacketSendListener() {
            @Override
            public void onSuccess() {
                runnable.run();
            }

            @Nullable
            @Override
            public Packet<?> onFailure() {
                runnable.run();
                return null;
            }
        };
    }

    static PacketSendListener exceptionallySend(Supplier<Packet<?>> failurePacket) {
        return new PacketSendListener() {
            @Nullable
            @Override
            public Packet<?> onFailure() {
                return failurePacket.get();
            }
        };
    }

    default void onSuccess() {
    }

    @Nullable
    default Packet<?> onFailure() {
        return null;
    }
}
