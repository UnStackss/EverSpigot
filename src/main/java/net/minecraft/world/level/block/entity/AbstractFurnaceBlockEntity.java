package net.minecraft.world.level.block.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
// CraftBukkit start
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftItemType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.CookingRecipe;
// CraftBukkit end

public abstract class AbstractFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, RecipeCraftingHolder, StackedContentsCompatible {

    protected static final int SLOT_INPUT = 0;
    protected static final int SLOT_FUEL = 1;
    protected static final int SLOT_RESULT = 2;
    public static final int DATA_LIT_TIME = 0;
    private static final int[] SLOTS_FOR_UP = new int[]{0};
    private static final int[] SLOTS_FOR_DOWN = new int[]{2, 1};
    private static final int[] SLOTS_FOR_SIDES = new int[]{1};
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_COOKING_PROGRESS = 2;
    public static final int DATA_COOKING_TOTAL_TIME = 3;
    public static final int NUM_DATA_VALUES = 4;
    public static final int BURN_TIME_STANDARD = 200;
    public static final int BURN_COOL_SPEED = 2;
    protected NonNullList<ItemStack> items;
    public int litTime;
    int litDuration;
    public int cookingProgress;
    public int cookingTotalTime;
    @Nullable
    private static volatile Map<Item, Integer> fuelCache;
    protected final ContainerData dataAccess;
    public final Object2IntOpenHashMap<ResourceLocation> recipesUsed;
    private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    protected AbstractFurnaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state, RecipeType<? extends AbstractCookingRecipe> recipeType) {
        super(blockEntityType, pos, state);
        this.items = NonNullList.withSize(3, ItemStack.EMPTY);
        this.dataAccess = new ContainerData() {
            @Override
            public int get(int index) {
                switch (index) {
                    case 0:
                        return AbstractFurnaceBlockEntity.this.litTime;
                    case 1:
                        return AbstractFurnaceBlockEntity.this.litDuration;
                    case 2:
                        return AbstractFurnaceBlockEntity.this.cookingProgress;
                    case 3:
                        return AbstractFurnaceBlockEntity.this.cookingTotalTime;
                    default:
                        return 0;
                }
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0:
                        AbstractFurnaceBlockEntity.this.litTime = value;
                        break;
                    case 1:
                        AbstractFurnaceBlockEntity.this.litDuration = value;
                        break;
                    case 2:
                        AbstractFurnaceBlockEntity.this.cookingProgress = value;
                        break;
                    case 3:
                        AbstractFurnaceBlockEntity.this.cookingTotalTime = value;
                }

            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        this.recipesUsed = new Object2IntOpenHashMap();
        this.quickCheck = RecipeManager.createCheck((RecipeType<AbstractCookingRecipe>) recipeType); // CraftBukkit - decompile error // Eclipse fail
    }

    public static void invalidateCache() {
        AbstractFurnaceBlockEntity.fuelCache = null;
    }

    public static Map<Item, Integer> getFuel() {
        Map<Item, Integer> map = AbstractFurnaceBlockEntity.fuelCache;

        if (map != null) {
            return map;
        } else {
            Map<Item, Integer> map1 = Maps.newLinkedHashMap();

            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.LAVA_BUCKET, 20000);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.COAL_BLOCK, 16000);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.BLAZE_ROD, 2400);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.COAL, 1600);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.CHARCOAL, 1600);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.LOGS, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.BAMBOO_BLOCKS, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.PLANKS, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BAMBOO_MOSAIC, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_STAIRS, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BAMBOO_MOSAIC_STAIRS, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_SLABS, 150);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BAMBOO_MOSAIC_SLAB, 150);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_TRAPDOORS, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_PRESSURE_PLATES, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_FENCES, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.FENCE_GATES, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.NOTE_BLOCK, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BOOKSHELF, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.CHISELED_BOOKSHELF, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.LECTERN, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.JUKEBOX, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.CHEST, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.TRAPPED_CHEST, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.CRAFTING_TABLE, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.DAYLIGHT_DETECTOR, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.BANNERS, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.BOW, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.FISHING_ROD, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.LADDER, 300);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.SIGNS, 200);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.HANGING_SIGNS, 800);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.WOODEN_SHOVEL, 200);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.WOODEN_SWORD, 200);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.WOODEN_HOE, 200);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.WOODEN_AXE, 200);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.WOODEN_PICKAXE, 200);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_DOORS, 200);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.BOATS, 1200);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOOL, 100);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOODEN_BUTTONS, 100);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.STICK, 100);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.SAPLINGS, 100);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.BOWL, 100);
            AbstractFurnaceBlockEntity.add(map1, ItemTags.WOOL_CARPETS, 67);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.DRIED_KELP_BLOCK, 4001);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Items.CROSSBOW, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BAMBOO, 50);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.DEAD_BUSH, 100);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.SCAFFOLDING, 50);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.LOOM, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.BARREL, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.CARTOGRAPHY_TABLE, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.FLETCHING_TABLE, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.SMITHING_TABLE, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.COMPOSTER, 300);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.AZALEA, 100);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.FLOWERING_AZALEA, 100);
            AbstractFurnaceBlockEntity.add(map1, (ItemLike) Blocks.MANGROVE_ROOTS, 300);
            AbstractFurnaceBlockEntity.fuelCache = map1;
            return map1;
        }
    }

    // CraftBukkit start - add fields and methods
    private int maxStack = MAX_STACK;
    public List<HumanEntity> transaction = new java.util.ArrayList<HumanEntity>();

    public List<ItemStack> getContents() {
        return this.items;
    }

    public void onOpen(CraftHumanEntity who) {
        this.transaction.add(who);
    }

    public void onClose(CraftHumanEntity who) {
        this.transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return this.transaction;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStack;
    }

    public void setMaxStackSize(int size) {
        this.maxStack = size;
    }

    public Object2IntOpenHashMap<ResourceLocation> getRecipesUsed() {
        return this.recipesUsed; // PAIL private -> public
    }
    // CraftBukkit end

    private static boolean isNeverAFurnaceFuel(Item item) {
        return item.builtInRegistryHolder().is(ItemTags.NON_FLAMMABLE_WOOD);
    }

    private static void add(Map<Item, Integer> fuelTimes, TagKey<Item> tag, int fuelTime) {
        Iterator iterator = BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator();

        while (iterator.hasNext()) {
            Holder<Item> holder = (Holder) iterator.next();

            if (!AbstractFurnaceBlockEntity.isNeverAFurnaceFuel((Item) holder.value())) {
                fuelTimes.put((Item) holder.value(), fuelTime);
            }
        }

    }

    private static void add(Map<Item, Integer> fuelTimes, ItemLike item, int fuelTime) {
        Item item1 = item.asItem();

        if (AbstractFurnaceBlockEntity.isNeverAFurnaceFuel(item1)) {
            if (SharedConstants.IS_RUNNING_IN_IDE) {
                throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("A developer tried to explicitly make fire resistant item " + item1.getName((ItemStack) null).getString() + " a furnace fuel. That will not work!"));
            }
        } else {
            fuelTimes.put(item1, fuelTime);
        }
    }

    private boolean isLit() {
        return this.litTime > 0;
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.items, registryLookup);
        this.litTime = nbt.getShort("BurnTime");
        this.cookingProgress = nbt.getShort("CookTime");
        this.cookingTotalTime = nbt.getShort("CookTimeTotal");
        this.litDuration = this.getBurnDuration((ItemStack) this.items.get(1));
        CompoundTag nbttagcompound1 = nbt.getCompound("RecipesUsed");
        Iterator iterator = nbttagcompound1.getAllKeys().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();

            this.recipesUsed.put(ResourceLocation.parse(s), nbttagcompound1.getInt(s));
        }

    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putShort("BurnTime", (short) this.litTime);
        nbt.putShort("CookTime", (short) this.cookingProgress);
        nbt.putShort("CookTimeTotal", (short) this.cookingTotalTime);
        ContainerHelper.saveAllItems(nbt, this.items, registryLookup);
        CompoundTag nbttagcompound1 = new CompoundTag();

        this.recipesUsed.forEach((minecraftkey, integer) -> {
            nbttagcompound1.putInt(minecraftkey.toString(), integer);
        });
        nbt.put("RecipesUsed", nbttagcompound1);
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity) {
        boolean flag = blockEntity.isLit();
        boolean flag1 = false;

        if (blockEntity.isLit()) {
            --blockEntity.litTime;
        }

        ItemStack itemstack = (ItemStack) blockEntity.items.get(1);
        ItemStack itemstack1 = (ItemStack) blockEntity.items.get(0);
        boolean flag2 = !itemstack1.isEmpty();
        boolean flag3 = !itemstack.isEmpty();

        if (!blockEntity.isLit() && (!flag3 || !flag2)) {
            if (!blockEntity.isLit() && blockEntity.cookingProgress > 0) {
                blockEntity.cookingProgress = Mth.clamp(blockEntity.cookingProgress - 2, 0, blockEntity.cookingTotalTime);
            }
        } else {
            RecipeHolder recipeholder;

            if (flag2) {
                recipeholder = (RecipeHolder) blockEntity.quickCheck.getRecipeFor(new SingleRecipeInput(itemstack1), world).orElse(null); // CraftBukkit - decompile error
            } else {
                recipeholder = null;
            }

            int i = blockEntity.getMaxStackSize();

            if (!blockEntity.isLit() && AbstractFurnaceBlockEntity.canBurn(world.registryAccess(), recipeholder, blockEntity.items, i)) {
                // CraftBukkit start
                CraftItemStack fuel = CraftItemStack.asCraftMirror(itemstack);

                FurnaceBurnEvent furnaceBurnEvent = new FurnaceBurnEvent(CraftBlock.at(world, pos), fuel, blockEntity.getBurnDuration(itemstack));
                world.getCraftServer().getPluginManager().callEvent(furnaceBurnEvent);

                if (furnaceBurnEvent.isCancelled()) {
                    return;
                }

                blockEntity.litTime = furnaceBurnEvent.getBurnTime();
                blockEntity.litDuration = blockEntity.litTime;
                if (blockEntity.isLit() && furnaceBurnEvent.isBurning()) {
                    // CraftBukkit end
                    flag1 = true;
                    if (flag3) {
                        Item item = itemstack.getItem();

                        itemstack.shrink(1);
                        if (itemstack.isEmpty()) {
                            Item item1 = item.getCraftingRemainingItem();

                            blockEntity.items.set(1, item1 == null ? ItemStack.EMPTY : new ItemStack(item1));
                        }
                    }
                }
            }

            if (blockEntity.isLit() && AbstractFurnaceBlockEntity.canBurn(world.registryAccess(), recipeholder, blockEntity.items, i)) {
                // CraftBukkit start
                if (recipeholder != null && blockEntity.cookingProgress == 0) {
                    CraftItemStack source = CraftItemStack.asCraftMirror(blockEntity.items.get(0));
                    CookingRecipe<?> recipe = (CookingRecipe<?>) recipeholder.toBukkitRecipe();

                    FurnaceStartSmeltEvent event = new FurnaceStartSmeltEvent(CraftBlock.at(world, pos), source, recipe);
                    world.getCraftServer().getPluginManager().callEvent(event);

                    blockEntity.cookingTotalTime = event.getTotalCookTime();
                }
                // CraftBukkit end

                ++blockEntity.cookingProgress;
                if (blockEntity.cookingProgress == blockEntity.cookingTotalTime) {
                    blockEntity.cookingProgress = 0;
                    blockEntity.cookingTotalTime = AbstractFurnaceBlockEntity.getTotalCookTime(world, blockEntity);
                    if (AbstractFurnaceBlockEntity.burn(blockEntity.level, blockEntity.worldPosition, world.registryAccess(), recipeholder, blockEntity.items, i)) { // CraftBukkit
                        blockEntity.setRecipeUsed(recipeholder);
                    }

                    flag1 = true;
                }
            } else {
                blockEntity.cookingProgress = 0;
            }
        }

        if (flag != blockEntity.isLit()) {
            flag1 = true;
            state = (BlockState) state.setValue(AbstractFurnaceBlock.LIT, blockEntity.isLit());
            world.setBlock(pos, state, 3);
        }

        if (flag1) {
            setChanged(world, pos, state);
        }

    }

    private static boolean canBurn(RegistryAccess registryManager, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> slots, int count) {
        if (!((ItemStack) slots.get(0)).isEmpty() && recipe != null) {
            ItemStack itemstack = recipe.value().getResultItem(registryManager);

            if (itemstack.isEmpty()) {
                return false;
            } else {
                ItemStack itemstack1 = (ItemStack) slots.get(2);

                return itemstack1.isEmpty() ? true : (!ItemStack.isSameItemSameComponents(itemstack1, itemstack) ? false : (itemstack1.getCount() < count && itemstack1.getCount() < itemstack1.getMaxStackSize() ? true : itemstack1.getCount() < itemstack.getMaxStackSize()));
            }
        } else {
            return false;
        }
    }

    private static boolean burn(Level world, BlockPos blockposition, RegistryAccess iregistrycustom, @Nullable RecipeHolder<?> recipeholder, NonNullList<ItemStack> nonnulllist, int i) { // CraftBukkit
        if (recipeholder != null && AbstractFurnaceBlockEntity.canBurn(iregistrycustom, recipeholder, nonnulllist, i)) {
            ItemStack itemstack = (ItemStack) nonnulllist.get(0);
            ItemStack itemstack1 = recipeholder.value().getResultItem(iregistrycustom);
            ItemStack itemstack2 = (ItemStack) nonnulllist.get(2);

            // CraftBukkit start - fire FurnaceSmeltEvent
            CraftItemStack source = CraftItemStack.asCraftMirror(itemstack);
            org.bukkit.inventory.ItemStack result = CraftItemStack.asBukkitCopy(itemstack1);

            FurnaceSmeltEvent furnaceSmeltEvent = new FurnaceSmeltEvent(CraftBlock.at(world, blockposition), source, result);
            world.getCraftServer().getPluginManager().callEvent(furnaceSmeltEvent);

            if (furnaceSmeltEvent.isCancelled()) {
                return false;
            }

            result = furnaceSmeltEvent.getResult();
            itemstack1 = CraftItemStack.asNMSCopy(result);

            if (!itemstack1.isEmpty()) {
                if (itemstack2.isEmpty()) {
                    nonnulllist.set(2, itemstack1.copy());
                } else if (CraftItemStack.asCraftMirror(itemstack2).isSimilar(result)) {
                    itemstack2.grow(itemstack1.getCount());
                } else {
                    return false;
                }
            }

            /*
            if (itemstack2.isEmpty()) {
                nonnulllist.set(2, itemstack1.copy());
            } else if (ItemStack.isSameItemSameComponents(itemstack2, itemstack1)) {
                itemstack2.grow(1);
            }
            */
            // CraftBukkit end

            if (itemstack.is(Blocks.WET_SPONGE.asItem()) && !((ItemStack) nonnulllist.get(1)).isEmpty() && ((ItemStack) nonnulllist.get(1)).is(Items.BUCKET)) {
                nonnulllist.set(1, new ItemStack(Items.WATER_BUCKET));
            }

            itemstack.shrink(1);
            return true;
        } else {
            return false;
        }
    }

    protected int getBurnDuration(ItemStack fuel) {
        if (fuel.isEmpty()) {
            return 0;
        } else {
            Item item = fuel.getItem();

            return (Integer) AbstractFurnaceBlockEntity.getFuel().getOrDefault(item, 0);
        }
    }

    private static int getTotalCookTime(Level world, AbstractFurnaceBlockEntity furnace) {
        if (world == null) return 200; // CraftBukkit - SPIGOT-4302
        SingleRecipeInput singlerecipeinput = new SingleRecipeInput(furnace.getItem(0));

        return (Integer) furnace.quickCheck.getRecipeFor(singlerecipeinput, world).map((recipeholder) -> {
            return ((AbstractCookingRecipe) recipeholder.value()).getCookingTime();
        }).orElse(200);
    }

    public static boolean isFuel(ItemStack stack) {
        return AbstractFurnaceBlockEntity.getFuel().containsKey(stack.getItem());
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? AbstractFurnaceBlockEntity.SLOTS_FOR_DOWN : (side == Direction.UP ? AbstractFurnaceBlockEntity.SLOTS_FOR_UP : AbstractFurnaceBlockEntity.SLOTS_FOR_SIDES);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return dir == Direction.DOWN && slot == 1 ? stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET) : true;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> inventory) {
        this.items = inventory;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack itemstack1 = (ItemStack) this.items.get(slot);
        boolean flag = !stack.isEmpty() && ItemStack.isSameItemSameComponents(itemstack1, stack);

        this.items.set(slot, stack);
        stack.limitSize(this.getMaxStackSize(stack));
        if (slot == 0 && !flag) {
            this.cookingTotalTime = AbstractFurnaceBlockEntity.getTotalCookTime(this.level, this);
            this.cookingProgress = 0;
            this.setChanged();
        }

    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 2) {
            return false;
        } else if (slot != 1) {
            return true;
        } else {
            ItemStack itemstack1 = (ItemStack) this.items.get(1);

            return AbstractFurnaceBlockEntity.isFuel(stack) || stack.is(Items.BUCKET) && !itemstack1.is(Items.BUCKET);
        }
    }

    @Override
    public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
        if (recipe != null) {
            ResourceLocation minecraftkey = recipe.id();

            this.recipesUsed.addTo(minecraftkey, 1);
        }

    }

    @Nullable
    @Override
    public RecipeHolder<?> getRecipeUsed() {
        return null;
    }

    @Override
    public void awardUsedRecipes(net.minecraft.world.entity.player.Player player, List<ItemStack> ingredients) {}

    public void awardUsedRecipesAndPopExperience(ServerPlayer entityplayer, ItemStack itemstack, int amount) { // CraftBukkit
        List<RecipeHolder<?>> list = this.getRecipesToAwardAndPopExperience(entityplayer.serverLevel(), entityplayer.position(), this.worldPosition, entityplayer, itemstack, amount); // CraftBukkit

        entityplayer.awardRecipes(list);
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            RecipeHolder<?> recipeholder = (RecipeHolder) iterator.next();

            if (recipeholder != null) {
                entityplayer.triggerRecipeCrafted(recipeholder, this.items);
            }
        }

        this.recipesUsed.clear();
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel world, Vec3 pos) {
        // CraftBukkit start
        return this.getRecipesToAwardAndPopExperience(world, pos, this.worldPosition, null, null, 0);
    }

    public List<RecipeHolder<?>> getRecipesToAwardAndPopExperience(ServerLevel worldserver, Vec3 vec3d, BlockPos blockposition, ServerPlayer entityplayer, ItemStack itemstack, int amount) {
        // CraftBukkit end
        List<RecipeHolder<?>> list = Lists.newArrayList();
        ObjectIterator objectiterator = this.recipesUsed.object2IntEntrySet().iterator();

        while (objectiterator.hasNext()) {
            Entry<ResourceLocation> entry = (Entry) objectiterator.next();

            worldserver.getRecipeManager().byKey((ResourceLocation) entry.getKey()).ifPresent((recipeholder) -> {
                list.add(recipeholder);
                AbstractFurnaceBlockEntity.createExperience(worldserver, vec3d, entry.getIntValue(), ((AbstractCookingRecipe) recipeholder.value()).getExperience(), blockposition, entityplayer, itemstack, amount); // CraftBukkit
            });
        }

        return list;
    }

    private static void createExperience(ServerLevel worldserver, Vec3 vec3d, int i, float f, BlockPos blockposition, net.minecraft.world.entity.player.Player entityhuman, ItemStack itemstack, int amount) { // CraftBukkit
        int j = Mth.floor((float) i * f);
        float f1 = Mth.frac((float) i * f);

        if (f1 != 0.0F && Math.random() < (double) f1) {
            ++j;
        }

        // CraftBukkit start - fire FurnaceExtractEvent / BlockExpEvent
        BlockExpEvent event;
        if (amount != 0) {
            event = new FurnaceExtractEvent((Player) entityhuman.getBukkitEntity(), CraftBlock.at(worldserver, blockposition), CraftItemType.minecraftToBukkit(itemstack.getItem()), amount, j);
        } else {
            event = new BlockExpEvent(CraftBlock.at(worldserver, blockposition), j);
        }
        worldserver.getCraftServer().getPluginManager().callEvent(event);
        j = event.getExpToDrop();
        // CraftBukkit end

        ExperienceOrb.award(worldserver, vec3d, j, org.bukkit.entity.ExperienceOrb.SpawnReason.FURNACE, entityhuman); // Paper
    }

    @Override
    public void fillStackedContents(StackedContents finder) {
        Iterator iterator = this.items.iterator();

        while (iterator.hasNext()) {
            ItemStack itemstack = (ItemStack) iterator.next();

            finder.accountStack(itemstack);
        }

    }
}
