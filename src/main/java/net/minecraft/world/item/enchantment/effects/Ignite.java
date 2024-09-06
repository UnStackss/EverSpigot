package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
// CraftBukkit end

public record Ignite(LevelBasedValue duration) implements EnchantmentEntityEffect {

    public static final MapCodec<Ignite> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(LevelBasedValue.CODEC.fieldOf("duration").forGetter((ignite) -> {
            return ignite.duration;
        })).apply(instance, Ignite::new);
    });

    @Override
    public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
        // CraftBukkit start - Call a combust event when somebody hits with a fire enchanted item
        EntityCombustEvent entityCombustEvent;
        if (context.owner() != null) {
            entityCombustEvent = new EntityCombustByEntityEvent(context.owner().getBukkitEntity(), user.getBukkitEntity(), this.duration.calculate(level));
        } else {
            entityCombustEvent = new EntityCombustEvent(user.getBukkitEntity(), this.duration.calculate(level));
        }

        org.bukkit.Bukkit.getPluginManager().callEvent(entityCombustEvent);
        if (entityCombustEvent.isCancelled()) {
            return;
        }

        user.igniteForSeconds(entityCombustEvent.getDuration(), false);
        // CraftBukkit end
    }

    @Override
    public MapCodec<Ignite> codec() {
        return Ignite.CODEC;
    }
}
