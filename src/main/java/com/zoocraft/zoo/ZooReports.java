package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zoocraft.core.report.DayRecord;
import com.zoocraft.core.report.FinanceHistory;
import com.zoocraft.core.report.RecommendationWindow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A zoo's reports (Phase 2 spec section 10): daily history, the recommendation window and today's complaint
 * subjects. Kept in one saved field so the zoo record stays small and Phase 1 saves load with empty reports.
 * Mutated only through {@link ZooManager}.
 */
public final class ZooReports {
    private static final Codec<DayRecord> DAY = Codec.LONG.listOf().xmap(DayRecord::fromList, DayRecord::toList);
    private static final Codec<FinanceHistory> HISTORY = RecordCodecBuilder.create(i -> i.group(
            DAY.fieldOf("today").forGetter(FinanceHistory::today),
            DAY.listOf().fieldOf("past").forGetter(FinanceHistory::past)
    ).apply(i, FinanceHistory::new));

    public static final Codec<ZooReports> CODEC = RecordCodecBuilder.create(i -> i.group(
            HISTORY.fieldOf("history").forGetter(r -> r.history),
            Codec.STRING.xmap(RecommendationWindow::decode, RecommendationWindow::encode).fieldOf("recommendations").forGetter(r -> r.recommendations),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("subjects").forGetter(r -> r.subjects),
            Codec.LONG.fieldOf("subjectsDay").forGetter(r -> r.subjectsDay),
            SavedExhibit.CODEC.listOf().optionalFieldOf("exhibits", List.of()).forGetter(r -> List.copyOf(r.exhibits))
    ).apply(i, ZooReports::new));

    private final FinanceHistory history;
    private final RecommendationWindow recommendations;
    private final Map<String, Integer> subjects;
    private long subjectsDay;
    private final List<SavedExhibit> exhibits;

    public ZooReports() {
        this(new FinanceHistory(), new RecommendationWindow(), Map.of(), Long.MIN_VALUE, List.of());
    }

    private ZooReports(FinanceHistory history, RecommendationWindow recommendations, Map<String, Integer> subjects, long subjectsDay,
                       List<SavedExhibit> exhibits) {
        this.history = history;
        this.recommendations = recommendations;
        this.subjects = new HashMap<>(subjects);
        this.subjectsDay = subjectsDay;
        this.exhibits = new ArrayList<>(exhibits);
    }

    /** Exhibits as of the last animal census, so view counts carry over a restart. */
    public List<SavedExhibit> exhibits() {
        return List.copyOf(exhibits);
    }

    void saveExhibits(List<SavedExhibit> latest) {
        exhibits.clear();
        exhibits.addAll(latest);
    }

    public FinanceHistory history() {
        return history;
    }

    public RecommendationWindow recommendations() {
        return recommendations;
    }

    /** {@code "<comment key>|<subject>"} to how often it happened today. */
    public Map<String, Integer> subjectsToday() {
        return Map.copyOf(subjects);
    }

    void rollDay(long day, long balance) {
        history.roll(day, balance);
        if (subjectsDay != day) {
            subjectsDay = day;
            subjects.clear();
        }
    }

    void recordSubject(String commentKey, String subject) {
        subjects.merge(commentKey + "|" + subject, 1, Integer::sum);
    }
}
