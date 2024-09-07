package net.minecraft.server.packs;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public abstract class AbstractPackResources implements PackResources {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;

    protected AbstractPackResources(PackLocationInfo info) {
        this.location = info;
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> metaReader) throws IOException {
        IoSupplier<InputStream> ioSupplier = this.getRootResource(new String[]{"pack.mcmeta"});
        if (ioSupplier == null) {
            return null;
        } else {
            Object var4;
            try (InputStream inputStream = ioSupplier.get()) {
                var4 = getMetadataFromStream(metaReader, inputStream);
            }

            return (T)var4;
        }
    }

    @Nullable
    public static <T> T getMetadataFromStream(MetadataSectionSerializer<T> metaReader, InputStream inputStream) {
        JsonObject jsonObject;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            jsonObject = GsonHelper.parse(bufferedReader);
        } catch (Exception var9) {
            LOGGER.error("Couldn't load {} metadata", metaReader.getMetadataSectionName(), var9);
            return null;
        }

        if (!jsonObject.has(metaReader.getMetadataSectionName())) {
            return null;
        } else {
            try {
                return metaReader.fromJson(GsonHelper.getAsJsonObject(jsonObject, metaReader.getMetadataSectionName()));
            } catch (Exception var7) {
                LOGGER.error("Couldn't load {} metadata", metaReader.getMetadataSectionName(), var7);
                return null;
            }
        }
    }

    @Override
    public PackLocationInfo location() {
        return this.location;
    }
}
