package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    public final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;

    @Nullable
    public static Pack readMetaAndCreate(PackLocationInfo info, Pack.ResourcesSupplier packFactory, PackType type, PackSelectionConfig position) {
        int i = SharedConstants.getCurrentVersion().getPackVersion(type);
        Pack.Metadata metadata = readPackMetadata(info, packFactory, i);
        return metadata != null ? new Pack(info, packFactory, metadata, position) : null;
    }

    public Pack(PackLocationInfo info, Pack.ResourcesSupplier packFactory, Pack.Metadata metaData, PackSelectionConfig position) {
        this.location = info;
        this.resources = packFactory;
        this.metadata = metaData;
        this.selectionConfig = position;
    }

    @Nullable
    public static Pack.Metadata readPackMetadata(PackLocationInfo info, Pack.ResourcesSupplier packFactory, int currentPackFormat) {
        try {
            Pack.Metadata var11;
            try (PackResources packResources = packFactory.openPrimary(info)) {
                PackMetadataSection packMetadataSection = packResources.getMetadataSection(PackMetadataSection.TYPE);
                if (packMetadataSection == null) {
                    LOGGER.warn("Missing metadata in pack {}", info.id());
                    return null;
                }

                FeatureFlagsMetadataSection featureFlagsMetadataSection = packResources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureFlagSet = featureFlagsMetadataSection != null ? featureFlagsMetadataSection.flags() : FeatureFlagSet.of();
                InclusiveRange<Integer> inclusiveRange = getDeclaredPackVersions(info.id(), packMetadataSection);
                PackCompatibility packCompatibility = PackCompatibility.forVersion(inclusiveRange, currentPackFormat);
                OverlayMetadataSection overlayMetadataSection = packResources.getMetadataSection(OverlayMetadataSection.TYPE);
                List<String> list = overlayMetadataSection != null ? overlayMetadataSection.overlaysForVersion(currentPackFormat) : List.of();
                var11 = new Pack.Metadata(packMetadataSection.description(), packCompatibility, featureFlagSet, list);
            }

            return var11;
        } catch (Exception var14) {
            LOGGER.warn("Failed to read pack {} metadata", info.id(), var14);
            return null;
        }
    }

    private static InclusiveRange<Integer> getDeclaredPackVersions(String packId, PackMetadataSection metadata) {
        int i = metadata.packFormat();
        if (metadata.supportedFormats().isEmpty()) {
            return new InclusiveRange<>(i);
        } else {
            InclusiveRange<Integer> inclusiveRange = metadata.supportedFormats().get();
            if (!inclusiveRange.isValueInRange(i)) {
                LOGGER.warn("Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", packId, inclusiveRange, i, i);
                return new InclusiveRange<>(i);
            } else {
                return inclusiveRange;
            }
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    public Component getChatLink(boolean enabled) {
        return this.location.createChatLink(enabled, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof Pack pack && this.location.equals(pack.location);
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public static record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
    }

    public static enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> items, T item, Function<T, PackSelectionConfig> profileGetter, boolean listInverted) {
            Pack.Position position = listInverted ? this.opposite() : this;
            if (position == BOTTOM) {
                int i;
                for (i = 0; i < items.size(); i++) {
                    PackSelectionConfig packSelectionConfig = profileGetter.apply(items.get(i));
                    if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
                        break;
                    }
                }

                items.add(i, item);
                return i;
            } else {
                int j;
                for (j = items.size() - 1; j >= 0; j--) {
                    PackSelectionConfig packSelectionConfig2 = profileGetter.apply(items.get(j));
                    if (!packSelectionConfig2.fixedPosition() || packSelectionConfig2.defaultPosition() != this) {
                        break;
                    }
                }

                items.add(j + 1, item);
                return j + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo info);

        PackResources openFull(PackLocationInfo info, Pack.Metadata metadata);
    }
}
