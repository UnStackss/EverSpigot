package net.minecraft.world.level;

import com.mojang.serialization.Dynamic;
import net.minecraft.world.Difficulty;

public final class LevelSettings {
    public String levelName;
    private final GameType gameType;
    public boolean hardcore;
    private final Difficulty difficulty;
    private final boolean allowCommands;
    private final GameRules gameRules;
    private final WorldDataConfiguration dataConfiguration;

    public LevelSettings(
        String name,
        GameType gameMode,
        boolean hardcore,
        Difficulty difficulty,
        boolean allowCommands,
        GameRules gameRules,
        WorldDataConfiguration dataConfiguration
    ) {
        this.levelName = name;
        this.gameType = gameMode;
        this.hardcore = hardcore;
        this.difficulty = difficulty;
        this.allowCommands = allowCommands;
        this.gameRules = gameRules;
        this.dataConfiguration = dataConfiguration;
    }

    public static LevelSettings parse(Dynamic<?> dynamic, WorldDataConfiguration dataConfiguration) {
        GameType gameType = GameType.byId(dynamic.get("GameType").asInt(0));
        return new LevelSettings(
            dynamic.get("LevelName").asString(""),
            gameType,
            dynamic.get("hardcore").asBoolean(false),
            dynamic.get("Difficulty").asNumber().map(difficulty -> Difficulty.byId(difficulty.byteValue())).result().orElse(Difficulty.NORMAL),
            dynamic.get("allowCommands").asBoolean(gameType == GameType.CREATIVE),
            new GameRules(dynamic.get("GameRules")),
            dataConfiguration
        );
    }

    public String levelName() {
        return this.levelName;
    }

    public GameType gameType() {
        return this.gameType;
    }

    public boolean hardcore() {
        return this.hardcore;
    }

    public Difficulty difficulty() {
        return this.difficulty;
    }

    public boolean allowCommands() {
        return this.allowCommands;
    }

    public GameRules gameRules() {
        return this.gameRules;
    }

    public WorldDataConfiguration getDataConfiguration() {
        return this.dataConfiguration;
    }

    public LevelSettings withGameType(GameType mode) {
        return new LevelSettings(this.levelName, mode, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
    }

    public LevelSettings withDifficulty(Difficulty difficulty) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, difficulty, this.allowCommands, this.gameRules, this.dataConfiguration);
    }

    public LevelSettings withDataConfiguration(WorldDataConfiguration dataConfiguration) {
        return new LevelSettings(this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules, dataConfiguration);
    }

    public LevelSettings copy() {
        return new LevelSettings(
            this.levelName, this.gameType, this.hardcore, this.difficulty, this.allowCommands, this.gameRules.copy(), this.dataConfiguration
        );
    }
}
