package net.minecraft.commands.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.SignableCommand;

public record ArgumentSignatures(List<ArgumentSignatures.Entry> entries) {
    public static final ArgumentSignatures EMPTY = new ArgumentSignatures(List.of());
    private static final int MAX_ARGUMENT_COUNT = 8;
    private static final int MAX_ARGUMENT_NAME_LENGTH = 16;

    public ArgumentSignatures(FriendlyByteBuf buf) {
        this(buf.readCollection(FriendlyByteBuf.limitValue(ArrayList::new, 8), ArgumentSignatures.Entry::new));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(this.entries, (buf2, entry) -> entry.write(buf2));
    }

    public static ArgumentSignatures signCommand(SignableCommand<?> arguments, ArgumentSignatures.Signer signer) {
        List<ArgumentSignatures.Entry> list = arguments.arguments().stream().map(argument -> {
            MessageSignature messageSignature = signer.sign(argument.value());
            return messageSignature != null ? new ArgumentSignatures.Entry(argument.name(), messageSignature) : null;
        }).filter(Objects::nonNull).toList();
        return new ArgumentSignatures(list);
    }

    public static record Entry(String name, MessageSignature signature) {
        public Entry(FriendlyByteBuf buf) {
            this(buf.readUtf(16), MessageSignature.read(buf));
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.name, 16);
            MessageSignature.write(buf, this.signature);
        }
    }

    @FunctionalInterface
    public interface Signer {
        @Nullable
        MessageSignature sign(String value);
    }
}
