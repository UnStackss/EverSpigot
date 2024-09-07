package net.minecraft.world.scores;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

public class Scoreboard {
    public static final String HIDDEN_SCORE_PREFIX = "#";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Object2ObjectMap<String, Objective> objectivesByName = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Reference2ObjectMap<ObjectiveCriteria, List<Objective>> objectivesByCriteria = new Reference2ObjectOpenHashMap<>();
    private final Map<String, PlayerScores> playerScores = new Object2ObjectOpenHashMap<>(16, 0.5F);
    private final Map<DisplaySlot, Objective> displayObjectives = new EnumMap<>(DisplaySlot.class);
    private final Object2ObjectMap<String, PlayerTeam> teamsByName = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<String, PlayerTeam> teamsByPlayer = new Object2ObjectOpenHashMap<>();

    @Nullable
    public Objective getObjective(@Nullable String name) {
        return this.objectivesByName.get(name);
    }

    public Objective addObjective(
        String name,
        ObjectiveCriteria criterion,
        Component displayName,
        ObjectiveCriteria.RenderType renderType,
        boolean displayAutoUpdate,
        @Nullable NumberFormat numberFormat
    ) {
        if (this.objectivesByName.containsKey(name)) {
            throw new IllegalArgumentException("An objective with the name '" + name + "' already exists!");
        } else {
            Objective objective = new Objective(this, name, criterion, displayName, renderType, displayAutoUpdate, numberFormat);
            this.objectivesByCriteria.computeIfAbsent(criterion, criterion2 -> Lists.newArrayList()).add(objective);
            this.objectivesByName.put(name, objective);
            this.onObjectiveAdded(objective);
            return objective;
        }
    }

    public final void forAllObjectives(ObjectiveCriteria criterion, ScoreHolder scoreHolder, Consumer<ScoreAccess> action) {
        this.objectivesByCriteria
            .getOrDefault(criterion, Collections.emptyList())
            .forEach(objective -> action.accept(this.getOrCreatePlayerScore(scoreHolder, objective, true)));
    }

    private PlayerScores getOrCreatePlayerInfo(String scoreHolderName) {
        return this.playerScores.computeIfAbsent(scoreHolderName, name -> new PlayerScores());
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        return this.getOrCreatePlayerScore(scoreHolder, objective, false);
    }

    public ScoreAccess getOrCreatePlayerScore(ScoreHolder scoreHolder, Objective objective, boolean forceWritable) {
        final boolean bl = forceWritable || !objective.getCriteria().isReadOnly();
        PlayerScores playerScores = this.getOrCreatePlayerInfo(scoreHolder.getScoreboardName());
        final MutableBoolean mutableBoolean = new MutableBoolean();
        final Score score = playerScores.getOrCreate(objective, scorex -> mutableBoolean.setTrue());
        return new ScoreAccess() {
            @Override
            public int get() {
                return score.value();
            }

            @Override
            public void set(int score) {
                if (!bl) {
                    throw new IllegalStateException("Cannot modify read-only score");
                } else {
                    boolean bl = mutableBoolean.isTrue();
                    if (objective.displayAutoUpdate()) {
                        Component component = scoreHolder.getDisplayName();
                        if (component != null && !component.equals(score.display())) {
                            score.display(component);
                            bl = true;
                        }
                    }

                    if (score != score.value()) {
                        score.value(score);
                        bl = true;
                    }

                    if (bl) {
                        this.sendScoreToPlayers();
                    }
                }
            }

            @Nullable
            @Override
            public Component display() {
                return score.display();
            }

            @Override
            public void display(@Nullable Component text) {
                if (mutableBoolean.isTrue() || !Objects.equals(text, score.display())) {
                    score.display(text);
                    this.sendScoreToPlayers();
                }
            }

            @Override
            public void numberFormatOverride(@Nullable NumberFormat numberFormat) {
                score.numberFormat(numberFormat);
                this.sendScoreToPlayers();
            }

            @Override
            public boolean locked() {
                return score.isLocked();
            }

            @Override
            public void unlock() {
                this.setLocked(false);
            }

            @Override
            public void lock() {
                this.setLocked(true);
            }

            private void setLocked(boolean locked) {
                score.setLocked(locked);
                if (mutableBoolean.isTrue()) {
                    this.sendScoreToPlayers();
                }

                Scoreboard.this.onScoreLockChanged(scoreHolder, objective);
            }

            private void sendScoreToPlayers() {
                Scoreboard.this.onScoreChanged(scoreHolder, objective, score);
                mutableBoolean.setFalse();
            }
        };
    }

    @Nullable
    public ReadOnlyScoreInfo getPlayerScoreInfo(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerScores != null ? playerScores.get(objective) : null;
    }

    public Collection<PlayerScoreEntry> listPlayerScores(Objective objective) {
        List<PlayerScoreEntry> list = new ArrayList<>();
        this.playerScores.forEach((scoreHolderName, scores) -> {
            Score score = scores.get(objective);
            if (score != null) {
                list.add(new PlayerScoreEntry(scoreHolderName, score.value(), score.display(), score.numberFormat()));
            }
        });
        return list;
    }

    public Collection<Objective> getObjectives() {
        return this.objectivesByName.values();
    }

    public Collection<String> getObjectiveNames() {
        return this.objectivesByName.keySet();
    }

    public Collection<ScoreHolder> getTrackedPlayers() {
        return this.playerScores.keySet().stream().map(ScoreHolder::forNameOnly).toList();
    }

