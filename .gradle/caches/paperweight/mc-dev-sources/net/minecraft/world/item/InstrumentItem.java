package net.minecraft.world.item;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

public class InstrumentItem extends Item {
    private final TagKey<Instrument> instruments;

    public InstrumentItem(Item.Properties settings, TagKey<Instrument> instrumentTag) {
        super(settings);
        this.instruments = instrumentTag;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        super.appendHoverText(stack, context, tooltip, type);
        Optional<ResourceKey<Instrument>> optional = this.getInstrument(stack).flatMap(Holder::unwrapKey);
        if (optional.isPresent()) {
            MutableComponent mutableComponent = Component.translatable(Util.makeDescriptionId("instrument", optional.get().location()));
            tooltip.add(mutableComponent.withStyle(ChatFormatting.GRAY));
        }
    }

    public static ItemStack create(Item item, Holder<Instrument> instrument) {
        ItemStack itemStack = new ItemStack(item);
        itemStack.set(DataComponents.INSTRUMENT, instrument);
        return itemStack;
    }

    public static void setRandom(ItemStack stack, TagKey<Instrument> instrumentTag, RandomSource random) {
        Optional<Holder<Instrument>> optional = BuiltInRegistries.INSTRUMENT.getRandomElementOf(instrumentTag, random);
        optional.ifPresent(instrument -> stack.set(DataComponents.INSTRUMENT, (Holder<Instrument>)instrument));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        Optional<? extends Holder<Instrument>> optional = this.getInstrument(itemStack);
        if (optional.isPresent()) {
            Instrument instrument = optional.get().value();
            user.startUsingItem(hand);
            play(world, user, instrument);
            user.getCooldowns().addCooldown(this, instrument.useDuration());
            user.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.consume(itemStack);
        } else {
            return InteractionResultHolder.fail(itemStack);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        Optional<Holder<Instrument>> optional = this.getInstrument(stack);
        return optional.<Integer>map(instrument -> instrument.value().useDuration()).orElse(0);
    }

    private Optional<Holder<Instrument>> getInstrument(ItemStack stack) {
        Holder<Instrument> holder = stack.get(DataComponents.INSTRUMENT);
        if (holder != null) {
            return Optional.of(holder);
        } else {
            Iterator<Holder<Instrument>> iterator = BuiltInRegistries.INSTRUMENT.getTagOrEmpty(this.instruments).iterator();
            return iterator.hasNext() ? Optional.of(iterator.next()) : Optional.empty();
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    private static void play(Level world, Player player, Instrument instrument) {
        SoundEvent soundEvent = instrument.soundEvent().value();
        float f = instrument.range() / 16.0F;
        world.playSound(player, player, soundEvent, SoundSource.RECORDS, f, 1.0F);
        world.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
    }
}
