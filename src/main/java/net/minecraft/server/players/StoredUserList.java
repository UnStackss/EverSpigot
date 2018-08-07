package net.minecraft.server.players;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;

public abstract class StoredUserList<K, V extends StoredUserEntry<K>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
    private final File file;
    private final Map<String, V> map = Maps.newConcurrentMap(); // Paper - Use ConcurrentHashMap in JsonList

    public StoredUserList(File file) {
        this.file = file;
    }

    public File getFile() {
        return this.file;
    }

    public void add(V entry) {
        this.map.put(this.getKeyForUser(entry.getUser()), entry);

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after adding a user.", ioexception);
        }

    }

    @Nullable
    public V get(K key) {
        // Paper start - Use ConcurrentHashMap in JsonList
        return (V) this.map.computeIfPresent(this.getKeyForUser(key), (k, v) -> {
            return v.hasExpired() ? null : v;
        });
        // Paper end - Use ConcurrentHashMap in JsonList
    }

    public void remove(K key) {
        this.map.remove(this.getKeyForUser(key));

        try {
            this.save();
        } catch (IOException ioexception) {
            StoredUserList.LOGGER.warn("Could not save the list after removing a user.", ioexception);
        }

    }

    public void remove(StoredUserEntry<K> entry) {
        this.remove(entry.getUser());
    }

    public String[] getUserList() {
        return (String[]) this.map.keySet().toArray(new String[0]);
    }

    public boolean isEmpty() {
        return this.map.isEmpty(); // Paper - Use ConcurrentHashMap in JsonList
    }

    protected String getKeyForUser(K profile) {
        return profile.toString();
    }

    protected boolean contains(K k0) {
        this.removeExpired(); // CraftBukkit - SPIGOT-7589: Consistently remove expired entries to mirror .get(...)
        return this.map.containsKey(this.getKeyForUser(k0));
    }

    private void removeExpired() {
        this.map.values().removeIf(StoredUserEntry::hasExpired); // Paper - Use ConcurrentHashMap in JsonList
    }

    protected abstract StoredUserEntry<K> createEntry(JsonObject json);

    public Collection<V> getEntries() {
        return this.map.values();
    }

    public void save() throws IOException {
        this.removeExpired(); // Paper - remove expired values before saving
        JsonArray jsonarray = new JsonArray();
        Stream<JsonObject> stream = this.map.values().stream().map((jsonlistentry) -> { // CraftBukkit - decompile error
            JsonObject jsonobject = new JsonObject();

            Objects.requireNonNull(jsonlistentry);
            return (JsonObject) Util.make(jsonobject, jsonlistentry::serialize);
        });

        Objects.requireNonNull(jsonarray);
        stream.forEach(jsonarray::add);
        BufferedWriter bufferedwriter = Files.newWriter(this.file, StandardCharsets.UTF_8);

        try {
            StoredUserList.GSON.toJson(jsonarray, StoredUserList.GSON.newJsonWriter(bufferedwriter));
        } catch (Throwable throwable) {
            if (bufferedwriter != null) {
                try {
                    bufferedwriter.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (bufferedwriter != null) {
            bufferedwriter.close();
        }

    }

    public void load() throws IOException {
        if (this.file.exists()) {
            BufferedReader bufferedreader = Files.newReader(this.file, StandardCharsets.UTF_8);

            label54:
            {
                try {
                    this.map.clear();
                    JsonArray jsonarray = (JsonArray) StoredUserList.GSON.fromJson(bufferedreader, JsonArray.class);

                    if (jsonarray == null) {
                        break label54;
                    }

                    Iterator iterator = jsonarray.iterator();

                    while (iterator.hasNext()) {
                        JsonElement jsonelement = (JsonElement) iterator.next();
                        JsonObject jsonobject = GsonHelper.convertToJsonObject(jsonelement, "entry");
                        StoredUserEntry<K> jsonlistentry = this.createEntry(jsonobject);

                        if (jsonlistentry.getUser() != null) {
                            this.map.put(this.getKeyForUser(jsonlistentry.getUser()), (V) jsonlistentry); // CraftBukkit - decompile error
                        }
                    }
                // Spigot Start
                } catch ( com.google.gson.JsonParseException | NullPointerException ex )
                {
                    org.bukkit.Bukkit.getLogger().log( java.util.logging.Level.WARNING, "Unable to read file " + this.file + ", backing it up to {0}.backup and creating new copy.", ex );
                    File backup = new File( this.file + ".backup" );
                    this.file.renameTo( backup );
                    this.file.delete();
                // Spigot End
                } catch (Throwable throwable) {
                    if (bufferedreader != null) {
                        try {
                            bufferedreader.close();
                        } catch (Throwable throwable1) {
                            throwable.addSuppressed(throwable1);
                        }
                    }

                    throw throwable;
                }

                if (bufferedreader != null) {
                    bufferedreader.close();
                }

                return;
            }

            if (bufferedreader != null) {
                bufferedreader.close();
            }

        }
    }
}
