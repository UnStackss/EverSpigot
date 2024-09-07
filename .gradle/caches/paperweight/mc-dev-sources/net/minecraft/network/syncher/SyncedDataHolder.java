package net.minecraft.network.syncher;

import java.util.List;

public interface SyncedDataHolder {
    void onSyncedDataUpdated(EntityDataAccessor<?> data);

    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> entries);
}
