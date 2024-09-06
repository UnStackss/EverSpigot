package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.AngleArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class SetWorldSpawnCommand {

    public SetWorldSpawnCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register((LiteralArgumentBuilder) ((LiteralArgumentBuilder) ((LiteralArgumentBuilder) net.minecraft.commands.Commands.literal("setworldspawn").requires((commandlistenerwrapper) -> {
            return commandlistenerwrapper.hasPermission(2);
        })).executes((commandcontext) -> {
            return SetWorldSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPos.containing(((CommandSourceStack) commandcontext.getSource()).getPosition()), 0.0F);
        })).then(((RequiredArgumentBuilder) net.minecraft.commands.Commands.argument("pos", BlockPosArgument.blockPos()).executes((commandcontext) -> {
            return SetWorldSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), 0.0F);
        })).then(net.minecraft.commands.Commands.argument("angle", AngleArgument.angle()).executes((commandcontext) -> {
            return SetWorldSpawnCommand.setSpawn((CommandSourceStack) commandcontext.getSource(), BlockPosArgument.getSpawnablePos(commandcontext, "pos"), AngleArgument.getAngle(commandcontext, "angle"));
        }))));
    }

    private static int setSpawn(CommandSourceStack source, BlockPos pos, float angle) {
        ServerLevel worldserver = source.getLevel();

        if (false && worldserver.dimension() != Level.OVERWORLD) { // CraftBukkit - SPIGOT-7649: allow in all worlds
            source.sendFailure(Component.translatable("commands.setworldspawn.failure.not_overworld"));
            return 0;
        } else {
            worldserver.setDefaultSpawnPos(pos, angle);
            source.sendSuccess(() -> {
                return Component.translatable("commands.setworldspawn.success", pos.getX(), pos.getY(), pos.getZ(), angle);
            }, true);
            return 1;
        }
    }
}
