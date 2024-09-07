package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;

public class ServerPackCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("serverpack")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("push")
                        .then(
                            Commands.argument("url", StringArgumentType.string())
                                .then(
                                    Commands.argument("uuid", UuidArgument.uuid())
                                        .then(
                                            Commands.argument("hash", StringArgumentType.word())
                                                .executes(
                                                    context -> pushPack(
                                                            context.getSource(),
                                                            StringArgumentType.getString(context, "url"),
                                                            Optional.of(UuidArgument.getUuid(context, "uuid")),
                                                            Optional.of(StringArgumentType.getString(context, "hash"))
                                                        )
                                                )
                                        )
                                        .executes(
                                            context -> pushPack(
                                                    context.getSource(),
                                                    StringArgumentType.getString(context, "url"),
                                                    Optional.of(UuidArgument.getUuid(context, "uuid")),
                                                    Optional.empty()
                                                )
                                        )
                                )
                                .executes(
                                    context -> pushPack(context.getSource(), StringArgumentType.getString(context, "url"), Optional.empty(), Optional.empty())
                                )
                        )
                )
                .then(
                    Commands.literal("pop")
                        .then(
                            Commands.argument("uuid", UuidArgument.uuid())
                                .executes(context -> popPack(context.getSource(), UuidArgument.getUuid(context, "uuid")))
                        )
                )
        );
    }

    private static void sendToAllConnections(CommandSourceStack source, Packet<?> packet) {
        source.getServer().getConnection().getConnections().forEach(connection -> connection.send(packet));
    }

    private static int pushPack(CommandSourceStack source, String url, Optional<UUID> uuid, Optional<String> hash) {
        UUID uUID = uuid.orElseGet(() -> UUID.nameUUIDFromBytes(url.getBytes(StandardCharsets.UTF_8)));
        String string = hash.orElse("");
        ClientboundResourcePackPushPacket clientboundResourcePackPushPacket = new ClientboundResourcePackPushPacket(uUID, url, string, false, null);
        sendToAllConnections(source, clientboundResourcePackPushPacket);
        return 0;
    }

    private static int popPack(CommandSourceStack source, UUID uuid) {
        ClientboundResourcePackPopPacket clientboundResourcePackPopPacket = new ClientboundResourcePackPopPacket(Optional.of(uuid));
        sendToAllConnections(source, clientboundResourcePackPopPacket);
        return 0;
    }
}
