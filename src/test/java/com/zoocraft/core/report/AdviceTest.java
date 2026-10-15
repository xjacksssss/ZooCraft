package com.zoocraft.core.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdviceTest {
    private static Advice.Issue issue(String kind, int count) {
        return new Advice.Issue(kind, List.of(), count);
    }

    @Test
    void ranksByCountTimesSeverity() {
        List<Advice.Issue> ranked = Advice.rank(List.of(
                issue("same_animals", 10),      // 10 × 1
                issue("hungry_no_food", 3),     // 3 × 4 = 12
                issue("sold_out", 3)), 5);      // 3 × 3 = 9
        assertEquals(List.of("hungry_no_food", "same_animals", "sold_out"), ranked.stream().map(Advice.Issue::kind).toList());
    }

    @Test
    void dropsIssuesWithNoCount() {
        assertEquals(List.of("no_toilet"), Advice.rank(List.of(issue("no_toilet", 1), issue("sold_out", 0)), 5)
                .stream().map(Advice.Issue::kind).toList());
    }

    @Test
    void respectsTheLimit() {
        assertEquals(2, Advice.rank(List.of(issue("a", 1), issue("b", 2), issue("c", 3)), 2).size());
    }

    @Test
    void unknownKindsWeighOne() {
        assertEquals(1, Advice.weight("something_new"));
        assertEquals(5, Advice.weight("could_not_leave"));
    }
}
