package net.minecraft.world.level.chunk.storage;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import javax.annotation.Nullable;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.minecraft.util.FastBufferedInputStream;
import org.slf4j.Logger;

public class RegionFileVersion {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Int2ObjectMap<RegionFileVersion> VERSIONS = new Int2ObjectOpenHashMap<>(); // Paper - private -> public
    private static final Object2ObjectMap<String, RegionFileVersion> VERSIONS_BY_NAME = new Object2ObjectOpenHashMap<>();
    public static final RegionFileVersion VERSION_GZIP = register(
        new RegionFileVersion(
            1, null, stream -> new FastBufferedInputStream(new GZIPInputStream(stream)), stream -> new BufferedOutputStream(new GZIPOutputStream(stream))
        )
    );
    public static final RegionFileVersion VERSION_DEFLATE = register(
        new RegionFileVersion(
            2,
            "deflate",
            stream -> new FastBufferedInputStream(new InflaterInputStream(stream)),
            stream -> new BufferedOutputStream(new DeflaterOutputStream(stream))
        )
    );
    public static final RegionFileVersion VERSION_NONE = register(new RegionFileVersion(3, "none", FastBufferedInputStream::new, BufferedOutputStream::new));
    public static final RegionFileVersion VERSION_LZ4 = register(
        new RegionFileVersion(
            4,
            "lz4",
            stream -> new FastBufferedInputStream(new LZ4BlockInputStream(stream)),
            stream -> new BufferedOutputStream(new LZ4BlockOutputStream(stream))
        )
    );
    public static final RegionFileVersion VERSION_CUSTOM = register(new RegionFileVersion(127, null, stream -> {
        throw new UnsupportedOperationException();
    }, stream -> {
        throw new UnsupportedOperationException();
    }));
    public static final RegionFileVersion DEFAULT = VERSION_DEFLATE;
    private static volatile RegionFileVersion selected = DEFAULT;
    private final int id;
    @Nullable
    private final String optionName;
    private final RegionFileVersion.StreamWrapper<InputStream> inputWrapper;
    private final RegionFileVersion.StreamWrapper<OutputStream> outputWrapper;

    // Paper start - Configurable region compression format
    public static RegionFileVersion getCompressionFormat() {
        return switch (io.papermc.paper.configuration.GlobalConfiguration.get().unsupportedSettings.compressionFormat) {
            case GZIP -> VERSION_GZIP;
            case ZLIB -> VERSION_DEFLATE;
            case NONE -> VERSION_NONE;
        };
    }
    // Paper end - Configurable region compression format
    private RegionFileVersion(
        int id,
        @Nullable String name,
        RegionFileVersion.StreamWrapper<InputStream> inputStreamWrapper,
        RegionFileVersion.StreamWrapper<OutputStream> outputStreamWrapper
    ) {
        this.id = id;
        this.optionName = name;
        this.inputWrapper = inputStreamWrapper;
        this.outputWrapper = outputStreamWrapper;
    }

    private static RegionFileVersion register(RegionFileVersion version) {
        VERSIONS.put(version.id, version);
        if (version.optionName != null) {
            VERSIONS_BY_NAME.put(version.optionName, version);
        }

        return version;
    }

    @Nullable
    public static RegionFileVersion fromId(int id) {
        return VERSIONS.get(id);
    }

    public static void configure(String name) {
        RegionFileVersion regionFileVersion = VERSIONS_BY_NAME.get(name);
        if (regionFileVersion != null) {
            selected = regionFileVersion;
        } else {
            LOGGER.error(
                "Invalid `region-file-compression` value `{}` in server.properties. Please use one of: {}", name, String.join(", ", VERSIONS_BY_NAME.keySet())
            );
        }
    }

    public static RegionFileVersion getSelected() {
        return selected;
    }

    public static boolean isValidVersion(int id) {
        return VERSIONS.containsKey(id);
    }

    public int getId() {
        return this.id;
    }

    public OutputStream wrap(OutputStream outputStream) throws IOException {
        return this.outputWrapper.wrap(outputStream);
    }

    public InputStream wrap(InputStream inputStream) throws IOException {
        return this.inputWrapper.wrap(inputStream);
    }

    @FunctionalInterface
    interface StreamWrapper<O> {
        O wrap(O object) throws IOException;
    }
}
