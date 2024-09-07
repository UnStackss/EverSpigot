package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;

public class DataPackCommand {
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_PACK = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.unknown", name)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_ENABLED = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.enable.failed", name)
    );
    private static final DynamicCommandExceptionType ERROR_PACK_ALREADY_DISABLED = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.disable.failed", name)
    );
    private static final DynamicCommandExceptionType ERROR_CANNOT_DISABLE_FEATURE = new DynamicCommandExceptionType(
        name -> Component.translatableEscape("commands.datapack.disable.failed.feature", name)
    );
    private static final Dynamic2CommandExceptionType ERROR_PACK_FEATURES_NOT_ENABLED = new Dynamic2CommandExceptionType(
        (name, flags) -> Component.translatableEscape("commands.datapack.enable.failed.no_flags", name, flags)
    );
    private static final SuggestionProvider<CommandSourceStack> SELECTED_PACKS = (context, builder) -> SharedSuggestionProvider.suggest(
            context.getSource().getServer().getPackRepository().getSelectedIds().stream().map(StringArgumentType::escapeIfRequired), builder
        );
    private static final SuggestionProvider<CommandSourceStack> UNSELECTED_PACKS = (context, builder) -> {
        PackRepository packRepository = context.getSource().getServer().getPackRepository();
        Collection<String> collection = packRepository.getSelectedIds();
        FeatureFlagSet featureFlagSet = context.getSource().enabledFeatures();
        return SharedSuggestionProvider.suggest(
            packRepository.getAvailablePacks()
                .stream()
                .filter(profile -> profile.getRequestedFeatures().isSubsetOf(featureFlagSet))
                .map(Pack::getId)
                .filter(name -> !collection.contains(name))
                .map(StringArgumentType::escapeIfRequired),
            builder
        );
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("datapack")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("enable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(UNSELECTED_PACKS)
                                .executes(
                                    context -> enablePack(
                                            context.getSource(),
                                            getPack(context, "name", true),
                                            (profiles, profile) -> profile.getDefaultPosition().insert(profiles, profile, Pack::selectionConfig, false)
                                        )
                                )
                                .then(
                                    Commands.literal("after")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    context -> enablePack(
                                                            context.getSource(),
                                                            getPack(context, "name", true),
                                                            (profiles, profile) -> profiles.add(
                                                                    profiles.indexOf(getPack(context, "existing", false)) + 1, profile
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("before")
                                        .then(
                                            Commands.argument("existing", StringArgumentType.string())
                                                .suggests(SELECTED_PACKS)
                                                .executes(
                                                    context -> enablePack(
                                                            context.getSource(),
                                                            getPack(context, "name", true),
                                                            (profiles, profile) -> profiles.add(profiles.indexOf(getPack(context, "existing", false)), profile)
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("last").executes(context -> enablePack(context.getSource(), getPack(context, "name", true), List::add)))
                                .then(
                                    Commands.literal("first")
                                        .executes(
                                            context -> enablePack(
                                                    context.getSource(), getPack(context, "name", true), (profiles, profile) -> profiles.add(0, profile)
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("disable")
                        .then(
                            Commands.argument("name", StringArgumentType.string())
                                .suggests(SELECTED_PACKS)
                                .executes(context -> disablePack(context.getSource(), getPack(context, "name", false)))
                        )
                )
                .then(
                    Commands.literal("list")
                        .executes(context -> listPacks(context.getSource()))
                        .then(Commands.literal("available").executes(context -> listAvailablePacks(context.getSource())))
                        .then(Commands.literal("enabled").executes(context -> listEnabledPacks(context.getSource())))
                )
        );
    }

    private static int enablePack(CommandSourceStack source, Pack container, DataPackCommand.Inserter packAdder) throws CommandSyntaxException {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        packAdder.apply(list, container);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.enable", container.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int disablePack(CommandSourceStack source, Pack container) {
        PackRepository packRepository = source.getServer().getPackRepository();
        List<Pack> list = Lists.newArrayList(packRepository.getSelectedPacks());
        list.remove(container);
        source.sendSuccess(() -> Component.translatable("commands.datapack.modify.disable", container.getChatLink(true)), true);
        ReloadCommand.reloadPacks(list.stream().map(Pack::getId).collect(Collectors.toList()), source);
        return list.size();
    }

    private static int listPacks(CommandSourceStack source) {
        return listEnabledPacks(source) + listAvailablePacks(source);
    }

    private static int listAvailablePacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        packRepository.reload();
        Collection<Pack> collection = packRepository.getSelectedPacks();
        Collection<Pack> collection2 = packRepository.getAvailablePacks();
        FeatureFlagSet featureFlagSet = source.enabledFeatures();
        List<Pack> list = collection2.stream()
            .filter(profile -> !collection.contains(profile) && profile.getRequestedFeatures().isSubsetOf(featureFlagSet))
            .toList();
        if (list.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.available.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.datapack.list.available.success", list.size(), ComponentUtils.formatList(list, profile -> profile.getChatLink(false))
                    ),
                false
            );
        }

        return list.size();
    }

    private static int listEnabledPacks(CommandSourceStack source) {
        PackRepository packRepository = source.getServer().getPackRepository();
        packRepository.reload();
        Collection<? extends Pack> collection = packRepository.getSelectedPacks();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.datapack.list.enabled.none"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.datapack.list.enabled.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, profile -> profile.getChatLink(true))
                    ),
                false
            );
        }

        return collection.size();
    }

    private static Pack getPack(CommandContext<CommandSourceStack> context, String name, boolean enable) throws CommandSyntaxException {
        String string = StringArgumentType.getString(context, name);
        PackRepository packRepository = context.getSource().getServer().getPackRepository();
        Pack pack = packRepository.getPack(string);
        if (pack == null) {
            throw ERROR_UNKNOWN_PACK.create(string);
        } else {
            boolean bl = packRepository.getSelectedPacks().contains(pack);
            if (enable && bl) {
                throw ERROR_PACK_ALREADY_ENABLED.create(string);
            } else if (!enable && !bl) {
                throw ERROR_PACK_ALREADY_DISABLED.create(string);
            } else {
                FeatureFlagSet featureFlagSet = context.getSource().enabledFeatures();
                FeatureFlagSet featureFlagSet2 = pack.getRequestedFeatures();
                if (!enable && !featureFlagSet2.isEmpty() && pack.getPackSource() == PackSource.FEATURE) {
                    throw ERROR_CANNOT_DISABLE_FEATURE.create(string);
                } else if (!featureFlagSet2.isSubsetOf(featureFlagSet)) {
                    throw ERROR_PACK_FEATURES_NOT_ENABLED.create(string, FeatureFlags.printMissingFlags(featureFlagSet, featureFlagSet2));
                } else {
                    return pack;
                }
            }
        }
    }

    interface Inserter {
        void apply(List<Pack> profiles, Pack profile) throws CommandSyntaxException;
    }
}
