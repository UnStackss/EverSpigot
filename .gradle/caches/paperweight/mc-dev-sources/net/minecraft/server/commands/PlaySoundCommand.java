package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PlaySoundCommand {
    private static final SimpleCommandExceptionType ERROR_TOO_FAR = new SimpleCommandExceptionType(Component.translatable("commands.playsound.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> requiredArgumentBuilder = Commands.argument("sound", ResourceLocationArgument.id())
            .suggests(SuggestionProviders.AVAILABLE_SOUNDS)
            .executes(
                context -> playSound(
                        context.getSource(),
                        getCallingPlayerAsCollection(context.getSource().getPlayer()),
                        ResourceLocationArgument.getId(context, "sound"),
                        SoundSource.MASTER,
                        context.getSource().getPosition(),
                        1.0F,
                        1.0F,
                        0.0F
                    )
            );

        for (SoundSource soundSource : SoundSource.values()) {
            requiredArgumentBuilder.then(source(soundSource));
        }

        dispatcher.register(Commands.literal("playsound").requires(source -> source.hasPermission(2)).then(requiredArgumentBuilder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> source(SoundSource category) {
        return Commands.literal(category.getName())
            .executes(
                context -> playSound(
                        context.getSource(),
                        getCallingPlayerAsCollection(context.getSource().getPlayer()),
                        ResourceLocationArgument.getId(context, "sound"),
                        category,
                        context.getSource().getPosition(),
                        1.0F,
                        1.0F,
                        0.0F
                    )
            )
            .then(
                Commands.argument("targets", EntityArgument.players())
                    .executes(
                        context -> playSound(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets"),
                                ResourceLocationArgument.getId(context, "sound"),
                                category,
                                context.getSource().getPosition(),
                                1.0F,
                                1.0F,
                                0.0F
                            )
                    )
                    .then(
                        Commands.argument("pos", Vec3Argument.vec3())
                            .executes(
                                context -> playSound(
                                        context.getSource(),
                                        EntityArgument.getPlayers(context, "targets"),
                                        ResourceLocationArgument.getId(context, "sound"),
                                        category,
                                        Vec3Argument.getVec3(context, "pos"),
                                        1.0F,
                                        1.0F,
                                        0.0F
                                    )
                            )
                            .then(
                                Commands.argument("volume", FloatArgumentType.floatArg(0.0F))
                                    .executes(
                                        context -> playSound(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "targets"),
                                                ResourceLocationArgument.getId(context, "sound"),
                                                category,
                                                Vec3Argument.getVec3(context, "pos"),
                                                context.getArgument("volume", Float.class),
                                                1.0F,
                                                0.0F
                                            )
                                    )
                                    .then(
                                        Commands.argument("pitch", FloatArgumentType.floatArg(0.0F, 2.0F))
                                            .executes(
                                                context -> playSound(
                                                        context.getSource(),
                                                        EntityArgument.getPlayers(context, "targets"),
                                                        ResourceLocationArgument.getId(context, "sound"),
                                                        category,
                                                        Vec3Argument.getVec3(context, "pos"),
                                                        context.getArgument("volume", Float.class),
                                                        context.getArgument("pitch", Float.class),
                                                        0.0F
                                                    )
                                            )
                                            .then(
                                                Commands.argument("minVolume", FloatArgumentType.floatArg(0.0F, 1.0F))
                                                    .executes(
                                                        context -> playSound(
                                                                context.getSource(),
                                                                EntityArgument.getPlayers(context, "targets"),
                                                                ResourceLocationArgument.getId(context, "sound"),
                                                                category,
                                                                Vec3Argument.getVec3(context, "pos"),
                                                                context.getArgument("volume", Float.class),
                                                                context.getArgument("pitch", Float.class),
                                                                context.getArgument("minVolume", Float.class)
                                                            )
                                                    )
                                            )
                                    )
                            )
                    )
            );
    }

    private static Collection<ServerPlayer> getCallingPlayerAsCollection(@Nullable ServerPlayer player) {
        return player != null ? List.of(player) : List.of();
    }

    private static int playSound(
        CommandSourceStack source,
        Collection<ServerPlayer> targets,
        ResourceLocation sound,
        SoundSource category,
        Vec3 pos,
        float volume,
        float pitch,
        float minVolume
    ) throws CommandSyntaxException {
        Holder<SoundEvent> holder = Holder.direct(SoundEvent.createVariableRangeEvent(sound));
        double d = (double)Mth.square(holder.value().getRange(volume));
        int i = 0;
        long l = source.getLevel().getRandom().nextLong();

        for (ServerPlayer serverPlayer : targets) {
            double e = pos.x - serverPlayer.getX();
            double f = pos.y - serverPlayer.getY();
            double g = pos.z - serverPlayer.getZ();
            double h = e * e + f * f + g * g;
            Vec3 vec3 = pos;
            float j = volume;
            if (h > d) {
                if (minVolume <= 0.0F) {
                    continue;
                }

                double k = Math.sqrt(h);
                vec3 = new Vec3(serverPlayer.getX() + e / k * 2.0, serverPlayer.getY() + f / k * 2.0, serverPlayer.getZ() + g / k * 2.0);
                j = minVolume;
            }

            serverPlayer.connection.send(new ClientboundSoundPacket(holder, category, vec3.x(), vec3.y(), vec3.z(), j, pitch, l));
            i++;
        }

        if (i == 0) {
            throw ERROR_TOO_FAR.create();
        } else {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.playsound.success.single", Component.translationArg(sound), targets.iterator().next().getDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(() -> Component.translatable("commands.playsound.success.multiple", Component.translationArg(sound), targets.size()), true);
            }

            return i;
        }
    }
}
