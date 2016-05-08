package net.minecraft.world.scores;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.NumberFormatTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.slf4j.Logger;

public class ScoreboardSaveData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String FILE_ID = "scoreboard";
    private final Scoreboard scoreboard;

    public ScoreboardSaveData(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
    }

    public ScoreboardSaveData load(CompoundTag nbt, HolderLookup.Provider registries) {
        this.loadObjectives(nbt.getList("Objectives", 10), registries);
        this.scoreboard.loadPlayerScores(nbt.getList("PlayerScores", 10), registries);
        if (nbt.contains("DisplaySlots", 10)) {
            this.loadDisplaySlots(nbt.getCompound("DisplaySlots"));
        }

        if (nbt.contains("Teams", 9)) {
            this.loadTeams(nbt.getList("Teams", 10), registries);
        }

        return this;
    }

    private void loadTeams(ListTag nbt, HolderLookup.Provider registries) {
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag compoundTag = nbt.getCompound(i);
            String string = compoundTag.getString("Name");
            PlayerTeam playerTeam = this.scoreboard.addPlayerTeam(string);
            Component component = Component.Serializer.fromJson(compoundTag.getString("DisplayName"), registries);
            if (component != null) {
                playerTeam.setDisplayName(component);
            }

            if (compoundTag.contains("TeamColor", 8)) {
                playerTeam.setColor(ChatFormatting.getByName(compoundTag.getString("TeamColor")));
            }

            if (compoundTag.contains("AllowFriendlyFire", 99)) {
                playerTeam.setAllowFriendlyFire(compoundTag.getBoolean("AllowFriendlyFire"));
            }

            if (compoundTag.contains("SeeFriendlyInvisibles", 99)) {
                playerTeam.setSeeFriendlyInvisibles(compoundTag.getBoolean("SeeFriendlyInvisibles"));
            }

            if (compoundTag.contains("MemberNamePrefix", 8)) {
                Component component2 = Component.Serializer.fromJson(compoundTag.getString("MemberNamePrefix"), registries);
                if (component2 != null) {
                    playerTeam.setPlayerPrefix(component2);
                }
            }

            if (compoundTag.contains("MemberNameSuffix", 8)) {
                Component component3 = Component.Serializer.fromJson(compoundTag.getString("MemberNameSuffix"), registries);
                if (component3 != null) {
                    playerTeam.setPlayerSuffix(component3);
                }
            }

            if (compoundTag.contains("NameTagVisibility", 8)) {
                Team.Visibility visibility = Team.Visibility.byName(compoundTag.getString("NameTagVisibility"));
                if (visibility != null) {
                    playerTeam.setNameTagVisibility(visibility);
                }
            }

            if (compoundTag.contains("DeathMessageVisibility", 8)) {
                Team.Visibility visibility2 = Team.Visibility.byName(compoundTag.getString("DeathMessageVisibility"));
                if (visibility2 != null) {
                    playerTeam.setDeathMessageVisibility(visibility2);
                }
            }

            if (compoundTag.contains("CollisionRule", 8)) {
                Team.CollisionRule collisionRule = Team.CollisionRule.byName(compoundTag.getString("CollisionRule"));
                if (collisionRule != null) {
                    playerTeam.setCollisionRule(collisionRule);
                }
            }

            this.loadTeamPlayers(playerTeam, compoundTag.getList("Players", 8));
        }
    }

    private void loadTeamPlayers(PlayerTeam team, ListTag nbt) {
        for (int i = 0; i < nbt.size(); i++) {
            this.scoreboard.addPlayerToTeam(nbt.getString(i), team);
        }
    }

    private void loadDisplaySlots(CompoundTag nbt) {
        for (String string : nbt.getAllKeys()) {
            DisplaySlot displaySlot = DisplaySlot.CODEC.byName(string);
            if (displaySlot != null) {
                String string2 = nbt.getString(string);
                Objective objective = this.scoreboard.getObjective(string2);
                this.scoreboard.setDisplayObjective(displaySlot, objective);
            }
        }
    }

    private void loadObjectives(ListTag nbt, HolderLookup.Provider registries) {
        for (int i = 0; i < nbt.size(); i++) {
            CompoundTag compoundTag = nbt.getCompound(i);
            String string = compoundTag.getString("CriteriaName");
            ObjectiveCriteria objectiveCriteria = ObjectiveCriteria.byName(string).orElseGet(() -> {
                LOGGER.warn("Unknown scoreboard criteria {}, replacing with {}", string, ObjectiveCriteria.DUMMY.getName());
                return ObjectiveCriteria.DUMMY;
            });
            String string2 = compoundTag.getString("Name");
            Component component = Component.Serializer.fromJson(compoundTag.getString("DisplayName"), registries);
            ObjectiveCriteria.RenderType renderType = ObjectiveCriteria.RenderType.byId(compoundTag.getString("RenderType"));
            boolean bl = compoundTag.getBoolean("display_auto_update");
            NumberFormat numberFormat = NumberFormatTypes.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), compoundTag.get("format"))
                .result()
                .orElse(null);
            this.scoreboard.addObjective(string2, objectiveCriteria, component, renderType, bl, numberFormat);
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        nbt.put("Objectives", this.saveObjectives(registryLookup));
        nbt.put("PlayerScores", this.scoreboard.savePlayerScores(registryLookup));
        nbt.put("Teams", this.saveTeams(registryLookup));
        this.saveDisplaySlots(nbt);
        return nbt;
    }

    private ListTag saveTeams(HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();

        for (PlayerTeam playerTeam : this.scoreboard.getPlayerTeams()) {
            if (!io.papermc.paper.configuration.GlobalConfiguration.get().scoreboards.saveEmptyScoreboardTeams && playerTeam.getPlayers().isEmpty()) continue; // Paper - Don't save empty scoreboard teams to scoreboard.dat
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("Name", playerTeam.getName());
            compoundTag.putString("DisplayName", Component.Serializer.toJson(playerTeam.getDisplayName(), registries));
            if (playerTeam.getColor().getId() >= 0) {
                compoundTag.putString("TeamColor", playerTeam.getColor().getName());
            }

            compoundTag.putBoolean("AllowFriendlyFire", playerTeam.isAllowFriendlyFire());
            compoundTag.putBoolean("SeeFriendlyInvisibles", playerTeam.canSeeFriendlyInvisibles());
            compoundTag.putString("MemberNamePrefix", Component.Serializer.toJson(playerTeam.getPlayerPrefix(), registries));
            compoundTag.putString("MemberNameSuffix", Component.Serializer.toJson(playerTeam.getPlayerSuffix(), registries));
            compoundTag.putString("NameTagVisibility", playerTeam.getNameTagVisibility().name);
            compoundTag.putString("DeathMessageVisibility", playerTeam.getDeathMessageVisibility().name);
            compoundTag.putString("CollisionRule", playerTeam.getCollisionRule().name);
            ListTag listTag2 = new ListTag();

            for (String string : playerTeam.getPlayers()) {
                listTag2.add(StringTag.valueOf(string));
            }

            compoundTag.put("Players", listTag2);
            listTag.add(compoundTag);
        }

        return listTag;
    }

    private void saveDisplaySlots(CompoundTag nbt) {
        CompoundTag compoundTag = new CompoundTag();

        for (DisplaySlot displaySlot : DisplaySlot.values()) {
            Objective objective = this.scoreboard.getDisplayObjective(displaySlot);
            if (objective != null) {
                compoundTag.putString(displaySlot.getSerializedName(), objective.getName());
            }
        }

        if (!compoundTag.isEmpty()) {
            nbt.put("DisplaySlots", compoundTag);
        }
    }

    private ListTag saveObjectives(HolderLookup.Provider registries) {
        ListTag listTag = new ListTag();

        for (Objective objective : this.scoreboard.getObjectives()) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("Name", objective.getName());
            compoundTag.putString("CriteriaName", objective.getCriteria().getName());
            compoundTag.putString("DisplayName", Component.Serializer.toJson(objective.getDisplayName(), registries));
            compoundTag.putString("RenderType", objective.getRenderType().getId());
            compoundTag.putBoolean("display_auto_update", objective.displayAutoUpdate());
            NumberFormat numberFormat = objective.numberFormat();
            if (numberFormat != null) {
                NumberFormatTypes.CODEC
                    .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), numberFormat)
                    .ifSuccess(tag -> compoundTag.put("format", tag));
            }

            listTag.add(compoundTag);
        }

        return listTag;
    }
}
