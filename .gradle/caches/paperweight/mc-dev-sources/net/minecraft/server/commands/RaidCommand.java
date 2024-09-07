package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;

public class RaidCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("raid")
                .requires(source -> source.hasPermission(3))
                .then(
                    Commands.literal("start")
                        .then(
                            Commands.argument("omenlvl", IntegerArgumentType.integer(0))
                                .executes(context -> start(context.getSource(), IntegerArgumentType.getInteger(context, "omenlvl")))
                        )
                )
                .then(Commands.literal("stop").executes(context -> stop(context.getSource())))
                .then(Commands.literal("check").executes(context -> check(context.getSource())))
                .then(
                    Commands.literal("sound")
                        .then(
                            Commands.argument("type", ComponentArgument.textComponent(registryAccess))
                                .executes(context -> playSound(context.getSource(), ComponentArgument.getComponent(context, "type")))
                        )
                )
                .then(Commands.literal("spawnleader").executes(context -> spawnLeader(context.getSource())))
                .then(
                    Commands.literal("setomen")
                        .then(
                            Commands.argument("level", IntegerArgumentType.integer(0))
                                .executes(context -> setRaidOmenLevel(context.getSource(), IntegerArgumentType.getInteger(context, "level")))
                        )
                )
                .then(Commands.literal("glow").executes(context -> glow(context.getSource())))
        );
    }

    private static int glow(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            for (Raider raider : raid.getAllRaiders()) {
                raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, 1000, 1));
            }
        }

        return 1;
    }

    private static int setRaidOmenLevel(CommandSourceStack source, int level) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            int i = raid.getMaxRaidOmenLevel();
            if (level > i) {
                source.sendFailure(Component.literal("Sorry, the max raid omen level you can set is " + i));
            } else {
                int j = raid.getRaidOmenLevel();
                raid.setRaidOmenLevel(level);
                source.sendSuccess(() -> Component.literal("Changed village's raid omen level from " + j + " to " + level), false);
            }
        } else {
            source.sendFailure(Component.literal("No raid found here"));
        }

        return 1;
    }

    private static int spawnLeader(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Spawned a raid captain"), false);
        Raider raider = EntityType.PILLAGER.create(source.getLevel());
        if (raider == null) {
            source.sendFailure(Component.literal("Pillager failed to spawn"));
            return 0;
        } else {
            raider.setPatrolLeader(true);
            raider.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance(source.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
            raider.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);
            raider.finalizeSpawn(
                source.getLevel(), source.getLevel().getCurrentDifficultyAt(BlockPos.containing(source.getPosition())), MobSpawnType.COMMAND, null
            );
            source.getLevel().addFreshEntityWithPassengers(raider);
            return 1;
        }
    }

    private static int playSound(CommandSourceStack source, @Nullable Component type) {
        if (type != null && type.getString().equals("local")) {
            ServerLevel serverLevel = source.getLevel();
            Vec3 vec3 = source.getPosition().add(5.0, 0.0, 0.0);
            serverLevel.playSeededSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.RAID_HORN, SoundSource.NEUTRAL, 2.0F, 1.0F, serverLevel.random.nextLong());
        }

        return 1;
    }

    private static int start(CommandSourceStack source, int level) throws CommandSyntaxException {
        ServerPlayer serverPlayer = source.getPlayerOrException();
        BlockPos blockPos = serverPlayer.blockPosition();
        if (serverPlayer.serverLevel().isRaided(blockPos)) {
            source.sendFailure(Component.literal("Raid already started close by"));
            return -1;
        } else {
            Raids raids = serverPlayer.serverLevel().getRaids();
            Raid raid = raids.createOrExtendRaid(serverPlayer, serverPlayer.blockPosition());
            if (raid != null) {
                raid.setRaidOmenLevel(level);
                raids.setDirty();
                source.sendSuccess(() -> Component.literal("Created a raid in your local village"), false);
            } else {
                source.sendFailure(Component.literal("Failed to create a raid in your local village"));
            }

            return 1;
        }
    }

    private static int stop(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer serverPlayer = source.getPlayerOrException();
        BlockPos blockPos = serverPlayer.blockPosition();
        Raid raid = serverPlayer.serverLevel().getRaidAt(blockPos);
        if (raid != null) {
            raid.stop();
            source.sendSuccess(() -> Component.literal("Stopped raid"), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("No raid here"));
            return -1;
        }
    }

    private static int check(CommandSourceStack source) throws CommandSyntaxException {
        Raid raid = getRaid(source.getPlayerOrException());
        if (raid != null) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Found a started raid! ");
            source.sendSuccess(() -> Component.literal(stringBuilder.toString()), false);
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("Num groups spawned: ");
            stringBuilder2.append(raid.getGroupsSpawned());
            stringBuilder2.append(" Raid omen level: ");
            stringBuilder2.append(raid.getRaidOmenLevel());
            stringBuilder2.append(" Num mobs: ");
            stringBuilder2.append(raid.getTotalRaidersAlive());
            stringBuilder2.append(" Raid health: ");
            stringBuilder2.append(raid.getHealthOfLivingRaiders());
            stringBuilder2.append(" / ");
            stringBuilder2.append(raid.getTotalHealth());
            source.sendSuccess(() -> Component.literal(stringBuilder2.toString()), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Found no started raids"));
            return 0;
        }
    }

    @Nullable
    private static Raid getRaid(ServerPlayer player) {
        return player.serverLevel().getRaidAt(player.blockPosition());
    }
}
