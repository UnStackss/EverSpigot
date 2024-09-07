package net.minecraft.advancements;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;

public class AdvancementProgress implements Comparable<AdvancementProgress> {
    private static final DateTimeFormatter OBTAINED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
    private static final Codec<Instant> OBTAINED_TIME_CODEC = ExtraCodecs.temporalCodec(OBTAINED_TIME_FORMAT)
        .xmap(Instant::from, instant -> instant.atZone(ZoneId.systemDefault()));
    private static final Codec<Map<String, CriterionProgress>> CRITERIA_CODEC = Codec.unboundedMap(Codec.STRING, OBTAINED_TIME_CODEC)
        .xmap(
            map -> map.entrySet().stream().collect(Collectors.toMap(Entry::getKey, entry -> new CriterionProgress(entry.getValue()))),
            map -> map.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().isDone())
                    .collect(Collectors.toMap(Entry::getKey, entry -> Objects.requireNonNull(entry.getValue().getObtained())))
        );
    public static final Codec<AdvancementProgress> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                    CRITERIA_CODEC.optionalFieldOf("criteria", Map.of()).forGetter(advancementProgress -> advancementProgress.criteria),
                    Codec.BOOL.fieldOf("done").orElse(true).forGetter(AdvancementProgress::isDone)
                )
                .apply(instance, (criteriaProgresses, done) -> new AdvancementProgress(new HashMap<>(criteriaProgresses)))
    );
    private final Map<String, CriterionProgress> criteria;
    private AdvancementRequirements requirements = AdvancementRequirements.EMPTY;

    private AdvancementProgress(Map<String, CriterionProgress> criteriaProgresses) {
        this.criteria = criteriaProgresses;
    }

    public AdvancementProgress() {
        this.criteria = Maps.newHashMap();
    }

    public void update(AdvancementRequirements requirements) {
        Set<String> set = requirements.names();
        this.criteria.entrySet().removeIf(progress -> !set.contains(progress.getKey()));

        for (String string : set) {
            this.criteria.putIfAbsent(string, new CriterionProgress());
        }

        this.requirements = requirements;
    }

    public boolean isDone() {
        return this.requirements.test(this::isCriterionDone);
    }

    public boolean hasProgress() {
        for (CriterionProgress criterionProgress : this.criteria.values()) {
            if (criterionProgress.isDone()) {
                return true;
            }
        }

        return false;
    }

    public boolean grantProgress(String name) {
        CriterionProgress criterionProgress = this.criteria.get(name);
        if (criterionProgress != null && !criterionProgress.isDone()) {
            criterionProgress.grant();
            return true;
        } else {
            return false;
        }
    }

    public boolean revokeProgress(String name) {
        CriterionProgress criterionProgress = this.criteria.get(name);
        if (criterionProgress != null && criterionProgress.isDone()) {
            criterionProgress.revoke();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "AdvancementProgress{criteria=" + this.criteria + ", requirements=" + this.requirements + "}";
    }

    public void serializeToNetwork(FriendlyByteBuf buf) {
        buf.writeMap(this.criteria, FriendlyByteBuf::writeUtf, (bufx, progresses) -> progresses.serializeToNetwork(bufx));
    }

    public static AdvancementProgress fromNetwork(FriendlyByteBuf buf) {
        Map<String, CriterionProgress> map = buf.readMap(FriendlyByteBuf::readUtf, CriterionProgress::fromNetwork);
        return new AdvancementProgress(map);
    }

    @Nullable
    public CriterionProgress getCriterion(String name) {
        return this.criteria.get(name);
    }

    private boolean isCriterionDone(String name) {
        CriterionProgress criterionProgress = this.getCriterion(name);
        return criterionProgress != null && criterionProgress.isDone();
    }

    public float getPercent() {
        if (this.criteria.isEmpty()) {
            return 0.0F;
        } else {
            float f = (float)this.requirements.size();
            float g = (float)this.countCompletedRequirements();
            return g / f;
        }
    }

    @Nullable
    public Component getProgressText() {
        if (this.criteria.isEmpty()) {
            return null;
        } else {
            int i = this.requirements.size();
            if (i <= 1) {
                return null;
            } else {
                int j = this.countCompletedRequirements();
                return Component.translatable("advancements.progress", j, i);
            }
        }
    }

    private int countCompletedRequirements() {
        return this.requirements.count(this::isCriterionDone);
    }

    public Iterable<String> getRemainingCriteria() {
        List<String> list = Lists.newArrayList();

        for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
            if (!entry.getValue().isDone()) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    public Iterable<String> getCompletedCriteria() {
        List<String> list = Lists.newArrayList();

        for (Entry<String, CriterionProgress> entry : this.criteria.entrySet()) {
            if (entry.getValue().isDone()) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    @Nullable
    public Instant getFirstProgressDate() {
        return this.criteria.values().stream().map(CriterionProgress::getObtained).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public int compareTo(AdvancementProgress advancementProgress) {
        Instant instant = this.getFirstProgressDate();
        Instant instant2 = advancementProgress.getFirstProgressDate();
        if (instant == null && instant2 != null) {
            return 1;
        } else if (instant != null && instant2 == null) {
            return -1;
        } else {
            return instant == null && instant2 == null ? 0 : instant.compareTo(instant2);
        }
    }
}
