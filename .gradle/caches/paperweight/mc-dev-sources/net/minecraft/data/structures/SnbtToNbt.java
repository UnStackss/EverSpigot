package net.minecraft.data.structures;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class SnbtToNbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackOutput output;
    private final Iterable<Path> inputFolders;
    private final List<SnbtToNbt.Filter> filters = Lists.newArrayList();

    public SnbtToNbt(PackOutput output, Iterable<Path> paths) {
        this.output = output;
        this.inputFolders = paths;
    }

    public SnbtToNbt addFilter(SnbtToNbt.Filter tweaker) {
        this.filters.add(tweaker);
        return this;
    }

    private CompoundTag applyFilters(String key, CompoundTag compound) {
        CompoundTag compoundTag = compound;

        for (SnbtToNbt.Filter filter : this.filters) {
            compoundTag = filter.apply(key, compoundTag);
        }

        return compoundTag;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        Path path = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = Lists.newArrayList();

        for (Path path2 : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture>supplyAsync(
                        () -> {
                            try {
                                CompletableFuture var5x;
                                try (Stream<Path> stream = Files.walk(path2)) {
                                    var5x = CompletableFuture.allOf(
                                        stream.filter(pathxx -> pathxx.toString().endsWith(".snbt")).map(pathxx -> CompletableFuture.runAsync(() -> {
                                                SnbtToNbt.TaskResult taskResult = this.readStructure(pathxx, this.getName(path2, pathxx));
                                                this.storeStructureIfChanged(writer, taskResult, path);
                                            }, Util.backgroundExecutor())).toArray(CompletableFuture[]::new)
                                    );
                                }

                                return var5x;
                            } catch (Exception var9) {
                                throw new RuntimeException("Failed to read structure input directory, aborting", var9);
                            }
                        },
                        Util.backgroundExecutor()
                    )
                    .thenCompose(future -> future)
            );
        }

        return Util.sequenceFailFast(list);
    }

    @Override
    public final String getName() {
        return "SNBT -> NBT";
    }

    private String getName(Path root, Path file) {
        String string = root.relativize(file).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".snbt".length());
    }

    private SnbtToNbt.TaskResult readStructure(Path path, String name) {
        try {
            SnbtToNbt.TaskResult var10;
            try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
                String string = IOUtils.toString(bufferedReader);
                CompoundTag compoundTag = this.applyFilters(name, NbtUtils.snbtToStructure(string));
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
                NbtIo.writeCompressed(compoundTag, hashingOutputStream);
                byte[] bs = byteArrayOutputStream.toByteArray();
                HashCode hashCode = hashingOutputStream.hash();
                var10 = new SnbtToNbt.TaskResult(name, bs, hashCode);
            }

            return var10;
        } catch (Throwable var13) {
            throw new SnbtToNbt.StructureConversionException(path, var13);
        }
    }

    private void storeStructureIfChanged(CachedOutput cache, SnbtToNbt.TaskResult data, Path root) {
        Path path = root.resolve(data.name + ".nbt");

        try {
            cache.writeIfNeeded(path, data.payload, data.hash);
        } catch (IOException var6) {
            LOGGER.error("Couldn't write structure {} at {}", data.name, path, var6);
        }
    }

    @FunctionalInterface
    public interface Filter {
        CompoundTag apply(String name, CompoundTag nbt);
    }

    static class StructureConversionException extends RuntimeException {
        public StructureConversionException(Path path, Throwable cause) {
            super(path.toAbsolutePath().toString(), cause);
        }
    }

    static record TaskResult(String name, byte[] payload, HashCode hash) {
    }
}
