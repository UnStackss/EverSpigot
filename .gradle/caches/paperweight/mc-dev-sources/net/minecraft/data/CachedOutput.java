package net.minecraft.data;

import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.FileUtil;

public interface CachedOutput {
    CachedOutput NO_CACHE = (path, data, hashCode) -> {
        FileUtil.createDirectoriesSafe(path.getParent());
        Files.write(path, data);
    };

    void writeIfNeeded(Path path, byte[] data, HashCode hashCode) throws IOException;
}