    public void resetAllPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerScores = this.playerScores.remove(scoreHolder.getScoreboardName());
        if (playerScores != null) {
            this.onPlayerRemoved(scoreHolder);
        }
    }

    public void resetSinglePlayerScore(ScoreHolder scoreHolder, Objective objective) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        if (playerScores != null) {
            boolean bl = playerScores.remove(objective);
            if (!playerScores.hasScores()) {
                PlayerScores playerScores2 = this.playerScores.remove(scoreHolder.getScoreboardName());
                if (playerScores2 != null) {
                    this.onPlayerRemoved(scoreHolder);
                }
            } else if (bl) {
                this.onPlayerScoreRemoved(scoreHolder, objective);
            }
        }
    }

    public Object2IntMap<Objective> listPlayerScores(ScoreHolder scoreHolder) {
        PlayerScores playerScores = this.playerScores.get(scoreHolder.getScoreboardName());
        return playerScores != null ? playerScores.listScores() : Object2IntMaps.emptyMap();
    }

    public void removeObjective(Objective objective) {
        this.objectivesByName.remove(objective.getName());

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            if (this.getDisplayObjective(displaySlot) == objective) {
                this.setDisplayObjective(displaySlot, null);
            }
        }

        List<Objective> list = this.objectivesByCriteria.get(objective.getCriteria());
        if (list != null) {
            list.remove(objective);
        }

        for (PlayerScores playerScores : this.playerScores.values()) {
            playerScores.remove(objective);
        }

        this.onObjectiveRemoved(objective);
    }

    public void setDisplayObjective(DisplaySlot slot, @Nullable Objective objective) {
        this.displayObjectives.put(slot, objective);
    }

    @Nullable
    public Objective getDisplayObjective(DisplaySlot slot) {
        return this.displayObjectives.get(slot);
    }

    @Nullable
    public PlayerTeam getPlayerTeam(String name) {
        return this.teamsByName.get(name);
    }

    public PlayerTeam addPlayerTeam(String name) {
        PlayerTeam playerTeam = this.getPlayerTeam(name);
        if (playerTeam != null) {
            LOGGER.warn("Requested creation of existing team '{}'", name);
            return playerTeam;
        } else {
            playerTeam = new PlayerTeam(this, name);
            this.teamsByName.put(name, playerTeam);
            this.onTeamAdded(playerTeam);
            return playerTeam;
        }
    }

    public void removePlayerTeam(PlayerTeam team) {
        this.teamsByName.remove(team.getName());

        for (String string : team.getPlayers()) {
            this.teamsByPlayer.remove(string);
        }

        this.onTeamRemoved(team);
    }

    public boolean addPlayerToTeam(String scoreHolderName, PlayerTeam team) {
        if (this.getPlayersTeam(scoreHolderName) != null) {
            this.removePlayerFromTeam(scoreHolderName);
        }

        this.teamsByPlayer.put(scoreHolderName, team);
        return team.getPlayers().add(scoreHolderName);
    }

    public boolean removePlayerFromTeam(String scoreHolderName) {
        PlayerTeam playerTeam = this.getPlayersTeam(scoreHolderName);
        if (playerTeam != null) {
            this.removePlayerFromTeam(scoreHolderName, playerTeam);
            return true;
        } else {
            return false;
        }
    }

    public void removePlayerFromTeam(String scoreHolderName, PlayerTeam team) {
        if (this.getPlayersTeam(scoreHolderName) != team) {
            throw new IllegalStateException("Player is either on another team or not on any team. Cannot remove from team '" + team.getName() + "'.");
        } else {
            this.teamsByPlayer.remove(scoreHolderName);
            team.getPlayers().remove(scoreHolderName);
        }
    }

    public Collection<String> getTeamNames() {
        return this.teamsByName.keySet();
    }

    public Collection<PlayerTeam> getPlayerTeams() {
        return this.teamsByName.values();
    }

    @Nullable
    public PlayerTeam getPlayersTeam(String scoreHolderName) {
        return this.teamsByPlayer.get(scoreHolderName);
    }

    public void onObjectiveAdded(Objective objective) {
    }

    public void onObjectiveChanged(Objective objective) {
    }

    public void onObjectiveRemoved(Objective objective) {
    }

    protected void onScoreChanged(ScoreHolder scoreHolder, Objective objective, Score score) {
    }

    protected void onScoreLockChanged(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onPlayerRemoved(ScoreHolder scoreHolder) {
    }

    public void onPlayerScoreRemoved(ScoreHolder scoreHolder, Objective objective) {
    }

    public void onTeamAdded(PlayerTeam team) {
    }

    public void onTeamChanged(PlayerTeam team) {
    }

    public void onTeamRemoved(PlayerTeam team) {
    }

    public void entityRemoved(Entity entity) {
        if (!(entity instanceof Player) && !entity.isAlive()) {
            this.resetAllPlayerScores(entity);
            this.removePlayerFromTeam(entity.getScoreboardName());
        }
    }

    protected ListTag savePlayerScores(HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();
        this.playerScores.forEach((name, scores) -> scores.listRawScores().forEach((objective, score) -> {
                CompoundTag compoundTag = score.write(registries);
                compoundTag.putString("Name", name);
                compoundTag.putString("Objective", objective.getName());
                listTag.add(compoundTag);
            }));
        return listTag;
    }

    protected void loadPlayerScores(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag compoundTag = list.getCompound(i);
            Score score = Score.read(compoundTag, registries);
            String string = compoundTag.getString("Name");
            String string2 = compoundTag.getString("Objective");
            Objective objective = this.getObjective(string2);
            if (objective == null) {
                LOGGER.error("Unknown objective {} for name {}, ignoring", string2, string);
            } else {
                this.getOrCreatePlayerInfo(string).setScore(objective, score);
            }
        }
    }
}
