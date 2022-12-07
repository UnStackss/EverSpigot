package net.minecraft.network.syncher;

import com.mojang.logging.LogUtils;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ClassTreeIdRegistry;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;

public class SynchedEntityData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_ID_VALUE = 254;
    static final ClassTreeIdRegistry ID_REGISTRY = new ClassTreeIdRegistry();
    private final SyncedDataHolder entity;
    private final SynchedEntityData.DataItem<?>[] itemsById;
    private boolean isDirty;

    SynchedEntityData(SyncedDataHolder trackedEntity, SynchedEntityData.DataItem<?>[] entries) {
        this.entity = trackedEntity;
        this.itemsById = entries;
    }

    public static <T> EntityDataAccessor<T> defineId(Class<? extends SyncedDataHolder> entityClass, EntityDataSerializer<T> dataHandler) {
        if (SynchedEntityData.LOGGER.isDebugEnabled()) {
            try {
                Class<?> oclass1 = Class.forName(Thread.currentThread().getStackTrace()[2].getClassName());

                if (!oclass1.equals(entityClass)) {
                    SynchedEntityData.LOGGER.debug("defineId called for: {} from {}", new Object[]{entityClass, oclass1, new RuntimeException()});
                }
            } catch (ClassNotFoundException classnotfoundexception) {
                ;
            }
        }

        int i = SynchedEntityData.ID_REGISTRY.define(entityClass);

        if (i > 254) {
            throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is 254)");
        } else {
            return dataHandler.createAccessor(i);
        }
    }

    public <T> SynchedEntityData.DataItem<T> getItem(EntityDataAccessor<T> key) { // Paper - public
        return (SynchedEntityData.DataItem<T>) this.itemsById[key.id()]; // CraftBukkit - decompile error
    }

    public <T> T get(EntityDataAccessor<T> data) {
        return this.getItem(data).getValue();
    }

    public <T> void set(EntityDataAccessor<T> key, T value) {
        this.set(key, value, false);
    }

    public <T> void set(EntityDataAccessor<T> key, T value, boolean force) {
        SynchedEntityData.DataItem<T> datawatcher_item = this.getItem(key);

        if (force || ObjectUtils.notEqual(value, datawatcher_item.getValue())) {
            datawatcher_item.setValue(value);
            this.entity.onSyncedDataUpdated(key);
            datawatcher_item.setDirty(true);
            this.isDirty = true;
        }

    }

    // CraftBukkit start - add method from above
    public <T> void markDirty(EntityDataAccessor<T> datawatcherobject) {
        this.getItem(datawatcherobject).setDirty(true);
        this.isDirty = true;
    }
    // CraftBukkit end

    public boolean isDirty() {
        return this.isDirty;
    }

    @Nullable
    public List<SynchedEntityData.DataValue<?>> packDirty() {
        if (!this.isDirty) {
            return null;
        } else {
            this.isDirty = false;
            List<SynchedEntityData.DataValue<?>> list = new ArrayList();
            SynchedEntityData.DataItem[] adatawatcher_item = this.itemsById;
            int i = adatawatcher_item.length;

            for (int j = 0; j < i; ++j) {
                SynchedEntityData.DataItem<?> datawatcher_item = adatawatcher_item[j];

                if (datawatcher_item.isDirty()) {
                    datawatcher_item.setDirty(false);
                    list.add(datawatcher_item.value());
                }
            }

            return list;
        }
    }

    @Nullable
    public List<SynchedEntityData.DataValue<?>> getNonDefaultValues() {
        List<SynchedEntityData.DataValue<?>> list = null;
        SynchedEntityData.DataItem[] adatawatcher_item = this.itemsById;
        int i = adatawatcher_item.length;

        for (int j = 0; j < i; ++j) {
            SynchedEntityData.DataItem<?> datawatcher_item = adatawatcher_item[j];

            if (!datawatcher_item.isSetToDefault()) {
                if (list == null) {
                    list = new ArrayList();
                }

                list.add(datawatcher_item.value());
            }
        }

        return list;
    }

    public void assignValues(List<SynchedEntityData.DataValue<?>> entries) {
        Iterator iterator = entries.iterator();

        while (iterator.hasNext()) {
            SynchedEntityData.DataValue<?> datawatcher_c = (SynchedEntityData.DataValue) iterator.next();
            SynchedEntityData.DataItem<?> datawatcher_item = this.itemsById[datawatcher_c.id];

            this.assignValue(datawatcher_item, datawatcher_c);
            this.entity.onSyncedDataUpdated(datawatcher_item.getAccessor());
        }

        this.entity.onSyncedDataUpdated(entries);
    }

    private <T> void assignValue(SynchedEntityData.DataItem<T> to, SynchedEntityData.DataValue<?> from) {
        if (!Objects.equals(from.serializer(), to.accessor.serializer())) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Invalid entity data item type for field %d on entity %s: old=%s(%s), new=%s(%s)", to.accessor.id(), this.entity, to.value, to.value.getClass(), from.value, from.value.getClass()));
        } else {
            to.setValue((T) from.value); // CraftBukkit - decompile error
        }
    }

    // Paper start
    // We need to pack all as we cannot rely on "non default values" or "dirty" ones.
    // Because these values can possibly be desynced on the client.
    @Nullable
    public List<SynchedEntityData.DataValue<?>> packAll() {
        final List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();
        for (final DataItem<?> dataItem : this.itemsById) {
            list.add(dataItem.value());
        }

        return list;
    }
    // Paper end

    public static class DataItem<T> {

        final EntityDataAccessor<T> accessor;
        T value;
        private final T initialValue;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> data, T value) {
            this.accessor = data;
            this.initialValue = value;
            this.value = value;
        }

        public EntityDataAccessor<T> getAccessor() {
            return this.accessor;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public T getValue() {
            return this.value;
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return this.initialValue.equals(this.value);
        }

        public SynchedEntityData.DataValue<T> value() {
            return SynchedEntityData.DataValue.create(this.accessor, this.value);
        }
    }

    public static record DataValue<T>(int id, EntityDataSerializer<T> serializer, T value) {

        public static <T> SynchedEntityData.DataValue<T> create(EntityDataAccessor<T> data, T value) {
            EntityDataSerializer<T> datawatcherserializer = data.serializer();

            return new SynchedEntityData.DataValue<>(data.id(), datawatcherserializer, datawatcherserializer.copy(value));
        }

        public void write(RegistryFriendlyByteBuf buf) {
            int i = EntityDataSerializers.getSerializedId(this.serializer);

            if (i < 0) {
                throw new EncoderException("Unknown serializer type " + String.valueOf(this.serializer));
            } else {
                buf.writeByte(this.id);
                buf.writeVarInt(i);
                this.serializer.codec().encode(buf, this.value);
            }
        }

        public static SynchedEntityData.DataValue<?> read(RegistryFriendlyByteBuf buf, int id) {
            int j = buf.readVarInt();
            EntityDataSerializer<?> datawatcherserializer = EntityDataSerializers.getSerializer(j);

            if (datawatcherserializer == null) {
                throw new DecoderException("Unknown serializer type " + j);
            } else {
                return read(buf, id, datawatcherserializer);
            }
        }

        private static <T> SynchedEntityData.DataValue<T> read(RegistryFriendlyByteBuf buf, int id, EntityDataSerializer<T> handler) {
            return new SynchedEntityData.DataValue<>(id, handler, handler.codec().decode(buf));
        }
    }

    public static class Builder {

        private final SyncedDataHolder entity;
        private final SynchedEntityData.DataItem<?>[] itemsById;

        public Builder(SyncedDataHolder entity) {
            this.entity = entity;
            this.itemsById = new SynchedEntityData.DataItem[SynchedEntityData.ID_REGISTRY.getCount(entity.getClass())];
        }

        public <T> SynchedEntityData.Builder define(EntityDataAccessor<T> data, T value) {
            int i = data.id();

            if (i > this.itemsById.length) {
                throw new IllegalArgumentException("Data value id is too big with " + i + "! (Max is " + this.itemsById.length + ")");
            } else if (this.itemsById[i] != null) {
                throw new IllegalArgumentException("Duplicate id value for " + i + "!");
            } else if (EntityDataSerializers.getSerializedId(data.serializer()) < 0) {
                String s = String.valueOf(data.serializer());

                throw new IllegalArgumentException("Unregistered serializer " + s + " for " + i + "!");
            } else {
                this.itemsById[data.id()] = new SynchedEntityData.DataItem<>(data, value);
                return this;
            }
        }

        public SynchedEntityData build() {
            for (int i = 0; i < this.itemsById.length; ++i) {
                if (this.itemsById[i] == null) {
                    String s = String.valueOf(this.entity.getClass());

                    throw new IllegalStateException("Entity " + s + " has not defined synched data value " + i);
                }
            }

            return new SynchedEntityData(this.entity, this.itemsById);
        }
    }
}
