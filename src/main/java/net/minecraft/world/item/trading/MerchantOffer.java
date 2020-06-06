package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import org.bukkit.craftbukkit.inventory.CraftMerchantRecipe; // CraftBukkit

public class MerchantOffer {

    public static final Codec<MerchantOffer> CODEC = RecordCodecBuilder.create((instance) -> {
        return instance.group(ItemCost.CODEC.fieldOf("buy").forGetter((merchantrecipe) -> {
            return merchantrecipe.baseCostA;
        }), ItemCost.CODEC.lenientOptionalFieldOf("buyB").forGetter((merchantrecipe) -> {
            return merchantrecipe.costB;
        }), ItemStack.CODEC.fieldOf("sell").forGetter((merchantrecipe) -> {
            return merchantrecipe.result;
        }), Codec.INT.lenientOptionalFieldOf("uses", 0).forGetter((merchantrecipe) -> {
            return merchantrecipe.uses;
        }), Codec.INT.lenientOptionalFieldOf("maxUses", 4).forGetter((merchantrecipe) -> {
            return merchantrecipe.maxUses;
        }), Codec.BOOL.lenientOptionalFieldOf("rewardExp", true).forGetter((merchantrecipe) -> {
            return merchantrecipe.rewardExp;
        }), Codec.INT.lenientOptionalFieldOf("specialPrice", 0).forGetter((merchantrecipe) -> {
            return merchantrecipe.specialPriceDiff;
        }), Codec.INT.lenientOptionalFieldOf("demand", 0).forGetter((merchantrecipe) -> {
            return merchantrecipe.demand;
        }), Codec.FLOAT.lenientOptionalFieldOf("priceMultiplier", 0.0F).forGetter((merchantrecipe) -> {
            return merchantrecipe.priceMultiplier;
        }), Codec.INT.lenientOptionalFieldOf("xp", 1).forGetter((merchantrecipe) -> {
            return merchantrecipe.xp;
        })).apply(instance, MerchantOffer::new);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffer> STREAM_CODEC = StreamCodec.of(MerchantOffer::writeToStream, MerchantOffer::createFromStream);
    public ItemCost baseCostA;
    public Optional<ItemCost> costB;
    public final ItemStack result;
    public int uses;
    public int maxUses;
    public boolean rewardExp;
    public int specialPriceDiff;
    public int demand;
    public float priceMultiplier;
    public int xp;
    // CraftBukkit start
    private CraftMerchantRecipe bukkitHandle;

    public CraftMerchantRecipe asBukkit() {
        return (this.bukkitHandle == null) ? this.bukkitHandle = new CraftMerchantRecipe(this) : this.bukkitHandle;
    }

    public MerchantOffer(ItemCost baseCostA, Optional<ItemCost> costB, ItemStack result, int uses, int maxUses, int experience, float priceMultiplier, int demand, CraftMerchantRecipe bukkit) {
        this(baseCostA, costB, result, uses, maxUses, experience, priceMultiplier, demand);
        this.bukkitHandle = bukkit;
    }
    // CraftBukkit end

    private MerchantOffer(ItemCost firstBuyItem, Optional<ItemCost> secondBuyItem, ItemStack sellItem, int uses, int maxUses, boolean rewardingPlayerExperience, int specialPrice, int demandBonus, float priceMultiplier, int merchantExperience) {
        this.baseCostA = firstBuyItem;
        this.costB = secondBuyItem;
        this.result = sellItem;
        this.uses = uses;
        this.maxUses = maxUses;
        this.rewardExp = rewardingPlayerExperience;
        this.specialPriceDiff = specialPrice;
        this.demand = demandBonus;
        this.priceMultiplier = priceMultiplier;
        this.xp = merchantExperience;
    }

    public MerchantOffer(ItemCost buyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(buyItem, Optional.empty(), sellItem, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemCost firstBuyItem, Optional<ItemCost> secondBuyItem, ItemStack sellItem, int maxUses, int merchantExperience, float priceMultiplier) {
        this(firstBuyItem, secondBuyItem, sellItem, 0, maxUses, merchantExperience, priceMultiplier);
    }

    public MerchantOffer(ItemCost firstBuyItem, Optional<ItemCost> secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier) {
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, merchantExperience, priceMultiplier, 0);
    }

    public MerchantOffer(ItemCost firstBuyItem, Optional<ItemCost> secondBuyItem, ItemStack sellItem, int uses, int maxUses, int merchantExperience, float priceMultiplier, int demandBonus) {
        this(firstBuyItem, secondBuyItem, sellItem, uses, maxUses, true, 0, demandBonus, priceMultiplier, merchantExperience);
    }

    private MerchantOffer(MerchantOffer offer) {
        this(offer.baseCostA, offer.costB, offer.result.copy(), offer.uses, offer.maxUses, offer.rewardExp, offer.specialPriceDiff, offer.demand, offer.priceMultiplier, offer.xp);
    }

    public ItemStack getBaseCostA() {
        return this.baseCostA.itemStack();
    }

    public ItemStack getCostA() {
        return this.baseCostA.itemStack().copyWithCount(this.getModifiedCostCount(this.baseCostA));
    }

    private int getModifiedCostCount(ItemCost firstBuyItem) {
        int i = firstBuyItem.count();
        int j = Math.max(0, Mth.floor((float) (i * this.demand) * this.priceMultiplier));

        return Mth.clamp(i + j + this.specialPriceDiff, 1, firstBuyItem.itemStack().getMaxStackSize());
    }

    public ItemStack getCostB() {
        return (ItemStack) this.costB.map(ItemCost::itemStack).orElse(ItemStack.EMPTY);
    }

    public ItemCost getItemCostA() {
        return this.baseCostA;
    }

    public Optional<ItemCost> getItemCostB() {
        return this.costB;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public void updateDemand() {
        this.demand = Math.max(0, this.demand + this.uses - (this.maxUses - this.uses)); // Paper - Fix MC-163962
    }

    public ItemStack assemble() {
        return this.result.copy();
    }

    public int getUses() {
        return this.uses;
    }

    public void resetUses() {
        this.uses = 0;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void increaseUses() {
        ++this.uses;
    }

    public int getDemand() {
        return this.demand;
    }

    public void addToSpecialPriceDiff(int increment) {
        this.specialPriceDiff += increment;
    }

    public void resetSpecialPriceDiff() {
        this.specialPriceDiff = 0;
    }

    public int getSpecialPriceDiff() {
        return this.specialPriceDiff;
    }

    public void setSpecialPriceDiff(int specialPrice) {
        this.specialPriceDiff = specialPrice;
    }

    public float getPriceMultiplier() {
        return this.priceMultiplier;
    }

    public int getXp() {
        return this.xp;
    }

    public boolean isOutOfStock() {
        return this.uses >= this.maxUses;
    }

    public void setToOutOfStock() {
        this.uses = this.maxUses;
    }

    public boolean needsRestock() {
        return this.uses > 0;
    }

    public boolean shouldRewardExp() {
        return this.rewardExp;
    }

    public boolean satisfiedBy(ItemStack stack, ItemStack buyItem) {
        return this.baseCostA.test(stack) && stack.getCount() >= this.getModifiedCostCount(this.baseCostA) ? (!this.costB.isPresent() ? buyItem.isEmpty() : ((ItemCost) this.costB.get()).test(buyItem) && buyItem.getCount() >= ((ItemCost) this.costB.get()).count()) : false;
    }

    public boolean take(ItemStack firstBuyStack, ItemStack secondBuyStack) {
        if (!this.satisfiedBy(firstBuyStack, secondBuyStack)) {
            return false;
        } else {
            // CraftBukkit start
            if (!this.getCostA().isEmpty()) {
                firstBuyStack.shrink(this.getCostA().getCount());
            }
            // CraftBukkit end
            if (!this.getCostB().isEmpty()) {
                secondBuyStack.shrink(this.getCostB().getCount());
            }

            return true;
        }
    }

    public MerchantOffer copy() {
        return new MerchantOffer(this);
    }

    private static void writeToStream(RegistryFriendlyByteBuf buf, MerchantOffer offer) {
        ItemCost.STREAM_CODEC.encode(buf, offer.getItemCostA());
        ItemStack.STREAM_CODEC.encode(buf, offer.getResult());
        ItemCost.OPTIONAL_STREAM_CODEC.encode(buf, offer.getItemCostB());
        buf.writeBoolean(offer.isOutOfStock());
        buf.writeInt(offer.getUses());
        buf.writeInt(offer.getMaxUses());
        buf.writeInt(offer.getXp());
        buf.writeInt(offer.getSpecialPriceDiff());
        buf.writeFloat(offer.getPriceMultiplier());
        buf.writeInt(offer.getDemand());
    }

    public static MerchantOffer createFromStream(RegistryFriendlyByteBuf buf) {
        ItemCost itemcost = (ItemCost) ItemCost.STREAM_CODEC.decode(buf);
        ItemStack itemstack = (ItemStack) ItemStack.STREAM_CODEC.decode(buf);
        Optional<ItemCost> optional = (Optional) ItemCost.OPTIONAL_STREAM_CODEC.decode(buf);
        boolean flag = buf.readBoolean();
        int i = buf.readInt();
        int j = buf.readInt();
        int k = buf.readInt();
        int l = buf.readInt();
        float f = buf.readFloat();
        int i1 = buf.readInt();
        MerchantOffer merchantrecipe = new MerchantOffer(itemcost, optional, itemstack, i, j, k, f, i1);

        if (flag) {
            merchantrecipe.setToOutOfStock();
        }

        merchantrecipe.setSpecialPriceDiff(l);
        return merchantrecipe;
    }
}
