package net.minecraft.commands;

import com.google.common.collect.Maps;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.gametest.framework.TestCommand;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.AdvancementCommands;
import net.minecraft.server.commands.AttributeCommand;
import net.minecraft.server.commands.BanIpCommands;
import net.minecraft.server.commands.BanListCommands;
import net.minecraft.server.commands.BanPlayerCommands;
import net.minecraft.server.commands.BossBarCommands;
import net.minecraft.server.commands.ClearInventoryCommands;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.commands.DamageCommand;
import net.minecraft.server.commands.DataPackCommand;
import net.minecraft.server.commands.DeOpCommands;
import net.minecraft.server.commands.DebugCommand;
import net.minecraft.server.commands.DebugConfigCommand;
import net.minecraft.server.commands.DebugMobSpawningCommand;
import net.minecraft.server.commands.DebugPathCommand;
import net.minecraft.server.commands.DefaultGameModeCommands;
import net.minecraft.server.commands.DifficultyCommand;
import net.minecraft.server.commands.EffectCommands;
import net.minecraft.server.commands.EmoteCommands;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.commands.ExperienceCommand;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.commands.ForceLoadCommand;
import net.minecraft.server.commands.FunctionCommand;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.server.commands.GiveCommand;
import net.minecraft.server.commands.HelpCommand;
import net.minecraft.server.commands.ItemCommands;
import net.minecraft.server.commands.JfrCommand;
import net.minecraft.server.commands.KickCommand;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.commands.ListPlayersCommand;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.commands.LootCommand;
import net.minecraft.server.commands.MsgCommand;
import net.minecraft.server.commands.OpCommand;
import net.minecraft.server.commands.PardonCommand;
import net.minecraft.server.commands.PardonIpCommand;
import net.minecraft.server.commands.ParticleCommand;
import net.minecraft.server.commands.PerfCommand;
import net.minecraft.server.commands.PlaceCommand;
import net.minecraft.server.commands.PlaySoundCommand;
import net.minecraft.server.commands.PublishCommand;
import net.minecraft.server.commands.RaidCommand;
import net.minecraft.server.commands.RandomCommand;
import net.minecraft.server.commands.RecipeCommand;
import net.minecraft.server.commands.ReloadCommand;
import net.minecraft.server.commands.ReturnCommand;
import net.minecraft.server.commands.RideCommand;
import net.minecraft.server.commands.SaveAllCommand;
import net.minecraft.server.commands.SaveOffCommand;
import net.minecraft.server.commands.SaveOnCommand;
import net.minecraft.server.commands.SayCommand;
import net.minecraft.server.commands.ScheduleCommand;
import net.minecraft.server.commands.ScoreboardCommand;
import net.minecraft.server.commands.SeedCommand;
import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.commands.SetBlockCommand;
import net.minecraft.server.commands.SetPlayerIdleTimeoutCommand;
import net.minecraft.server.commands.SetSpawnCommand;
import net.minecraft.server.commands.SetWorldSpawnCommand;
import net.minecraft.server.commands.SpawnArmorTrimsCommand;
import net.minecraft.server.commands.SpectateCommand;
import net.minecraft.server.commands.SpreadPlayersCommand;
import net.minecraft.server.commands.StopCommand;
import net.minecraft.server.commands.StopSoundCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.commands.TagCommand;
import net.minecraft.server.commands.TeamCommand;
import net.minecraft.server.commands.TeamMsgCommand;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.commands.TellRawCommand;
import net.minecraft.server.commands.TickCommand;
import net.minecraft.server.commands.TimeCommand;
import net.minecraft.server.commands.TitleCommand;
import net.minecraft.server.commands.TransferCommand;
import net.minecraft.server.commands.TriggerCommand;
import net.minecraft.server.commands.WardenSpawnTrackerCommand;
import net.minecraft.server.commands.WeatherCommand;
import net.minecraft.server.commands.WhitelistCommand;
import net.minecraft.server.commands.WorldBorderCommand;
import net.minecraft.server.commands.data.DataCommands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

// CraftBukkit start
import com.google.common.base.Joiner;
import java.util.Collection;
import java.util.LinkedHashSet;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;
// CraftBukkit end

public class Commands {

