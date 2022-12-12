package net.minecraft.core.dispenser;

import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.craftbukkit.util.DummyGeneratorAccess;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public interface DispenseItemBehavior {

    Logger LOGGER = LogUtils.getLogger();
    DispenseItemBehavior NOOP = (sourceblock, itemstack) -> {
        return itemstack;
    };

    ItemStack dispense(BlockSource pointer, ItemStack stack);

    static void bootStrap() {
        DispenserBlock.registerProjectileBehavior(Items.ARROW);
        DispenserBlock.registerProjectileBehavior(Items.TIPPED_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.SPECTRAL_ARROW);
        DispenserBlock.registerProjectileBehavior(Items.EGG);
        DispenserBlock.registerProjectileBehavior(Items.SNOWBALL);
        DispenserBlock.registerProjectileBehavior(Items.EXPERIENCE_BOTTLE);
        DispenserBlock.registerProjectileBehavior(Items.SPLASH_POTION);
        DispenserBlock.registerProjectileBehavior(Items.LINGERING_POTION);
        DispenserBlock.registerProjectileBehavior(Items.FIREWORK_ROCKET);
        DispenserBlock.registerProjectileBehavior(Items.FIRE_CHARGE);
        DispenserBlock.registerProjectileBehavior(Items.WIND_CHARGE);
        DefaultDispenseItemBehavior dispensebehavioritem = new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
                EntityType<?> entitytypes = ((SpawnEggItem) stack.getItem()).getType(stack);

                // CraftBukkit start
                ServerLevel worldserver = pointer.level();
                ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
                org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    // stack.grow(1); // Paper - shrink below
                    return stack;
                }

                boolean shrink = true; // Paper
                if (!event.getItem().equals(craftItem)) {
                    shrink = false; // Paper - shrink below
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }

                try {
                    entitytypes.spawn(pointer.level(), stack, (Player) null, pointer.pos().relative(enumdirection), MobSpawnType.DISPENSER, enumdirection != Direction.UP, false);
                } catch (Exception exception) {
                    DispenseItemBehavior.LOGGER.error("Error while dispensing spawn egg from dispenser at {}", pointer.pos(), exception); // CraftBukkit - decompile error
                    return ItemStack.EMPTY;
                }

                if (shrink) stack.shrink(1); // Paper - actually handle here
                // CraftBukkit end
                pointer.level().gameEvent((Entity) null, (Holder) GameEvent.ENTITY_PLACE, pointer.pos());
                return stack;
            }
        };
        Iterator iterator = SpawnEggItem.eggs().iterator();

        while (iterator.hasNext()) {
            SpawnEggItem itemmonsteregg = (SpawnEggItem) iterator.next();

            DispenserBlock.registerBehavior(itemmonsteregg, dispensebehavioritem);
        }

        DispenserBlock.registerBehavior(Items.ARMOR_STAND, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
                BlockPos blockposition = pointer.pos().relative(enumdirection);
                ServerLevel worldserver = pointer.level();

                // CraftBukkit start
                ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
                org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    // stack.grow(1); // Paper - shrink below
                    return stack;
                }

                boolean shrink = true; // Paper
                if (!event.getItem().equals(craftItem)) {
                    shrink = false; // Paper - shrink below
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                // CraftBukkit end

                Consumer<ArmorStand> consumer = EntityType.appendDefaultStackConfig((entityarmorstand) -> {
                    entityarmorstand.setYRot(enumdirection.toYRot());
                }, worldserver, stack, (Player) null);
                ArmorStand entityarmorstand = (ArmorStand) EntityType.ARMOR_STAND.spawn(worldserver, consumer, blockposition, MobSpawnType.DISPENSER, false, false);

                if (entityarmorstand != null) {
                    if (shrink) stack.shrink(1); // Paper - actually handle here
                }

                return stack;
            }
        });
        DispenserBlock.registerBehavior(Items.SADDLE, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                List<LivingEntity> list = pointer.level().getEntitiesOfClass(LivingEntity.class, new AABB(blockposition), (entityliving) -> {
                    if (!(entityliving instanceof Saddleable isaddleable)) {
                        return false;
                    } else {
                        return !isaddleable.isSaddled() && isaddleable.isSaddleable();
                    }
                });

                if (!list.isEmpty()) {
                    // CraftBukkit start
                    ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
                    ServerLevel world = pointer.level();
                    org.bukkit.block.Block block = CraftBlock.at(world, pointer.pos());
                    CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                    BlockDispenseArmorEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) list.get(0).getBukkitEntity());
                    if (!DispenserBlock.eventFired) {
                        world.getCraftServer().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        // stack.grow(1); // Paper - shrink below
                        return stack;
                    }

                    boolean shrink = true; // Paper
                    if (!event.getItem().equals(craftItem)) {
                        shrink = false; // Paper - shrink below
                        // Chain to handler for new item
                        ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                        if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                            idispensebehavior.dispense(pointer, eventStack);
                            return stack;
                        }
                    }
                    ((Saddleable) list.get(0)).equipSaddle(itemstack1, SoundSource.BLOCKS);
                    // CraftBukkit end
                    if (shrink) stack.shrink(1); // Paper - actually handle here
                    this.setSuccess(true);
                    return stack;
                } else {
                    return super.execute(pointer, stack);
                }
            }
        });
        OptionalDispenseItemBehavior dispensebehaviormaybe = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                List<AbstractHorse> list = pointer.level().getEntitiesOfClass(AbstractHorse.class, new AABB(blockposition), (entityhorseabstract) -> {
                    return entityhorseabstract.isAlive() && entityhorseabstract.canUseSlot(EquipmentSlot.BODY);
                });
                Iterator iterator1 = list.iterator();

                AbstractHorse entityhorseabstract;

                do {
                    if (!iterator1.hasNext()) {
                        return super.execute(pointer, stack);
                    }

                    entityhorseabstract = (AbstractHorse) iterator1.next();
                } while (!entityhorseabstract.isBodyArmorItem(stack) || entityhorseabstract.isWearingBodyArmor() || !entityhorseabstract.isTamed());

                // CraftBukkit start
                ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below and single item in event
                ServerLevel world = pointer.level();
                org.bukkit.block.Block block = CraftBlock.at(world, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseArmorEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) entityhorseabstract.getBukkitEntity());
                if (!DispenserBlock.eventFired) {
                    world.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    // stack.grow(1); // Paper - shrink below
                    return stack;
                }

                boolean shrink = true; // Paper
                if (!event.getItem().equals(craftItem)) {
                    shrink = false; // Paper - shrink below
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }

                if (shrink) stack.shrink(1); // Paper - shrink here
                entityhorseabstract.setBodyArmorItem(CraftItemStack.asNMSCopy(event.getItem()));
                // CraftBukkit end
                this.setSuccess(true);
                return stack;
            }
        };

        DispenserBlock.registerBehavior(Items.LEATHER_HORSE_ARMOR, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.IRON_HORSE_ARMOR, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.GOLDEN_HORSE_ARMOR, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.DIAMOND_HORSE_ARMOR, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.WHITE_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.ORANGE_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.CYAN_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.BLUE_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.BROWN_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.BLACK_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.GRAY_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.GREEN_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.LIGHT_BLUE_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.LIGHT_GRAY_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.LIME_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.MAGENTA_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.PINK_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.PURPLE_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.RED_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.YELLOW_CARPET, dispensebehaviormaybe);
        DispenserBlock.registerBehavior(Items.CHEST, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                List<AbstractChestedHorse> list = pointer.level().getEntitiesOfClass(AbstractChestedHorse.class, new AABB(blockposition), (entityhorsechestedabstract) -> {
                    return entityhorsechestedabstract.isAlive() && !entityhorsechestedabstract.hasChest();
                });
                Iterator iterator1 = list.iterator();

                AbstractChestedHorse entityhorsechestedabstract;

                do {
                    if (!iterator1.hasNext()) {
                        return super.execute(pointer, stack);
                    }

                    entityhorsechestedabstract = (AbstractChestedHorse) iterator1.next();
                    // CraftBukkit start
                } while (!entityhorsechestedabstract.isTamed());
                ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink below
                ServerLevel world = pointer.level();
                org.bukkit.block.Block block = CraftBlock.at(world, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseArmorEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) entityhorsechestedabstract.getBukkitEntity());
                if (!DispenserBlock.eventFired) {
                    world.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    // stack.grow(1); // Paper - shrink below (this was actually missing and should be here, added it commented out to be consistent)
                    return stack;
                }

                boolean shrink = true; // Paper
                if (!event.getItem().equals(craftItem)) {
                    shrink = false; // Paper - shrink below
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                entityhorsechestedabstract.getSlot(499).set(CraftItemStack.asNMSCopy(event.getItem()));
                // CraftBukkit end

                if (shrink) stack.shrink(1); // Paper - actually handle here
                this.setSuccess(true);
                return stack;
            }
        });
        DispenserBlock.registerBehavior(Items.OAK_BOAT, new BoatDispenseItemBehavior(Boat.Type.OAK));
        DispenserBlock.registerBehavior(Items.SPRUCE_BOAT, new BoatDispenseItemBehavior(Boat.Type.SPRUCE));
        DispenserBlock.registerBehavior(Items.BIRCH_BOAT, new BoatDispenseItemBehavior(Boat.Type.BIRCH));
        DispenserBlock.registerBehavior(Items.JUNGLE_BOAT, new BoatDispenseItemBehavior(Boat.Type.JUNGLE));
        DispenserBlock.registerBehavior(Items.DARK_OAK_BOAT, new BoatDispenseItemBehavior(Boat.Type.DARK_OAK));
        DispenserBlock.registerBehavior(Items.ACACIA_BOAT, new BoatDispenseItemBehavior(Boat.Type.ACACIA));
        DispenserBlock.registerBehavior(Items.CHERRY_BOAT, new BoatDispenseItemBehavior(Boat.Type.CHERRY));
        DispenserBlock.registerBehavior(Items.MANGROVE_BOAT, new BoatDispenseItemBehavior(Boat.Type.MANGROVE));
        DispenserBlock.registerBehavior(Items.BAMBOO_RAFT, new BoatDispenseItemBehavior(Boat.Type.BAMBOO));
        DispenserBlock.registerBehavior(Items.OAK_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.OAK, true));
        DispenserBlock.registerBehavior(Items.SPRUCE_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.SPRUCE, true));
        DispenserBlock.registerBehavior(Items.BIRCH_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.BIRCH, true));
        DispenserBlock.registerBehavior(Items.JUNGLE_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.JUNGLE, true));
        DispenserBlock.registerBehavior(Items.DARK_OAK_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.DARK_OAK, true));
        DispenserBlock.registerBehavior(Items.ACACIA_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.ACACIA, true));
        DispenserBlock.registerBehavior(Items.CHERRY_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.CHERRY, true));
        DispenserBlock.registerBehavior(Items.MANGROVE_CHEST_BOAT, new BoatDispenseItemBehavior(Boat.Type.MANGROVE, true));
        DispenserBlock.registerBehavior(Items.BAMBOO_CHEST_RAFT, new BoatDispenseItemBehavior(Boat.Type.BAMBOO, true));
        DefaultDispenseItemBehavior dispensebehavioritem1 = new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                DispensibleContainerItem dispensiblecontaineritem = (DispensibleContainerItem) stack.getItem();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                ServerLevel worldserver = pointer.level();

                // CraftBukkit start
                int x = blockposition.getX();
                int y = blockposition.getY();
                int z = blockposition.getZ();
                BlockState iblockdata = worldserver.getBlockState(blockposition);
                // Paper start - correctly check if the bucket place will succeed
                /* Taken from SolidBucketItem#emptyContents */
                boolean willEmptyContentsSolidBucketItem = dispensiblecontaineritem instanceof net.minecraft.world.item.SolidBucketItem && worldserver.isInWorldBounds(blockposition) && iblockdata.isAir();
                /* Taken from BucketItem#emptyContents */
                boolean willEmptyBucketItem = dispensiblecontaineritem instanceof final BucketItem bucketItem && bucketItem.content instanceof net.minecraft.world.level.material.FlowingFluid && (iblockdata.isAir() || iblockdata.canBeReplaced(bucketItem.content) || (iblockdata.getBlock() instanceof LiquidBlockContainer liquidBlockContainer && liquidBlockContainer.canPlaceLiquid(null, worldserver, blockposition, iblockdata, bucketItem.content)));
                if (willEmptyContentsSolidBucketItem || willEmptyBucketItem) {
                // Paper end - correctly check if the bucket place will succeed
                    org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
                    CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

                    BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(x, y, z));
                    if (!DispenserBlock.eventFired) {
                        worldserver.getCraftServer().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        return stack;
                    }

                    if (!event.getItem().equals(craftItem)) {
                        // Chain to handler for new item
                        ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                        if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                            idispensebehavior.dispense(pointer, eventStack);
                            return stack;
                        }
                    }

                    dispensiblecontaineritem = (DispensibleContainerItem) CraftItemStack.asNMSCopy(event.getItem()).getItem();
                }
                // CraftBukkit end

                if (dispensiblecontaineritem.emptyContents((Player) null, worldserver, blockposition, (BlockHitResult) null)) {
                    dispensiblecontaineritem.checkExtraContent((Player) null, worldserver, stack, blockposition);
                    return this.consumeWithRemainder(pointer, stack, new ItemStack(Items.BUCKET));
                } else {
                    return this.defaultDispenseItemBehavior.dispense(pointer, stack);
                }
            }
        };

        DispenserBlock.registerBehavior(Items.LAVA_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.WATER_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.POWDER_SNOW_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.SALMON_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.COD_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.PUFFERFISH_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.TROPICAL_FISH_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.AXOLOTL_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.TADPOLE_BUCKET, dispensebehavioritem1);
        DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                BlockState iblockdata = worldserver.getBlockState(blockposition);
                Block block = iblockdata.getBlock();

                if (block instanceof BucketPickup ifluidsource) {
                    ItemStack itemstack1 = ifluidsource.pickupBlock((Player) null, DummyGeneratorAccess.INSTANCE, blockposition, iblockdata); // CraftBukkit

                    if (itemstack1.isEmpty()) {
                        return super.execute(pointer, stack);
                    } else {
                        worldserver.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, blockposition);
                        Item item = itemstack1.getItem();

                        // CraftBukkit start
                        org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
                        CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

                        BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                        if (!DispenserBlock.eventFired) {
                            worldserver.getCraftServer().getPluginManager().callEvent(event);
                        }

                        if (event.isCancelled()) {
                            return stack;
                        }

                        if (!event.getItem().equals(craftItem)) {
                            // Chain to handler for new item
                            ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                            DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                            if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                                idispensebehavior.dispense(pointer, eventStack);
                                return stack;
                            }
                        }

                        itemstack1 = ifluidsource.pickupBlock((Player) null, worldserver, blockposition, iblockdata); // From above
                        // CraftBukkit end

                        return this.consumeWithRemainder(pointer, stack, new ItemStack(item));
                    }
                } else {
                    return super.execute(pointer, stack);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.FLINT_AND_STEEL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack); // Paper - ignore stack size on damageable items

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return stack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                // CraftBukkit end

                this.setSuccess(true);
                Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
                BlockPos blockposition = pointer.pos().relative(enumdirection);
                BlockState iblockdata = worldserver.getBlockState(blockposition);

                if (BaseFireBlock.canBePlacedAt(worldserver, blockposition, enumdirection)) {
                    // CraftBukkit start - Ignition by dispensing flint and steel
                    if (!org.bukkit.craftbukkit.event.CraftEventFactory.callBlockIgniteEvent(worldserver, blockposition, pointer.pos()).isCancelled()) {
                        worldserver.setBlockAndUpdate(blockposition, BaseFireBlock.getState(worldserver, blockposition));
                        worldserver.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockposition);
                    }
                    // CraftBukkit end
                } else if (!CampfireBlock.canLight(iblockdata) && !CandleBlock.canLight(iblockdata) && !CandleCakeBlock.canLight(iblockdata)) {
                    if (iblockdata.getBlock() instanceof TntBlock && org.bukkit.craftbukkit.event.CraftEventFactory.callTNTPrimeEvent(worldserver, blockposition, org.bukkit.event.block.TNTPrimeEvent.PrimeCause.DISPENSER, null, pointer.pos())) { // CraftBukkit - TNTPrimeEvent
                        TntBlock.explode(worldserver, blockposition);
                        worldserver.removeBlock(blockposition, false);
                    } else {
                        this.setSuccess(false);
                    }
                } else {
                    worldserver.setBlockAndUpdate(blockposition, (BlockState) iblockdata.setValue(BlockStateProperties.LIT, true));
                    worldserver.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_CHANGE, blockposition);
                }

                if (this.isSuccess()) {
                    stack.hurtAndBreak(1, worldserver, (ServerPlayer) null, (item) -> {
                    });
                }

                return stack;
            }
        });
        DispenserBlock.registerBehavior(Items.BONE_MEAL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                this.setSuccess(true);
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                // CraftBukkit start
                org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector(0, 0, 0));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return stack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }

                worldserver.captureTreeGeneration = true;
                // CraftBukkit end

                if (!BoneMealItem.growCrop(stack, worldserver, blockposition) && !BoneMealItem.growWaterPlant(stack, worldserver, blockposition, (Direction) null)) {
                    this.setSuccess(false);
                } else if (!worldserver.isClientSide) {
                    worldserver.levelEvent(1505, blockposition, 15);
                }
                // CraftBukkit start
                worldserver.captureTreeGeneration = false;
                if (worldserver.capturedBlockStates.size() > 0) {
                    TreeType treeType = SaplingBlock.treeType;
                    SaplingBlock.treeType = null;
                    Location location = CraftLocation.toBukkit(blockposition, worldserver.getWorld());
                    List<org.bukkit.block.BlockState> blocks = new java.util.ArrayList<>(worldserver.capturedBlockStates.values());
                    worldserver.capturedBlockStates.clear();
                    StructureGrowEvent structureEvent = null;
                    if (treeType != null) {
                        structureEvent = new StructureGrowEvent(location, treeType, false, null, blocks);
                        org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                    }

                    BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(location.getBlock(), null, blocks);
                    fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                    org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                    if (!fertilizeEvent.isCancelled()) {
                        for (org.bukkit.block.BlockState blockstate : blocks) {
                            blockstate.update(true);
                        }
                    }
                }
                // CraftBukkit end

                return stack;
            }
        });
        DispenserBlock.registerBehavior(Blocks.TNT, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                // CraftBukkit start
                // EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(worldserver, (double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D, (EntityLiving) null);

                ItemStack itemstack1 = stack.copyWithCount(1); // Paper - shrink at end and single item in event
                org.bukkit.block.Block block = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                BlockDispenseEvent event = new BlockDispenseEvent(block, craftItem.clone(), new org.bukkit.util.Vector((double) blockposition.getX() + 0.5D, (double) blockposition.getY(), (double) blockposition.getZ() + 0.5D));
                if (!DispenserBlock.eventFired) {
                   worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    // stack.grow(1); // Paper - shrink below
                    return stack;
                }

                boolean shrink = true; // Paper
                if (!event.getItem().equals(craftItem)) {
                    shrink = false; // Paper - shrink below
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }

                PrimedTnt entitytntprimed = new PrimedTnt(worldserver, event.getVelocity().getX(), event.getVelocity().getY(), event.getVelocity().getZ(), (LivingEntity) null);
                // CraftBukkit end

                worldserver.addFreshEntity(entitytntprimed);
                worldserver.playSound((Player) null, entitytntprimed.getX(), entitytntprimed.getY(), entitytntprimed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
                worldserver.gameEvent((Entity) null, (Holder) GameEvent.ENTITY_PLACE, blockposition);
                if (shrink) stack.shrink(1); // Paper - actually handle here
                return stack;
            }
        });
        OptionalDispenseItemBehavior dispensebehaviormaybe1 = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                this.setSuccess(ArmorItem.dispenseArmor(pointer, stack));
                return stack;
            }
        };

        DispenserBlock.registerBehavior(Items.CREEPER_HEAD, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.ZOMBIE_HEAD, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.DRAGON_HEAD, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.SKELETON_SKULL, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.PIGLIN_HEAD, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.PLAYER_HEAD, dispensebehaviormaybe1);
        DispenserBlock.registerBehavior(Items.WITHER_SKELETON_SKULL, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();
                Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
                BlockPos blockposition = pointer.pos().relative(enumdirection);

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return stack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                // CraftBukkit end

                if (worldserver.isEmptyBlock(blockposition) && WitherSkullBlock.canSpawnMob(worldserver, blockposition, stack)) {
                    worldserver.setBlock(blockposition, (BlockState) Blocks.WITHER_SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, RotationSegment.convertToSegment(enumdirection)), 3);
                    worldserver.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockposition);
                    BlockEntity tileentity = worldserver.getBlockEntity(blockposition);

                    if (tileentity instanceof SkullBlockEntity) {
                        WitherSkullBlock.checkSpawn(worldserver, blockposition, (SkullBlockEntity) tileentity);
                    }

                    stack.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(ArmorItem.dispenseArmor(pointer, stack));
                }

                return stack;
            }
        });
        DispenserBlock.registerBehavior(Blocks.CARVED_PUMPKIN, new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                CarvedPumpkinBlock blockpumpkincarved = (CarvedPumpkinBlock) Blocks.CARVED_PUMPKIN;

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - single item in event

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return stack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                // CraftBukkit end

                if (worldserver.isEmptyBlock(blockposition) && blockpumpkincarved.canSpawnGolem(worldserver, blockposition)) {
                    if (!worldserver.isClientSide) {
                        worldserver.setBlock(blockposition, blockpumpkincarved.defaultBlockState(), 3);
                        worldserver.gameEvent((Entity) null, (Holder) GameEvent.BLOCK_PLACE, blockposition);
                    }

                    stack.shrink(1);
                    this.setSuccess(true);
                } else {
                    this.setSuccess(ArmorItem.dispenseArmor(pointer, stack));
                }

                return stack;
            }
        });
        DispenserBlock.registerBehavior(Blocks.SHULKER_BOX.asItem(), new ShulkerBoxDispenseBehavior());
        DyeColor[] aenumcolor = DyeColor.values();
        int i = aenumcolor.length;

        for (int j = 0; j < i; ++j) {
            DyeColor enumcolor = aenumcolor[j];

            DispenserBlock.registerBehavior(ShulkerBoxBlock.getBlockByColor(enumcolor).asItem(), new ShulkerBoxDispenseBehavior());
        }

        DispenserBlock.registerBehavior(Items.GLASS_BOTTLE.asItem(), new OptionalDispenseItemBehavior() {
            private ItemStack takeLiquid(BlockSource pointer, ItemStack oldStack, ItemStack newStack) {
                pointer.level().gameEvent((Entity) null, (Holder) GameEvent.FLUID_PICKUP, pointer.pos());
                return this.consumeWithRemainder(pointer, oldStack, newStack);
            }

            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                this.setSuccess(false);
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                BlockState iblockdata = worldserver.getBlockState(blockposition);

                // CraftBukkit start
                org.bukkit.block.Block bukkitBlock = CraftBlock.at(worldserver, pointer.pos());
                CraftItemStack craftItem = CraftItemStack.asCraftMirror(stack.copyWithCount(1)); // Paper - only single item in event

                BlockDispenseEvent event = new BlockDispenseEvent(bukkitBlock, craftItem.clone(), new org.bukkit.util.Vector(blockposition.getX(), blockposition.getY(), blockposition.getZ()));
                if (!DispenserBlock.eventFired) {
                    worldserver.getCraftServer().getPluginManager().callEvent(event);
                }

                if (event.isCancelled()) {
                    return stack;
                }

                if (!event.getItem().equals(craftItem)) {
                    // Chain to handler for new item
                    ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                    DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                    if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != this) {
                        idispensebehavior.dispense(pointer, eventStack);
                        return stack;
                    }
                }
                // CraftBukkit end

                if (iblockdata.is(BlockTags.BEEHIVES, (blockbase_blockdata) -> {
                    return blockbase_blockdata.hasProperty(BeehiveBlock.HONEY_LEVEL) && blockbase_blockdata.getBlock() instanceof BeehiveBlock;
                }) && (Integer) iblockdata.getValue(BeehiveBlock.HONEY_LEVEL) >= 5) {
                    ((BeehiveBlock) iblockdata.getBlock()).releaseBeesAndResetHoneyLevel(worldserver, iblockdata, blockposition, (Player) null, BeehiveBlockEntity.BeeReleaseStatus.BEE_RELEASED);
                    this.setSuccess(true);
                    return this.takeLiquid(pointer, stack, new ItemStack(Items.HONEY_BOTTLE));
                } else if (worldserver.getFluidState(blockposition).is(FluidTags.WATER)) {
                    this.setSuccess(true);
                    return this.takeLiquid(pointer, stack, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                } else {
                    return super.execute(pointer, stack);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.GLOWSTONE, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                Direction enumdirection = (Direction) pointer.state().getValue(DispenserBlock.FACING);
                BlockPos blockposition = pointer.pos().relative(enumdirection);
                ServerLevel worldserver = pointer.level();
                BlockState iblockdata = worldserver.getBlockState(blockposition);

                this.setSuccess(true);
                if (iblockdata.is(Blocks.RESPAWN_ANCHOR)) {
                    if ((Integer) iblockdata.getValue(RespawnAnchorBlock.CHARGE) != 4) {
                        RespawnAnchorBlock.charge((Entity) null, worldserver, blockposition, iblockdata);
                        stack.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }

                    return stack;
                } else {
                    return super.execute(pointer, stack);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.SHEARS.asItem(), new ShearsDispenseItemBehavior());
        DispenserBlock.registerBehavior(Items.BRUSH.asItem(), new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource pointer, ItemStack stack) {
                ServerLevel worldserver = pointer.level();
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                List<Armadillo> list = worldserver.getEntitiesOfClass(Armadillo.class, new AABB(blockposition), EntitySelector.NO_SPECTATORS);

                if (list.isEmpty()) {
                    this.setSuccess(false);
                    return stack;
                } else {
                    // CraftBukkit start
                    ItemStack itemstack1 = stack;
                    ServerLevel world = pointer.level();
                    org.bukkit.block.Block block = CraftBlock.at(world, pointer.pos());
                    CraftItemStack craftItem = CraftItemStack.asCraftMirror(itemstack1);

                    BlockDispenseEvent event = new BlockDispenseArmorEvent(block, craftItem.clone(), (org.bukkit.craftbukkit.entity.CraftLivingEntity) list.get(0).getBukkitEntity());
                    if (!DispenserBlock.eventFired) {
                        world.getCraftServer().getPluginManager().callEvent(event);
                    }

                    if (event.isCancelled()) {
                        return stack;
                    }

                    if (!event.getItem().equals(craftItem)) {
                        // Chain to handler for new item
                        ItemStack eventStack = CraftItemStack.asNMSCopy(event.getItem());
                        DispenseItemBehavior idispensebehavior = (DispenseItemBehavior) DispenserBlock.DISPENSER_REGISTRY.get(eventStack.getItem());
                        if (idispensebehavior != DispenseItemBehavior.NOOP && idispensebehavior != ArmorItem.DISPENSE_ITEM_BEHAVIOR) {
                            idispensebehavior.dispense(pointer, eventStack);
                            return stack;
                        }
                    }
                    // CraftBukkit end
                    Iterator iterator1 = list.iterator();

                    Armadillo armadillo;

                    do {
                        if (!iterator1.hasNext()) {
                            this.setSuccess(false);
                            return stack;
                        }

                        armadillo = (Armadillo) iterator1.next();
                    } while (!armadillo.brushOffScute());

                    stack.hurtAndBreak(16, worldserver, (ServerPlayer) null, (item) -> {
                    });
                    return stack;
                }
            }
        });
        DispenserBlock.registerBehavior(Items.HONEYCOMB, new OptionalDispenseItemBehavior() {
            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                BlockPos blockposition = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));
                ServerLevel worldserver = pointer.level();
                BlockState iblockdata = worldserver.getBlockState(blockposition);
                Optional<BlockState> optional = HoneycombItem.getWaxed(iblockdata);

                if (optional.isPresent()) {
                    worldserver.setBlockAndUpdate(blockposition, (BlockState) optional.get());
                    worldserver.levelEvent(3003, blockposition, 0);
                    stack.shrink(1);
                    this.setSuccess(true);
                    return stack;
                } else {
                    return super.execute(pointer, stack);
                }
            }
        });
        DispenserBlock.registerBehavior(Items.POTION, new DefaultDispenseItemBehavior() {
            private final DefaultDispenseItemBehavior defaultDispenseItemBehavior = new DefaultDispenseItemBehavior();

            @Override
            public ItemStack execute(BlockSource pointer, ItemStack stack) {
                PotionContents potioncontents = (PotionContents) stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);

                if (!potioncontents.is(Potions.WATER)) {
                    return this.defaultDispenseItemBehavior.dispense(pointer, stack);
                } else {
                    ServerLevel worldserver = pointer.level();
                    BlockPos blockposition = pointer.pos();
                    BlockPos blockposition1 = pointer.pos().relative((Direction) pointer.state().getValue(DispenserBlock.FACING));

                    if (!worldserver.getBlockState(blockposition1).is(BlockTags.CONVERTABLE_TO_MUD)) {
                        return this.defaultDispenseItemBehavior.dispense(pointer, stack);
                    } else {
                        if (!worldserver.isClientSide) {
                            for (int k = 0; k < 5; ++k) {
                                worldserver.sendParticles(ParticleTypes.SPLASH, (double) blockposition.getX() + worldserver.random.nextDouble(), (double) (blockposition.getY() + 1), (double) blockposition.getZ() + worldserver.random.nextDouble(), 1, 0.0D, 0.0D, 0.0D, 1.0D);
                            }
                        }

                        worldserver.playSound((Player) null, blockposition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        worldserver.gameEvent((Entity) null, (Holder) GameEvent.FLUID_PLACE, blockposition);
                        worldserver.setBlockAndUpdate(blockposition1, Blocks.MUD.defaultBlockState());
                        return this.consumeWithRemainder(pointer, stack, new ItemStack(Items.GLASS_BOTTLE));
                    }
                }
            }
        });
    }
}
