package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

public class DamageCommand {
    private static final SimpleCommandExceptionType ERROR_INVULNERABLE = new SimpleCommandExceptionType(Component.translatable("commands.damage.invulnerable"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("damage")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("target", EntityArgument.entity())
                        .then(
                            Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                .executes(
                                    context -> damage(
                                            context.getSource(),
                                            EntityArgument.getEntity(context, "target"),
                                            FloatArgumentType.getFloat(context, "amount"),
                                            context.getSource().getLevel().damageSources().generic()
                                        )
                                )
                                .then(
                                    Commands.argument("damageType", ResourceArgument.resource(registryAccess, Registries.DAMAGE_TYPE))
                                        .executes(
                                            context -> damage(
                                                    context.getSource(),
                                                    EntityArgument.getEntity(context, "target"),
                                                    FloatArgumentType.getFloat(context, "amount"),
                                                    new DamageSource(ResourceArgument.getResource(context, "damageType", Registries.DAMAGE_TYPE))
                                                )
                                        )
                                        .then(
                                            Commands.literal("at")
                                                .then(
                                                    Commands.argument("location", Vec3Argument.vec3())
                                                        .executes(
                                                            context -> damage(
                                                                    context.getSource(),
                                                                    EntityArgument.getEntity(context, "target"),
                                                                    FloatArgumentType.getFloat(context, "amount"),
                                                                    new DamageSource(
                                                                        ResourceArgument.getResource(context, "damageType", Registries.DAMAGE_TYPE),
                                                                        Vec3Argument.getVec3(context, "location")
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("by")
                                                .then(
                                                    Commands.argument("entity", EntityArgument.entity())
                                                        .executes(
                                                            context -> damage(
                                                                    context.getSource(),
                                                                    EntityArgument.getEntity(context, "target"),
                                                                    FloatArgumentType.getFloat(context, "amount"),
                                                                    new DamageSource(
                                                                        ResourceArgument.getResource(context, "damageType", Registries.DAMAGE_TYPE),
                                                                        EntityArgument.getEntity(context, "entity")
                                                                    )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("from")
                                                                .then(
                                                                    Commands.argument("cause", EntityArgument.entity())
                                                                        .executes(
                                                                            context -> damage(
                                                                                    context.getSource(),
                                                                                    EntityArgument.getEntity(context, "target"),
                                                                                    FloatArgumentType.getFloat(context, "amount"),
                                                                                    new DamageSource(
                                                                                        ResourceArgument.getResource(
                                                                                            context, "damageType", Registries.DAMAGE_TYPE
                                                                                        ),
                                                                                        EntityArgument.getEntity(context, "entity"),
                                                                                        EntityArgument.getEntity(context, "cause")
                                                                                    )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int damage(CommandSourceStack source, Entity target, float amount, DamageSource damageSource) throws CommandSyntaxException {
        if (target.hurt(damageSource, amount)) {
            source.sendSuccess(() -> Component.translatable("commands.damage.success", amount, target.getDisplayName()), true);
            return 1;
        } else {
            throw ERROR_INVULNERABLE.create();
        }
    }
}
