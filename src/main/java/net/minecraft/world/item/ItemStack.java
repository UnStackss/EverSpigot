package net.minecraft.world.item;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DataResult.Error;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.NullOps;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.TooltipProvider;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.slf4j.Logger;

// CraftBukkit start
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WitherSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.bukkit.TreeType;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.block.CapturedBlockState;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftLocation;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.world.StructureGrowEvent;
// CraftBukkit end

public final class ItemStack implements DataComponentHolder {

    public static final Codec<Holder<Item>> ITEM_NON_AIR_CODEC = BuiltInRegistries.ITEM.holderByNameCodec().validate((holder) -> {
        return holder.is((Holder) Items.AIR.builtInRegistryHolder()) ? DataResult.error(() -> {
            return "Item must not be minecraft:air";
        }) : DataResult.success(holder);
    });
    public static final Codec<ItemStack> CODEC = Codec.lazyInitialized(() -> {
        return RecordCodecBuilder.<ItemStack>create((instance) -> { // CraftBukkit - decompile error
            return instance.group(ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                return itemstack.components.asPatch();
            })).apply(instance, ItemStack::new);
        });
    });
    public static final Codec<ItemStack> SINGLE_ITEM_CODEC = Codec.lazyInitialized(() -> {
        return RecordCodecBuilder.<ItemStack>create((instance) -> { // CraftBukkit - decompile error
            return instance.group(ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder), DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter((itemstack) -> {
                return itemstack.components.asPatch();
            })).apply(instance, (holder, datacomponentpatch) -> {
                return new ItemStack(holder, 1, datacomponentpatch);
            });
        });
    });
    public static final Codec<ItemStack> STRICT_CODEC = ItemStack.CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> STRICT_SINGLE_ITEM_CODEC = ItemStack.SINGLE_ITEM_CODEC.validate(ItemStack::validateStrict);
    public static final Codec<ItemStack> OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(ItemStack.CODEC).xmap((optional) -> {
        return (ItemStack) optional.orElse(ItemStack.EMPTY);
    }, (itemstack) -> {
        return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
    });
    public static final Codec<ItemStack> SIMPLE_ITEM_CODEC = ItemStack.ITEM_NON_AIR_CODEC.xmap(ItemStack::new, ItemStack::getItemHolder);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> OPTIONAL_STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        private static final StreamCodec<RegistryFriendlyByteBuf, Holder<Item>> ITEM_STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ITEM);

        public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
            int i = registryfriendlybytebuf.readVarInt();

            if (i <= 0) {
                return ItemStack.EMPTY;
            } else {
                Holder<Item> holder = (Holder) ITEM_STREAM_CODEC.decode(registryfriendlybytebuf); // CraftBukkit - decompile error
                DataComponentPatch datacomponentpatch = (DataComponentPatch) DataComponentPatch.STREAM_CODEC.decode(registryfriendlybytebuf);

                // CraftBukkit start
                ItemStack itemstack = new ItemStack(holder, i, datacomponentpatch);
                if (false && !datacomponentpatch.isEmpty()) { // Paper - This is no longer needed with raw NBT being handled in metadata
                    CraftItemStack.setItemMeta(itemstack, CraftItemStack.getItemMeta(itemstack));
                }
                return itemstack;
                // CraftBukkit end
            }
        }

        public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
            if (itemstack.isEmpty() || itemstack.getItem() == null) { // CraftBukkit - NPE fix itemstack.getItem()
                registryfriendlybytebuf.writeVarInt(0);
            } else {
                registryfriendlybytebuf.writeVarInt(itemstack.getCount());
                // Spigot start - filter
                // itemstack = itemstack.copy();
                // CraftItemStack.setItemMeta(itemstack, CraftItemStack.getItemMeta(itemstack)); // Paper - This is no longer with raw NBT being handled in metadata
                // Spigot end
                ITEM_STREAM_CODEC.encode(registryfriendlybytebuf, itemstack.getItemHolder()); // CraftBukkit - decompile error
                // Paper start - adventure; conditionally render translatable components
                boolean prev = net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.get();
                try {
                    net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.set(true);
                DataComponentPatch.STREAM_CODEC.encode(registryfriendlybytebuf, itemstack.components.asPatch());
                } finally {
                    net.minecraft.network.chat.ComponentSerialization.DONT_RENDER_TRANSLATABLES.set(prev);
                }
                // Paper end - adventure; conditionally render translatable components
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> STREAM_CODEC = new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
        public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
            ItemStack itemstack = (ItemStack) ItemStack.OPTIONAL_STREAM_CODEC.decode(registryfriendlybytebuf);

            if (itemstack.isEmpty()) {
                throw new DecoderException("Empty ItemStack not allowed");
            } else {
                return itemstack;
            }
        }

        public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
            if (itemstack.isEmpty()) {
                throw new EncoderException("Empty ItemStack not allowed");
            } else {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(registryfriendlybytebuf, itemstack);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> OPTIONAL_LIST_STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
    public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> LIST_STREAM_CODEC = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.collection(NonNullList::createWithCapacity));
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ItemStack EMPTY = new ItemStack((Void) null);
    private static final Component DISABLED_ITEM_TOOLTIP = Component.translatable("item.disabled").withStyle(ChatFormatting.RED);
    private int count;
    private int popTime;
    /** @deprecated */
    @Deprecated
    @Nullable
    private Item item;
    private PatchedDataComponentMap components;
    @Nullable
    private Entity entityRepresentation;

    private static DataResult<ItemStack> validateStrict(ItemStack stack) {
        DataResult<Unit> dataresult = ItemStack.validateComponents(stack.getComponents());

        return dataresult.isError() ? dataresult.map((unit) -> {
            return stack;
        }) : (stack.getCount() > stack.getMaxStackSize() ? DataResult.<ItemStack>error(() -> { // CraftBukkit - decompile error
            int i = stack.getCount();

            return "Item stack with stack size of " + i + " was larger than maximum: " + stack.getMaxStackSize();
        }) : DataResult.success(stack));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ItemStack> validatedStreamCodec(final StreamCodec<RegistryFriendlyByteBuf, ItemStack> basePacketCodec) {
        return new StreamCodec<RegistryFriendlyByteBuf, ItemStack>() {
            public ItemStack decode(RegistryFriendlyByteBuf registryfriendlybytebuf) {
                ItemStack itemstack = (ItemStack) basePacketCodec.decode(registryfriendlybytebuf);

                if (!itemstack.isEmpty()) {
                    RegistryOps<Unit> registryops = registryfriendlybytebuf.registryAccess().createSerializationContext(NullOps.INSTANCE);

                    ItemStack.CODEC.encodeStart(registryops, itemstack).getOrThrow(DecoderException::new);
                }

                return itemstack;
            }

            public void encode(RegistryFriendlyByteBuf registryfriendlybytebuf, ItemStack itemstack) {
                basePacketCodec.encode(registryfriendlybytebuf, itemstack);
            }
        };
    }

    public Optional<TooltipComponent> getTooltipImage() {
        return this.getItem().getTooltipImage(this);
    }

    @Override
    public DataComponentMap getComponents() {
        return (DataComponentMap) (!this.isEmpty() ? this.components : DataComponentMap.EMPTY);
    }

    public DataComponentMap getPrototype() {
        return !this.isEmpty() ? this.getItem().components() : DataComponentMap.EMPTY;
    }

    public DataComponentPatch getComponentsPatch() {
        return !this.isEmpty() ? this.components.asPatch() : DataComponentPatch.EMPTY;
    }

    public ItemStack(ItemLike item) {
        this(item, 1);
    }

    public ItemStack(Holder<Item> entry) {
        this((ItemLike) entry.value(), 1);
    }

    public ItemStack(Holder<Item> item, int count, DataComponentPatch changes) {
        this((ItemLike) item.value(), count, PatchedDataComponentMap.fromPatch(((Item) item.value()).components(), changes));
    }

    public ItemStack(Holder<Item> itemEntry, int count) {
        this((ItemLike) itemEntry.value(), count);
    }

    public ItemStack(ItemLike item, int count) {
        this(item, count, new PatchedDataComponentMap(item.asItem().components()));
    }

    private ItemStack(ItemLike item, int count, PatchedDataComponentMap components) {
        this.item = item.asItem();
        this.count = count;
        this.components = components;
        this.getItem().verifyComponentsAfterLoad(this);
    }

    private ItemStack(@Nullable Void v) {
        this.item = null;
        this.components = new PatchedDataComponentMap(DataComponentMap.EMPTY);
    }

    public static DataResult<Unit> validateComponents(DataComponentMap components) {
        if (components.has(DataComponents.MAX_DAMAGE) && (Integer) components.getOrDefault(DataComponents.MAX_STACK_SIZE, 1) > 1) {
            return DataResult.error(() -> {
                return "Item cannot be both damageable and stackable";
            });
        } else {
            ItemContainerContents itemcontainercontents = (ItemContainerContents) components.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            Iterator iterator = itemcontainercontents.nonEmptyItems().iterator();

            int i;
            int j;

            do {
                if (!iterator.hasNext()) {
                    return DataResult.success(Unit.INSTANCE);
                }

                ItemStack itemstack = (ItemStack) iterator.next();

                i = itemstack.getCount();
                j = itemstack.getMaxStackSize();
            } while (i <= j);

            int finalI = i, finalJ = j; // CraftBukkit - decompile error
            return DataResult.error(() -> {
                return "Item stack with count of " + finalI + " was larger than maximum: " + finalJ; // CraftBukkit - decompile error
            });
        }
    }

    public static Optional<ItemStack> parse(HolderLookup.Provider registries, Tag nbt) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt).resultOrPartial((s) -> {
            ItemStack.LOGGER.error("Tried to load invalid item: '{}'", s);
        });
    }

    public static ItemStack parseOptional(HolderLookup.Provider registries, CompoundTag nbt) {
        return nbt.isEmpty() ? ItemStack.EMPTY : (ItemStack) ItemStack.parse(registries, nbt).orElse(ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        return this == ItemStack.EMPTY || this.item == Items.AIR || this.count <= 0;
    }

    public boolean isItemEnabled(FeatureFlagSet enabledFeatures) {
        return this.isEmpty() || this.getItem().isEnabled(enabledFeatures);
    }

    public ItemStack split(int amount) {
        int j = Math.min(amount, this.getCount());
        ItemStack itemstack = this.copyWithCount(j);

        this.shrink(j);
        return itemstack;
    }

    public ItemStack copyAndClear() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            this.setCount(0);
            return itemstack;
        }
    }

    public Item getItem() {
        return this.isEmpty() ? Items.AIR : this.item;
    }

    public Holder<Item> getItemHolder() {
        return this.getItem().builtInRegistryHolder();
    }

    public boolean is(TagKey<Item> tag) {
        return this.getItem().builtInRegistryHolder().is(tag);
    }

    public boolean is(Item item) {
        return this.getItem() == item;
    }

    public boolean is(Predicate<Holder<Item>> predicate) {
        return predicate.test(this.getItem().builtInRegistryHolder());
    }

    public boolean is(Holder<Item> itemEntry) {
        return this.getItem().builtInRegistryHolder() == itemEntry;
    }

    public boolean is(HolderSet<Item> registryEntryList) {
        return registryEntryList.contains(this.getItemHolder());
    }

    public Stream<TagKey<Item>> getTags() {
        return this.getItem().builtInRegistryHolder().tags();
    }

    public InteractionResult useOn(UseOnContext context) {
        net.minecraft.world.entity.player.Player entityhuman = context.getPlayer();
        BlockPos blockposition = context.getClickedPos();

        if (entityhuman != null && !entityhuman.getAbilities().mayBuild && !this.canPlaceOnBlockInAdventureMode(new BlockInWorld(context.getLevel(), blockposition, false))) {
            return InteractionResult.PASS;
        } else {
            Item item = this.getItem();
            // CraftBukkit start - handle all block place event logic here
            DataComponentPatch oldData = this.components.asPatch();
            int oldCount = this.getCount();
            ServerLevel world = (ServerLevel) context.getLevel();

            if (!(item instanceof BucketItem/* || item instanceof SolidBucketItem*/)) { // if not bucket // Paper - Fix cancelled powdered snow bucket placement
                world.captureBlockStates = true;
                // special case bonemeal
                if (item == Items.BONE_MEAL) {
                    world.captureTreeGeneration = true;
                }
            }
            InteractionResult enuminteractionresult;
            try {
                enuminteractionresult = item.useOn(context);
            } finally {
                world.captureBlockStates = false;
            }
            DataComponentPatch newData = this.components.asPatch();
            int newCount = this.getCount();
            this.setCount(oldCount);
            this.restorePatch(oldData);
            if (enuminteractionresult.consumesAction() && world.captureTreeGeneration && world.capturedBlockStates.size() > 0) {
                world.captureTreeGeneration = false;
                Location location = CraftLocation.toBukkit(blockposition, world.getWorld());
                TreeType treeType = SaplingBlock.treeType;
                SaplingBlock.treeType = null;
                List<CraftBlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                world.capturedBlockStates.clear();
                StructureGrowEvent structureEvent = null;
                if (treeType != null) {
                    boolean isBonemeal = this.getItem() == Items.BONE_MEAL;
                    structureEvent = new StructureGrowEvent(location, treeType, isBonemeal, (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                    org.bukkit.Bukkit.getPluginManager().callEvent(structureEvent);
                }

                BlockFertilizeEvent fertilizeEvent = new BlockFertilizeEvent(CraftBlock.at(world, blockposition), (Player) entityhuman.getBukkitEntity(), (List< BlockState>) (List<? extends BlockState>) blocks);
                fertilizeEvent.setCancelled(structureEvent != null && structureEvent.isCancelled());
                org.bukkit.Bukkit.getPluginManager().callEvent(fertilizeEvent);

                if (!fertilizeEvent.isCancelled()) {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), oldData)) {
                        this.restorePatch(newData);
                        this.setCount(newCount);
                    }
                    for (CraftBlockState blockstate : blocks) {
                        // SPIGOT-7572 - Move fix for SPIGOT-7248 to CapturedBlockState, to allow bees in bee nest
                        CapturedBlockState.setBlockState(blockstate);
                    }
                    entityhuman.awardStat(Stats.ITEM_USED.get(item)); // SPIGOT-7236 - award stat
                }

                SignItem.openSign = null; // SPIGOT-6758 - Reset on early return
                return enuminteractionresult;
            }
            world.captureTreeGeneration = false;

            if (entityhuman != null && enuminteractionresult.indicateItemUse()) {
                InteractionHand enumhand = context.getHand();
                org.bukkit.event.block.BlockPlaceEvent placeEvent = null;
                List<BlockState> blocks = new java.util.ArrayList<>(world.capturedBlockStates.values());
                world.capturedBlockStates.clear();
                if (blocks.size() > 1) {
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockMultiPlaceEvent(world, entityhuman, enumhand, blocks, blockposition.getX(), blockposition.getY(), blockposition.getZ());
                } else if (blocks.size() == 1 && item != Items.POWDER_SNOW_BUCKET) { // Paper - Fix cancelled powdered snow bucket placement
                    placeEvent = org.bukkit.craftbukkit.event.CraftEventFactory.callBlockPlaceEvent(world, entityhuman, enumhand, blocks.get(0), blockposition.getX(), blockposition.getY(), blockposition.getZ());
                }

                if (placeEvent != null && (placeEvent.isCancelled() || !placeEvent.canBuild())) {
                    enuminteractionresult = InteractionResult.FAIL; // cancel placement
                    // PAIL: Remove this when MC-99075 fixed
                    placeEvent.getPlayer().updateInventory();
                    world.capturedTileEntities.clear(); // Paper - Allow chests to be placed with NBT data; clear out block entities as chests and such will pop loot
                    // revert back all captured blocks
                    world.preventPoiUpdated = true; // CraftBukkit - SPIGOT-5710
                    for (BlockState blockstate : blocks) {
                        blockstate.update(true, false);
                    }
                    world.preventPoiUpdated = false;

                    // Brute force all possible updates
                    BlockPos placedPos = ((CraftBlock) placeEvent.getBlock()).getPosition();
                    for (Direction dir : Direction.values()) {
                        ((ServerPlayer) entityhuman).connection.send(new ClientboundBlockUpdatePacket(world, placedPos.relative(dir)));
                    }
                    SignItem.openSign = null; // SPIGOT-6758 - Reset on early return
                } else {
                    // Change the stack to its new contents if it hasn't been tampered with.
                    if (this.getCount() == oldCount && Objects.equals(this.components.asPatch(), oldData)) {
                        this.restorePatch(newData);
                        this.setCount(newCount);
                    }

                    for (Map.Entry<BlockPos, BlockEntity> e : world.capturedTileEntities.entrySet()) {
                        world.setBlockEntity(e.getValue());
                    }

                    for (BlockState blockstate : blocks) {
                        int updateFlag = ((CraftBlockState) blockstate).getFlag();
                        net.minecraft.world.level.block.state.BlockState oldBlock = ((CraftBlockState) blockstate).getHandle();
                        BlockPos newblockposition = ((CraftBlockState) blockstate).getPosition();
                        net.minecraft.world.level.block.state.BlockState block = world.getBlockState(newblockposition);

                        if (!(block.getBlock() instanceof BaseEntityBlock)) { // Containers get placed automatically
                            block.onPlace(world, newblockposition, oldBlock, true, context);
                        }

                        world.notifyAndUpdatePhysics(newblockposition, null, oldBlock, block, world.getBlockState(newblockposition), updateFlag, 512); // send null chunk as chunk.k() returns false by this point
                    }

                    if (this.item == Items.WITHER_SKELETON_SKULL) { // Special case skulls to allow wither spawns to be cancelled
                        BlockPos bp = blockposition;
                        if (!world.getBlockState(blockposition).canBeReplaced()) {
                            if (!world.getBlockState(blockposition).isSolid()) {
                                bp = null;
                            } else {
                                bp = bp.relative(context.getClickedFace());
                            }
                        }
                        if (bp != null) {
                            BlockEntity te = world.getBlockEntity(bp);
                            if (te instanceof SkullBlockEntity) {
                                WitherSkullBlock.checkSpawn(world, bp, (SkullBlockEntity) te);
                            }
                        }
                    }

                    // SPIGOT-4678
                    if (this.item instanceof SignItem && SignItem.openSign != null) {
                        try {
                            if (world.getBlockEntity(SignItem.openSign) instanceof SignBlockEntity tileentitysign) {
                                if (world.getBlockState(SignItem.openSign).getBlock() instanceof SignBlock blocksign) {
                                    blocksign.openTextEdit(entityhuman, tileentitysign, true, io.papermc.paper.event.player.PlayerOpenSignEvent.Cause.PLACE); // Paper - Add PlayerOpenSignEvent
                                }
                            }
                        } finally {
                            SignItem.openSign = null;
                        }
                    }

                    // SPIGOT-7315: Moved from BlockBed#setPlacedBy
                    if (placeEvent != null && this.item instanceof BedItem) {
                        BlockPos position = ((CraftBlock) placeEvent.getBlock()).getPosition();
                        net.minecraft.world.level.block.state.BlockState blockData =  world.getBlockState(position);

                        if (blockData.getBlock() instanceof BedBlock) {
                            world.blockUpdated(position, Blocks.AIR);
                            blockData.updateNeighbourShapes(world, position, 3);
                        }
                    }

                    // SPIGOT-1288 - play sound stripped from ItemBlock
                    if (this.item instanceof BlockItem) {
                        // Paper start - Fix spigot sound playing for BlockItem ItemStacks
                        BlockPos position = new net.minecraft.world.item.context.BlockPlaceContext(context).getClickedPos();
                        net.minecraft.world.level.block.state.BlockState blockData = world.getBlockState(position);
                        SoundType soundeffecttype = blockData.getSoundType();
                        // Paper end - Fix spigot sound playing for BlockItem ItemStacks
                        world.playSound(entityhuman, blockposition, soundeffecttype.getPlaceSound(), SoundSource.BLOCKS, (soundeffecttype.getVolume() + 1.0F) / 2.0F, soundeffecttype.getPitch() * 0.8F);
                    }

                    entityhuman.awardStat(Stats.ITEM_USED.get(item));
                }
            }
            world.capturedTileEntities.clear();
            world.capturedBlockStates.clear();
            // CraftBukkit end

            return enuminteractionresult;
        }
    }

    public float getDestroySpeed(net.minecraft.world.level.block.state.BlockState state) {
        return this.getItem().getDestroySpeed(this, state);
    }

    public InteractionResultHolder<ItemStack> use(Level world, net.minecraft.world.entity.player.Player user, InteractionHand hand) {
        return this.getItem().use(world, user, hand);
    }

    public ItemStack finishUsingItem(Level world, LivingEntity user) {
        return this.getItem().finishUsingItem(this, world, user);
    }

    public Tag save(HolderLookup.Provider registries, Tag prefix) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return (Tag) ItemStack.CODEC.encode(this, registries.createSerializationContext(NbtOps.INSTANCE), prefix).getOrThrow();
        }
    }

    public Tag save(HolderLookup.Provider registries) {
        if (this.isEmpty()) {
            throw new IllegalStateException("Cannot encode empty ItemStack");
        } else {
            return (Tag) ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow();
        }
    }

    public Tag saveOptional(HolderLookup.Provider registries) {
        return (Tag) (this.isEmpty() ? new CompoundTag() : this.save(registries, new CompoundTag()));
    }

    public int getMaxStackSize() {
        return (Integer) this.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.isDamageableItem() || !this.isDamaged());
    }

    public boolean isDamageableItem() {
        return this.has(DataComponents.MAX_DAMAGE) && !this.has(DataComponents.UNBREAKABLE) && this.has(DataComponents.DAMAGE);
    }

    public boolean isDamaged() {
        return this.isDamageableItem() && this.getDamageValue() > 0;
    }

    public int getDamageValue() {
        return Mth.clamp((Integer) this.getOrDefault(DataComponents.DAMAGE, 0), 0, this.getMaxDamage());
    }

    public void setDamageValue(int damage) {
        this.set(DataComponents.DAMAGE, Mth.clamp(damage, 0, this.getMaxDamage()));
    }

    public int getMaxDamage() {
        return (Integer) this.getOrDefault(DataComponents.MAX_DAMAGE, 0);
    }

    public void hurtAndBreak(int amount, ServerLevel world, @Nullable LivingEntity player, Consumer<Item> breakCallback) { // Paper - Add EntityDamageItemEvent
        // Paper start - add param to skip infinite mats check
        this.hurtAndBreak(amount, world, player, breakCallback, false);
    }
    public void hurtAndBreak(int amount, ServerLevel world, @Nullable LivingEntity player, Consumer<Item> breakCallback, boolean force) {
        // Paper end - add param to skip infinite mats check
        if (this.isDamageableItem()) {
            if (player == null || !player.hasInfiniteMaterials() || force) { // Paper
                if (amount > 0) {
                    int originalDamage = amount; // Paper - Expand PlayerItemDamageEvent
                    amount = EnchantmentHelper.processDurabilityChange(world, this, amount);
                    // CraftBukkit start
                    if (player instanceof ServerPlayer serverPlayer) { // Paper - Add EntityDamageItemEvent
                        PlayerItemDamageEvent event = new PlayerItemDamageEvent(serverPlayer.getBukkitEntity(), CraftItemStack.asCraftMirror(this), amount, originalDamage); // Paper - Add EntityDamageItemEvent & Expand PlayerItemDamageEvent
                        event.getPlayer().getServer().getPluginManager().callEvent(event);

                        if (amount != event.getDamage() || event.isCancelled()) {
                            event.getPlayer().updateInventory();
                        }
                        if (event.isCancelled()) {
                            return;
                        }

                        amount = event.getDamage();
                        // Paper start - Add EntityDamageItemEvent
                    } else if (player != null) {
                        io.papermc.paper.event.entity.EntityDamageItemEvent event = new io.papermc.paper.event.entity.EntityDamageItemEvent(player.getBukkitLivingEntity(), CraftItemStack.asCraftMirror(this), amount);
                        if (!event.callEvent()) {
                            return;
                        }
                        amount = event.getDamage();
                        // Paper end - Add EntityDamageItemEvent
                    }
                    // CraftBukkit end
                    if (amount <= 0) {
                        return;
                    }
                }

                if (player instanceof ServerPlayer serverPlayer && amount != 0) { // Paper - Add EntityDamageItemEvent
                    CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(serverPlayer, this, this.getDamageValue() + amount); // Paper - Add EntityDamageItemEvent
                }

                int j = this.getDamageValue() + amount;

                this.setDamageValue(j);
                if (j >= this.getMaxDamage()) {
                    Item item = this.getItem();

                    this.shrink(1);
                    breakCallback.accept(item);
                }

            }
        }
    }

    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot) {
        // Paper start - add param to skip infinite mats check
        this.hurtAndBreak(amount, entity, slot, false);
    }
    public void hurtAndBreak(int amount, LivingEntity entity, EquipmentSlot slot, boolean force) {
        // Paper end - add param to skip infinite mats check
        Level world = entity.level();

        if (world instanceof ServerLevel worldserver) {
            ServerPlayer entityplayer;

            if (entity instanceof ServerPlayer entityplayer1) {
                entityplayer = entityplayer1;
            } else {
                entityplayer = null;
            }

            this.hurtAndBreak(amount, worldserver, entity, (item) -> { // Paper - Add EntityDamageItemEvent
                // CraftBukkit start - Check for item breaking
                if (this.count == 1 && entity instanceof net.minecraft.world.entity.player.Player) {
                    org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerItemBreakEvent((net.minecraft.world.entity.player.Player) entity, this);
                }
                // CraftBukkit end
                if (slot != null) entity.onEquippedItemBroken(item, slot); // Paper - itemstack damage API - do not process entity related callbacks when damaging from API
            }, force); // Paper
        }

    }

    public ItemStack hurtAndConvertOnBreak(int amount, ItemLike itemAfterBreaking, LivingEntity entity, EquipmentSlot slot) {
        this.hurtAndBreak(amount, entity, slot);
        if (this.isEmpty()) {
            ItemStack itemstack = this.transmuteCopyIgnoreEmpty(itemAfterBreaking, 1);

            if (itemstack.isDamageableItem()) {
                itemstack.setDamageValue(0);
            }

            return itemstack;
        } else {
            return this;
        }
    }

    public boolean isBarVisible() {
        return this.getItem().isBarVisible(this);
    }

    public int getBarWidth() {
        return this.getItem().getBarWidth(this);
    }

    public int getBarColor() {
        return this.getItem().getBarColor(this);
    }

    public boolean overrideStackedOnOther(Slot slot, ClickAction clickType, net.minecraft.world.entity.player.Player player) {
        return this.getItem().overrideStackedOnOther(this, slot, clickType, player);
    }

    public boolean overrideOtherStackedOnMe(ItemStack stack, Slot slot, ClickAction clickType, net.minecraft.world.entity.player.Player player, SlotAccess cursorStackReference) {
        return this.getItem().overrideOtherStackedOnMe(this, stack, slot, clickType, player, cursorStackReference);
    }

    public boolean hurtEnemy(LivingEntity target, net.minecraft.world.entity.player.Player player) {
        Item item = this.getItem();

        if (item.hurtEnemy(this, target, player)) {
            player.awardStat(Stats.ITEM_USED.get(item));
            return true;
        } else {
            return false;
        }
    }

    public void postHurtEnemy(LivingEntity target, net.minecraft.world.entity.player.Player player) {
        this.getItem().postHurtEnemy(this, target, player);
    }

    public void mineBlock(Level world, net.minecraft.world.level.block.state.BlockState state, BlockPos pos, net.minecraft.world.entity.player.Player miner) {
        Item item = this.getItem();

        if (item.mineBlock(this, world, state, pos, miner)) {
            miner.awardStat(Stats.ITEM_USED.get(item));
        }

    }

    public boolean isCorrectToolForDrops(net.minecraft.world.level.block.state.BlockState state) {
        return this.getItem().isCorrectToolForDrops(this, state);
    }

    public InteractionResult interactLivingEntity(net.minecraft.world.entity.player.Player user, LivingEntity entity, InteractionHand hand) {
        return this.getItem().interactLivingEntity(this, user, entity, hand);
    }

    public ItemStack copy() {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = new ItemStack(this.getItem(), this.count, this.components.copy());

            itemstack.setPopTime(this.getPopTime());
            return itemstack;
        }
    }

    public ItemStack copyWithCount(int count) {
        if (this.isEmpty()) {
            return ItemStack.EMPTY;
        } else {
            ItemStack itemstack = this.copy();

            itemstack.setCount(count);
            return itemstack;
        }
    }

    public ItemStack transmuteCopy(ItemLike item) {
        return this.transmuteCopy(item, this.getCount());
    }

    public ItemStack transmuteCopy(ItemLike item, int count) {
        return this.isEmpty() ? ItemStack.EMPTY : this.transmuteCopyIgnoreEmpty(item, count);
    }

    private ItemStack transmuteCopyIgnoreEmpty(ItemLike item, int count) {
        return new ItemStack(item.asItem().builtInRegistryHolder(), count, this.components.asPatch());
    }

    public static boolean matches(ItemStack left, ItemStack right) {
        return left == right ? true : (left.getCount() != right.getCount() ? false : ItemStack.isSameItemSameComponents(left, right));
    }

    /** @deprecated */
    @Deprecated
    public static boolean listMatches(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        } else {
            for (int i = 0; i < left.size(); ++i) {
                if (!ItemStack.matches((ItemStack) left.get(i), (ItemStack) right.get(i))) {
                    return false;
                }
            }

            return true;
        }
    }

    public static boolean isSameItem(ItemStack left, ItemStack right) {
        return left.is(right.getItem());
    }

    public static boolean isSameItemSameComponents(ItemStack stack, ItemStack otherStack) {
        return !stack.is(otherStack.getItem()) ? false : (stack.isEmpty() && otherStack.isEmpty() ? true : Objects.equals(stack.components, otherStack.components));
    }

    public static MapCodec<ItemStack> lenientOptionalFieldOf(String fieldName) {
        return ItemStack.CODEC.lenientOptionalFieldOf(fieldName).xmap((optional) -> {
            return (ItemStack) optional.orElse(ItemStack.EMPTY);
        }, (itemstack) -> {
            return itemstack.isEmpty() ? Optional.empty() : Optional.of(itemstack);
        });
    }

    public static int hashItemAndComponents(@Nullable ItemStack stack) {
        if (stack != null) {
            int i = 31 + stack.getItem().hashCode();

            return 31 * i + stack.getComponents().hashCode();
        } else {
            return 0;
        }
    }

    /** @deprecated */
    @Deprecated
    public static int hashStackList(List<ItemStack> stacks) {
        int i = 0;

        ItemStack itemstack;

        for (Iterator iterator = stacks.iterator(); iterator.hasNext(); i = i * 31 + ItemStack.hashItemAndComponents(itemstack)) {
            itemstack = (ItemStack) iterator.next();
        }

        return i;
    }

    public String getDescriptionId() {
        return this.getItem().getDescriptionId(this);
    }

    public String toString() {
        int i = this.getCount();

        return "" + i + " " + String.valueOf(this.getItem());
    }

    public void inventoryTick(Level world, Entity entity, int slot, boolean selected) {
        if (this.popTime > 0) {
            --this.popTime;
        }

        if (this.getItem() != null) {
            this.getItem().inventoryTick(this, world, entity, slot, selected);
        }

    }

    public void onCraftedBy(Level world, net.minecraft.world.entity.player.Player player, int amount) {
        player.awardStat(Stats.ITEM_CRAFTED.get(this.getItem()), amount);
        this.getItem().onCraftedBy(this, world, player);
    }

    public void onCraftedBySystem(Level world) {
        this.getItem().onCraftedPostProcess(this, world);
    }

    public int getUseDuration(LivingEntity user) {
        return this.getItem().getUseDuration(this, user);
    }

    public UseAnim getUseAnimation() {
        return this.getItem().getUseAnimation(this);
    }

    public void releaseUsing(Level world, LivingEntity user, int remainingUseTicks) {
        this.getItem().releaseUsing(this, world, user, remainingUseTicks);
    }

    public boolean useOnRelease() {
        return this.getItem().useOnRelease(this);
    }

    // CraftBukkit start
    public void restorePatch(DataComponentPatch datacomponentpatch) {
        this.components.restorePatch(datacomponentpatch);
    }
    // CraftBukkit end

    @Nullable
    public <T> T set(DataComponentType<? super T> type, @Nullable T value) {
        return this.components.set(type, value);
    }

    @Nullable
    public <T, U> T update(DataComponentType<T> type, T defaultValue, U change, BiFunction<T, U, T> applier) {
        return this.set(type, applier.apply(this.getOrDefault(type, defaultValue), change));
    }

    @Nullable
    public <T> T update(DataComponentType<T> type, T defaultValue, UnaryOperator<T> applier) {
        T t1 = this.getOrDefault(type, defaultValue);

        return this.set(type, applier.apply(t1));
    }

    @Nullable
    public <T> T remove(DataComponentType<? extends T> type) {
        return this.components.remove(type);
    }

    public void applyComponentsAndValidate(DataComponentPatch changes) {
        DataComponentPatch datacomponentpatch1 = this.components.asPatch();

        this.components.applyPatch(changes);
        Optional<Error<ItemStack>> optional = ItemStack.validateStrict(this).error();

        if (optional.isPresent()) {
            ItemStack.LOGGER.error("Failed to apply component patch '{}' to item: '{}'", changes, ((Error) optional.get()).message());
            this.components.restorePatch(datacomponentpatch1);
        } else {
            this.getItem().verifyComponentsAfterLoad(this);
        }
    }

    // Paper start - (this is just a good no conflict location)
    public org.bukkit.inventory.ItemStack asBukkitMirror() {
        return CraftItemStack.asCraftMirror(this);
    }
    public org.bukkit.inventory.ItemStack asBukkitCopy() {
        return CraftItemStack.asCraftMirror(this.copy());
    }
    public static ItemStack fromBukkitCopy(org.bukkit.inventory.ItemStack itemstack) {
        return CraftItemStack.asNMSCopy(itemstack);
    }
    private org.bukkit.craftbukkit.inventory.CraftItemStack bukkitStack;
    public org.bukkit.inventory.ItemStack getBukkitStack() {
        if (bukkitStack == null || bukkitStack.handle != this) {
            bukkitStack = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(this);
        }
        return bukkitStack;
    }
    // Paper end

    public void applyComponents(DataComponentPatch changes) {
        this.components.applyPatch(changes);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public void applyComponents(DataComponentMap components) {
        this.components.setAll(components);
        this.getItem().verifyComponentsAfterLoad(this);
    }

    public Component getHoverName() {
        Component ichatbasecomponent = (Component) this.get(DataComponents.CUSTOM_NAME);

        if (ichatbasecomponent != null) {
            return ichatbasecomponent;
        } else {
            Component ichatbasecomponent1 = (Component) this.get(DataComponents.ITEM_NAME);

            return ichatbasecomponent1 != null ? ichatbasecomponent1 : this.getItem().getName(this);
        }
    }

    private <T extends TooltipProvider> void addToTooltip(DataComponentType<T> componentType, Item.TooltipContext context, Consumer<Component> textConsumer, TooltipFlag type) {
        T t0 = (T) this.get(componentType); // CraftBukkit - decompile error

        if (t0 != null) {
            t0.addToTooltip(context, textConsumer, type);
        }

    }

    public List<Component> getTooltipLines(Item.TooltipContext context, @Nullable net.minecraft.world.entity.player.Player player, TooltipFlag type) {
        if (!type.isCreative() && this.has(DataComponents.HIDE_TOOLTIP)) {
            return List.of();
        } else {
            List<Component> list = Lists.newArrayList();
            MutableComponent ichatmutablecomponent = Component.empty().append(this.getHoverName()).withStyle(this.getRarity().color());

            if (this.has(DataComponents.CUSTOM_NAME)) {
                ichatmutablecomponent.withStyle(ChatFormatting.ITALIC);
            }

            list.add(ichatmutablecomponent);
            if (!type.isAdvanced() && !this.has(DataComponents.CUSTOM_NAME) && this.is(Items.FILLED_MAP)) {
                MapId mapid = (MapId) this.get(DataComponents.MAP_ID);

                if (mapid != null) {
                    list.add(MapItem.getTooltipForId(mapid));
                }
            }

            Objects.requireNonNull(list);
            Consumer<Component> consumer = list::add;

            if (!this.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
                this.getItem().appendHoverText(this, context, list, type);
            }

            this.addToTooltip(DataComponents.JUKEBOX_PLAYABLE, context, consumer, type);
            this.addToTooltip(DataComponents.TRIM, context, consumer, type);
            this.addToTooltip(DataComponents.STORED_ENCHANTMENTS, context, consumer, type);
            this.addToTooltip(DataComponents.ENCHANTMENTS, context, consumer, type);
            this.addToTooltip(DataComponents.DYED_COLOR, context, consumer, type);
            this.addToTooltip(DataComponents.LORE, context, consumer, type);
            this.addAttributeTooltips(consumer, player);
            this.addToTooltip(DataComponents.UNBREAKABLE, context, consumer, type);
            AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

            if (adventuremodepredicate != null && adventuremodepredicate.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_BREAK_HEADER);
                adventuremodepredicate.addToTooltip(consumer);
            }

            AdventureModePredicate adventuremodepredicate1 = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

            if (adventuremodepredicate1 != null && adventuremodepredicate1.showInTooltip()) {
                consumer.accept(CommonComponents.EMPTY);
                consumer.accept(AdventureModePredicate.CAN_PLACE_HEADER);
                adventuremodepredicate1.addToTooltip(consumer);
            }

            if (type.isAdvanced()) {
                if (this.isDamaged()) {
                    list.add(Component.translatable("item.durability", this.getMaxDamage() - this.getDamageValue(), this.getMaxDamage()));
                }

                list.add(Component.literal(BuiltInRegistries.ITEM.getKey(this.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
                int i = this.components.size();

                if (i > 0) {
                    list.add(Component.translatable("item.components", i).withStyle(ChatFormatting.DARK_GRAY));
                }
            }

            if (player != null && !this.getItem().isEnabled(player.level().enabledFeatures())) {
                list.add(ItemStack.DISABLED_ITEM_TOOLTIP);
            }

            return list;
        }
    }

    private void addAttributeTooltips(Consumer<Component> textConsumer, @Nullable net.minecraft.world.entity.player.Player player) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (itemattributemodifiers.showInTooltip()) {
            EquipmentSlotGroup[] aequipmentslotgroup = EquipmentSlotGroup.values();
            int i = aequipmentslotgroup.length;

            for (int j = 0; j < i; ++j) {
                EquipmentSlotGroup equipmentslotgroup = aequipmentslotgroup[j];
                MutableBoolean mutableboolean = new MutableBoolean(true);

                this.forEachModifier(equipmentslotgroup, (holder, attributemodifier) -> {
                    if (mutableboolean.isTrue()) {
                        textConsumer.accept(CommonComponents.EMPTY);
                        textConsumer.accept(Component.translatable("item.modifiers." + equipmentslotgroup.getSerializedName()).withStyle(ChatFormatting.GRAY));
                        mutableboolean.setFalse();
                    }

                    this.addModifierTooltip(textConsumer, player, holder, attributemodifier);
                });
            }

        }
    }

    private void addModifierTooltip(Consumer<Component> textConsumer, @Nullable net.minecraft.world.entity.player.Player player, Holder<Attribute> attribute, AttributeModifier modifier) {
        double d0 = modifier.amount();
        boolean flag = false;

        if (player != null) {
            if (modifier.is(Item.BASE_ATTACK_DAMAGE_ID)) {
                d0 += player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                flag = true;
            } else if (modifier.is(Item.BASE_ATTACK_SPEED_ID)) {
                d0 += player.getAttributeBaseValue(Attributes.ATTACK_SPEED);
                flag = true;
            }
        }

        double d1;

        if (modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            if (attribute.is(Attributes.KNOCKBACK_RESISTANCE)) {
                d1 = d0 * 10.0D;
            } else {
                d1 = d0;
            }
        } else {
            d1 = d0 * 100.0D;
        }

        if (flag) {
            textConsumer.accept(CommonComponents.space().append((Component) Component.translatable("attribute.modifier.equals." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId()))).withStyle(ChatFormatting.DARK_GREEN));
        } else if (d0 > 0.0D) {
            textConsumer.accept(Component.translatable("attribute.modifier.plus." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId())).withStyle(((Attribute) attribute.value()).getStyle(true)));
        } else if (d0 < 0.0D) {
            textConsumer.accept(Component.translatable("attribute.modifier.take." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-d1), Component.translatable(((Attribute) attribute.value()).getDescriptionId())).withStyle(((Attribute) attribute.value()).getStyle(false)));
        }

    }

    public boolean hasFoil() {
        Boolean obool = (Boolean) this.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);

        return obool != null ? obool : this.getItem().isFoil(this);
    }

    public Rarity getRarity() {
        Rarity enumitemrarity = (Rarity) this.getOrDefault(DataComponents.RARITY, Rarity.COMMON);

        if (!this.isEnchanted()) {
            return enumitemrarity;
        } else {
            Rarity enumitemrarity1;

            switch (enumitemrarity) {
                case COMMON:
                case UNCOMMON:
                    enumitemrarity1 = Rarity.RARE;
                    break;
                case RARE:
                    enumitemrarity1 = Rarity.EPIC;
                    break;
                default:
                    enumitemrarity1 = enumitemrarity;
            }

            return enumitemrarity1;
        }
    }

    public boolean isEnchantable() {
        if (!this.getItem().isEnchantable(this)) {
            return false;
        } else {
            ItemEnchantments itemenchantments = (ItemEnchantments) this.get(DataComponents.ENCHANTMENTS);

            return itemenchantments != null && itemenchantments.isEmpty();
        }
    }

    public void enchant(Holder<Enchantment> enchantment, int level) {
        EnchantmentHelper.updateEnchantments(this, (itemenchantments_a) -> {
            itemenchantments_a.upgrade(enchantment, level);
        });
    }

    public boolean isEnchanted() {
        return !((ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)).isEmpty();
    }

    public ItemEnchantments getEnchantments() {
        return (ItemEnchantments) this.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    public boolean isFramed() {
        return this.entityRepresentation instanceof ItemFrame;
    }

    public void setEntityRepresentation(@Nullable Entity holder) {
        if (!this.isEmpty()) {
            this.entityRepresentation = holder;
        }

    }

    @Nullable
    public ItemFrame getFrame() {
        return this.entityRepresentation instanceof ItemFrame ? (ItemFrame) this.getEntityRepresentation() : null;
    }

    @Nullable
    public Entity getEntityRepresentation() {
        return !this.isEmpty() ? this.entityRepresentation : null;
    }

    public void forEachModifier(EquipmentSlotGroup slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeModifierConsumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(slot, attributeModifierConsumer);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(slot, attributeModifierConsumer);
        }

        EnchantmentHelper.forEachModifier(this, slot, attributeModifierConsumer);
    }

    public void forEachModifier(EquipmentSlot slot, BiConsumer<Holder<Attribute>, AttributeModifier> attributeModifierConsumer) {
        ItemAttributeModifiers itemattributemodifiers = (ItemAttributeModifiers) this.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        if (!itemattributemodifiers.modifiers().isEmpty()) {
            itemattributemodifiers.forEach(slot, attributeModifierConsumer);
        } else {
            this.getItem().getDefaultAttributeModifiers().forEach(slot, attributeModifierConsumer);
        }

        EnchantmentHelper.forEachModifier(this, slot, attributeModifierConsumer);
    }

    // CraftBukkit start
    @Deprecated
    public void setItem(Item item) {
        this.bukkitStack = null; // Paper
        this.item = item;
    }
    // CraftBukkit end

    public Component getDisplayName() {
        MutableComponent ichatmutablecomponent = Component.empty().append(this.getHoverName());

        if (this.has(DataComponents.CUSTOM_NAME)) {
            ichatmutablecomponent.withStyle(ChatFormatting.ITALIC);
        }

        MutableComponent ichatmutablecomponent1 = ComponentUtils.wrapInSquareBrackets(ichatmutablecomponent);

        if (!this.isEmpty()) {
            ichatmutablecomponent1.withStyle(this.getRarity().color()).withStyle((chatmodifier) -> {
                return chatmodifier.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(this)));
            });
        }

        return ichatmutablecomponent1;
    }

    public boolean canPlaceOnBlockInAdventureMode(BlockInWorld pos) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_PLACE_ON);

        return adventuremodepredicate != null && adventuremodepredicate.test(pos);
    }

    public boolean canBreakBlockInAdventureMode(BlockInWorld pos) {
        AdventureModePredicate adventuremodepredicate = (AdventureModePredicate) this.get(DataComponents.CAN_BREAK);

        return adventuremodepredicate != null && adventuremodepredicate.test(pos);
    }

    public int getPopTime() {
        return this.popTime;
    }

    public void setPopTime(int bobbingAnimationTime) {
        this.popTime = bobbingAnimationTime;
    }

    public int getCount() {
        return this.isEmpty() ? 0 : this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public void limitSize(int maxCount) {
        if (!this.isEmpty() && this.getCount() > maxCount) {
            this.setCount(maxCount);
        }

    }

    public void grow(int amount) {
        this.setCount(this.getCount() + amount);
    }

    public void shrink(int amount) {
        this.grow(-amount);
    }

    public void consume(int amount, @Nullable LivingEntity entity) {
        if ((entity == null || !entity.hasInfiniteMaterials()) && this != ItemStack.EMPTY) { // CraftBukkit
            this.shrink(amount);
        }

    }

    public ItemStack consumeAndReturn(int amount, @Nullable LivingEntity entity) {
        ItemStack itemstack = this.copyWithCount(amount);

        this.consume(amount, entity);
        return itemstack;
    }

    public void onUseTick(Level world, LivingEntity user, int remainingUseTicks) {
        this.getItem().onUseTick(world, user, this, remainingUseTicks);
    }

    public void onDestroyed(ItemEntity entity) {
        this.getItem().onDestroyed(entity);
    }

    public SoundEvent getDrinkingSound() {
        return this.getItem().getDrinkingSound();
    }

    public SoundEvent getEatingSound() {
        return this.getItem().getEatingSound();
    }

    public SoundEvent getBreakingSound() {
        return this.getItem().getBreakingSound();
    }

    public boolean canBeHurtBy(DamageSource source) {
        return !this.has(DataComponents.FIRE_RESISTANT) || !source.is(DamageTypeTags.IS_FIRE);
    }
}
