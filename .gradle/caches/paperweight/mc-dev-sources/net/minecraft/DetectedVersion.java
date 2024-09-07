package net.minecraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.DataVersion;
import org.slf4j.Logger;

public class DetectedVersion implements WorldVersion {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final WorldVersion BUILT_IN = new DetectedVersion();
    private final String id;
    private final String name;
    private final boolean stable;
    private final DataVersion worldVersion;
    private final int protocolVersion;
    private final int resourcePackVersion;
    private final int dataPackVersion;
    private final Date buildTime;

    private DetectedVersion() {
        this.id = UUID.randomUUID().toString().replaceAll("-", "");
        this.name = "1.21.1";
        this.stable = true;
        this.worldVersion = new DataVersion(3955, "main");
        this.protocolVersion = SharedConstants.getProtocolVersion();
        this.resourcePackVersion = 34;
        this.dataPackVersion = 48;
        this.buildTime = new Date();
    }

    private DetectedVersion(JsonObject json) {
        this.id = GsonHelper.getAsString(json, "id");
        this.name = GsonHelper.getAsString(json, "name");
        this.stable = GsonHelper.getAsBoolean(json, "stable");
        this.worldVersion = new DataVersion(GsonHelper.getAsInt(json, "world_version"), GsonHelper.getAsString(json, "series_id", DataVersion.MAIN_SERIES));
        this.protocolVersion = GsonHelper.getAsInt(json, "protocol_version");
        JsonObject jsonObject = GsonHelper.getAsJsonObject(json, "pack_version");
        this.resourcePackVersion = GsonHelper.getAsInt(jsonObject, "resource");
        this.dataPackVersion = GsonHelper.getAsInt(jsonObject, "data");
        this.buildTime = Date.from(ZonedDateTime.parse(GsonHelper.getAsString(json, "build_time")).toInstant());
    }

    public static WorldVersion tryDetectVersion() {
        try {
            DetectedVersion var2;
            try (InputStream inputStream = DetectedVersion.class.getResourceAsStream("/version.json")) {
                if (inputStream == null) {
                    LOGGER.warn("Missing version information!");
                    return BUILT_IN;
                }

                try (InputStreamReader inputStreamReader = new InputStreamReader(inputStream)) {
                    var2 = new DetectedVersion(GsonHelper.parse(inputStreamReader));
                }
            }

            return var2;
        } catch (JsonParseException | IOException var8) {
            throw new IllegalStateException("Game version information is corrupt", var8);
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public DataVersion getDataVersion() {
        return this.worldVersion;
    }

    @Override
    public int getProtocolVersion() {
        return this.protocolVersion;
    }

    @Override
    public int getPackVersion(PackType type) {
        return type == PackType.SERVER_DATA ? this.dataPackVersion : this.resourcePackVersion;
    }

    @Override
    public Date getBuildTime() {
        return this.buildTime;
    }

    @Override
    public boolean isStable() {
        return this.stable;
    }
}
