package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import net.minecraft.world.item.Items;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
// CraftBukkit end

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {

    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec((instance) -> {
        return instance.group(RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes), Codec.BOOL.optionalFieldOf("join_team", false).forGetter(SummonEntityEffect::joinTeam)).apply(instance, SummonEntityEffect::new);
    });

    @Override
    public void apply(ServerLevel world, int level, EnchantedItemInUse context, Entity user, Vec3 pos) {
        BlockPos blockposition = BlockPos.containing(pos);

        if (Level.isInSpawnableBounds(blockposition)) {
            Optional<Holder<EntityType<?>>> optional = this.entityTypes().getRandomElement(world.getRandom());

            if (!optional.isEmpty()) {
                Entity entity1 = ((EntityType) ((Holder) optional.get()).value()).create(world, null, blockposition, MobSpawnType.TRIGGERED, false, false); // CraftBukkit

                if (entity1 != null) {
                    if (entity1 instanceof LightningBolt) {
                        LightningBolt entitylightning = (LightningBolt) entity1;
                        LivingEntity entityliving = context.owner();

                        if (entityliving instanceof ServerPlayer) {
                            ServerPlayer entityplayer = (ServerPlayer) entityliving;

                            entitylightning.setCause(entityplayer);
                        }
                        // CraftBukkit start
                        world.strikeLightning(entity1, (context.itemStack().getItem() == Items.TRIDENT) ? LightningStrikeEvent.Cause.TRIDENT : LightningStrikeEvent.Cause.ENCHANTMENT);
                    } else {
                        world.addFreshEntityWithPassengers(user, CreatureSpawnEvent.SpawnReason.ENCHANTMENT);
                        // CraftBukkit end
                    }

                    if (this.joinTeam && user.getTeam() != null) {
                        world.getScoreboard().addPlayerToTeam(entity1.getScoreboardName(), user.getTeam());
                    }

                    entity1.moveTo(pos.x, pos.y, pos.z, entity1.getYRot(), entity1.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return SummonEntityEffect.CODEC;
    }
}
