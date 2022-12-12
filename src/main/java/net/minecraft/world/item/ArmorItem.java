package net.minecraft.world.item;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.block.BlockDispenseArmorEvent;
// CraftBukkit end

public class ArmorItem extends Item implements Equipable {

    public static final DispenseItemBehavior DISPENSE_ITEM_BEHAVIOR = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource pointer, ItemStack stack) {
            return ArmorItem.dispenseArmor(pointer, stack) ? stack : super.execute(pointer, stack);
        }
    };
    protected final ArmorItem.Type type;
    protected final Holder<ArmorMaterial> material;
    private final Supplier<ItemAttributeModifiers> defaultModifiers;

    public static boolean dispenseArmor(BlockSource pointer, ItemStack armor) {
        BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
        List<LivingEntity> list = pointer.level().getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), EntitySelector.NO_SPECTATORS.and(new EntitySelector.MobCanWearArmorEntitySelector(armor)));

        if (list.isEmpty()) {
            return false;
        } else {
            LivingEntity entityliving = (LivingEntity) list.get(0);
            EquipmentSlot enumitemslot = entityliving.getEquipmentSlotForItem(armor);
            ItemStack itemstack1 = armor.copyWithCount(1); // Paper - shrink below and single item in event
            // CraftBukkit start
            Level world = pointer.level();
            org.bukkit.block.Block block = CraftBlock.at(world, pointer.pos());
            CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

            BlockDispenseArmorEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) entityliving.getBukkitEntity());
            if (!DispenserBlock.eventFired) {
                world.getCraftServer().getPluginManager().callEvent(event);
            }

            if (event.isCancelled()) {
                // armor.grow(1); // Paper - shrink below
                return false;
            }

            boolean shrink = true; // Paper
            if (!event.getItem().equals(craftItem)) {
                shrink = false; // Paper - shrink below
                // Chain to handler for new item
                ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                    idispensebehavior.dispense(pointer, eventStack);
                    return true;
                }
            }

            entityliving.setItemSlot(enumitemslot, CraftItemStack.asNMSCopy(event.getItem()));
            // CraftBukkit end
            if (entityliving instanceof Mob) {
                ((Mob) entityliving).setDropChance(enumitemslot, 2.0F);
                ((Mob) entityliving).setPersistenceRequired();
            }

            if (shrink) armor.shrink(1); // Paper
            return true;
        }
    }

    public ArmorItem(Holder<ArmorMaterial> material, ArmorItem.Type type, Item.Properties settings) {
        super(settings);
        this.material = material;
        this.type = type;
        DispenserBlock.registerBehavior(this, ArmorItem.DISPENSE_ITEM_BEHAVIOR);
        this.defaultModifiers = Suppliers.memoize(() -> {
            int i = ((ArmorMaterial) material.value()).getDefense(type);
            float f = ((ArmorMaterial) material.value()).toughness();
            ItemAttributeModifiers.Builder itemattributemodifiers_a = ItemAttributeModifiers.builder();
            EquipmentSlotGroup equipmentslotgroup = EquipmentSlotGroup.bySlot(type.getSlot());
            ResourceLocation minecraftkey = ResourceLocation.withDefaultNamespace("armor." + type.getName());

            itemattributemodifiers_a.add(Attributes.ARMOR, new AttributeModifier(minecraftkey, (double) i, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);
            itemattributemodifiers_a.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(minecraftkey, (double) f, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);
            float f1 = ((ArmorMaterial) material.value()).knockbackResistance();

            if (f1 > 0.0F) {
                itemattributemodifiers_a.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(minecraftkey, (double) f1, AttributeModifier.Operation.ADD_VALUE), equipmentslotgroup);
            }

            return itemattributemodifiers_a.build();
        });
    }

    public ArmorItem.Type getType() {
        return this.type;
    }

    @Override
    public int getEnchantmentValue() {
        return ((ArmorMaterial) this.material.value()).enchantmentValue();
    }

    public Holder<ArmorMaterial> getMaterial() {
        return this.material;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack ingredient) {
        return ((Ingredient) ((ArmorMaterial) this.material.value()).repairIngredient().get()).test(ingredient) || super.isValidRepairItem(stack, ingredient);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        return this.swapWithEquipmentSlot(this, world, user, hand);
    }

    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return (ItemAttributeModifiers) this.defaultModifiers.get();
    }

    public int getDefense() {
        return ((ArmorMaterial) this.material.value()).getDefense(this.type);
    }

    public float getToughness() {
        return ((ArmorMaterial) this.material.value()).toughness();
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return this.type.getSlot();
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return ((ArmorMaterial) this.getMaterial().value()).equipSound();
    }

    public static enum Type implements StringRepresentable {

        HELMET(EquipmentSlot.HEAD, 11, "helmet"), CHESTPLATE(EquipmentSlot.CHEST, 16, "chestplate"), LEGGINGS(EquipmentSlot.LEGS, 15, "leggings"), BOOTS(EquipmentSlot.FEET, 13, "boots"), BODY(EquipmentSlot.BODY, 16, "body");

        public static final Codec<ArmorItem.Type> CODEC = StringRepresentable.fromValues(ArmorItem.Type::values);
        private final EquipmentSlot slot;
        private final String name;
        private final int durability;

        private Type(final EquipmentSlot enumitemslot, final int i, final String s) {
            this.slot = enumitemslot;
            this.name = s;
            this.durability = i;
        }

        public int getDurability(int multiplier) {
            return this.durability * multiplier;
        }

        public EquipmentSlot getSlot() {
            return this.slot;
        }

        public String getName() {
            return this.name;
        }

        public boolean hasTrims() {
            return this == ArmorItem.Type.HELMET || this == ArmorItem.Type.CHESTPLATE || this == ArmorItem.Type.LEGGINGS || this == ArmorItem.Type.BOOTS;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