    private static final ThreadLocal<ExecutionContext<CommandSourceStack>> CURRENT_EXECUTION_CONTEXT = new ThreadLocal();
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int LEVEL_ALL = 0;
    public static final int LEVEL_MODERATORS = 1;
    public static final int LEVEL_GAMEMASTERS = 2;
    public static final int LEVEL_ADMINS = 3;
    public static final int LEVEL_OWNERS = 4;
    private final com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = new com.mojang.brigadier.CommandDispatcher();

    public Commands(Commands.CommandSelection environment, CommandBuildContext commandRegistryAccess) {
        this(); // CraftBukkit
        AdvancementCommands.register(this.dispatcher);
        AttributeCommand.register(this.dispatcher, commandRegistryAccess);
        ExecuteCommand.register(this.dispatcher, commandRegistryAccess);
        BossBarCommands.register(this.dispatcher, commandRegistryAccess);
        ClearInventoryCommands.register(this.dispatcher, commandRegistryAccess);
        CloneCommands.register(this.dispatcher, commandRegistryAccess);
        DamageCommand.register(this.dispatcher, commandRegistryAccess);
        DataCommands.register(this.dispatcher);
        DataPackCommand.register(this.dispatcher);
        DebugCommand.register(this.dispatcher);
        DefaultGameModeCommands.register(this.dispatcher);
        DifficultyCommand.register(this.dispatcher);
        EffectCommands.register(this.dispatcher, commandRegistryAccess);
        EmoteCommands.register(this.dispatcher);
        EnchantCommand.register(this.dispatcher, commandRegistryAccess);
        ExperienceCommand.register(this.dispatcher);
        FillCommand.register(this.dispatcher, commandRegistryAccess);
        FillBiomeCommand.register(this.dispatcher, commandRegistryAccess);
        ForceLoadCommand.register(this.dispatcher);
        FunctionCommand.register(this.dispatcher);
        GameModeCommand.register(this.dispatcher);
        GameRuleCommand.register(this.dispatcher);
        GiveCommand.register(this.dispatcher, commandRegistryAccess);
        HelpCommand.register(this.dispatcher);
        ItemCommands.register(this.dispatcher, commandRegistryAccess);
        KickCommand.register(this.dispatcher);
        KillCommand.register(this.dispatcher);
        ListPlayersCommand.register(this.dispatcher);
        LocateCommand.register(this.dispatcher, commandRegistryAccess);
        LootCommand.register(this.dispatcher, commandRegistryAccess);
        MsgCommand.register(this.dispatcher);
        ParticleCommand.register(this.dispatcher, commandRegistryAccess);
        PlaceCommand.register(this.dispatcher);
        PlaySoundCommand.register(this.dispatcher);
        RandomCommand.register(this.dispatcher);
        ReloadCommand.register(this.dispatcher);
        RecipeCommand.register(this.dispatcher);
        ReturnCommand.register(this.dispatcher);
        RideCommand.register(this.dispatcher);
        SayCommand.register(this.dispatcher);
        ScheduleCommand.register(this.dispatcher);
        ScoreboardCommand.register(this.dispatcher, commandRegistryAccess);
        SeedCommand.register(this.dispatcher, environment != Commands.CommandSelection.INTEGRATED);
        SetBlockCommand.register(this.dispatcher, commandRegistryAccess);
        SetSpawnCommand.register(this.dispatcher);
        SetWorldSpawnCommand.register(this.dispatcher);
        SpectateCommand.register(this.dispatcher);
        SpreadPlayersCommand.register(this.dispatcher);
        StopSoundCommand.register(this.dispatcher);
        SummonCommand.register(this.dispatcher, commandRegistryAccess);
        TagCommand.register(this.dispatcher);
        TeamCommand.register(this.dispatcher, commandRegistryAccess);
        TeamMsgCommand.register(this.dispatcher);
        TeleportCommand.register(this.dispatcher);
        TellRawCommand.register(this.dispatcher, commandRegistryAccess);
        TickCommand.register(this.dispatcher);
        TimeCommand.register(this.dispatcher);
        TitleCommand.register(this.dispatcher, commandRegistryAccess);
        TriggerCommand.register(this.dispatcher);
        WeatherCommand.register(this.dispatcher);
        WorldBorderCommand.register(this.dispatcher);
        if (JvmProfiler.INSTANCE.isAvailable()) {
            JfrCommand.register(this.dispatcher);
        }

        if (SharedConstants.IS_RUNNING_IN_IDE) {
            TestCommand.register(this.dispatcher);
            RaidCommand.register(this.dispatcher, commandRegistryAccess);
            DebugPathCommand.register(this.dispatcher);
            DebugMobSpawningCommand.register(this.dispatcher);
            WardenSpawnTrackerCommand.register(this.dispatcher);
            SpawnArmorTrimsCommand.register(this.dispatcher);
            ServerPackCommand.register(this.dispatcher);
            if (environment.includeDedicated) {
                DebugConfigCommand.register(this.dispatcher);
            }
        }

        if (environment.includeDedicated) {
            BanIpCommands.register(this.dispatcher);
            BanListCommands.register(this.dispatcher);
            BanPlayerCommands.register(this.dispatcher);
            DeOpCommands.register(this.dispatcher);
            OpCommand.register(this.dispatcher);
            PardonCommand.register(this.dispatcher);
            PardonIpCommand.register(this.dispatcher);
            PerfCommand.register(this.dispatcher);
            SaveAllCommand.register(this.dispatcher);
            SaveOffCommand.register(this.dispatcher);
            SaveOnCommand.register(this.dispatcher);
            SetPlayerIdleTimeoutCommand.register(this.dispatcher);
            StopCommand.register(this.dispatcher);
            TransferCommand.register(this.dispatcher);
            WhitelistCommand.register(this.dispatcher);
        }

        if (environment.includeIntegrated) {
            PublishCommand.register(this.dispatcher);
        }

        // Paper start - Vanilla command permission fixes
        for (final CommandNode<CommandSourceStack> node : this.dispatcher.getRoot().getChildren()) {
            if (node.getRequirement() == com.mojang.brigadier.builder.ArgumentBuilder.<CommandSourceStack>defaultRequirement()) {
                node.requirement = stack -> stack.source == CommandSource.NULL || stack.getBukkitSender().hasPermission(org.bukkit.craftbukkit.command.VanillaCommandWrapper.getPermission(node));
            }
        }
        // Paper end - Vanilla command permission fixes
        // CraftBukkit start
    }

