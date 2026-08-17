package idusw.sb.missionplan.settle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CycleTest {

    private static final LocalDate ROOM_START = LocalDate.of(2026, 8, 10);

    @Test
    void firstCycle_startsOnRoomStartDate() {
        LocalDate target = LocalDate.of(2026, 8, 12);

        assertThat(Cycle.startDate(ROOM_START, target)).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(Cycle.endDate(ROOM_START, target)).isEqualTo(LocalDate.of(2026, 8, 16));
    }

    @Test
    void secondCycle_startsSevenDaysAfterFirst() {
        LocalDate target = LocalDate.of(2026, 8, 20);

        assertThat(Cycle.startDate(ROOM_START, target)).isEqualTo(LocalDate.of(2026, 8, 17));
        assertThat(Cycle.endDate(ROOM_START, target)).isEqualTo(LocalDate.of(2026, 8, 23));
    }

    @Test
    void cycleBoundary_lastDayBelongsToCurrentCycle() {
        LocalDate lastDayOfFirstCycle = LocalDate.of(2026, 8, 16);

        assertThat(Cycle.startDate(ROOM_START, lastDayOfFirstCycle)).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void cycleBoundary_nextDayBelongsToNextCycle() {
        LocalDate firstDayOfSecondCycle = LocalDate.of(2026, 8, 17);

        assertThat(Cycle.startDate(ROOM_START, firstDayOfSecondCycle)).isEqualTo(LocalDate.of(2026, 8, 17));
    }
}
