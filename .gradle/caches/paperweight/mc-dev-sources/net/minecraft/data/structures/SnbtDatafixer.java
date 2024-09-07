package net.minecraft.data.structures;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.Bootstrap;

public class SnbtDatafixer {
    public static void main(String[] args) throws IOException {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        for (String string : args) {
            updateInDirectory(string);
        }
    }

    private static void updateInDirectory(String directory) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(directory))) {
            stream.filter(path -> path.toString().endsWith(".snbt")).forEach(path -> {
                try {
                    String string = Files.readString(path);
                    CompoundTag compoundTag = NbtUtils.snbtToStructure(string);
                    CompoundTag compoundTag2 = StructureUpdater.update(path.toString(), compoundTag);
                    NbtToSnbt.writeSnbt(CachedOutput.NO_CACHE, path, NbtUtils.structureToSnbt(compoundTag2));
                } catch (IOException | CommandSyntaxException var4) {
                    throw new RuntimeException(var4);
                }
            });
        }
    }
}