    public Commands() {
        // CraftBukkkit end
        this.dispatcher.setConsumer(ExecutionCommandSource.resultConsumer());
    }

    public static <S> ParseResults<S> mapSource(ParseResults<S> parseResults, UnaryOperator<S> sourceMapper) {
        CommandContextBuilder<S> commandcontextbuilder = parseResults.getContext();
        CommandContextBuilder<S> commandcontextbuilder1 = commandcontextbuilder.withSource(sourceMapper.apply(commandcontextbuilder.getSource()));

        return new ParseResults(commandcontextbuilder1, parseResults.getReader(), parseResults.getExceptions());
    }

    // CraftBukkit start
    public void dispatchServerCommand(CommandSourceStack sender, String command) {
        Joiner joiner = Joiner.on(" ");
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        ServerCommandEvent event = new ServerCommandEvent(sender.getBukkitSender(), command);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        command = event.getCommand();

        String[] args = command.split(" ");
        if (args.length == 0) return; // Paper - empty commands shall not be dispatched

        String cmd = args[0];
        if (cmd.startsWith("minecraft:")) cmd = cmd.substring("minecraft:".length());
        if (cmd.startsWith("bukkit:")) cmd = cmd.substring("bukkit:".length());

        // Block disallowed commands
        if (cmd.equalsIgnoreCase("stop") || cmd.equalsIgnoreCase("kick") || cmd.equalsIgnoreCase("op")
                || cmd.equalsIgnoreCase("deop") || cmd.equalsIgnoreCase("ban") || cmd.equalsIgnoreCase("ban-ip")
                || cmd.equalsIgnoreCase("pardon") || cmd.equalsIgnoreCase("pardon-ip") || cmd.equalsIgnoreCase("reload")) {
            return;
        }

        // Handle vanilla commands;
        if (sender.getLevel().getCraftServer().getCommandBlockOverride(args[0])) {
            args[0] = "minecraft:" + args[0];
        }

        String newCommand = joiner.join(args);
        this.performPrefixedCommand(sender, newCommand, newCommand);
    }
    // CraftBukkit end

