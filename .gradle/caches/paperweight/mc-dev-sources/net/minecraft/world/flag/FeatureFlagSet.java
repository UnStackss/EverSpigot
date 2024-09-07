package net.minecraft.world.flag;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;

public final class FeatureFlagSet {
    private static final FeatureFlagSet EMPTY = new FeatureFlagSet(null, 0L);
    public static final int MAX_CONTAINER_SIZE = 64;
    @Nullable
    private final FeatureFlagUniverse universe;
    private final long mask;

    private FeatureFlagSet(@Nullable FeatureFlagUniverse universe, long featuresMask) {
        this.universe = universe;
        this.mask = featuresMask;
    }

    static FeatureFlagSet create(FeatureFlagUniverse universe, Collection<FeatureFlag> features) {
        if (features.isEmpty()) {
            return EMPTY;
        } else {
            long l = computeMask(universe, 0L, features);
            return new FeatureFlagSet(universe, l);
        }
    }

    public static FeatureFlagSet of() {
        return EMPTY;
    }

    public static FeatureFlagSet of(FeatureFlag feature) {
        return new FeatureFlagSet(feature.universe, feature.mask);
    }

    public static FeatureFlagSet of(FeatureFlag feature1, FeatureFlag... features) {
        long l = features.length == 0 ? feature1.mask : computeMask(feature1.universe, feature1.mask, Arrays.asList(features));
        return new FeatureFlagSet(feature1.universe, l);
    }

    private static long computeMask(FeatureFlagUniverse universe, long featuresMask, Iterable<FeatureFlag> newFeatures) {
        for (FeatureFlag featureFlag : newFeatures) {
            if (universe != featureFlag.universe) {
                throw new IllegalStateException("Mismatched feature universe, expected '" + universe + "', but got '" + featureFlag.universe + "'");
            }

            featuresMask |= featureFlag.mask;
        }

        return featuresMask;
    }

    public boolean contains(FeatureFlag feature) {
        return this.universe == feature.universe && (this.mask & feature.mask) != 0L;
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    public boolean isSubsetOf(FeatureFlagSet features) {
        return this.universe == null || this.universe == features.universe && (this.mask & ~features.mask) == 0L;
    }

    public boolean intersects(FeatureFlagSet features) {
        return this.universe != null && features.universe != null && this.universe == features.universe && (this.mask & features.mask) != 0L;
    }

    public FeatureFlagSet join(FeatureFlagSet features) {
        if (this.universe == null) {
            return features;
        } else if (features.universe == null) {
            return this;
        } else if (this.universe != features.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + features.universe + "'");
        } else {
            return new FeatureFlagSet(this.universe, this.mask | features.mask);
        }
    }

    public FeatureFlagSet subtract(FeatureFlagSet features) {
        if (this.universe == null || features.universe == null) {
            return this;
        } else if (this.universe != features.universe) {
            throw new IllegalArgumentException("Mismatched set elements: '" + this.universe + "' != '" + features.universe + "'");
        } else {
            long l = this.mask & ~features.mask;
            return l == 0L ? EMPTY : new FeatureFlagSet(this.universe, l);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else {
            if (object instanceof FeatureFlagSet featureFlagSet && this.universe == featureFlagSet.universe && this.mask == featureFlagSet.mask) {
                return true;
            }

            return false;
        }
    }

    @Override
    public int hashCode() {
        return (int)HashCommon.mix(this.mask);
    }
}
