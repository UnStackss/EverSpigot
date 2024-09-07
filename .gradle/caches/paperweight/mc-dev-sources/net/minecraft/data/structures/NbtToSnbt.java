package net.minecraft.data.structures;

import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.mojang.logging.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.FastBufferedInputStream;
import org.slf4j.Logger;

public class NbtToSnbt implements DataProvider {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Iterable<Path> inputFolders;
    private final PackOutput output;

    public NbtToSnbt(PackOutput output, Collection<Path> paths) {
        this.inputFolders = paths;
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput writer) {
        Path path = this.output.getOutputFolder();
        List<CompletableFuture<?>> list = new ArrayList<>();

        for (Path path2 : this.inputFolders) {
            list.add(
                CompletableFuture.<CompletableFuture>supplyAsync(
                        () -> {
                            try {
                                CompletableFuture var4;
                                try (Stream<Path> stream = Files.walk(path2)) {
                                    var4 = CompletableFuture.allOf(
                                        stream.filter(pathxx -> pathxx.toString().endsWith(".nbt"))
                                            .map(
                                                pathxx -> CompletableFuture.runAsync(
                                                        () -> convertStructure(writer, pathxx, getName(path2, pathxx), path), Util.ioPool()
                                                    )
                                            )
                                            .toArray(CompletableFuture[]::new)
                                    );
                                }

                                return var4;
                            } catch (IOException var8) {
                                LOGGER.error("Failed to read structure input directory", (Throwable)var8);
                                return CompletableFuture.completedFuture(null);
                            }
                        },
                        Util.backgroundExecutor()
                    )
                    .thenCompose(future -> future)
            );
        }

        return CompletableFuture.allOf(list.toArray(CompletableFuture[]::new));
    }

    @Override
    public final String getName() {
        return "NBT -> SNBT";
    }

    private static String getName(Path inputPath, Path filePath) {
        String string = inputPath.relativize(filePath).toString().replaceAll("\\\\", "/");
        return string.substring(0, string.length() - ".nbt".length());
    }

    @Nullable
    public static Path convertStructure(CachedOutput writer, Path inputPath, String filename, Path outputPath) {
        try {
            Path var7;
            try (
                InputStream inputStream = Files.newInputStream(inputPath);
                InputStream inputStream2 = new FastBufferedInputStream(inputStream);
            ) {
                Path path = outputPath.resolve(filename + ".snbt");
                writeSnbt(writer, path, NbtUtils.structureToSnbt(NbtIo.readCompressed(inputStream2, NbtAccounter.unlimitedHeap())));
                LOGGER.info("Converted {} from NBT to SNBT", filename);
                var7 = path;
            }

            return var7;
        } catch (IOException var12) {
            LOGGER.error("Couldn't convert {} from NBT to SNBT at {}", filename, inputPath, var12);
            return null;
        }
    }

    public static void writeSnbt(CachedOutput writer, Path path, String content) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        HashingOutputStream hashingOutputStream = new HashingOutputStream(Hashing.sha1(), byteArrayOutputStream);
        hashingOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        hashingOutputStream.write(10);
        writer.writeIfNeeded(path, byteArrayOutputStream.toByteArray(), hashingOutputStream.hash());
    }
}