    public void performPrefixedCommand(CommandSourceStack source, String command) {
        // CraftBukkit start
        this.performPrefixedCommand(source, command, command);
    }

    public void performPrefixedCommand(CommandSourceStack commandlistenerwrapper, String s, String label) {
        s = s.startsWith("/") ? s.substring(1) : s;
        this.performCommand(this.dispatcher.parse(s, commandlistenerwrapper), s, label);
        // CraftBukkit end
    }

    public void performCommand(ParseResults<CommandSourceStack> parseResults, String command) {
        this.performCommand(parseResults, command, command);
    }

    public void performCommand(ParseResults<CommandSourceStack> parseresults, String s, String label) { // CraftBukkit
        CommandSourceStack commandlistenerwrapper = (CommandSourceStack) parseresults.getContext().getSource();

        commandlistenerwrapper.getServer().getProfiler().push(() -> {
            return "/" + s;
        });
        ContextChain contextchain = this.finishParsing(parseresults, s, commandlistenerwrapper, label); // CraftBukkit // Paper - Add UnknownCommandEvent

        try {
            if (contextchain != null) {
                Commands.executeCommandInContext(commandlistenerwrapper, (executioncontext) -> {
                    ExecutionContext.queueInitialCommandExecution(executioncontext, s, contextchain, commandlistenerwrapper, CommandResultCallback.EMPTY);
                });
            }
        } catch (Exception exception) {
            MutableComponent ichatmutablecomponent = Component.literal(exception.getMessage() == null ? exception.getClass().getName() : exception.getMessage());

            if (commandlistenerwrapper.getServer().isDebugging() || Commands.LOGGER.isDebugEnabled()) { // Paper - Debugging
                Commands.LOGGER.error("Command exception: /{}", s, exception);
                StackTraceElement[] astacktraceelement = exception.getStackTrace();

                for (int i = 0; i < Math.min(astacktraceelement.length, 3); ++i) {
                    ichatmutablecomponent.append("\n\n").append(astacktraceelement[i].getMethodName()).append("\n ").append(astacktraceelement[i].getFileName()).append(":").append(String.valueOf(astacktraceelement[i].getLineNumber()));
                }
            }

            commandlistenerwrapper.sendFailure(Component.translatable("command.failed").withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, ichatmutablecomponent));
            }));
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                commandlistenerwrapper.sendFailure(Component.literal(Util.describeError(exception)));
                Commands.LOGGER.error("'/{}' threw an exception", s, exception);
            }
        } finally {
            commandlistenerwrapper.getServer().getProfiler().pop();
        }

    }

    @Nullable
    private ContextChain<CommandSourceStack> finishParsing(ParseResults<CommandSourceStack> parseresults, String s, CommandSourceStack commandlistenerwrapper, String label) { // CraftBukkit // Paper - Add UnknownCommandEvent
        try {
            Commands.validateParseResults(parseresults);
            return (ContextChain) ContextChain.tryFlatten(parseresults.getContext().build(s)).orElseThrow(() -> {
                return CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parseresults.getReader());
            });
        } catch (CommandSyntaxException commandsyntaxexception) {
            // Paper start - Add UnknownCommandEvent
            final net.kyori.adventure.text.TextComponent.Builder builder = net.kyori.adventure.text.Component.text();
            // commandlistenerwrapper.sendFailure(ComponentUtils.fromMessage(commandsyntaxexception.getRawMessage()));
            builder.color(net.kyori.adventure.text.format.NamedTextColor.RED).append(io.papermc.paper.brigadier.PaperBrigadier.componentFromMessage(commandsyntaxexception.getRawMessage()));
            // Paper end - Add UnknownCommandEvent
            if (commandsyntaxexception.getInput() != null && commandsyntaxexception.getCursor() >= 0) {
                int i = Math.min(commandsyntaxexception.getInput().length(), commandsyntaxexception.getCursor());
                MutableComponent ichatmutablecomponent = Component.empty().withStyle(ChatFormatting.GRAY).withStyle((chatmodifier) -> {
                    return chatmodifier.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + label)); // CraftBukkit // Paper
                });

                if (i > 10) {
                    ichatmutablecomponent.append(CommonComponents.ELLIPSIS);
                }

                ichatmutablecomponent.append(commandsyntaxexception.getInput().substring(Math.max(0, i - 10), i));
                if (i < commandsyntaxexception.getInput().length()) {
                    MutableComponent ichatmutablecomponent1 = Component.literal(commandsyntaxexception.getInput().substring(i)).withStyle(ChatFormatting.RED, ChatFormatting.UNDERLINE);

                    ichatmutablecomponent.append((Component) ichatmutablecomponent1);
                }

                ichatmutablecomponent.append((Component) Component.translatable("command.context.here").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
                // Paper start - Add UnknownCommandEvent
                // commandlistenerwrapper.sendFailure(ichatmutablecomponent);
                builder
                    .append(net.kyori.adventure.text.Component.newline())
                    .append(io.papermc.paper.adventure.PaperAdventure.asAdventure(ichatmutablecomponent));
            }
            org.bukkit.event.command.UnknownCommandEvent event = new org.bukkit.event.command.UnknownCommandEvent(commandlistenerwrapper.getBukkitSender(), s, org.spigotmc.SpigotConfig.unknownCommandMessage.isEmpty() ? null : builder.build());
            org.bukkit.Bukkit.getServer().getPluginManager().callEvent(event);
            if (event.message() != null) {
                commandlistenerwrapper.sendFailure(io.papermc.paper.adventure.PaperAdventure.asVanilla(event.message()), false);
                // Paper end - Add UnknownCommandEvent
            }

            return null;
        }
    }

    public static void executeCommandInContext(CommandSourceStack commandSource, Consumer<ExecutionContext<CommandSourceStack>> callback) {
        MinecraftServer minecraftserver = commandSource.getServer();
        ExecutionContext<CommandSourceStack> executioncontext = (ExecutionContext) Commands.CURRENT_EXECUTION_CONTEXT.get();
        boolean flag = executioncontext == null;

        if (flag) {
            int i = Math.max(1, minecraftserver.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_CHAIN_LENGTH));
            int j = minecraftserver.getGameRules().getInt(GameRules.RULE_MAX_COMMAND_FORK_COUNT);

            try {
                ExecutionContext<CommandSourceStack> executioncontext1 = new ExecutionContext<>(i, j, minecraftserver.getProfiler());

                try {
                    Commands.CURRENT_EXECUTION_CONTEXT.set(executioncontext1);
                    callback.accept(executioncontext1);
                    executioncontext1.runCommandQueue();
                } catch (Throwable throwable) {
                    try {
                        executioncontext1.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                executioncontext1.close();
            } finally {
                Commands.CURRENT_EXECUTION_CONTEXT.set(null); // CraftBukkit - decompile error
            }
        } else {
            callback.accept(executioncontext);
        }

    }

    public void sendCommands(ServerPlayer player) {
        // Paper start - Send empty commands if tab completion is disabled
        if (org.spigotmc.SpigotConfig.tabComplete < 0) {
            player.connection.send(new ClientboundCommandsPacket(new RootCommandNode<>()));
            return;
        }
        // Paper end - Send empty commands if tab completion is disabled
        // CraftBukkit start
        // Register Vanilla commands into builtRoot as before
        // Paper start - Perf: Async command map building
        COMMAND_SENDING_POOL.execute(() -> {
                this.sendAsync(player);
        });
    }

    public static final java.util.concurrent.ThreadPoolExecutor COMMAND_SENDING_POOL = new java.util.concurrent.ThreadPoolExecutor(
        0, 2, 60L, java.util.concurrent.TimeUnit.SECONDS,
        new java.util.concurrent.LinkedBlockingQueue<>(),
        new com.google.common.util.concurrent.ThreadFactoryBuilder()
            .setNameFormat("Paper Async Command Builder Thread Pool - %1$d")
            .setUncaughtExceptionHandler(new net.minecraft.DefaultUncaughtExceptionHandlerWithName(net.minecraft.server.MinecraftServer.LOGGER))
            .build(),
        new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
    );

    private void sendAsync(ServerPlayer player) {
        // Paper end - Perf: Async command map building
        Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> map = Maps.newIdentityHashMap(); // Use identity to prevent aliasing issues
        RootCommandNode vanillaRoot = new RootCommandNode();

        RootCommandNode<CommandSourceStack> vanilla = player.server.vanillaCommandDispatcher.getDispatcher().getRoot();
        map.put(vanilla, vanillaRoot);
        this.fillUsableCommands(vanilla, vanillaRoot, player.createCommandSourceStack(), (Map) map);

        // Now build the global commands in a second pass
        RootCommandNode<SharedSuggestionProvider> rootcommandnode = new RootCommandNode();

        map.put(this.dispatcher.getRoot(), rootcommandnode);
        this.fillUsableCommands(this.dispatcher.getRoot(), rootcommandnode, player.createCommandSourceStack(), map);

        Collection<String> bukkit = new LinkedHashSet<>();
        for (CommandNode node : rootcommandnode.getChildren()) {
            bukkit.add(node.getName());
        }
        // Paper start - Perf: Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootcommandnode, false).callEvent(); // Paper - Brigadier API
        net.minecraft.server.MinecraftServer.getServer().execute(() -> {
           runSync(player, bukkit, rootcommandnode);
        });
    }

    private void runSync(ServerPlayer player, Collection<String> bukkit, RootCommandNode<SharedSuggestionProvider> rootcommandnode) {
        // Paper end - Perf: Async command map building
        new com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent<CommandSourceStack>(player.getBukkitEntity(), (RootCommandNode) rootcommandnode, true).callEvent(); // Paper - Brigadier API
        PlayerCommandSendEvent event = new PlayerCommandSendEvent(player.getBukkitEntity(), new LinkedHashSet<>(bukkit));
        event.getPlayer().getServer().getPluginManager().callEvent(event);

        // Remove labels that were removed during the event
        for (String orig : bukkit) {
            if (!event.getCommands().contains(orig)) {
                rootcommandnode.removeCommand(orig);
            }
        }
        // CraftBukkit end
        player.connection.send(new ClientboundCommandsPacket(rootcommandnode));
    }

    private void fillUsableCommands(CommandNode<CommandSourceStack> tree, CommandNode<SharedSuggestionProvider> result, CommandSourceStack source, Map<CommandNode<CommandSourceStack>, CommandNode<SharedSuggestionProvider>> resultNodes) {
        Iterator iterator = tree.getChildren().iterator();

        while (iterator.hasNext()) {
            CommandNode<CommandSourceStack> commandnode2 = (CommandNode) iterator.next();
            // Paper start - Brigadier API
            if (commandnode2.clientNode != null) {
                commandnode2 = commandnode2.clientNode;
            }
            // Paper end - Brigadier API
            if ( !org.spigotmc.SpigotConfig.sendNamespaced && commandnode2.getName().contains( ":" ) ) continue; // Spigot

            if (commandnode2.canUse(source)) {
                ArgumentBuilder argumentbuilder = commandnode2.createBuilder(); // CraftBukkit - decompile error

                argumentbuilder.requires((icompletionprovider) -> {
                    return true;
                });
                if (argumentbuilder.getCommand() != null) {
                    argumentbuilder.executes((commandcontext) -> {
                        return 0;
                    });
                }

                if (argumentbuilder instanceof RequiredArgumentBuilder) {
                    RequiredArgumentBuilder<SharedSuggestionProvider, ?> requiredargumentbuilder = (RequiredArgumentBuilder) argumentbuilder;

                    if (requiredargumentbuilder.getSuggestionsProvider() != null) {
                        requiredargumentbuilder.suggests(SuggestionProviders.safelySwap(requiredargumentbuilder.getSuggestionsProvider()));
                    }
                }

                if (argumentbuilder.getRedirect() != null) {
                    argumentbuilder.redirect((CommandNode) resultNodes.get(argumentbuilder.getRedirect()));
                }

                CommandNode commandnode3 = argumentbuilder.build(); // CraftBukkit - decompile error

                resultNodes.put(commandnode2, commandnode3);
                result.addChild(commandnode3);
                if (!commandnode2.getChildren().isEmpty()) {
                    this.fillUsableCommands(commandnode2, commandnode3, source, resultNodes);
                }
            }
        }

    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(String literal) {
        return LiteralArgumentBuilder.literal(literal);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public static Predicate<String> createValidator(Commands.ParseFunction parser) {
        return (s) -> {
            try {
                parser.parse(new StringReader(s));
                return true;
            } catch (CommandSyntaxException commandsyntaxexception) {
                return false;
            }
        };
    }

    public com.mojang.brigadier.CommandDispatcher<CommandSourceStack> getDispatcher() {
        return this.dispatcher;
    }

    public static <S> void validateParseResults(ParseResults<S> parse) throws CommandSyntaxException {
        CommandSyntaxException commandsyntaxexception = Commands.getParseException(parse);

        if (commandsyntaxexception != null) {
            throw commandsyntaxexception;
        }
    }

    @Nullable
    public static <S> CommandSyntaxException getParseException(ParseResults<S> parse) {
        return !parse.getReader().canRead() ? null : (parse.getExceptions().size() == 1 ? (CommandSyntaxException) parse.getExceptions().values().iterator().next() : (parse.getContext().getRange().isEmpty() ? CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(parse.getReader()) : CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(parse.getReader())));
    }

    public static CommandBuildContext createValidationContext(final HolderLookup.Provider registryLookup) {
        return new CommandBuildContext() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                return registryLookup.listRegistries();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<? extends Registry<? extends T>> registryRef) {
                return registryLookup.lookup(registryRef).map(this::createLookup);
            }

            private <T> HolderLookup.RegistryLookup.Delegate<T> createLookup(final HolderLookup.RegistryLookup<T> original) {
                return new HolderLookup.RegistryLookup.Delegate<T>() { // CraftBukkit - decompile error
                    @Override
                    public HolderLookup.RegistryLookup<T> parent() {
                        return original;
                    }

                    @Override
                    public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
                        return Optional.of(this.getOrThrow(tag));
                    }

                    @Override
                    public HolderSet.Named<T> getOrThrow(TagKey<T> tag) {
                        Optional<HolderSet.Named<T>> optional = this.parent().get(tag);

                        return (HolderSet.Named) optional.orElseGet(() -> {
                            return HolderSet.emptyNamed(this.parent(), tag);
                        });
                    }
                };
            }
        };
    }

    public static void validate() {
        CommandBuildContext commandbuildcontext = Commands.createValidationContext(VanillaRegistries.createLookup());
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> com_mojang_brigadier_commanddispatcher = (new Commands(Commands.CommandSelection.ALL, commandbuildcontext)).getDispatcher();
        RootCommandNode<CommandSourceStack> rootcommandnode = com_mojang_brigadier_commanddispatcher.getRoot();

        com_mojang_brigadier_commanddispatcher.findAmbiguities((commandnode, commandnode1, commandnode2, collection) -> {
            Commands.LOGGER.warn("Ambiguity between arguments {} and {} with inputs: {}", new Object[]{com_mojang_brigadier_commanddispatcher.getPath(commandnode1), com_mojang_brigadier_commanddispatcher.getPath(commandnode2), collection});
        });
        Set<ArgumentType<?>> set = ArgumentUtils.findUsedArgumentTypes(rootcommandnode);
        Set<ArgumentType<?>> set1 = (Set) set.stream().filter((argumenttype) -> {
            return !ArgumentTypeInfos.isClassRecognized(argumenttype.getClass());
        }).collect(Collectors.toSet());

        if (!set1.isEmpty()) {
            Commands.LOGGER.warn("Missing type registration for following arguments:\n {}", set1.stream().map((argumenttype) -> {
                return "\t" + String.valueOf(argumenttype);
            }).collect(Collectors.joining(",\n")));
            throw new IllegalStateException("Unregistered argument types");
        }
    }

    public static enum CommandSelection {

        ALL(true, true), DEDICATED(false, true), INTEGRATED(true, false);

        final boolean includeIntegrated;
        final boolean includeDedicated;

        private CommandSelection(final boolean flag, final boolean flag1) {
            this.includeIntegrated = flag;
            this.includeDedicated = flag1;
        }
    }

    @FunctionalInterface
    public interface ParseFunction {

        void parse(StringReader reader) throws CommandSyntaxException;
    }
}
