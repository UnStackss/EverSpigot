package net.minecraft.world.level.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.slf4j.Logger;

public class PlayerDataStorage {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final File playerDir;
    protected final DataFixer fixerUpper;
    private static final DateTimeFormatter FORMATTER = FileNameDateFormatter.create();

    public PlayerDataStorage(LevelStorageSource.LevelStorageAccess session, DataFixer dataFixer) {
        this.fixerUpper = dataFixer;
        this.playerDir = session.getLevelPath(LevelResource.PLAYER_DATA_DIR).toFile();
        this.playerDir.mkdirs();
    }

    public void save(Player player) {
        if (org.spigotmc.SpigotConfig.disablePlayerDataSaving) return; // Spigot
        try {
            CompoundTag nbttagcompound = player.saveWithoutId(new CompoundTag());
            Path path = this.playerDir.toPath();
            Path path1 = Files.createTempFile(path, player.getStringUUID() + "-", ".dat");

            NbtIo.writeCompressed(nbttagcompound, path1);
            Path path2 = path.resolve(player.getStringUUID() + ".dat");
            Path path3 = path.resolve(player.getStringUUID() + ".dat_old");

            Util.safeReplaceFile(path2, path1, path3);
        } catch (Exception exception) {
            PlayerDataStorage.LOGGER.warn("Failed to save player data for {}", player.getScoreboardName(), exception); // Paper - Print exception
        }

    }

    private void backup(String name, String s1, String s) { // name, uuid, extension
        Path path = this.playerDir.toPath();
        // String s1 = entityhuman.getStringUUID(); // CraftBukkit - used above
        Path path1 = path.resolve(s1 + s);

        // s1 = entityhuman.getStringUUID(); // CraftBukkit - used above
        Path path2 = path.resolve(s1 + "_corrupted_" + LocalDateTime.now().format(PlayerDataStorage.FORMATTER) + s);

        if (Files.isRegularFile(path1, new LinkOption[0])) {
            try {
                Files.copy(path1, path2, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (Exception exception) {
                PlayerDataStorage.LOGGER.warn("Failed to copy the player.dat file for {}", name, exception); // CraftBukkit
            }

        }
    }

    // CraftBukkit start
    private Optional<CompoundTag> load(String name, String s1, String s) { // name, uuid, extension
        // CraftBukkit end
        File file = this.playerDir;
        // String s1 = entityhuman.getStringUUID(); // CraftBukkit - used above
        File file1 = new File(file, s1 + s);
        // Spigot Start
        boolean usingWrongFile = false;
        if ( org.bukkit.Bukkit.getOnlineMode() && !file1.exists() ) // Paper - Check online mode first
        {
            file1 = new File( file, java.util.UUID.nameUUIDFromBytes( ( "OfflinePlayer:" + name ).getBytes( java.nio.charset.StandardCharsets.UTF_8 ) ).toString() + s );
            if ( file1.exists() )
            {
                usingWrongFile = true;
                org.bukkit.Bukkit.getServer().getLogger().warning( "Using offline mode UUID file for player " + name + " as it is the only copy we can find." );
            }
        }
        // Spigot End

        if (file1.exists() && file1.isFile()) {
            try {
                // Spigot Start
                Optional<CompoundTag> optional = Optional.of(NbtIo.readCompressed(file1.toPath(), NbtAccounter.unlimitedHeap()));
                if ( usingWrongFile )
                {
                    file1.renameTo( new File( file1.getPath() + ".offline-read" ) );
                }
                return optional;
                // Spigot End
            } catch (Exception exception) {
                PlayerDataStorage.LOGGER.warn("Failed to load player data for {}", name); // CraftBukkit
            }
        }

        return Optional.empty();
    }

    public Optional<CompoundTag> load(Player player) {
        // CraftBukkit start
        return this.load(player.getName().getString(), player.getStringUUID()).map((nbttagcompound) -> {
            if (player instanceof ServerPlayer) {
                CraftPlayer player1 = (CraftPlayer) player.getBukkitEntity();
                // Only update first played if it is older than the one we have
                long modified = new File(this.playerDir, player.getStringUUID() + ".dat").lastModified();
                if (modified < player1.getFirstPlayed()) {
                    player1.setFirstPlayed(modified);
                }
            }

            player.load(nbttagcompound); // From below
            return nbttagcompound;
        });
    }

    public Optional<CompoundTag> load(String name, String uuid) {
        // CraftBukkit end
        Optional<CompoundTag> optional = this.load(name, uuid, ".dat"); // CraftBukkit

        if (optional.isEmpty()) {
            this.backup(name, uuid, ".dat"); // CraftBukkit
        }

        return optional.or(() -> {
            return this.load(name, uuid, ".dat_old"); // CraftBukkit
        }).map((nbttagcompound) -> {
            int i = NbtUtils.getDataVersion(nbttagcompound, -1);

            nbttagcompound = ca.spottedleaf.dataconverter.minecraft.MCDataConverter.convertTag(ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry.PLAYER, nbttagcompound, i, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()); // Paper - rewrite data conversion system
            // entityhuman.load(nbttagcompound); // CraftBukkit - handled above
            return nbttagcompound;
        });
    }

    // CraftBukkit start
    public File getPlayerDir() {
        return this.playerDir;
    }
    // CraftBukkit end
}
