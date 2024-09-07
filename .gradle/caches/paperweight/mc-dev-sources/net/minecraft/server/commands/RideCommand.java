package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class RideCommand {
    private static final DynamicCommandExceptionType ERROR_NOT_RIDING = new DynamicCommandExceptionType(
        entity -> Component.translatableEscape("commands.ride.not_riding", entity)
    );
    private static final Dynamic2CommandExceptionType ERROR_ALREADY_RIDING = new Dynamic2CommandExceptionType(
        (rider, vehicle) -> Component.translatableEscape("commands.ride.already_riding", rider, vehicle)
    );
    private static final Dynamic2CommandExceptionType ERROR_MOUNT_FAILED = new Dynamic2CommandExceptionType(
        (rider, vehicle) -> Component.translatableEscape("commands.ride.mount.failure.generic", rider, vehicle)
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_PLAYER = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.cant_ride_players")
    );
    private static final SimpleCommandExceptionType ERROR_MOUNTING_LOOP = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.loop")
    );
    private static final SimpleCommandExceptionType ERROR_WRONG_DIMENSION = new SimpleCommandExceptionType(
        Component.translatable("commands.ride.mount.failure.wrong_dimension")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("ride")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.literal("mount")
                                .then(
                                    Commands.argument("vehicle", EntityArgument.entity())
                                        .executes(
                                            context -> mount(
                                                    context.getSource(),
                                                    EntityArgument.getEntity(context, "target"),
                                                    EntityArgument.getEntity(context, "vehicle")
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("dismount").executes(context -> dismount(context.getSource(), EntityArgument.getEntity(context, "target"))))
                )
        );
    }

    private static int mount(CommandSourceStack source, Entity rider, Entity vehicle) throws CommandSyntaxException {
        Entity entity = rider.getVehicle();
        if (entity != null) {
            throw ERROR_ALREADY_RIDING.create(rider.getDisplayName(), entity.getDisplayName());
        } else if (vehicle.getType() == EntityType.PLAYER) {
            throw ERROR_MOUNTING_PLAYER.create();
        } else if (rider.getSelfAndPassengers().anyMatch(passenger -> passenger == vehicle)) {
            throw ERROR_MOUNTING_LOOP.create();
        } else if (rider.level() != vehicle.level()) {
            throw ERROR_WRONG_DIMENSION.create();
        } else if (!rider.startRiding(vehicle, true)) {
            throw ERROR_MOUNT_FAILED.create(rider.getDisplayName(), vehicle.getDisplayName());
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ride.mount.success", rider.getDisplayName(), vehicle.getDisplayName()), true);
            return 1;
        }
    }

    private static int dismount(CommandSourceStack source, Entity rider) throws CommandSyntaxException {
        Entity entity = rider.getVehicle();
        if (entity == null) {
            throw ERROR_NOT_RIDING.create(rider.getDisplayName());
        } else {
            rider.stopRiding();
            source.sendSuccess(() -> Component.translatable("commands.ride.dismount.success", rider.getDisplayName(), entity.getDisplayName()), true);
            return 1;
        }
    }
}
