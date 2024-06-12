package io.papermc.paper.persistence;

import com.google.common.base.Preconditions;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataAdapterContext;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataTypeRegistry;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public abstract class PaperPersistentDataContainerView implements PersistentDataContainerView {

    protected final CraftPersistentDataTypeRegistry registry;
    protected final CraftPersistentDataAdapterContext adapterContext;

    public PaperPersistentDataContainerView(final CraftPersistentDataTypeRegistry registry) {
        this.registry = registry;
        this.adapterContext = new CraftPersistentDataAdapterContext(this.registry);
    }

    public abstract @Nullable Tag getTag(final String key);

    public abstract CompoundTag toTagCompound();

    @Override
    public <P, C> boolean has(final NamespacedKey key, final PersistentDataType<P, C> type) {
        Preconditions.checkArgument(key != null, "The NamespacedKey key cannot be null");
        Preconditions.checkArgument(type != null, "The provided type cannot be null");

        final @Nullable Tag value = this.getTag(key.toString());
        if (value == null) {
            return false;
        }

        return this.registry.isInstanceOf(type, value);
    }

    @Override
    public boolean has(final NamespacedKey key) {
        Preconditions.checkArgument(key != null, "The provided key for the custom value was null"); // Paper
        return this.getTag(key.toString()) != null;
    }

    @Override
    public <P, C> @Nullable C get(final NamespacedKey key, final PersistentDataType<P, C> type) {
        Preconditions.checkArgument(key != null, "The NamespacedKey key cannot be null");
        Preconditions.checkArgument(type != null, "The provided type cannot be null");

        final @Nullable Tag value = this.getTag(key.toString());
        if (value == null) {
            return null;
        }

        return type.fromPrimitive(this.registry.extract(type, value), this.adapterContext);
    }

    @Override
    public <P, C> C getOrDefault(final NamespacedKey key, final PersistentDataType<P, C> type, final C defaultValue) {
        final C c = this.get(key, type);
        return c != null ? c : defaultValue;
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() {
        return this.adapterContext;
    }

    @Override
    public byte[] serializeToBytes() throws IOException {
        final net.minecraft.nbt.CompoundTag root = this.toTagCompound();
        final ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
        try (final DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput)) {
            NbtIo.write(root, dataOutput);
            return byteArrayOutput.toByteArray();
        }
    }
}
