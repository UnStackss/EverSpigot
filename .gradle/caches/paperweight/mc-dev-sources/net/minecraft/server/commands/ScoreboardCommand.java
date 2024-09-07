package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ComponentArgument;
import net.minecraft.commands.arguments.ObjectiveArgument;
import net.minecraft.commands.arguments.ObjectiveCriteriaArgument;
import net.minecraft.commands.arguments.OperationArgument;
import net.minecraft.commands.arguments.ScoreHolderArgument;
import net.minecraft.commands.arguments.ScoreboardSlotArgument;
import net.minecraft.commands.arguments.StyleArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ScoreboardCommand {
    private static final SimpleCommandExceptionType ERROR_OBJECTIVE_ALREADY_EXISTS = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.add.duplicate")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_EMPTY = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadyEmpty")
    );
    private static final SimpleCommandExceptionType ERROR_DISPLAY_SLOT_ALREADY_SET = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.objectives.display.alreadySet")
    );
    private static final SimpleCommandExceptionType ERROR_TRIGGER_ALREADY_ENABLED = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.failed")
    );
    private static final SimpleCommandExceptionType ERROR_NOT_TRIGGER = new SimpleCommandExceptionType(
        Component.translatable("commands.scoreboard.players.enable.invalid")
    );
    private static final Dynamic2CommandExceptionType ERROR_NO_VALUE = new Dynamic2CommandExceptionType(
        (objective, target) -> Component.translatableEscape("commands.scoreboard.players.get.null", objective, target)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(
            Commands.literal("scoreboard")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("objectives")
                        .then(Commands.literal("list").executes(context -> listObjectives(context.getSource())))
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("objective", StringArgumentType.word())
                                        .then(
                                            Commands.argument("criteria", ObjectiveCriteriaArgument.criteria())
                                                .executes(
                                                    context -> addObjective(
                                                            context.getSource(),
                                                            StringArgumentType.getString(context, "objective"),
                                                            ObjectiveCriteriaArgument.getCriteria(context, "criteria"),
                                                            Component.literal(StringArgumentType.getString(context, "objective"))
                                                        )
                                                )
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(registryAccess))
                                                        .executes(
                                                            context -> addObjective(
                                                                    context.getSource(),
                                                                    StringArgumentType.getString(context, "objective"),
                                                                    ObjectiveCriteriaArgument.getCriteria(context, "criteria"),
                                                                    ComponentArgument.getComponent(context, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("modify")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .then(
                                            Commands.literal("displayname")
                                                .then(
                                                    Commands.argument("displayName", ComponentArgument.textComponent(registryAccess))
                                                        .executes(
                                                            context -> setDisplayName(
                                                                    context.getSource(),
                                                                    ObjectiveArgument.getObjective(context, "objective"),
                                                                    ComponentArgument.getComponent(context, "displayName")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(createRenderTypeModify())
                                        .then(
                                            Commands.literal("displayautoupdate")
                                                .then(
                                                    Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(
                                                            commandContext -> setDisplayAutoUpdate(
                                                                    commandContext.getSource(),
                                                                    ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                    BoolArgumentType.getBool(commandContext, "value")
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            addNumberFormats(
                                                registryAccess,
                                                Commands.literal("numberformat"),
                                                (commandContext, numberFormat) -> setObjectiveFormat(
                                                        commandContext.getSource(), ObjectiveArgument.getObjective(commandContext, "objective"), numberFormat
                                                    )
                                            )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("objective", ObjectiveArgument.objective())
                                        .executes(context -> removeObjective(context.getSource(), ObjectiveArgument.getObjective(context, "objective")))
                                )
                        )
                        .then(
                            Commands.literal("setdisplay")
                                .then(
                                    Commands.argument("slot", ScoreboardSlotArgument.displaySlot())
                                        .executes(context -> clearDisplaySlot(context.getSource(), ScoreboardSlotArgument.getDisplaySlot(context, "slot")))
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    context -> setDisplaySlot(
                                                            context.getSource(),
                                                            ScoreboardSlotArgument.getDisplaySlot(context, "slot"),
                                                            ObjectiveArgument.getObjective(context, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("players")
                        .then(
                            Commands.literal("list")
                                .executes(context -> listTrackedPlayers(context.getSource()))
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(context -> listTrackedPlayerScores(context.getSource(), ScoreHolderArgument.getName(context, "target")))
                                )
                        )
                        .then(
                            Commands.literal("set")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer())
                                                        .executes(
                                                            context -> setScore(
                                                                    context.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(context, "objective"),
                                                                    IntegerArgumentType.getInteger(context, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("get")
                                .then(
                                    Commands.argument("target", ScoreHolderArgument.scoreHolder())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    context -> getScore(
                                                            context.getSource(),
                                                            ScoreHolderArgument.getName(context, "target"),
                                                            ObjectiveArgument.getObjective(context, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            context -> addScore(
                                                                    context.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(context, "objective"),
                                                                    IntegerArgumentType.getInteger(context, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("remove")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("score", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            context -> removeScore(
                                                                    context.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                                    ObjectiveArgument.getWritableObjective(context, "objective"),
                                                                    IntegerArgumentType.getInteger(context, "score")
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("reset")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .executes(
                                            context -> resetScores(context.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"))
                                        )
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .executes(
                                                    context -> resetScore(
                                                            context.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                            ObjectiveArgument.getObjective(context, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("enable")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("objective", ObjectiveArgument.objective())
                                                .suggests(
                                                    (context, builder) -> suggestTriggers(
                                                            context.getSource(), ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"), builder
                                                        )
                                                )
                                                .executes(
                                                    context -> enableTrigger(
                                                            context.getSource(),
                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                            ObjectiveArgument.getObjective(context, "objective")
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("display")
                                .then(
                                    Commands.literal("name")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    Commands.argument("objective", ObjectiveArgument.objective())
                                                        .then(
                                                            Commands.argument("name", ComponentArgument.textComponent(registryAccess))
                                                                .executes(
                                                                    commandContext -> setScoreDisplay(
                                                                            commandContext.getSource(),
                                                                            ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                            ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                            ComponentArgument.getComponent(commandContext, "name")
                                                                        )
                                                                )
                                                        )
                                                        .executes(
                                                            commandContext -> setScoreDisplay(
                                                                    commandContext.getSource(),
                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                    ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                    null
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(
                                    Commands.literal("numberformat")
                                        .then(
                                            Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                .then(
                                                    addNumberFormats(
                                                        registryAccess,
                                                        Commands.argument("objective", ObjectiveArgument.objective()),
                                                        (commandContext, numberFormat) -> setScoreNumberFormat(
                                                                commandContext.getSource(),
                                                                ScoreHolderArgument.getNamesWithDefaultWildcard(commandContext, "targets"),
                                                                ObjectiveArgument.getObjective(commandContext, "objective"),
                                                                numberFormat
                                                            )
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.literal("operation")
                                .then(
                                    Commands.argument("targets", ScoreHolderArgument.scoreHolders())
                                        .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                        .then(
                                            Commands.argument("targetObjective", ObjectiveArgument.objective())
                                                .then(
                                                    Commands.argument("operation", OperationArgument.operation())
                                                        .then(
                                                            Commands.argument("source", ScoreHolderArgument.scoreHolders())
                                                                .suggests(ScoreHolderArgument.SUGGEST_SCORE_HOLDERS)
                                                                .then(
                                                                    Commands.argument("sourceObjective", ObjectiveArgument.objective())
                                                                        .executes(
                                                                            context -> performOperation(
                                                                                    context.getSource(),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(context, "targets"),
                                                                                    ObjectiveArgument.getWritableObjective(context, "targetObjective"),
                                                                                    OperationArgument.getOperation(context, "operation"),
                                                                                    ScoreHolderArgument.getNamesWithDefaultWildcard(context, "source"),
                                                                                    ObjectiveArgument.getObjective(context, "sourceObjective")
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

    private static ArgumentBuilder<CommandSourceStack, ?> addNumberFormats(
        CommandBuildContext registryAccess, ArgumentBuilder<CommandSourceStack, ?> argumentBuilder, ScoreboardCommand.NumberFormatCommandExecutor executor
    ) {
        return argumentBuilder.then(Commands.literal("blank").executes(context -> executor.run(context, BlankFormat.INSTANCE)))
            .then(Commands.literal("fixed").then(Commands.argument("contents", ComponentArgument.textComponent(registryAccess)).executes(context -> {
                Component component = ComponentArgument.getComponent(context, "contents");
                return executor.run(context, new FixedFormat(component));
            })))
            .then(Commands.literal("styled").then(Commands.argument("style", StyleArgument.style(registryAccess)).executes(context -> {
                Style style = StyleArgument.getStyle(context, "style");
                return executor.run(context, new StyledFormat(style));
            })))
            .executes(context -> executor.run(context, null));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createRenderTypeModify() {
        LiteralArgumentBuilder<CommandSourceStack> literalArgumentBuilder = Commands.literal("rendertype");

        for (ObjectiveCriteria.RenderType renderType : ObjectiveCriteria.RenderType.values()) {
            literalArgumentBuilder.then(
                Commands.literal(renderType.getId())
                    .executes(context -> setRenderType(context.getSource(), ObjectiveArgument.getObjective(context, "objective"), renderType))
            );
        }

        return literalArgumentBuilder;
    }

    private static CompletableFuture<Suggestions> suggestTriggers(CommandSourceStack source, Collection<ScoreHolder> targets, SuggestionsBuilder builder) {
        List<String> list = Lists.newArrayList();
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (Objective objective : scoreboard.getObjectives()) {
            if (objective.getCriteria() == ObjectiveCriteria.TRIGGER) {
                boolean bl = false;

                for (ScoreHolder scoreHolder : targets) {
                    ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                    if (readOnlyScoreInfo == null || readOnlyScoreInfo.isLocked()) {
                        bl = true;
                        break;
                    }
                }

                if (bl) {
                    list.add(objective.getName());
                }
            }
        }

        return SharedSuggestionProvider.suggest(list, builder);
    }

    private static int getScore(CommandSourceStack source, ScoreHolder scoreHolder, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
        if (readOnlyScoreInfo == null) {
            throw ERROR_NO_VALUE.create(objective.getName(), scoreHolder.getFeedbackDisplayName());
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.get.success",
                        scoreHolder.getFeedbackDisplayName(),
                        readOnlyScoreInfo.value(),
                        objective.getFormattedDisplayName()
                    ),
                false
            );
            return readOnlyScoreInfo.value();
        }
    }

    private static Component getFirstTargetName(Collection<ScoreHolder> targets) {
        return targets.iterator().next().getFeedbackDisplayName();
    }

    private static int performOperation(
        CommandSourceStack source,
        Collection<ScoreHolder> targets,
        Objective targetObjective,
        OperationArgument.Operation operation,
        Collection<ScoreHolder> sources,
        Objective sourceObjectives
    ) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, targetObjective);

            for (ScoreHolder scoreHolder2 : sources) {
                ScoreAccess scoreAccess2 = scoreboard.getOrCreatePlayerScore(scoreHolder2, sourceObjectives);
                operation.apply(scoreAccess, scoreAccess2);
            }

            i += scoreAccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.operation.success.single", targetObjective.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.operation.success.multiple", targetObjective.getFormattedDisplayName(), targets.size()
                    ),
                true
            );
        }

        return i;
    }

    private static int enableTrigger(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) throws CommandSyntaxException {
        if (objective.getCriteria() != ObjectiveCriteria.TRIGGER) {
            throw ERROR_NOT_TRIGGER.create();
        } else {
            Scoreboard scoreboard = source.getServer().getScoreboard();
            int i = 0;

            for (ScoreHolder scoreHolder : targets) {
                ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
                if (scoreAccess.locked()) {
                    scoreAccess.unlock();
                    i++;
                }
            }

            if (i == 0) {
                throw ERROR_TRIGGER_ALREADY_ENABLED.create();
            } else {
                if (targets.size() == 1) {
                    source.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.enable.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                            ),
                        true
                    );
                } else {
                    source.sendSuccess(
                        () -> Component.translatable("commands.scoreboard.players.enable.success.multiple", objective.getFormattedDisplayName(), targets.size()),
                        true
                    );
                }

                return i;
            }
        }
    }

    private static int resetScores(CommandSourceStack source, Collection<ScoreHolder> targets) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.resetAllPlayerScores(scoreHolder);
        }

        if (targets.size() == 1) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.single", getFirstTargetName(targets)), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.reset.all.multiple", targets.size()), true);
        }

        return targets.size();
    }

    private static int resetScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.resetSinglePlayerScore(scoreHolder, objective);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.reset.specific.single", objective.getFormattedDisplayName(), getFirstTargetName(targets)
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.reset.specific.multiple", objective.getFormattedDisplayName(), targets.size()), true
            );
        }

        return targets.size();
    }

    private static int setScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int score) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).set(score);
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.set.success.single", objective.getFormattedDisplayName(), getFirstTargetName(targets), score
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.set.success.multiple", objective.getFormattedDisplayName(), targets.size(), score),
                true
            );
        }

        return score * targets.size();
    }

    private static int setScoreDisplay(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable Component displayName) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).display(displayName);
        }

        if (displayName == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.single", getFirstTargetName(targets), objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.name.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.single",
                        displayName,
                        getFirstTargetName(targets),
                        objective.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.name.set.success.multiple", displayName, targets.size(), objective.getFormattedDisplayName()
                    ),
                true
            );
        }

        return targets.size();
    }

    private static int setScoreNumberFormat(
        CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, @Nullable NumberFormat numberFormat
    ) {
        Scoreboard scoreboard = source.getServer().getScoreboard();

        for (ScoreHolder scoreHolder : targets) {
            scoreboard.getOrCreatePlayerScore(scoreHolder, objective).numberFormatOverride(numberFormat);
        }

        if (numberFormat == null) {
            if (targets.size() == 1) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.single",
                            getFirstTargetName(targets),
                            objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.players.display.numberFormat.clear.success.multiple", targets.size(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        } else if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.single", getFirstTargetName(targets), objective.getFormattedDisplayName()
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.display.numberFormat.set.success.multiple", targets.size(), objective.getFormattedDisplayName()
                    ),
                true
            );
        }

        return targets.size();
    }

    private static int addScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int score) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
            scoreAccess.set(scoreAccess.get() + score);
            i += scoreAccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.add.success.single", score, objective.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.add.success.multiple", score, objective.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return i;
    }

    private static int removeScore(CommandSourceStack source, Collection<ScoreHolder> targets, Objective objective, int score) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        int i = 0;

        for (ScoreHolder scoreHolder : targets) {
            ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(scoreHolder, objective);
            scoreAccess.set(scoreAccess.get() - score);
            i += scoreAccess.get();
        }

        if (targets.size() == 1) {
            int j = i;
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.remove.success.single", score, objective.getFormattedDisplayName(), getFirstTargetName(targets), j
                    ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.remove.success.multiple", score, objective.getFormattedDisplayName(), targets.size()),
                true
            );
        }

        return i;
    }

    private static int listTrackedPlayers(CommandSourceStack source) {
        Collection<ScoreHolder> collection = source.getServer().getScoreboard().getTrackedPlayers();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.players.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, ScoreHolder::getFeedbackDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    private static int listTrackedPlayerScores(CommandSourceStack source, ScoreHolder scoreHolder) {
        Object2IntMap<Objective> object2IntMap = source.getServer().getScoreboard().listPlayerScores(scoreHolder);
        if (object2IntMap.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.players.list.entity.empty", scoreHolder.getFeedbackDisplayName()), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.players.list.entity.success", scoreHolder.getFeedbackDisplayName(), object2IntMap.size()),
                false
            );
            Object2IntMaps.fastForEach(
                object2IntMap,
                entry -> source.sendSuccess(
                        () -> Component.translatable(
                                "commands.scoreboard.players.list.entity.entry", ((Objective)entry.getKey()).getFormattedDisplayName(), entry.getIntValue()
                            ),
                        false
                    )
            );
        }

        return object2IntMap.size();
    }

    private static int clearDisplaySlot(CommandSourceStack source, DisplaySlot slot) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == null) {
            throw ERROR_DISPLAY_SLOT_ALREADY_EMPTY.create();
        } else {
            scoreboard.setDisplayObjective(slot, null);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.display.cleared", slot.getSerializedName()), true);
            return 0;
        }
    }

    private static int setDisplaySlot(CommandSourceStack source, DisplaySlot slot, Objective objective) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getDisplayObjective(slot) == objective) {
            throw ERROR_DISPLAY_SLOT_ALREADY_SET.create();
        } else {
            scoreboard.setDisplayObjective(slot, objective);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.display.set", slot.getSerializedName(), objective.getDisplayName()), true
            );
            return 0;
        }
    }

    private static int setDisplayName(CommandSourceStack source, Objective objective, Component displayName) {
        if (!objective.getDisplayName().equals(displayName)) {
            objective.setDisplayName(displayName);
            source.sendSuccess(
                () -> Component.translatable("commands.scoreboard.objectives.modify.displayname", objective.getName(), objective.getFormattedDisplayName()),
                true
            );
        }

        return 0;
    }

    private static int setDisplayAutoUpdate(CommandSourceStack source, Objective objective, boolean enable) {
        if (objective.displayAutoUpdate() != enable) {
            objective.setDisplayAutoUpdate(enable);
            if (enable) {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.enable", objective.getName(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            } else {
                source.sendSuccess(
                    () -> Component.translatable(
                            "commands.scoreboard.objectives.modify.displayAutoUpdate.disable", objective.getName(), objective.getFormattedDisplayName()
                        ),
                    true
                );
            }
        }

        return 0;
    }

    private static int setObjectiveFormat(CommandSourceStack source, Objective objective, @Nullable NumberFormat format) {
        objective.setNumberFormat(format);
        if (format != null) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.set", objective.getName()), true);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.objectiveFormat.clear", objective.getName()), true);
        }

        return 0;
    }

    private static int setRenderType(CommandSourceStack source, Objective objective, ObjectiveCriteria.RenderType type) {
        if (objective.getRenderType() != type) {
            objective.setRenderType(type);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.modify.rendertype", objective.getFormattedDisplayName()), true);
        }

        return 0;
    }

    private static int removeObjective(CommandSourceStack source, Objective objective) {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        scoreboard.removeObjective(objective);
        source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.remove.success", objective.getFormattedDisplayName()), true);
        return scoreboard.getObjectives().size();
    }

    private static int addObjective(CommandSourceStack source, String objective, ObjectiveCriteria criteria, Component displayName) throws CommandSyntaxException {
        Scoreboard scoreboard = source.getServer().getScoreboard();
        if (scoreboard.getObjective(objective) != null) {
            throw ERROR_OBJECTIVE_ALREADY_EXISTS.create();
        } else {
            scoreboard.addObjective(objective, criteria, displayName, criteria.getDefaultRenderType(), false, null);
            Objective objective2 = scoreboard.getObjective(objective);
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.add.success", objective2.getFormattedDisplayName()), true);
            return scoreboard.getObjectives().size();
        }
    }

    private static int listObjectives(CommandSourceStack source) {
        Collection<Objective> collection = source.getServer().getScoreboard().getObjectives();
        if (collection.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.scoreboard.objectives.list.empty"), false);
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                        "commands.scoreboard.objectives.list.success",
                        collection.size(),
                        ComponentUtils.formatList(collection, Objective::getFormattedDisplayName)
                    ),
                false
            );
        }

        return collection.size();
    }

    @FunctionalInterface
    public interface NumberFormatCommandExecutor {
        int run(CommandContext<CommandSourceStack> context, @Nullable NumberFormat numberFormat) throws CommandSyntaxException;
    }
}
