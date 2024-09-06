package net.minecraft.world.entity.animal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.Level;
// CraftBukkit start
import org.bukkit.event.entity.EntityRemoveEvent;
// CraftBukkit end

public abstract class ShoulderRidingEntity extends TamableAnimal {

    private static final int RIDE_COOLDOWN = 100;
    private int rideCooldownCounter;

    protected ShoulderRidingEntity(EntityType<? extends ShoulderRidingEntity> type, Level world) {
        super(type, world);
    }

    public boolean setEntityOnShoulder(ServerPlayer player) {
        CompoundTag nbttagcompound = new CompoundTag();

        nbttagcompound.putString("id", this.getEncodeId());
        this.saveWithoutId(nbttagcompound);
        if (player.setEntityOnShoulder(nbttagcompound)) {
            this.discard(EntityRemoveEvent.Cause.PICKUP); // CraftBukkit - add Bukkit remove cause
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void tick() {
        ++this.rideCooldownCounter;
        super.tick();
    }

    public boolean canSitOnShoulder() {
        return this.rideCooldownCounter > 100;
    }
}
