package net.minecraft.world.level.storage.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemContainerContents;

public interface ContainerComponentManipulators {
    ContainerComponentManipulator<ItemContainerContents> CONTAINER = new ContainerComponentManipulator<ItemContainerContents>() {
        @Override
        public DataComponentType<ItemContainerContents> type() {
            return DataComponents.CONTAINER;
        }

        @Override
        public Stream<ItemStack> getContents(ItemContainerContents component) {
            return component.stream();
        }

        @Override
        public ItemContainerContents empty() {
            return ItemContainerContents.EMPTY;
        }

        @Override
        public ItemContainerContents setContents(ItemContainerContents component, Stream<ItemStack> contents) {
            return ItemContainerContents.fromItems(contents.toList());
        }
    };
    ContainerComponentManipulator<BundleContents> BUNDLE_CONTENTS = new ContainerComponentManipulator<BundleContents>() {
        @Override
        public DataComponentType<BundleContents> type() {
            return DataComponents.BUNDLE_CONTENTS;
        }

        @Override
        public BundleContents empty() {
            return BundleContents.EMPTY;
        }

        @Override
        public Stream<ItemStack> getContents(BundleContents component) {
            return component.itemCopyStream();
        }

        @Override
        public BundleContents setContents(BundleContents component, Stream<ItemStack> contents) {
            BundleContents.Mutable mutable = new BundleContents.Mutable(component).clearItems();
            contents.forEach(mutable::tryInsert);
            return mutable.toImmutable();
        }
    };
    ContainerComponentManipulator<ChargedProjectiles> CHARGED_PROJECTILES = new ContainerComponentManipulator<ChargedProjectiles>() {
        @Override
        public DataComponentType<ChargedProjectiles> type() {
            return DataComponents.CHARGED_PROJECTILES;
        }

        @Override
        public ChargedProjectiles empty() {
            return ChargedProjectiles.EMPTY;
        }

        @Override
        public Stream<ItemStack> getContents(ChargedProjectiles component) {
            return component.getItems().stream();
        }

        @Override
        public ChargedProjectiles setContents(ChargedProjectiles component, Stream<ItemStack> contents) {
            return ChargedProjectiles.of(contents.toList());
        }
    };
    Map<DataComponentType<?>, ContainerComponentManipulator<?>> ALL_MANIPULATORS = Stream.of(CONTAINER, BUNDLE_CONTENTS, CHARGED_PROJECTILES)
        .collect(
            Collectors.toMap(
                ContainerComponentManipulator::type, containerComponentManipulator -> (ContainerComponentManipulator<?>)containerComponentManipulator
            )
        );
    Codec<ContainerComponentManipulator<?>> CODEC = BuiltInRegistries.DATA_COMPONENT_TYPE.byNameCodec().comapFlatMap(componentType -> {
        ContainerComponentManipulator<?> containerComponentManipulator = ALL_MANIPULATORS.get(componentType);
        return containerComponentManipulator != null ? DataResult.success(containerComponentManipulator) : DataResult.error(() -> "No items in component");
    }, ContainerComponentManipulator::type);
}
