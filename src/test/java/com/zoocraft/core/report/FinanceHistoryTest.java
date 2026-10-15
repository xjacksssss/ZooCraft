package com.zoocraft.core.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FinanceHistoryTest {
    @Test
    void rollingClosesTodayAndStartsAFreshDay() {
        FinanceHistory history = new FinanceHistory();
        history.roll(10, 0);
        history.today().admission = 12;
        history.today().shopSales = 5;
        history.roll(11, 117);
        DayRecord yesterday = history.past().getLast();
        assertEquals(10, yesterday.day);
        assertEquals(117, yesterday.closingBalance);
        assertEquals(17, yesterday.income());
        assertEquals(11, history.today().day);
        assertEquals(0, history.today().admission);
    }

    @Test
    void rollingToTheSameDayDoesNothing() {
        FinanceHistory history = new FinanceHistory();
        history.roll(3, 0);
        DayRecord today = history.today();
        today.meals = 4;
        history.roll(3, 50);
        assertSame(today, history.today());
        assertEquals(0, history.past().size());
    }

    @Test
    void aGapOfSeveralDaysAddsOnlyOneRecord() {
        FinanceHistory history = new FinanceHistory();
        history.roll(1, 0);
        history.roll(6, 20);
        assertEquals(1, history.past().size());
        assertEquals(6, history.today().day);
    }

    @Test
    void historyIsCapped() {
        FinanceHistory history = new FinanceHistory();
        for (int day = 0; day < 40; day++) {
            history.roll(day, day);
        }
        assertEquals(FinanceHistory.DAYS - 1, history.past().size());
        assertEquals(39, history.today().day);
        assertEquals(12, history.past().getFirst().day);
    }

    @Test
    void lastDaysEndsWithToday() {
        FinanceHistory history = new FinanceHistory();
        for (int day = 0; day < 20; day++) {
            history.roll(day, 0);
        }
        List<DayRecord> last = history.lastDays(14);
        assertEquals(14, last.size());
        assertEquals(6, last.getFirst().day);
        assertSame(history.today(), last.getLast());
    }

    @Test
    void dayRecordsSurviveTheirSavedForm() {
        DayRecord record = new DayRecord();
        record.day = 7;
        record.admission = 3;
        record.generalVisitors = 9;
        DayRecord back = DayRecord.fromList(record.toList());
        assertEquals(record.toList(), back.toList());
        DayRecord short_ = DayRecord.fromList(List.of(5L, 2L));
        assertEquals(5, short_.day);
        assertEquals(2, short_.admission);
        assertEquals(0, short_.generalVisitors);
    }
}
