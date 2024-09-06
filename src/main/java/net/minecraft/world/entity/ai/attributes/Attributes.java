// mc-dev import
package net.minecraft.world.entity.ai.attributes;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public class Attributes {

    public static final Holder<Attribute> ARMOR = Attributes.register("generic.armor", (new RangedAttribute("attribute.name.generic.armor", 0.0D, 0.0D, 30.0D)).setSyncable(true));
    public static final Holder<Attribute> ARMOR_TOUGHNESS = Attributes.register("generic.armor_toughness", (new RangedAttribute("attribute.name.generic.armor_toughness", 0.0D, 0.0D, 20.0D)).setSyncable(true));
    public static final Holder<Attribute> ATTACK_DAMAGE = Attributes.register("generic.attack_damage", new RangedAttribute("attribute.name.generic.attack_damage", 2.0D, 0.0D, org.spigotmc.SpigotConfig.attackDamage));
    public static final Holder<Attribute> ATTACK_KNOCKBACK = Attributes.register("generic.attack_knockback", new RangedAttribute("attribute.name.generic.attack_knockback", 0.0D, 0.0D, 5.0D));
    public static final Holder<Attribute> ATTACK_SPEED = Attributes.register("generic.attack_speed", (new RangedAttribute("attribute.name.generic.attack_speed", 4.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> BLOCK_BREAK_SPEED = Attributes.register("player.block_break_speed", (new RangedAttribute("attribute.name.player.block_break_speed", 1.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> BLOCK_INTERACTION_RANGE = Attributes.register("player.block_interaction_range", (new RangedAttribute("attribute.name.player.block_interaction_range", 4.5D, 0.0D, 64.0D)).setSyncable(true));
    public static final Holder<Attribute> BURNING_TIME = Attributes.register("generic.burning_time", (new RangedAttribute("attribute.name.generic.burning_time", 1.0D, 0.0D, 1024.0D)).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));
    public static final Holder<Attribute> EXPLOSION_KNOCKBACK_RESISTANCE = Attributes.register("generic.explosion_knockback_resistance", (new RangedAttribute("attribute.name.generic.explosion_knockback_resistance", 0.0D, 0.0D, 1.0D)).setSyncable(true));
    public static final Holder<Attribute> ENTITY_INTERACTION_RANGE = Attributes.register("player.entity_interaction_range", (new RangedAttribute("attribute.name.player.entity_interaction_range", 3.0D, 0.0D, 64.0D)).setSyncable(true));
    public static final Holder<Attribute> FALL_DAMAGE_MULTIPLIER = Attributes.register("generic.fall_damage_multiplier", (new RangedAttribute("attribute.name.generic.fall_damage_multiplier", 1.0D, 0.0D, 100.0D)).setSyncable(true).setSentiment(Attribute.Sentiment.NEGATIVE));
    public static final Holder<Attribute> FLYING_SPEED = Attributes.register("generic.flying_speed", (new RangedAttribute("attribute.name.generic.flying_speed", 0.4D, 0.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> FOLLOW_RANGE = Attributes.register("generic.follow_range", new RangedAttribute("attribute.name.generic.follow_range", 32.0D, 0.0D, 2048.0D));
    public static final Holder<Attribute> GRAVITY = Attributes.register("generic.gravity", (new RangedAttribute("attribute.name.generic.gravity", 0.08D, -1.0D, 1.0D)).setSyncable(true).setSentiment(Attribute.Sentiment.NEUTRAL));
    public static final Holder<Attribute> JUMP_STRENGTH = Attributes.register("generic.jump_strength", (new RangedAttribute("attribute.name.generic.jump_strength", 0.41999998688697815D, 0.0D, 32.0D)).setSyncable(true));
    public static final Holder<Attribute> KNOCKBACK_RESISTANCE = Attributes.register("generic.knockback_resistance", new RangedAttribute("attribute.name.generic.knockback_resistance", 0.0D, 0.0D, 1.0D));
    public static final Holder<Attribute> LUCK = Attributes.register("generic.luck", (new RangedAttribute("attribute.name.generic.luck", 0.0D, -1024.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> MAX_ABSORPTION = Attributes.register("generic.max_absorption", (new RangedAttribute("attribute.name.generic.max_absorption", 0.0D, 0.0D, org.spigotmc.SpigotConfig.maxAbsorption)).setSyncable(true));
    public static final Holder<Attribute> MAX_HEALTH = Attributes.register("generic.max_health", (new RangedAttribute("attribute.name.generic.max_health", 20.0D, 1.0D, org.spigotmc.SpigotConfig.maxHealth)).setSyncable(true));
    public static final Holder<Attribute> MINING_EFFICIENCY = Attributes.register("player.mining_efficiency", (new RangedAttribute("attribute.name.player.mining_efficiency", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> MOVEMENT_EFFICIENCY = Attributes.register("generic.movement_efficiency", (new RangedAttribute("attribute.name.generic.movement_efficiency", 0.0D, 0.0D, 1.0D)).setSyncable(true));
    public static final Holder<Attribute> MOVEMENT_SPEED = Attributes.register("generic.movement_speed", (new RangedAttribute("attribute.name.generic.movement_speed", 0.7D, 0.0D, org.spigotmc.SpigotConfig.movementSpeed)).setSyncable(true));
    public static final Holder<Attribute> OXYGEN_BONUS = Attributes.register("generic.oxygen_bonus", (new RangedAttribute("attribute.name.generic.oxygen_bonus", 0.0D, 0.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> SAFE_FALL_DISTANCE = Attributes.register("generic.safe_fall_distance", (new RangedAttribute("attribute.name.generic.safe_fall_distance", 3.0D, -1024.0D, 1024.0D)).setSyncable(true));
    public static final Holder<Attribute> SCALE = Attributes.register("generic.scale", (new RangedAttribute("attribute.name.generic.scale", 1.0D, 0.0625D, 16.0D)).setSyncable(true).setSentiment(Attribute.Sentiment.NEUTRAL));
    public static final Holder<Attribute> SNEAKING_SPEED = Attributes.register("player.sneaking_speed", (new RangedAttribute("attribute.name.player.sneaking_speed", 0.3D, 0.0D, 1.0D)).setSyncable(true));
    public static final Holder<Attribute> SPAWN_REINFORCEMENTS_CHANCE = Attributes.register("zombie.spawn_reinforcements", new RangedAttribute("attribute.name.zombie.spawn_reinforcements", 0.0D, 0.0D, 1.0D));
    public static final Holder<Attribute> STEP_HEIGHT = Attributes.register("generic.step_height", (new RangedAttribute("attribute.name.generic.step_height", 0.6D, 0.0D, 10.0D)).setSyncable(true));
    public static final Holder<Attribute> SUBMERGED_MINING_SPEED = Attributes.register("player.submerged_mining_speed", (new RangedAttribute("attribute.name.player.submerged_mining_speed", 0.2D, 0.0D, 20.0D)).setSyncable(true));
    public static final Holder<Attribute> SWEEPING_DAMAGE_RATIO = Attributes.register("player.sweeping_damage_ratio", (new RangedAttribute("attribute.name.player.sweeping_damage_ratio", 0.0D, 0.0D, 1.0D)).setSyncable(true));
    public static final Holder<Attribute> WATER_MOVEMENT_EFFICIENCY = Attributes.register("generic.water_movement_efficiency", (new RangedAttribute("attribute.name.generic.water_movement_efficiency", 0.0D, 0.0D, 1.0D)).setSyncable(true));

    public Attributes() {}

    private static Holder<Attribute> register(String id, Attribute attribute) {
        return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, ResourceLocation.withDefaultNamespace(id), attribute);
    }

    public static Holder<Attribute> bootstrap(Registry<Attribute> registry) {
        return Attributes.MAX_HEALTH;
    }
}
