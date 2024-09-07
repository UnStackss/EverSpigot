package net.minecraft.world.item.trading;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public class MerchantOffers extends ArrayList<MerchantOffer> {
    public static final Codec<MerchantOffers> CODEC = MerchantOffer.CODEC.listOf().fieldOf("Recipes").xmap(MerchantOffers::new, Function.identity()).codec();
    public static final StreamCodec<RegistryFriendlyByteBuf, MerchantOffers> STREAM_CODEC = MerchantOffer.STREAM_CODEC
        .apply(ByteBufCodecs.collection(MerchantOffers::new));

    public MerchantOffers() {
    }

    private MerchantOffers(int size) {
        super(size);
    }

    private MerchantOffers(Collection<MerchantOffer> tradeOffers) {
        super(tradeOffers);
    }

    @Nullable
    public MerchantOffer getRecipeFor(ItemStack firstBuyItem, ItemStack secondBuyItem, int index) {
        if (index > 0 && index < this.size()) {
            MerchantOffer merchantOffer = this.get(index);
            return merchantOffer.satisfiedBy(firstBuyItem, secondBuyItem) ? merchantOffer : null;
        } else {
            for (int i = 0; i < this.size(); i++) {
                MerchantOffer merchantOffer2 = this.get(i);
                if (merchantOffer2.satisfiedBy(firstBuyItem, secondBuyItem)) {
                    return merchantOffer2;
                }
            }

            return null;
        }
    }

    public MerchantOffers copy() {
        MerchantOffers merchantOffers = new MerchantOffers(this.size());

        for (MerchantOffer merchantOffer : this) {
            merchantOffers.add(merchantOffer.copy());
        }

        return merchantOffers;
    }
}
